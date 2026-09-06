package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.MediaRepository
import com.example.data.repository.SmartSearchResultItem
import com.example.data.repository.SearchItemStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val firebaseService = com.example.data.remote.FirebaseService.getInstance(application)
    val mediaRepository = MediaRepository(database.playFilmeDao(), firebaseService)
    val downloadRepository = com.example.data.download.DownloadRepository(application, database.playFilmeDao())

    // --- Remote Config & Device Management ---
    val remoteConfig: StateFlow<com.example.data.remote.RemoteConfigEntity> = firebaseService.remoteConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.RemoteConfigEntity())

    val isDeviceBlocked: StateFlow<Boolean> = firebaseService.isDeviceBlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAdminAuthorized: StateFlow<Boolean> = firebaseService.isAdminAuthorized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun initDeviceManager(customPrefs: android.content.SharedPreferences? = null) {
        firebaseService.initDeviceManager(customPrefs)
    }

    // --- Offline Downloads Flows ---
    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeDownloadForMedia(tmdbId: Int): Flow<List<DownloadEntity>> =
        downloadRepository.observeDownloadForMedia(tmdbId)

    fun observeDownloadForEpisode(tmdbId: Int, seasonNumber: Int?, episodeNumber: Int?): Flow<DownloadEntity?> =
        downloadRepository.observeDownloadForEpisode(tmdbId, seasonNumber, episodeNumber)

    fun observeDownloadById(id: String): Flow<DownloadEntity?> =
        downloadRepository.observeDownloadById(id)

    fun startMovieDownload(media: MediaEntity, customUrl: String? = null) {
        viewModelScope.launch {
            downloadRepository.startMovieDownload(media, customUrl)
        }
    }

    fun startEpisodeDownload(media: MediaEntity, episode: EpisodeEntity, customUrl: String? = null) {
        viewModelScope.launch {
            downloadRepository.startEpisodeDownload(media, episode, customUrl)
        }
    }

    fun pauseDownload(downloadId: String) {
        downloadRepository.pauseDownload(downloadId)
    }

    fun resumeDownload(downloadId: String) {
        downloadRepository.resumeDownload(downloadId)
    }

    fun cancelDownload(downloadId: String) {
        downloadRepository.cancelDownload(downloadId)
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(downloadId)
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteAllDownloads()
        }
    }

    fun getUsedStorageBytes(): Long = downloadRepository.getUsedStorageBytes()
    fun getAvailableStorageBytes(): Long = downloadRepository.getAvailableStorageBytes()
    fun getTotalStorageBytes(): Long = downloadRepository.getTotalStorageBytes()

    val syncStatus = firebaseService.syncStatus
    val lastSyncFormatted = firebaseService.lastSyncFormatted
    val catalogVersion = firebaseService.catalogVersion
    val publishedVersions: StateFlow<List<com.example.data.remote.AppVersionEntity>> = firebaseService.publishedVersions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val updateControl: StateFlow<com.example.data.remote.UpdateControlEntity> = firebaseService.updateControl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.UpdateControlEntity(enabled = false))

    val activeAppVersion: StateFlow<com.example.data.remote.AppVersionEntity?> = kotlinx.coroutines.flow.combine(
        firebaseService.updateControl,
        firebaseService.publishedVersions
    ) { control, versions ->
        if (!control.enabled || control.activeVersionCode == null) {
            null
        } else {
            val found = versions.firstOrNull { it.versionCode == control.activeVersionCode && it.isPublished }
            if (found != null && found.hasConfiguredApk) {
                found.copy(mandatory = control.mandatory || found.mandatory)
            } else if (control.apkUrl.isNotBlank() && control.apkUrl.startsWith("https://", ignoreCase = true)) {
                com.example.data.remote.AppVersionEntity(
                    id = "active_${control.activeVersionCode}",
                    versionName = control.activeVersionName ?: "${control.activeVersionCode}",
                    versionCode = control.activeVersionCode,
                    apkUrl = control.apkUrl,
                    releaseNotes = control.releaseNotes,
                    mandatory = control.mandatory,
                    sha256 = control.sha256,
                    fileSize = control.fileSize,
                    published = true,
                    status = "PUBLISHED"
                )
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestAppVersion: StateFlow<com.example.data.remote.AppVersionEntity?> = activeAppVersion

    private val _updateOverrideVersion = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.remote.AppVersionEntity?>(null)
    val updateOverrideVersion: StateFlow<com.example.data.remote.AppVersionEntity?> = _updateOverrideVersion

    fun triggerForceUpdateDialog(version: com.example.data.remote.AppVersionEntity) {
        _updateOverrideVersion.value = version
    }

    fun clearForceUpdateDialog() {
        _updateOverrideVersion.value = null
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            try {
                firebaseService.updateFcmToken(token)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error updating FCM token: ${e.message}")
            }
        }
    }

    fun updatePushStatus(hasPermission: Boolean, token: String?) {
        viewModelScope.launch {
            try {
                firebaseService.updatePushStatus(hasPermission, token)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error updating push status: ${e.message}")
            }
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        setNotificationsEnabled(enabled)
    }

    // --- Application Settings Flows & Persistence ---
    private val appPrefs = application.getSharedPreferences("playfilme_prefs", android.content.Context.MODE_PRIVATE)

    private val _notificationsEnabled = MutableStateFlow(appPrefs.getBoolean("notificationsEnabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _newMoviesEnabled = MutableStateFlow(appPrefs.getBoolean("newMoviesEnabled", true))
    val newMoviesEnabled: StateFlow<Boolean> = _newMoviesEnabled.asStateFlow()

    private val _newSeriesEnabled = MutableStateFlow(appPrefs.getBoolean("newSeriesEnabled", true))
    val newSeriesEnabled: StateFlow<Boolean> = _newSeriesEnabled.asStateFlow()

    private val _newEpisodesEnabled = MutableStateFlow(appPrefs.getBoolean("newEpisodesEnabled", true))
    val newEpisodesEnabled: StateFlow<Boolean> = _newEpisodesEnabled.asStateFlow()

    private val _autoplayEnabled = MutableStateFlow(appPrefs.getBoolean("autoplayEnabled", true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _preferredQuality = MutableStateFlow(appPrefs.getString("preferredQuality", "Automática") ?: "Automática")
    val preferredQuality: StateFlow<String> = _preferredQuality.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(appPrefs.getString("preferredLanguage", "Português") ?: "Português")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    private val _dataSaverEnabled = MutableStateFlow(appPrefs.getBoolean("dataSaverEnabled", false))
    val dataSaverEnabled: StateFlow<Boolean> = _dataSaverEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        appPrefs.edit().putBoolean("notificationsEnabled", enabled).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("notificationsEnabled", enabled.toString()))
            syncNotificationSettingsToCloud()
        }
    }

    fun setNewMoviesEnabled(enabled: Boolean) {
        _newMoviesEnabled.value = enabled
        appPrefs.edit().putBoolean("newMoviesEnabled", enabled).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("newMoviesEnabled", enabled.toString()))
            syncNotificationSettingsToCloud()
        }
    }

    fun setNewSeriesEnabled(enabled: Boolean) {
        _newSeriesEnabled.value = enabled
        appPrefs.edit().putBoolean("newSeriesEnabled", enabled).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("newSeriesEnabled", enabled.toString()))
            syncNotificationSettingsToCloud()
        }
    }

    fun setNewEpisodesEnabled(enabled: Boolean) {
        _newEpisodesEnabled.value = enabled
        appPrefs.edit().putBoolean("newEpisodesEnabled", enabled).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("newEpisodesEnabled", enabled.toString()))
            syncNotificationSettingsToCloud()
        }
    }

    private suspend fun syncNotificationSettingsToCloud() {
        firebaseService.updateDeviceNotificationSettings(
            notificationsEnabled = _notificationsEnabled.value,
            newMoviesEnabled = _newMoviesEnabled.value,
            newSeriesEnabled = _newSeriesEnabled.value,
            newEpisodesEnabled = _newEpisodesEnabled.value
        )
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        _autoplayEnabled.value = enabled
        appPrefs.edit().putBoolean("autoplayEnabled", enabled).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("autoplayEnabled", enabled.toString()))
        }
    }

    fun setPreferredQuality(quality: String) {
        _preferredQuality.value = quality
        appPrefs.edit().putString("preferredQuality", quality).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("preferredQuality", quality))
        }
    }

    fun setPreferredLanguage(language: String) {
        _preferredLanguage.value = language
        appPrefs.edit().putString("preferredLanguage", language).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("preferredLanguage", language))
        }
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        _dataSaverEnabled.value = enabled
        appPrefs.edit().putBoolean("dataSaverEnabled", enabled).apply()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("dataSaverEnabled", enabled.toString()))
        }
    }

    fun clearAppCache(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Clear WebView cache
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        android.webkit.WebStorage.getInstance().deleteAllData()
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Error clearing WebStorage: ${e.message}")
                    }
                }
                // 2. Clear Coil Image Cache
                try {
                    coil.Coil.imageLoader(context).diskCache?.clear()
                    coil.Coil.imageLoader(context).memoryCache?.clear()
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Error clearing Coil cache: ${e.message}")
                }
                // 3. Clear temporary files in app cacheDir safely without corrupting active WebView cache
                try {
                    context.cacheDir.listFiles()?.forEach { file ->
                        val name = file.name.lowercase()
                        if (!name.startsWith("webview") && !name.contains("chromium")) {
                            file.deleteRecursively()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Error cleaning cacheDir: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error clearing app cache: ${e.message}")
            }
        }
    }

    // --- Real-time Notifications Flows ---
    val allNotifications: StateFlow<List<NotificationEntity>> = mediaRepository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = mediaRepository.unreadNotificationsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inAppNotificationEvent = firebaseService.inAppNotificationEvent

    val allMedia: StateFlow<List<MediaEntity>> = mediaRepository.allMedia
        .map { list -> list.distinctBy { "${it.tmdbId}_${it.mediaType}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movies: StateFlow<List<MediaEntity>> = mediaRepository.movies
        .map { list -> list.distinctBy { it.tmdbId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<MediaEntity>> = mediaRepository.series
        .map { list -> list.distinctBy { it.tmdbId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myList: StateFlow<List<MediaEntity>> = mediaRepository.myList
        .map { list -> list.distinctBy { "${it.tmdbId}_${it.mediaType}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<WatchHistoryEntity>> = mediaRepository.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = mediaRepository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingMedia: StateFlow<List<MediaEntity>> = allMedia
        .map { list ->
            val movies = list.filter { it.mediaType == "movie" }.sortedByDescending { it.rating }
            val series = list.filter { it.mediaType == "tv" }.sortedByDescending { it.rating }
            
            val combined = mutableListOf<MediaEntity>()
            val maxLen = maxOf(movies.size, series.size)
            for (i in 0 until maxLen) {
                if (i < movies.size) combined.add(movies[i])
                if (i < series.size) combined.add(series[i])
            }
            combined.take(30)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topRatedMedia: StateFlow<List<MediaEntity>> = allMedia
        .map { list -> list.sortedByDescending { it.rating }.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedMedia: StateFlow<List<MediaEntity>> = allMedia
        .map { list ->
            list.sortedWith(
                compareByDescending<MediaEntity> { it.addedAt }
                    .thenByDescending { it.id }
            ).take(20)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGenres: StateFlow<List<String>> = allMedia
        .map { list ->
            list.flatMap { it.genres.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Admin-Configured Featured Items
    val activeFeaturedItems: StateFlow<List<FeaturedMediaItem>> = mediaRepository.activeFeaturedItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hero Banner Auto-Rotation
    private val _heroIndex = MutableStateFlow(0)
    val heroIndex: StateFlow<Int> = _heroIndex.asStateFlow()

    fun selectHeroIndex(index: Int) {
        if (index >= 0) {
            _heroIndex.value = index
            startHeroRotation()
        }
    }

    fun nextHeroIndex() {
        val heroList = featuredMedias.value
        if (heroList.isNotEmpty()) {
            _heroIndex.value = (_heroIndex.value + 1) % heroList.size
            startHeroRotation()
        }
    }

    private val _featuredMedias = MutableStateFlow<List<MediaEntity>>(emptyList())
    val featuredMedias: StateFlow<List<MediaEntity>> = _featuredMedias.asStateFlow()

    private val _top10Medias = MutableStateFlow<List<MediaEntity>>(emptyList())
    val top10Medias: StateFlow<List<MediaEntity>> = _top10Medias.asStateFlow()

    private var heroRotationJob: Job? = null
    private val _heroIntervalSeconds = MutableStateFlow(10) // Rotation interval
    val heroIntervalSeconds: StateFlow<Int> = _heroIntervalSeconds.asStateFlow()

    // Smart Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaEntity>>(emptyList())
    val searchResults: StateFlow<List<MediaEntity>> = _searchResults.asStateFlow()

    private val _smartSearchResults = MutableStateFlow<List<SmartSearchResultItem>>(emptyList())
    val smartSearchResults: StateFlow<List<SmartSearchResultItem>> = _smartSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Selected Media Details
    private val _selectedMedia = MutableStateFlow<MediaEntity?>(null)
    val selectedMedia: StateFlow<MediaEntity?> = _selectedMedia.asStateFlow()

    private val _mediaCastMap = MutableStateFlow<Map<Int, List<DetailedCastMember>>>(emptyMap())
    val mediaCastMap: StateFlow<Map<Int, List<DetailedCastMember>>> = _mediaCastMap.asStateFlow()

    private val _mediaCastLoadingMap = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val mediaCastLoadingMap: StateFlow<Map<Int, Boolean>> = _mediaCastLoadingMap.asStateFlow()

    private val _episodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = _episodes.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private var episodesJob: Job? = null

    private val _megaEmbedConfig = MutableStateFlow(com.example.data.remote.MegaEmbedConfig())
    val megaEmbedConfig: StateFlow<com.example.data.remote.MegaEmbedConfig> = _megaEmbedConfig.asStateFlow()

    init {
        firebaseService.startListeningRequests()
        viewModelScope.launch {
            mediaRepository.seedInitialCatalogIfEmpty()
            _megaEmbedConfig.value = mediaRepository.getMegaEmbedConfig()
            val interval = mediaRepository.getHeroIntervalSeconds()
            _heroIntervalSeconds.value = if (interval <= 0) 10 else interval
            
            // Collect media, Top 10 config and Featured config to populate featured hero and top 10
            launch {
                combine(allMedia, firebaseService.top10Config, firebaseService.featuredConfig) { list, top10Cfg, featCfg ->
                    Triple(list, top10Cfg, featCfg)
                }.collect { (list, config, featuredConfig) ->
                    if (list.isNotEmpty()) {
                        // 1. Featured / Hero section: Use configured active featured items if available, else fallback to heroCandidates
                        val activeFeatured = featuredConfig.items.filter { it.isActive }.sortedBy { it.displayOrder }
                        if (activeFeatured.isNotEmpty()) {
                            val resolvedHero = activeFeatured.map { featItem ->
                                val inCatalog = list.find { it.tmdbId == featItem.tmdbId && it.mediaType == featItem.mediaType }
                                if (inCatalog != null) {
                                    if (featItem.trailerUrl.isNotBlank()) {
                                        val cleanKey = when {
                                            featItem.trailerUrl.contains("v=") -> featItem.trailerUrl.substringAfter("v=").substringBefore("&")
                                            featItem.trailerUrl.contains("youtu.be/") -> featItem.trailerUrl.substringAfter("youtu.be/").substringBefore("?")
                                            !featItem.trailerUrl.contains("/") -> featItem.trailerUrl
                                            else -> inCatalog.trailerKey
                                        }
                                        inCatalog.copy(trailerKey = cleanKey ?: inCatalog.trailerKey)
                                    } else inCatalog
                                } else {
                                    featItem.toMediaEntity()
                                }
                            }
                            _featuredMedias.value = resolvedHero
                        } else {
                            // Fallback to catalog hero items
                            if (_featuredMedias.value.isEmpty()) {
                                val heroCandidates = list.filter { it.isHeroFeatured }
                                    .ifEmpty { list.filter { it.backdropPath != null }.sortedByDescending { it.rating } }
                                val selection = heroCandidates.take(6)
                                _featuredMedias.value = selection
                                
                                // Proactively fetch trailers for hero items if missing
                                selection.forEach { media ->
                                    if (media.trailerKey.isNullOrBlank()) {
                                        launch {
                                            val key = mediaRepository.getTrailerKeyForMedia(media.tmdbId, media.mediaType)
                                            if (key != null) {
                                                mediaRepository.updateMediaInCatalog(media.copy(trailerKey = key))
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Update existing hero items if metadata changed, preserving order
                                val currentIds = _featuredMedias.value.map { it.tmdbId to it.mediaType }
                                val updatedHero = currentIds.mapNotNull { (id, type) ->
                                    list.find { it.tmdbId == id && it.mediaType == type }
                                }
                                if (updatedHero.isNotEmpty()) {
                                    _featuredMedias.value = updatedHero
                                    
                                    // Also fetch missing trailers for updated items
                                    updatedHero.forEach { media ->
                                        if (media.trailerKey.isNullOrBlank()) {
                                            launch {
                                                val key = mediaRepository.getTrailerKeyForMedia(media.tmdbId, media.mediaType)
                                                if (key != null) {
                                                    mediaRepository.updateMediaInCatalog(media.copy(trailerKey = key))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 2. Use Configured Top 10 if available, otherwise fallback to default rating-based mix
                        if (config.items.isNotEmpty()) {
                            val resolvedItems = config.items.sortedBy { it.rank }.mapNotNull { item ->
                                list.find { it.tmdbId == item.tmdbId && it.mediaType == item.mediaType }
                            }
                            if (resolvedItems.isNotEmpty()) {
                                _top10Medias.value = resolvedItems
                            } else {
                                _top10Medias.value = generateDefaultTop10(list)
                            }
                        } else {
                            _top10Medias.value = generateDefaultTop10(list)
                        }
                    }
                }
            }
            
            startHeroRotation()
        }
    }

    fun reloadMegaEmbedConfig() {
        viewModelScope.launch {
            _megaEmbedConfig.value = mediaRepository.getMegaEmbedConfig()
        }
    }

    private fun generateDefaultTop10(list: List<MediaEntity>): List<MediaEntity> {
        val moviesList = list.filter { it.mediaType == "movie" }.sortedByDescending { it.rating }
        val seriesList = list.filter { it.mediaType == "tv" }.sortedByDescending { it.rating }
        val hybridTop10 = mutableListOf<MediaEntity>()
        val maxCount = maxOf(moviesList.size, seriesList.size)
        for (i in 0 until maxCount) {
            if (i < seriesList.size) hybridTop10.add(seriesList[i])
            if (i < moviesList.size) hybridTop10.add(moviesList[i])
            if (hybridTop10.size >= 10) break
        }
        return hybridTop10.take(10)
    }

    fun startHeroRotation() {
        heroRotationJob?.cancel()
        heroRotationJob = viewModelScope.launch {
            delay(_heroIntervalSeconds.value * 1000L)
            nextHeroIndex()
        }
    }

    fun setHeroInterval(seconds: Int) {
        val valid = if (seconds <= 0) 15 else seconds
        _heroIntervalSeconds.value = valid
        viewModelScope.launch {
            mediaRepository.setHeroIntervalSeconds(valid)
        }
        startHeroRotation()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _smartSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = mediaRepository.performSmartSearch(query)
                _smartSearchResults.value = results
                _searchResults.value = results.map { it.entity }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _smartSearchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun loadMediaDetails(tmdbId: Int, type: String? = null) {
        viewModelScope.launch {
            _episodes.value = emptyList()
            val media = mediaRepository.getOrFetchMediaByTmdbId(tmdbId, type)
            _selectedMedia.value = media
            if (media?.mediaType == "tv") {
                loadSeasonEpisodes(tmdbId, _selectedSeason.value)
            }
            fetchCastForMedia(tmdbId, media?.mediaType ?: type ?: "movie", media?.cast)
        }
    }

    fun fetchCastForMedia(tmdbId: Int, mediaType: String, rawCastStr: String? = null) {
        if (tmdbId <= 0) return
        if (_mediaCastMap.value[tmdbId]?.isNotEmpty() == true) return

        viewModelScope.launch {
            _mediaCastLoadingMap.value = _mediaCastLoadingMap.value + (tmdbId to true)
            try {
                val castList = mediaRepository.fetchCastForMedia(tmdbId, mediaType, rawCastStr)
                _mediaCastMap.value = _mediaCastMap.value + (tmdbId to castList)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in fetchCastForMedia: ${e.message}")
            } finally {
                _mediaCastLoadingMap.value = _mediaCastLoadingMap.value + (tmdbId to false)
            }
        }
    }

    fun loadSeasonEpisodes(tmdbId: Int, season: Int) {
        val oldSeason = _selectedSeason.value
        _selectedSeason.value = season
        if (oldSeason != season) {
            _episodes.value = emptyList()
        }
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            mediaRepository.fetchAndStoreEpisodes(tmdbId, season)
            mediaRepository.getEpisodesForSeason(tmdbId, season).collect { epList ->
                _episodes.value = epList
            }
        }
    }

    fun toggleMyList(tmdbId: Int, type: String) {
        viewModelScope.launch {
            mediaRepository.toggleMyList(tmdbId, type)
        }
    }

    fun trackCurrentPage(pageName: String) {
        // Auth removed, no page tracking by user
    }

    fun saveWatchProgress(
        tmdbId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        progressPercent: Float,
        positionMs: Long,
        totalDurationMs: Long
    ) {
        viewModelScope.launch {
            mediaRepository.saveWatchProgress(
                tmdbId = tmdbId,
                mediaType = mediaType,
                title = title,
                posterPath = posterPath,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                progressPercent = progressPercent,
                positionMs = positionMs,
                totalDurationMs = totalDurationMs
            )
        }
    }

    fun isMediaInMyList(tmdbId: Int): Flow<Boolean> = mediaRepository.isMediaInMyList(tmdbId)

    fun getWatchHistoryForMedia(tmdbId: Int): Flow<WatchHistoryEntity?> = mediaRepository.getWatchHistoryForMedia(tmdbId)

    fun getSimilarMedia(tmdbId: Int, type: String): Flow<List<MediaEntity>> = mediaRepository.getSimilarMedia(type, tmdbId)

    fun removeFromContinueWatching(item: WatchHistoryEntity) {
        viewModelScope.launch {
            mediaRepository.removeFromContinueWatching(item)
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            mediaRepository.clearWatchHistory()
        }
    }

    private val _isCatalogSyncing = MutableStateFlow(false)
    val isCatalogSyncing: StateFlow<Boolean> = _isCatalogSyncing.asStateFlow()

    private val _catalogSyncResult = MutableStateFlow<com.example.data.repository.MegaEmbedSyncResult?>(null)
    val catalogSyncResult: StateFlow<com.example.data.repository.MegaEmbedSyncResult?> = _catalogSyncResult.asStateFlow()

    fun clearCatalogSyncResult() {
        _catalogSyncResult.value = null
    }

    fun manualRefreshCatalog() {
        if (_isCatalogSyncing.value) return
        viewModelScope.launch {
            _isCatalogSyncing.value = true
            try {
                val megaResult = kotlinx.coroutines.withTimeoutOrNull(35000L) {
                    mediaRepository.syncMegaEmbedCatalog()
                } ?: com.example.data.repository.MegaEmbedSyncResult(
                    errorCount = 1,
                    message = "A sincronização excedeu o tempo limite (timeout)."
                )
                firebaseService.forceRealtimeSyncCheck(database.playFilmeDao())
                _catalogSyncResult.value = megaResult
            } catch (e: Exception) {
                _catalogSyncResult.value = com.example.data.repository.MegaEmbedSyncResult(
                    errorCount = 1,
                    message = "Erro ao sincronizar catálogo: ${e.localizedMessage ?: "Erro desconhecido"}"
                )
            } finally {
                _isCatalogSyncing.value = false
            }
        }
    }

    // --- Notification Actions ---
    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            mediaRepository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            mediaRepository.markAllNotificationsAsRead()
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            mediaRepository.deleteNotification(id)
        }
    }

    // --- Media Requests ---
    private val _requestSearchQuery = MutableStateFlow("")
    val requestSearchQuery: StateFlow<String> = _requestSearchQuery.asStateFlow()

    // Media Request Status helper for UI
    data class RequestResult(
        val entity: MediaEntity,
        val isAlreadyInCatalog: Boolean,
        val isAlreadyRequested: Boolean
    )

    private val _requestSearchResults = MutableStateFlow<List<RequestResult>>(emptyList())
    val requestSearchResults: StateFlow<List<RequestResult>> = _requestSearchResults.asStateFlow()

    private val _isRequestSearching = MutableStateFlow(false)
    val isRequestSearching: StateFlow<Boolean> = _isRequestSearching.asStateFlow()

    private val _requestStatusMessage = MutableStateFlow<String?>(null)
    val requestStatusMessage: StateFlow<String?> = _requestStatusMessage.asStateFlow()

    private var requestSearchJob: Job? = null

    fun onRequestSearchQueryChanged(query: String) {
        _requestSearchQuery.value = query
        requestSearchJob?.cancel()
        _processingRequestKeys.value = emptySet()
        _requestStatusMessage.value = null
        if (query.isBlank()) {
            _requestSearchResults.value = emptyList()
            return
        }
        requestSearchJob = viewModelScope.launch {
            _isRequestSearching.value = true
            delay(300)
            
            // Get all pending requests to check for duplicates instantly
            val allRequests = firebaseService.allRequests.value
            val pendingTmdbIds = allRequests.filter { 
                val s = it.status.lowercase()
                s == "pending" || s == "pendente" || s == "added" || s == "adicionado" || s == "processing" || s == "processando"
            }.map { "${it.mediaType}_${it.tmdbId}" }.toSet()

            val results = mediaRepository.searchTmdbForAdmin(query, "all")
            _requestSearchResults.value = results.map { 
                RequestResult(
                    entity = it.entity,
                    isAlreadyInCatalog = it.isAlreadyInCatalog,
                    isAlreadyRequested = pendingTmdbIds.contains("${it.entity.mediaType}_${it.entity.tmdbId}")
                )
            }
            _isRequestSearching.value = false
        }
    }

    private val _processingRequestKeys = MutableStateFlow<Set<String>>(emptySet())
    val processingRequestKeys: StateFlow<Set<String>> = _processingRequestKeys.asStateFlow()

    // Backward compatibility for any remaining caller
    val isSendingRequest: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    fun clearProcessingStates() {
        _processingRequestKeys.value = emptySet()
    }

    fun clearRequestStatusMessage() {
        _requestStatusMessage.value = null
    }

    fun sendMediaRequest(entity: MediaEntity) {
        val requestKey = "${entity.mediaType}_${entity.tmdbId}"
        // Evitar clique duplo: se já estiver em processamento, ignora novo clique
        if (_processingRequestKeys.value.contains(requestKey)) return
        
        _processingRequestKeys.value = _processingRequestKeys.value + requestKey
        viewModelScope.launch {
            try {
                val dId = firebaseService.getOrGeneratePersistentDeviceId()
                val request = com.example.data.remote.MediaRequest(
                    tmdbId = entity.tmdbId,
                    title = entity.title,
                    originalTitle = entity.originalTitle,
                    mediaType = entity.mediaType,
                    poster = entity.posterPath,
                    backdrop = entity.backdropPath,
                    year = entity.releaseYear,
                    overview = entity.overview,
                    genres = entity.genres,
                    createdAt = System.currentTimeMillis(),
                    status = "pending",
                    deviceId = dId
                )
                val (success, msg) = mediaRepository.sendMediaRequest(request)
                if (success) {
                    // Update only this item to show it has been requested
                    _requestSearchResults.value = _requestSearchResults.value.map { item ->
                        if (item.entity.tmdbId == entity.tmdbId && item.entity.mediaType == entity.mediaType) {
                            item.copy(isAlreadyRequested = true)
                        } else {
                            item
                        }
                    }
                    _smartSearchResults.value = _smartSearchResults.value.map { item ->
                        if (item.entity.tmdbId == entity.tmdbId && item.entity.mediaType == entity.mediaType) {
                            item.copy(
                                status = SearchItemStatus.REQUEST_PENDING,
                                statusLabel = "🟡 PEDIDO ENVIADO",
                                isPendingRequest = true
                            )
                        } else {
                            item
                        }
                    }
                }
                _requestStatusMessage.value = msg
            } catch (e: Exception) {
                _requestStatusMessage.value = "Não foi possível enviar o pedido."
            } finally {
                // SEMPRE remove o item de processamento ao término (sucesso ou erro)
                _processingRequestKeys.value = _processingRequestKeys.value - requestKey
            }
        }
    }
}
