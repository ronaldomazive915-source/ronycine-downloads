package com.example.data.remote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UpdateControlEntity(
    val enabled: Boolean = false,
    val activeVersionCode: Int? = null,
    val activeVersionName: String? = null,
    val mandatory: Boolean = false,
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val sha256: String = "",
    val fileSize: String = "",
    val activatedAt: Long? = null,
    val activatedBy: String? = null,
    val deactivatedAt: Long? = null,
    val deactivatedBy: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val dateActivatedFormatted: String
        get() {
            val ts = activatedAt ?: return "N/A"
            if (ts <= 0L) return "N/A"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(ts))
        }

    val dateDeactivatedFormatted: String
        get() {
            val ts = deactivatedAt ?: return "N/A"
            if (ts <= 0L) return "N/A"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(ts))
        }
}

data class AppVersionEntity(
    val id: String = "",
    val versionName: String = "1.5.0",
    val versionCode: Int = 105,
    val packageName: String = "com.aistudio.playfilmeplus.app",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val minimumVersionCode: Int = 100,
    val mandatory: Boolean = false,
    val published: Boolean = true,
    val status: String = "PUBLISHED", // DRAFT, PUBLISHED, DISABLED, ARCHIVED, DEPRECATED
    val createdAt: Long = System.currentTimeMillis(),
    val publishedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sha256: String = "",
    val fileSize: String = "",
    val updatedDevicesCount: Int = 0,
    val pendingDevicesCount: Int = 0,
    val failedDevicesCount: Int = 0
) {
    val isPublished: Boolean get() = status.equals("PUBLISHED", ignoreCase = true) && published
    val isDraft: Boolean get() = status.equals("DRAFT", ignoreCase = true) || !published
    val isDisabled: Boolean get() = status.equals("DISABLED", ignoreCase = true)
    val isArchived: Boolean get() = status.equals("ARCHIVED", ignoreCase = true) || status.equals("DEPRECATED", ignoreCase = true)
    val isDeprecated: Boolean get() = isArchived
    val hasConfiguredApk: Boolean get() = apkUrl.isNotBlank() && apkUrl.startsWith("https://", ignoreCase = true)

    val displayStatus: String
        get() = when {
            isDraft -> "Rascunho"
            isPublished -> "Publicada"
            isDisabled -> "Desativada"
            isArchived -> "Arquivada"
            else -> status
        }

    val dateFormatted: String
        get() {
            val ts = if (publishedAt > 0L) publishedAt else createdAt
            if (ts <= 0L) return "Sem data"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(ts))
        }

    val dateShortFormatted: String
        get() {
            val ts = if (publishedAt > 0L) publishedAt else createdAt
            if (ts <= 0L) return "Sem data"
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            return sdf.format(Date(ts))
        }
}

data class UpdateEventEntity(
    val id: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val fromVersion: String = "",
    val toVersion: String = "",
    val status: String = "PENDING", // PENDING, NOTIFIED, DOWNLOADING, DOWNLOADED, INSTALLING, UPDATED, FAILED, CANCELLED
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val error: String? = null,
    val trigger: String = "admin", // admin, auto, fcm
    val adminId: String? = "admin@ronycine.app"
) {
    val dateFormatted: String
        get() {
            if (startedAt <= 0L) return "Sem data"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
            return sdf.format(Date(startedAt))
        }
}

