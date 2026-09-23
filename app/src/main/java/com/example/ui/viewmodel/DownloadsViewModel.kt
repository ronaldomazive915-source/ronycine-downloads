package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.local.DownloadTask
import com.example.util.AppDownloadManager
import kotlinx.coroutines.flow.StateFlow

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = AppDownloadManager.getInstance(application)
    val downloads: StateFlow<List<DownloadTask>> = downloadManager.allDownloads

    fun pauseDownload(id: String) {
        downloadManager.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        downloadManager.resumeDownload(id)
    }

    fun cancelDownload(id: String) {
        downloadManager.cancelDownload(id)
    }

    fun retryDownload(id: String) {
        downloadManager.retryDownload(id)
    }

    fun deleteDownload(task: DownloadTask) {
        downloadManager.deleteDownload(task)
    }
}
