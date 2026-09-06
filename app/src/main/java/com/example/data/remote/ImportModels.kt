package com.example.data.remote

import com.google.firebase.firestore.DocumentId
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImportJob(
    @DocumentId val id: String = "",
    val type: String = "both", // "movie", "tv", "both"
    val source: String = "tmdb_ids", // "tmdb_ids", "pasted_list", "tmdb_pages"
    val status: String = "queued", // "queued", "processing", "paused", "completed", "cancelled", "failed"
    val total: Int = 0,
    val processed: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val duplicates: Int = 0,
    val incomplete: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val lastError: String? = null,
    val config: ImportConfig = ImportConfig(),
    val adminEmail: String = "ronaldomazive915@gmail.com"
)

@JsonClass(generateAdapter = true)
data class ImportConfig(
    val concurrentWorkers: Int = 2, // Max 2-3 concurrent workers
    val retryErrors: Boolean = true,
    val ignoreDuplicates: Boolean = true,
    val updateExisting: Boolean = false,
    val autoResume: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ImportItem(
    @DocumentId val id: String = "",
    val jobId: String = "",
    val tmdbId: Int = 0,
    val mediaType: String = "movie",
    val status: String = "pending", // "pending", "processing", "success", "failed", "duplicate", "incomplete"
    val error: String? = null,
    val title: String? = null,
    val processedAt: Long? = null,
    val retryCount: Int = 0,
    val stage: String? = null
)

@JsonClass(generateAdapter = true)
data class ImportSummary(
    val totalMovies: Int = 0,
    val totalSeries: Int = 0,
    val activeJobsCount: Int = 0,
    val lastUpdate: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class SeriesUpdateResult(
    val success: Boolean = false,
    val seasonsVerified: Int = 0,
    val episodesFound: Int = 0,
    val episodesExisting: Int = 0,
    val episodesAdded: Int = 0,
    val errorsCount: Int = 0,
    val message: String = ""
)
