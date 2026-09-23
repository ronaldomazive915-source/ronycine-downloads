package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.MediaRepository
import com.example.data.repository.SmartSearchResultItem
import com.example.data.repository.SearchItemStatus
import com.example.util.MediaClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.data.download.DownloadResolutionResult
import com.example.data.download.DownloadResolver

sealed class DownloadState {
    object Idle : DownloadState()
    data class Resolving(val tmdbId: Int, val season: Int? = null, val episode: Int? = null) : DownloadState()
    data class Ready(val url: String, val fileName: String, val source: String?) : DownloadState()
    data class Error(val code: String, val message: String) : DownloadState()
}

data class PendingDownloadIntent(
    val type: String,
    val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    val mediaTitle: String? = null,
    val isSeriesDownloadSheet: Boolean = false,
    val isPlayer: Boolean = false
)

enum class AuthGuardResult {
    AUTHENTICATED,
    CANCELLED,
    FAILED
}

sealed class PendingAction {
    object None : PendingAction()
    data class ToggleMyList(val tmdbId: Int, val type: String) : PendingAction()
    data class Reaction(val tmdbId: Int, val type: String, val isLike: Boolean, val isRemoving: Boolean = false) : PendingAction()
    data class Navigate(val route: String) : PendingAction()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _pendingDownloadIntent = MutableStateFlow<PendingDownloadIntent?>(null)
    val pendingDownloadIntent: StateFlow<PendingDownloadIntent?> = _pendingDownloadIntent.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction>(PendingAction.None)
    val pendingAction: StateFlow<PendingAction> = _pendingAction.asStateFlow()

    fun setPendingAction(action: PendingAction) {
        _pendingAction.value = action
    }

    fun consumePendingAction(): PendingAction {
        val action = _pendingAction.value
        _pendingAction.value = PendingAction.None
        return action
    }

    fun setPendingDownloadIntent(intent: PendingDownloadIntent?) {
        _pendingDownloadIntent.value = intent
    }

    fun requireAuthenticationForDownload(
        isAuthenticated: Boolean,
        pendingIntent: PendingDownloadIntent,
        onNavigateToLogin: () -> Unit,
        onAlreadyAuthenticated: () -> Unit
    ): AuthGuardResult {
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (isAuthenticated && fbUser != null) {
            onAlreadyAuthenticated()
            return AuthGuardResult.AUTHENTICATED
        } else {
            setPendingDownloadIntent(pendingIntent)
            onNavigateToLogin()
            return AuthGuardResult.FAILED
        }
    }

    fun clearPendingDownloadIntent() {
        _pendingDownloadIntent.value = null
    }

    fun requireAuthentication(
        isAuthenticated: Boolean,
        onNavigateToLogin: () -> Unit,
        pendingAction: PendingAction? = null,
        onAlreadyAuthenticated: () -> Unit
    ) {
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (isAuthenticated && fbUser != null) {
            onAlreadyAuthenticated()
        } else {
            if (pendingAction != null) {
                setPendingAction(pendingAction)
            }
            onNavigateToLogin()
        }
    }

    private val downloadResolver = DownloadResolver(application)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun resolveDownload(
        type: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        mediaTitle: String? = null
    ) {
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (fbUser == null) {
            _downloadState.value = DownloadState.Idle
            return
        }
        viewModelScope.launch {
            _downloadState.value = DownloadState.Resolving(tmdbId, season, episode)
            
            // Safety timeout
            val result = withContext(Dispatchers.IO) {
                try {
                    downloadResolver.resolve(type, tmdbId, season, episode, mediaTitle)
                } catch (e: Exception) {
                    DownloadResolutionResult.Error("TIMEOUT", "Tempo de resposta excedido.")
                }
            }

            when (result) {
                is DownloadResolutionResult.Success -> {
                    _downloadState.value = DownloadState.Ready(result.url, result.fileName, result.source)
                }
                is DownloadResolutionResult.Error -> {
                    _downloadState.value = DownloadState.Error(result.code, result.message)
                }
            }
        }
    }

    private val database = AppDatabase.getInstance(application)
    private val firebaseService = com.example.data.remote.FirebaseService.getInstance(application)
    val mediaRepository = MediaRepository(database.playFilmeDao(), firebaseService)

    fun canStartPlayback(): Boolean {
        return true
    }

    fun requestPlayback(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        watchRoute: String,
        onAllowed: () -> Unit
    ) {
        onAllowed()
    }

    fun consumePendingPlaybackRoute(): String? {
        return null
    }

