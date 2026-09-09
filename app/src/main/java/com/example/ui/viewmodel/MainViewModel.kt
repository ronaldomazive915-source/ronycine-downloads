package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.MediaRepository
import com.example.data.repository.SmartSearchResultItem
import com.example.data.repository.SearchItemStatus
import com.example.util.MediaClassifier
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

    val remoteUpdateManager = com.example.data.remote.RemoteUpdateManager.getInstance(application)
    val remoteAppConfig = remoteUpdateManager.remoteAppConfig
    val remoteUpdateState = remoteUpdateManager.updateState
    val remoteUpdateEvents = remoteUpdateManager.updateEvents

    fun setPlayerActive(active: Boolean) {
        remoteUpdateManager.isPlayerActive.value = active
    }

    fun onAppForeground() {
        remoteUpdateManager.onForegroundReturn()
    }

    fun checkRemoteUpdateNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val state = remoteUpdateManager.checkNow()
            val msg = when (state) {
                is com.example.data.remote.RemoteUpdateState.UpToDate -> "✓ Você já está na versão remota mais recente (${state.remoteVersion})."
                is com.example.data.remote.RemoteUpdateState.Applied -> "✓ Conteúdo e interface atualizados para a versão v${state.remoteVersion}!"
                is com.example.data.remote.RemoteUpdateState.Offline -> "🟡 Modo offline. Utilizando cache local."
                is com.example.data.remote.RemoteUpdateState.Error -> "🔴 Erro na verificação: ${state.message}"
                else -> "Sincronização concluída."
            }
            onResult(msg)
        }
    }

    val isDeviceBlocked: StateFlow<Boolean> = firebaseService.isDeviceBlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAdminAuthorized: StateFlow<Boolean> = firebaseService.isAdminAuthorized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playerSources: StateFlow<List<com.example.data.remote.PlayerSource>> = firebaseService.playerSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerConfig: StateFlow<com.example.data.remote.PlayerConfig> = firebaseService.playerConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.PlayerConfig())

    init {
        com.example.util.LanguageManager.init(application)
        firebaseService.startPlayerSourcesListener()
    }

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

    val appLanguage: StateFlow<String> = com.example.util.LanguageManager.appLanguage
    val preferredPlayerLanguage: StateFlow<String> = com.example.util.LanguageManager.preferredPlayerLanguage
    val languageSource: StateFlow<String> = com.example.util.LanguageManager.languageSource

    fun setAppLanguage(languageCode: String) {
        val app = getApplication<Application>()
        com.example.util.LanguageManager.setAppLanguage(app, languageCode, source = "manual")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("ronycine.language", languageCode))
            database.playFilmeDao().saveSetting(AppSettingsEntity("ronycine.languageSource", "manual"))
            firebaseService.updateActiveProfileLanguage(
                language = languageCode,
                playerLanguage = com.example.util.LanguageManager.preferredPlayerLanguage.value,
                source = "manual"
            )
        }
    }

    fun setPreferredPlayerLanguage(mode: String) {
        val app = getApplication<Application>()
        com.example.util.LanguageManager.setPreferredPlayerLanguage(app, mode)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.playFilmeDao().saveSetting(AppSettingsEntity("ronycine.preferredPlayerLanguage", mode))
            firebaseService.updateActiveProfileLanguage(
                language = com.example.util.LanguageManager.appLanguage.value,
                playerLanguage = mode,
                source = com.example.util.LanguageManager.languageSource.value
            )
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

    val animes: StateFlow<List<MediaEntity>> = mediaRepository.animes
        .map { list -> list.distinctBy { it.tmdbId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doramas: StateFlow<List<MediaEntity>> = mediaRepository.doramas
        .map { list -> list.distinctBy { it.tmdbId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myList: StateFlow<List<MediaEntity>> = firebaseService.activeProfile
        .flatMapLatest { profile ->
            if (profile != null) mediaRepository.getMyList(profile.id)
            else flowOf(emptyList())
        }
        .map { list -> list.distinctBy { "${it.tmdbId}_${it.mediaType}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<WatchHistoryEntity>> = firebaseService.activeProfile
        .flatMapLatest { profile ->
            if (profile != null) mediaRepository.getContinueWatching(profile.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watchHistory: StateFlow<List<WatchHistoryEntity>> = firebaseService.activeProfile
        .flatMapLatest { profile ->
            if (profile != null) mediaRepository.getWatchHistory(profile.id)
            else flowOf(emptyList())
        }
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
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topRatedMedia: StateFlow<List<MediaEntity>> = allMedia
        .map { list -> list.sortedByDescending { it.rating }.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val releases: StateFlow<List<MediaEntity>> = allMedia
        .map { list -> list.sortedByDescending { it.releaseYear } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topRated: StateFlow<List<MediaEntity>> = allMedia
        .map { list -> list.filter { it.rating >= 7.5 } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedMedia: StateFlow<List<MediaEntity>> = allMedia
        .map { list ->
            list.filter {
                val cat = MediaClassifier.classifyMedia(it)
                cat == MediaClassifier.CATEGORY_MOVIE || cat == MediaClassifier.CATEGORY_SERIES
            }.sortedWith(
                compareByDescending<MediaEntity> { it.addedAt }
                    .thenByDescending { it.id }
            ).take(20)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAnimes: StateFlow<List<MediaEntity>> = animes
        .map { list ->
            list.sortedWith(
                compareByDescending<MediaEntity> { it.addedAt }
                    .thenByDescending { it.id }
            ).take(20)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDoramas: StateFlow<List<MediaEntity>> = doramas
        .map { list ->
            list.sortedWith(
                compareByDescending<MediaEntity> { it.addedAt }
                    .thenByDescending { it.id }
            ).take(20)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
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

    val fallbackFeaturedList: StateFlow<List<com.example.data.local.FeaturedMediaItem>> = combine(
        activeFeaturedItems, 
        featuredMedias
    ) { active, featured ->
        if (active.isNotEmpty()) {
            active
        } else if (featured.isNotEmpty()) {
            featured.map { media ->
                com.example.data.local.FeaturedMediaItem(
                    featured = com.example.data.local.FeaturedMediaEntity(
                        mediaTmdbId = media.tmdbId,
                        mediaType = media.mediaType,
                        trailerUrl = media.trailerKey ?: "",
                        autoPlayTrailer = true
                    ),
                    media = media
                )
            }
        } else {
            emptyList()
        }
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var heroRotationJob: Job? = null
    private val _heroIntervalSeconds = MutableStateFlow(10) // Rotation interval
    val heroIntervalSeconds: StateFlow<Int> = _heroIntervalSeconds.asStateFlow()

    // --- Home Isolated Search ---
    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _homeSearchResults = MutableStateFlow<List<MediaEntity>>(emptyList())
    val homeSearchResults: StateFlow<List<MediaEntity>> = _homeSearchResults.asStateFlow()

    private val _isHomeSearching = MutableStateFlow(false)
    val isHomeSearching: StateFlow<Boolean> = _isHomeSearching.asStateFlow()

    private var homeSearchJob: Job? = null

    // --- Explore (SearchScreen) Isolated Search ---
    private val _exploreSearchQuery = MutableStateFlow("")
    val exploreSearchQuery: StateFlow<String> = _exploreSearchQuery.asStateFlow()
    val searchQuery: StateFlow<String> = _exploreSearchQuery.asStateFlow() // Alias for backwards compatibility

    private val _exploreSearchResults = MutableStateFlow<List<MediaEntity>>(emptyList())
    val exploreSearchResults: StateFlow<List<MediaEntity>> = _exploreSearchResults.asStateFlow()
    val searchResults: StateFlow<List<MediaEntity>> = _exploreSearchResults.asStateFlow() // Alias for backwards compatibility

    private val _exploreSmartSearchResults = MutableStateFlow<List<SmartSearchResultItem>>(emptyList())
    val exploreSmartSearchResults: StateFlow<List<SmartSearchResultItem>> = _exploreSmartSearchResults.asStateFlow()
    val smartSearchResults: StateFlow<List<SmartSearchResultItem>> = _exploreSmartSearchResults.asStateFlow() // Alias for backwards compatibility

    private val _isExploreSearching = MutableStateFlow(false)
    val isExploreSearching: StateFlow<Boolean> = _isExploreSearching.asStateFlow()
    val isSearching: StateFlow<Boolean> = _isExploreSearching.asStateFlow() // Alias for backwards compatibility

    private var exploreSearchJob: Job? = null

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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            unreadNotificationsCount.collect { count ->
                try {
                    com.example.util.BadgeUtils.updateBadge(application, count)
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Error updating badge: ${e.message}")
                }
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mediaRepository.seedInitialCatalogIfEmpty()
            _megaEmbedConfig.value = mediaRepository.getMegaEmbedConfig()
            val interval = mediaRepository.getHeroIntervalSeconds()
            _heroIntervalSeconds.value = if (interval <= 0) 10 else interval
            
            // Collect media, Top 10 config and Featured config to populate featured hero and top 10
            launch(kotlinx.coroutines.Dispatchers.Default) {
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

    // =========================================================================
    // HOME ISOLATED SEARCH (Local Catalog + Smart Match, Debounced)
    // =========================================================================

    fun onHomeSearchQueryChanged(query: String) {
        _homeSearchQuery.value = query
        homeSearchJob?.cancel()

        if (query.isBlank()) {
            _homeSearchResults.value = emptyList()
            _isHomeSearching.value = false
            return
        }

        homeSearchJob = viewModelScope.launch {
            delay(350L) // 350ms debounce
            _isHomeSearching.value = true
            try {
                val q = query.trim().lowercase()
                
                // 1. Fast local catalog search
                val localMatches = allMedia.value.filter { media ->
                    val titleMatch = media.title.lowercase().contains(q)
                    val originalTitleMatch = media.originalTitle.lowercase().contains(q)
                    val genreMatch = media.genres.lowercase().contains(q)
                    val categoryMatch = when (q) {
                        "anime", "animes" -> MediaClassifier.isAnime(media)
                        "dorama", "doramas" -> MediaClassifier.isDorama(media)
                        "filme", "filmes", "movie" -> media.mediaType.equals("movie", ignoreCase = true)
                        "serie", "série", "series", "tv" -> media.mediaType.equals("tv", ignoreCase = true)
                        else -> false
                    }
                    titleMatch || originalTitleMatch || genreMatch || categoryMatch
                }

                // 2. Repository smart search for full catalog coverage
                val smartMatches = try {
                    mediaRepository.performSmartSearch(query).map { it.entity }
                } catch (_: Exception) {
                    emptyList()
                }

                // 3. Combine and deduplicate preserving local relevance
                val combined = (localMatches + smartMatches).distinctBy { "${it.tmdbId}_${it.mediaType}" }
                _homeSearchResults.value = combined
            } catch (e: Exception) {
                _homeSearchResults.value = emptyList()
            } finally {
                _isHomeSearching.value = false
            }
        }
    }

    fun clearHomeSearch() {
        homeSearchJob?.cancel()
        _homeSearchQuery.value = ""
        _homeSearchResults.value = emptyList()
        _isHomeSearching.value = false
    }

    // =========================================================================
    // EXPLORE ISOLATED SEARCH (Catalog Explorer Screen, Debounced)
    // =========================================================================

    fun onExploreSearchQueryChanged(query: String) {
        _exploreSearchQuery.value = query
        exploreSearchJob?.cancel()

        if (query.isBlank()) {
            _exploreSearchResults.value = emptyList()
            _exploreSmartSearchResults.value = emptyList()
            _isExploreSearching.value = false
            return
        }

        exploreSearchJob = viewModelScope.launch {
            delay(350L) // 350ms debounce
            _isExploreSearching.value = true
            try {
                val results = mediaRepository.performSmartSearch(query)
                _exploreSmartSearchResults.value = results
                _exploreSearchResults.value = results.map { it.entity }
            } catch (e: Exception) {
                _exploreSearchResults.value = emptyList()
                _exploreSmartSearchResults.value = emptyList()
            } finally {
                _isExploreSearching.value = false
            }
        }
    }

    fun clearExploreSearch() {
        exploreSearchJob?.cancel()
        _exploreSearchQuery.value = ""
        _exploreSearchResults.value = emptyList()
        _exploreSmartSearchResults.value = emptyList()
        _isExploreSearching.value = false
    }

    // Backwards-compatible delegator for any external caller
    fun onSearchQueryChanged(query: String) {
        onExploreSearchQueryChanged(query)
    }

    private var detailJob: Job? = null
    private val vmDetailsCache = java.util.concurrent.ConcurrentHashMap<String, MediaEntity>()

    fun loadMediaDetails(tmdbId: Int, type: String? = null) {
        if (tmdbId <= 0) return
        val normType = if (type.equals("tv", ignoreCase = true) || type.equals("serie", ignoreCase = true) || type.equals("series", ignoreCase = true)) "tv" else "movie"
        val cacheKey = "${normType}_$tmdbId"

        // 1. Instant Synchronous State Flow Emission (0ms latency!)
        val existing = vmDetailsCache[cacheKey]
            ?: allMedia.value.firstOrNull { it.tmdbId == tmdbId }

        if (existing != null) {
            _selectedMedia.value = existing
        } else {
            // Optimistic placeholder so DetailScreen has immediate non-null object with tmdbId and type
            _selectedMedia.value = MediaEntity(
                tmdbId = tmdbId,
                mediaType = normType,
                title = "",
                overview = "",
                posterPath = null,
                backdropPath = null,
                releaseYear = "",
                rating = 0.0,
                genres = ""
            )
        }

        _selectedSeason.value = 1
        _episodes.value = emptyList()

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                val fullMedia = mediaRepository.getOrFetchMediaByTmdbId(tmdbId, normType)
                if (fullMedia != null) {
                    vmDetailsCache[cacheKey] = fullMedia
                    _selectedMedia.value = fullMedia

                    if (fullMedia.mediaType == "tv") {
                        loadSeasonEpisodes(tmdbId, _selectedSeason.value)
                    }
                    fetchCastForMedia(tmdbId, fullMedia.mediaType, fullMedia.cast)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error loading media details for $tmdbId: ${e.message}")
            }
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
            // 1. Listen to local Room DB immediately (0ms delay)
            launch {
                mediaRepository.getEpisodesForSeason(tmdbId, season).collect { epList ->
                    if (epList.isNotEmpty()) {
                        _episodes.value = epList
                    }
                }
            }
            // 2. Fetch/update in background without blocking local DB Flow
            try {
                mediaRepository.fetchAndStoreEpisodes(tmdbId, season)
            } catch (_: Exception) {}
        }
    }

    fun toggleMyList(tmdbId: Int, type: String) {
        viewModelScope.launch {
            val profile = firebaseService.activeProfile.value
            if (profile != null) {
                mediaRepository.toggleMyList(tmdbId, type, profile.id)
            }
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
            val profile = firebaseService.activeProfile.value
            if (profile != null) {
                mediaRepository.saveWatchProgress(
                    profileId = profile.id,
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
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun isMediaInMyList(tmdbId: Int): Flow<Boolean> = firebaseService.activeProfile.flatMapLatest { profile ->
        if (profile != null) mediaRepository.isMediaInMyList(tmdbId, profile.id)
        else flowOf(false)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getWatchHistoryForMedia(tmdbId: Int): Flow<WatchHistoryEntity?> = firebaseService.activeProfile.flatMapLatest { profile ->
        if (profile != null) mediaRepository.getWatchHistoryForMedia(tmdbId, profile.id)
        else flowOf(null)
    }

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
                    _exploreSmartSearchResults.value = _exploreSmartSearchResults.value.map { item ->
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

    suspend fun fetchAnimeFromTMDB(page: Int = 1) = mediaRepository.fetchAnimeFromTMDB(page)
    suspend fun fetchDoramaFromTMDB(page: Int = 1) = mediaRepository.fetchDoramaFromTMDB(page)
    suspend fun importAnimeFromTMDB(tmdbId: Int) = mediaRepository.importAnimeFromTMDB(tmdbId)
    suspend fun importDoramaFromTMDB(tmdbId: Int) = mediaRepository.importDoramaFromTMDB(tmdbId)
}
