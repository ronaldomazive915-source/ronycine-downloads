package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadEntity(
    @PrimaryKey
    val id: String, // e.g., "movie_12345" or "tv_12345_1_2"
    val tmdbId: Int,
    val mediaType: String, // "movie" or "tv"
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val status: String = STATUS_PENDING, // PENDING, PREPARING, DOWNLOADING, PAUSED, COMPLETED, ERROR, NOT_SUPPORTED
    val progress: Int = 0, // 0 to 100
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeed: String? = null,
    val localFilePath: String? = null,
    val videoUrl: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_ERROR = "ERROR"
        const val STATUS_NOT_SUPPORTED = "NOT_SUPPORTED"

        fun generateId(tmdbId: Int, mediaType: String, seasonNumber: Int? = null, episodeNumber: Int? = null): String {
            return if (mediaType == "tv" || mediaType == "serie" || (seasonNumber != null && episodeNumber != null)) {
                "tv_${tmdbId}_${seasonNumber ?: 1}_${episodeNumber ?: 1}"
            } else {
                "movie_$tmdbId"
            }
        }
    }
}