    private val _heroIntervalSeconds = MutableStateFlow(10)
    val heroIntervalSeconds: StateFlow<Int> = _heroIntervalSeconds.asStateFlow()

    suspend fun refreshHomeData(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            firebaseService.forceRealtimeSyncCheck(database.playFilmeDao())
            remoteUpdateManager.checkNow()
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("MainViewModel", "Erro ao atualizar dados da Home: ${e.message}")
            false
        }
    }

    fun updateExploreCategory(cat: String) { _exploreCategory.value = cat }
    fun updateExploreQuickFilter(filter: String) { _exploreQuickFilter.value = filter }
    fun updateExploreGenre(genre: String?) { _exploreGenreFilter.value = genre }
    fun updateExploreYear(year: String) { _exploreYearFilter.value = year }
    fun updateExploreMinRating(rating: Double) { _exploreMinRatingFilter.value = rating }
    fun updateExploreSort(sort: com.example.ui.screens.ExploreSortOption) { _exploreSortOption.value = sort }

    // --- Remote Config & Device Management ---
    val remoteConfig: StateFlow<com.example.data.remote.RemoteConfigEntity> = firebaseService.remoteConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.RemoteConfigEntity())

    val remoteUpdateManager = com.example.data.remote.RemoteUpdateManager.getInstance(application)
    val remoteAppConfig = remoteUpdateManager.remoteAppConfig
    val remoteUpdateState = remoteUpdateManager.updateState
    val remoteUpdateEvents = remoteUpdateManager.updateEvents
    val manualUpdateConfig = firebaseService.manualUpdateConfig
    val updateFetchStatus = firebaseService.updateFetchStatus

    fun listenAppUpdatesCurrent(
        onUpdate: (com.example.data.remote.AppUpdate) -> Unit,
        onError: (Exception) -> Unit
    ) = firebaseService.listenAppUpdatesCurrent(onUpdate, onError)

    fun fetchAppUpdatesCurrent(fromServer: Boolean = true, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val res = firebaseService.fetchAppUpdatesCurrent(fromServer)
            onComplete?.invoke(res.isSuccess)
        }
    }

    fun getCachedAppUpdate() = firebaseService.getCachedAppUpdate()

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

    // --- Explore Screen Filter States ---
    private val _exploreCategory = MutableStateFlow("all")
    val exploreCategory: StateFlow<String> = _exploreCategory.asStateFlow()

    private val _exploreQuickFilter = MutableStateFlow("Todos")
    val exploreQuickFilter: StateFlow<String> = _exploreQuickFilter.asStateFlow()

    private val _exploreGenreFilter = MutableStateFlow<String?>(null)
    val exploreGenreFilter: StateFlow<String?> = _exploreGenreFilter.asStateFlow()

    private val _exploreYearFilter = MutableStateFlow("Todos")
    val exploreYearFilter: StateFlow<String> = _exploreYearFilter.asStateFlow()

    private val _exploreMinRatingFilter = MutableStateFlow(0.0)
    val exploreMinRatingFilter: StateFlow<Double> = _exploreMinRatingFilter.asStateFlow()

    private val _exploreSortOption = MutableStateFlow(com.example.ui.screens.ExploreSortOption.RECENTES)
    val exploreSortOption: StateFlow<com.example.ui.screens.ExploreSortOption> = _exploreSortOption.asStateFlow()

    private val _exploreSearchQuery = MutableStateFlow("")
    val exploreSearchQuery: StateFlow<String> = _exploreSearchQuery.asStateFlow()
    val searchQuery: StateFlow<String> = _exploreSearchQuery.asStateFlow()

    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _allMediaRaw = mediaRepository.allMedia
    val allMedia: StateFlow<List<MediaEntity>> = combine(_allMediaRaw, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Optimized Grouped Media Processing ---
    data class CategorizedMedia(
        val movies: List<MediaEntity> = emptyList(),
        val series: List<MediaEntity> = emptyList(),
        val animes: List<MediaEntity> = emptyList(),
        val doramas: List<MediaEntity> = emptyList(),
        val myMedia: List<MediaEntity> = emptyList(),
        val recentlyAdded: List<MediaEntity> = emptyList(),
        val trending: List<MediaEntity> = emptyList(),
        val releases: List<MediaEntity> = emptyList(),
        val topRated: List<MediaEntity> = emptyList(),
        val genres: List<String> = emptyList(),
        val years: List<String> = emptyList()
    )

    private val categorizedMedia: StateFlow<CategorizedMedia> = allMedia
        .map { list ->
            val movies = mutableListOf<MediaEntity>()
            val series = mutableListOf<MediaEntity>()
            val animes = mutableListOf<MediaEntity>()
            val doramas = mutableListOf<MediaEntity>()
            val myMedia = mutableListOf<MediaEntity>()
            val genresSet = mutableSetOf<String>()
            val yearsSet = mutableSetOf<String>()

            list.forEach { media ->
                val type = media.mediaType
                val cat = media.mediaCategory
                
                if (type == "movie") movies.add(media)
                if (type == "tv") series.add(media)
                
                if (cat == "anime" || media.genres.contains("Anime", ignoreCase = true)) animes.add(media)
                if (cat == "dorama" || media.genres.contains("Dorama", ignoreCase = true)) doramas.add(media)
                if (cat == "meu_conteudo") myMedia.add(media)

                media.genres.split(",").forEach {
                    val g = it.trim()
                    if (g.isNotEmpty() && g != "Todos") genresSet.add(g)
                }
                if (media.releaseYear.isNotBlank()) yearsSet.add(media.releaseYear)
            }

            CategorizedMedia(
                movies = movies,
                series = series,
                animes = animes,
                doramas = doramas,
                myMedia = myMedia,
                genres = genresSet.toList().sorted(),
                years = yearsSet.toList().sortedDescending()
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategorizedMedia())

    val movies: StateFlow<List<MediaEntity>> = categorizedMedia.map { it.movies }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<MediaEntity>> = categorizedMedia.map { it.series }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val animes: StateFlow<List<MediaEntity>> = categorizedMedia.map { it.animes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doramas: StateFlow<List<MediaEntity>> = categorizedMedia.map { it.doramas }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myMedia: StateFlow<List<MediaEntity>> = categorizedMedia.map { it.myMedia }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGenres: StateFlow<List<String>> = categorizedMedia.map { it.genres }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val years: StateFlow<List<String>> = categorizedMedia.map { it.years }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myList: StateFlow<List<MediaEntity>> = firebaseService.activeProfile
        .flatMapLatest { profile ->
            val profileId = profile?.id ?: "default"
            mediaRepository.getMyList(profileId).map { list ->
                com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<WatchHistoryEntity>> = combine(
        firebaseService.activeProfile.flatMapLatest { profile ->
            val profileId = profile?.id ?: "default"
            mediaRepository.getContinueWatching(profileId)
        },
        firebaseService.activeProfile,
        allMedia
    ) { historyList, profile, allowedMedia ->
        if (profile == null || !profile.isKidsProfile) {
            historyList
        } else {
            val allowedIds = allowedMedia.map { "${it.tmdbId}_${it.mediaType}" }.toSet()
            historyList.filter { allowedIds.contains("${it.tmdbId}_${it.mediaType}") }
        }
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watchHistory: StateFlow<List<WatchHistoryEntity>> = combine(
        firebaseService.activeProfile.flatMapLatest { profile ->
            val profileId = profile?.id ?: "default"
            mediaRepository.getWatchHistory(profileId)
        },
        firebaseService.activeProfile,
        allMedia
    ) { historyList, profile, allowedMedia ->
        if (profile == null || !profile.isKidsProfile) {
            historyList
        } else {
            val allowedIds = allowedMedia.map { "${it.tmdbId}_${it.mediaType}" }.toSet()
            historyList.filter { allowedIds.contains("${it.tmdbId}_${it.mediaType}") }
        }
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAdded: StateFlow<List<MediaEntity>> = combine(mediaRepository.recentlyAddedMedia, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trending: StateFlow<List<MediaEntity>> = combine(mediaRepository.trendingMedia, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun getInstalledVersionCode(): Int {
        return try {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            pInfo.versionName ?: "v1.1.2"
        } catch (e: Exception) {
            "v1.1.2"
        }
    }

    val shouldShowUpdateDot: StateFlow<Boolean> = manualUpdateConfig.map { update ->
        update.active && update.versionCode > getInstalledVersionCode()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    val trendingMedia: StateFlow<List<MediaEntity>> = combine(mediaRepository.trendingMedia, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topRatedMedia: StateFlow<List<MediaEntity>> = combine(mediaRepository.topRatedMedia, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val releases: StateFlow<List<MediaEntity>> = combine(mediaRepository.releases, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topRated: StateFlow<List<MediaEntity>> = combine(mediaRepository.topRated, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedMedia: StateFlow<List<MediaEntity>> = combine(mediaRepository.recentlyAddedMedia, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAnimes: StateFlow<List<MediaEntity>> = combine(mediaRepository.recentAnimes, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDoramas: StateFlow<List<MediaEntity>> = combine(mediaRepository.recentDoramas, firebaseService.activeProfile) { list, profile ->
        com.example.util.ContentAccessManager.filterContentForProfile(profile, list)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFeaturedItems: StateFlow<List<FeaturedMediaItem>> = combine(mediaRepository.activeFeaturedItems, firebaseService.activeProfile) { list, profile ->
        if (profile == null || !profile.isKidsProfile) list
        else list.filter { com.example.util.ContentAccessManager.canProfileAccessContent(profile, it.media) }
    }
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
        val filteredActive = active.filter { MediaClassifier.isMovieOrSeries(it.media) }
        val filteredFeatured = featured.filter { MediaClassifier.isMovieOrSeries(it) }
        if (filteredActive.isNotEmpty()) {
            filteredActive
        } else if (filteredFeatured.isNotEmpty()) {
            filteredFeatured.map { media ->
                com.example.data.local.FeaturedMediaItem(
                    featured = com.example.data.local.FeaturedMediaEntity(
                        mediaTmdbId = media.tmdbId,
                        mediaType = media.mediaType,
                        trailerUrl = media.trailerKey ?: "",
                        autoPlayTrailer = false
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

    val filteredExploreCatalog: StateFlow<List<MediaEntity>> = combine(
        listOf(
            allMedia,
            exploreCategory,
            exploreQuickFilter,
            exploreGenreFilter,
            exploreYearFilter,
            exploreMinRatingFilter,
            exploreSortOption,
            exploreSearchQuery
        )
    ) { args ->
        val list = args[0] as List<MediaEntity>
        val cat = args[1] as String
        val quick = args[2] as String
        val genre = args[3] as String?
        val year = args[4] as String
        val minRating = args[5] as Double
        val sort = args[6] as com.example.ui.screens.ExploreSortOption
        val query = args[7] as String

        // Heavy filtering and sorting in background
        list.filter { media ->
            val classifierCat = MediaClassifier.classifyMedia(media)

            // 1. Category check
            val categoryMatch = when (cat) {
                "movie" -> classifierCat == MediaClassifier.CATEGORY_MOVIE
                "tv" -> classifierCat == MediaClassifier.CATEGORY_SERIES
                "anime" -> classifierCat == MediaClassifier.CATEGORY_ANIME
                "dorama" -> classifierCat == MediaClassifier.CATEGORY_DORAMA
                else -> true
            }
            if (!categoryMatch) return@filter false

            // 2. Search query match
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val titleMatch = media.title.lowercase().contains(q)
                val originalTitleMatch = media.originalTitle.lowercase().contains(q)
                val genreMatch = media.genres.lowercase().contains(q)
                if (!titleMatch && !originalTitleMatch && !genreMatch) return@filter false
            }

            // 3. Quick Filter check
            when (quick) {
                "8+ ⭐" -> if (media.rating < 8.0) return@filter false
                "Todos", "Recentes", "Populares" -> { /* handled in sort */ }
                else -> if (!media.genres.contains(quick, ignoreCase = true)) return@filter false
            }

            // 4. Advanced Genre filter
            if (!genre.isNullOrBlank() && genre != "Todos") {
                if (!media.genres.contains(genre, ignoreCase = true)) return@filter false
            }

            // 5. Year Filter
            if (year != "Todos") {
                when (year) {
                    "2010s" -> {
                        val y = media.releaseYear.toIntOrNull()
                        if (y == null || y !in 2010..2019) return@filter false
                    }
                    "Clássicos" -> {
                        val y = media.releaseYear.toIntOrNull()
                        if (y == null || y >= 2010) return@filter false
                    }
                    else -> if (!media.releaseYear.startsWith(year)) return@filter false
                }
            }

            // 6. Rating Filter
            if (minRating > 0.0 && media.rating < minRating) return@filter false

            true
        }.distinctBy { "${it.tmdbId}_${it.mediaType}" }
        .let { filteredList ->
            when {
                quick == "Recentes" -> filteredList.sortedWith(compareByDescending<MediaEntity> { it.releaseYear }.thenByDescending { it.addedAt })
                quick == "Populares" -> filteredList.sortedByDescending { it.rating }
                else -> when (sort) {
                    com.example.ui.screens.ExploreSortOption.RECENTES -> filteredList.sortedWith(compareByDescending<MediaEntity> { it.releaseYear }.thenByDescending { it.addedAt })
                    com.example.ui.screens.ExploreSortOption.POPULARES -> filteredList.sortedByDescending { it.rating }
                    com.example.ui.screens.ExploreSortOption.MELHOR_AVALIADOS -> filteredList.sortedByDescending { it.rating }
                    com.example.ui.screens.ExploreSortOption.ANTIGOS -> filteredList.sortedBy { it.releaseYear.ifBlank { "9999" } }
                    com.example.ui.screens.ExploreSortOption.TITULO_AZ -> filteredList.sortedBy { it.title.lowercase() }
                    com.example.ui.screens.ExploreSortOption.TITULO_ZA -> filteredList.sortedByDescending { it.title.lowercase() }
                }
            }
        }
    }
    .debounce(300L)
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Contextual Quick Filters for Explore - Offloaded from UI Thread to ViewModel
    val exploreContextualQuickFilters: StateFlow<List<String>> = combine(
        exploreCategory,
        allMedia
    ) { cat, list ->
        val base = mutableListOf("Todos", "Recentes", "Populares", "8+ ⭐")
        
        // Fast extraction of top 8 genres for the selected category
        val topGenres = list.asSequence()
            .filter { media ->
                val classifierCat = MediaClassifier.classifyMedia(media)
                when (cat) {
                    "movie" -> classifierCat == MediaClassifier.CATEGORY_MOVIE
                    "tv" -> classifierCat == MediaClassifier.CATEGORY_SERIES
                    "anime" -> classifierCat == MediaClassifier.CATEGORY_ANIME
                    "dorama" -> classifierCat == MediaClassifier.CATEGORY_DORAMA
                    else -> true
                }
            }
            .flatMap { it.genres.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "Todos" }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
            .toList()
            
        base.addAll(topGenres)
        base
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Todos", "Recentes", "Populares", "8+ ⭐"))
    // --- Categorized Media Sections for Home (Optimized) ---
    val topMovies: StateFlow<List<MediaEntity>> = movies
        .debounce(300L)
        .map { it.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val releaseMovies: StateFlow<List<MediaEntity>> = movies
        .debounce(300L)
        .map { it.sortedByDescending { m -> m.releaseYear } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topRatedMovies: StateFlow<List<MediaEntity>> = movies
        .debounce(300L)
        .map { it.filter { m -> m.rating >= 7.0 } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSeries: StateFlow<List<MediaEntity>> = series
        .debounce(300L)
        .map { it.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val releaseSeries: StateFlow<List<MediaEntity>> = series
        .debounce(300L)
        .map { it.sortedByDescending { m -> m.releaseYear } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredRecentlyAdded: StateFlow<List<MediaEntity>> = recentlyAddedMedia
        .debounce(400L)
        .map { list -> list.filter { MediaClassifier.isMovieOrSeries(it) }.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTrending: StateFlow<List<MediaEntity>> = trendingMedia
        .debounce(400L)
        .map { list -> list.filter { MediaClassifier.isMovieOrSeries(it) }.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredReleases: StateFlow<List<MediaEntity>> = releases
        .debounce(400L)
        .map { list -> list.filter { MediaClassifier.isMovieOrSeries(it) }.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTopRated: StateFlow<List<MediaEntity>> = topRated
        .debounce(400L)
        .map { list -> list.filter { MediaClassifier.isMovieOrSeries(it) }.take(15) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fix for filteredPopularMovies and series to use debounced flows
    val filteredPopularMovies: StateFlow<List<MediaEntity>> = movies
        .debounce(400L)
        .map { it.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val filteredPopularSeries: StateFlow<List<MediaEntity>> = series
        .debounce(400L)
        .map { it.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displayAnimes: StateFlow<List<MediaEntity>> = combine(recentAnimes, animes) { recent, all ->
        if (recent.isNotEmpty()) recent else all
    }
    .debounce(500L)
    .map { it.take(20) }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displayDoramas: StateFlow<List<MediaEntity>> = combine(recentDoramas, doramas) { recent, all ->
        if (recent.isNotEmpty()) recent else all
    }
    .debounce(500L)
    .map { it.take(20) }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val destaqueItems: StateFlow<List<MediaEntity>> = combine(
        featuredMedias, 
        fallbackFeaturedList, 
        movies, 
        series
    ) { featured, fallback, m, s ->
        val candidates = if (featured.isNotEmpty()) {
            featured
        } else if (fallback.isNotEmpty()) {
            fallback.map { it.media }
        } else {
            emptyList()
        }
        val filtered = candidates.filter { MediaClassifier.isMovieOrSeries(it) }
        if (filtered.isNotEmpty()) {
            filtered
        } else {
            (m.take(8) + s.take(8)).distinctBy { "${it.mediaType}:${it.tmdbId}" }
        }
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _homeSearchResults = MutableStateFlow<List<MediaEntity>>(emptyList())
    val homeSearchResults: StateFlow<List<MediaEntity>> = _homeSearchResults.asStateFlow()

    private val _isHomeSearching = MutableStateFlow(false)
    val isHomeSearching: StateFlow<Boolean> = _isHomeSearching.asStateFlow()

    private var homeSearchJob: Job? = null

    // --- Explore (SearchScreen) Isolated Search ---
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

    val calendarRepository = com.example.data.repository.CalendarRepository.getInstance(application)
    private val _selectedMediaReleaseInfo = MutableStateFlow<com.example.data.remote.MediaReleaseInfo?>(null)
    val selectedMediaReleaseInfo: StateFlow<com.example.data.remote.MediaReleaseInfo?> = _selectedMediaReleaseInfo.asStateFlow()
    private var releaseInfoJob: Job? = null

    private val _mediaCastMap = MutableStateFlow<Map<Int, List<DetailedCastMember>>>(emptyMap())
    val mediaCastMap: StateFlow<Map<Int, List<DetailedCastMember>>> = _mediaCastMap.asStateFlow()

    private val _mediaCastLoadingMap = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val mediaCastLoadingMap: StateFlow<Map<Int, Boolean>> = _mediaCastLoadingMap.asStateFlow()

    private val _episodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    val episodes: StateFlow<List<EpisodeEntity>> = _episodes.asStateFlow()

    // Cache for episodes to avoid redundant Room/Network calls when switching seasons
    private val episodesCache = mutableMapOf<String, List<EpisodeEntity>>()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private var episodesJob: Job? = null

    val activeProfile: StateFlow<com.example.data.remote.UserProfile?> = firebaseService.activeProfile

    private val _megaEmbedConfig = MutableStateFlow(com.example.data.remote.MegaEmbedConfig())
    val megaEmbedConfig: StateFlow<com.example.data.remote.MegaEmbedConfig> = _megaEmbedConfig.asStateFlow()

    private data class HeroTop10Bundle(
        val list: List<MediaEntity>,
        val top10Config: com.example.data.remote.Top10ConfigEntity,
        val featuredConfig: com.example.data.remote.FeaturedConfigEntity,
        val profile: com.example.data.remote.UserProfile?
    )

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
            
            // Collect media, Top 10 config, Featured config, and activeProfile to populate featured hero and top 10
            launch(kotlinx.coroutines.Dispatchers.Default) {
                combine(allMedia, firebaseService.top10Config, firebaseService.featuredConfig, firebaseService.activeProfile) { list, top10Cfg, featCfg, profile ->
                    HeroTop10Bundle(list, top10Cfg, featCfg, profile)
                }.collect { bundle ->
                    val list = bundle.list
                    val config = bundle.top10Config
                    val featuredConfig = bundle.featuredConfig
                    val currentProfile = bundle.profile

                    if (list.isNotEmpty()) {
                        // Create a map for O(1) lookups
                        val mediaMap = list.associateBy { "${it.tmdbId}_${it.mediaType}" }
                        val movieOrSeriesList = list.filter { MediaClassifier.isMovieOrSeries(it) }

                        // 1. Featured / Hero section (Strict Kids Mode verification)
                        val activeFeatured = featuredConfig.items.filter { it.isActive }.sortedBy { it.displayOrder }
                        val safeConfigFeatured = activeFeatured.mapNotNull { featItem ->
                            val inCatalog = mediaMap["${featItem.tmdbId}_${featItem.mediaType}"]
                            val entity = if (inCatalog != null) {
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
                            if (com.example.util.ContentAccessManager.canProfileAccessContent(currentProfile, entity)) {
                                entity
                            } else null
                        }

                        if (safeConfigFeatured.isNotEmpty()) {
                            _featuredMedias.value = safeConfigFeatured
                        } else {
                            val heroCandidates = movieOrSeriesList.filter { it.isHeroFeatured }
                                .ifEmpty { movieOrSeriesList.filter { it.backdropPath != null }.sortedByDescending { it.rating } }
                                .ifEmpty { movieOrSeriesList }
                            _featuredMedias.value = heroCandidates.take(6)
                        }
                        
                        // 2. Use Configured Top 10 if available, otherwise fallback to default rating-based mix (Strict Kids Mode verification)
                        val resolvedItems = if (config.items.isNotEmpty()) {
                            config.items.sortedBy { it.rank }.mapNotNull { item ->
                                val candidate = mediaMap["${item.tmdbId}_${item.mediaType}"]
                                if (candidate != null && MediaClassifier.isMovieOrSeries(candidate) && com.example.util.ContentAccessManager.canProfileAccessContent(currentProfile, candidate)) {
                                    candidate
                                } else null
                            }
                        } else emptyList()

                        if (resolvedItems.isNotEmpty()) {
                            _top10Medias.value = resolvedItems
                        } else {
                            _top10Medias.value = generateDefaultTop10(list)
                        }
                    } else {
                        _featuredMedias.value = emptyList()
                        _top10Medias.value = emptyList()
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
        val nonAnimeDorama = list.filter { MediaClassifier.isMovieOrSeries(it) }
        val moviesList = nonAnimeDorama.filter { it.mediaType == "movie" }.sortedByDescending { it.rating }
        val seriesList = nonAnimeDorama.filter { it.mediaType == "tv" || it.mediaType == "series" }.sortedByDescending { it.rating }
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
                
                // 1. Fast local catalog search - MOVE TO DEFAULT DISPATCHER
                val localMatches = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    allMedia.value.filter { media ->
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
                }

                // 2. Repository smart search for full catalog coverage
                val smartMatches = try {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val raw = mediaRepository.performSmartSearch(query).map { it.entity }
                        com.example.util.ContentAccessManager.filterContentForProfile(firebaseService.activeProfile.value, raw)
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                // 3. Combine and deduplicate preserving local relevance
                val combined = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    (localMatches + smartMatches).distinctBy { "${it.tmdbId}_${it.mediaType}" }
                }
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
                val results = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    mediaRepository.performSmartSearch(query)
                }
                
                val profile = firebaseService.activeProfile.value
                val filteredSmartResults = if (profile != null && profile.isKidsProfile) {
                    results.filter { com.example.util.ContentAccessManager.canProfileAccessContent(profile, it.entity) }
                } else results
                
                _exploreSmartSearchResults.value = filteredSmartResults
                _exploreSearchResults.value = filteredSmartResults.map { it.entity }
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

    private val _isAccessRestricted = MutableStateFlow(false)
    val isAccessRestricted: StateFlow<Boolean> = _isAccessRestricted.asStateFlow()

    private var detailJob: Job? = null
    private val vmDetailsCache = java.util.concurrent.ConcurrentHashMap<String, MediaEntity>()

    private var currentLoadingTmdbId: Int = -1
    private var currentLoadingType: String? = null

    fun loadMediaDetails(tmdbId: Int, type: String? = null) {
        if (tmdbId <= 0) return
        val normType = if (type.equals("tv", ignoreCase = true) || type.equals("serie", ignoreCase = true) || type.equals("series", ignoreCase = true)) "tv" else "movie"
        val cacheKey = "${normType}_$tmdbId"

        _isAccessRestricted.value = false

        // Avoid redundant loading if already loading the same media
        if (currentLoadingTmdbId == tmdbId && currentLoadingType == normType && detailJob?.isActive == true) {
            return
        }

        // 1. Instant Synchronous State Flow Emission (0ms latency!)
        val existing = vmDetailsCache[cacheKey]
            ?: allMedia.value.firstOrNull { it.tmdbId == tmdbId }

        if (existing != null) {
            val profile = firebaseService.activeProfile.value
            if (profile != null && profile.isKidsProfile && !com.example.util.ContentAccessManager.canProfileAccessContent(profile, existing)) {
                _isAccessRestricted.value = true
                _selectedMedia.value = null
                return
            }
            _selectedMedia.value = existing
            // If it's already cached, we might still want to refresh in background, 
            // but let's see if we need episodes
            if (existing.mediaType == "tv" && episodes.value.isEmpty()) {
                loadSeasonEpisodes(tmdbId, _selectedSeason.value)
            }
        } else {
            // Optimistic placeholder
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

        currentLoadingTmdbId = tmdbId
        currentLoadingType = normType

        _selectedMediaReleaseInfo.value = null
        releaseInfoJob?.cancel()

        // Trigger asynchronous non-blocking calendar release info lookup
        val initialMedia = existing
        if (initialMedia != null && initialMedia.title.isNotBlank()) {
            releaseInfoJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val relInfo = calendarRepository.findReleaseInfoForMedia(
                        tmdbId = tmdbId,
                        mediaType = normType,
                        title = initialMedia.title,
                        availableEpisodes = episodes.value,
                        releaseYear = initialMedia.releaseYear
                    )
                    _selectedMediaReleaseInfo.value = relInfo
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("MainViewModel", "Erro ao buscar dados do calendário: ${e.message}")
                }
            }
        }

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                val fullMedia = mediaRepository.getOrFetchMediaByTmdbId(tmdbId, normType)
                if (fullMedia != null) {
                    val profile = firebaseService.activeProfile.value
                    if (profile != null && profile.isKidsProfile && !com.example.util.ContentAccessManager.canProfileAccessContent(profile, fullMedia)) {
                        _isAccessRestricted.value = true
                        _selectedMedia.value = null
                        return@launch
                    }
                    vmDetailsCache[cacheKey] = fullMedia
                    _selectedMedia.value = fullMedia

                    if (fullMedia.mediaType == "tv") {
                        loadSeasonEpisodes(tmdbId, _selectedSeason.value)
                    }
                    fetchCastForMedia(tmdbId, fullMedia.mediaType, fullMedia.cast)

                    // Re-evaluate calendar release info with accurate title & metadata
                    releaseInfoJob?.cancel()
                    releaseInfoJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val relInfo = calendarRepository.findReleaseInfoForMedia(
                                tmdbId = tmdbId,
                                mediaType = normType,
                                title = fullMedia.title,
                                availableEpisodes = episodes.value,
                                releaseYear = fullMedia.releaseYear
                            )
                            _selectedMediaReleaseInfo.value = relInfo
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.e("MainViewModel", "Erro ao buscar dados do calendário: ${e.message}")
                        }
                    }
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
        val cacheKey = "${tmdbId}_$season"
        val oldSeason = _selectedSeason.value
        _selectedSeason.value = season
        
        // If switching season, clear current list to show loader or cached data immediately
        if (oldSeason != season) {
            _episodes.value = episodesCache[cacheKey] ?: emptyList()
        }

        // Avoid redundant loading if already loading this season
        if (episodesJob?.isActive == true && oldSeason == season && _episodes.value.isNotEmpty()) {
            return
        }

        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            // 1. Try Cache First
            val cached = episodesCache[cacheKey]
            if (cached != null) {
                _episodes.value = cached
                updateReleaseInfoFromEpisodes(tmdbId, cached)
            }

            // 2. Listen to local Room DB
            launch {
                mediaRepository.getEpisodesForSeason(tmdbId, season).collect { epList ->
                    if (epList.isNotEmpty()) {
                        episodesCache[cacheKey] = epList
                        _episodes.value = epList
                        updateReleaseInfoFromEpisodes(tmdbId, epList)
                    }
                }
            }

            // 3. Fetch/update in background
            try {
                mediaRepository.fetchAndStoreEpisodes(tmdbId, season)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Erro ao buscar episódios: ${e.message}")
            }
        }
    }

    private fun updateReleaseInfoFromEpisodes(tmdbId: Int, epList: List<EpisodeEntity>) {
        val currentMedia = _selectedMedia.value
        if (currentMedia != null && currentMedia.tmdbId == tmdbId) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val relInfo = calendarRepository.findReleaseInfoForMedia(
                        tmdbId = tmdbId,
                        mediaType = "tv",
                        title = currentMedia.title,
                        availableEpisodes = epList,
                        releaseYear = currentMedia.releaseYear
                    )
                    _selectedMediaReleaseInfo.value = relInfo
                } catch (_: Exception) {}
            }
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
        totalDurationMs: Long,
        currentTimeSeconds: Double = 0.0,
        durationSeconds: Double = 0.0
    ) {
        viewModelScope.launch {
            val profileId = firebaseService.activeProfile.value?.id ?: "default"
            mediaRepository.saveWatchProgress(
                profileId = profileId,
                tmdbId = tmdbId,
                mediaType = mediaType,
                title = title,
                posterPath = posterPath,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                progressPercent = progressPercent,
                positionMs = positionMs,
                totalDurationMs = totalDurationMs,
                currentTimeSeconds = currentTimeSeconds,
                durationSeconds = durationSeconds
            )
        }
    }

    suspend fun getWatchHistoryItemSync(
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): WatchHistoryEntity? {
        val profileId = firebaseService.activeProfile.value?.id ?: "default"
        return mediaRepository.getWatchHistoryItemByKey(
            tmdbId = tmdbId,
            profileId = profileId,
            mediaType = mediaType,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
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
