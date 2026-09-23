package com.example.data.repository

import android.util.Log
import com.example.data.local.ChannelEntity
import com.example.data.remote.ApiChannel
import com.example.data.remote.ApiEvent
import com.example.data.remote.ApiEventEmbed
import com.example.data.remote.ApiGuideItem
import com.example.data.remote.EmbedTvApiService
import com.example.data.remote.FirebaseService
import com.example.data.remote.ReiDosEmbedsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveTvApiStats(
    val isOnline: Boolean = true,
    val lastSync: String = "Sincronizado recentemente",
    val channelsCount: Int = 0,
    val eventsCount: Int = 0,
    val activeChannelsCount: Int = 0,
    val blockedDuplicatesCount: Int = 0,
    val categoriesCount: Int = 0,
    val guideStatus: String = "Ativo (EPG 24h)"
)

data class LiveTvSyncSummary(
    val totalFoundOnApi: Int = 0,
    val newChannelsAdded: Int = 0,
    val existingUpdated: Int = 0,
    val duplicatesBlocked: Int = 0,
    val errorCount: Int = 0,
    val isApiOnline: Boolean = true,
    val message: String = ""
)

sealed class AdminAddChannelResult {
    data class Success(val channel: ApiChannel) : AdminAddChannelResult()
    data class Duplicate(val existingChannel: ApiChannel) : AdminAddChannelResult()
    data class Error(val message: String) : AdminAddChannelResult()
}

sealed class AdminImportChannelResult {
    data class Preview(val channel: ApiChannel) : AdminImportChannelResult()
    data class Duplicate(val existingChannel: ApiChannel) : AdminImportChannelResult()
    data class NotFound(val message: String) : AdminImportChannelResult()
    data class Error(val message: String) : AdminImportChannelResult()
}

