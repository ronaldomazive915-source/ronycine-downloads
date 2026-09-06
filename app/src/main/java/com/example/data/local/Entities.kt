package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "my_list")
data class MyListEntity(
    @PrimaryKey val tmdbId: Int,
    val mediaType: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val progressPercent: Float = 0f,
    val lastWatchedPositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

data class FeaturedMediaItem(
    val featured: FeaturedMediaEntity,
    val media: MediaEntity
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user: String,
    val action: String,
    val target: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "EXPORT" or "RESTORE"
    val itemCount: Int,
    val status: String, // "SUCCESS", "FAILED"
    val result: String = "" // e.g. "245 filmes, 82 séries"
)

@Entity(tableName = "tmdb_auto_sync_history")
data class TmdbAutoSyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "Automática", // "Automática" or "Manual"
    val totalFound: Int = 0,
    val newMoviesFound: Int = 0,
    val newSeriesFound: Int = 0,
    val importedCount: Int = 0,
    val existingCount: Int = 0,
    val ignoredCount: Int = 0,
    val errorCount: Int = 0,
    val durationSeconds: Long = 0,
    val status: String = "Concluído", // "Concluído", "Parcial", "Erro", "Cancelado"
    val details: String = ""
)


