package com.example.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.download.SourceValidator
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    
    private lateinit var repository: DownloadRepository
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "ronycine_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"

        fun startDownload(context: Context, id: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        repository = DownloadRepository(db.downloadDao())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Gerenciador de downloads ativo", 0, false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)
        val action = intent?.action

        when (action) {
            ACTION_START, ACTION_RESUME -> {
                if (downloadId != null) {
                    scheduleDownload(downloadId)
                }
            }
            ACTION_PAUSE -> {
                downloadId?.let { pauseDownload(it) }
            }
            ACTION_CANCEL -> {
                downloadId?.let { cancelDownload(it) }
            }
        }

        checkAndStartNextQueued()

        return START_NOT_STICKY
    }

    private fun scheduleDownload(id: String) {
        if (activeDownloads.containsKey(id)) return

        if (activeDownloads.size >= MAX_CONCURRENT_DOWNLOADS) {
            serviceScope.launch {
                repository.updateStatus(id, DownloadStatus.QUEUED)
            }
            return
        }

        val job = serviceScope.launch {
            runDownload(id)
        }
        activeDownloads[id] = job
    }

    private fun checkAndStartNextQueued() {
        if (activeDownloads.size >= MAX_CONCURRENT_DOWNLOADS) return

        serviceScope.launch {
            val queued = repository.getQueuedDownloads()
            for (task in queued) {
                if (activeDownloads.size >= MAX_CONCURRENT_DOWNLOADS) break
                if (!activeDownloads.containsKey(task.id)) {
                    scheduleDownload(task.id)
                }
            }
        }
    }

    private suspend fun runDownload(id: String) {
        val task = repository.getDownloadById(id)
        if (task == null || task.status == DownloadStatus.COMPLETED) {
            activeDownloads.remove(id)
            return
        }

        val downloadsDir = File(getExternalFilesDir(null), "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val file = File(downloadsDir, task.fileName)
        var existingBytes = if (file.exists()) file.length() else 0L

        // Storage Check
        val availableDisk = SourceValidator.getAvailableStorageBytes(downloadsDir)
        if (task.totalSize > 0 && !SourceValidator.hasAvailableDiskSpace(downloadsDir, (task.totalSize - existingBytes).coerceAtLeast(10 * 1024 * 1024L))) {
            Log.e(TAG, "Not enough disk space for $id")
            repository.updateStatus(id, DownloadStatus.ERROR, "Espaço de armazenamento insuficiente no dispositivo.")
            activeDownloads.remove(id)
            return
        }

        val isHls = task.url.contains(".m3u8", ignoreCase = true) ||
                    task.mimeType?.contains("mpegurl", ignoreCase = true) == true ||
                    task.mimeType?.contains("hls", ignoreCase = true) == true

        try {
            repository.updateStatus(id, DownloadStatus.DOWNLOADING)
            updateNotification("Iniciando download: ${task.title}", 0, true)

            if (isHls) {
                runHlsDownload(task, file, id)
            } else {
                runDirectDownload(task, file, id, existingBytes)
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet: ${e.message}")
            repository.updateStatus(id, DownloadStatus.ERROR, "Sem conexão com a internet.")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout: ${e.message}")
            repository.updateStatus(id, DownloadStatus.ERROR, "Tempo limite esgotado com o servidor.")
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $id: ${e.message}", e)
            if (activeDownloads.containsKey(id)) {
                repository.updateStatus(id, DownloadStatus.ERROR, e.localizedMessage ?: "Erro desconhecido durante o download.")
            }
        } finally {
            activeDownloads.remove(id)
            checkAndStartNextQueued()
            if (activeDownloads.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private suspend fun runHlsDownload(task: DownloadTask, file: File, id: String) = withContext(Dispatchers.IO) {
        val masterReq = Request.Builder()
            .url(task.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        var playlistContent: String
        var mediaPlaylistUrl = task.url

        client.newCall(masterReq).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Erro ${response.code} ao obter playlist HLS.")
            playlistContent = response.body?.string() ?: throw IOException("Playlist HLS vazia.")
        }

        // Check if Master playlist
        if (playlistContent.contains("#EXT-X-STREAM-INF")) {
            val lines = playlistContent.lines()
            var chosenUri: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                    val candidate = lines[i + 1].trim()
                    if (candidate.isNotBlank() && !candidate.startsWith("#")) {
                        chosenUri = candidate
                        break
                    }
                }
            }
            if (chosenUri != null) {
                mediaPlaylistUrl = resolveUrl(task.url, chosenUri)
                val mediaReq = Request.Builder()
                    .url(mediaPlaylistUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                client.newCall(mediaReq).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Erro ${response.code} ao obter mídia HLS.")
                    playlistContent = response.body?.string() ?: throw IOException("Mídia HLS vazia.")
                }
            }
        }

        // Extract segments
        val segmentUrls = mutableListOf<String>()
        val mediaLines = playlistContent.lines()
        for (line in mediaLines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                segmentUrls.add(resolveUrl(mediaPlaylistUrl, trimmed))
            }
        }

        if (segmentUrls.isEmpty()) {
            throw IOException("Nenhum segmento de vídeo encontrado na transmissão HLS.")
        }

        val totalSegments = segmentUrls.size
        var downloadedBytes = 0L
        var lastTime = System.currentTimeMillis()
        var bytesSinceLast = 0L
        val outputStream = FileOutputStream(file, false)

        outputStream.use { out ->
            for ((index, segUrl) in segmentUrls.withIndex()) {
                if (!activeDownloads.containsKey(id)) {
                    break // Paused or canceled
                }

                val segReq = Request.Builder()
                    .url(segUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                client.newCall(segReq).execute().use { segResp ->
                    if (!segResp.isSuccessful) return@use
                    val bytes = segResp.body?.bytes() ?: return@use
                    out.write(bytes)
                    downloadedBytes += bytes.size
                    bytesSinceLast += bytes.size
                }

                val now = System.currentTimeMillis()
                val elapsed = now - lastTime
                if (elapsed >= 1000L || index == totalSegments - 1) {
                    val speed = if (elapsed > 0) (bytesSinceLast * 1000L) / elapsed else 0L
                    lastTime = now
                    bytesSinceLast = 0L

                    val progressPercent = ((index + 1) * 100) / totalSegments
                    val estimatedTotal = if (downloadedBytes > 0) (downloadedBytes * totalSegments) / (index + 1) else 0L
                    updateNotification("${task.title} ($progressPercent%)", progressPercent, false)
                    repository.updateProgress(id, downloadedBytes, estimatedTotal, speed, DownloadStatus.DOWNLOADING)
                }
            }
        }

        if (activeDownloads.containsKey(id)) {
            if (file.exists() && file.length() > 0) {
                val finalSize = file.length()
                repository.markCompleted(id, file.absolutePath, finalSize)
                updateNotification("Concluído: ${task.title}", 100, false)
                Log.d(TAG, "HLS Download completed: $id at ${file.absolutePath} ($finalSize bytes)")
            } else {
                repository.updateStatus(id, DownloadStatus.ERROR, "Arquivo corrompido ou vazio.")
            }
        }
    }

    private suspend fun runDirectDownload(task: DownloadTask, file: File, id: String, startByte: Long) = withContext(Dispatchers.IO) {
        var existingBytes = startByte
        val requestBuilder = Request.Builder()
            .url(task.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        // Range support for pause/resume
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            Log.d(TAG, "Resuming download from byte: $existingBytes")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                val errorMsg = when (response.code) {
                    403, 401 -> "Acesso negado pelo servidor da fonte."
                    404 -> "Arquivo não encontrado no servidor."
                    500, 502, 503 -> "Servidor de stream temporariamente indisponível."
                    else -> "Erro HTTP ${response.code} ao baixar arquivo."
                }
                repository.updateStatus(id, DownloadStatus.ERROR, errorMsg)
                return@use
            }

            val body = response.body ?: throw IOException("Corpo de resposta vazio do servidor.")
            val isPartial = response.code == 206
            
            val appendMode: Boolean
            val totalBytes: Long

            if (isPartial) {
                appendMode = true
                val incomingLength = body.contentLength()
                totalBytes = if (incomingLength > 0) existingBytes + incomingLength else task.totalSize
            } else {
                appendMode = false
                existingBytes = 0L
                totalBytes = body.contentLength()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file, appendMode)

            val buffer = ByteArray(16384) // 16 KB buffer for efficiency
            var bytesRead: Int
            var downloadedBytes = existingBytes
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!activeDownloads.containsKey(id)) {
                            // Task was cancelled or paused
                            break
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastTime += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 1000L) {
                            val speed = (bytesSinceLastTime * 1000L) / elapsed
                            lastTime = now
                            bytesSinceLastTime = 0L

                            val progressPercent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                            updateNotification("${task.title} ($progressPercent%)", progressPercent, false)
                            repository.updateProgress(id, downloadedBytes, totalBytes, speed, DownloadStatus.DOWNLOADING)
                        }
                    }
                }
            }

            if (activeDownloads.containsKey(id)) {
                if (file.exists() && file.length() > 0) {
                    val finalSize = file.length()
                    repository.markCompleted(id, file.absolutePath, finalSize)
                    updateNotification("Concluído: ${task.title}", 100, false)
                    Log.d(TAG, "Download completed: $id at ${file.absolutePath} ($finalSize bytes)")
                } else {
                    repository.updateStatus(id, DownloadStatus.ERROR, "Arquivo corrompido ou vazio.")
                }
            }
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            val baseUri = java.net.URI(baseUrl)
            baseUri.resolve(relativeOrAbsolute).toString()
        } catch (e: Exception) {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else {
                baseUrl.substringBeforeLast("/") + "/" + relativeOrAbsolute
            }
        }
    }

    private fun pauseDownload(id: String) {
        val job = activeDownloads.remove(id)
        job?.cancel()
        serviceScope.launch {
            repository.updateStatus(id, DownloadStatus.PAUSED)
        }
    }

    private fun cancelDownload(id: String) {
        val job = activeDownloads.remove(id)
        job?.cancel()
        serviceScope.launch {
            repository.updateStatus(id, DownloadStatus.CANCELED)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads RONYCINE",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso em segundo plano dos downloads do RONYCINE"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int, indeterminate: Boolean): Notification {
        val smallIconRes = R.mipmap.ic_launcher
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RONYCINE")
            .setContentText(content)
            .setSmallIcon(smallIconRes)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean = false) {
        try {
            val notification = createNotification(content, progress, indeterminate)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Notification update error: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