class LiveTvRepository(
    private val apiService: ReiDosEmbedsApiService = ReiDosEmbedsApiService(),
    private val embedTvService: EmbedTvApiService = EmbedTvApiService()
) {
    private val TAG = "LiveTvRepository"

    private val _cachedChannels = MutableStateFlow<List<ApiChannel>>(emptyList())
    val cachedChannels: StateFlow<List<ApiChannel>> = _cachedChannels.asStateFlow()

    private val _cachedEvents = MutableStateFlow<List<ApiEvent>>(emptyList())
    val cachedEvents: StateFlow<List<ApiEvent>> = _cachedEvents.asStateFlow()

    private val _cachedChannelCategories = MutableStateFlow<List<String>>(listOf("Todos"))
    val cachedChannelCategories: StateFlow<List<String>> = _cachedChannelCategories.asStateFlow()

    private val _cachedEventCategories = MutableStateFlow<List<String>>(listOf("Todos"))
    val cachedEventCategories: StateFlow<List<String>> = _cachedEventCategories.asStateFlow()

    private val _cachedGuide = MutableStateFlow<List<ApiGuideItem>>(emptyList())
    val cachedGuide: StateFlow<List<ApiGuideItem>> = _cachedGuide.asStateFlow()

    private val _embedTvStats = MutableStateFlow(LiveTvApiStats())
    val embedTvStats: StateFlow<LiveTvApiStats> = _embedTvStats.asStateFlow()

    private val _apiStats = MutableStateFlow(
        LiveTvApiStats(
            isOnline = true,
            lastSync = getCurrentTimestamp(),
            channelsCount = _cachedChannels.value.size,
            eventsCount = _cachedEvents.value.size,
            categoriesCount = _cachedChannelCategories.value.size + _cachedEventCategories.value.size,
            guideStatus = "Ativo (Atualização diária)"
        )
    )
    val apiStats: StateFlow<LiveTvApiStats> = _apiStats.asStateFlow()

    private val _syncHistory = MutableStateFlow<List<String>>(emptyList())
    val syncHistory: StateFlow<List<String>> = _syncHistory.asStateFlow()

    private var lastGuideFetchTime = 0L
    private val GUIDE_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    private fun addSyncLog(provider: String, summary: LiveTvSyncSummary) {
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val log = """
            $timestamp
            $provider
            ✓ Canais encontrados: ${summary.totalFoundOnApi}
            ✓ Novos: ${summary.newChannelsAdded}
            ✓ Atualizados: ${summary.existingUpdated}
            ✓ Duplicados evitados: ${summary.duplicatesBlocked}
            ✓ Erros: ${summary.errorCount}
        """.trimIndent()
        _syncHistory.value = listOf(log) + _syncHistory.value.take(19)
    }

    private fun normalizeKey(input: String?): String {
        if (input == null) return ""
        return input.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    fun getCanonicalChannelKey(channel: ApiChannel): String {
        // Order of preference for canonical key:
        // 1. slug (if normalized isn't empty)
        // 2. id (if normalized isn't empty)
        // 3. name (normalized)
        val normSlug = normalizeKey(channel.slug)
        if (normSlug.isNotEmpty()) return normSlug
        val normId = normalizeKey(channel.id)
        if (normId.isNotEmpty()) return normId
        return normalizeKey(channel.name)
    }

    fun findExistingChannel(
        candidates: List<ApiChannel>,
        newChannel: ApiChannel
    ): ApiChannel? {
        val newKey = getCanonicalChannelKey(newChannel)
        val newNameNorm = normalizeKey(newChannel.name)
        val newEmbedNorm = (newChannel.embedUrl ?: "").trim().lowercase(Locale.ROOT).removeSuffix("/")

        return candidates.find { existing ->
            // Check by canonical key
            if (getCanonicalChannelKey(existing) == newKey) return@find true
            
            // Check by name
            if (normalizeKey(existing.name) == newNameNorm) return@find true
            
            // Check by URL
            val existingEmbedNorm = (existing.embedUrl ?: "").trim().lowercase(Locale.ROOT).removeSuffix("/")
            if (newEmbedNorm.isNotEmpty() && existingEmbedNorm.isNotEmpty() && newEmbedNorm == existingEmbedNorm) return@find true
            
            false
        }
    }

    suspend fun syncEmbedTvChannels(): LiveTvSyncSummary = withContext(Dispatchers.IO) {
        try {
            val remote = embedTvService.getChannels()
            if (remote.isEmpty()) return@withContext LiveTvSyncSummary(isApiOnline = false, message = "Nenhum canal retornado")

            val currentList = _cachedChannels.value.toMutableList()
            var newAdded = 0
            var existingUpdated = 0
            var duplicatesBlocked = 0

            remote.forEach { remoteCh ->
                val duplicate = findExistingChannel(currentList, remoteCh)
                if (duplicate != null) {
                    val index = currentList.indexOfFirst { it.id == duplicate.id }
                    if (index >= 0) {
                        val current = currentList[index]
                        // Preserve original source if it's the same, or merge info
                        val updated = current.copy(
                            nowPlayingTitle = remoteCh.nowPlayingTitle?.takeIf { it.isNotBlank() } ?: current.nowPlayingTitle,
                            logoUrl = remoteCh.logoUrl?.takeIf { it.isNotBlank() } ?: current.logoUrl,
                            category = remoteCh.category?.takeIf { it.isNotBlank() } ?: current.category
                        )
                        currentList[index] = updated
                        existingUpdated++
                    } else {
                        duplicatesBlocked++
                    }
                } else {
                    currentList.add(remoteCh)
                    newAdded++
                }
            }

            _cachedChannels.value = currentList
            val summary = LiveTvSyncSummary(
                totalFoundOnApi = remote.size,
                newChannelsAdded = newAdded,
                existingUpdated = existingUpdated,
                duplicatesBlocked = duplicatesBlocked
            )
            
            _embedTvStats.value = _embedTvStats.value.copy(
                isOnline = true,
                lastSync = getCurrentTimestamp(),
                channelsCount = currentList.count { it.sourceProvider == "embedtv" },
                activeChannelsCount = currentList.count { it.sourceProvider == "embedtv" && it.isActive == true }
            )
            
            addSyncLog("EmbedTV (Canais)", summary)
            summary
        } catch (e: Exception) {
            LiveTvSyncSummary(isApiOnline = false, message = e.message ?: "Erro desconhecido")
        }
    }

    suspend fun syncEmbedTvEvents(): LiveTvSyncSummary = withContext(Dispatchers.IO) {
        try {
            val remote = embedTvService.getEvents()
            val currentEvents = _cachedEvents.value.toMutableList()
            var newAdded = 0
            
            remote.forEach { event ->
                if (currentEvents.none { it.id == event.id || it.title == event.title }) {
                    currentEvents.add(event)
                    newAdded++
                }
            }
            
            _cachedEvents.value = currentEvents
            val summary = LiveTvSyncSummary(
                totalFoundOnApi = remote.size,
                newChannelsAdded = newAdded
            )
            
            _embedTvStats.value = _embedTvStats.value.copy(
                eventsCount = currentEvents.count { it.sourceProvider == "embedtv" }
            )
            
            addSyncLog("EmbedTV (Eventos)", summary)
            summary
        } catch (e: Exception) {
            LiveTvSyncSummary(isApiOnline = false, message = e.message ?: "Erro desconhecido")
        }
    }

    suspend fun syncEmbedTvGuide(): LiveTvSyncSummary = withContext(Dispatchers.IO) {
        try {
            val remote = embedTvService.getGuide()
            val currentGuide = _cachedGuide.value.toMutableList()
            
            // Merge guide items
            remote.forEach { item ->
                if (currentGuide.none { it.channelId == item.channelId && it.title == item.title && it.time == item.time }) {
                    currentGuide.add(item)
                }
            }
            
            _cachedGuide.value = currentGuide
            _embedTvStats.value = _embedTvStats.value.copy(
                guideStatus = "Disponível (${remote.size} itens)"
            )
            
            val summary = LiveTvSyncSummary(totalFoundOnApi = remote.size)
            addSyncLog("EmbedTV (EPG)", summary)
            summary
        } catch (e: Exception) {
            LiveTvSyncSummary(isApiOnline = false, message = e.message ?: "Erro desconhecido")
        }
    }

    suspend fun syncAllEmbedTv(): LiveTvSyncSummary {
        val ch = syncEmbedTvChannels()
        val ev = syncEmbedTvEvents()
        val ep = syncEmbedTvGuide()
        
        return LiveTvSyncSummary(
            totalFoundOnApi = ch.totalFoundOnApi + ev.totalFoundOnApi + ep.totalFoundOnApi,
            newChannelsAdded = ch.newChannelsAdded,
            existingUpdated = ch.existingUpdated,
            duplicatesBlocked = ch.duplicatesBlocked,
            message = "Sincronização completa concluída"
        )
    }

    suspend fun refreshChannels(category: String? = null): List<ApiChannel> {
        return try {
            val remote = apiService.getChannels(category)
            if (remote.isNotEmpty()) {
                _cachedChannels.value = remote
                updateStats(isOnline = true, channels = remote.size)
                remote
            } else {
                _cachedChannels.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing channels: ${e.message}")
            _cachedChannels.value
        }
    }

    suspend fun getChannelById(channelId: String, forceRefresh: Boolean = true): ApiChannel? {
        if (!forceRefresh) {
            val inMemory = _cachedChannels.value.find { it.id.equals(channelId, ignoreCase = true) || (it.slug != null && it.slug.equals(channelId, ignoreCase = true)) }
            if (inMemory != null) return inMemory
        }

        return try {
            android.util.Log.i("RONYCINE_DIAG", "[LIVE_API_STATUS] Fetching updated data for channel $channelId")
            val remote = apiService.getChannelById(channelId)
            if (remote != null) {
                android.util.Log.i("RONYCINE_DIAG", "[LIVE_API_STATUS] Channel $channelId fetched successfully. embed_url=${remote.embedUrl}")
                remote
            } else {
                android.util.Log.e("RONYCINE_DIAG", "[LIVE_API_STATUS] Channel $channelId not found on API")
                _cachedChannels.value.find { it.id.equals(channelId, ignoreCase = true) }
            }
        } catch (e: Exception) {
            android.util.Log.e("RONYCINE_DIAG", "[LIVE_API_STATUS] Error fetching channel $channelId: ${e.message}")
            _cachedChannels.value.find { it.id.equals(channelId, ignoreCase = true) }
        }
    }

    suspend fun refreshChannelCategories(): List<String> {
        return try {
            val remote = apiService.getChannelCategories()
            if (remote.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(remote.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedChannelCategories.value = list
                updateStats(isOnline = true, categories = list.size + _cachedEventCategories.value.size)
                list
            } else {
                _cachedChannelCategories.value
            }
        } catch (e: Exception) {
            _cachedChannelCategories.value
        }
    }

    suspend fun refreshEvents(category: String? = null, status: String? = null): List<ApiEvent> {
        return try {
            val remote = apiService.getEvents(category, status)
            if (remote.isNotEmpty()) {
                _cachedEvents.value = remote
                updateStats(isOnline = true, events = remote.size)
                remote
            } else {
                _cachedEvents.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing events: ${e.message}")
            _cachedEvents.value
        }
    }

    suspend fun getEventById(eventId: String): ApiEvent? {
        val inMemory = _cachedEvents.value.find { it.id.equals(eventId, ignoreCase = true) || it.slug.equals(eventId, ignoreCase = true) }
        if (inMemory != null) return inMemory

        return try {
            val remote = apiService.getEventById(eventId)
            remote ?: _cachedEvents.value.firstOrNull()
        } catch (e: Exception) {
            _cachedEvents.value.firstOrNull()
        }
    }

    suspend fun refreshEventCategories(): List<String> {
        return try {
            val remote = apiService.getEventCategories()
            if (remote.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(remote.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedEventCategories.value = list
                updateStats(isOnline = true, categories = _cachedChannelCategories.value.size + list.size)
                list
            } else {
                _cachedEventCategories.value
            }
        } catch (e: Exception) {
            _cachedEventCategories.value
        }
    }

    suspend fun search(query: String): Pair<List<ApiChannel>, List<ApiEvent>> {
        if (query.isBlank()) {
            return Pair(_cachedChannels.value, _cachedEvents.value)
        }

        val localChannels = _cachedChannels.value.filter {
            it.name.contains(query, ignoreCase = true) ||
                    (it.category?.contains(query, ignoreCase = true) == true) ||
                    (it.nowPlayingTitle?.contains(query, ignoreCase = true) == true)
        }
        val localEvents = _cachedEvents.value.filter {
            it.title.contains(query, ignoreCase = true) ||
                    (it.category?.contains(query, ignoreCase = true) == true) ||
                    (it.competition?.contains(query, ignoreCase = true) == true) ||
                    (it.time1Name?.contains(query, ignoreCase = true) == true) ||
                    (it.time2Name?.contains(query, ignoreCase = true) == true)
        }

        return try {
            val (remoteChannels, remoteEvents) = apiService.search(query)
            val combinedChannels = if (remoteChannels.isNotEmpty()) remoteChannels else localChannels
            val combinedEvents = if (remoteEvents.isNotEmpty()) remoteEvents else localEvents
            Pair(combinedChannels, combinedEvents)
        } catch (e: Exception) {
            Pair(localChannels, localEvents)
        }
    }

    suspend fun getGuide(forceRefresh: Boolean = false): List<ApiGuideItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _cachedGuide.value.isNotEmpty() && (now - lastGuideFetchTime < GUIDE_CACHE_DURATION_MS)) {
            return _cachedGuide.value
        }

        return try {
            val remote = apiService.getGuide()
            if (remote.isNotEmpty()) {
                _cachedGuide.value = remote
                lastGuideFetchTime = now
                updateStats(isOnline = true)
                remote
            } else {
                _cachedGuide.value
            }
        } catch (e: Exception) {
            _cachedGuide.value
        }
    }

    private val _totalBlockedDuplicates = MutableStateFlow(0)
    val totalBlockedDuplicates: StateFlow<Int> = _totalBlockedDuplicates.asStateFlow()

    fun findDuplicateChannel(
        candidates: List<ApiChannel>,
        newId: String,
        newName: String,
        newStreamUrl: String?,
        newCategory: String,
        newSlug: String? = null
    ): ApiChannel? {
        val normNewId = normalizeKey(newId)
        val normNewSlug = newSlug?.let { normalizeKey(it) } ?: normNewId
        val normNewName = normalizeKey(newName)
        val normNewCat = normalizeKey(newCategory)
        val normNewStream = (newStreamUrl ?: "").trim().lowercase(Locale.ROOT).removeSuffix("/")

        return candidates.find { existing ->
            val normExistingId = normalizeKey(existing.id)
            val normExistingSlug = existing.slug?.let { normalizeKey(it) } ?: normExistingId
            val normExistingName = normalizeKey(existing.name)
            val normExistingCat = normalizeKey(existing.category ?: "")
            val normExistingStream = (existing.embedUrl ?: "").trim().lowercase(Locale.ROOT).removeSuffix("/")

            if (normNewId.isNotEmpty() && (normNewId == normExistingId || normNewId == normExistingSlug)) {
                return@find true
            }
            if (normNewSlug.isNotEmpty() && (normNewSlug == normExistingId || normNewSlug == normExistingSlug)) {
                return@find true
            }
            if (normNewStream.isNotEmpty() && normNewStream == normExistingStream) {
                return@find true
            }
            if (normNewName.isNotEmpty() && normNewName == normExistingName && normNewCat == normExistingCat) {
                return@find true
            }

            false
        }
    }

    suspend fun syncReiDosEmbeds(): LiveTvSyncSummary {
        return syncAllWithSummary()
    }

    suspend fun syncAllWithSummary(mediaRepository: MediaRepository? = null): LiveTvSyncSummary = withContext(Dispatchers.IO) {
        var foundApiCount = 0
        var newAdded = 0
        var existingUpdated = 0
        var duplicatesBlocked = 0
        var errorCount = 0
        var isOnline = true

        val currentList = _cachedChannels.value.toMutableList()

        try {
            val remoteChannels = apiService.getChannels()
            foundApiCount = remoteChannels.size

            if (remoteChannels.isNotEmpty()) {
                for (remote in remoteChannels) {
                    val duplicate = findDuplicateChannel(
                        candidates = currentList,
                        newId = remote.id,
                        newName = remote.name,
                        newStreamUrl = remote.embedUrl,
                        newCategory = remote.category ?: "",
                        newSlug = remote.slug
                    )

                    if (duplicate != null) {
                        val index = currentList.indexOfFirst { it.id == duplicate.id }
                        if (index >= 0) {
                            val current = currentList[index]
                            val updated = current.copy(
                                nowPlayingTitle = remote.nowPlayingTitle?.ifBlank { current.nowPlayingTitle } ?: current.nowPlayingTitle,
                                logoUrl = remote.logoUrl.takeIf { !it.isNullOrBlank() } ?: current.logoUrl,
                                category = remote.category.takeIf { !it.isNullOrBlank() } ?: current.category
                            )
                            currentList[index] = updated
                            existingUpdated++
                        } else {
                            duplicatesBlocked++
                        }
                    } else {
                        currentList.add(remote)
                        newAdded++
                    }
                }
                _cachedChannels.value = currentList
                _totalBlockedDuplicates.value += duplicatesBlocked
            } else {
                isOnline = false
            }

            val remoteEvents = apiService.getEvents()
            if (remoteEvents.isNotEmpty()) {
                _cachedEvents.value = remoteEvents
            }

            val chCats = apiService.getChannelCategories()
            if (chCats.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(chCats.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedChannelCategories.value = list
            }

            val evCats = apiService.getEventCategories()
            if (evCats.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(evCats.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedEventCategories.value = list
            }

            val guide = apiService.getGuide()
            if (guide.isNotEmpty()) {
                _cachedGuide.value = guide
                lastGuideFetchTime = System.currentTimeMillis()
            }

            val channelEntities = currentList.map { apiCh ->
                ChannelEntity(
                    id = apiCh.id,
                    name = apiCh.name,
                    category = apiCh.category ?: "Geral",
                    logoUrl = apiCh.logoUrl ?: "",
                    streamUrl = apiCh.getEffectiveEmbedUrl(),
                    isOnline = apiCh.isActive == true,
                    addedAt = System.currentTimeMillis()
                )
            }
            if (mediaRepository != null) {
                for (entity in channelEntities) {
                    mediaRepository.saveLiveChannel(entity)
                }
            } else {
                FirebaseService.getInstance()?.batchUpsertChannels(channelEntities)
            }

            updateStats(
                isOnline = isOnline,
                channels = currentList.size,
                events = _cachedEvents.value.size,
                categories = _cachedChannelCategories.value.size + _cachedEventCategories.value.size
            )

            val summary = LiveTvSyncSummary(
                totalFoundOnApi = foundApiCount,
                newChannelsAdded = newAdded,
                existingUpdated = existingUpdated,
                duplicatesBlocked = duplicatesBlocked,
                errorCount = errorCount,
                isApiOnline = isOnline,
                message = if (isOnline) "Sincronização concluída com sucesso." else "API indisponível. Os dados existentes foram preservados."
            )
            addSyncLog("API Atual", summary)
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            updateStats(isOnline = false)
            LiveTvSyncSummary(
                totalFoundOnApi = foundApiCount,
                newChannelsAdded = 0,
                existingUpdated = 0,
                duplicatesBlocked = 0,
                errorCount = 1,
                isApiOnline = false,
                message = "Erro de conexão com a API. Os dados locais foram preservados."
            )
        }
    }

    suspend fun addChannel(
        name: String,
        publicId: String,
        category: String,
        logoUrl: String,
        streamUrl: String,
        description: String,
        isActive: Boolean,
        mediaRepository: MediaRepository? = null,
        sourceProvider: String? = "manual"
    ): AdminAddChannelResult = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanId = publicId.trim().lowercase(Locale.ROOT)
        val cleanStream = streamUrl.trim()

        if (cleanName.isBlank() || cleanId.isBlank() || cleanStream.isBlank()) {
            return@withContext AdminAddChannelResult.Error("Preencha Nome, ID Público e URL do Stream.")
        }

        val currentList = _cachedChannels.value.toMutableList()
        val duplicate = findDuplicateChannel(
            candidates = currentList,
            newId = cleanId,
            newName = cleanName,
            newStreamUrl = cleanStream,
            newCategory = category
        )

        if (duplicate != null) {
            _totalBlockedDuplicates.value += 1
            return@withContext AdminAddChannelResult.Duplicate(duplicate)
        }

        val newChannel = ApiChannel(
            id = cleanId,
            name = cleanName,
            category = category.trim().ifBlank { "Geral" },
            logoUrl = logoUrl.trim(),
            embedUrl = cleanStream,
            description = description.trim(),
            isActive = isActive,
            slug = cleanId,
            sourceProvider = sourceProvider ?: "manual"
        )

        currentList.add(0, newChannel)
        _cachedChannels.value = currentList

        val entity = ChannelEntity(
            id = newChannel.id,
            name = newChannel.name,
            category = newChannel.category ?: "Geral",
            logoUrl = newChannel.logoUrl ?: "",
            streamUrl = newChannel.getEffectiveEmbedUrl(),
            isOnline = newChannel.isActive == true,
            addedAt = System.currentTimeMillis(),
            sourceProvider = newChannel.sourceProvider ?: "manual"
        )

        if (mediaRepository != null) {
            mediaRepository.saveLiveChannel(entity)
        } else {
            FirebaseService.getInstance()?.upsertChannelInCloud(entity)
        }

        updateStats()
        AdminAddChannelResult.Success(newChannel)
    }

    suspend fun importChannelFromApi(
        publicId: String,
        mediaRepository: MediaRepository? = null,
        provider: String = "api_atual"
    ): AdminImportChannelResult = withContext(Dispatchers.IO) {
        val cleanId = publicId.trim()
        if (cleanId.isBlank()) {
            return@withContext AdminImportChannelResult.Error("Informe um ID público válido.")
        }

        val currentList = _cachedChannels.value
        val fetched = if (provider == "embedtv") {
            embedTvService.getChannels().find { it.id == cleanId || it.slug == cleanId }
        } else {
            apiService.getChannelById(cleanId)
        } ?: return@withContext AdminImportChannelResult.NotFound("Canal '$cleanId' não foi encontrado na API ($provider).")

        val duplicate = findDuplicateChannel(
            candidates = currentList,
            newId = fetched.id,
            newName = fetched.name,
            newStreamUrl = fetched.embedUrl,
            newCategory = fetched.category ?: "",
            newSlug = fetched.slug
        )

        if (duplicate != null) {
            _totalBlockedDuplicates.value += 1
            return@withContext AdminImportChannelResult.Duplicate(duplicate)
        }

        AdminImportChannelResult.Preview(fetched.copy(sourceProvider = provider))
    }

    suspend fun saveImportedChannel(
        channel: ApiChannel,
        mediaRepository: MediaRepository? = null
    ): AdminAddChannelResult = withContext(Dispatchers.IO) {
        val currentList = _cachedChannels.value.toMutableList()
        val duplicate = findDuplicateChannel(
            candidates = currentList,
            newId = channel.id,
            newName = channel.name,
            newStreamUrl = channel.embedUrl,
            newCategory = channel.category ?: ""
        )

        if (duplicate != null) {
            _totalBlockedDuplicates.value += 1
            return@withContext AdminAddChannelResult.Duplicate(duplicate)
        }

        currentList.add(0, channel)
        _cachedChannels.value = currentList

        val entity = ChannelEntity(
            id = channel.id,
            name = channel.name,
            category = channel.category ?: "Geral",
            logoUrl = channel.logoUrl ?: "",
            streamUrl = channel.getEffectiveEmbedUrl(),
            isOnline = channel.isActive == true,
            addedAt = System.currentTimeMillis(),
            sourceProvider = channel.sourceProvider ?: "manual"
        )

        if (mediaRepository != null) {
            mediaRepository.saveLiveChannel(entity)
        } else {
            FirebaseService.getInstance()?.upsertChannelInCloud(entity)
        }

        updateStats()
        AdminAddChannelResult.Success(channel)
    }

    suspend fun deleteChannel(
        channelId: String,
        mediaRepository: MediaRepository? = null
    ) = withContext(Dispatchers.IO) {
        val currentList = _cachedChannels.value.filter { !it.id.equals(channelId, ignoreCase = true) }
        _cachedChannels.value = currentList

        if (mediaRepository != null) {
            mediaRepository.deleteLiveChannel(channelId)
        } else {
            FirebaseService.getInstance()?.deleteChannelFromCloud(channelId)
        }
        updateStats()
    }

    suspend fun updateChannel(
        updated: ApiChannel,
        mediaRepository: MediaRepository? = null
    ) = withContext(Dispatchers.IO) {
        val currentList = _cachedChannels.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.equals(updated.id, ignoreCase = true) }
        if (index >= 0) {
            currentList[index] = updated
            _cachedChannels.value = currentList

            val entity = ChannelEntity(
                id = updated.id,
                name = updated.name,
                category = updated.category ?: "Geral",
                logoUrl = updated.logoUrl ?: "",
                streamUrl = updated.getEffectiveEmbedUrl(),
                isOnline = updated.isActive == true,
                addedAt = System.currentTimeMillis()
            )

            if (mediaRepository != null) {
                mediaRepository.saveLiveChannel(entity)
            } else {
                FirebaseService.getInstance()?.upsertChannelInCloud(entity)
            }
            updateStats()
        }
    }

    suspend fun toggleChannelStatus(
        channelId: String,
        mediaRepository: MediaRepository? = null
    ) = withContext(Dispatchers.IO) {
        val currentList = _cachedChannels.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.equals(channelId, ignoreCase = true) }
        if (index >= 0) {
            val existing = currentList[index]
            val toggled = existing.copy(isActive = !(existing.isActive == true))
            currentList[index] = toggled
            _cachedChannels.value = currentList

            val entity = ChannelEntity(
                id = toggled.id,
                name = toggled.name,
                category = toggled.category ?: "Geral",
                logoUrl = toggled.logoUrl ?: "",
                streamUrl = toggled.getEffectiveEmbedUrl(),
                isOnline = toggled.isActive == true,
                addedAt = System.currentTimeMillis()
            )

            if (mediaRepository != null) {
                mediaRepository.saveLiveChannel(entity)
            } else {
                FirebaseService.getInstance()?.upsertChannelInCloud(entity)
            }
            updateStats()
        }
    }

    suspend fun syncAll(): LiveTvApiStats {
        return try {
            val channels = apiService.getChannels()
            if (channels.isNotEmpty()) _cachedChannels.value = channels

            val events = apiService.getEvents()
            if (events.isNotEmpty()) _cachedEvents.value = events

            val chCats = apiService.getChannelCategories()
            if (chCats.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(chCats.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedChannelCategories.value = list
            }

            val evCats = apiService.getEventCategories()
            if (evCats.isNotEmpty()) {
                val list = mutableListOf("Todos")
                list.addAll(evCats.filter { it.isNotBlank() && !it.equals("Todos", ignoreCase = true) })
                _cachedEventCategories.value = list
            }

            val guide = apiService.getGuide()
            if (guide.isNotEmpty()) {
                _cachedGuide.value = guide
                lastGuideFetchTime = System.currentTimeMillis()
            }

            updateStats(
                isOnline = true,
                channels = _cachedChannels.value.size,
                events = _cachedEvents.value.size,
                categories = _cachedChannelCategories.value.size + _cachedEventCategories.value.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            updateStats(isOnline = false)
        }
    }

    private fun updateStats(
        isOnline: Boolean = true,
        channels: Int? = null,
        events: Int? = null,
        categories: Int? = null
    ): LiveTvApiStats {
        val totalChannels = channels ?: _cachedChannels.value.size
        val activeCount = _cachedChannels.value.count { it.isActive == true }
        val updated = _apiStats.value.copy(
            isOnline = isOnline,
            lastSync = getCurrentTimestamp(),
            channelsCount = totalChannels,
            eventsCount = events ?: _cachedEvents.value.size,
            activeChannelsCount = activeCount,
            blockedDuplicatesCount = _totalBlockedDuplicates.value,
            categoriesCount = categories ?: (_cachedChannelCategories.value.size + _cachedEventCategories.value.size),
            guideStatus = "Sincronizado (EPG 24h)"
        )
        _apiStats.value = updated
        return updated
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
    }

    companion object {
        @Volatile
        private var instance: LiveTvRepository? = null

        fun getInstance(): LiveTvRepository {
            return instance ?: synchronized(this) {
                instance ?: LiveTvRepository().also { instance = it }
            }
        }

        fun getFallbackChannels(): List<ApiChannel> {
            return listOf(
                ApiChannel(
                    id = "globo",
                    name = "Globo SP",
                    category = "Abertos",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2013/12/rede-globo-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/globo",
                    nowPlayingTitle = "Jornal Nacional",
                    description = "Rede Globo de Televisão com programação ao vivo em alta definição."
                ),
                ApiChannel(
                    id = "sbt",
                    name = "SBT",
                    category = "Abertos",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2014/04/sbt-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/sbt",
                    nowPlayingTitle = "Programa do Ratinho",
                    description = "Sistema Brasileiro de Televisão com entretenimento e programas de auditório."
                ),
                ApiChannel(
                    id = "record",
                    name = "Record TV",
                    category = "Abertos",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2014/05/record-tv-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/record",
                    nowPlayingTitle = "Jornal da Record",
                    description = "Record TV com jornalismo, séries e variedades."
                ),
                ApiChannel(
                    id = "band",
                    name = "Band TV",
                    category = "Abertos",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2014/05/band-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/band",
                    nowPlayingTitle = "Jornal da Band",
                    description = "Rede Bandeirantes com jornalismo e cobertura esportiva."
                ),
                ApiChannel(
                    id = "sportv",
                    name = "SporTV",
                    category = "Esportes",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/sportv-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/sportv",
                    nowPlayingTitle = "Troca de Passes",
                    description = "O canal campeão com as melhores coberturas esportivas nacionais e internacionais."
                ),
                ApiChannel(
                    id = "sportv2",
                    name = "SporTV 2",
                    category = "Esportes",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/sportv-2-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/sportv2",
                    nowPlayingTitle = "Vôlei Superliga",
                    description = "Transmissões ao vivo e análises aprofundadas dos esportes olímpicos e futebol."
                ),
                ApiChannel(
                    id = "espn",
                    name = "ESPN Brasil",
                    category = "Esportes",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/espn-brasil-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/espn",
                    nowPlayingTitle = "SportsCenter",
                    description = "Informação com credibilidade e grandes torneios mundiais de futebol e basquete."
                ),
                ApiChannel(
                    id = "espn4",
                    name = "ESPN 4",
                    category = "Esportes",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/espn-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/espn4",
                    nowPlayingTitle = "Premier League Ao Vivo",
                    description = "Futebol europeu, NBA, NFL e grandes emoções."
                ),
                ApiChannel(
                    id = "premiere",
                    name = "Premiere Clubes",
                    category = "Esportes",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/premiere-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/premiere",
                    nowPlayingTitle = "Brasileirão Série A",
                    description = "O melhor do futebol brasileiro ao vivo."
                ),
                ApiChannel(
                    id = "tnt",
                    name = "TNT",
                    category = "Filmes & Séries",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2016/10/tnt-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/tnt",
                    nowPlayingTitle = "Champions League / Filme",
                    description = "Filmes de grande sucesso e Champions League."
                ),
                ApiChannel(
                    id = "space",
                    name = "Space",
                    category = "Filmes & Séries",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2016/10/space-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/space",
                    nowPlayingTitle = "Filme de Ação",
                    description = "Muita ação, suspense, ficção científica e emoção sem limites."
                ),
                ApiChannel(
                    id = "megapix",
                    name = "Megapix",
                    category = "Filmes & Séries",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/megapix-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/megapix",
                    nowPlayingTitle = "Sessão Megapix",
                    description = "Os maiores sucessos do cinema dublados em português."
                ),
                ApiChannel(
                    id = "telecine_premium",
                    name = "Telecine Premium",
                    category = "Filmes & Séries",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/telecine-premium-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/telecine-premium",
                    nowPlayingTitle = "Estreia da Semana",
                    description = "As maiores e mais recentes produções do cinema mundial."
                ),
                ApiChannel(
                    id = "telecine_action",
                    name = "Telecine Action",
                    category = "Filmes & Séries",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/telecine-action-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/telecine-action",
                    nowPlayingTitle = "Adrenalina Máxima",
                    description = "O canal exclusivo para quem adora filmes de ação e adrenalina."
                ),
                ApiChannel(
                    id = "cnn_brasil",
                    name = "CNN Brasil",
                    category = "Notícias",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2020/03/cnn-brasil-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/cnn-brasil",
                    nowPlayingTitle = "CNN 360°",
                    description = "Notícias 24 horas com análises políticas e econômicas em tempo real."
                ),
                ApiChannel(
                    id = "globonews",
                    name = "GloboNews",
                    category = "Notícias",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/globonews-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/globonews",
                    nowPlayingTitle = "Edição das 18h",
                    description = "Jornalismo que nunca desliga, com furos e análises exclusivas."
                ),
                ApiChannel(
                    id = "discovery_channel",
                    name = "Discovery Channel",
                    category = "Documentários",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/discovery-channel-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/discovery",
                    nowPlayingTitle = "Largados e Pelados",
                    description = "Ciência, natureza, tecnologia e sobrevivência."
                ),
                ApiChannel(
                    id = "cartoon_network",
                    name = "Cartoon Network",
                    category = "Infantil",
                    logoUrl = "https://logodownload.org/wp-content/uploads/2017/04/cartoon-network-logo-0.png",
                    embedUrl = "https://reidosembeds.online/embed/cartoon",
                    nowPlayingTitle = "O Incrível Mundo de Gumball",
                    description = "Os melhores desenhos e animações para toda a família."
                )
            )
        }

        fun getFallbackEvents(): List<ApiEvent> {
            return listOf(
                ApiEvent(
                    id = "palmeiras-x-flamengo",
                    title = "Palmeiras x Flamengo",
                    time1 = "Palmeiras",
                    time2 = "Flamengo",
                    time1Name = "Palmeiras",
                    time2Name = "Flamengo",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2017/02/palmeiras-logo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2016/09/flamengo-logo-escudo-0.png",
                    startTime = "Hoje às 21:30",
                    status = "live",
                    category = "Futebol",
                    competition = "Brasileirão Série A",
                    sportKey = "futebol",
                    visualModel = "match",
                    description = "Grande clássico decisivo do futebol brasileiro válido pelo Campeonato Brasileiro.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Premiere 1 HD", quality = "1080p", embedUrl = "https://reidosembeds.online/embed/premiere", name = "Opção 1 (Premiere)"),
                        ApiEventEmbed(provider = "Globo SP HD", quality = "720p", embedUrl = "https://reidosembeds.online/embed/globo", name = "Opção 2 (Globo)")
                    )
                ),
                ApiEvent(
                    id = "real-madrid-x-barcelona",
                    title = "Real Madrid x Barcelona",
                    time1 = "Real Madrid",
                    time2 = "Barcelona",
                    time1Name = "Real Madrid",
                    time2Name = "Barcelona",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2016/03/real-madrid-logo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2016/03/barcelona-logo-0.png",
                    startTime = "Ao Vivo",
                    status = "live",
                    category = "Futebol",
                    competition = "La Liga",
                    sportKey = "futebol",
                    visualModel = "match",
                    description = "El Clásico mundial direto do Santiago Bernabéu.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "ESPN 4 HD", quality = "1080p", embedUrl = "https://reidosembeds.online/embed/espn4", name = "ESPN 4")
                    )
                ),
                ApiEvent(
                    id = "lakers-x-celtics",
                    title = "LA Lakers x Boston Celtics",
                    time1 = "Los Angeles Lakers",
                    time2 = "Boston Celtics",
                    time1Name = "Lakers",
                    time2Name = "Celtics",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2018/02/los-angeles-lakers-logo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2018/02/boston-celtics-logo-0.png",
                    startTime = "Hoje às 22:30",
                    status = "live",
                    category = "Basquete",
                    competition = "NBA Temporada Regular",
                    sportKey = "basquete",
                    visualModel = "match",
                    description = "O maior clássico da história da NBA com transmissão ao vivo.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "ESPN Principal", quality = "HD", embedUrl = "https://reidosembeds.online/embed/espn", name = "ESPN")
                    )
                ),
                ApiEvent(
                    id = "ufc-300-main-card",
                    title = "UFC Fight Night: Disputa de Cinturão",
                    startTime = "Sábado às 23:00",
                    status = "upcoming",
                    category = "Lutas / MMA",
                    competition = "UFC",
                    sportKey = "mma",
                    visualModel = "event",
                    poster = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=600",
                    description = "Card principal do UFC com disputa de cinturão e lutas de tirar o fôlego.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Combate HD", quality = "HD", embedUrl = "https://reidosembeds.online/embed/combate", name = "Canal Combate")
                    )
                ),
                ApiEvent(
                    id = "sao-paulo-x-corinthians",
                    title = "São Paulo x Corinthians",
                    time1 = "São Paulo",
                    time2 = "Corinthians",
                    time1Name = "São Paulo",
                    time2Name = "Corinthians",
                    time1Logo = "https://logodownload.org/wp-content/uploads/2016/09/sao-paulo-logo-escudo-0.png",
                    time2Logo = "https://logodownload.org/wp-content/uploads/2016/09/corinthians-logo-escudo-0.png",
                    startTime = "Amanhã às 16:00",
                    status = "upcoming",
                    category = "Futebol",
                    competition = "Paulistão / Brasileirão",
                    sportKey = "futebol",
                    visualModel = "match",
                    description = "Clássico Majestoso no Morumbi com casa cheia.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Premiere 2 HD", quality = "HD", embedUrl = "https://reidosembeds.online/embed/premiere2", name = "Premiere")
                    )
                ),
                ApiEvent(
                    id = "f1-gp-monaco",
                    title = "Fórmula 1: Grande Prêmio de Mônaco",
                    startTime = "Domingo às 10:00",
                    status = "upcoming",
                    category = "Automobilismo",
                    competition = "Fórmula 1",
                    sportKey = "f1",
                    visualModel = "event",
                    poster = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=600",
                    description = "Corrida clássica nas ruas estreitas de Monte Carlo.",
                    embeds = listOf(
                        ApiEventEmbed(provider = "Band TV HD", quality = "1080p", embedUrl = "https://reidosembeds.online/embed/band", name = "Band TV")
                    )
                )
            )
        }

        fun getFallbackGuide(): List<ApiGuideItem> {
            return listOf(
                ApiGuideItem(channelId = "globo", channelName = "Globo SP", time = "19:40", title = "Jornal Nacional", description = "As principais notícias do Brasil e do mundo.", category = "Jornalismo"),
                ApiGuideItem(channelId = "globo", channelName = "Globo SP", time = "20:30", title = "Novela das Nove", description = "Capítulo inédito com reviravoltas.", category = "Novela"),
                ApiGuideItem(channelId = "globo", channelName = "Globo SP", time = "21:30", title = "Futebol ao Vivo", description = "Campeonato Brasileiro Série A.", category = "Esportes"),
                ApiGuideItem(channelId = "sportv", channelName = "SporTV", time = "20:00", title = "Troca de Passes", description = "Debates e resenha dos jogos da rodada.", category = "Esportes"),
                ApiGuideItem(channelId = "sportv", channelName = "SporTV", time = "22:00", title = "SporTV News", description = "Resumo dos acontecimentos esportivos da noite.", category = "Jornalismo"),
                ApiGuideItem(channelId = "espn", channelName = "ESPN Brasil", time = "20:00", title = "SportsCenter", description = "Notícias, gols e melhores momentos com convidados especiais.", category = "Esportes"),
                ApiGuideItem(channelId = "sbt", channelName = "SBT", time = "19:45", title = "SBT Brasil", description = "Jornalismo com dinamismo e credibilidade.", category = "Jornalismo"),
                ApiGuideItem(channelId = "sbt", channelName = "SBT", time = "21:00", title = "Programa do Ratinho", description = "Atrações, humor e música ao vivo.", category = "Auditório")
            )
        }
    }
}
