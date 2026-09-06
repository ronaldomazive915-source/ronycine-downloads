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
        val player = overridePlayer ?: "vidstack"
        val cleanColor = config.colorHex.removePrefix("#").ifBlank { "E50914" }
        val domain = config.baseDomain.removeSuffix("/")

        val idSegment = if (tmdbId > 0) tmdbId.toString() else (imdbId ?: "0")
        val isMovie = mediaType.equals("movie", ignoreCase = true) || mediaType.equals("filme", ignoreCase = true)

        val path = if (isMovie) {
            "$domain/embed/$idSegment"
        } else {
            val s = if (season > 0) season else 1
            val e = if (episode > 0) episode else 1
            "$domain/embed/$idSegment/$s/$e"
        }

        return "$path?player=$player#color:$cleanColor"
    }

    /**
     * Fetches movies catalog from MegaEmbed API: https://mgeb.top/api/movie
     * Uses 2-hour memory cache to prevent rate-limiting and unnecessary requests.
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
                    val adapter = moshi.adapter<List<MegaEmbedApiItem>>(
                        com.squareup.moshi.Types.newParameterizedType(List::class.java, MegaEmbedApiItem::class.java)
                    )
                    val list = adapter.fromJson(bodyString) ?: emptyList()
                    cachedMovies = list
                    lastMoviesFetchTimeMs = now
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            // Logged silently, fallback to cached
        }
        return@withContext cachedMovies
    }

    /**
     * Fetches series catalog from MegaEmbed API: https://mgeb.top/api/series
     * Uses 2-hour memory cache.
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
                    val adapter = moshi.adapter<List<MegaEmbedApiItem>>(
                        com.squareup.moshi.Types.newParameterizedType(List::class.java, MegaEmbedApiItem::class.java)
                    )
                    val list = adapter.fromJson(bodyString) ?: emptyList()
                    cachedSeries = list
                    lastSeriesFetchTimeMs = now
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            // Fallback to cached
        }
        return@withContext cachedSeries
    }
}
