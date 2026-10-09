package com.example.data.remote

import com.example.data.local.NotificationEntity
import java.text.SimpleDateFormat
import java.util.*

enum class AppNotificationType(val label: String, val icon: String) {
    MOVIE("Filme", "🎬"),
    SERIE("Série", "📺"),
    EPISODE("Episódio", "🎞️"),
    BULK("Pacote", "📦"),
    ANNOUNCEMENT("Aviso", "📢"),
    UPDATE("Atualização", "🚀"),
    MAINTENANCE("Manutenção", "🛠️"),
    SYSTEM("Sistema", "⚙️")
}

data class AppNotification(
    val id: String = "",
    val type: String = "ANNOUNCEMENT",
    val title: String = "",
    val message: String = "",
    val poster: String? = null,
    val tmdbId: Int? = null,
    val mediaType: String? = null, // movie, tv
    val season: Int? = null,
    val episode: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val actionUrl: String? = null,
    val isGlobal: Boolean = true,
    val targetSegment: String = "ALL", // ALL, MOVIES, SERIES, ADMIN
    val metadata: Map<String, String> = emptyMap()
) {
    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            return sdf.format(Date(timestamp))
        }

    fun toLocalEntity(isRead: Boolean = false): NotificationEntity {
        return NotificationEntity(
            id = id,
            title = title,
            message = message,
            imageUrl = poster,
            type = type,
            timestamp = timestamp,
            actionUrl = actionUrl,
            targetSegment = targetSegment,
            isRead = isRead
        )
    }
}

data class UserNotificationState(
    val notificationId: String = "",
    val userId: String = "",
    val read: Boolean = false,
    val readAt: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class NotificationSettings(
    val newMoviesEnabled: Boolean = true,
    val newSeriesEnabled: Boolean = true,
    val newEpisodesEnabled: Boolean = true,
    val generalAnnouncementsEnabled: Boolean = true,
    val systemAlertsEnabled: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val showBadgeEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class AdminNotificationStats(
    val todaySent: Int = 0,
    val todayDelivered: Int = 0,
    val todayFailed: Int = 0,
    val totalDevices: Int = 0,
    val activeUsers: Int = 0,
    val lastNotificationAt: Long = 0L,
    val lastUpdateAt: Long = System.currentTimeMillis()
)

data class NotificationHistoryItem(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val sentAt: Long = 0L,
    val targetCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val status: String = "SUCCESS" // SUCCESS, FAILED, PARTIAL
)

data class AutoNotificationConfig(
    val enabled: Boolean = true,
    val notifyNewMovies: Boolean = true,
    val notifyNewSeries: Boolean = true,
    val notifyNewEpisodes: Boolean = true,
    val notifyAppUpdates: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
