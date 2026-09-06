package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val type: String = "AVISO", // AVISO, NOVO_FILME, NOVA_SERIE, EM_ALTA, ATUALIZACAO, MANUTENCAO, INFORMACAO
    val timestamp: Long = System.currentTimeMillis(),
    val scheduledAt: Long = 0L,
    val buttonText: String? = null,
    val actionUrl: String? = null, // e.g. "media/12345" or "tv/channel_id" or URL
    val targetSegment: String = "ALL", // ALL, MOVIES, SERIES
    val isActive: Boolean = true,
    val isRead: Boolean = false,
    val isSent: Boolean = true
)
