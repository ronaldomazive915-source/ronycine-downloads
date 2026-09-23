package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.remote.CalendarFilter
import com.example.data.remote.CalendarItem
import com.example.data.remote.CalendarItemDto
import com.example.data.remote.CalendarMediaType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class CalendarUiState {
    object Loading : CalendarUiState()
    data class Success(
        val items: List<CalendarItem>,
        val groupedByDate: Map<String, List<CalendarItem>>
    ) : CalendarUiState()
    object Empty : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}

class CalendarRepository(private val context: Context) {

    companion object {
        private const val TAG = "CalendarRepository"

        // DUAS APIS DO PROJETO - PRESERVADAS RIGOROSAMENTE
        const val SUPERFLIX_URL = "https://superflixapi.quest/calendario.php"
        const val WAREZCDN_URL = "https://warezcdn.sbs/calendario.php"

        const val SOURCE_SUPERFLIX = "superflix"
        const val SOURCE_WAREZCDN = "warezcdn"

        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutos de cache

        @Volatile
        private var INSTANCE: CalendarRepository? = null

        fun getInstance(context: Context): CalendarRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CalendarRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatReleaseDateLong(dateStr: String): String {
            return try {
                val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdfIn.parse(dateStr) ?: return dateStr
                val dayFormat = SimpleDateFormat("dd", Locale("pt", "BR"))
                val monthFormat = SimpleDateFormat("MMMM", Locale("pt", "BR"))
                val yearFormat = SimpleDateFormat("yyyy", Locale("pt", "BR"))

                val day = dayFormat.format(date)
                val month = monthFormat.format(date).uppercase()
                val year = yearFormat.format(date)

                "$day DE $month DE $year"
            } catch (_: Exception) {
                dateStr
            }
        }

        fun formatReleaseDateShort(dateStr: String): String {
            return try {
                val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdfIn.parse(dateStr) ?: return dateStr
                val dayFormat = SimpleDateFormat("dd", Locale("pt", "BR"))
                val monthFormat = SimpleDateFormat("MMM", Locale("pt", "BR"))
                val yearFormat = SimpleDateFormat("yyyy", Locale("pt", "BR"))

                val day = dayFormat.format(date)
                val month = monthFormat.format(date).replace(".", "").uppercase()
                val year = yearFormat.format(date)

                "$day $month $year"
            } catch (_: Exception) {
                dateStr
            }
        }

        fun formatEpisodeAirDate(dateStr: String): String {
            return try {
                val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdfIn.parse(dateStr) ?: return dateStr
                val dayFormat = SimpleDateFormat("dd", Locale("pt", "BR"))
                val monthFormat = SimpleDateFormat("MMM", Locale("pt", "BR"))

                val day = dayFormat.format(date)
                val month = monthFormat.format(date).replace(".", "").uppercase()

                "$day $month"
            } catch (_: Exception) {
                dateStr
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, CalendarItemDto::class.java)
    private val jsonAdapter = moshi.adapter<List<CalendarItemDto>>(listType)

    private val _cachedItems = MutableStateFlow<List<CalendarItem>>(emptyList())
    val cachedItems: StateFlow<List<CalendarItem>> = _cachedItems.asStateFlow()

    private var lastFetchTimestamp = 0L
    private val fetchMutex = Mutex()

    /**
     * Ponto único de entrada: consulta Superflix e WarezCDN simultaneamente,
     * normaliza, consolida atributos, deduplica por chave determinística e ordena cronologicamente (ASC).
     */
    suspend fun fetchCalendar(forceRefresh: Boolean = false): Result<List<CalendarItem>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _cachedItems.value.isNotEmpty() && (now - lastFetchTimestamp < CACHE_VALIDITY_MS)) {
            Log.d(TAG, "CALENDAR_ITEMS_CACHED: Retornando ${_cachedItems.value.size} itens em cache")
            return@withContext Result.success(_cachedItems.value)
        }

        fetchMutex.withLock {
            // Re-checar dentro do lock para evitar requisições simultâneas duplicadas
            val recheckNow = System.currentTimeMillis()
            if (!forceRefresh && _cachedItems.value.isNotEmpty() && (recheckNow - lastFetchTimestamp < CACHE_VALIDITY_MS)) {
                return@withContext Result.success(_cachedItems.value)
            }

            coroutineScope {
                // Consultar ambas as APIs em paralelo
                val superflixDeferred = async { fetchFromApi(SUPERFLIX_URL, SOURCE_SUPERFLIX) }
                val warezDeferred = async { fetchFromApi(WAREZCDN_URL, SOURCE_WAREZCDN) }

                val superflixResult = superflixDeferred.await()
                val warezResult = warezDeferred.await()

                val superflixDtos = superflixResult.getOrDefault(emptyList())
                val warezDtos = warezResult.getOrDefault(emptyList())

                if (superflixResult.isSuccess) {
                    Log.i(TAG, "CALENDAR_SOURCE_SUPERFLIX_SUCCESS: ${superflixDtos.size} itens")
                } else {
                    Log.w(TAG, "CALENDAR_SOURCE_SUPERFLIX_ERROR: ${superflixResult.exceptionOrNull()?.message}")
                }

                if (warezResult.isSuccess) {
                    Log.i(TAG, "CALENDAR_SOURCE_WAREZ_SUCCESS: ${warezDtos.size} itens")
                } else {
                    Log.w(TAG, "CALENDAR_SOURCE_WAREZ_ERROR: ${warezResult.exceptionOrNull()?.message}")
                }

                val totalReceived = superflixDtos.size + warezDtos.size
                Log.i(TAG, "CALENDAR_ITEMS_RECEIVED: Superflix=${superflixDtos.size}, WarezCDN=${warezDtos.size}, Total=$totalReceived")

                if (superflixResult.isFailure && warezResult.isFailure) {
                    if (_cachedItems.value.isNotEmpty()) {
                        Log.w(TAG, "Ambas APIs falharam, usando dados em cache existente.")
                        return@coroutineScope Result.success(_cachedItems.value)
                    }
                    val msg = "Não foi possível conectar às fontes do calendário."
                    Log.e(TAG, msg)
                    return@coroutineScope Result.failure(Exception(msg))
                }

                // Normalização e Deduplicação Central
                val deduplicatedMap = LinkedHashMap<String, CalendarItem>()
                var duplicatesCount = 0

                // 1. Processar Superflix
                for (dto in superflixDtos) {
                    val item = normalizeCalendarItem(dto, SOURCE_SUPERFLIX) ?: continue
                    deduplicatedMap[item.id] = item
                }

                // 2. Processar WarezCDN com consolidação de atributos
                for (dto in warezDtos) {
                    val incoming = normalizeCalendarItem(dto, SOURCE_WAREZCDN) ?: continue
                    val existing = deduplicatedMap[incoming.id]

                    if (existing != null) {
                        duplicatesCount++
                        Log.d(TAG, "CALENDAR_DUPLICATE_DETECTED: chave=${incoming.id} mesclando fontes [${existing.sources.joinToString()} + ${incoming.sources.joinToString()}]")
                        
                        // Mesclar dados com preferência a dados mais completos
                        val merged = existing.copy(
                            sources = (existing.sources + incoming.sources).distinct(),
                            posterUrl = if (!existing.posterUrl.isNullOrBlank()) existing.posterUrl else incoming.posterUrl,
                            backdropUrl = if (!existing.backdropUrl.isNullOrBlank()) existing.backdropUrl else incoming.backdropUrl,
                            episodeTitle = if (!existing.episodeTitle.isNullOrBlank()) existing.episodeTitle else incoming.episodeTitle,
                            status = if (!existing.status.isNullOrBlank()) existing.status else incoming.status,
                            tmdbId = existing.tmdbId ?: incoming.tmdbId,
                            imdbId = if (!existing.imdbId.isNullOrBlank()) existing.imdbId else incoming.imdbId,
                            seasonNumber = existing.seasonNumber ?: incoming.seasonNumber,
                            episodeNumber = existing.episodeNumber ?: incoming.episodeNumber
                        )
                        deduplicatedMap[incoming.id] = merged
                    } else {
                        deduplicatedMap[incoming.id] = incoming
                    }
                }

                Log.i(TAG, "CALENDAR_ITEMS_DEDUPLICATED: duplicados_consolidados=$duplicatesCount")

                // 3. Ordenação cronológica rigorosa ASC (por data ASC, depois título, temporada, episódio)
                val finalItems = deduplicatedMap.values.toList()
                    .sortedWith(
                        compareBy<CalendarItem> { it.airDate }
                            .thenBy { it.title }
                            .thenBy { it.seasonNumber ?: 0 }
                            .thenBy { it.episodeNumber ?: 0 }
                    )

                Log.i(TAG, "CALENDAR_FINAL_COUNT: ${finalItems.size} lançamentos únicos")

                _cachedItems.value = finalItems
                lastFetchTimestamp = System.currentTimeMillis()
                Result.success(finalItems)
            }
        }
    }

