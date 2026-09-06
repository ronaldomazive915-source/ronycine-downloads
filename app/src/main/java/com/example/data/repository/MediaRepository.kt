package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.config.AppShareConfig
import com.example.data.local.*
import com.example.data.remote.ImportConfig
import com.example.data.remote.ImportItem
import com.example.data.remote.ImportJob
import com.example.data.remote.ImportSummary
import com.example.data.remote.SeriesUpdateResult
import com.example.data.remote.MediaRequest
import com.example.data.remote.MegaEmbedConfig
import com.example.data.remote.MegaEmbedService
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbMediaDto
import com.example.data.remote.TmdbNetwork
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class TmdbAutoSyncConfig(
    val enabled: Boolean = true,
    val frequencyHours: Int = 24, // 0, 1, 6, 12, 24
    val maxItemsPerSync: Int = 20, // 5, 10, 20, 50
    val contentType: String = "both", // "both", "movie", "tv"
    val criterion: String = "trending", // "releases", "trending", "popular", "today", "this_week"
    val updateExistingMetadata: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "Inativo"
)

data class TmdbAutoSyncProgress(
    val isRunning: Boolean = false,
    val stepMessage: String = "",
    val totalFound: Int = 0,
    val processedCount: Int = 0,
    val newMoviesFound: Int = 0,
    val newSeriesFound: Int = 0,
    val importedCount: Int = 0,
    val existingCount: Int = 0,
    val ignoredCount: Int = 0,
    val errorCount: Int = 0,
    val pendingCount: Int = 0,
    val inProgressCount: Int = 0,
    val currentTitle: String = "",
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val durationSeconds: Long = 0,
    val lastResultSummary: String? = null
)

data class MegaEmbedSyncResult(
    val newMovies: Int = 0,
    val newSeries: Int = 0,
    val updatedCount: Int = 0,
    val ignoredCount: Int = 0,
    val duplicateCount: Int = 0,
    val errorCount: Int = 0,
    val totalProcessed: Int = 0,
    val lastSyncFormatted: String = "",
    val message: String = ""
)

enum class SearchItemStatus {
    IN_CATALOG,            // 🟢 DISPONÍVEL (No Catálogo)
    READY_TO_WATCH,        // 🟢 PRONTO PARA ASSISTIR (Fonte Externa Válida)
    REQUEST_PENDING,       // 🟡 PEDIDO ENVIADO (Já Solicitado)
    AVAILABLE_FOR_REQUEST  // 🔴 DISPONÍVEL PARA PEDIDO (Sem Fonte)
}

data class SmartSearchResultItem(
    val entity: MediaEntity,
    val status: SearchItemStatus,
    val statusLabel: String,
    val isLocalCatalog: Boolean,
    val hasValidSource: Boolean,
    val isPendingRequest: Boolean
)

data class TmdbSearchResultItem(
    val entity: MediaEntity,
    val isAlreadyInCatalog: Boolean
)

