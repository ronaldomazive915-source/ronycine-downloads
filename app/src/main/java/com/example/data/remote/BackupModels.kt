package com.example.data.remote

import android.net.Uri
import com.squareup.moshi.JsonClass
import com.example.data.local.*

@JsonClass(generateAdapter = true)
data class BackupData(
    val version: Int = 100,
    val appVersion: String = "1.0",
    val timestamp: Long = System.currentTimeMillis(),
    val movies: List<MediaEntity> = emptyList(),
    val series: List<MediaEntity> = emptyList(),
    val episodes: List<EpisodeEntity> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
    val featured: List<FeaturedMediaEntity> = emptyList(),
    val megaEmbedConfig: MegaEmbedConfig? = null,
    val catalogVersion: Long = 0
)

@JsonClass(generateAdapter = true)
data class BackupHistoryItem(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "EXPORT" or "RESTORE"
    val itemCount: Int,
    val status: String, // "SUCCESS", "FAILED"
    val details: String = ""
)

enum class RestoreStatus {
    IDLE,
    VALIDATING,
    PREPARING,
    RESTORING,
    PAUSED,
    CANCELLED,
    COMPLETED,
    ERROR
}

data class BackupPreviewInfo(
    val uri: Uri,
    val fileName: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val isZip: Boolean,
    val version: Int = 100,
    val timestamp: Long = System.currentTimeMillis(),
    val moviesCount: Int = 0,
    val seriesCount: Int = 0,
    val episodesCount: Int = 0,
    val channelsCount: Int = 0,
    val featuredCount: Int = 0,
    val hasConfig: Boolean = false,
    val isValid: Boolean = true,
    val errorMessage: String? = null
)

data class RestoreRealProgress(
    val status: RestoreStatus = RestoreStatus.IDLE,
    val progress: Float = 0f,
    val percentage: Int = 0,
    val currentPhase: String = "",
    val currentItemTitle: String = "",
    val moviesRestored: Int = 0,
    val moviesTotal: Int = 0,
    val seriesRestored: Int = 0,
    val seriesTotal: Int = 0,
    val episodesRestored: Int = 0,
    val episodesTotal: Int = 0,
    val channelsRestored: Int = 0,
    val channelsTotal: Int = 0,
    val featuredRestored: Int = 0,
    val featuredTotal: Int = 0,
    val itemsIgnored: Int = 0,
    val errorsCount: Int = 0,
    val errorMessage: String? = null
)