    /**
     * Requisição HTTP isolada por API com fallback de parsing seguro.
     */
    private fun fetchFromApi(url: String, sourceName: String): Result<List<CalendarItemDto>> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) RonycineApp/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code} ao consultar $sourceName"))
            }

            val bodyString = response.body?.string() ?: ""
            if (bodyString.isBlank()) {
                return Result.success(emptyList())
            }

            val dtos: List<CalendarItemDto> = try {
                jsonAdapter.fromJson(bodyString) ?: parseManually(bodyString)
            } catch (e: Exception) {
                Log.w(TAG, "Falha no parser Moshi para $sourceName, tentando manual: ${e.message}")
                parseManually(bodyString)
            }

            Result.success(dtos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Normalização e geração de chave determinística única.
     * Chaves geradas:
     * 1. tmdbId + season + episode -> "serie:1399:s2:e8" ou "filme:tmdb:12345"
     * 2. imdbId + season + episode -> "imdb:tt123456:s2:e8" ou "filme:imdb:tt123456"
     * 3. tipo + ID externo + season + episode
     * 4. fallback sem ID: título normalizado + data + season + episode -> "raw:serie:one_piece:2026-09-25:s2:e8"
     */
    fun normalizeCalendarItem(dto: CalendarItemDto, sourceName: String): CalendarItem? {
        val title = dto.title?.trim() ?: return null
        if (title.isBlank()) return null

        val airDate = dto.airDate?.trim() ?: ""
        val tmdbIdInt = dto.tmdbId?.toIntOrNull()
        val cleanImdb = dto.imdbId?.trim()?.takeIf { it.isNotBlank() }
        val mediaType = CalendarMediaType.fromType(dto.type, title, dto.season)

        val posterResolved = when {
            dto.poster.isNullOrBlank() -> null
            dto.poster.startsWith("http://") || dto.poster.startsWith("https://") -> dto.poster
            dto.poster.startsWith("/") -> "https://image.tmdb.org/t/p/w500${dto.poster}"
            else -> "https://image.tmdb.org/t/p/w500/${dto.poster}"
        }

        val backdropResolved = when {
            dto.backdrop.isNullOrBlank() -> null
            dto.backdrop.startsWith("http://") || dto.backdrop.startsWith("https://") -> dto.backdrop
            dto.backdrop.startsWith("/") -> "https://image.tmdb.org/t/p/w1280${dto.backdrop}"
            else -> "https://image.tmdb.org/t/p/w1280/${dto.backdrop}"
        }

        val season = dto.season
        val episode = dto.number

        // GERAÇÃO DE CHAVE DETERMINÍSTICA ÚNICA
        val uniqueKey = generateUniqueKey(
            mediaType = mediaType,
            tmdbId = tmdbIdInt,
            imdbId = cleanImdb,
            title = title,
            season = season,
            episode = episode,
            airDate = airDate
        )

        return CalendarItem(
            id = uniqueKey,
            title = title,
            episodeTitle = dto.episode?.trim(),
            seasonNumber = season,
            episodeNumber = episode,
            airDate = airDate,
            mediaType = mediaType,
            tmdbId = tmdbIdInt,
            imdbId = cleanImdb,
            posterUrl = posterResolved,
            backdropUrl = backdropResolved,
            status = dto.status?.trim(),
            sources = listOf(sourceName)
        )
    }

    /**
     * Regra determinística de chave única de identificação
     */
    fun generateUniqueKey(
        mediaType: CalendarMediaType,
        tmdbId: Int?,
        imdbId: String?,
        title: String,
        season: Int?,
        episode: Int?,
        airDate: String
    ): String {
        val typeTag = mediaType.routeType
        val isMovie = mediaType == CalendarMediaType.MOVIE

        return when {
            // 1. TMDB ID + Temporada + Episódio
            tmdbId != null && tmdbId > 0 -> {
                if (isMovie) "filme:tmdb:$tmdbId"
                else "$typeTag:$tmdbId:s${season ?: 0}:e${episode ?: 0}"
            }
            // 2. IMDb ID + Temporada + Episódio
            !imdbId.isNullOrBlank() -> {
                if (isMovie) "filme:imdb:$imdbId"
                else "$typeTag:imdb:$imdbId:s${season ?: 0}:e${episode ?: 0}"
            }
            // 3. Fallback: Título normalizado + Data + Temporada + Episódio
            else -> {
                val cleanTitle = normalizeTitle(title)
                if (isMovie) "filme:raw:$cleanTitle:$airDate"
                else "$typeTag:raw:$cleanTitle:$airDate:s${season ?: 0}:e${episode ?: 0}"
            }
        }
    }

    private fun parseManually(json: String): List<CalendarItemDto> {
        val list = mutableListOf<CalendarItemDto>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    CalendarItemDto(
                        title = obj.optString("title").takeIf { it.isNotBlank() },
                        episode = obj.optString("episode").takeIf { it.isNotBlank() },
                        season = if (obj.has("season")) obj.optInt("season") else null,
                        number = if (obj.has("number")) obj.optInt("number") else null,
                        airDate = obj.optString("air_date").takeIf { it.isNotBlank() },
                        type = if (obj.has("type")) obj.optInt("type") else null,
                        tmdbId = obj.optString("tmdb_id").takeIf { it.isNotBlank() },
                        imdbId = obj.optString("imdb_id").takeIf { it.isNotBlank() },
                        poster = obj.optString("poster").takeIf { it.isNotBlank() },
                        backdrop = obj.optString("backdrop").takeIf { it.isNotBlank() },
                        status = obj.optString("status").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual JSON parse exception: ${e.message}")
        }
        return list
    }

    /**
     * Agrupamento cronológico inteligente em seções:
     * - HOJE • DD DE MMMM
     * - AMANHÃ • DD DE MMMM
     * - ESTA SEMANA (dias restantes da semana corrente)
     * - PRÓXIMA SEMANA
     * - PRÓXIMO MÊS / FUTURO
     * - JÁ DISPONÍVEIS (para lançamentos passados)
     */
    fun groupItemsByDate(items: List<CalendarItem>): Map<String, List<CalendarItem>> {
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfMonthDay = SimpleDateFormat("dd 'DE' MMMM", Locale("pt", "BR"))
        val sdfShort = SimpleDateFormat("dd MMM", Locale("pt", "BR"))

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val yesterdayCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

        // Fim desta semana (próximos 7 dias)
        val endOfWeekCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
        // Fim da próxima semana (próximos 14 dias)
        val endOfNextWeekCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 14) }

        val todayStr = sdfInput.format(todayCal.time)
        val tomorrowStr = sdfInput.format(tomorrowCal.time)
        val endOfWeekStr = sdfInput.format(endOfWeekCal.time)
        val endOfNextWeekStr = sdfInput.format(endOfNextWeekCal.time)

        val grouped = linkedMapOf<String, MutableList<CalendarItem>>()

        // Itens já vêm ordenados ASC
        items.forEach { item ->
            val dateKey = when {
                item.airDate.isBlank() -> "DATA A CONFIRMAR"
                item.airDate < todayStr -> {
                    try {
                        val parsed = sdfInput.parse(item.airDate)
                        if (parsed != null) "ANTERIORES • ${sdfShort.format(parsed).replace(".", "").uppercase()}"
                        else "ANTERIORES"
                    } catch (_: Exception) {
                        "ANTERIORES"
                    }
                }
                item.airDate == todayStr -> "HOJE • ${sdfMonthDay.format(todayCal.time).uppercase()}"
                item.airDate == tomorrowStr -> "AMANHÃ • ${sdfMonthDay.format(tomorrowCal.time).uppercase()}"
                item.airDate <= endOfWeekStr -> {
                    try {
                        val parsed = sdfInput.parse(item.airDate)
                        if (parsed != null) "ESTA SEMANA • ${sdfShort.format(parsed).replace(".", "").uppercase()}"
                        else "ESTA SEMANA"
                    } catch (_: Exception) {
                        "ESTA SEMANA"
                    }
                }
                item.airDate <= endOfNextWeekStr -> {
                    try {
                        val parsed = sdfInput.parse(item.airDate)
                        if (parsed != null) "PRÓXIMA SEMANA • ${sdfShort.format(parsed).replace(".", "").uppercase()}"
                        else "PRÓXIMA SEMANA"
                    } catch (_: Exception) {
                        "PRÓXIMA SEMANA"
                    }
                }
                else -> {
                    try {
                        val parsed = sdfInput.parse(item.airDate)
                        if (parsed != null) "PRÓXIMO MÊS • ${sdfShort.format(parsed).replace(".", "").uppercase()}"
                        else "EM BREVE"
                    } catch (_: Exception) {
                        "EM BREVE"
                    }
                }
            }

            grouped.getOrPut(dateKey) { mutableListOf() }.add(item)
        }

        return grouped
    }

    /**
     * Localização de dados de lançamento para a tela de detalhes
     */
    suspend fun findReleaseInfoForMedia(
        tmdbId: Int,
        mediaType: String,
        title: String,
        availableEpisodes: List<com.example.data.local.EpisodeEntity> = emptyList(),
        releaseYear: String = ""
    ): com.example.data.remote.MediaReleaseInfo? {
        if (tmdbId <= 0 && title.isBlank()) return null

        val items = if (_cachedItems.value.isNotEmpty()) {
            _cachedItems.value
        } else {
            fetchCalendar(forceRefresh = false).getOrDefault(emptyList())
        }

        if (items.isEmpty()) return null

        val isMovie = mediaType.equals("movie", ignoreCase = true) || mediaType.equals("filme", ignoreCase = true)
        val cleanTargetTitle = normalizeTitle(title)

        // 1. Match por TMDB ID
        var matchingItems = items.filter { item ->
            item.tmdbId != null && item.tmdbId == tmdbId
        }

        // 2. Match por título e tipo
        if (matchingItems.isEmpty() && cleanTargetTitle.isNotBlank()) {
            matchingItems = items.filter { item ->
                val itemTitle = normalizeTitle(item.title)
                val typeMatches = if (isMovie) {
                    item.mediaType == CalendarMediaType.MOVIE
                } else {
                    item.mediaType != CalendarMediaType.MOVIE
                }
                typeMatches && (itemTitle == cleanTargetTitle || (itemTitle.length > 4 && cleanTargetTitle.length > 4 && (itemTitle.contains(cleanTargetTitle) || cleanTargetTitle.contains(itemTitle))))
            }
        }

        if (matchingItems.isEmpty()) {
            return null
        }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdfDate.format(Date())

        if (isMovie) {
            val futureMovieItem = matchingItems.firstOrNull { item ->
                item.airDate.isNotBlank() && item.airDate >= todayStr
            } ?: return null

            val formattedLong = formatReleaseDateLong(futureMovieItem.airDate)
            val formattedShort = formatReleaseDateShort(futureMovieItem.airDate)

            return com.example.data.remote.MediaReleaseInfo(
                tmdbId = futureMovieItem.tmdbId ?: tmdbId,
                imdbId = futureMovieItem.imdbId,
                title = futureMovieItem.title.ifBlank { title },
                rawAirDate = futureMovieItem.airDate,
                formattedReleaseDate = formattedLong,
                formattedShortDate = formattedShort,
                isMovie = true,
                isNextEpisode = false,
                isUnreleasedContent = true
            )
        } else {
            val validFutureEpisodes = matchingItems
                .filter { it.airDate.isNotBlank() && it.airDate >= todayStr }
                .sortedWith(compareBy<CalendarItem> { it.airDate }
                    .thenBy { it.seasonNumber ?: 0 }
                    .thenBy { it.episodeNumber ?: 0 })

            if (validFutureEpisodes.isNotEmpty()) {
                val nextEpisode = validFutureEpisodes.first()
                val formattedLong = formatReleaseDateLong(nextEpisode.airDate)
                val formattedShort = formatEpisodeAirDate(nextEpisode.airDate)

                val hasAvailableEpisodes = availableEpisodes.isNotEmpty()

                return com.example.data.remote.MediaReleaseInfo(
                    tmdbId = nextEpisode.tmdbId ?: tmdbId,
                    imdbId = nextEpisode.imdbId,
                    title = nextEpisode.title.ifBlank { title },
                    rawAirDate = nextEpisode.airDate,
                    formattedReleaseDate = formattedLong,
                    formattedShortDate = formattedShort,
                    isMovie = false,
                    isNextEpisode = true,
                    seasonNumber = nextEpisode.seasonNumber,
                    episodeNumber = nextEpisode.episodeNumber,
                    episodeTitle = nextEpisode.episodeTitle,
                    isUnreleasedContent = !hasAvailableEpisodes
                )
            }

            return null
        }
    }

    private fun normalizeTitle(input: String): String {
        if (input.isBlank()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .replace("[^a-z0-9]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }
}