class MediaRepository(
    private val dao: PlayFilmeDao,
    private val firebaseService: com.example.data.remote.FirebaseService? = null
) {

    private val api: TmdbApiService = TmdbNetwork.apiService

    private val apiKey: String
        get() = BuildConfig.TMDB_API_KEY.ifEmpty { "d60f5b5a778fe74b5cf4a371f008725b" }

    // --- TMDB Professional Auto-Sync Engine ---
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isAutoSyncRunning = AtomicBoolean(false)
    private var autoSyncJob: Job? = null
    private val _tmdbAutoSyncProgress = MutableStateFlow(TmdbAutoSyncProgress())
    val tmdbAutoSyncProgress = _tmdbAutoSyncProgress.asStateFlow()

    init {
        // Automatically start realtime centralized Firestore synchronization
        firebaseService?.startRealtimeSync(dao)
        startPeriodicTmdbAutoSyncScheduler()
    }

    fun observeTmdbAutoSyncHistory(): Flow<List<TmdbAutoSyncHistoryEntity>> = dao.getAllTmdbAutoSyncHistory()
    fun observeLatestTmdbAutoSync(): Flow<TmdbAutoSyncHistoryEntity?> = dao.observeLatestTmdbAutoSync()
    suspend fun clearTmdbAutoSyncHistory() = withContext(Dispatchers.IO) {
        dao.clearTmdbAutoSyncHistory()
    }

    suspend fun getTmdbAutoSyncConfig(): TmdbAutoSyncConfig = withContext(Dispatchers.IO) {
        val enabled = dao.getSetting("tmdb_auto_sync_enabled")?.toBooleanStrictOrNull() ?: true
        val freq = dao.getSetting("tmdb_auto_sync_frequency")?.toIntOrNull() ?: 24
        val maxItems = dao.getSetting("tmdb_auto_sync_max_items")?.toIntOrNull() ?: 20
        val contentType = dao.getSetting("tmdb_auto_sync_content_type") ?: "both"
        val criterion = dao.getSetting("tmdb_auto_sync_criterion") ?: "trending"
        val updateExisting = dao.getSetting("tmdb_auto_sync_update_existing")?.toBooleanStrictOrNull() ?: false
        val lastRun = dao.getSetting("tmdb_auto_sync_last_run")?.toLongOrNull() ?: 0L
        val lastStatus = dao.getSetting("tmdb_auto_sync_last_status") ?: "Aguardando execução"
        TmdbAutoSyncConfig(
            enabled = enabled,
            frequencyHours = freq,
            maxItemsPerSync = maxItems,
            contentType = contentType,
            criterion = criterion,
            updateExistingMetadata = updateExisting,
            lastSyncTimestamp = lastRun,
            lastSyncStatus = lastStatus
        )
    }

    suspend fun saveTmdbAutoSyncConfig(config: TmdbAutoSyncConfig) = withContext(Dispatchers.IO) {
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_enabled", config.enabled.toString()))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_frequency", config.frequencyHours.toString()))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_max_items", config.maxItemsPerSync.toString()))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_content_type", config.contentType))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_criterion", config.criterion))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_update_existing", config.updateExistingMetadata.toString()))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_last_run", config.lastSyncTimestamp.toString()))
        dao.saveSetting(AppSettingsEntity("tmdb_auto_sync_last_status", config.lastSyncStatus))
    }

    private fun startPeriodicTmdbAutoSyncScheduler() {
        repoScope.launch {
            // Wait 15 seconds after app startup before checking to let initial sync stabilize
            delay(15_000L)
            while (true) {
                try {
                    val config = getTmdbAutoSyncConfig()
                    if (config.enabled) {
                        val now = System.currentTimeMillis()
                        val intervalMs = config.frequencyHours.toLong() * 3600_000L
                        val timeSinceLast = now - config.lastSyncTimestamp
                        if (timeSinceLast >= intervalMs) {
                            android.util.Log.d("MediaRepository", "[TMDB AUTO-SYNC] Intervalo atingido (${timeSinceLast / 1000}s >= ${intervalMs / 1000}s). Iniciando atualização automática em segundo plano...")
                            startTmdbAutoSync(isManual = false, customConfig = config).collect { }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MediaRepository", "[TMDB AUTO-SYNC] Erro no agendador periódico: ${e.message}")
                }
                // Check periodically every 10 minutes
                delay(10 * 60_000L)
            }
        }
    }

    fun cancelTmdbAutoSync() {
        autoSyncJob?.cancel()
        isAutoSyncRunning.set(false)
        _tmdbAutoSyncProgress.value = _tmdbAutoSyncProgress.value.copy(
            isRunning = false,
            isCancelled = true,
            isFinished = true,
            stepMessage = "Sincronização cancelada pelo administrador."
        )
    }

    fun startTmdbAutoSync(isManual: Boolean = true, customConfig: TmdbAutoSyncConfig? = null): Flow<TmdbAutoSyncProgress> = flow {
        if (!isAutoSyncRunning.compareAndSet(false, true)) {
            emit(_tmdbAutoSyncProgress.value.copy(stepMessage = "Uma sincronização já está em andamento!"))
            return@flow
        }

        val startTime = System.currentTimeMillis()
        val config = customConfig ?: getTmdbAutoSyncConfig()

        var currentProgress = TmdbAutoSyncProgress(
            isRunning = true,
            stepMessage = "Consultando TMDB e buscando conteúdos...",
            isFinished = false,
            isCancelled = false
        )
        _tmdbAutoSyncProgress.value = currentProgress
        emit(currentProgress)

        val candidates = mutableListOf<Pair<Int, String>>() // (tmdbId, mediaType)
        var newMoviesFound = 0
        var newSeriesFound = 0

        try {
            val criterion = config.criterion.ifBlank { "trending" }
            val fetchMovies = config.contentType == "all" || config.contentType == "both" || config.contentType == "movie"
            val fetchSeries = config.contentType == "all" || config.contentType == "both" || config.contentType == "tv"

            currentProgress = currentProgress.copy(stepMessage = "Consultando TMDB (Critério: $criterion)...")
            _tmdbAutoSyncProgress.value = currentProgress
            emit(currentProgress)

            if (fetchMovies) {
                when (criterion) {
                    "releases" -> {
                        try {
                            val res = api.getNowPlayingMovies(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha now_playing: ${e.message}") }
                        try {
                            val res = api.getUpcomingMovies(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha upcoming: ${e.message}") }
                    }
                    "popular" -> {
                        try {
                            val res = api.getPopularMovies(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha popular: ${e.message}") }
                    }
                    "today" -> {
                        try {
                            val res = api.getTrendingMoviesDay(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha trending day: ${e.message}") }
                    }
                    "this_week" -> {
                        try {
                            val res = api.getTrendingMoviesWeek(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha trending week: ${e.message}") }
                    }
                    else -> { // "trending"
                        try {
                            val res = api.getTrendingMoviesWeek(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha trending: ${e.message}") }
                        try {
                            val res = api.getNowPlayingMovies(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "movie")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha now_playing: ${e.message}") }
                    }
                }
            }

            if (fetchSeries) {
                when (criterion) {
                    "releases" -> {
                        try {
                            val res = api.getOnTheAirSeries(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha on_the_air: ${e.message}") }
                        try {
                            val res = api.getAiringTodaySeries(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha airing_today: ${e.message}") }
                    }
                    "popular" -> {
                        try {
                            val res = api.getPopularSeries(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha popular tv: ${e.message}") }
                    }
                    "today" -> {
                        try {
                            val res = api.getAiringTodaySeries(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha airing_today: ${e.message}") }
                        try {
                            val res = api.getTrendingTvDay(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha trending tv day: ${e.message}") }
                    }
                    "this_week" -> {
                        try {
                            val res = api.getOnTheAirSeries(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha on_the_air: ${e.message}") }
                        try {
                            val res = api.getTrendingTvWeek(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha trending tv week: ${e.message}") }
                    }
                    else -> { // "trending"
                        try {
                            val res = api.getTrendingTvWeek(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha trending tv: ${e.message}") }
                        try {
                            val res = api.getOnTheAirSeries(apiKey = apiKey, page = 1)
                            res.results.forEach { if (it.id > 0) candidates.add(Pair(it.id, "tv")) }
                        } catch (e: Exception) { Log.w("MediaRepository", "Falha on_the_air: ${e.message}") }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "[TMDB AUTO-SYNC] Erro durante consulta ao TMDB: ${e.message}", e)
        }

        // Deduplica candidatos mantendo ordem
        val uniqueCandidates = candidates.distinctBy { "${it.second}_${it.first}" }.take(config.maxItemsPerSync)
        newMoviesFound = uniqueCandidates.count { it.second == "movie" }
        newSeriesFound = uniqueCandidates.count { it.second == "tv" }

        currentProgress = currentProgress.copy(
            totalFound = uniqueCandidates.size,
            newMoviesFound = newMoviesFound,
            newSeriesFound = newSeriesFound,
            pendingCount = uniqueCandidates.size,
            inProgressCount = 0,
            stepMessage = "Encontrados ${uniqueCandidates.size} conteúdos ($newMoviesFound filmes, $newSeriesFound séries). Verificando catálogo..."
        )
        _tmdbAutoSyncProgress.value = currentProgress
        emit(currentProgress)

        val deletedIds = firebaseService?.getDeletedCatalogIds() ?: emptySet()
        if (deletedIds.isNotEmpty()) {
            Log.d("MediaRepository", "[TMDB AUTO-SYNC] Identificados ${deletedIds.size} itens na lista negra (tombstone).")
        }

        var processedCount = 0
        var importedCount = 0
        var existingCount = 0
        var ignoredCount = 0
        var errorCount = 0
        val importedTitles = mutableListOf<String>()

        for (item in uniqueCandidates) {
            if (!isAutoSyncRunning.get()) {
                currentProgress = currentProgress.copy(
                    isRunning = false,
                    isCancelled = true,
                    isFinished = true,
                    pendingCount = (uniqueCandidates.size - processedCount).coerceAtLeast(0),
                    inProgressCount = 0,
                    stepMessage = "Sincronização cancelada pelo administrador."
                )
                _tmdbAutoSyncProgress.value = currentProgress
                emit(currentProgress)
                break
            }

            processedCount++
            val tmdbId = item.first
            val type = item.second

            currentProgress = currentProgress.copy(
                processedCount = processedCount,
                pendingCount = (uniqueCandidates.size - processedCount).coerceAtLeast(0),
                inProgressCount = 1,
                stepMessage = "Processando (${processedCount}/${uniqueCandidates.size}): TMDB #$tmdbId ($type)..."
            )
            _tmdbAutoSyncProgress.value = currentProgress
            emit(currentProgress)

            val docId = if (type == "movie") "movie_$tmdbId" else "tv_$tmdbId"
            if (deletedIds.contains(docId) || deletedIds.contains("movie_$tmdbId") || deletedIds.contains("tv_$tmdbId")) {
                ignoredCount++
                currentProgress = currentProgress.copy(
                    processedCount = processedCount,
                    ignoredCount = ignoredCount,
                    inProgressCount = 0,
                    stepMessage = "Ignorando ID #$tmdbId: Este conteúdo foi excluído definitivamente pelo administrador."
                )
                _tmdbAutoSyncProgress.value = currentProgress
                emit(currentProgress)
                Log.i("MediaRepository", "[TMDB AUTO-SYNC] Item #$tmdbId ($type) ignorado por estar na lista 'deleted_catalog'.")
                continue
            }

            // Detecção rigorosa de duplicados por tmdbId + mediaType
            val existing = dao.getMediaByTmdbIdAndType(tmdbId, type) ?: dao.getMediaByTmdbId(tmdbId)

            if (existing != null) {
                if (type == "tv") {
                    // Para série existente, faz verificação incremental de novos episódios/temporadas sem deletar episódios existentes
                    currentProgress = currentProgress.copy(
                        currentTitle = existing.title,
                        stepMessage = "Verificando novos episódios para a série '${existing.title}'..."
                    )
                    _tmdbAutoSyncProgress.value = currentProgress
                    emit(currentProgress)

                    val updateRes = updateExistingSeries(tmdbId)
                    if (updateRes.episodesAdded > 0) {
                        importedCount++
                        importedTitles.add("${existing.title} (+${updateRes.episodesAdded} eps)")
                        currentProgress = currentProgress.copy(
                            processedCount = processedCount,
                            importedCount = importedCount,
                            inProgressCount = 0,
                            stepMessage = "Série '${existing.title}' atualizada com +${updateRes.episodesAdded} novos episódios!"
                        )
                    } else {
                        existingCount++
                        currentProgress = currentProgress.copy(
                            processedCount = processedCount,
                            existingCount = existingCount,
                            inProgressCount = 0,
                            stepMessage = "Série '${existing.title}' já está completa e atualizada."
                        )
                    }
                    _tmdbAutoSyncProgress.value = currentProgress
                    emit(currentProgress)
                    delay(100L)
                    continue
                } else {
                    if (!config.updateExistingMetadata) {
                        existingCount++
                        currentProgress = currentProgress.copy(
                            processedCount = processedCount,
                            existingCount = existingCount,
                            currentTitle = existing.title,
                            inProgressCount = 0,
                            stepMessage = "Verificando (${processedCount}/${uniqueCandidates.size}): '${existing.title}' já existe no catálogo (ignorado)."
                        )
                        _tmdbAutoSyncProgress.value = currentProgress
                        emit(currentProgress)
                        delay(50L)
                        continue
                    } else {
                        try {
                            currentProgress = currentProgress.copy(
                                processedCount = processedCount,
                                currentTitle = existing.title,
                                stepMessage = "Atualizando metadados (${processedCount}/${uniqueCandidates.size}): '${existing.title}'..."
                            )
                            _tmdbAutoSyncProgress.value = currentProgress
                            emit(currentProgress)

                            val dto = api.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
                            val finalVideos = fetchVideosWithEnglishFallback(tmdbId, type, dto.videos?.results)
                            val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))
                            val updatedEntity = mapDtoToEntity(dtoWithMerged, type, isHero = existing.isHeroFeatured)
                            dao.insertMedia(updatedEntity)
                            firebaseService?.upsertMediaInCloud(updatedEntity)
                            importedCount++
                            importedTitles.add(updatedEntity.title)
                        } catch (e: Exception) {
                            errorCount++
                        }
                        continue
                    }
                }
            }

            // Novo conteúdo a ser importado com timeout e retry
            var attempts = 0
            var importSuccess = false
            var lastErr: String? = null

            while (attempts < 3 && !importSuccess && isAutoSyncRunning.get()) {
                attempts++
                try {
                    val dto = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                        if (type == "movie") {
                            api.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
                        } else {
                            api.getSeriesDetails(seriesId = tmdbId, apiKey = apiKey)
                        }
                    }

                    if (dto == null) {
                        lastErr = "Timeout ao comunicar com TMDB (20s)"
                        delay(500L)
                        continue
                    }

                    val title = dto.title ?: dto.name
                    if (title.isNullOrBlank() || dto.id <= 0) {
                        ignoredCount++
                        importSuccess = true
                        currentProgress = currentProgress.copy(
                            processedCount = processedCount,
                            ignoredCount = ignoredCount,
                            inProgressCount = 0,
                            stepMessage = "Conteúdo TMDB #$tmdbId ignorado (dados essenciais incompletos)."
                        )
                        _tmdbAutoSyncProgress.value = currentProgress
                        emit(currentProgress)
                        break
                    }

                    val finalVideos = fetchVideosWithEnglishFallback(tmdbId, type, dto.videos?.results)
                    val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))
                    val entity = mapDtoToEntity(dtoWithMerged, type)

                    dao.insertMedia(entity)
                    firebaseService?.upsertMediaInCloud(entity)

                    if (type == "tv") {
                        currentProgress = currentProgress.copy(
                            stepMessage = "Importando episódios e temporadas de '${entity.title}'..."
                        )
                        _tmdbAutoSyncProgress.value = currentProgress
                        emit(currentProgress)
                        fetchAndStoreAllSeasonsAndEpisodes(entity.tmdbId, dto.numberOfSeasons)
                    }

                    importedCount++
                    importedTitles.add(entity.title)
                    importSuccess = true

                    currentProgress = currentProgress.copy(
                        processedCount = processedCount,
                        importedCount = importedCount,
                        currentTitle = entity.title,
                        inProgressCount = 0,
                        stepMessage = "Importado com sucesso (${importedCount}): '${entity.title}'."
                    )
                    _tmdbAutoSyncProgress.value = currentProgress
                    emit(currentProgress)

                    delay(150L)
                } catch (e: Exception) {
                    lastErr = e.localizedMessage ?: "Erro de rede"
                    delay(500L)
                }
            }

            if (!importSuccess && lastErr != null) {
                errorCount++
                Log.e("MediaRepository", "[TMDB AUTO-SYNC] Erro ao importar TMDB #$tmdbId: $lastErr")
                currentProgress = currentProgress.copy(
                    processedCount = processedCount,
                    errorCount = errorCount,
                    inProgressCount = 0,
                    stepMessage = "Aviso: Falha ao processar item #$tmdbId ($lastErr). Continuando..."
                )
                _tmdbAutoSyncProgress.value = currentProgress
                emit(currentProgress)
                delay(100L)
            }
        }

        val durationSec = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(1)
        val statusStr = if (importedCount > 0) "Concluído" else if (existingCount > 0 && errorCount == 0) "Concluído (Sem novos)" else if (errorCount > 0) "Parcial" else "Concluído"

        val summary = "Sincronização finalizada em ${durationSec}s: $importedCount importados, $existingCount já existentes, $ignoredCount ignorados, $errorCount erros."

        try {
            val historyItem = TmdbAutoSyncHistoryEntity(
                timestamp = startTime,
                type = if (isManual) "Manual" else "Automática",
                totalFound = uniqueCandidates.size,
                newMoviesFound = newMoviesFound,
                newSeriesFound = newSeriesFound,
                importedCount = importedCount,
                existingCount = existingCount,
                ignoredCount = ignoredCount,
                errorCount = errorCount,
                durationSeconds = durationSec,
                status = statusStr,
                details = if (importedTitles.isNotEmpty()) "Títulos: ${importedTitles.take(5).joinToString(", ")}${if (importedTitles.size > 5) "..." else ""}" else "Nenhum novo título necessário."
            )
            dao.insertTmdbAutoSyncHistory(historyItem)

            val updatedConfig = config.copy(
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncStatus = statusStr
            )
            saveTmdbAutoSyncConfig(updatedConfig)

            dao.insertAuditLog(
                AuditLogEntity(
                    user = if (isManual) "Administrador" else "Sistema Automático",
                    action = "Atualização TMDB ($statusStr)",
                    target = "$importedCount novos, $existingCount existentes"
                )
            )

            if (importedCount > 0) {
                val notif = NotificationEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "🎬 Novo Conteúdo no RONYCINE",
                    message = if (importedTitles.size == 1) "'${importedTitles.first()}' foi adicionado ao catálogo!" else "'${importedTitles.first()}' e outros ${importedTitles.size - 1} conteúdos foram adicionados ao catálogo!",
                    type = "NOVO_FILME",
                    timestamp = System.currentTimeMillis()
                )
                dao.insertNotification(notif)
                firebaseService?.sendNotificationToCloud(notif)
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "[TMDB AUTO-SYNC] Erro ao gravar histórico: ${e.message}", e)
        }

        currentProgress = currentProgress.copy(
            isRunning = false,
            isFinished = true,
            pendingCount = 0,
            inProgressCount = 0,
            durationSeconds = durationSec,
            lastResultSummary = summary,
            stepMessage = summary
        )
        _tmdbAutoSyncProgress.value = currentProgress
        emit(currentProgress)

        isAutoSyncRunning.set(false)
    }

    // --- PROFESSIONAL MASS IMPORT SYSTEM ---
    private val activeImportJobs = mutableMapOf<String, Job>()
    
    fun observeImportJobs(): Flow<List<ImportJob>> = callbackFlow {
        val db = firebaseService?.firestoreInstance ?: run {
            close()
            return@callbackFlow
        }
        
        val listener = db.collection("import_jobs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val jobs = snapshot.toObjects(ImportJob::class.java)
                    trySend(jobs)
                }
            }
        
        awaitClose { listener.remove() }
    }
    
    fun observeImportJob(jobId: String): Flow<ImportJob?> = callbackFlow {
        val db = firebaseService?.firestoreInstance ?: run {
            close()
            return@callbackFlow
        }
        
        val listener = db.collection("import_jobs").document(jobId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    trySend(snapshot.toObject(ImportJob::class.java))
                }
            }
        
        awaitClose { listener.remove() }
    }
    
    fun observeImportItems(jobId: String, limit: Int = 100): Flow<List<ImportItem>> = callbackFlow {
        val db = firebaseService?.firestoreInstance ?: run {
            close()
            return@callbackFlow
        }
        
        val listener = db.collection("import_items")
            .whereEqualTo("jobId", jobId)
            .orderBy("processedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val items = snapshot.toObjects(ImportItem::class.java)
                    trySend(items)
                }
            }
        
        awaitClose { listener.remove() }
    }
    
    suspend fun createMassImportJob(
        type: String,
        source: String,
        ids: List<Pair<Int, String>>,
        config: ImportConfig
    ): String = withContext(Dispatchers.IO) {
        val db = firebaseService?.firestoreInstance ?: throw IllegalStateException("Firestore unavailable")
        val jobId = "job_${System.currentTimeMillis()}"
        
        val job = ImportJob(
            id = jobId,
            type = type,
            source = source,
            total = ids.size,
            status = "queued",
            config = config
        )
        
        db.collection("import_jobs").document(jobId).set(job).await()
        
        // Add items in batches to Firestore
        val batchSize = 500
        ids.chunked(batchSize).forEach { chunk ->
            db.runBatch { batch ->
                chunk.forEach { (tmdbId, mediaType) ->
                    val itemId = "${jobId}_${mediaType}_${tmdbId}"
                    val item = ImportItem(
                        id = itemId,
                        jobId = jobId,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        status = "pending"
                    )
                    batch.set(db.collection("import_items").document(itemId), item)
                }
            }.await()
        }
        
        // Auto-start if it's the only one or if it's high priority
        startImportWorker(jobId)
        
        jobId
    }

    fun startImportWorker(jobId: String) {
        if (activeImportJobs.containsKey(jobId)) return
        
        val job = repoScope.launch {
            try {
                processImportJob(jobId)
            } catch (e: Exception) {
                Log.e("MediaRepository", "Error in import worker for $jobId: ${e.message}")
                updateJobStatus(jobId, "failed", error = e.localizedMessage)
            } finally {
                activeImportJobs.remove(jobId)
            }
        }
        activeImportJobs[jobId] = job
    }
    
    private suspend fun processImportJob(jobId: String) {
        val db = firebaseService?.firestoreInstance ?: return
        
        Log.d("MediaRepository", "[BATCH] Iniciando processamento do lote $jobId")
        updateJobStatus(jobId, "processing", startedAt = System.currentTimeMillis())
        
        val jobConfig = db.collection("import_jobs").document(jobId).get().await()
            .toObject(ImportJob::class.java)?.config ?: ImportConfig()
            
        val maxConcurrency = jobConfig.concurrentWorkers.coerceIn(1, 5)
            
        while (true) {
            val jobSnapshot = db.collection("import_jobs").document(jobId).get().await()
            val currentStatus = jobSnapshot.getString("status") ?: "failed"
            if (currentStatus != "processing") {
                Log.d("MediaRepository", "[BATCH] Lote $jobId parou com status: $currentStatus")
                break
            }
            
            // Fetch all items for this job to avoid composite index requirements
            val allItemsSnapshot = db.collection("import_items")
                .whereEqualTo("jobId", jobId)
                .get().await()
            
            val allItems = allItemsSnapshot.toObjects(ImportItem::class.java)
            val pendingItems = allItems.filter { it.status == "pending" }.take(maxConcurrency)
            
            if (pendingItems.isEmpty()) {
                val processingItems = allItems.filter { it.status == "processing" }
                val now = System.currentTimeMillis()
                val stuckItems = processingItems.filter { (now - (it.processedAt ?: now)) > 120000L } // stuck > 2 mins
                
                if (stuckItems.isNotEmpty()) {
                    Log.w("MediaRepository", "[BATCH] Encontrados ${stuckItems.size} itens travados em 'processing'. Reivindicando...")
                    stuckItems.forEach { stuck ->
                        if (stuck.retryCount >= 2) {
                            updateItemStatus(jobId, stuck.id, "failed", error = "Timeout excedido no processamento", stage = "ERRO")
                        } else {
                            db.collection("import_items").document(stuck.id).update(
                                mapOf(
                                    "status" to "pending",
                                    "retryCount" to stuck.retryCount + 1,
                                    "stage" to "PENDENTE"
                                )
                            ).await()
                        }
                    }
                    delay(500)
                    continue
                }
                
                // Check if any items are still processing
                if (processingItems.isEmpty()) {
                    val finalSuccess = allItems.count { it.status == "success" }
                    val finalFailed = allItems.count { it.status == "failed" || it.status == "incomplete" }
                    val finalStatus = if (finalFailed > 0 && finalSuccess == 0) "failed" else "completed"
                    Log.d("MediaRepository", "[BATCH] Lote $jobId concluído. Status final: $finalStatus")
                    updateJobStatus(jobId, finalStatus, finishedAt = System.currentTimeMillis())
                    break
                } else {
                    // Still processing items, wait a bit
                    delay(1000)
                    continue
                }
            }
            
            // Process pending items concurrently with maxConcurrency
            coroutineScope {
                pendingItems.map { item ->
                    async(Dispatchers.IO) {
                        importSingleItemInJob(jobId, item, jobConfig)
                    }
                }.awaitAll()
            }
            
            delay(150)
        }
    }
    
    private suspend fun importSingleItemInJob(jobId: String, item: ImportItem, config: ImportConfig) {
        val db = firebaseService?.firestoreInstance ?: return
        
        try {
            // Claim item: pending -> processing
            db.collection("import_items").document(item.id).update(
                mapOf(
                    "status" to "processing",
                    "stage" to "VALIDANDO",
                    "processedAt" to System.currentTimeMillis()
                )
            ).await()
            
            Log.d("MediaRepository", "[ITEM] Processando TMDB #${item.tmdbId} (${item.mediaType})")
            
            // 1. Check for duplicates
            val existingLocal = dao.getMediaByTmdbIdAndType(item.tmdbId, item.mediaType)
            if (existingLocal != null && !config.updateExisting) {
                updateItemStatus(jobId, item.id, "duplicate", title = existingLocal.title, stage = "JÁ EXISTE")
                return
            }
            
            // 2. Fetch from TMDB with timeout
            db.collection("import_items").document(item.id).update("stage", "METADADOS").await()
            val dto = kotlinx.coroutines.withTimeoutOrNull(20000L) {
                if (item.mediaType == "movie") {
                    api.getMovieDetails(item.tmdbId, apiKey)
                } else {
                    api.getSeriesDetails(item.tmdbId, apiKey)
                }
            }
            
            if (dto == null) {
                updateItemStatus(jobId, item.id, "failed", error = "Timeout de resposta do TMDB (20s)", stage = "ERRO")
                return
            }
            
            val title = dto.title ?: dto.name ?: "Sem título"
            if (title == "Sem título" || dto.id <= 0) {
                updateItemStatus(jobId, item.id, "incomplete", title = title, stage = "INCOMPLETO")
                return
            }
            
            // 3. Map and Save to Room & Firestore
            db.collection("import_items").document(item.id).update("stage", "FIRESTORE").await()
            val finalVideos = fetchVideosWithEnglishFallback(item.tmdbId, item.mediaType, dto.videos?.results)
            val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))
            val entity = mapDtoToEntity(dtoWithMerged, item.mediaType)
            
            dao.insertMedia(entity)
            firebaseService?.upsertMediaInCloud(entity)
            
            // Remove da lista de excluídos (se estiver lá) já que está sendo re-importado manualmente
            firebaseService?.removeFromDeletedCatalog(item.tmdbId, item.mediaType)
            
            if (entity.mediaType == "tv") {
                db.collection("import_items").document(item.id).update("stage", "EPISÓDIOS").await()
                fetchAndStoreAllSeasonsAndEpisodes(entity.tmdbId, dto.numberOfSeasons)
            }
            
            updateItemStatus(jobId, item.id, "success", title = entity.title, stage = "IMPORTADO")
            Log.d("MediaRepository", "[ITEM SUCCESS] TMDB #${item.tmdbId} (${entity.title}) importado com sucesso.")
            
        } catch (e: Exception) {
            Log.e("MediaRepository", "[ITEM ERROR] Falha ao importar TMDB #${item.tmdbId}: ${e.message}", e)
            if (config.retryErrors && item.retryCount < 2) {
                db.collection("import_items").document(item.id).update(
                    mapOf(
                        "status" to "pending",
                        "stage" to "PENDENTE",
                        "retryCount" to item.retryCount + 1,
                        "error" to (e.localizedMessage ?: "Erro desconhecido")
                    )
                ).await()
            } else {
                updateItemStatus(jobId, item.id, "failed", error = (e.localizedMessage ?: "Erro de processamento"), stage = "ERRO")
            }
        }
    }
    
    private suspend fun updateItemStatus(
        jobId: String, 
        itemId: String, 
        status: String, 
        title: String? = null,
        error: String? = null,
        stage: String? = null
    ) {
        val db = firebaseService?.firestoreInstance ?: return
        
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "processedAt" to System.currentTimeMillis()
        )
        title?.let { updates["title"] = it }
        error?.let { updates["error"] = it }
        stage?.let { updates["stage"] = it }
        
        try {
            db.collection("import_items").document(itemId).update(updates).await()
            
            val jobUpdates = mutableMapOf<String, Any>(
                "processed" to com.google.firebase.firestore.FieldValue.increment(1),
                "updatedAt" to System.currentTimeMillis()
            )
            when (status) {
                "success" -> jobUpdates["success"] = com.google.firebase.firestore.FieldValue.increment(1)
                "failed", "incomplete" -> jobUpdates["failed"] = com.google.firebase.firestore.FieldValue.increment(1)
                "duplicate" -> jobUpdates["duplicates"] = com.google.firebase.firestore.FieldValue.increment(1)
            }
            db.collection("import_jobs").document(jobId).update(jobUpdates).await()
        } catch (e: Exception) {
            Log.e("MediaRepository", "Erro ao atualizar status do item $itemId no Firestore: ${e.message}", e)
        }
    }
    
    private suspend fun updateJobStatus(
        jobId: String, 
        status: String, 
        startedAt: Long? = null, 
        finishedAt: Long? = null,
        error: String? = null
    ) {
        val db = firebaseService?.firestoreInstance ?: return
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to System.currentTimeMillis()
        )
        startedAt?.let { updates["startedAt"] = it }
        finishedAt?.let { updates["finishedAt"] = it }
        error?.let { updates["lastError"] = it }
        
        db.collection("import_jobs").document(jobId).update(updates).await()
    }
    
    suspend fun pauseImportJob(jobId: String) = updateJobStatus(jobId, "paused")
    suspend fun resumeImportJob(jobId: String) {
        updateJobStatus(jobId, "processing")
        startImportWorker(jobId)
    }
    suspend fun cancelImportJob(jobId: String) = updateJobStatus(jobId, "cancelled")

    suspend fun reprocessFailedItems(jobId: String) = withContext(Dispatchers.IO) {
        val db = firebaseService?.firestoreInstance ?: return@withContext
        
        val failedItems = db.collection("import_items")
            .whereEqualTo("jobId", jobId)
            .whereEqualTo("status", "failed")
            .get().await()
            .toObjects(ImportItem::class.java)
            
        if (failedItems.isEmpty()) return@withContext
        
        // Update job counters (decrement failed, decrement processed)
        val jobRef = db.collection("import_jobs").document(jobId)
        db.runTransaction { transaction ->
            val job = transaction.get(jobRef).toObject(ImportJob::class.java) ?: return@runTransaction
            transaction.update(jobRef, mapOf(
                "processed" to (job.processed - failedItems.size).coerceAtLeast(0),
                "failed" to (job.failed - failedItems.size).coerceAtLeast(0),
                "status" to "processing",
                "updatedAt" to System.currentTimeMillis()
            ))
            
            failedItems.forEach { item ->
                transaction.update(db.collection("import_items").document(item.id), mapOf(
                    "status" to "pending",
                    "retryCount" to 0,
                    "error" to null
                ))
            }
        }.await()
        
        startImportWorker(jobId)
    }

    suspend fun getGlobalStatsFromFirestore(): ImportSummary = withContext(Dispatchers.IO) {
        val db = firebaseService?.firestoreInstance
        if (db == null) {
            return@withContext ImportSummary(
                totalMovies = dao.getMovieCount(),
                totalSeries = dao.getSeriesCount(),
                activeJobsCount = 0
            )
        }
        try {
            val moviesCount = db.collection("catalog").whereEqualTo("mediaType", "movie").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            val seriesCount = db.collection("catalog").whereEqualTo("mediaType", "tv").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
            val activeJobs = db.collection("import_jobs").whereIn("status", listOf("queued", "processing", "paused")).get().await().size()
            
            ImportSummary(
                totalMovies = moviesCount.toInt(),
                totalSeries = seriesCount.toInt(),
                activeJobsCount = activeJobs
            )
        } catch (e: Exception) {
            Log.w("MediaRepository", "Firestore stats unavailable (${e.message}), falling back to local database counts.")
            ImportSummary(
                totalMovies = dao.getMovieCount(),
                totalSeries = dao.getSeriesCount(),
                activeJobsCount = 0
            )
        }
    }


    // --- Live Channels Flow & Operations ---
    val allLiveChannels: Flow<List<ChannelEntity>> = dao.getAllLiveChannels()

    suspend fun saveLiveChannel(channel: ChannelEntity) = withContext(Dispatchers.IO) {
        dao.insertChannel(channel)
        firebaseService?.upsertChannelInCloud(channel)
    }

    suspend fun deleteLiveChannel(channelId: String) = withContext(Dispatchers.IO) {
        dao.deleteChannelById(channelId)
        firebaseService?.deleteChannelFromCloud(channelId)
    }

    // --- Catalog Observables ---
    val allMedia: Flow<List<MediaEntity>> = dao.getAllMedia()
    val movies: Flow<List<MediaEntity>> = dao.getMediaByType("movie")
    val series: Flow<List<MediaEntity>> = dao.getMediaByType("tv")
    val featuredHeroMedia: Flow<List<MediaEntity>> = dao.getFeaturedHeroMedia()
    val myList: Flow<List<MediaEntity>> = dao.getMyList()
    val continueWatching: Flow<List<WatchHistoryEntity>> = dao.getContinueWatching().map { list ->
        list.distinctBy { it.tmdbId }
    }
    val watchHistory: Flow<List<WatchHistoryEntity>> = dao.getWatchHistory()

    // --- Featured Media Management Observables ---
    val activeFeaturedItems: Flow<List<FeaturedMediaItem>> = combine(
        dao.getActiveFeaturedMedia(),
        dao.getAllMedia()
    ) { featuredList, catalog ->
        val catalogMap = catalog.associateBy { it.tmdbId }
        featuredList.mapNotNull { featured ->
            val media = catalogMap[featured.mediaTmdbId]
            if (media != null) {
                FeaturedMediaItem(featured = featured, media = media)
            } else null
        }
    }

    val allFeaturedItems: Flow<List<FeaturedMediaItem>> = combine(
        dao.getAllFeaturedMedia(),
        dao.getAllMedia()
    ) { featuredList, catalog ->
        val catalogMap = catalog.associateBy { it.tmdbId }
        featuredList.mapNotNull { featured ->
            val media = catalogMap[featured.mediaTmdbId]
            if (media != null) {
                FeaturedMediaItem(featured = featured, media = media)
            } else null
        }
    }

    suspend fun saveFeaturedMedia(featured: FeaturedMediaEntity) = withContext(Dispatchers.IO) {
        dao.insertFeaturedMedia(featured)
        firebaseService?.syncFeaturedItemToCloud(featured)
    }

    suspend fun deleteFeaturedMedia(id: Int, mediaTmdbId: Int) = withContext(Dispatchers.IO) {
        dao.deleteFeaturedMediaById(id)
        firebaseService?.deleteFeaturedItemFromCloud(mediaTmdbId)
    }

    suspend fun toggleFeaturedActive(id: Int, mediaTmdbId: Int, isActive: Boolean) = withContext(Dispatchers.IO) {
        dao.setFeaturedActiveStatus(id, isActive)
        val updated = dao.getFeaturedById(id) ?: dao.getFeaturedByTmdbId(mediaTmdbId)
        if (updated != null) {
            firebaseService?.syncFeaturedItemToCloud(updated)
        }
    }


    // --- Statistics Observables ---
    val movieCount: Flow<Int> = dao.observeMovieCount()
    val seriesCount: Flow<Int> = dao.observeSeriesCount()
    val episodeCount: Flow<Int> = dao.observeEpisodeCount()
    val myListCount: Flow<Int> = dao.observeMyListCount()
    val watchHistoryCount: Flow<Int> = dao.observeWatchHistoryCount()
    val latestAddedMedia: Flow<MediaEntity?> = dao.observeLatestAddedMedia()
    val latestMovie: Flow<MediaEntity?> = dao.observeLatestMovie()
    val latestSeries: Flow<MediaEntity?> = dao.observeLatestSeries()
    val latestWatchHistory: Flow<WatchHistoryEntity?> = dao.observeLatestWatchHistory()
    val popularMedia: Flow<List<MediaEntity>> = dao.observePopularMedia()
    
    // Popular from TMDB (simplified for Admin)
    val popularMovies: Flow<List<MediaEntity>> = flow {
        try {
            val response = api.getPopularMovies(apiKey = apiKey, page = 1)
            emit(response.results.map { mapDtoToEntity(it, "movie") })
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    val popularSeries: Flow<List<MediaEntity>> = flow {
        try {
            val response = api.getPopularSeries(apiKey = apiKey, page = 1)
            emit(response.results.map { mapDtoToEntity(it, "tv") })
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    suspend fun getMediaByTmdbId(tmdbId: Int, type: String? = null): MediaEntity? {
        return if (type != null) {
            dao.getMediaByTmdbIdAndType(tmdbId, type)
        } else {
            dao.getMediaByTmdbId(tmdbId)
        }
    }

    suspend fun fetchPopularMoviesFromTmdb(page: Int = 1): List<com.example.data.remote.TmdbMediaDto> = withContext(Dispatchers.IO) {
        try {
            api.getPopularMovies(apiKey = apiKey, page = page).results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPopularSeriesFromTmdb(page: Int = 1): List<com.example.data.remote.TmdbMediaDto> = withContext(Dispatchers.IO) {
        try {
            api.getPopularSeries(apiKey = apiKey, page = page).results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getOrFetchMediaByTmdbId(tmdbId: Int, type: String? = null): MediaEntity? = withContext(Dispatchers.IO) {
        val local = getMediaByTmdbId(tmdbId, type)
        if (local != null) return@withContext local

        // If not in local database, fetch metadata from TMDB for temporary display (do NOT save to DB)
        val mediaType = type ?: "movie"
        try {
            if (mediaType == "movie" || mediaType == "filme") {
                val dto = api.getMovieDetails(tmdbId, apiKey)
                mapDtoToEntity(dto, "movie")
            } else {
                val dto = api.getSeriesDetails(tmdbId, apiKey)
                mapDtoToEntity(dto, "tv")
            }
        } catch (e: Exception) {
            null
        }
    }
    fun observeMediaByTmdbId(tmdbId: Int): Flow<MediaEntity?> = dao.observeMediaByTmdbId(tmdbId)
    fun isMediaInMyList(tmdbId: Int): Flow<Boolean> = dao.isMediaInMyList(tmdbId)

    // --- Initial Seed & Protection ---
    suspend fun seedInitialCatalogIfEmpty() = withContext(Dispatchers.IO) {
        val currentCount = dao.getMediaCount()
        if (currentCount == 0) {
            // Seed initial cinematic items so user has rich catalog immediately
            val initialItems = getInitialSeedData()
            dao.insertMediaList(initialItems)

            // Try fetching popular TMDB items in background if network available
            try {
                fetchAndImportPopularTmdbItems()
            } catch (e: Exception) {
                // Keep local fallback seed safe!
            }
        }
    }

    private suspend fun fetchAndImportPopularTmdbItems() {
        val popMovies = api.getPopularMovies(apiKey = apiKey, page = 1)
        val popSeries = api.getPopularSeries(apiKey = apiKey, page = 1)

        val entities = mutableListOf<MediaEntity>()
        popMovies.results.take(10).forEach { dto ->
            entities.add(mapDtoToEntity(dto, "movie", isHero = true))
        }
        popSeries.results.take(10).forEach { dto ->
            entities.add(mapDtoToEntity(dto, "tv", isHero = false))
        }

        if (entities.isNotEmpty()) {
            enforceCatalogGuardAndInsert(entities, "TMDB_AUTO_SYNC")
        }
    }

    // --- Catalog Guard ---
    suspend fun enforceCatalogGuardAndInsert(
        newItems: List<MediaEntity>,
        actionName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val currentCount = dao.getMediaCount()

        // Catalog Guard: Never replace catalog with empty list!
        if (newItems.isEmpty() && currentCount > 0) {
            return@withContext false
        }

        dao.insertMediaList(newItems)
        return@withContext true
    }

    // --- Import Content By TMDB ID ---
    private suspend fun fetchVideosWithEnglishFallback(tmdbId: Int, type: String, ptVideos: List<com.example.data.remote.TmdbVideoDto>?): List<com.example.data.remote.TmdbVideoDto> {
        val list = ptVideos ?: emptyList()
        val hasGoodTrailer = list.any { it.site.equals("YouTube", ignoreCase = true) && (it.type == "Trailer" || it.type == "Teaser") }
        if (hasGoodTrailer) {
            return list
        }
        return try {
            val enVideosResponse = if (type == "movie") {
                api.getMovieVideos(movieId = tmdbId, apiKey = apiKey, language = "en-US")
            } else {
                api.getSeriesVideos(seriesId = tmdbId, apiKey = apiKey, language = "en-US")
            }
            list + (enVideosResponse.results ?: emptyList())
        } catch (e: Exception) {
            list
        }
    }

    suspend fun importByTmdbId(tmdbId: Int, type: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.d("MediaRepository", "[IMPORT] iniciado | TMDB ID: $tmdbId | tipo: $type")
        val existing = dao.getMediaByTmdbId(tmdbId)
        if (existing != null) {
            Log.d("MediaRepository", "[IMPORT] duplicidade verificada: conteúdo já existe.")
            return@withContext Pair(false, "Este título já está no catálogo.")
        }

        try {
            Log.d("MediaRepository", "[IMPORT] buscando dados do TMDB...")
            val dto = kotlinx.coroutines.withTimeout(15000L) {
                if (type == "movie") {
                    api.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
                } else {
                    api.getSeriesDetails(seriesId = tmdbId, apiKey = apiKey)
                }
            }
            Log.d("MediaRepository", "[IMPORT] dados recebidos do TMDB.")

            val finalVideos = fetchVideosWithEnglishFallback(tmdbId, type, dto.videos?.results)
            val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))

            Log.d("MediaRepository", "[IMPORT] preparando documento...")
            val entity = mapDtoToEntity(dtoWithMerged, if (type.isNotBlank()) type else (dto.mediaType ?: "movie"))
            
            Log.d("MediaRepository", "[IMPORT] salvando no Firestore...")
            kotlinx.coroutines.withTimeout(15000L) {
                dao.insertMedia(entity)
                firebaseService?.upsertMediaInCloud(entity)

                // If series, fetch season 1 episodes
                if (entity.mediaType == "tv") {
                    fetchAndStoreEpisodes(entity.tmdbId, 1)
                }
            }
            Log.d("MediaRepository", "[IMPORT] Firestore confirmou e concluído.")

            Pair(true, "Conteúdo '${entity.title}' importado com sucesso!")
        } catch (e: Exception) {
            val errorCode = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            Log.e("MediaRepository", "[IMPORT ERROR] code=$errorCode, message=${e.message}, stage=importByTmdbId", e)
            val userMsg = when (errorCode) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                    "Você não tem permissão para importar conteúdo."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                    "Servidor indisponível ou tempo limite esgotado. Tente novamente."
                else -> "Erro ao importar do TMDB: ${e.localizedMessage ?: "Erro desconhecido"}"
            }
            Pair(false, userMsg)
        }
    }

    // --- Mass Import Progress Flow & Cancellation ---
    data class MassImportProgress(
        val total: Int = 0,
        val processed: Int = 0,
        val success: Int = 0,
        val existing: Int = 0,
        val failed: Int = 0,
        val cancelled: Boolean = false,
        val isFinished: Boolean = false,
        val currentTitle: String = ""
    )

    private var massImportJob: Job? = null
    private val isMassImportCancelled = AtomicBoolean(false)

    fun cancelMassImport() {
        isMassImportCancelled.set(true)
        massImportJob?.cancel()
    }

    fun startMassImport(pagesToImport: Int = 3) = flow {
        isMassImportCancelled.set(false)
        var progress = MassImportProgress()
        emit(progress)

        val itemsToProcess = mutableListOf<Pair<Int, String>>()

        try {
            for (p in 1..pagesToImport) {
                if (isMassImportCancelled.get()) break
                val moviesPage = api.getPopularMovies(apiKey = apiKey, page = p)
                moviesPage.results.forEach { itemsToProcess.add(Pair(it.id, "movie")) }

                val seriesPage = api.getPopularSeries(apiKey = apiKey, page = p)
                seriesPage.results.forEach { itemsToProcess.add(Pair(it.id, "tv")) }
            }
        } catch (e: Exception) {
            // proceed with whatever we collected
        }

        progress = progress.copy(total = itemsToProcess.size)
        emit(progress)

        for (item in itemsToProcess) {
            if (isMassImportCancelled.get()) {
                progress = progress.copy(cancelled = true, isFinished = true)
                emit(progress)
                return@flow
            }

            val existing = dao.getMediaByTmdbId(item.first)
            if (existing != null) {
                progress = progress.copy(
                    processed = progress.processed + 1,
                    existing = progress.existing + 1,
                    currentTitle = existing.title
                )
                emit(progress)
                continue
            }

            try {
                val dto = if (item.second == "movie") {
                    api.getMovieDetails(item.first, apiKey)
                } else {
                    api.getSeriesDetails(item.first, apiKey)
                }

                val entity = mapDtoToEntity(dto, item.second)
                dao.insertMedia(entity)
                firebaseService?.upsertMediaInCloud(entity)

                if (entity.mediaType == "tv") {
                    fetchAndStoreEpisodes(entity.tmdbId, 1)
                }

                progress = progress.copy(
                    processed = progress.processed + 1,
                    success = progress.success + 1,
                    currentTitle = entity.title
                )
                emit(progress)
            } catch (e: Exception) {
                progress = progress.copy(
                    processed = progress.processed + 1,
                    failed = progress.failed + 1
                )
                emit(progress)
            }
        }

        progress = progress.copy(isFinished = true)
        emit(progress)
    }

    // In-memory cache for admin TMDB search
    private val adminSearchCache = java.util.concurrent.ConcurrentHashMap<String, List<TmdbSearchResultItem>>()

    suspend fun searchTmdbForAdmin(query: String, filter: String = "all"): List<TmdbSearchResultItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase().replace(Regex("\\s+"), " ")
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val cacheKey = "$cleanQuery:$filter"
        adminSearchCache[cacheKey]?.let { return@withContext it }

        try {
            val response = api.searchMulti(apiKey = apiKey, query = cleanQuery)
            val items = response.results.mapNotNull { dto ->
                val type = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
                if (type == "movie" || type == "tv") {
                    if (filter == "movie" && type != "movie") return@mapNotNull null
                    if (filter == "tv" && type != "tv") return@mapNotNull null

                    val entity = mapDtoToEntity(dto, type)
                    val exists = dao.getMediaByTmdbIdAndType(entity.tmdbId, type) != null
                    TmdbSearchResultItem(entity = entity, isAlreadyInCatalog = exists)
                } else null
            }
            adminSearchCache[cacheKey] = items
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTmdbPreview(tmdbId: Int, type: String): Pair<MediaEntity?, Boolean> = withContext(Dispatchers.IO) {
        val existing = dao.getMediaByTmdbIdAndType(tmdbId, type)
        try {
            val dto = if (type == "movie") {
                api.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
            } else {
                api.getSeriesDetails(seriesId = tmdbId, apiKey = apiKey)
            }
            val finalVideos = fetchVideosWithEnglishFallback(tmdbId, type, dto.videos?.results)
            val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))
            val entity = mapDtoToEntity(dtoWithMerged, if (type.isNotBlank()) type else (dto.mediaType ?: "movie"))
            Pair(entity, existing != null)
        } catch (e: Exception) {
            Pair(existing, existing != null)
        }
    }

    suspend fun importMediaEntity(entity: MediaEntity): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        Log.d("MediaRepository", "[IMPORT] iniciado | TMDB ID: ${entity.tmdbId} | tipo: ${entity.mediaType} | título: ${entity.title}")
        try {
            val existing = dao.getMediaByTmdbId(entity.tmdbId)
            val isUpdate = existing != null
            
            Log.d("MediaRepository", "[IMPORT] salvando no Firestore...")
            kotlinx.coroutines.withTimeout(20000L) {
                dao.insertMedia(entity)
                firebaseService?.upsertMediaInCloud(entity)

                if (entity.mediaType == "tv") {
                    fetchAndStoreEpisodes(entity.tmdbId, 1)
                }
            }
            Log.d("MediaRepository", "[IMPORT] Firestore confirmou e concluído.")
            
            val message = if (isUpdate) {
                "Conteúdo '${entity.title}' foi atualizado com sucesso no catálogo!"
            } else {
                "Conteúdo '${entity.title}' importado com sucesso para o catálogo do RONYCINE!"
            }
            Pair(true, message)
        } catch (e: Exception) {
            val errorCode = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            Log.e("MediaRepository", "[IMPORT ERROR] code=$errorCode, message=${e.message}, stage=importMediaEntity", e)
            val userMsg = when (errorCode) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                    "Você não tem permissão para importar conteúdo."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                    "Servidor indisponível ou tempo limite esgotado. Tente novamente."
                else -> "Erro ao salvar no banco: ${e.localizedMessage ?: "Erro desconhecido"}"
            }
            Pair(false, userMsg)
        }
    }

    suspend fun updateMediaInCatalog(entity: MediaEntity): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            dao.insertMedia(entity)
            firebaseService?.upsertMediaInCloud(entity)
            Pair(true, "Conteúdo '${entity.title}' atualizado com sucesso!")
        } catch (e: Exception) {
            Pair(false, "Erro ao atualizar conteúdo: ${e.localizedMessage}")
        }
    }

    suspend fun deleteMediaFromCatalog(tmdbId: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val existing = dao.getMediaByTmdbId(tmdbId)
            val title = existing?.title ?: "Item #$tmdbId"
            val type = existing?.mediaType ?: "movie"

            Log.d("MediaRepository", "[DELETE] Iniciando exclusão do item '$title' (ID $tmdbId, Tipo $type)")

            // 1. Exclui do Firestore primeiro (lança exceção em caso de falha)
            if (firebaseService != null) {
                firebaseService.deleteMediaFromCloud(tmdbId, type)
                Log.d("MediaRepository", "[DELETE] Firestore confirmado com sucesso.")
            } else {
                Log.w("MediaRepository", "[DELETE] Sem serviço do Firebase, excluindo apenas local.")
            }

            // 2. Só depois exclui do SQLite local
            dao.deleteMediaByTmdbId(tmdbId)
            dao.deleteEpisodesByMediaId(tmdbId)
            dao.deleteFromMyList(tmdbId)
            dao.deleteFromWatchHistory(tmdbId)

            Log.d("MediaRepository", "[DELETE] SQLite local atualizado.")
            Pair(true, "Conteúdo '$title' foi excluído do catálogo com sucesso.")
        } catch (e: Exception) {
            val errorCode = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            Log.e("MediaRepository", "[CATALOG DELETE ERROR] code=$errorCode, message=${e.message}", e)
            
            val userMsg = when (errorCode) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                    "Você não tem permissão para excluir este conteúdo."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> 
                    "Este conteúdo já foi removido."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                    "Servidor indisponível ou tempo limite esgotado. Verifique sua conexão."
                else -> "Erro ao excluir: ${e.localizedMessage ?: "Erro desconhecido"}"
            }
            Pair(false, userMsg)
        }
    }

    suspend fun deleteMediaBatchFromCatalog(items: List<Pair<Int, String>>, onProgress: (Int) -> Unit): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.d("MediaRepository", "[BULK-DELETE] Iniciando exclusão em massa de ${items.size} mídias.")
            
            // 1. Exclui do Firestore primeiro em lotes (lança exceção se falhar)
            if (firebaseService != null) {
                firebaseService.deleteMediaBatchFromCloud(items, onProgress)
                Log.d("MediaRepository", "[BULK-DELETE] Firestore lote confirmado com sucesso.")
            } else {
                Log.w("MediaRepository", "[BULK-DELETE] Sem serviço do Firebase, atualizando apenas localmente.")
                var tempCount = 0
                for (item in items) {
                    tempCount++
                    onProgress(tempCount)
                }
            }
            
            // 2. Só depois exclui do SQLite local
            for (item in items) {
                val tmdbId = item.first
                dao.deleteMediaByTmdbId(tmdbId)
                dao.deleteEpisodesByMediaId(tmdbId)
                dao.deleteFromMyList(tmdbId)
                dao.deleteFromWatchHistory(tmdbId)
            }
            
            Log.d("MediaRepository", "[BULK-DELETE] SQLite local lote atualizado com sucesso.")
            Pair(true, "${items.size} títulos foram excluídos do catálogo com sucesso.")
        } catch (e: Exception) {
            val errorCode = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            Log.e("MediaRepository", "[CATALOG BULK DELETE ERROR] code=$errorCode, message=${e.message}", e)
            
            val userMsg = when (errorCode) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                    "Você não tem permissão para excluir estes títulos."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                    "Servidor indisponível ou tempo limite esgotado no lote. Verifique sua conexão."
                else -> "Erro ao excluir títulos selecionados: ${e.localizedMessage ?: "Erro desconhecido"}"
            }
            Pair(false, userMsg)
        }
    }

    suspend fun refreshMediaFromTmdb(tmdbId: Int, type: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val dto = if (type == "movie") {
                api.getMovieDetails(movieId = tmdbId, apiKey = apiKey)
            } else {
                api.getSeriesDetails(seriesId = tmdbId, apiKey = apiKey)
            }
            val finalVideos = fetchVideosWithEnglishFallback(tmdbId, type, dto.videos?.results)
            val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))
            val existing = dao.getMediaByTmdbId(tmdbId)
            val entity = mapDtoToEntity(dtoWithMerged, if (type.isNotBlank()) type else (dto.mediaType ?: "movie"), isHero = existing?.isHeroFeatured ?: false)
            dao.insertMedia(entity)
            firebaseService?.upsertMediaInCloud(entity)

            if (entity.mediaType == "tv") {
                fetchAndStoreEpisodes(entity.tmdbId, 1)
            }
            Pair(true, "Informações de '${entity.title}' atualizadas com sucesso do TMDB!")
        } catch (e: Exception) {
            Pair(false, "Erro ao consultar TMDB: ${e.localizedMessage}")
        }
    }

    fun startMassImportByIds(idList: List<Pair<Int, String>>) = flow {
        isMassImportCancelled.set(false)
        var progress = MassImportProgress(total = idList.size)
        emit(progress)

        for (item in idList) {
            if (isMassImportCancelled.get()) {
                progress = progress.copy(cancelled = true, isFinished = true)
                emit(progress)
                return@flow
            }

            val existing = dao.getMediaByTmdbId(item.first)
            if (existing != null) {
                progress = progress.copy(
                    processed = progress.processed + 1,
                    existing = progress.existing + 1,
                    currentTitle = existing.title
                )
                emit(progress)
                continue
            }

            try {
                val dto = if (item.second == "movie") {
                    api.getMovieDetails(item.first, apiKey)
                } else {
                    api.getSeriesDetails(item.first, apiKey)
                }
                val entity = mapDtoToEntity(dto, item.second)
                dao.insertMedia(entity)
                firebaseService?.upsertMediaInCloud(entity)
                if (entity.mediaType == "tv") {
                    fetchAndStoreEpisodes(entity.tmdbId, 1)
                }

                progress = progress.copy(
                    processed = progress.processed + 1,
                    success = progress.success + 1,
                    currentTitle = entity.title
                )
                emit(progress)
            } catch (e: Exception) {
                progress = progress.copy(
                    processed = progress.processed + 1,
                    failed = progress.failed + 1
                )
                emit(progress)
            }
        }

        progress = progress.copy(isFinished = true)
        emit(progress)
    }

    // --- Intelligent Search System ---
    suspend fun checkVideoSourceAvailability(tmdbId: Int, mediaType: String): Boolean = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext false

        // 1. Check if item exists in MegaEmbed catalog API
        try {
            val megaEmbedItems = if (mediaType == "movie" || mediaType == "filme") {
                MegaEmbedService.fetchMegaEmbedMovies()
            } else {
                MegaEmbedService.fetchMegaEmbedSeries()
            }
            if (megaEmbedItems.any { it.tmdbId == tmdbId }) {
                return@withContext true
            }
        } catch (e: Exception) {
            // Ignore & continue
        }

        // 2. Probe RONYCINE embed endpoint (mgeb.top)
        try {
            val embedUrl = com.example.ui.components.EmbedUrlBuilder.buildUrl(mediaType, tmdbId, season = 1, episode = 1)
            if (embedUrl.isNotBlank() && isUrlReachableAndValid(embedUrl)) {
                return@withContext true
            }
        } catch (e: Exception) {
            // Ignore & continue
        }

        // 3. Probe MegaEmbed endpoint
        try {
            val megaUrl = MegaEmbedService.buildEmbedUrl(tmdbId, mediaType, season = 1, episode = 1)
            if (megaUrl.isNotBlank() && isUrlReachableAndValid(megaUrl)) {
                return@withContext true
            }
        } catch (e: Exception) {
            // Ignore & continue
        }

        false
    }

    private fun isUrlReachableAndValid(urlStr: String): Boolean {
        return try {
            val uri = java.net.URI(urlStr)
            if (uri.scheme == null || (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true))) {
                return false
            }
            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .head()
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }

    suspend fun performSmartSearch(query: String): List<SmartSearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1st stage: Check local DB catalog
        val localResults = try {
            dao.searchLocalMedia(query).first()
        } catch (e: Exception) {
            emptyList()
        }

        val localSmartItems = localResults.map { entity ->
            SmartSearchResultItem(
                entity = entity,
                status = SearchItemStatus.IN_CATALOG,
                statusLabel = "🟢 DISPONÍVEL",
                isLocalCatalog = true,
                hasValidSource = true,
                isPendingRequest = false
            )
        }

        // 2nd stage: TMDB search for items outside local catalog
        val tmdbEntities = try {
            val response = api.searchMulti(apiKey = apiKey, query = query)
            response.results.mapNotNull { dto ->
                val type = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
                if (type == "movie" || type == "tv") mapDtoToEntity(dto, type) else null
            }
        } catch (e: Exception) {
            emptyList()
        }

        val localKeys = localResults.map { "${it.tmdbId}_${it.mediaType}" }.toSet()
        val remoteOutsideCatalog = tmdbEntities.filter { entity ->
            !localKeys.contains("${entity.tmdbId}_${entity.mediaType}")
        }.distinctBy { "${it.tmdbId}_${it.mediaType}" }

        val pendingRequests = firebaseService?.allRequests?.value ?: emptyList()

        // 3rd stage: Evaluate source validity concurrently for remote items
        val remoteSmartItems = coroutineScope {
            remoteOutsideCatalog.map { entity ->
                async(Dispatchers.IO) {
                    val isPending = pendingRequests.any { req ->
                        req.tmdbId == entity.tmdbId && req.mediaType == entity.mediaType &&
                        (req.status.equals("pending", true) || req.status.equals("pendente", true) ||
                         req.status.equals("processing", true) || req.status.equals("processando", true))
                    }
                    val isAdded = pendingRequests.any { req ->
                        req.tmdbId == entity.tmdbId && req.mediaType == entity.mediaType &&
                        (req.status.equals("added", true) || req.status.equals("adicionado", true))
                    }

                    when {
                        isAdded -> SmartSearchResultItem(
                            entity = entity,
                            status = SearchItemStatus.IN_CATALOG,
                            statusLabel = "🟢 JÁ DISPONÍVEL",
                            isLocalCatalog = false,
                            hasValidSource = true,
                            isPendingRequest = false
                        )
                        isPending -> SmartSearchResultItem(
                            entity = entity,
                            status = SearchItemStatus.REQUEST_PENDING,
                            statusLabel = "🟡 PEDIDO ENVIADO",
                            isLocalCatalog = false,
                            hasValidSource = false,
                            isPendingRequest = true
                        )
                        else -> {
                            val hasSource = checkVideoSourceAvailability(entity.tmdbId, entity.mediaType)
                            if (hasSource) {
                                SmartSearchResultItem(
                                    entity = entity,
                                    status = SearchItemStatus.READY_TO_WATCH,
                                    statusLabel = "🟢 PRONTO PARA ASSISTIR",
                                    isLocalCatalog = false,
                                    hasValidSource = true,
                                    isPendingRequest = false
                                )
                            } else {
                                SmartSearchResultItem(
                                    entity = entity,
                                    status = SearchItemStatus.AVAILABLE_FOR_REQUEST,
                                    statusLabel = "🔴 DISPONÍVEL PARA PEDIDO",
                                    isLocalCatalog = false,
                                    hasValidSource = false,
                                    isPendingRequest = false
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        localSmartItems + remoteSmartItems
    }

    fun rankYouTubeVideos(videos: List<com.example.data.remote.TmdbVideoDto>?): List<com.example.data.remote.TmdbVideoDto> {
        if (videos.isNullOrEmpty()) return emptyList()
        val youtubeVideos = videos.filter {
            it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank()
        }.distinctBy { it.key }

        return youtubeVideos.sortedWith(compareBy { video ->
            val nameLower = video.name?.lowercase() ?: ""
            val videoType = video.type
            val isPT = video.iso_639_1?.lowercase()?.startsWith("pt") == true
            val isOfficial = video.official == true || nameLower.contains("oficial") || nameLower.contains("official")

            val baseScore = when {
                videoType.equals("Trailer", ignoreCase = true) && isOfficial && isPT -> 10
                videoType.equals("Trailer", ignoreCase = true) && isOfficial -> 20
                videoType.equals("Trailer", ignoreCase = true) && isPT -> 25
                videoType.equals("Trailer", ignoreCase = true) -> 30
                videoType.equals("Teaser", ignoreCase = true) && isOfficial && isPT -> 35
                videoType.equals("Teaser", ignoreCase = true) && isOfficial -> 40
                videoType.equals("Teaser", ignoreCase = true) -> 50
                videoType.equals("Featurette", ignoreCase = true) -> 60
                videoType.equals("Clip", ignoreCase = true) -> 70
                else -> 100
            }
            val languageBonus = if (isPT || nameLower.contains("dublado") || nameLower.contains("legendado")) -3 else 0
            baseScore + languageBonus
        })
    }

    suspend fun getTrailerCandidatesForMedia(tmdbId: Int, type: String): List<com.example.data.remote.TmdbVideoDto> = withContext(Dispatchers.IO) {
        try {
            // 1. Try PT-BR first
            val ptVideos = try {
                if (type == "movie") api.getMovieVideos(tmdbId, apiKey, "pt-BR")
                else api.getSeriesVideos(tmdbId, apiKey, "pt-BR")
            } catch (e: Exception) {
                null
            }

            // 2. Try EN-US fallback
            val enVideos = try {
                if (type == "movie") api.getMovieVideos(tmdbId, apiKey, "en-US")
                else api.getSeriesVideos(tmdbId, apiKey, "en-US")
            } catch (e: Exception) {
                null
            }

            // 3. Combined list
            val allVideos = (ptVideos?.results ?: emptyList()) + (enVideos?.results ?: emptyList())
            Log.d("TRAILER", "[TRAILER] TMDB ID: $tmdbId")
            
            val ranked = rankYouTubeVideos(allVideos)
            Log.d("TRAILER", "[TRAILER] Vídeos encontrados: ${ranked.size}")

            val bestVideo = ranked.firstOrNull()
            Log.d("TRAILER", "[TRAILER] Trailer selecionado: ${bestVideo?.name ?: "nenhum"} (Key: ${bestVideo?.key})")
            Log.d("TRAILER", "[TRAILER] ID do YouTube: ${bestVideo?.key ?: "nenhum"}")
            if (bestVideo?.key != null) {
                Log.d("TRAILER", "[TRAILER] URL de embed: https://www.youtube-nocookie.com/embed/${bestVideo.key}")
            }

            ranked
        } catch (e: Exception) {
            Log.e("TRAILER", "[TRAILER] Erro ao buscar vídeos TMDB: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getTrailerKeyForMedia(tmdbId: Int, type: String): String? = withContext(Dispatchers.IO) {
        getTrailerCandidatesForMedia(tmdbId, type).firstOrNull()?.key
    }

    // --- Search ---
    suspend fun searchLocalAndTmdb(query: String): List<MediaEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1st stage: Check local DB catalog
        val localResults = dao.searchLocalMedia(query).first()
        if (localResults.size >= 4) {
            return@withContext localResults
        }

        // 2nd stage: TMDB search
        return@withContext try {
            val response = api.searchMulti(apiKey = apiKey, query = query)
            val remoteEntities = response.results.mapNotNull { dto ->
                val type = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
                if (type == "movie" || type == "tv") mapDtoToEntity(dto, type) else null
            }
            // Combine local and remote without duplicates
            val combined = (localResults + remoteEntities).distinctBy { it.tmdbId }
            combined
        } catch (e: Exception) {
            localResults
        }
    }

    // --- Episodes ---
    fun getEpisodesForSeason(tmdbId: Int, seasonNumber: Int): Flow<List<EpisodeEntity>> {
        return dao.getEpisodesForSeason(tmdbId, seasonNumber)
    }

    suspend fun fetchAndStoreAllSeasonsAndEpisodes(tmdbId: Int, numberOfSeasons: Int? = null) {
        val totalSeasons = (numberOfSeasons ?: 1).coerceAtLeast(1)
        for (s in 1..totalSeasons) {
            try {
                fetchAndStoreEpisodes(tmdbId, s)
            } catch (e: Exception) {
                Log.w("MediaRepository", "Failed to fetch season $s for TV #$tmdbId: ${e.message}")
            }
        }
    }

    suspend fun fetchAndStoreEpisodes(tmdbId: Int, seasonNumber: Int) = withContext(Dispatchers.IO) {
        try {
            val seasonDto = api.getSeasonDetails(seriesId = tmdbId, seasonNumber = seasonNumber, apiKey = apiKey)
            val episodes = seasonDto.episodes?.map { ep ->
                EpisodeEntity(
                    mediaTmdbId = tmdbId,
                    seasonNumber = seasonNumber,
                    episodeNumber = ep.episodeNumber,
                    title = ep.name,
                    overview = ep.overview ?: "Sem sinopse disponível.",
                    duration = "${ep.runtime ?: 45} min",
                    stillPath = ep.stillPath
                )
            } ?: emptyList()

            if (episodes.isNotEmpty()) {
                dao.insertEpisodes(episodes)
                episodes.forEach { ep ->
                    firebaseService?.upsertEpisodeInCloud(ep)
                }
            }
        } catch (e: Exception) {
            // Keep default generated episodes if offline
            generateFallbackEpisodes(tmdbId, seasonNumber)
        }
    }

    private suspend fun generateFallbackEpisodes(tmdbId: Int, seasonNumber: Int) {
        val fallback = (1..8).map { epNum ->
            EpisodeEntity(
                mediaTmdbId = tmdbId,
                seasonNumber = seasonNumber,
                episodeNumber = epNum,
                title = "Episódio $epNum",
                overview = "Assistir ao episódio $epNum da Temporada $seasonNumber no RONYCINE.",
                duration = "45 min"
            )
        }
        dao.insertEpisodes(fallback)
    }

    // --- My List & History ---
    fun getWatchHistoryForMedia(tmdbId: Int): Flow<WatchHistoryEntity?> = dao.getWatchHistoryForMedia(tmdbId)
    fun getSimilarMedia(type: String, excludeTmdbId: Int): Flow<List<MediaEntity>> = dao.getSimilarMedia(type, excludeTmdbId)

    suspend fun toggleMyList(tmdbId: Int, mediaType: String) = withContext(Dispatchers.IO) {
        val inList = dao.isMediaInMyList(tmdbId).first()
        if (inList) {
            dao.removeFromMyList(tmdbId)
        } else {
            dao.addToMyList(MyListEntity(tmdbId = tmdbId, mediaType = mediaType))
        }
    }

    suspend fun saveWatchProgress(
        tmdbId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        progressPercent: Float,
        positionMs: Long,
        totalDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val isTv = mediaType == "tv" || mediaType == "serie"
        val existing = if (isTv) {
            dao.getWatchHistoryItemByKey(tmdbId, mediaType, seasonNumber, episodeNumber)
        } else {
            dao.getWatchHistoryItemByKey(tmdbId, mediaType, null, null)
        }
        val idToUse = existing?.id ?: 0
        dao.saveWatchProgress(
            WatchHistoryEntity(
                id = idToUse,
                tmdbId = tmdbId,
                mediaType = mediaType,
                title = title,
                posterPath = posterPath,
                seasonNumber = if (isTv) seasonNumber else null,
                episodeNumber = if (isTv) episodeNumber else null,
                progressPercent = progressPercent,
                lastWatchedPositionMs = positionMs,
                totalDurationMs = totalDurationMs,
                watchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFromContinueWatching(item: WatchHistoryEntity) = withContext(Dispatchers.IO) {
        if (item.id > 0) {
            dao.deleteWatchHistoryById(item.id)
        }
        if (item.tmdbId > 0) {
            dao.deleteWatchHistoryByTmdbId(item.tmdbId)
        }
    }

    suspend fun clearWatchHistory() = withContext(Dispatchers.IO) {
        dao.clearWatchHistory()
    }

    // --- MegaEmbed Configuration & Catalog Sync ---
    suspend fun getMegaEmbedConfig(): MegaEmbedConfig = withContext(Dispatchers.IO) {
        val player = dao.getSetting("megaembed_player") ?: "megaplay"
        val lang = dao.getSetting("megaembed_language") ?: "dubbed"
        val color = dao.getSetting("megaembed_color") ?: "E50914"
        val cacheHours = dao.getSetting("megaembed_cache_hours")?.toIntOrNull() ?: 2
        val autoSync = dao.getSetting("megaembed_auto_sync")?.toBoolean() ?: true

        val config = MegaEmbedConfig(
            defaultPlayer = player,
            defaultLanguage = lang,
            colorHex = color,
            cacheHours = cacheHours,
            isAutoSyncEnabled = autoSync
        )
        MegaEmbedService.updateConfig(config)
        config
    }

    suspend fun saveMegaEmbedConfig(config: MegaEmbedConfig) = withContext(Dispatchers.IO) {
        dao.saveSetting(AppSettingsEntity("megaembed_player", config.defaultPlayer))
        dao.saveSetting(AppSettingsEntity("megaembed_language", config.defaultLanguage))
        dao.saveSetting(AppSettingsEntity("megaembed_color", config.colorHex))
        dao.saveSetting(AppSettingsEntity("megaembed_cache_hours", config.cacheHours.toString()))
        dao.saveSetting(AppSettingsEntity("megaembed_auto_sync", config.isAutoSyncEnabled.toString()))

        MegaEmbedService.updateConfig(config)
    }

    data class ShareAppConfigData(
        val installUrl: String = AppShareConfig.defaultInstallUrl,
        val apkUrl: String = AppShareConfig.defaultApkUrl,
        val playStoreUrl: String = AppShareConfig.defaultPlayStoreUrl
    )

    suspend fun getShareAppConfig(): ShareAppConfigData = withContext(Dispatchers.IO) {
        val installUrl = dao.getSetting("share_install_url") ?: AppShareConfig.defaultInstallUrl
        val apkUrl = dao.getSetting("share_apk_url") ?: AppShareConfig.defaultApkUrl
        val playStoreUrl = dao.getSetting("share_playstore_url") ?: AppShareConfig.defaultPlayStoreUrl

        AppShareConfig.defaultInstallUrl = installUrl
        AppShareConfig.defaultApkUrl = apkUrl
        AppShareConfig.defaultPlayStoreUrl = playStoreUrl

        ShareAppConfigData(installUrl, apkUrl, playStoreUrl)
    }

    suspend fun saveShareAppConfig(config: ShareAppConfigData) = withContext(Dispatchers.IO) {
        dao.saveSetting(AppSettingsEntity("share_install_url", config.installUrl))
        dao.saveSetting(AppSettingsEntity("share_apk_url", config.apkUrl))
        dao.saveSetting(AppSettingsEntity("share_playstore_url", config.playStoreUrl))

        AppShareConfig.defaultInstallUrl = config.installUrl
        AppShareConfig.defaultApkUrl = config.apkUrl
        AppShareConfig.defaultPlayStoreUrl = config.playStoreUrl
    }

    private val isSyncRunning = AtomicBoolean(false)

    suspend fun getLastSyncFormatted(): String = withContext(Dispatchers.IO) {
        dao.getSetting("megaembed_last_sync") ?: "Nenhuma sincronização executada"
    }

    suspend fun syncMegaEmbedCatalog(): MegaEmbedSyncResult = withContext(Dispatchers.IO) {
        if (!isSyncRunning.compareAndSet(false, true)) {
            return@withContext MegaEmbedSyncResult(
                message = "A sincronização já está em andamento. Aguarde concluir."
            )
        }

        var newMovies = 0
        var newSeries = 0
        var updatedCount = 0
        var ignoredCount = 0
        var duplicateCount = 0
        var errorCount = 0

        try {
            val apiMovies = MegaEmbedService.fetchMegaEmbedMovies(forceRefresh = true)
            val apiSeries = MegaEmbedService.fetchMegaEmbedSeries(forceRefresh = true)

            if (apiMovies.isEmpty() && apiSeries.isEmpty()) {
                isSyncRunning.set(false)
                return@withContext MegaEmbedSyncResult(
                    message = "A API do MegaEmbed não retornou itens. O catálogo local foi mantido intacto para proteção dos dados."
                )
            }

            // Process movies
            for (item in apiMovies) {
                val tmdbId = item.tmdbId ?: continue
                try {
                    val existing = dao.getMediaByTmdbId(tmdbId)
                    val dto = api.getMovieDetails(tmdbId, apiKey)
                    val entity = mapDtoToEntity(dto, "movie")
                    if (existing != null) {
                        dao.insertMedia(entity)
                        updatedCount++
                    } else {
                        dao.insertMedia(entity)
                        newMovies++
                    }
                } catch (e: Exception) {
                    errorCount++
                }
            }

            // Process series
            for (item in apiSeries) {
                val tmdbId = item.tmdbId ?: continue
                try {
                    val existing = dao.getMediaByTmdbId(tmdbId)
                    val dto = api.getSeriesDetails(tmdbId, apiKey)
                    val entity = mapDtoToEntity(dto, "tv")
                    if (existing != null) {
                        dao.insertMedia(entity)
                        updatedCount++
                    } else {
                        dao.insertMedia(entity)
                        fetchAndStoreEpisodes(entity.tmdbId, 1)
                        newSeries++
                    }
                } catch (e: Exception) {
                    errorCount++
                }
            }

            val timestamp = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            dao.saveSetting(AppSettingsEntity("megaembed_last_sync", timestamp))

            val resultMsg = "Sincronização concluída • $newMovies filmes adicionados, $newSeries séries adicionadas, $updatedCount atualizados, $errorCount erros."

            MegaEmbedSyncResult(
                newMovies = newMovies,
                newSeries = newSeries,
                updatedCount = updatedCount,
                ignoredCount = ignoredCount,
                duplicateCount = duplicateCount,
                errorCount = errorCount,
                totalProcessed = apiMovies.size + apiSeries.size,
                lastSyncFormatted = timestamp,
                message = resultMsg
            )
        } catch (e: Exception) {
            MegaEmbedSyncResult(
                errorCount = 1,
                message = "Erro ao executar sincronização: ${e.localizedMessage ?: "Erro de rede ou timeout"}"
            )
        } finally {
            isSyncRunning.set(false)
        }
    }

    // --- Hero Rotation Settings ---
    suspend fun getHeroIntervalSeconds(): Int = withContext(Dispatchers.IO) {
        val setting = dao.getSetting("hero_interval")
        setting?.toIntOrNull() ?: 30
    }

    suspend fun setHeroIntervalSeconds(seconds: Int) = withContext(Dispatchers.IO) {
        dao.saveSetting(AppSettingsEntity("hero_interval", seconds.toString()))
    }

    // --- Statistics ---
    suspend fun getStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf(
            "movies" to dao.getMovieCount(),
            "series" to dao.getSeriesCount(),
            "views" to (dao.getMovieCount() * 14 + dao.getSeriesCount() * 28 + 120)
        )
    }

    // --- Helper DTO Mapping ---
    private fun mapDtoToEntity(dto: TmdbMediaDto, defaultType: String, isHero: Boolean = false): MediaEntity {
        val title = dto.title ?: dto.name ?: "Sem título"
        val originalTitle = dto.originalTitle ?: dto.originalName ?: title
        val type = dto.mediaType ?: defaultType
        val year = (dto.releaseDate ?: dto.firstAirDate ?: "2024").take(4)
        val poster = dto.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val backdrop = dto.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
        val rating = dto.voteAverage ?: 8.0
        val castStr = dto.credits?.cast?.take(10)?.joinToString(";") {
            val name = it.name.trim()
            val char = it.character?.replace("|", "/")?.replace(";", ",")?.trim() ?: ""
            val profile = it.profilePath?.trim() ?: ""
            "$name|$char|$profile"
        } ?: "Atores Principais"
        val directorStr = dto.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "Direção Aclamada"
        
        val trailerKey = rankYouTubeVideos(dto.videos?.results).firstOrNull()?.key

        return MediaEntity(
            tmdbId = dto.id,
            title = title,
            originalTitle = originalTitle,
            mediaType = type,
            posterPath = poster,
            backdropPath = backdrop,
            overview = if (dto.overview.isNullOrBlank()) "Uma emocionante história exclusiva para você assistir no RONYCINE." else dto.overview,
            releaseYear = year,
            rating = Math.round(rating * 10) / 10.0,
            genres = "Ação, Drama, Lançamentos",
            durationMinutes = dto.runtime ?: 118,
            cast = castStr,
            director = directorStr,
            seasonsCount = dto.numberOfSeasons ?: 1,
            episodesCount = dto.numberOfEpisodes ?: 12,
            trailerKey = trailerKey,
            isHeroFeatured = isHero
        )
    }

    // Fallback Initial Seed Catalog
    private fun getInitialSeedData(): List<MediaEntity> {
        return listOf(
            MediaEntity(
                tmdbId = 157336,
                title = "Interstellar",
                originalTitle = "Interstellar",
                mediaType = "movie",
                posterPath = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                backdropPath = "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK238Rsnv.jpg",
                overview = "Uma equipe de exploradores viaja através de um buraco de minhoca no espaço em uma tentativa de garantir a sobrevivência da humanidade.",
                releaseYear = "2014",
                rating = 8.6,
                genres = "Ficção Científica, Aventura, Drama",
                durationMinutes = 169,
                cast = "Matthew McConaughey, Anne Hathaway, Jessica Chastain",
                director = "Christopher Nolan",
                trailerKey = "zSWdZVtXT7E",
                isHeroFeatured = true
            ),
            MediaEntity(
                tmdbId = 1396,
                title = "Breaking Bad",
                originalTitle = "Breaking Bad",
                mediaType = "tv",
                posterPath = "https://image.tmdb.org/t/p/w500/ztSlA38C61TDEe3A4X1oT8k75g.jpg",
                backdropPath = "https://image.tmdb.org/t/p/w1280/tsRy63MuScu8etL1X2ZL1S9L81I.jpg",
                overview = "Ao descobrir que tem câncer terminal, um professor de química do ensino médio se junta a um ex-aluno para fabricar e vender metanfetamina.",
                releaseYear = "2008",
                rating = 9.5,
                genres = "Drama, Crime, Suspense",
                seasonsCount = 5,
                episodesCount = 62,
                cast = "Bryan Cranston, Aaron Paul, Anna Gunn",
                director = "Vince Gilligan",
                trailerKey = "HhesaQXLuRY",
                isHeroFeatured = true
            ),
            MediaEntity(
                tmdbId = 27205,
                title = "A Origem",
                originalTitle = "Inception",
                mediaType = "movie",
                posterPath = "https://image.tmdb.org/t/p/w500/oYuLE1s321A2y9RdR134jY9i5C5.jpg",
                backdropPath = "https://image.tmdb.org/t/p/w1280/8ZTVqv84C8P05G12m80p33m2213.jpg",
                overview = "Um ladrão que rouba segredos corporativos através do uso da tecnologia de compartilhamento de sonhos recebe a tarefa de plantar uma ideia na mente de um CEO.",
                releaseYear = "2010",
                rating = 8.8,
                genres = "Ação, Ficção Científica",
                durationMinutes = 148,
                cast = "Leonardo DiCaprio, Joseph Gordon-Levitt, Elliot Page",
                director = "Christopher Nolan",
                trailerKey = "YoHD9XEInc0",
                isHeroFeatured = true
            ),
            MediaEntity(
                tmdbId = 66732,
                title = "Stranger Things",
                originalTitle = "Stranger Things",
                mediaType = "tv",
                posterPath = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn88qEKoJv.jpg",
                backdropPath = "https://image.tmdb.org/t/p/w1280/56v2KjK1OA3323030303.jpg",
                overview = "Quando um garoto desaparece, uma pequena cidade descobre um mistério envolvendo experimentos secretos, forças sobrenaturais e uma garotinha estranha.",
                releaseYear = "2016",
                rating = 8.6,
                genres = "Ficção Científica, Mistério, Terror",
                seasonsCount = 4,
                episodesCount = 34,
                cast = "Millie Bobby Brown, Finn Wolfhard, Winona Ryder",
                director = "The Duffer Brothers",
                trailerKey = "b9EkMc79ZSU",
                isHeroFeatured = true
            ),
            MediaEntity(
                tmdbId = 299536,
                title = "Vingadores: Ultimato",
                originalTitle = "Avengers: Endgame",
                mediaType = "movie",
                posterPath = "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
                backdropPath = "https://image.tmdb.org/t/p/w1280/7RyG1533939.jpg",
                overview = "Após os eventos devastadores de Vingadores: Guerra Infinita, o universo está em ruínas. Com a ajuda dos aliados restantes, os Vingadores se reúnem.",
                releaseYear = "2019",
                rating = 8.3,
                genres = "Ação, Aventura, Ficção Científica",
                durationMinutes = 181,
                cast = "Robert Downey Jr., Chris Evans, Mark Ruffalo",
                director = "Anthony Russo, Joe Russo",
                trailerKey = "TcMBFSGVi1c",
                isHeroFeatured = false
            ),
            MediaEntity(
                tmdbId = 155,
                title = "Batman: O Cavaleiro das Trevas",
                originalTitle = "The Dark Knight",
                mediaType = "movie",
                posterPath = "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911221123.jpg",
                backdropPath = "https://image.tmdb.org/t/p/w1280/nMK2832819.jpg",
                overview = "Com a ajuda de Jim Gordon e Harvey Dent, Batman mantém a ordem em Gotham até que um criminoso sádico conhecido como Coringa traz o caos.",
                releaseYear = "2008",
                rating = 9.0,
                genres = "Ação, Crime, Drama",
                durationMinutes = 152,
                cast = "Christian Bale, Heath Ledger, Aaron Eckhart",
                director = "Christopher Nolan",
                trailerKey = "EXeTwQWrcwY",
                isHeroFeatured = false
            )
        )
    }

    // --- Real-Time Notification Operations ---
    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()
    val unreadNotifications: Flow<List<NotificationEntity>> = dao.getUnreadNotifications()
    val unreadNotificationsCount: Flow<Int> = dao.observeUnreadNotificationsCount()

    suspend fun sendNotification(notification: NotificationEntity) = withContext(Dispatchers.IO) {
        dao.insertNotification(notification)
        firebaseService?.sendNotificationToCloud(notification)
    }

    suspend fun markNotificationAsRead(id: String) = withContext(Dispatchers.IO) {
        dao.markNotificationAsRead(id)
        firebaseService?.markNotificationAsReadInCloud(id)
    }

    suspend fun markAllNotificationsAsRead() = withContext(Dispatchers.IO) {
        dao.markAllNotificationsAsRead()
    }

    suspend fun deleteNotification(id: String) = withContext(Dispatchers.IO) {
        dao.deleteNotificationById(id)
        firebaseService?.deleteNotificationFromCloud(id)
    }

    // --- Media Requests ---
    val allMediaRequests: Flow<List<MediaRequest>> = firebaseService?.allRequests ?: flow { emit(emptyList()) }

    suspend fun sendMediaRequest(request: MediaRequest): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        android.util.Log.d("MediaRepository", "[REQUEST] Iniciando envio: ${request.title}")
        android.util.Log.d("MediaRepository", "[REQUEST] tmdbId: ${request.tmdbId}")
        android.util.Log.d("MediaRepository", "[REQUEST] mediaType: ${request.mediaType}")

        if (firebaseService == null) {
            android.util.Log.e("MediaRepository", "[REQUEST ERROR] FirebaseService é nulo - Firebase não inicializado.")
            return@withContext Pair(false, "Serviço de pedidos indisponível no momento (Firebase não inicializado).")
        }
        
        android.util.Log.d("MediaRepository", "[REQUEST] Firebase inicializado")
        android.util.Log.d("MediaRepository", "[REQUEST] Firestore disponível")

        // 1. Validação dos dados do pedido
        if (request.tmdbId <= 0 || request.title.isBlank()) {
            android.util.Log.w("MediaRepository", "[REQUEST ERROR] Falha na validação: tmdbId ou título inválido")
            return@withContext Pair(false, "Dados inválidos: Conteúdo não possui identificador TMDB ou título válido.")
        }

        // 2. Verificar se já existe no catálogo local
        android.util.Log.d("MediaRepository", "[REQUEST] Verificando catálogo")
        val inCatalog = dao.getMediaByTmdbIdAndType(request.tmdbId, request.mediaType)
        if (inCatalog != null) {
            android.util.Log.d("MediaRepository", "[REQUEST] Conteúdo já disponível no catálogo local.")
            return@withContext Pair(false, "Este título já está disponível no catálogo.")
        }

        // 3. Verificar se já existe um pedido pendente ou adicionado
        android.util.Log.d("MediaRepository", "[REQUEST] Verificando pedido existente")
        val exists = try {
            firebaseService.checkRequestExists(request.tmdbId, request.mediaType)
        } catch (e: Exception) {
            false
        }
        if (exists) {
            android.util.Log.d("MediaRepository", "[REQUEST] Já existe pedido pendente para este conteúdo.")
            return@withContext Pair(false, "Pedido já enviado. Analisaremos sua solicitação em breve.")
        }
        
        // 4. Gravação no Firestore com aguardo real da confirmação
        try {
            android.util.Log.d("MediaRepository", "[REQUEST] Criando pedido")
            firebaseService.sendMediaRequest(request)
            val reqId = "req_${request.mediaType}_${request.tmdbId}"
            android.util.Log.d("MediaRepository", "[REQUEST] Pedido gravado")
            android.util.Log.d("MediaRepository", "[REQUEST] requestId: $reqId")
            android.util.Log.d("MediaRepository", "[REQUEST] Finalizado")
            Pair(true, "Pedido enviado com sucesso!")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e("MediaRepository", "[REQUEST ERROR] Timeout esgotado aguardando Firestore", e)
            Pair(false, "O servidor demorou para responder. Tente novamente.")
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            android.util.Log.e("MediaRepository", "[REQUEST ERROR] code: ${e.code} message: ${e.message}", e)
            val msg = when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Você não possui permissão para enviar pedidos."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Servidor temporariamente indisponível. Tente novamente."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Falha de autenticação com o servidor."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "O servidor demorou para responder. Tente novamente."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                    "Não foi possível enviar o pedido no momento."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
                    "Recurso não encontrado no servidor."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                    "Limite de requisições atingido. Tente mais tarde."
                else -> "Não foi possível enviar o pedido."
            }
            Pair(false, msg)
        } catch (e: IllegalStateException) {
            android.util.Log.e("MediaRepository", "[REQUEST ERROR] Configuração ausente ou Firebase não iniciado", e)
            Pair(false, e.message ?: "Não foi possível enviar o pedido.")
        } catch (e: java.io.IOException) {
            android.util.Log.e("MediaRepository", "[REQUEST ERROR] Erro de I/O / Rede", e)
            Pair(false, "Verifique sua conexão com a internet.")
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "[REQUEST ERROR] Erro inesperado: ${e.message}", e)
            Pair(false, "Não foi possível enviar o pedido.")
        }
    }

    suspend fun updateMediaRequestStatus(requestId: String, status: String) = withContext(Dispatchers.IO) {
        firebaseService?.updateMediaRequestStatus(requestId, status)
    }

    suspend fun updateExistingSeries(tmdbId: Int): SeriesUpdateResult = withContext(Dispatchers.IO) {
        try {
            Log.d("MediaRepository", "[UPDATE SERIES] Iniciando atualização incremental para TMDB ID: $tmdbId")
            val apiKey = apiKey ?: return@withContext SeriesUpdateResult(false, 0, 0, 0, 0, 1, "API Key ausente")
            
            val dto = api.getSeriesDetails(seriesId = tmdbId, apiKey = apiKey)
            val finalVideos = fetchVideosWithEnglishFallback(tmdbId, "tv", dto.videos?.results)
            val dtoWithMerged = dto.copy(videos = com.example.data.remote.TmdbVideosResponse(results = finalVideos))
            val entity = mapDtoToEntity(dtoWithMerged, "tv")
            
            dao.insertMedia(entity)
            firebaseService?.upsertMediaInCloud(entity)
            
            val totalSeasons = (dto.numberOfSeasons ?: 1).coerceAtLeast(1)
            
            val existingEpisodes = dao.getAllEpisodesForMediaSync(tmdbId)
            val existingSet = existingEpisodes.map { "${it.seasonNumber}_${it.episodeNumber}" }.toSet()
            
            var seasonsVerified = 0
            var episodesFound = 0
            val episodesExisting = existingEpisodes.size
            var episodesAdded = 0
            var errorsCount = 0
            
            val seasonsToCheck = (1..totalSeasons).toList()
            
            for (seasonNum in seasonsToCheck) {
                seasonsVerified++
                try {
                    val seasonDto = api.getSeasonDetails(seriesId = tmdbId, seasonNumber = seasonNum, apiKey = apiKey)
                    val seasonEpisodes = seasonDto.episodes ?: emptyList()
                    episodesFound += seasonEpisodes.size
                    
                    val newEpisodesToInsert = mutableListOf<EpisodeEntity>()
                    for (ep in seasonEpisodes) {
                        val key = "${seasonNum}_${ep.episodeNumber}"
                        if (!existingSet.contains(key)) {
                            val episodeEntity = EpisodeEntity(
                                mediaTmdbId = tmdbId,
                                seasonNumber = seasonNum,
                                episodeNumber = ep.episodeNumber,
                                title = ep.name ?: "Episódio ${ep.episodeNumber}",
                                overview = ep.overview ?: "Sem sinopse disponível.",
                                duration = "${ep.runtime ?: 45} min",
                                stillPath = ep.stillPath
                            )
                            newEpisodesToInsert.add(episodeEntity)
                        }
                    }
                    
                    if (newEpisodesToInsert.isNotEmpty()) {
                        dao.insertEpisodes(newEpisodesToInsert)
                        newEpisodesToInsert.forEach { ep ->
                            firebaseService?.upsertEpisodeInCloud(ep)
                        }
                        episodesAdded += newEpisodesToInsert.size
                        Log.d("MediaRepository", "[UPDATE SERIES] Adicionados ${newEpisodesToInsert.size} episódios na Temporada $seasonNum")
                    }
                    delay(100L)
                } catch (e: Exception) {
                    errorsCount++
                    Log.e("MediaRepository", "[UPDATE SERIES ERROR] Falha ao verificar temporada $seasonNum para série #$tmdbId: ${e.message}")
                }
            }
            
            val summaryMsg = if (episodesAdded > 0) {
                "Série atualizada com sucesso! Adicionados $episodesAdded novos episódios."
            } else {
                "Esta série já está completa e atualizada."
            }
            
            Log.d("MediaRepository", "[UPDATE SERIES] Concluído. Verificadas: $seasonsVerified temporadas, Encontrados: $episodesFound episódios, Adicionados: $episodesAdded, Erros: $errorsCount")
            
            SeriesUpdateResult(
                success = true,
                seasonsVerified = seasonsVerified,
                episodesFound = episodesFound,
                episodesExisting = episodesExisting,
                episodesAdded = episodesAdded,
                errorsCount = errorsCount,
                message = summaryMsg
            )
        } catch (e: Exception) {
            Log.e("MediaRepository", "[UPDATE SERIES ERROR] Erro geral ao atualizar série #$tmdbId: ${e.message}", e)
            SeriesUpdateResult(false, 0, 0, 0, 0, 1, "Erro ao atualizar série: ${e.localizedMessage}")
        }
    }

    // --- TMDB Cast Cache & Fetching ---
    private val castCache = java.util.concurrent.ConcurrentHashMap<Int, List<DetailedCastMember>>()

    suspend fun fetchCastForMedia(tmdbId: Int, mediaType: String, rawCastStr: String? = null): List<DetailedCastMember> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext emptyList()

        // 1. Check in-memory cache first
        castCache[tmdbId]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }

        // 2. If rawCastStr has rich structured format (contains | and ;) and at least 1 photo, use it
        if (!rawCastStr.isNullOrBlank() && rawCastStr.contains("|")) {
            val parsed = parseCastList(rawCastStr)
            if (parsed.isNotEmpty() && parsed.any { !it.profileUrl.isNullOrBlank() }) {
                castCache[tmdbId] = parsed
                return@withContext parsed
            }
        }

        // 3. Fetch real credits directly from TMDB API
        try {
            val normalizedType = if (mediaType.equals("tv", ignoreCase = true) || mediaType.equals("serie", ignoreCase = true) || mediaType.equals("series", ignoreCase = true)) "tv" else "movie"
            
            val credits = if (normalizedType == "tv") {
                api.getSeriesCredits(tmdbId, apiKey)
            } else {
                api.getMovieCredits(tmdbId, apiKey)
            }

            val rawList = credits.cast ?: emptyList()
            val formattedList = rawList
                .filter { it.name.isNotBlank() }
                .sortedBy { it.order ?: 999 }
                .distinctBy { (it.id ?: 0).toString() + "_" + it.name.lowercase().trim() }
                .map { dto ->
                    val charName = dto.character
                        ?.replace("|", "/")
                        ?.replace(";", ",")
                        ?.replace("undefined", "", ignoreCase = true)
                        ?.replace("null", "", ignoreCase = true)
                        ?.replace("N/A", "", ignoreCase = true)
                        ?.trim() ?: ""

                    val cleanChar = if (charName.equals("Atores Principais", ignoreCase = true)) "" else charName

                    val profileUrl = if (!dto.profilePath.isNullOrBlank()) {
                        val path = dto.profilePath.trim()
                        if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/w185$path"
                    } else null

                    DetailedCastMember(
                        name = dto.name.trim(),
                        character = cleanChar,
                        profileUrl = profileUrl
                    )
                }

            if (formattedList.isNotEmpty()) {
                castCache[tmdbId] = formattedList

                // Asynchronously update local database cast string if local item exists with generic string
                try {
                    val localMedia = dao.getMediaByTmdbId(tmdbId)
                    if (localMedia != null) {
                        val castStringToSave = formattedList.take(15).joinToString(";") {
                            val profile = it.profileUrl?.removePrefix("https://image.tmdb.org/t/p/w185") ?: ""
                            "${it.name}|${it.character}|$profile"
                        }
                        if (localMedia.cast != castStringToSave) {
                            dao.insertMedia(localMedia.copy(cast = castStringToSave))
                        }
                    }
                } catch (_: Exception) {}

                return@withContext formattedList
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error fetching cast for tmdbId $tmdbId: ${e.message}")
        }

        // 4. Fallback: Parse rawCastStr if available
        if (!rawCastStr.isNullOrBlank()) {
            val fallbackList = parseCastList(rawCastStr)
            if (fallbackList.isNotEmpty()) {
                castCache[tmdbId] = fallbackList
                return@withContext fallbackList
            }
        }

        emptyList()
    }
}
