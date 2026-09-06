package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "featured_media",
    indices = [Index(value = ["mediaTmdbId"], unique = true)]
)
data class FeaturedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaTmdbId: Int,
    val mediaType: String, // "movie" or "tv"
    val trailerUrl: String = "",
    val autoPlayTrailer: Boolean = true,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
