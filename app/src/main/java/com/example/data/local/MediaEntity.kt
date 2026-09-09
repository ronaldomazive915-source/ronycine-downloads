package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "media_catalog",
    indices = [Index(value = ["tmdbId", "mediaType"], unique = true)]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String = "",
    val mediaType: String, // "movie" or "tv"
    val mediaCategory: String = "", // "anime", "dorama", "movie", "series", "live_tv"
    val originalLanguage: String = "",
    val originCountry: String = "",
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String,
    val releaseYear: String,
    val rating: Double,
    val genres: String, // comma separated
    val durationMinutes: Int = 120,
    val cast: String = "",
    val director: String = "",
    val seasonsCount: Int = 1,
    val episodesCount: Int = 1,
    val trailerKey: String? = null,
    val isHeroFeatured: Boolean = false,
    val restricted18: Boolean = false,
    val restricted18UpdatedAt: Long = 0L,
    val restricted18UpdatedBy: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

data class DetailedCastMember(
    val name: String,
    val character: String = "",
    val profileUrl: String? = null
)

fun parseCastList(rawCast: String): List<DetailedCastMember> {
    if (rawCast.isBlank()) return emptyList()

    val items = if (rawCast.contains(";")) {
        rawCast.split(";")
    } else {
        rawCast.split(",")
    }

    return items.mapNotNull { raw ->
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("Atores Principais", ignoreCase = true)) return@mapNotNull null

        if (trimmed.contains("|")) {
            val parts = trimmed.split("|")
            val name = parts.getOrNull(0)?.trim() ?: ""
            val character = parts.getOrNull(1)?.trim() ?: ""
            val rawPath = parts.getOrNull(2)?.trim()?.ifBlank { null }

            val profileUrl = when {
                rawPath == null -> null
                rawPath.startsWith("http") -> rawPath
                rawPath.startsWith("/") -> "https://image.tmdb.org/t/p/w185$rawPath"
                else -> "https://image.tmdb.org/t/p/w185/$rawPath"
            }

            if (name.isNotBlank()) DetailedCastMember(name, character, profileUrl) else null
        } else {
            DetailedCastMember(trimmed)
        }
    }.distinctBy { (it.name.trim() + "_" + it.character.trim()).lowercase() }
}
