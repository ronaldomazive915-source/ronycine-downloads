package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class MegaEmbedConfig(
    val defaultPlayer: String = "megaplay", // "megaplay", "megatube", "vidstack", "clappr"
    val defaultLanguage: String = "dubbed", // "dubbed" (Dublado) or "subtitled" (Legendado)
    val colorHex: String = "E50914",
    val baseDomain: String = "https://mgeb.top",
    val cacheHours: Int = 2,
    val isAutoSyncEnabled: Boolean = true
)

data class MegaEmbedApiItem(
    @Json(name = "tmdb_id") val tmdbId: Int? = null,
    @Json(name = "imdb_id") val imdbId: String? = null,
    val title: String? = null,
    val type: String? = null, // "movie" or "tv"
    @Json(name = "updated_at") val updatedAt: String? = null
)

object MegaEmbedService {

    private var cachedConfig = MegaEmbedConfig()

    private var lastMoviesFetchTimeMs: Long = 0
    private var cachedMovies: List<MegaEmbedApiItem> = emptyList()

    private var lastSeriesFetchTimeMs: Long = 0
    private var cachedSeries: List<MegaEmbedApiItem> = emptyList()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun updateConfig(config: MegaEmbedConfig) {
        cachedConfig = config
    }

    fun getConfig(): MegaEmbedConfig = cachedConfig

    /**
     * Builds official RONYCINE Embed URL according to specifications.
     * Example Movie: https://mgeb.top/embed/1439930?player=vidstack#color:E50914
     * Example Series: https://mgeb.top/embed/1399/1/1?player=vidstack#color:E50914
     */
    fun buildEmbedUrl(
        tmdbId: Int,
        mediaType: String,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null,
        language: String? = null,
        overridePlayer: String? = null,
        config: MegaEmbedConfig = cachedConfig
    ): String {
        val player = overridePlayer ?: config.defaultPlayer.ifBlank { "megaplay" }
        val color = config.colorHex.ifBlank { "fb542b" }

        return com.example.util.PlayerUtils.buildPlayerUrl(
            provider = "MegaEmbed",
            mediaType = mediaType,
            tmdbId = if (tmdbId > 0) tmdbId else null,
            imdbId = imdbId,
            season = season,
            episode = episode,
            audio = if (language.equals("subtitled", ignoreCase = true)) "Legendado" else "Dublado",
            player = player,
            color = color
        )
    }

    /**
     * Fetches movies catalog from MegaEmbed API: https://mgeb.top/api/movie
     * Handles both older object format and new simple ID array format.
     */
    suspend fun fetchMegaEmbedMovies(forceRefresh: Boolean = false): List<MegaEmbedApiItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cacheDurationMs = cachedConfig.cacheHours * 3600 * 1000L

        if (!forceRefresh && cachedMovies.isNotEmpty() && (now - lastMoviesFetchTimeMs) < cacheDurationMs) {
            return@withContext cachedMovies
        }

        try {
            val url = "${cachedConfig.baseDomain.removeSuffix("/")}/api/movie"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrBlank()) {
                    // Try parsing as List<MegaEmbedApiItem> first (Object format)
                    try {
                        val adapter = moshi.adapter<List<MegaEmbedApiItem>>(
                            com.squareup.moshi.Types.newParameterizedType(List::class.java, MegaEmbedApiItem::class.java)
                        )
                        val list = adapter.fromJson(bodyString)
                        if (!list.isNullOrEmpty() && list[0].tmdbId != null) {
                            cachedMovies = list
                            lastMoviesFetchTimeMs = now
                            return@withContext list
                        }
                    } catch (e: Exception) {
                        // Fallback to ID array
                    }

                    // Fallback: Try parsing as List<Int> (Simple ID format)
                    try {
                        val idListAdapter = moshi.adapter<List<Int>>(
                            com.squareup.moshi.Types.newParameterizedType(List::class.java, Integer::class.java)
                        )
                        val idList = idListAdapter.fromJson(bodyString)
                        if (!idList.isNullOrEmpty()) {
                            val list = idList.map { MegaEmbedApiItem(tmdbId = it, type = "movie") }
                            cachedMovies = list
                            lastMoviesFetchTimeMs = now
                            return@withContext list
                        }
                    } catch (e: Exception) {
                        // Both failed
                    }
                }
            }
        } catch (e: Exception) {
            // Logged silently
        }
        return@withContext cachedMovies
    }

    /**
     * Fetches series catalog from MegaEmbed API: https://mgeb.top/api/series
     * Handles both older object format and new simple ID array format.
     */
    suspend fun fetchMegaEmbedSeries(forceRefresh: Boolean = false): List<MegaEmbedApiItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cacheDurationMs = cachedConfig.cacheHours * 3600 * 1000L

        if (!forceRefresh && cachedSeries.isNotEmpty() && (now - lastSeriesFetchTimeMs) < cacheDurationMs) {
            return@withContext cachedSeries
        }

        try {
            val url = "${cachedConfig.baseDomain.removeSuffix("/")}/api/series"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrBlank()) {
                    // Try parsing as List<MegaEmbedApiItem> first
                    try {
                        val adapter = moshi.adapter<List<MegaEmbedApiItem>>(
                            com.squareup.moshi.Types.newParameterizedType(List::class.java, MegaEmbedApiItem::class.java)
                        )
                        val list = adapter.fromJson(bodyString)
                        if (!list.isNullOrEmpty() && list[0].tmdbId != null) {
                            cachedSeries = list
                            lastSeriesFetchTimeMs = now
                            return@withContext list
                        }
                    } catch (e: Exception) {
                        // Fallback
                    }

                    // Fallback: Try parsing as List<Int>
                    try {
                        val idListAdapter = moshi.adapter<List<Int>>(
                            com.squareup.moshi.Types.newParameterizedType(List::class.java, Integer::class.java)
                        )
                        val idList = idListAdapter.fromJson(bodyString)
                        if (!idList.isNullOrEmpty()) {
                            val list = idList.map { MegaEmbedApiItem(tmdbId = it, type = "tv") }
                            cachedSeries = list
                            lastSeriesFetchTimeMs = now
                            return@withContext list
                        }
                    } catch (e: Exception) {
                        // Both failed
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return@withContext cachedSeries
    }
}
