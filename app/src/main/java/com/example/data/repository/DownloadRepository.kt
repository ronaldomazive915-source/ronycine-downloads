package com.example.data.repository

import com.example.data.local.DownloadDao
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {

    val allDownloads: Flow<List<DownloadTask>> = downloadDao.getAllDownloads()

    suspend fun getDownloadById(id: String): DownloadTask? {
        return downloadDao.getDownloadById(id)
    }

    fun getDownloadFlowById(id: String): Flow<DownloadTask?> {
        return downloadDao.getDownloadFlowById(id)
    }

    fun getDownloadsByTmdbId(tmdbId: String): Flow<List<DownloadTask>> {
        return downloadDao.getDownloadsByTmdbId(tmdbId)
    }

    suspend fun findExistingDownload(tmdbId: String, seasonNumber: Int? = null, episodeNumber: Int? = null): DownloadTask? {
        return downloadDao.findExistingDownload(tmdbId, seasonNumber, episodeNumber)
    }

    suspend fun insertOrUpdate(task: DownloadTask) {
        downloadDao.insertOrUpdate(task)
    }

    suspend fun updateProgress(id: String, downloadedSize: Long, totalSize: Long, speed: Long, status: DownloadStatus) {
        downloadDao.updateProgress(id, downloadedSize, totalSize, speed, status)
    }

    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null) {
        downloadDao.updateStatus(id, status, errorMessage)
    }

    suspend fun markCompleted(id: String, localPath: String, totalSize: Long) {
        downloadDao.markCompleted(id, localPath, totalSize, DownloadStatus.COMPLETED)
    }

    suspend fun getQueuedDownloads(): List<DownloadTask> {
        return downloadDao.getQueuedDownloads()
    }

    suspend fun delete(task: DownloadTask) {
        downloadDao.delete(task)
    }
    
    suspend fun deleteById(id: String) {
        downloadDao.deleteById(id)
    }
}
