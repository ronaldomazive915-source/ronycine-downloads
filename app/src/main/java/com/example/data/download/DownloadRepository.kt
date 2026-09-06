package com.example.data.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.data.local.DownloadEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import com.example.data.local.PlayFilmeDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class DownloadRepository(
    private val context: Context,
    private val dao: PlayFilmeDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    private val okHttpClient: OkHttpClient = createResilientOkHttpClient()

    val allDownloads: Flow<List<DownloadEntity>> = dao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = dao.getCompletedDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = dao.getActiveDownloads()

    init {
        scope.launch {
            cleanInvalidDownloads()
        }
    }

    companion object {
        private const val TAG = "DownloadRepo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private fun createResilientOkHttpClient(): OkHttpClient {
            return try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            } catch (e: Exception) {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            }
        }
    }

    private suspend fun cleanInvalidDownloads() {
        try {
            val all = dao.getAllDownloadsSync()
            for (item in all) {
                if (item.status == DownloadEntity.STATUS_COMPLETED) {
                    val path = item.localFilePath
                    val file = path?.let { File(it) }
                    if (file == null || !file.exists() || file.length() <= 0L || item.downloadedBytes <= 0L) {
                        Log.w(TAG, "Limpando download inválido/ausente: ${item.title}")
                        dao.updateDownloadProgress(
                            id = item.id,
                            status = DownloadEntity.STATUS_ERROR,
                            progress = 0,
                            downloadedBytes = 0L,
                            totalBytes = 0L,
                            downloadSpeed = null,
                            errorMessage = "O arquivo baixado não foi encontrado ou está vazio no dispositivo."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar downloads inválidos: ${e.message}")
        }
    }

    fun observeDownloadForMedia(tmdbId: Int): Flow<List<DownloadEntity>> {
        return dao.observeDownloadsForMedia(tmdbId)
    }

    fun observeDownloadForEpisode(tmdbId: Int, seasonNumber: Int?, episodeNumber: Int?): Flow<DownloadEntity?> {
        return dao.observeDownloadForEpisode(tmdbId, seasonNumber, episodeNumber)
    }

    fun observeDownloadById(id: String): Flow<DownloadEntity?> {
        return dao.observeDownloadById(id)
    }

    // --- Network & Storage Checks ---

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val targetDir = Environment.getExternalStorageDirectory() ?: context.filesDir
            val stat = StatFs(targetDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    fun getTotalStorageBytes(): Long {
        return try {
            val targetDir = Environment.getExternalStorageDirectory() ?: context.filesDir
            val stat = StatFs(targetDir.path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    fun getUsedStorageBytes(): Long {
        return try {
            var total = 0L
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RONYCINE")
            if (publicDir.exists()) {
                total += publicDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            }
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "RONYCINE")
            if (fallbackDir.exists()) {
                total += fallbackDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            }
            total
        } catch (_: Exception) {
            0L
        }
    }

    // --- Safe File Naming & Folder Organization ---

    fun sanitizeFileName(name: String): String {
        val invalid = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")
        val cleaned = name.replace(invalid, "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return cleaned.ifBlank { "video" }
    }

    fun getTargetFileForDownload(download: DownloadEntity, releaseYear: String? = null): File {
        val isTv = download.mediaType == "tv" || download.mediaType == "serie" || download.seasonNumber != null
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val ronycinePublicDir = File(publicDownloads, "RONYCINE")

        val targetDir: File
        val fileName: String

        if (isTv) {
            val safeSeries = sanitizeFileName(download.title)
            val sNum = download.seasonNumber ?: 1
            val eNum = download.episodeNumber ?: 1
            val sStr = String.format("%02d", sNum)
            val eStr = String.format("%02d", eNum)
            val epName = if (!download.episodeTitle.isNullOrBlank()) " - ${sanitizeFileName(download.episodeTitle)}" else ""
            fileName = "$safeSeries - S${sStr}E${eStr}${epName}.mp4"

            val seasonFolder = "Temporada $sStr"
            targetDir = File(File(ronycinePublicDir, "Series"), "$safeSeries/$seasonFolder")
        } else {
            val safeTitle = sanitizeFileName(download.title)
            val year = releaseYear?.trim() ?: ""
            fileName = if (year.isNotBlank()) "$safeTitle ($year).mp4" else "$safeTitle.mp4"
            targetDir = File(ronycinePublicDir, "Filmes")
        }

        val canUsePublic = try {
            if (!targetDir.exists()) targetDir.mkdirs()
            targetDir.exists() && targetDir.canWrite()
        } catch (_: Exception) {
            false
        }

        return if (canUsePublic) {
            File(targetDir, fileName)
        } else {
            val fallbackBase = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "RONYCINE")
            val fallbackDir = if (isTv) {
                val safeSeries = sanitizeFileName(download.title)
                val sStr = String.format("%02d", download.seasonNumber ?: 1)
                File(File(fallbackBase, "Series"), "$safeSeries/Temporada $sStr")
            } else {
                File(fallbackBase, "Filmes")
            }
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            File(fallbackDir, fileName)
        }
    }

    // --- Stream URL Resolution ---

    suspend fun resolveVideoUrl(download: DownloadEntity): String? = withContext(Dispatchers.IO) {
        val rawUrl = download.videoUrl?.trim()
        if (!rawUrl.isNullOrBlank() && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
            val lower = rawUrl.lowercase()
            val isImage = lower.contains("image.tmdb.org") || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp")
            val isYoutube = lower.contains("youtube.com") || lower.contains("youtu.be")
            val isHtmlEmbed = lower.contains("embed.playmoz.xyz") && !lower.contains(".mp4") && !lower.contains(".mkv") && !lower.contains(".m3u8")

            if (!isImage && !isYoutube && !isHtmlEmbed) {
                if (lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm") || lower.contains(".m3u8") || lower.contains("/media/") || lower.contains("/video/")) {
                    try {
                        val testReq = Request.Builder()
                            .url(rawUrl)
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", "https://embed.playmoz.xyz/")
                            .header("Range", "bytes=0-1024")
                            .build()

                        val testResp = okHttpClient.newCall(testReq).execute()
                        val code = testResp.code
                        val cType = testResp.header("Content-Type")?.lowercase() ?: ""
                        testResp.close()

                        if ((code in 200..299 || code in 300..399) && !cType.contains("text/html")) {
                            return@withContext rawUrl
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Aviso ao validar URL fornecida: $rawUrl (${e.message})")
                    }
                }
            }
        }

        val tmdbId = download.tmdbId
        if (tmdbId <= 0) return@withContext null

        val isTv = download.mediaType == "tv" || download.mediaType == "serie" || download.seasonNumber != null
        val s = download.seasonNumber ?: 1
        val e = download.episodeNumber ?: 1

        val endpointUrl = if (isTv) {
            "https://embed.playmoz.xyz/serie/?action=sources&id=$tmdbId&t=$s&e=$e"
        } else {
            "https://embed.playmoz.xyz/filme/?action=sources&id=$tmdbId"
        }

        val refererUrl = if (isTv) {
            "https://embed.playmoz.xyz/tv/$tmdbId/$s/$e"
        } else {
            "https://embed.playmoz.xyz/movie/$tmdbId"
        }

        Log.d(TAG, "Resolvendo fontes para ${download.title} através de: $endpointUrl")

        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", refererUrl)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "PlayMoz sources retornou status ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)
            val sourcesArray = json.optJSONArray("sources") ?: return@withContext null

            val mp4Candidates = mutableListOf<String>()
            val hlsCandidates = mutableListOf<String>()

            for (i in 0 until sourcesArray.length()) {
                val item = sourcesArray.optJSONObject(i) ?: continue
                val fileUrl = item.optString("file").trim()
                val type = item.optString("type").lowercase()
                if (fileUrl.isBlank()) continue

                if (type == "hls" || fileUrl.contains(".m3u8")) {
                    hlsCandidates.add(fileUrl)
                } else if (type == "mp4" || fileUrl.contains(".mp4") || fileUrl.contains(".mkv") || fileUrl.contains(".webm")) {
                    mp4Candidates.add(fileUrl)
                }
            }

            val sortedMp4 = mp4Candidates.sortedByDescending {
                when {
                    it.contains("r2.cloudflarestorage.com") -> 3
                    it.contains("hubby.cx") -> 2
                    it.contains(".mp4") -> 1
                    else -> 0
                }
            }

            for (candidate in sortedMp4) {
                try {
                    val testReq = Request.Builder()
                        .url(candidate)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://embed.playmoz.xyz/")
                        .header("Range", "bytes=0-1024")
                        .build()

                    val testResp = okHttpClient.newCall(testReq).execute()
                    val code = testResp.code
                    val cType = testResp.header("Content-Type")?.lowercase() ?: ""
                    testResp.close()

                    if ((code in 200..299 || code in 300..399) && !cType.contains("text/html")) {
                        Log.d(TAG, "Fonte MP4 validada com sucesso: $candidate")
                        return@withContext candidate
                    }
                } catch (err: Exception) {
                    Log.d(TAG, "Fonte MP4 falhou no teste: $candidate (${err.message})")
                }
            }

            for (candidate in hlsCandidates) {
                try {
                    val testReq = Request.Builder()
                        .url(candidate)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://embed.playmoz.xyz/")
                        .build()

                    val testResp = okHttpClient.newCall(testReq).execute()
                    val code = testResp.code
                    val cType = testResp.header("Content-Type")?.lowercase() ?: ""
                    testResp.close()

                    if ((code in 200..299 || code in 300..399) && !cType.contains("text/html")) {
                        Log.d(TAG, "Fonte HLS validada com sucesso: $candidate")
                        return@withContext candidate
                    }
                } catch (err: Exception) {
                    Log.d(TAG, "Fonte HLS falhou no teste: $candidate (${err.message})")
                }
            }

            if (sortedMp4.isNotEmpty()) {
                return@withContext sortedMp4.first()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao resolver fontes do PlayMoz: ${e.message}", e)
        }

        return@withContext null
    }

    // --- Initiation & Execution ---

    suspend fun startMovieDownload(media: MediaEntity, customUrl: String? = null) {
        val downloadId = DownloadEntity.generateId(media.tmdbId, "movie")
        val existing = dao.getDownloadById(downloadId)

        if (existing != null && existing.status == DownloadEntity.STATUS_COMPLETED) {
            val file = existing.localFilePath?.let { File(it) }
            if (file != null && file.exists() && file.length() > 0) {
                Log.d(TAG, "Filme ${media.title} já está baixado no dispositivo.")
                return
            }
        }

        if (activeDownloadJobs.containsKey(downloadId) || existing?.status == DownloadEntity.STATUS_DOWNLOADING) {
            Log.d(TAG, "Download do filme ${media.title} já está em andamento.")
            return
        }

        if (!isNetworkAvailable()) {
            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = media.tmdbId,
                mediaType = "movie",
                title = media.title,
                posterPath = media.posterPath,
                backdropPath = media.backdropPath,
                status = DownloadEntity.STATUS_ERROR,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorMessage = "Sem conexão com a internet.",
                createdAt = System.currentTimeMillis()
            )
            dao.insertDownload(entity)
            return
        }

        val targetFile = getTargetFileForDownload(
            DownloadEntity(
                id = downloadId,
                tmdbId = media.tmdbId,
                mediaType = "movie",
                title = media.title
            ),
            releaseYear = media.releaseYear
        )

        val tempFile = File(targetFile.absolutePath + ".part")

        val downloadEntity = DownloadEntity(
            id = downloadId,
            tmdbId = media.tmdbId,
            mediaType = "movie",
            title = media.title,
            posterPath = media.posterPath,
            backdropPath = media.backdropPath,
            status = DownloadEntity.STATUS_PREPARING,
            progress = 0,
            downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L,
            totalBytes = 0L,
            localFilePath = targetFile.absolutePath,
            videoUrl = customUrl,
            createdAt = System.currentTimeMillis()
        )

        dao.insertDownload(downloadEntity)
        executeDownload(downloadEntity, targetFile)
    }

    suspend fun startEpisodeDownload(
        media: MediaEntity,
        episode: EpisodeEntity,
        customUrl: String? = null
    ) {
        val downloadId = DownloadEntity.generateId(
            media.tmdbId,
            "tv",
            episode.seasonNumber,
            episode.episodeNumber
        )
        val existing = dao.getDownloadById(downloadId)

        if (existing != null && existing.status == DownloadEntity.STATUS_COMPLETED) {
            val file = existing.localFilePath?.let { File(it) }
            if (file != null && file.exists() && file.length() > 0) {
                Log.d(TAG, "Episódio ${media.title} S${episode.seasonNumber}E${episode.episodeNumber} já está baixado.")
                return
            }
        }

        if (activeDownloadJobs.containsKey(downloadId) || existing?.status == DownloadEntity.STATUS_DOWNLOADING) {
            Log.d(TAG, "Download do episódio já em andamento.")
            return
        }

        if (!isNetworkAvailable()) {
            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = media.tmdbId,
                mediaType = "tv",
                title = media.title,
                posterPath = media.posterPath,
                backdropPath = media.backdropPath,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.title.ifBlank { "Episódio ${episode.episodeNumber}" },
                status = DownloadEntity.STATUS_ERROR,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorMessage = "Sem conexão com a internet.",
                createdAt = System.currentTimeMillis()
            )
            dao.insertDownload(entity)
            return
        }

        val targetFile = getTargetFileForDownload(
            DownloadEntity(
                id = downloadId,
                tmdbId = media.tmdbId,
                mediaType = "tv",
                title = media.title,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.title
            )
        )

        val tempFile = File(targetFile.absolutePath + ".part")
        val resolvedUrl = customUrl ?: episode.videoUrl.takeIf { !it.isNullOrBlank() }

        val downloadEntity = DownloadEntity(
            id = downloadId,
            tmdbId = media.tmdbId,
            mediaType = "tv",
            title = media.title,
            posterPath = media.posterPath,
            backdropPath = media.backdropPath,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title.ifBlank { "Episódio ${episode.episodeNumber}" },
            status = DownloadEntity.STATUS_PREPARING,
            progress = 0,
            downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L,
            totalBytes = 0L,
            localFilePath = targetFile.absolutePath,
            videoUrl = resolvedUrl,
            createdAt = System.currentTimeMillis()
        )

        dao.insertDownload(downloadEntity)
        executeDownload(downloadEntity, targetFile)
    }

    fun resumeDownload(downloadId: String) {
        scope.launch {
            val entity = dao.getDownloadById(downloadId) ?: return@launch
            val targetFile = entity.localFilePath?.let { File(it) } ?: getTargetFileForDownload(entity)
            executeDownload(entity, targetFile)
        }
    }

    fun pauseDownload(downloadId: String) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)
        scope.launch {
            val entity = dao.getDownloadById(downloadId) ?: return@launch
            val targetFile = entity.localFilePath?.let { File(it) }
            val tempFile = targetFile?.let { File(it.absolutePath + ".part") }
            val currentBytes = if (tempFile != null && tempFile.exists()) tempFile.length() else entity.downloadedBytes

            dao.updateDownloadProgress(
                id = downloadId,
                status = DownloadEntity.STATUS_PAUSED,
                progress = entity.progress,
                downloadedBytes = currentBytes,
                totalBytes = entity.totalBytes,
                downloadSpeed = null,
                errorMessage = null
            )
        }
    }

    fun cancelDownload(downloadId: String) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)
        scope.launch {
            val entity = dao.getDownloadById(downloadId)
            if (entity?.localFilePath != null) {
                try {
                    val targetFile = File(entity.localFilePath)
                    if (targetFile.exists()) targetFile.delete()
                    val tempFile = File(targetFile.absolutePath + ".part")
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}
            }
            dao.deleteDownloadById(downloadId)
        }
    }

    suspend fun deleteDownload(downloadId: String) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)
        val entity = dao.getDownloadById(downloadId)
        if (entity?.localFilePath != null) {
            try {
                val targetFile = File(entity.localFilePath)
                if (targetFile.exists()) {
                    targetFile.delete()
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            arrayOf("video/mp4")
                        ) { _, _ -> }
                    } catch (_: Exception) {}
                }
                val tempFile = File(targetFile.absolutePath + ".part")
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
        }
        dao.deleteDownloadById(downloadId)
    }

    suspend fun deleteAllDownloads() {
        for ((_, job) in activeDownloadJobs) {
            job.cancel()
        }
        activeDownloadJobs.clear()

        try {
            val all = dao.getAllDownloadsSync()
            for (item in all) {
                if (item.localFilePath != null) {
                    try {
                        val targetFile = File(item.localFilePath)
                        if (targetFile.exists()) targetFile.delete()
                        val tempFile = File(targetFile.absolutePath + ".part")
                        if (tempFile.exists()) tempFile.delete()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        dao.deleteAllDownloads()
    }

    private fun executeDownload(download: DownloadEntity, targetFile: File) {
        activeDownloadJobs[download.id]?.cancel()

        val job = scope.launch {
            val tempFile = File(targetFile.absolutePath + ".part")
            try {
                if (!isNetworkAvailable()) {
                    dao.updateDownloadProgress(
                        id = download.id,
                        status = DownloadEntity.STATUS_ERROR,
                        progress = download.progress,
                        downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L,
                        totalBytes = download.totalBytes,
                        downloadSpeed = null,
                        localFilePath = targetFile.absolutePath,
                        errorMessage = "Sem conexão com a internet."
                    )
                    return@launch
                }

                dao.updateDownloadProgress(
                    id = download.id,
                    status = DownloadEntity.STATUS_PREPARING,
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    downloadSpeed = null,
                    localFilePath = targetFile.absolutePath,
                    errorMessage = null
                )

                val directUrl = resolveVideoUrl(download)
                if (directUrl.isNullOrBlank()) {
                    Log.w(TAG, "Nenhuma fonte de vídeo acessível para ${download.title}")
                    dao.updateDownloadProgress(
                        id = download.id,
                        status = DownloadEntity.STATUS_ERROR,
                        progress = 0,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        downloadSpeed = null,
                        localFilePath = targetFile.absolutePath,
                        errorMessage = "Não foi possível iniciar o download. A fonte do vídeo não está disponível."
                    )
                    return@launch
                }

                dao.updateDownload(download.copy(videoUrl = directUrl, localFilePath = targetFile.absolutePath))

                targetFile.parentFile?.mkdirs()

                val isHls = directUrl.contains(".m3u8", ignoreCase = true)

                if (isHls) {
                    downloadHlsStream(download, directUrl, tempFile, targetFile)
                } else {
                    downloadDirectStream(download, directUrl, tempFile, targetFile)
                }

                if (!isActive) return@launch

                val finalTempSize = tempFile.length()
                if (finalTempSize <= 0L) {
                    if (tempFile.exists()) tempFile.delete()
                    throw Exception("Nenhum byte de vídeo foi recebido do servidor. O arquivo não foi gravado.")
                }

                if (targetFile.exists()) targetFile.delete()
                val moved = tempFile.renameTo(targetFile)
                if (!moved || !targetFile.exists() || targetFile.length() <= 0L) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val confirmedSize = targetFile.length()
                if (confirmedSize <= 0L) {
                    throw Exception("Erro ao salvar arquivo de vídeo final no dispositivo.")
                }

                dao.updateDownloadProgress(
                    id = download.id,
                    status = DownloadEntity.STATUS_COMPLETED,
                    progress = 100,
                    downloadedBytes = confirmedSize,
                    totalBytes = confirmedSize,
                    downloadSpeed = null,
                    localFilePath = targetFile.absolutePath,
                    completedAt = System.currentTimeMillis(),
                    errorMessage = null
                )

                Log.d(TAG, "Download de ${download.title} concluído: ${targetFile.absolutePath} ($confirmedSize bytes)")

                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf("video/mp4")
                    ) { path, uri ->
                        Log.d(TAG, "Indexado no MediaStore: $path -> $uri")
                    }
                } catch (_: Exception) {}

            } catch (e: CancellationException) {
                // Cancelled or paused
            } catch (e: Exception) {
                Log.e(TAG, "Falha no download de ${download.title}: ${e.message}", e)
                val isConnError = !isNetworkAvailable()
                val partialBytes = if (tempFile.exists()) tempFile.length() else 0L

                if (isConnError && partialBytes > 0) {
                    dao.updateDownloadProgress(
                        id = download.id,
                        status = DownloadEntity.STATUS_PAUSED,
                        progress = download.progress,
                        downloadedBytes = partialBytes,
                        totalBytes = download.totalBytes,
                        downloadSpeed = null,
                        localFilePath = targetFile.absolutePath,
                        errorMessage = "Conexão perdida. Download pausado."
                    )
                } else {
                    dao.updateDownloadProgress(
                        id = download.id,
                        status = DownloadEntity.STATUS_ERROR,
                        progress = if (partialBytes > 0) download.progress else 0,
                        downloadedBytes = partialBytes,
                        totalBytes = download.totalBytes,
                        downloadSpeed = null,
                        localFilePath = targetFile.absolutePath,
                        errorMessage = if (isConnError) "Sem conexão com a internet." else (e.message ?: "Falha ao realizar download.")
                    )
                }
            } finally {
                activeDownloadJobs.remove(download.id)
            }
        }

        activeDownloadJobs[download.id] = job
    }

    private suspend fun downloadDirectStream(
        download: DownloadEntity,
        directUrl: String,
        tempFile: File,
        targetFile: File
    ) {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(directUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://embed.playmoz.xyz/")

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        dao.updateDownloadProgress(
            id = download.id,
            status = DownloadEntity.STATUS_DOWNLOADING,
            progress = download.progress,
            downloadedBytes = existingBytes,
            totalBytes = download.totalBytes,
            downloadSpeed = "Iniciando...",
            localFilePath = targetFile.absolutePath,
            errorMessage = null
        )

        var response = okHttpClient.newCall(requestBuilder.build()).execute()

        if (existingBytes > 0 && response.code == 416) {
            response.close()
            tempFile.delete()
            val retryReq = Request.Builder()
                .url(directUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://embed.playmoz.xyz/")
                .build()
            response = okHttpClient.newCall(retryReq).execute()
        }

        if (!response.isSuccessful && response.code != 206) {
            throw Exception("Erro HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Resposta vazia do servidor de vídeo.")
        val contentType = body.contentType()?.toString()?.lowercase() ?: ""
        if (contentType.contains("text/html")) {
            throw Exception("O servidor retornou uma página web em vez do arquivo de vídeo.")
        }

        val responseContentLength = body.contentLength()
        val isRange = response.code == 206 && existingBytes > 0
        val totalLength = if (isRange && responseContentLength > 0) {
            existingBytes + responseContentLength
        } else if (responseContentLength > 0) {
            responseContentLength
        } else {
            0L
        }

        val availableSpace = getAvailableStorageBytes()
        if (totalLength > 0 && availableSpace > 0 && availableSpace < totalLength + (20 * 1024 * 1024L)) {
            throw Exception("Espaço de armazenamento insuficiente no dispositivo.")
        }

        val output = if (isRange) {
            RandomAccessFile(tempFile, "rw").apply { seek(existingBytes) }
        } else {
            RandomAccessFile(tempFile, "rw").apply { setLength(0) }
        }

        val inputStream = body.byteStream()
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var currentDownloaded = if (isRange) existingBytes else 0L

        var lastProgressUpdate = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!currentCoroutineContext().isActive) break

                output.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val timeDiff = now - lastProgressUpdate

                if (timeDiff >= 500L || (totalLength > 0 && currentDownloaded == totalLength)) {
                    val speedKb = (bytesSinceLastUpdate * 1000L) / (timeDiff.coerceAtLeast(1L) * 1024L)
                    val speedStr = if (speedKb >= 1024) {
                        String.format("%.1f MB/s", speedKb / 1024.0)
                    } else {
                        "$speedKb KB/s"
                    }

                    val progressPct = if (totalLength > 0) {
                        ((currentDownloaded * 100L) / totalLength).toInt().coerceIn(0, 99)
                    } else {
                        0
                    }

                    dao.updateDownloadProgress(
                        id = download.id,
                        status = DownloadEntity.STATUS_DOWNLOADING,
                        progress = progressPct,
                        downloadedBytes = currentDownloaded,
                        totalBytes = totalLength,
                        downloadSpeed = speedStr,
                        localFilePath = targetFile.absolutePath,
                        errorMessage = null
                    )

                    lastProgressUpdate = now
                    bytesSinceLastUpdate = 0L
                }
            }
        } finally {
            try { output.close() } catch (_: Exception) {}
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    private suspend fun downloadHlsStream(
        download: DownloadEntity,
        m3u8Url: String,
        tempFile: File,
        targetFile: File
    ) {
        dao.updateDownloadProgress(
            id = download.id,
            status = DownloadEntity.STATUS_DOWNLOADING,
            progress = 0,
            downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L,
            totalBytes = 0L,
            downloadSpeed = "Analisando HLS...",
            localFilePath = targetFile.absolutePath,
            errorMessage = null
        )

        val request = Request.Builder()
            .url(m3u8Url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://embed.playmoz.xyz/")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Erro HTTP ${response.code} ao obter lista HLS.")
        val playlistText = response.body?.string() ?: throw Exception("Lista HLS vazia.")

        var variantUrl = m3u8Url
        var playlistLines = playlistText.lines()

        if (playlistText.contains("#EXT-X-STREAM-INF")) {
            val baseUrl = m3u8Url.substringBeforeLast('/') + "/"
            var bestUrl: String? = null
            for (line in playlistLines) {
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    bestUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        trimmed
                    } else {
                        baseUrl + trimmed
                    }
                    break
                }
            }
            if (bestUrl != null) {
                variantUrl = bestUrl
                val varReq = Request.Builder()
                    .url(variantUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://embed.playmoz.xyz/")
                    .build()
                val varResp = okHttpClient.newCall(varReq).execute()
                if (!varResp.isSuccessful) throw Exception("Erro HTTP ${varResp.code} ao obter variante HLS.")
                val varText = varResp.body?.string() ?: throw Exception("Variante HLS vazia.")
                playlistLines = varText.lines()
            }
        }

        val segmentBaseUrl = variantUrl.substringBeforeLast('/') + "/"
        val segmentUrls = mutableListOf<String>()
        for (line in playlistLines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val fullSegUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    trimmed
                } else {
                    segmentBaseUrl + trimmed
                }
                segmentUrls.add(fullSegUrl)
            }
        }

        if (segmentUrls.isEmpty()) {
            throw Exception("Nenhum segmento de vídeo encontrado na lista HLS.")
        }

        val totalSegments = segmentUrls.size
        var currentDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        val output = RandomAccessFile(tempFile, "rw").apply { seek(currentDownloaded) }

        var lastProgressUpdate = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L

        try {
            for ((index, segUrl) in segmentUrls.withIndex()) {
                if (!currentCoroutineContext().isActive) break

                try {
                    val segReq = Request.Builder()
                        .url(segUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://embed.playmoz.xyz/")
                        .build()

                    val segResp = okHttpClient.newCall(segReq).execute()
                    if (segResp.isSuccessful && segResp.body != null) {
                        val bytes = segResp.body!!.bytes()
                        output.write(bytes)
                        currentDownloaded += bytes.size
                        bytesSinceLastUpdate += bytes.size
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Falha no segmento $index ($segUrl): ${e.message}")
                }

                val now = System.currentTimeMillis()
                val timeDiff = now - lastProgressUpdate
                if (timeDiff >= 500L || index == totalSegments - 1) {
                    val speedKb = (bytesSinceLastUpdate * 1000L) / (timeDiff.coerceAtLeast(1L) * 1024L)
                    val speedStr = if (speedKb >= 1024) {
                        String.format("%.1f MB/s", speedKb / 1024.0)
                    } else {
                        "$speedKb KB/s"
                    }

                    val progressPct = (((index + 1) * 100) / totalSegments).coerceIn(0, 99)

                    dao.updateDownloadProgress(
                        id = download.id,
                        status = DownloadEntity.STATUS_DOWNLOADING,
                        progress = progressPct,
                        downloadedBytes = currentDownloaded,
                        totalBytes = 0L,
                        downloadSpeed = speedStr,
                        localFilePath = targetFile.absolutePath,
                        errorMessage = null
                    )

                    lastProgressUpdate = now
                    bytesSinceLastUpdate = 0L
                }
            }
        } finally {
            try { output.close() } catch (_: Exception) {}
        }
    }
}
