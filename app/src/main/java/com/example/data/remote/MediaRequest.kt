package com.example.data.remote

data class MediaRequest(
    val id: String = "",
    val tmdbId: Int = 0,
    val mediaType: String = "movie", // "movie" or "tv"
    val title: String = "",
    val originalTitle: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String = "",
    val overview: String = "",
    val genres: String = "",
    val deviceId: String = "",
    val status: String = "pending", // "pending", "added", "ignored"
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

