package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class CalendarItemDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "episode") val episode: String? = null,
    @Json(name = "season") val season: Int? = null,
    @Json(name = "number") val number: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "type") val type: Int? = null,
    @Json(name = "tmdb_id") val tmdbId: String? = null,
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "backdrop") val backdrop: String? = null,
    @Json(name = "status") val status: String? = null
)

enum class CalendarMediaType(val rawType: Int, val label: String, val routeType: String) {
    MOVIE(1, "FILME", "movie"),
    SERIES(2, "SÉRIE", "tv"),
    ANIME(3, "ANIME", "anime"),
    DORAMA(5, "DORAMA", "dorama");

    companion object {
        fun fromType(rawType: Int?, title: String? = null, season: Int? = null): CalendarMediaType {
            return when (rawType) {
                1 -> MOVIE
                2 -> SERIES
                3 -> ANIME
                4, 5 -> DORAMA
                else -> {
                    // Fallback inferência inteligente
                    val t = title?.lowercase() ?: ""
                    if (t.contains("anime") || t.contains("naruto") || t.contains("one piece") || t.contains("dragon ball")) {
                        ANIME
                    } else if (t.contains("dorama") || t.contains("drama coreano") || t.contains("k-drama")) {
                        DORAMA
                    } else if (season != null && season > 0) {
                        SERIES
                    } else {
                        MOVIE
                    }
                }
            }
        }
    }
}

enum class CalendarFilter(val displayName: String) {
    TODOS("Todos"),
    FILMES("Filmes"),
    SERIES("Séries"),
    ANIMES("Animes"),
    DORAMAS("Doramas")
}

enum class CalendarDateRange(val displayName: String) {
    TODOS("Todas as Datas"),
    HOJE("Hoje"),
    AMANHA("Amanhã"),
    ESTA_SEMANA("Esta Semana"),
    PROXIMA_SEMANA("Próxima Semana"),
    PROXIMO_MES("Próximo Mês")
}

data class CalendarItem(
    val id: String,
    val title: String,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val airDate: String, // YYYY-MM-DD
    val mediaType: CalendarMediaType,
    val tmdbId: Int?,
    val imdbId: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val status: String?,
    val sources: List<String> = emptyList()
) {
    val formattedEpisode: String?
        get() {
            return when {
                mediaType == CalendarMediaType.MOVIE -> null
                mediaType == CalendarMediaType.ANIME -> {
                    if (episodeNumber != null && episodeNumber > 0) "EP $episodeNumber"
                    else if (seasonNumber != null && seasonNumber > 0) "T$seasonNumber"
                    else null
                }
                seasonNumber != null && episodeNumber != null -> {
                    "S%02d • E%02d".format(seasonNumber, episodeNumber)
                }
                seasonNumber != null -> "T$seasonNumber"
                episodeNumber != null -> "EP $episodeNumber"
                else -> null
            }
        }

    val displayDateFormatted: String
        get() {
            if (airDate.isBlank()) return ""
            return try {
                val parts = airDate.split("-")
                if (parts.size == 3) {
                    val day = parts[2]
                    val month = parts[1]
                    "$day/$month"
                } else airDate
            } catch (_: Exception) {
                airDate
            }
        }

    fun getComputedStatus(todayStr: String, tomorrowStr: String): String {
        if (airDate.isBlank()) return status?.uppercase() ?: "EM BREVE"
        return when {
            airDate < todayStr -> "✓ JÁ DISPONÍVEL"
            airDate == todayStr -> "● DISPONÍVEL HOJE"
            airDate == tomorrowStr -> "AMANHÃ"
            else -> {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val targetDate = sdf.parse(airDate)
                    val currentDate = sdf.parse(todayStr)
                    if (targetDate != null && currentDate != null) {
                        val diffMillis = targetDate.time - currentDate.time
                        val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
                        if (diffDays in 2..6) {
                            "EM $diffDays DIAS"
                        } else {
                            val shortFmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale("pt", "BR"))
                            "LANÇA EM ${shortFmt.format(targetDate).replace(".", "").uppercase()}"
                        }
                    } else {
                        "EM BREVE"
                    }
                } catch (_: Exception) {
                    "EM BREVE"
                }
            }
        }
    }
}

data class MediaReleaseInfo(
    val tmdbId: Int?,
    val imdbId: String? = null,
    val title: String,
    val rawAirDate: String, // YYYY-MM-DD
    val formattedReleaseDate: String, // "15 DE JANEIRO DE 2026"
    val formattedShortDate: String, // "25 SET"
    val isMovie: Boolean,
    val isNextEpisode: Boolean,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val isUnreleasedContent: Boolean = false
) {
    val formattedEpisodeLabel: String?
        get() = when {
            seasonNumber != null && episodeNumber != null -> "S%02d • E%02d".format(seasonNumber, episodeNumber)
            episodeNumber != null -> "EP $episodeNumber"
            seasonNumber != null -> "T$seasonNumber"
            else -> null
        }
}

interface CalendarApiService {
    @GET("calendario.php")
    suspend fun getCalendar(): List<CalendarItemDto>
}
