package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RemoteUpdateState {
    object Idle : RemoteUpdateState()
    object Checking : RemoteUpdateState()
    data class Applied(val remoteVersion: String, val cacheVersion: Int, val changelog: String, val activeRevision: String = "") : RemoteUpdateState()
    data class UpToDate(val remoteVersion: String, val activeRevision: String = "") : RemoteUpdateState()
    object Offline : RemoteUpdateState()
    data class Error(val message: String) : RemoteUpdateState()
}

class RemoteUpdateManager private constructor(private val appContext: Context) {

    private val TAG = "RemoteUpdateManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences = appContext.getSharedPreferences("ronycine_remote_update_prefs", Context.MODE_PRIVATE)
    private val firebaseService = FirebaseService.getInstance(appContext)

    // Player state flag - true when ExoPlayer / Video player is active
    val isPlayerActive = MutableStateFlow(false)

    // Current state of remote update manager
    private val _updateState = MutableStateFlow<RemoteUpdateState>(RemoteUpdateState.Idle)
    val updateState: StateFlow<RemoteUpdateState> = _updateState.asStateFlow()

    private val _updateEvents = MutableSharedFlow<RemoteUpdateState>()
    val updateEvents: SharedFlow<RemoteUpdateState> = _updateEvents.asSharedFlow()

    val remoteAppConfig: StateFlow<RemoteAppConfigEntity> = firebaseService.remoteAppConfig
    val remoteUpdateHistory: StateFlow<List<RemoteUpdateHistoryEntity>> = firebaseService.remoteUpdateHistory

    private var lastCheckTime = 0L
    private val checkThrottlingMs = 30_000L // Minimum 30s between automatic background checks

    init {
        // Start listening to Firestore remote config
        firebaseService.startListeningRemoteAppConfig()
        firebaseService.startListeningRemoteUpdateHistory()

        // Observe config changes in background
        scope.launch {
            firebaseService.remoteAppConfig.collect { config ->
                checkAndApplyRemoteConfig(config, isManualCheck = false)
            }
        }
    }

    fun getSavedRemoteVersion(): String = prefs.getString("last_applied_remote_version", "2026.09.07.01") ?: "2026.09.07.01"
    fun getSavedCacheVersion(): Int = prefs.getInt("last_applied_cache_version", 1)
    fun getSavedActiveRevision(): String = prefs.getString("last_applied_active_revision", "") ?: ""