data class RemoteConfigEntity(
    val appName: String = "RONYCINE",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Estamos realizando uma atualização no sistema. Tente novamente em breve.",
    val allowedVersionsDuringMaintenance: List<Int> = emptyList(),
    val minimumVersionCode: Int = 100,
    val latestVersionCode: Int = 105,
    val latestVersionName: String = "1.5.0",
    val latestApkUrl: String = "",
    val latestReleaseNotes: String = "",
    val enableDownloads: Boolean = true,
    val enableRequests: Boolean = true,
    val enableLiveTV: Boolean = true,
    val enableTrailers: Boolean = true,
    val announcement: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class AdminAuditLogEntity(
    val id: String = "",
    val adminId: String = "admin@ronycine.app",
    val adminEmail: String = "admin@ronycine.app",
    val action: String = "",
    val targetDeviceId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val result: String = "SUCESSO",
    val details: String = ""
) {
    val dateFormatted: String
        get() {
            if (timestamp <= 0L) return "Sem data"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
            return sdf.format(Date(timestamp))
        }
}

data class UpdateOperationEntity(
    val id: String = "",
    val targetVersionCode: Int = 0,
    val targetVersionName: String = "",
    val createdBy: String = "admin@ronycine.app",
    val createdAt: Long = System.currentTimeMillis(),
    val totalDevices: Int = 0,
    val pendingCount: Int = 0,
    val notifiedCount: Int = 0,
    val downloadingCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
    val status: String = "RUNNING" // "RUNNING", "COMPLETED", "CANCELLED"
) {
    val progressPercent: Int
        get() = if (totalDevices > 0) ((successCount + failedCount) * 100) / totalDevices else 0

    val dateFormatted: String
        get() {
            if (createdAt <= 0L) return "Sem data"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
            return sdf.format(Date(createdAt))
        }
}

data class Top10ItemEntity(
    val rank: Int = 1,
    val tmdbId: Int = 0,
    val mediaType: String = "movie",
    val title: String = "", // Opcional, para exibição rápida no admin
    val posterPath: String? = null // Opcional
)

data class Top10ConfigEntity(
    val mode: String = "manual", // "manual" ou "automatic"
    val enabled: Boolean = true,
    val autoUpdateIntervalHours: Int = 12,
    val items: List<Top10ItemEntity> = emptyList(),
    val lastGeneratedAt: Long = 0L,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "admin@ronycine.app",
    val source: String = "admin", // "admin" ou "tmdb"
    val version: Long = 1L
)

data class FeaturedItemConfigEntity(
    val id: String = "",
    val tmdbId: Int = 0,
    val mediaType: String = "movie", // "movie" ou "tv"
    val title: String = "",
    val originalTitle: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double = 0.0,
    val releaseYear: Int = 0,
    val overview: String = "",
    val genres: String = "",
    val trailerUrl: String = "",
    val autoPlayTrailer: Boolean = true,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
) {
    val uniqueKey: String get() = "${tmdbId}_$mediaType"

    fun toMediaEntity(): com.example.data.local.MediaEntity {
        val cleanTrailerKey = when {
            trailerUrl.contains("v=") -> trailerUrl.substringAfter("v=").substringBefore("&")
            trailerUrl.contains("youtu.be/") -> trailerUrl.substringAfter("youtu.be/").substringBefore("?")
            trailerUrl.isNotBlank() && !trailerUrl.contains("/") -> trailerUrl
            else -> null
        }
        return com.example.data.local.MediaEntity(
            id = 0,
            tmdbId = tmdbId,
            title = title,
            originalTitle = originalTitle,
            mediaType = mediaType,
            posterPath = posterPath,
            backdropPath = backdropPath,
            overview = overview,
            releaseYear = if (releaseYear > 0) releaseYear.toString() else "",
            rating = rating,
            genres = genres,
            trailerKey = cleanTrailerKey,
            isHeroFeatured = true
        )
    }
}

data class FeaturedConfigEntity(
    val mode: String = "automatic", // "manual" ou "automatic"
    val source: String = "TMDB", // "TMDB" ou "MANUAL"
    val period: String = "today", // "today", "week", "popular", "top_rated", "trending"
    val mediaType: String = "all", // "all" (Filmes + Séries), "movie" (Somente Filmes), "tv" (Somente Séries)
    val limit: Int = 10, // 5, 10, 15, 20
    val autoUpdateIntervalHours: Int = 6, // 0 (desativado), 1, 6, 12, 24
    val autoPublish: Boolean = true,
    val autoImportToCatalog: Boolean = false,
    val minRating: Double = 0.0, // 0.0 (sem filtro), 6.0, 7.0, 8.0
    val selectedGenre: String = "", // "" para todos
    val minYear: Int = 0, // 0 para sem filtro
    val excludedTmdbKeys: List<String> = emptyList(), // lista de "tmdbId_mediaType" excluídos
    val useInHeroBanner: Boolean = true,
    val items: List<FeaturedItemConfigEntity> = emptyList(),
    val previewItems: List<FeaturedItemConfigEntity> = emptyList(),
    val lastKnownGoodItems: List<FeaturedItemConfigEntity> = emptyList(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val nextUpdateAt: Long = 0L,
    val lastSyncStatus: String = "SUCCESS", // "SUCCESS", "ERROR", "UPDATING"
    val lastSyncError: String = "",
    val updatedBy: String = "admin@ronycine.app",
    val version: Long = 1L
) {
    val lastUpdatedFormatted: String
        get() {
            if (lastUpdatedAt <= 0L) return "Nunca"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(lastUpdatedAt))
        }

    val nextUpdateFormatted: String
        get() {
            if (autoUpdateIntervalHours <= 0) return "Desativada"
            if (nextUpdateAt <= 0L) {
                val next = lastUpdatedAt + (autoUpdateIntervalHours * 3600 * 1000L)
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                return sdf.format(Date(next))
            }
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(nextUpdateAt))
        }

    val periodDisplay: String
        get() = when (period) {
            "today" -> "HOJE"
            "week" -> "ESTA SEMANA"
            "popular" -> "MAIS POPULARES"
            "top_rated" -> "MELHOR AVALIADOS"
            "trending" -> "EM ALTA"
            else -> period.uppercase()
        }

    val mediaTypeDisplay: String
        get() = when (mediaType) {
            "movie" -> "FILMES"
            "tv" -> "SÉRIES"
            else -> "FILMES + SÉRIES"
        }

    val isAutomatic: Boolean get() = mode.equals("automatic", ignoreCase = true)
}

data class FeaturedHistoryEntity(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "TMDB",
    val mode: String = "automatic",
    val period: String = "today",
    val count: Int = 0,
    val itemsSummary: List<String> = emptyList(),
    val addedItemsCount: Int = 0,
    val removedItemsCount: Int = 0,
    val triggeredBy: String = "admin" // "admin", "schedule", "manual"
) {
    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(timestamp))
        }
}

object VersionFieldNormalizer {
    private val CANDIDATE_KEYS = listOf(
        "apkUrl",
        "downloadUrl",
        "apkURL",
        "apk_url",
        "url",
        "downloadURL",
        "latestApkUrl"
    )

    fun extractApkUrl(data: Map<String, Any?>?): String {
        if (data == null) return ""
        for (key in CANDIDATE_KEYS) {
            val value = data[key]?.toString()?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return ""
    }
}
