package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AppDownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getInstance(context)
    val repository = DownloadRepository(database.downloadDao())

    val allDownloads: StateFlow<List<DownloadTask>> = repository.allDownloads.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    companion object {
        private const val TAG = "AppDownloadManager"

        @Volatile
        private var INSTANCE: AppDownloadManager? = null

        fun getInstance(context: Context): AppDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun generateTaskId(mediaType: String, tmdbId: String, season: Int? = null, episode: Int? = null): String {
            val isTv = mediaType.equals("tv", ignoreCase = true) || 
                       mediaType.equals("serie", ignoreCase = true) || 
                       mediaType.equals("series", ignoreCase = true)
            return if (isTv && season != null && episode != null) {
                "series:$tmdbId:s$season:e$episode"
            } else {
                "movie:$tmdbId"
            }
        }
    }

    suspend fun getExistingTask(tmdbId: String, season: Int? = null, episode: Int? = null): DownloadTask? {
        return repository.findExistingDownload(tmdbId, season, episode)
    }

    fun startDownload(task: DownloadTask) {
        scope.launch {
            Log.d(TAG, "Starting download for task: ${task.id} (${task.title})")
            val existing = repository.getDownloadById(task.id)
            if (existing == null) {
                repository.insertOrUpdate(task.copy(status = DownloadStatus.QUEUED))
            } else if (existing.status == DownloadStatus.ERROR || existing.status == DownloadStatus.CANCELED) {
                repository.insertOrUpdate(existing.copy(
                    status = DownloadStatus.QUEUED,
                    errorMessage = null,
                    speed = 0
                ))
            }

            sendCommandToService(DownloadService.ACTION_START, task.id)
        }
    }

    fun pauseDownload(id: String) {
        scope.launch {
            Log.d(TAG, "Pausing download: $id")
            repository.updateStatus(id, DownloadStatus.PAUSED)
            sendCommandToService(DownloadService.ACTION_PAUSE, id)
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            Log.d(TAG, "Resuming download: $id")
            repository.updateStatus(id, DownloadStatus.QUEUED)
            sendCommandToService(DownloadService.ACTION_RESUME, id)
        }
    }

    fun cancelDownload(id: String, deleteFile: Boolean = true) {
        scope.launch {
            Log.d(TAG, "Cancelling download: $id")
            sendCommandToService(DownloadService.ACTION_CANCEL, id)
            val task = repository.getDownloadById(id)
            if (task != null) {
                if (deleteFile) {
                    deleteTaskFile(task)
                    repository.deleteById(id)
                } else {
                    repository.updateStatus(id, DownloadStatus.CANCELED)
                }
            }
        }
    }

    fun retryDownload(id: String) {
        scope.launch {
            Log.d(TAG, "Retrying download: $id")
            val task = repository.getDownloadById(id) ?: return@launch
            repository.updateStatus(id, DownloadStatus.QUEUED, errorMessage = null)
            sendCommandToService(DownloadService.ACTION_START, id)
        }
    }

    fun deleteDownload(task: DownloadTask) {
        scope.launch {
            Log.d(TAG, "Deleting download task: ${task.id}")
            sendCommandToService(DownloadService.ACTION_CANCEL, task.id)
            deleteTaskFile(task)
            repository.delete(task)
        }
    }

    private fun deleteTaskFile(task: DownloadTask) {
        try {
            if (!task.localPath.isNullOrBlank()) {
                val file = File(task.localPath)
                if (file.exists()) file.delete()
            }
            val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
            val candidate = File(downloadsDir, task.fileName)
            if (candidate.exists()) candidate.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting task file: ${e.message}")
        }
    }

    private fun sendCommandToService(action: String, downloadId: String) {
        try {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command $action to DownloadService: ${e.message}")
        }
    }
}
