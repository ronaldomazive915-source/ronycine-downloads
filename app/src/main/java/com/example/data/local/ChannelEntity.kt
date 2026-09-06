package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "live_channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val logoUrl: String,
    val streamUrl: String,
    val isOnline: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