    fun onForegroundReturn() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime > checkThrottlingMs) {
            lastCheckTime = now
            scope.launch {
                val currentConfig = firebaseService.remoteAppConfig.value
                checkAndApplyRemoteConfig(currentConfig, isManualCheck = false)
            }
        }
    }

    suspend fun checkNow(): RemoteUpdateState = withContext(Dispatchers.IO) {
        _updateState.value = RemoteUpdateState.Checking
        try {
            val freshConfig = firebaseService.fetchRemoteAppConfigDirectly()
            if (freshConfig != null) {
                val result = checkAndApplyRemoteConfig(freshConfig, isManualCheck = true)
                result
            } else {
                val offlineState = RemoteUpdateState.Offline
                _updateState.value = offlineState
                offlineState
            }
        } catch (e: Exception) {
            Log.e(TAG, "[REMOTE-UPDATE] Erro ao verificar atualização remota: ${e.message}")
            val errorState = RemoteUpdateState.Error(e.message ?: "Erro desconhecido")
            _updateState.value = errorState
            errorState
        }
    }

    private suspend fun checkAndApplyRemoteConfig(
        config: RemoteAppConfigEntity,
        isManualCheck: Boolean
    ): RemoteUpdateState {
        val savedVersion = getSavedRemoteVersion()
        val savedCacheVersion = getSavedCacheVersion()
        val savedRevision = getSavedActiveRevision()

        val isNewVersion = config.remoteVersion != savedVersion
        val isNewCache = config.cacheVersion != savedCacheVersion
        val isNewRevision = config.activeRevision.isNotBlank() && config.activeRevision != savedRevision
        
        val needsRefresh = isNewVersion || isNewCache || isNewRevision || config.forceRefresh

        Log.d(TAG, "[REMOTE-UPDATE] Config atual na nuvem: v=${config.remoteVersion}, cache=${config.cacheVersion}, revision=${config.activeRevision}, forceRefresh=${config.forceRefresh}. Local: v=$savedVersion, cache=$savedCacheVersion, revision=$savedRevision")

        if (!needsRefresh) {
            val upToDate = RemoteUpdateState.UpToDate(config.remoteVersion, config.activeRevision)
            _updateState.value = upToDate
            if (isManualCheck) {
                scope.launch { _updateEvents.emit(upToDate) }
            }
            logRemoteEvent("remote_update_skipped", config.remoteVersion, "Aplicativo já está na versão remota mais recente.")
            return upToDate
        }

        // Avoid interrupting video playback if user is in player!
        if (isPlayerActive.value && !isManualCheck) {
            Log.d(TAG, "[REMOTE-UPDATE] Reprodutor ativo! Adiando recarregamento de interface até o término do vídeo.")
            logRemoteEvent("remote_update_postponed", config.remoteVersion, "Player em reprodução.")
            return RemoteUpdateState.Idle
        }

        // Apply remote update logic
        try {
            // 1. Invalidate remote WebView / HTTP cache if cacheVersion changed
            if (isNewCache || config.forceRefresh) {
                withContext(Dispatchers.Main) {
                    try {
                        val tempWebView = WebView(appContext)
                        tempWebView.clearCache(true)
                        com.example.util.WebViewUtils.safeDestroy(tempWebView)
                        Log.d(TAG, "[REMOTE-UPDATE] Cache do WebView/HTTP limpo com sucesso.")
                    } catch (e: Exception) {
                        Log.w(TAG, "[REMOTE-UPDATE] Aviso ao limpar WebView cache: ${e.message}")
                    }
                }
            }

            // 2. Save new applied version state
            prefs.edit()
                .putString("last_applied_remote_version", config.remoteVersion)
                .putInt("last_applied_cache_version", config.cacheVersion)
                .putString("last_applied_active_revision", config.activeRevision)
                .putInt("last_applied_build", config.build)
                .putLong("last_applied_timestamp", System.currentTimeMillis())
                .apply()

            val appliedState = RemoteUpdateState.Applied(
                remoteVersion = config.remoteVersion,
                cacheVersion = config.cacheVersion,
                changelog = config.changelog,
                activeRevision = config.activeRevision
            )

            _updateState.value = appliedState
            scope.launch { 
                _updateEvents.emit(appliedState) 
                // Confirmar aplicação no Firestore para rastreamento administrativo
                firebaseService.confirmAppliedRevision(config.activeRevision, config.remoteVersion)
            }

            logRemoteEvent(
                "remote_update_applied",
                config.remoteVersion,
                "Versão remota v${config.remoteVersion} (Cache ${config.cacheVersion}) aplicada com sucesso."
            )

            return appliedState

        } catch (e: Exception) {
            Log.e(TAG, "[REMOTE-UPDATE] Falha ao aplicar atualização remota: ${e.message}")
            val errorState = RemoteUpdateState.Error("Falha na atualização: ${e.message}")
            _updateState.value = errorState
            logRemoteEvent("remote_update_failed", config.remoteVersion, e.message ?: "Erro ao aplicar")
            return errorState
        }
    }

    private fun logRemoteEvent(event: String, version: String, details: String) {
        scope.launch {
            try {
                firebaseService.logRemoteUpdateEvent(event, version, details)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao registrar log de atualização remota: ${e.message}")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: RemoteUpdateManager? = null

        fun getInstance(context: Context): RemoteUpdateManager {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: RemoteUpdateManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
