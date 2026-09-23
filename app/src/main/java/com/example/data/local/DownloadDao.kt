package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadTask?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadFlowById(id: String): Flow<DownloadTask?>

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getDownloadsByTmdbId(tmdbId: String): Flow<List<DownloadTask>>

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND (:seasonNumber IS NULL OR seasonNumber = :seasonNumber) AND (:episodeNumber IS NULL OR episodeNumber = :episodeNumber) LIMIT 1")
    suspend fun findExistingDownload(tmdbId: String, seasonNumber: Int?, episodeNumber: Int?): DownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: DownloadTask)

    @Query("UPDATE downloads SET downloadedSize = :downloadedSize, totalSize = :totalSize, speed = :speed, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, downloadedSize: Long, totalSize: Long, speed: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null)

    @Query("UPDATE downloads SET status = :status, localPath = :localPath, totalSize = :totalSize, downloadedSize = :totalSize, speed = 0, errorMessage = null WHERE id = :id")
    suspend fun markCompleted(id: String, localPath: String, totalSize: Long, status: DownloadStatus = DownloadStatus.COMPLETED)

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun getQueuedDownloads(): List<DownloadTask>

    @Delete
    suspend fun delete(task: DownloadTask)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}
