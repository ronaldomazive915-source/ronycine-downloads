package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadTask(
    @PrimaryKey val id: String, // "movie_{tmdbId}" ou "tv_{tmdbId}_s{season}_e{episode}"
    val title: String,
    val subTitle: String?, // Temporada/Episódio ou Ano
    val posterPath: String?,
    val url: String,
    val fileName: String,
    val localPath: String? = null,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
    val mediaType: String, // "movie" ou "tv"
    val tmdbId: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val mimeType: String? = null,
    val speed: Long = 0L, // bytes por segundo
    val errorMessage: String? = null
)

enum class DownloadStatus {
    IDLE,
    QUEUED,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
    CANCELED
}
