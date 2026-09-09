package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "episodes",
    primaryKeys = ["mediaTmdbId", "seasonNumber", "episodeNumber"]
)
data class EpisodeEntity(
    val mediaTmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val duration: String = "45 min",
    val stillPath: String? = null,
    val videoUrl: String? = null,
    val restricted18: Boolean = false
)
