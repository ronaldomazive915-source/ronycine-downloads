package com.example.data.remote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceEntity(
    val deviceId: String = "",
    val name: String = "",
    val platform: String = "Android",
    val appVersion: String = "1.5.0",
    val buildNumber: Int = 105,
    val model: String = "",
    val osVersion: String = "",
    val firstSeen: Long = 0L,
    val firstConnection: Long = 0L,
    val lastSeen: Long = 0L,
    val lastActivity: Long = 0L,
    val status: String = "online",
    val isBlocked: Boolean = false,
    val adminAccess: Boolean = false,
    val accessStatus: String = "ACTIVE", // "ACTIVE", "SUSPENDED", "BANNED"
    val fcmToken: String = "",
    val fcmStatus: String = "INDISPONÍVEL", // "DISPONÍVEL", "INDISPONÍVEL"
    val notificationsPermission: String = "NEGADA", // "CONCEDIDA", "NEGADA"
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    // Timestamp efetivo do primeiro acesso (compatibilidade retroativa)
    val effectiveFirstSeen: Long
        get() = when {
            firstSeen > 0L -> firstSeen
            createdAt > 0L -> createdAt
            firstConnection > 0L -> firstConnection
            else -> 0L
        }

    // Timestamp efetivo do último acesso (compatibilidade retroativa)
    val effectiveLastSeen: Long
        get() = when {
            lastSeen > 0L -> lastSeen
            updatedAt > 0L -> updatedAt
            lastActivity > 0L -> lastActivity
            else -> 0L
        }

    // Considerado ONLINE se enviou heartbeat/acesso nos últimos 5 minutos (300 segundos)
    val isOnline: Boolean
        get() = !isBlocked && accessStatus == "ACTIVE" && (System.currentTimeMillis() - effectiveLastSeen) < 5 * 60 * 1000L

    val isAdmin: Boolean
        get() = adminAccess && accessStatus == "ACTIVE" && !isBlocked

    val lastActivityFormatted: String
        get() = formatDateTime(effectiveLastSeen)

    val firstConnectionFormatted: String
        get() = formatDateTime(effectiveFirstSeen)

    val maskedDeviceId: String
        get() = if (deviceId.length > 12) {
            "${deviceId.take(6)}...${deviceId.takeLast(4)}"
        } else {
            deviceId
        }

    private fun formatDateTime(millis: Long): String {
        if (millis == 0L) return "Desconhecida"
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
        return formatter.format(Date(millis))
    }
}


