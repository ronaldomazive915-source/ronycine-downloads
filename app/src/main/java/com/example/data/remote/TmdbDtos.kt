package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbPageResponse<T>(
    @Json(name = "page") val page: Int,
    @Json(name = "results") val results: List<T>,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int
)

@JsonClass(generateAdapter = true)
data class TmdbMediaDto(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "origin_country") val originCountry: List<String>? = emptyList(),
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "overview") val overview: String? = "",
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = 0.0,
    @Json(name = "genre_ids") val genreIds: List<Int>? = emptyList(),
    @Json(name = "genres") val genres: List<TmdbGenreDto>? = emptyList(),
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "videos") val videos: TmdbVideosResponse? = null,
    @Json(name = "credits") val credits: TmdbCreditsResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(
    @Json(name = "results") val results: List<TmdbVideoDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbVideoDto(
    @Json(name = "key") val key: String,
    @Json(name = "site") val site: String,
    @Json(name = "type") val type: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "iso_639_1") val iso_639_1: String? = null,
    @Json(name = "official") val official: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsResponse(
    @Json(name = "cast") val cast: List<TmdbCastDto>? = emptyList(),
    @Json(name = "crew") val crew: List<TmdbCrewDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "order") val order: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCrewDto(
    @Json(name = "name") val name: String,
    @Json(name = "job") val job: String
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDto(
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String? = "",
    @Json(name = "episodes") val episodes: List<TmdbEpisodeDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String? = "",
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "runtime") val runtime: Int? = 45
)

@JsonClass(generateAdapter = true)
data class TmdbGenreDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

