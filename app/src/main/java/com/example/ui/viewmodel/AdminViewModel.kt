package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.FeaturedMediaEntity
import com.example.data.local.FeaturedMediaItem
import com.example.data.local.MediaEntity
import com.example.data.local.NotificationEntity
import com.example.data.remote.ImportConfig
import com.example.data.remote.ImportItem
import com.example.data.remote.ImportJob
import com.example.data.remote.ImportSummary
import com.example.data.remote.MegaEmbedConfig
import com.example.data.remote.MegaEmbedApiItem
import com.example.data.remote.MegaEmbedService
import com.example.data.remote.AppVersionEntity
import com.example.data.repository.MediaRepository
import com.example.data.repository.MegaEmbedSyncResult
import com.example.data.repository.TmdbSearchResultItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import com.example.data.local.AppSettingsEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.TmdbAutoSyncHistoryEntity
import com.example.data.remote.BackupData
import com.example.data.remote.BackupManager
import com.example.data.remote.BackupPreviewInfo
import com.example.data.remote.RestoreRealProgress
import com.example.data.remote.RestoreStatus
import com.example.data.repository.TmdbAutoSyncConfig
import com.example.data.repository.TmdbAutoSyncProgress
import android.net.Uri

enum class AdminSection(val title: String, val iconName: String) {
    ESTATISTICAS("Visão Geral", "Dashboard"),
    CATALOGO("Catálogo", "Movie"),
    IMPORTACAO("Importar Filmes & Séries", "Download"),
    IMPORTACAO_MASSA("Importação em Massa", "CloudSync"),
    TOP_10("TOP 10", "Whatshot"),
    DESTAQUES("Conteúdo em Destaque", "Star"),
    TV_AO_VIVO("TV ao Vivo", "LiveTv"),
    SINCRONIZACAO_AUTOMATICA("Sincronização Automática", "AutoMode"),
    USUARIOS("Usuários", "Group"),
    PERFIS("Perfis", "AccountCircle"),
    DISPOSITIVOS("Dispositivos", "Smartphone"),
    ADMINISTRADORES("Administradores", "AdminPanelSettings"),
    NOTIFICACOES("Notificações", "Notifications"),
    PEDIDOS("Pedidos de Filmes e Séries", "AddBox"),
    ATUALIZACOES_APP("Versões do Aplicativo", "Update"),
    ATUALIZACOES("Atualizações", "CloudSync"),
    CONTROLE_REMOTO("Controle por Dispositivo", "SettingsRemote"),
    ALTERACOES_PENDENTES("Alterações Pendentes", "PendingActions"),
    SINCRONIZACAO("Status da Sincronização", "Sync"),
    PLAYERS("Gerenciador de Players", "PlayCircleOutline"),
    CONFIGURACOES("Configurações", "Settings"),
    CINE_CONFIG("Configurações do Cine", "SmartToy"),
    LOGS("Logs do Sistema", "History"),
    HISTORICO("Histórico", "ManageHistory"),
    STATUS_SISTEMA("Status do Sistema", "CheckCircle")
}

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val firebaseService = com.example.data.remote.FirebaseService.getInstance(application)
    val mediaRepository = MediaRepository(database.playFilmeDao(), firebaseService)

    // Auth state - supports custom administrator password (fallback is "200419")
    private val _isAdminLoggedIn = MutableStateFlow(false)
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()
    
    private var cachedAdminPassword = "200419"

    private val _shareAppConfig = MutableStateFlow(MediaRepository.ShareAppConfigData())
    val shareAppConfig: StateFlow<MediaRepository.ShareAppConfigData> = _shareAppConfig.asStateFlow()

    // --- Users & Profiles State ---
    val allUsers: StateFlow<List<com.example.data.remote.UserEntity>> = firebaseService.allUsers
    val allGlobalProfiles: StateFlow<List<com.example.data.remote.UserProfile>> = firebaseService.allGlobalProfiles

    fun updateUserStatus(userId: String, status: String) {
        viewModelScope.launch {
            firebaseService.updateUserStatus(userId, status)
        }
    }

    fun setUserRole(userId: String, role: String) {
        viewModelScope.launch {
            firebaseService.updateUserRole(userId, role)
        }
    }

    fun deleteUserProfile(userId: String, profileId: String) {
        viewModelScope.launch {
            firebaseService.adminDeleteProfile(userId, profileId)
        }
    }

    fun toggleProfileVerification(userId: String, profileId: String, currentVerified: Boolean, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val targetState = !currentVerified
            val res = firebaseService.setProfileVerification(userId, profileId, targetState)
            if (res.isSuccess) {
                val actionLabel = if (targetState) "Verificação de Perfil (Ativado)" else "Verificação de Perfil (Removido)"
                addAuditLog(actionLabel, "Perfil ID: $profileId para Usuário: $userId")
                
                // Força refresh dos perfis para garantir sincronização imediata
                firebaseService.startListeningAllUsers()
                
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Não foi possível confirmar a verificação no Firebase."
                onResult(false, err)
            }
        }
    }

    fun toggleUserVerification(userId: String, currentVerified: Boolean, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val targetState = !currentVerified
            val res = firebaseService.setUserVerification(userId, targetState)
            if (res.isSuccess) {
                val actionLabel = if (targetState) "Verificação de Conta (Ativado)" else "Verificação de Conta (Removido)"
                addAuditLog(actionLabel, "Usuário ID: $userId")
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Não foi possível confirmar a verificação da conta no Firebase."
                onResult(false, err)
            }
        }
    }

    init {
        viewModelScope.launch {
            firebaseService.currentUser.collect { user ->
                if (user?.role == "FOUNDER" || user?.role == "ADMIN") {
                    _isAdminLoggedIn.value = true
                }
            }
        }
        firebaseService.startListeningAllUsers()
        firebaseService.startListeningAuditLogs()
        firebaseService.startPlayerSourcesListener()
        seedDefaultPlayers()
    }

    private val _tmdbAutoSyncConfig = MutableStateFlow(TmdbAutoSyncConfig())
    val tmdbAutoSyncConfig: StateFlow<TmdbAutoSyncConfig> = _tmdbAutoSyncConfig.asStateFlow()

    fun verifyAdminPassword(password: String): Boolean {
        val isValid = password.trim() == cachedAdminPassword
        if (isValid) {
            _isAdminLoggedIn.value = true
        }
        return isValid
    }

    suspend fun updateAdminPassword(newPassword: String): Boolean {
        return try {
            database.playFilmeDao().saveSetting(AppSettingsEntity("admin_password", newPassword.trim()))
            cachedAdminPassword = newPassword.trim()
            addAuditLog("Alterou a senha administrativa", "Configurações")
            true
        } catch (e: Exception) {
            android.util.Log.e("AdminViewModel", "Error saving admin password: ${e.message}")
            false
        }
    }

    fun toggleMediaRestricted18(media: MediaEntity) {
        viewModelScope.launch {
            val newRestricted = !media.restricted18
            val updated = media.copy(
                restricted18 = newRestricted,
                restricted18UpdatedAt = System.currentTimeMillis(),
                restricted18UpdatedBy = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            )
            
            // Update in Room first for immediate feedback
            database.playFilmeDao().insertMedia(updated)
            
            // Then sync to Cloud
            firebaseService.upsertMediaInCloud(updated)
            
            val action = if (newRestricted) "MARCOU CONTEÚDO COMO +18" else "REMOVEU RESTRIÇÃO +18"
            addAuditLog("$action: ${media.title}", "Catálogo")
        }
    }

    fun logoutAdmin() {
        _isAdminLoggedIn.value = false
        _currentSection.value = AdminSection.IMPORTACAO
    }

    // --- AUTOMATIC NOTIFICATIONS CONFIGURATION ---
    private val _autoNotificationsEnabled = MutableStateFlow(true)
    val autoNotificationsEnabled: StateFlow<Boolean> = _autoNotificationsEnabled.asStateFlow()

    init {
        loadAutoNotificationsConfig()
    }

    // --- Audit Logs ---
    val allAuditLogs: StateFlow<List<AuditLogEntity>> = database.playFilmeDao().getAllAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAuditLog(action: String, target: String) {
        viewModelScope.launch {
            try {
                val log = AuditLogEntity(
                    user = "Ronaldo Mazive",
                    action = action,
                    target = target
                )
                database.playFilmeDao().insertAuditLog(log)
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Error saving audit log: ${e.message}")
            }
        }
    }

    fun clearAllAuditLogs() {
        viewModelScope.launch {
            try {
                database.playFilmeDao().clearAllAuditLogs()
                addAuditLog("Limpou os logs", "Todos os logs do sistema")
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Error clearing audit logs: ${e.message}")
            }
        }
    }

    // --- PLAYER MANAGEMENT ---
    val playerSources: StateFlow<List<com.example.data.remote.PlayerSource>> = firebaseService.playerSources
    val playerConfig: StateFlow<com.example.data.remote.PlayerConfig> = firebaseService.playerConfig

    private val _isSettingDefaultPlayer = MutableStateFlow<String?>(null) // playerId
    val isSettingDefaultPlayer = _isSettingDefaultPlayer.asStateFlow()

    private val _isSavingPlayerConfig = MutableStateFlow(false)
    val isSavingPlayerConfig: StateFlow<Boolean> = _isSavingPlayerConfig.asStateFlow()

    private val _playerSaveStatusMessage = MutableStateFlow<String?>(null)
    val playerSaveStatusMessage: StateFlow<String?> = _playerSaveStatusMessage.asStateFlow()

    fun clearPlayerStatusMessage() {
        _playerSaveStatusMessage.value = null
    }

    fun saveMegaEmbedSettings(
        player: String,
        color: String,
        enabled: Boolean = true,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (_isSavingPlayerConfig.value) return@launch
            _isSavingPlayerConfig.value = true
            _playerSaveStatusMessage.value = null

            val cleanColor = com.example.data.remote.MegaEmbedPlayerType.normalizeColor(color)
            val cleanPlayer = player.trim().lowercase()

            val currentAdminEmail = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            val newMegaConfig = com.example.data.remote.MegaEmbedPlayerConfig(
                enabled = enabled,
                player = cleanPlayer,
                color = cleanColor
            )

            val success = firebaseService.updateMegaEmbedConfig(newMegaConfig, currentAdminEmail)

            if (success) {
                // Update local repository and StateFlow
                val repoConfig = mediaRepository.getMegaEmbedConfig().copy(
                    defaultPlayer = cleanPlayer,
                    colorHex = cleanColor
                )
                mediaRepository.saveMegaEmbedConfig(repoConfig)
                _megaEmbedConfig.value = repoConfig

                val displayName = com.example.data.remote.MegaEmbedPlayerType.getDisplayName(cleanPlayer)
                addAuditLog("Atualizou MegaEmbed: Player=$displayName, Cor=#$cleanColor, Ativo=$enabled", "Gerenciador de Players")
                firebaseService.addAuditLogRemote("MEGAEMBED_CONFIG_UPDATED", cleanPlayer)

                _playerSaveStatusMessage.value = "Player atualizado com sucesso"
                onResult(true)
            } else {
                _playerSaveStatusMessage.value = "Falha ao salvar configuração no Firestore"
                onResult(false)
            }
            _isSavingPlayerConfig.value = false
        }
    }

    fun saveSubtitledSettings(
        provider: String = "vidsrc",
        defaultLanguage: String = "pt",
        enabled: Boolean = true,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (_isSavingPlayerConfig.value) return@launch
            _isSavingPlayerConfig.value = true
            _playerSaveStatusMessage.value = null

            val currentAdminEmail = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            val newSubtitledConfig = com.example.data.remote.SubtitledPlayerConfig(
                provider = provider.trim().lowercase(),
                defaultLanguage = defaultLanguage.trim(),
                enabled = enabled
            )

            val success = firebaseService.updateSubtitledPlayerConfig(newSubtitledConfig, currentAdminEmail)

            if (success) {
                addAuditLog("Atualizou Player Legendado: Provedor=$provider, Idioma=$defaultLanguage, Ativo=$enabled", "Gerenciador de Players")
                firebaseService.addAuditLogRemote("SUBTITLED_CONFIG_UPDATED", provider)

                _playerSaveStatusMessage.value = "Player legendado salvo com sucesso"
                onResult(true)
            } else {
                _playerSaveStatusMessage.value = "Falha ao salvar configuração no Firestore"
                onResult(false)
            }
            _isSavingPlayerConfig.value = false
        }
    }

    fun savePlayerSource(source: com.example.data.remote.PlayerSource) {
        viewModelScope.launch {
            firebaseService.upsertPlayerSourceInCloud(source)
            addAuditLog("Salvou player: ${source.name}", "Gerenciador de Players")
            firebaseService.addAuditLogRemote("PLAYER_UPDATED", source.id)
        }
    }

    fun deletePlayerSource(id: String) {
        viewModelScope.launch {
            firebaseService.deletePlayerSourceFromCloud(id)
            addAuditLog("Excluiu player (ID: $id)", "Gerenciador de Players")
            firebaseService.addAuditLogRemote("PLAYER_DELETED", id)
        }
    }

    fun setDefaultPlayer(playerId: String) {
        viewModelScope.launch {
            if (_isSettingDefaultPlayer.value != null) return@launch
            _isSettingDefaultPlayer.value = playerId
            try {
                firebaseService.setDefaultPlayer(playerId)
                
                // Allow some time for Firestore sync and local observation
                delay(1000)
                
                addAuditLog("Definiu player como principal: $playerId", "Gerenciador de Players")
                firebaseService.addAuditLogRemote("PLAYER_SET_DEFAULT", playerId)
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error setting default player: ${e.message}")
            } finally {
                _isSettingDefaultPlayer.value = null
            }
        }
    }

    fun togglePlayerEnabled(playerId: String, enabled: Boolean) {
        viewModelScope.launch {
            firebaseService.togglePlayerEnabled(playerId, enabled)
            val action = if (enabled) "Ativou" else "Desativou"
            addAuditLog("$action player: $playerId", "Gerenciador de Players")
            firebaseService.addAuditLogRemote(if (enabled) "PLAYER_ENABLED" else "PLAYER_DISABLED", playerId)
        }
    }

    fun duplicatePlayerSource(source: com.example.data.remote.PlayerSource) {
        viewModelScope.launch {
            val newSource = source.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${source.name} (Cópia)",
                isDefault = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            firebaseService.upsertPlayerSourceInCloud(newSource)
            addAuditLog("Duplicou player: ${source.name}", "Gerenciador de Players")
            firebaseService.addAuditLogRemote("PLAYER_DUPLICATED", newSource.id)
        }
    }

    fun updatePlayerConfig(config: com.example.data.remote.PlayerConfig) {
        viewModelScope.launch {
            firebaseService.updatePlayerConfig(config)
            addAuditLog("Atualizou configurações globais de players", "Gerenciador de Players")
        }
    }

    fun seedDefaultPlayers() {
        viewModelScope.launch {
            // Give it time for the initial listener snapshot
            delay(1200)
            val currentSources = playerSources.value
            val mgebExists = currentSources.any { it.id == "mgeb" || it.name.contains("mgeb", ignoreCase = true) || it.name.contains("megaembed", ignoreCase = true) }
            val vidsrcExists = currentSources.any { it.id == "vidsrc" || it.name.contains("vidsrc", ignoreCase = true) }

            if (!mgebExists) {
                val mgeb = com.example.data.remote.PlayerSource(
                    id = "mgeb",
                    name = "MegaEmbed",
                    type = "Embed",
                    priority = 1,
                    language = "Dublado",
                    movieTmdbUrl = "https://mgeb.top/embed/{tmdb_id}",
                    tvTmdbUrl = "https://mgeb.top/embed/{tmdb_id}/{season_number}/{episode_number}",
                    playerColor = "#fb542b",
                    isDefault = true,
                    enabled = true
                )
                firebaseService.upsertPlayerSourceInCloud(mgeb)
            }

            if (!vidsrcExists) {
                val vidsrc = com.example.data.remote.PlayerSource(
                    id = "vidsrc",
                    name = "VidSrc",
                    type = "Embed",
                    priority = 2,
                    language = "Legendado",
                    movieTmdbUrl = "https://vidsrc.tw/embed/movie/{tmdb_id}",
                    tvTmdbUrl = "https://vidsrc.tw/embed/tv/{tmdb_id}/{season_number}/{episode_number}",
                    isDefault = false,
                    enabled = true
                )
                firebaseService.upsertPlayerSourceInCloud(vidsrc)
            }

            // Consolidate duplicate VidSrc entries if any
            val vidsrcSources = playerSources.value.filter { it.id == "vidsrc" || it.name.equals("VidSrc", ignoreCase = true) }
            if (vidsrcSources.size > 1) {
                vidsrcSources.drop(1).forEach { dup ->
                    if (dup.id.isNotEmpty() && dup.id != "vidsrc") {
                        firebaseService.deletePlayerSourceFromCloud(dup.id)
                    }
                }
            }

            // If players exist but none is default, set Mgeb as default if it exists
            val hasDefault = playerSources.value.any { it.isDefault }
            if (!hasDefault && playerSources.value.isNotEmpty()) {
                val mgeb = playerSources.value.find { it.id == "mgeb" || it.name.contains("mgeb", ignoreCase = true) }
                    ?: playerSources.value.firstOrNull()
                if (mgeb != null) {
                    firebaseService.setDefaultPlayer(mgeb.id)
                }
            }
        }
    }

    // --- Device Management & Remote Control ---
    val isAdminAuthorized: StateFlow<Boolean> = firebaseService.isAdminAuthorized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allDevices: StateFlow<List<com.example.data.remote.DeviceEntity>> = firebaseService.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remoteConfig: StateFlow<com.example.data.remote.RemoteConfigEntity> = firebaseService.remoteConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.RemoteConfigEntity())

    val remoteAppConfig: StateFlow<com.example.data.remote.RemoteAppConfigEntity> = firebaseService.remoteAppConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.RemoteAppConfigEntity())

    val remoteUpdateHistory: StateFlow<List<com.example.data.remote.RemoteUpdateHistoryEntity>> = firebaseService.remoteUpdateHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val remoteUpdateManager = com.example.data.remote.RemoteUpdateManager.getInstance(application)

    fun publishRemoteUpdate(
        changelog: String,
        forceRefresh: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentConfig = remoteAppConfig.value
                val sdf = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US)
                val todayPrefix = sdf.format(java.util.Date())

                val newRemoteVersion = if (currentConfig.remoteVersion.startsWith(todayPrefix)) {
                    val seq = currentConfig.remoteVersion.substringAfterLast(".").toIntOrNull() ?: 0
                    String.format(java.util.Locale.US, "%s.%02d", todayPrefix, seq + 1)
                } else {
                    "$todayPrefix.01"
                }

                val newBuild = currentConfig.build + 1
                val newCacheVersion = if (forceRefresh) currentConfig.cacheVersion + 1 else currentConfig.cacheVersion

                val success = firebaseService.publishRemoteUpdateInCloud(
                    remoteVersion = newRemoteVersion,
                    build = newBuild,
                    cacheVersion = newCacheVersion,
                    changelog = changelog,
                    forceRefresh = forceRefresh,
                    publishedBy = "Ronaldo Mazive (Admin)"
                )

                if (success) {
                    addAuditLog("Publicou Alterações Remotas", "Versão $newRemoteVersion")
                    onResult(true, "✓ ALTERAÇÕES PUBLICADAS!\nVersão remota: $newRemoteVersion")
                } else {
                    onResult(false, "Falha ao publicar alteração no servidor.")
                }
            } catch (e: Exception) {
                onResult(false, "Erro ao publicar: ${e.message}")
            }
        }
    }

    fun checkRemoteUpdateNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val state = remoteUpdateManager.checkNow()
            val msg = when (state) {
                is com.example.data.remote.RemoteUpdateState.UpToDate -> "✓ Você já está na versão remota mais recente (${state.remoteVersion})."
                is com.example.data.remote.RemoteUpdateState.Applied -> "✓ Versão remota v${state.remoteVersion} sincronizada!"
                is com.example.data.remote.RemoteUpdateState.Offline -> "🟡 Dispositivo offline. Mantendo cache local."
                is com.example.data.remote.RemoteUpdateState.Error -> "🔴 Erro: ${state.message}"
                else -> "Sincronização concluída."
            }
            onResult(msg)
        }
    }

    val updateControl: StateFlow<com.example.data.remote.UpdateControlEntity> = firebaseService.updateControl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.remote.UpdateControlEntity(enabled = false))

    val publishedVersions: StateFlow<List<com.example.data.remote.AppVersionEntity>> = firebaseService.publishedVersions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val updateEvents: StateFlow<List<com.example.data.remote.UpdateEventEntity>> = firebaseService.updateEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingChanges: StateFlow<List<com.example.data.remote.PendingChangeEntity>> = firebaseService.pendingChanges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val releaseVersions: StateFlow<List<com.example.data.remote.ReleaseRevisionEntity>> = firebaseService.releaseVersions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicationEvents: StateFlow<List<com.example.data.remote.PublicationEventEntity>> = firebaseService.publicationEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun detectRealChanges(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = firebaseService.detectRealChangesToday()
            onResult(count)
        }
    }

    fun createPendingChange(change: com.example.data.remote.PendingChangeEntity) {
        viewModelScope.launch {
            firebaseService.createPendingChangeInCloud(change)
        }
    }

    fun updatePendingChangeStatus(changeId: String, newStatus: String) {
        viewModelScope.launch {
            val adminId = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            firebaseService.updatePendingChangeStatusInCloud(changeId, newStatus, adminId)
        }
    }

    fun publishSelectedChanges(
        title: String,
        description: String,
        selectedIds: List<String>,
        releaseType: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val adminId = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            val result = firebaseService.publishSelectedChangesInCloud(
                title = title,
                description = description,
                selectedChangeIds = selectedIds,
                releaseType = releaseType,
                adminId = adminId
            )
            if (result.isSuccess) {
                onResult(true, "✓ Revisão ${result.getOrNull()} publicada com sucesso!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Erro desconhecido ao publicar.")
            }
        }
    }

    fun revertToRevision(revisionId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val adminId = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            val result = firebaseService.revertToRevision(revisionId, adminId)
            if (result.isSuccess) {
                onResult(true, "✓ Reversão concluída com sucesso.")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Erro ao reverter.")
            }
        }
    }

    val remoteAuditLogs: StateFlow<List<com.example.data.remote.AdminAuditLogEntity>> = firebaseService.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Top 10 Management ---
    val top10Config: StateFlow<com.example.data.remote.Top10ConfigEntity> = firebaseService.top10Config

    private val _top10SearchQuery = MutableStateFlow("")
    val top10SearchQuery = _top10SearchQuery.asStateFlow()

    private val _top10SearchResults = MutableStateFlow<List<MediaEntity>>(emptyList())
    val top10SearchResults = _top10SearchResults.asStateFlow()

    private val _isGeneratingTop10 = MutableStateFlow(false)
    val isGeneratingTop10 = _isGeneratingTop10.asStateFlow()

    private val _top10GeneratingMessage = MutableStateFlow("")
    val top10GeneratingMessage = _top10GeneratingMessage.asStateFlow()

    fun searchCatalogForTop10(query: String) {
        _top10SearchQuery.value = query
        if (query.trim().length < 2) {
            _top10SearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            database.playFilmeDao().searchLocalMedia(query).collect { results ->
                _top10SearchResults.value = results
            }
        }
    }

    fun saveTop10Config(config: com.example.data.remote.Top10ConfigEntity) {
        viewModelScope.launch {
            firebaseService.updateTop10ConfigInCloud(config)
        }
    }

    fun generateTop10Automatic(onlyInCatalog: Boolean = true) {
        viewModelScope.launch {
            _isGeneratingTop10.value = true
            _top10GeneratingMessage.value = "Buscando conteúdos..."
            try {
                val tmdbApi = com.example.data.remote.TmdbNetwork.apiService
                val apiKey = com.example.BuildConfig.TMDB_API_KEY
                
                val popularMovies = tmdbApi.getPopularMovies(apiKey).results
                val popularSeries = tmdbApi.getPopularSeries(apiKey).results
                
                _top10GeneratingMessage.value = "Analisando resultados..."
                delay(800)
                
                val catalog = database.playFilmeDao().getAllMediaSync()
                
                _top10GeneratingMessage.value = "Verificando catálogo..."
                delay(800)

                val candidates = (popularMovies + popularSeries)
                    .map { dto ->
                        val type = if (dto.title != null) "movie" else "tv"
                        com.example.data.remote.Top10ItemEntity(
                            tmdbId = dto.id,
                            mediaType = type,
                            title = dto.title ?: dto.name ?: "Sem título",
                            posterPath = dto.posterPath
                        )
                    }

                val filteredItems = if (onlyInCatalog) {
                    candidates.filter { cand ->
                        catalog.any { cat -> cat.tmdbId == cand.tmdbId && cat.mediaType == cand.mediaType }
                    }
                } else {
                    candidates
                }

                _top10GeneratingMessage.value = "Montando TOP 10..."
                delay(800)

                val top10 = filteredItems.take(10).mapIndexed { index, item ->
                    item.copy(rank = index + 1)
                }

                if (top10.isNotEmpty()) {
                    _top10GeneratingMessage.value = "Salvando..."
                    val current = top10Config.value
                    val newConfig = current.copy(
                        mode = "automatic",
                        items = top10,
                        lastGeneratedAt = System.currentTimeMillis(),
                        source = "tmdb",
                        version = current.version + 1
                    )
                    firebaseService.updateTop10ConfigInCloud(newConfig)
                    delay(500)
                    _top10GeneratingMessage.value = "Concluído."
                } else {
                    _top10GeneratingMessage.value = "Nenhum conteúdo do catálogo encontrado nas tendências."
                }
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Error generating top 10: ${e.message}")
                _top10GeneratingMessage.value = "Erro ao atualizar: ${e.localizedMessage}"
            } finally {
                delay(2000)
                _isGeneratingTop10.value = false
                _top10GeneratingMessage.value = ""
            }
        }
    }

    fun updateSelectedDevices(deviceIds: Set<String>, version: AppVersionEntity) {
        viewModelScope.launch {
            val allDevs = allDevices.value
            // 1. Filtrar dispositivos elegíveis (excluir desativados, suspensos, banidos, bloqueados e já atualizados)
            val eligibleDevs = allDevs.filter { dev ->
                dev.deviceId in deviceIds &&
                !dev.isBlocked &&
                dev.accessStatus == "ACTIVE" &&
                dev.buildNumber < version.versionCode
            }
            if (eligibleDevs.isEmpty()) {
                Log.d("AdminViewModel", "Nenhum dispositivo elegível encontrado para atualização.")
                return@launch
            }

            val operationId = "op_global_${version.versionCode}_${System.currentTimeMillis()}"
            
            // 2. Criar operação global em updateOperations
            firebaseService.recordUpdateOperationInCloud(
                operationId = operationId,
                type = "GLOBAL",
                targetVersionCode = version.versionCode,
                targetVersionName = version.versionName,
                totalDevices = eligibleDevs.size
            )

            // 3. Criar eventos individuais com idempotência
            eligibleDevs.forEach { dev ->
                val deviceId = dev.deviceId
                val devName = dev.name.ifBlank { dev.model }.ifBlank { "Dispositivo $deviceId" }
                val eventId = "evt_${deviceId}_${version.versionCode}" // Idempotent unique key (deviceId_targetVersionCode)

                firebaseService.recordUpdateEventInCloud(
                    com.example.data.remote.UpdateEventEntity(
                        id = eventId,
                        deviceId = deviceId,
                        deviceName = devName,
                        fromVersion = dev.appVersion ?: "desconhecida",
                        toVersion = version.versionName,
                        status = if (dev.status == "online") "NOTIFIED" else "PENDING",
                        startedAt = System.currentTimeMillis(),
                        trigger = "admin",
                        adminId = "admin@ronycine.app"
                    )
                )

                // Envia comando/notificação via FCM
                firebaseService.sendNotificationEventToCloud(
                    id = "update_${deviceId}_${version.versionCode}_${System.currentTimeMillis()}",
                    title = "Atualização Disponível",
                    message = "Uma nova versão ${version.versionName} está disponível.",
                    imageUrl = null,
                    type = "APP_UPDATE",
                    actionUrl = version.apkUrl,
                    targetSegment = deviceId,
                    buttonText = "Atualizar"
                )
            }
            addAuditLog("DISPAROU ATUALIZAÇÃO EM MASSA", "Versão ${version.versionName} (Build ${version.versionCode}) para ${eligibleDevs.size} aparelhos")
        }
    }

    fun updateAllCompatibleDevices(version: AppVersionEntity) {
        viewModelScope.launch {
            val allDevs = allDevices.value
            val compatibleIds = allDevs.filter { dev ->
                !dev.isBlocked &&
                dev.accessStatus == "ACTIVE" &&
                dev.buildNumber < version.versionCode
            }.map { it.deviceId }.toSet()
            if (compatibleIds.isNotEmpty()) {
                updateSelectedDevices(compatibleIds, version)
            }
        }
    }

    fun cancelDeviceUpdate(deviceId: String) {
        viewModelScope.launch {
            firebaseService.cancelDeviceUpdateInCloud(deviceId)
            addAuditLog("Cancelou atualização do dispositivo", deviceId)
        }
    }

    fun sendUpdateNoticeToDevice(deviceId: String, version: AppVersionEntity) {
        viewModelScope.launch {
            firebaseService.sendNotificationEventToCloud(
                id = "notice_${deviceId}_${System.currentTimeMillis()}",
                title = "Aviso de Atualização - RONYCINE",
                message = "A versão v${version.versionName} está disponível para seu dispositivo. Atualize agora para continuar aproveitando os novos recursos!",
                imageUrl = null,
                type = "APP_UPDATE",
                actionUrl = version.apkUrl,
                targetSegment = deviceId,
                buttonText = "Ver Atualização"
            )
            addAuditLog("Enviou aviso de atualização para dispositivo", deviceId)
        }
    }

    fun setAppVersionStatus(versionId: String, newStatus: String) {
        viewModelScope.launch {
            firebaseService.setAppVersionStatusInCloud(versionId, newStatus)
        }
    }

    fun refreshDevices() {
        firebaseService.startListeningDevices()
    }

    fun setDeviceBlockedStatus(deviceId: String, blocked: Boolean) {
        viewModelScope.launch {
            firebaseService.setDeviceBlocked(deviceId, blocked)
            addAuditLog(if (blocked) "Bloqueou dispositivo" else "Desbloqueou dispositivo", deviceId)
        }
    }

    fun setDeviceAdminAccess(targetDeviceId: String, adminAccess: Boolean) {
        viewModelScope.launch {
            firebaseService.setDeviceAdminAccess(targetDeviceId, adminAccess)
            val action = if (adminAccess) "Concedeu acesso de administrador" else "Revogou acesso de administrador"
            addAuditLog(action, "Dispositivo $targetDeviceId")
        }
    }

    fun setDeviceAccessStatus(targetDeviceId: String, accessStatus: String) {
        viewModelScope.launch {
            firebaseService.setDeviceAccessStatus(targetDeviceId, accessStatus)
            addAuditLog("Alterou status de acesso para $accessStatus", "Dispositivo $targetDeviceId")
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            firebaseService.removeDevice(deviceId)
            addAuditLog("Removeu dispositivo", deviceId)
        }
    }

    fun requestDeviceSync(deviceId: String) {
        viewModelScope.launch {
            firebaseService.requestDeviceSync(deviceId)
        }
    }

    fun updateRemoteConfig(config: com.example.data.remote.RemoteConfigEntity) {
        viewModelScope.launch {
            firebaseService.updateRemoteConfigInCloud(config)
            firebaseService.addAuditLog("ALTEROU CONFIGURAÇÃO REMOTA", "Modo Manutenção: ${config.maintenanceMode}")
        }
    }

    fun publishAppVersion(version: com.example.data.remote.AppVersionEntity) {
        viewModelScope.launch {
            firebaseService.publishAppVersionInCloud(version)
        }
    }

    suspend fun uploadApk(versionName: String, file: java.io.File): Result<String> {
        return firebaseService.uploadApkToStorage(versionName, file)
    }

    fun activateAppUpdate(
        version: com.example.data.remote.AppVersionEntity,
        mandatory: Boolean,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = firebaseService.activateAppUpdateInCloud(version, mandatory, "admin@ronycine.app")
            if (result.isSuccess) {
                onResult?.invoke(true, "Atualização para a versão ${version.versionName} ativada com sucesso.")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Erro desconhecido ao ativar atualização."
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun deactivateAppUpdate(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = firebaseService.deactivateAppUpdateInCloud("admin@ronycine.app")
            if (result.isSuccess) {
                onResult?.invoke(true, "Atualizações desativadas globalmente com sucesso.")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Erro desconhecido ao desativar atualização."
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun deleteAppVersion(versionId: String) {
        viewModelScope.launch {
            firebaseService.deleteAppVersionInCloud(versionId)
        }
    }

    // --- MGEB (MegaEmbed) Import States ---
    private val _mgebMovies = MutableStateFlow<List<MegaEmbedApiItem>>(emptyList())
    val mgebMovies: StateFlow<List<MegaEmbedApiItem>> = _mgebMovies.asStateFlow()

    private val _mgebSeries = MutableStateFlow<List<MegaEmbedApiItem>>(emptyList())
    val mgebSeries: StateFlow<List<MegaEmbedApiItem>> = _mgebSeries.asStateFlow()

    private val _isMgebLoading = MutableStateFlow(false)
    val isMgebLoading: StateFlow<Boolean> = _isMgebLoading.asStateFlow()

    private val _mgebSearchQuery = MutableStateFlow("")
    val mgebSearchQuery: StateFlow<String> = _mgebSearchQuery.asStateFlow()

    private val _mgebCandidates = MutableStateFlow<List<TmdbSearchResultItem>>(emptyList())
    val mgebCandidates: StateFlow<List<TmdbSearchResultItem>> = _mgebCandidates.asStateFlow()

    // Advanced selection & pagination states
    private val mgebEnrichedCache = mutableMapOf<Pair<Int, String>, MediaEntity>()

    private val _mgebPage = MutableStateFlow(1)
    val mgebPage: StateFlow<Int> = _mgebPage.asStateFlow()

    private val _mgebTotalPages = MutableStateFlow(1)
    val mgebTotalPages: StateFlow<Int> = _mgebTotalPages.asStateFlow()

    private val _mgebTotalCount = MutableStateFlow(0)
    val mgebTotalCount: StateFlow<Int> = _mgebTotalCount.asStateFlow()

    private val _mgebTypeFilter = MutableStateFlow("ALL") // "ALL", "movie", "tv"
    val mgebTypeFilter: StateFlow<String> = _mgebTypeFilter.asStateFlow()

    private val _mgebSelectedIds = MutableStateFlow<Set<Pair<Int, String>>>(emptySet())
    val mgebSelectedIds: StateFlow<Set<Pair<Int, String>>> = _mgebSelectedIds.asStateFlow()

    fun loadMgebCatalog(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isMgebLoading.value = true
            try {
                val movies = MegaEmbedService.fetchMegaEmbedMovies(forceRefresh)
                val series = MegaEmbedService.fetchMegaEmbedSeries(forceRefresh)
                _mgebMovies.value = movies
                _mgebSeries.value = series
                
                // Initially populate candidates with first page
                updateMgebCandidates()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error loading Mgeb catalog: ${e.message}")
            } finally {
                _isMgebLoading.value = false
            }
        }
    }

    fun onMgebSearchQueryChanged(query: String) {
        _mgebSearchQuery.value = query
        _mgebPage.value = 1
        updateMgebCandidates()
    }

    fun setMgebTypeFilter(filter: String) {
        _mgebTypeFilter.value = filter
        _mgebPage.value = 1
        updateMgebCandidates()
    }

    fun setMgebPage(page: Int) {
        val total = _mgebTotalPages.value
        if (page in 1..total) {
            _mgebPage.value = page
            updateMgebCandidates()
        }
    }

    private fun updateMgebCandidates() {
        viewModelScope.launch {
            val query = _mgebSearchQuery.value.lowercase()
            val typeFilter = _mgebTypeFilter.value
            val allMovies = _mgebMovies.value
            val allSeries = _mgebSeries.value
            
            val allCombined = (allMovies + allSeries).map { item ->
                val type = if (item.type == "tv") "tv" else "movie"
                Pair(item.tmdbId ?: 0, type)
            }
            
            // Filter combined results
            val filteredList = allCombined.filter { (tmdbId, type) ->
                val matchesType = typeFilter == "ALL" || type == typeFilter
                
                val cached = mgebEnrichedCache[Pair(tmdbId, type)]
                val title = cached?.title?.lowercase() ?: "tmdb #$tmdbId"
                val matchesQuery = query.isBlank() || 
                        tmdbId.toString().contains(query) || 
                        title.contains(query)
                
                matchesType && matchesQuery
            }
            
            _mgebTotalCount.value = filteredList.size
            val pageSize = 24
            val pages = if (filteredList.isEmpty()) 1 else (filteredList.size + pageSize - 1) / pageSize
            _mgebTotalPages.value = pages
            
            // Safety adjustment
            if (_mgebPage.value > pages) {
                _mgebPage.value = pages
            } else if (_mgebPage.value < 1) {
                _mgebPage.value = 1
            }
            
            val startIndex = (_mgebPage.value - 1) * pageSize
            val pageSlice = filteredList.drop(startIndex).take(pageSize)
            
            val candidates = mutableListOf<TmdbSearchResultItem>()
            for ((tmdbId, type) in pageSlice) {
                val existing = database.playFilmeDao().getMediaByTmdbIdAndType(tmdbId, type)
                if (existing != null) {
                    candidates.add(TmdbSearchResultItem(existing, true))
                } else {
                    val cached = mgebEnrichedCache[Pair(tmdbId, type)]
                    val placeholder = cached ?: MediaEntity(
                        tmdbId = tmdbId,
                        title = "TMDB #$tmdbId",
                        mediaType = type,
                        posterPath = "",
                        backdropPath = "",
                        overview = "",
                        releaseYear = "",
                        rating = 0.0,
                        genres = ""
                    )
                    candidates.add(TmdbSearchResultItem(placeholder, false))
                }
            }
            
            _mgebCandidates.value = candidates
            
            // Background metadata enrichment for placeholders
            val toEnrich = candidates.filter { !it.isAlreadyInCatalog && (it.entity.posterPath == null || it.entity.posterPath.isEmpty()) }
            enrichMgebMetadata(toEnrich)
        }
    }

    private fun enrichMgebMetadata(items: List<TmdbSearchResultItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                try {
                    val tmdbId = item.entity.tmdbId
                    val type = item.entity.mediaType
                    val dto = if (type == "movie") {
                        com.example.data.remote.TmdbNetwork.apiService.getMovieDetails(tmdbId, com.example.BuildConfig.TMDB_API_KEY)
                    } else {
                        com.example.data.remote.TmdbNetwork.apiService.getSeriesDetails(tmdbId, com.example.BuildConfig.TMDB_API_KEY)
                    }
                    
                    val enrichedEntity = item.entity.copy(
                        title = dto.title ?: dto.name ?: item.entity.title,
                        posterPath = dto.posterPath ?: "",
                        releaseYear = (dto.releaseDate ?: dto.firstAirDate ?: "").take(4),
                        rating = dto.voteAverage ?: 0.0
                    )
                    
                    mgebEnrichedCache[Pair(tmdbId, type)] = enrichedEntity
                    
                    val current = _mgebCandidates.value.toMutableList()
                    val index = current.indexOfFirst { it.entity.tmdbId == tmdbId && it.entity.mediaType == type }
                    if (index != -1) {
                        current[index] = TmdbSearchResultItem(enrichedEntity, false)
                        _mgebCandidates.value = current
                    }
                } catch (e: Exception) {
                    // Silent fail for background enrichment
                }
            }
        }
    }

    fun toggleMgebSelection(tmdbId: Int, type: String) {
        val current = _mgebSelectedIds.value
        val pair = Pair(tmdbId, type)
        _mgebSelectedIds.value = if (current.contains(pair)) {
            current - pair
        } else {
            current + pair
        }
    }

    fun toggleMgebPageSelection(visibleItems: List<Pair<Int, String>>, selectAll: Boolean) {
        val current = _mgebSelectedIds.value
        _mgebSelectedIds.value = if (selectAll) {
            current + visibleItems
        } else {
            current - visibleItems.toSet()
        }
    }

    fun clearMgebSelection() {
        _mgebSelectedIds.value = emptySet()
    }

    fun selectAllMgebResults() {
        viewModelScope.launch {
            val query = _mgebSearchQuery.value.lowercase()
            val typeFilter = _mgebTypeFilter.value
            val allMovies = _mgebMovies.value
            val allSeries = _mgebSeries.value
            
            val allCombined = (allMovies + allSeries).map { item ->
                val type = if (item.type == "tv") "tv" else "movie"
                Pair(item.tmdbId ?: 0, type)
            }
            
            val filtered = allCombined.filter { (tmdbId, type) ->
                val matchesType = typeFilter == "ALL" || type == typeFilter
                val cached = mgebEnrichedCache[Pair(tmdbId, type)]
                val title = cached?.title?.lowercase() ?: "tmdb #$tmdbId"
                val matchesQuery = query.isBlank() || 
                        tmdbId.toString().contains(query) || 
                        title.contains(query)
                matchesType && matchesQuery
            }
            
            _mgebSelectedIds.value = _mgebSelectedIds.value + filtered
        }
    }

    fun getMgebSelectionSummary(onResult: (total: Int, novos: Int, existentes: Int) -> Unit) {
        viewModelScope.launch {
            val selected = _mgebSelectedIds.value
            var existentesCount = 0
            for ((tmdbId, type) in selected) {
                val exists = database.playFilmeDao().getMediaByTmdbIdAndType(tmdbId, type) != null
                if (exists) {
                    existentesCount++
                }
            }
            val novosCount = selected.size - existentesCount
            onResult(selected.size, novosCount, existentesCount)
        }
    }

    fun startMgebSelectedImport(onComplete: () -> Unit) {
        viewModelScope.launch {
            val selected = _mgebSelectedIds.value
            val idsToImport = mutableListOf<Pair<Int, String>>()
            
            for ((tmdbId, type) in selected) {
                val exists = database.playFilmeDao().getMediaByTmdbIdAndType(tmdbId, type) != null
                if (!exists) {
                    idsToImport.add(Pair(tmdbId, type))
                }
            }
            
            if (idsToImport.isNotEmpty()) {
                startMgebMassImport(idsToImport)
            }
            clearMgebSelection()
            onComplete()
        }
    }

    fun startMgebMassImport(ids: List<Pair<Int, String>>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val config = ImportConfig(concurrentWorkers = 2, retryErrors = true, updateExisting = false)
                val jobId = mediaRepository.createMassImportJob(
                    type = "mgeb_batch",
                    source = "MGEB API",
                    ids = ids,
                    config = config
                )
                _importMessage.value = "🚀 Importação em massa da Mgeb iniciada para ${ids.size} itens!"
                selectSection(AdminSection.IMPORTACAO_MASSA)
                selectJob(jobId)
            } catch (e: Exception) {
                _importMessage.value = "❌ Erro ao iniciar importação: ${e.localizedMessage}"
            }
        }
    }

    // --- Active Admin Section ---
    private val _currentSection = MutableStateFlow(AdminSection.ESTATISTICAS)
    val currentSection: StateFlow<AdminSection> = _currentSection.asStateFlow()

    fun selectSection(section: AdminSection) {
        _currentSection.value = section
    }

    // --- Sync & Count Flows ---
    val movieCount: Flow<Int> = mediaRepository.movieCount
    val seriesCount: Flow<Int> = mediaRepository.seriesCount
    val animeCount: Flow<Int> = mediaRepository.animeCount
    val doramaCount: Flow<Int> = mediaRepository.doramaCount
    val allChannels: Flow<List<com.example.data.local.ChannelEntity>> = mediaRepository.allLiveChannels

    // --- Catalog Management States ---
    val allCatalogMedia: StateFlow<List<MediaEntity>> = mediaRepository.allMedia
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _catalogSearchQuery = MutableStateFlow("")
    val catalogSearchQuery: StateFlow<String> = _catalogSearchQuery.asStateFlow()

    private val _catalogFilter = MutableStateFlow("all") // "all", "movie", "tv", "anime", "dorama"
    val catalogFilter: StateFlow<String> = _catalogFilter.asStateFlow()

    private val _catalogDisplayLimit = MutableStateFlow(30)
    val catalogDisplayLimit: StateFlow<Int> = _catalogDisplayLimit.asStateFlow()

    // Dialog & Action States
    private val _catalogDetailMedia = MutableStateFlow<MediaEntity?>(null)
    val catalogDetailMedia: StateFlow<MediaEntity?> = _catalogDetailMedia.asStateFlow()

    private val _catalogEditMedia = MutableStateFlow<MediaEntity?>(null)
    val catalogEditMedia: StateFlow<MediaEntity?> = _catalogEditMedia.asStateFlow()

    private val _catalogDeleteMedia = MutableStateFlow<MediaEntity?>(null)
    val catalogDeleteMedia: StateFlow<MediaEntity?> = _catalogDeleteMedia.asStateFlow()

    private val _isCatalogActionRunning = MutableStateFlow(false)
    val isCatalogActionRunning: StateFlow<Boolean> = _isCatalogActionRunning.asStateFlow()

    private val _catalogActionStage = MutableStateFlow<String?>(null)
    val catalogActionStage: StateFlow<String?> = _catalogActionStage.asStateFlow()

    private val _catalogActionMessage = MutableStateFlow<String?>(null)
    val catalogActionMessage: StateFlow<String?> = _catalogActionMessage.asStateFlow()

    // --- Selection and Mass Delete States ---
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedMedia = MutableStateFlow<Set<Pair<Int, String>>>(emptySet()) // Set de Pair(tmdbId, mediaType)
    val selectedMedia: StateFlow<Set<Pair<Int, String>>> = _selectedMedia.asStateFlow()

    private val _bulkDeleteProgress = MutableStateFlow(0)
    val bulkDeleteProgress: StateFlow<Int> = _bulkDeleteProgress.asStateFlow()

    private val _bulkDeleteTotal = MutableStateFlow(0)
    val bulkDeleteTotal: StateFlow<Int> = _bulkDeleteTotal.asStateFlow()

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedMedia.value = emptySet()
        }
    }

    fun toggleSelectMedia(tmdbId: Int, mediaType: String) {
        val current = _selectedMedia.value.toMutableSet()
        val item = Pair(tmdbId, mediaType)
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _selectedMedia.value = current
    }

    fun selectAllMedia(items: List<MediaEntity>) {
        val current = _selectedMedia.value.toMutableSet()
        for (media in items) {
            current.add(Pair(media.tmdbId, media.mediaType))
        }
        _selectedMedia.value = current
    }

    fun clearSelection() {
        _selectedMedia.value = emptySet()
    }

    fun confirmBulkDelete() {
        val itemsToDelete = _selectedMedia.value.toList()
        if (itemsToDelete.isEmpty() || _isCatalogActionRunning.value) return
        
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _bulkDeleteTotal.value = itemsToDelete.size
            _bulkDeleteProgress.value = 0
            _catalogActionStage.value = "Excluindo 0 de ${itemsToDelete.size}..."
            
            try {
                android.util.Log.d("AdminViewModel", "[BULK-DELETE] Iniciando exclusão de ${itemsToDelete.size} itens...")
                val (success, msg) = mediaRepository.deleteMediaBatchFromCatalog(itemsToDelete) { progress ->
                    _bulkDeleteProgress.value = progress
                    _catalogActionStage.value = "Excluindo $progress de ${itemsToDelete.size}..."
                }
                
                if (success) {
                    addAuditLog("Excluiu em massa", "${itemsToDelete.size} títulos")
                    _selectedMedia.value = emptySet()
                    _isSelectionMode.value = false
                }
                _catalogActionMessage.value = msg
                loadStats()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "[BULK-DELETE] Erro ao excluir lote: ${e.message}", e)
                _catalogActionMessage.value = "Erro ao excluir títulos selecionados: ${e.localizedMessage}"
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
                _bulkDeleteProgress.value = 0
                _bulkDeleteTotal.value = 0
            }
        }
    }

    fun onCatalogSearchQueryChanged(query: String) {
        _catalogSearchQuery.value = query
        _catalogDisplayLimit.value = 30
    }

    fun setCatalogFilter(filter: String) {
        _catalogFilter.value = filter
        _catalogDisplayLimit.value = 30
    }

    fun loadMoreCatalog() {
        _catalogDisplayLimit.value = _catalogDisplayLimit.value + 30
    }

    fun openDetailDialog(media: MediaEntity) {
        _catalogDetailMedia.value = media
    }

    fun closeDetailDialog() {
        _catalogDetailMedia.value = null
    }

    fun openEditDialog(media: MediaEntity) {
        _catalogEditMedia.value = media
    }

    fun closeEditDialog() {
        _catalogEditMedia.value = null
    }

    fun openDeleteDialog(media: MediaEntity) {
        _catalogDeleteMedia.value = media
    }

    fun closeDeleteDialog() {
        _catalogDeleteMedia.value = null
    }

    fun confirmDeleteMedia(tmdbId: Int) {
        if (_isCatalogActionRunning.value) return
        val title = _catalogDeleteMedia.value?.title ?: "ID $tmdbId"
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _catalogActionStage.value = "Excluindo conteúdo do catálogo..."
            try {
                val (success, msg) = mediaRepository.deleteMediaFromCatalog(tmdbId)
                _catalogDeleteMedia.value = null
                _catalogActionMessage.value = msg
                if (success) {
                    addAuditLog("Excluiu o conteúdo", title)
                }
                loadStats()
            } catch (e: Exception) {
                _catalogActionMessage.value = "Erro ao excluir conteúdo: ${e.localizedMessage}"
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
            }
        }
    }

    fun saveMediaEdit(editedEntity: MediaEntity) {
        if (_isCatalogActionRunning.value) return
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _catalogActionStage.value = "Salvando alterações no catálogo..."
            try {
                val (success, msg) = mediaRepository.updateMediaInCatalog(editedEntity)
                _catalogEditMedia.value = null
                _catalogActionMessage.value = msg
                if (success) {
                    addAuditLog("Editou o conteúdo", editedEntity.title)
                }
                loadStats()
            } catch (e: Exception) {
                _catalogActionMessage.value = "Erro ao salvar alteração: ${e.localizedMessage}"
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
            }
        }
    }

    fun refreshMediaInfo(tmdbId: Int, type: String) {
        if (_isCatalogActionRunning.value) return
        val title = _catalogDetailMedia.value?.title ?: "ID $tmdbId"
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _catalogActionStage.value = "Consultando e atualizando dados no TMDB..."
            try {
                val (success, msg) = mediaRepository.refreshMediaFromTmdb(tmdbId, type)
                _catalogActionMessage.value = msg
                if (success) {
                    addAuditLog("Atualizou metadados do TMDB", title)
                }
                loadStats()
            } catch (e: Exception) {
                _catalogActionMessage.value = "Erro ao atualizar dados: ${e.localizedMessage}"
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
            }
        }
    }

    fun clearCatalogActionMessage() {
        _catalogActionMessage.value = null
    }

    // Import states
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _isImportingSingle = MutableStateFlow(false)
    val isImportingSingle: StateFlow<Boolean> = _isImportingSingle.asStateFlow()

    // --- Smart TMDB Search States ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow("all") // "all", "movie", "tv"
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TmdbSearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<TmdbSearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // Selected Preview & Duplication Check State
    private val _selectedPreviewMedia = MutableStateFlow<MediaEntity?>(null)
    val selectedPreviewMedia: StateFlow<MediaEntity?> = _selectedPreviewMedia.asStateFlow()

    private val _selectedPreviewExists = MutableStateFlow(false)
    val selectedPreviewExists: StateFlow<Boolean> = _selectedPreviewExists.asStateFlow()

    private val _isPreviewLoading = MutableStateFlow(false)
    val isPreviewLoading: StateFlow<Boolean> = _isPreviewLoading.asStateFlow()

    // Import Steps Progress Animation State
    private val _importStepMessage = MutableStateFlow<String?>(null)
    val importStepMessage: StateFlow<String?> = _importStepMessage.asStateFlow()

    private val _importSuccessEntity = MutableStateFlow<MediaEntity?>(null)
    val importSuccessEntity: StateFlow<MediaEntity?> = _importSuccessEntity.asStateFlow()

    // Mass import state
    val massImportProgress = MutableStateFlow(MediaRepository.MassImportProgress())
    private var massImportJob: Job? = null

    private val _isBulkImportRunning = MutableStateFlow(false)
    val isBulkImportRunning: StateFlow<Boolean> = _isBulkImportRunning.asStateFlow()

    private val _isSeriesUpdateRunning = MutableStateFlow(false)
    val isSeriesUpdateRunning: StateFlow<Boolean> = _isSeriesUpdateRunning.asStateFlow()

    fun reimportOrUpdateSeries(tmdbId: Int) {
        if (_isSeriesUpdateRunning.value) {
            Log.w("AdminViewModel", "[UPDATE SERIES] Atualização de série já em andamento.")
            return
        }

        viewModelScope.launch {
            _isSeriesUpdateRunning.value = true
            try {
                _importStepMessage.value = "Verificando série..."
                Log.d("AdminViewModel", "[UPDATE SERIES] Verificando série TMDB ID: $tmdbId")
                delay(300)

                _importStepMessage.value = "Buscando temporadas no TMDB..."
                delay(300)

                _importStepMessage.value = "Verificando episódios..."
                delay(300)

                _importStepMessage.value = "Adicionando conteúdos faltantes..."
                val result = mediaRepository.updateExistingSeries(tmdbId)

                _importMessage.value = result.message
                _importStepMessage.value = null
                addAuditLog("Re-importou/Atualizou série TMDB ID $tmdbId", "Série")

                _selectedPreviewMedia.value?.let { current ->
                    if (current.tmdbId == tmdbId) {
                        selectTmdbIdForPreview(tmdbId.toString(), current.mediaType)
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminViewModel", "[UPDATE SERIES ERROR] ${e.message}", e)
                _importMessage.value = "❌ Erro ao atualizar série: ${e.localizedMessage}"
                _importStepMessage.value = null
            } finally {
                _isSeriesUpdateRunning.value = false
            }
        }
    }

    // Dashboard stats & Reactive Flows
    val movieCountState = mediaRepository.movieCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val seriesCountState = mediaRepository.seriesCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val animeCountState = mediaRepository.animeCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val doramaCountState = mediaRepository.doramaCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val episodeCountState = mediaRepository.episodeCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val myListCountState = mediaRepository.myListCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val watchHistoryCountState = mediaRepository.watchHistoryCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val latestAddedMediaState = mediaRepository.latestAddedMedia.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val latestMovieState = mediaRepository.latestMovie.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val latestSeriesState = mediaRepository.latestSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val latestWatchHistoryState = mediaRepository.latestWatchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val popularMediaState = mediaRepository.popularMedia.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus = firebaseService.syncStatus
    val catalogVersion = firebaseService.catalogVersion

    fun getDeviceInfo(): String = firebaseService.getDeviceInfo()

    private val _stats = MutableStateFlow(mapOf("movies" to 0, "series" to 0, "views" to 0))
    val stats: StateFlow<Map<String, Int>> = _stats.asStateFlow()

    // MegaEmbed Config & Sync State
    private val _megaEmbedConfig = MutableStateFlow(MegaEmbedConfig())
    val megaEmbedConfig: StateFlow<MegaEmbedConfig> = _megaEmbedConfig.asStateFlow()

    private val _lastSyncFormatted = MutableStateFlow("Carregando...")
    val lastSyncFormatted: StateFlow<String> = _lastSyncFormatted.asStateFlow()

    private val _isSyncingMegaEmbed = MutableStateFlow(false)
    val isSyncingMegaEmbed: StateFlow<Boolean> = _isSyncingMegaEmbed.asStateFlow()

    private val _syncResult = MutableStateFlow<MegaEmbedSyncResult?>(null)
    val syncResult: StateFlow<MegaEmbedSyncResult?> = _syncResult.asStateFlow()

    // Import history
    private val _importHistory = MutableStateFlow<List<ImportHistoryItem>>(emptyList())
    val importHistory: StateFlow<List<ImportHistoryItem>> = _importHistory.asStateFlow()

    data class ImportHistoryItem(
        val tmdbId: Int,
        val title: String,
        val type: String,
        val timestamp: Long = System.currentTimeMillis(),
        val status: String // "success", "existing", "failed"
    )

    data class MassCandidateItem(
        val tmdbId: Int,
        val mediaType: String,
        val title: String,
        val originalTitle: String = "",
        val year: String = "",
        val rating: Double = 0.0,
        val posterPath: String? = null,
        val overview: String = "",
        val genres: String = "",
        val isAlreadyInCatalog: Boolean = false,
        val selected: Boolean = true
    )

    private val _massCandidates = MutableStateFlow<List<MassCandidateItem>>(emptyList())
    val massCandidates: StateFlow<List<MassCandidateItem>> = _massCandidates.asStateFlow()

    private val _recentCandidates = MutableStateFlow<List<MassCandidateItem>>(emptyList())
    val recentCandidates: StateFlow<List<MassCandidateItem>> = _recentCandidates.asStateFlow()

    private val _isLoadingRecentCandidates = MutableStateFlow(false)
    val isLoadingRecentCandidates: StateFlow<Boolean> = _isLoadingRecentCandidates.asStateFlow()

    fun addMassCandidate(item: MassCandidateItem) {
        if (_massCandidates.value.none { it.tmdbId == item.tmdbId && it.mediaType == item.mediaType }) {
            _massCandidates.value = _massCandidates.value + item
        }
    }

    fun addMassCandidates(items: List<MassCandidateItem>) {
        val existingKeys = _massCandidates.value.map { "${it.tmdbId}_${it.mediaType}" }.toSet()
        val newItems = items.filter { "${it.tmdbId}_${it.mediaType}" !in existingKeys }
        _massCandidates.value = _massCandidates.value + newItems
        enrichMassCandidates(newItems)
    }

    private fun enrichMassCandidates(items: List<MassCandidateItem>) {
        viewModelScope.launch {
            items.forEach { candidate ->
                try {
                    val exists = mediaRepository.getMediaByTmdbId(candidate.tmdbId, candidate.mediaType) != null
                    val apiKey = com.example.BuildConfig.TMDB_API_KEY.ifEmpty { "d60f5b5a778fe74b5cf4a371f008725b" }
                    val details = try {
                        if (candidate.mediaType == "movie") {
                            com.example.data.remote.TmdbNetwork.apiService.getMovieDetails(candidate.tmdbId, apiKey)
                        } else {
                            com.example.data.remote.TmdbNetwork.apiService.getSeriesDetails(candidate.tmdbId, apiKey)
                        }
                    } catch (e: Exception) { null }
                    
                    if (details != null) {
                        _massCandidates.value = _massCandidates.value.map { item ->
                            if (item.tmdbId == candidate.tmdbId && item.mediaType == candidate.mediaType) {
                                val titleStr = details.title ?: details.name ?: "TMDB #${candidate.tmdbId}"
                                val yearStr = details.releaseDate?.takeIf { it.length >= 4 }?.take(4) ?: details.firstAirDate?.takeIf { it.length >= 4 }?.take(4) ?: ""
                                val ratingVal = details.voteAverage ?: 0.0
                                val posterStr = details.posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
                                
                                item.copy(
                                    title = titleStr,
                                    year = yearStr,
                                    rating = ratingVal,
                                    posterPath = posterStr,
                                    overview = details.overview ?: "",
                                    isAlreadyInCatalog = exists
                                )
                            } else item
                        }
                    } else if (exists) {
                        _massCandidates.value = _massCandidates.value.map { item ->
                            if (item.tmdbId == candidate.tmdbId && item.mediaType == candidate.mediaType) {
                                item.copy(isAlreadyInCatalog = true)
                            } else item
                        }
                    }
                } catch (e: Exception) {
                    // Ignore enrichment errors silently
                }
            }
        }
    }

    fun toggleMassCandidateSelected(tmdbId: Int, mediaType: String) {
        _massCandidates.value = _massCandidates.value.map {
            if (it.tmdbId == tmdbId && it.mediaType == mediaType) {
                it.copy(selected = !it.selected)
            } else it
        }
    }

    fun selectAllMassCandidates(selected: Boolean) {
        _massCandidates.value = _massCandidates.value.map { it.copy(selected = selected) }
    }

    fun removeSelectedMassCandidates() {
        _massCandidates.value = _massCandidates.value.filter { !it.selected }
    }

    fun clearMassCandidates() {
        _massCandidates.value = emptyList()
    }

    fun loadRecentCandidatesFromTmdb() {
        viewModelScope.launch {
            _isLoadingRecentCandidates.value = true
            try {
                val candidates = mutableListOf<MassCandidateItem>()

                // 1. Fetch Movies independently
                try {
                    val movies = mediaRepository.fetchPopularMoviesFromTmdb(1)
                    for (m in movies) {
                        val exists = mediaRepository.getMediaByTmdbId(m.id, "movie") != null
                        if (!exists) {
                            candidates.add(
                                MassCandidateItem(
                                    tmdbId = m.id,
                                    mediaType = "movie",
                                    title = m.title ?: m.name ?: "Sem título",
                                    originalTitle = m.originalTitle ?: "",
                                    year = m.releaseDate?.take(4) ?: "",
                                    rating = m.voteAverage ?: 0.0,
                                    posterPath = m.posterPath,
                                    overview = m.overview ?: "Sem sinopse disponível.",
                                    isAlreadyInCatalog = false,
                                    selected = false
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Error fetching popular movies for recent candidates: ${e.message}")
                }

                // 2. Fetch Series independently
                try {
                    val series = mediaRepository.fetchPopularSeriesFromTmdb(1)
                    for (s in series) {
                        val exists = mediaRepository.getMediaByTmdbId(s.id, "tv") != null
                        if (!exists) {
                            candidates.add(
                                MassCandidateItem(
                                    tmdbId = s.id,
                                    mediaType = "tv",
                                    title = s.name ?: s.title ?: "Sem título",
                                    originalTitle = s.originalName ?: "",
                                    year = s.firstAirDate?.take(4) ?: "",
                                    rating = s.voteAverage ?: 0.0,
                                    posterPath = s.posterPath,
                                    overview = s.overview ?: "Sem sinopse disponível.",
                                    isAlreadyInCatalog = false,
                                    selected = false
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdminViewModel", "Error fetching popular series for recent candidates: ${e.message}")
                }

                _recentCandidates.value = candidates
                Log.d("AdminViewModel", "[RECENT] Carregadas ${candidates.size} novidades válidas do TMDB.")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error loading recent candidates from TMDB: ${e.message}", e)
            } finally {
                _isLoadingRecentCandidates.value = false
            }
        }
    }

    fun startMassImportForSelectedCandidates(config: ImportConfig = ImportConfig(concurrentWorkers = 2)) {
        if (_isBulkImportRunning.value) {
            Log.w("AdminViewModel", "[BULK] Importação já está em execução. Ignorando clique duplo.")
            return
        }

        val selected = _massCandidates.value.filter { it.selected }
        if (selected.isEmpty()) {
            _importMessage.value = "⚠️ Selecione pelo menos um conteúdo para importar."
            Log.w("AdminViewModel", "[BULK] Nenhum item selecionado.")
            return
        }

        val idList = selected.map { Pair(it.tmdbId, it.mediaType) }
        Log.d("AdminViewModel", "[BULK] Botão clicado. Total selecionados: ${idList.size}")

        viewModelScope.launch {
            _isBulkImportRunning.value = true
            try {
                Log.d("AdminViewModel", "[BULK] Criando job de importação em massa no Firestore...")
                val jobId = mediaRepository.createMassImportJob(
                    type = "mixed",
                    source = "candidates_selection",
                    ids = idList,
                    config = config
                )
                Log.d("AdminViewModel", "[BULK] Job criado com sucesso ID: $jobId. Selecionando job na UI...")
                selectJob(jobId)
                _importMessage.value = "🚀 Importação em massa iniciada com ${idList.size} itens!"
                addAuditLog("Iniciou importação em massa de ${idList.size} itens", "Importação")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "[BULK ERROR] Erro ao criar lote de importação: ${e.message}", e)
                _importMessage.value = "❌ Erro ao iniciar importação: ${e.localizedMessage}"
            } finally {
                _isBulkImportRunning.value = false
            }
        }
    }

    private fun addHistory(id: Int, title: String, type: String, status: String) {
        val newItem = ImportHistoryItem(id, title, type, status = status)
        _importHistory.value = (listOf(newItem) + _importHistory.value).take(50)
    }

    // --- PROFESSIONAL MASS IMPORT SYSTEM ---
    val importJobs: StateFlow<List<ImportJob>> = mediaRepository.observeImportJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    private val _selectedJobId = MutableStateFlow<String?>(null)
    val selectedJobId: StateFlow<String?> = _selectedJobId.asStateFlow()
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentImportJob: StateFlow<ImportJob?> = _selectedJobId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(null)
        else mediaRepository.observeImportJob(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentImportItems: StateFlow<List<ImportItem>> = _selectedJobId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else mediaRepository.observeImportItems(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _globalImportStats = MutableStateFlow(ImportSummary())
    val globalImportStats: StateFlow<ImportSummary> = _globalImportStats.asStateFlow()
    
    fun selectJob(jobId: String?) {
        _selectedJobId.value = jobId
    }
    
    fun startNewMassImport(
        type: String,
        source: String,
        ids: List<Pair<Int, String>>,
        config: ImportConfig
    ) {
        viewModelScope.launch {
            try {
                val jobId = mediaRepository.createMassImportJob(type, source, ids, config)
                _selectedJobId.value = jobId
                addAuditLog("Iniciou importação em massa ($source)", "Job: $jobId")
                refreshGlobalStats()
            } catch (e: Exception) {
                _importMessage.value = "Erro ao iniciar importação: ${e.localizedMessage}"
            }
        }
    }
    
    fun pauseJob(jobId: String) {
        viewModelScope.launch {
            mediaRepository.pauseImportJob(jobId)
            addAuditLog("Pausou importação", jobId)
        }
    }
    
    fun resumeJob(jobId: String) {
        viewModelScope.launch {
            mediaRepository.resumeImportJob(jobId)
            addAuditLog("Retomou importação", jobId)
        }
    }
    
    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            mediaRepository.cancelImportJob(jobId)
            addAuditLog("Cancelou importação", jobId)
        }
    }
    
    fun reprocessFailed(jobId: String) {
        viewModelScope.launch {
            mediaRepository.reprocessFailedItems(jobId)
            addAuditLog("Reprocessando falhas de importação", jobId)
        }
    }
    
    fun refreshGlobalStats() {
        viewModelScope.launch {
            _globalImportStats.value = mediaRepository.getGlobalStatsFromFirestore()
        }
    }

    init {
        firebaseService.startListeningDevices()
        firebaseService.startListeningRequests()
        viewModelScope.launch {
            try {
                val dbPassword = database.playFilmeDao().getSetting("admin_password")
                if (dbPassword != null && dbPassword.isNotBlank()) {
                    cachedAdminPassword = dbPassword.trim()
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminViewModel", "Error loading admin password: ${e.message}")
            }
        }
        loadStats()
        loadMegaEmbedConfig()
        loadShareAppConfig()
        loadTmdbAutoSyncConfig()
        refreshGlobalStats()
        
        // Auto-resume active jobs on startup
        viewModelScope.launch {
            delay(2000)
            importJobs.value.filter { it.status == "processing" }.forEach { job ->
                mediaRepository.startImportWorker(job.id)
            }
        }
    }



    fun loadShareAppConfig() {
        viewModelScope.launch {
            _shareAppConfig.value = mediaRepository.getShareAppConfig()
        }
    }

    fun saveShareAppConfig(config: MediaRepository.ShareAppConfigData) {
        viewModelScope.launch {
            mediaRepository.saveShareAppConfig(config)
            _shareAppConfig.value = config
            _importMessage.value = "Configuração de compartilhamento e links salva!"
        }
    }

    fun loadMegaEmbedConfig() {
        viewModelScope.launch {
            _megaEmbedConfig.value = mediaRepository.getMegaEmbedConfig()
            _lastSyncFormatted.value = mediaRepository.getLastSyncFormatted()
        }
    }

    fun saveMegaEmbedConfig(config: MegaEmbedConfig) {
        viewModelScope.launch {
            mediaRepository.saveMegaEmbedConfig(config)
            _megaEmbedConfig.value = config

            val currentAdminEmail = firebaseService.currentUser.value?.email ?: "admin@ronycine.app"
            firebaseService.updateMegaEmbedConfig(
                com.example.data.remote.MegaEmbedPlayerConfig(
                    enabled = true,
                    player = config.defaultPlayer,
                    color = com.example.data.remote.MegaEmbedPlayerType.normalizeColor(config.colorHex)
                ),
                currentAdminEmail
            )

            _importMessage.value = "Configuração do MegaEmbed salva com sucesso!"
        }
    }

    fun runMegaEmbedSync() {
        viewModelScope.launch {
            _isSyncingMegaEmbed.value = true
            val result = mediaRepository.syncMegaEmbedCatalog()
            _syncResult.value = result
            _lastSyncFormatted.value = mediaRepository.getLastSyncFormatted()
            _isSyncingMegaEmbed.value = false
            loadStats()
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _stats.value = mediaRepository.getStats()
        }
    }

    // --- Smart Live Search with Debounce (350ms) ---
    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(350) // 350ms Debounce
            val results = mediaRepository.searchTmdbForAdmin(newQuery, _searchFilter.value)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun setSearchFilter(filter: String) {
        _searchFilter.value = filter
        onSearchQueryChanged(_searchQuery.value)
    }

    fun selectMediaForPreview(entity: MediaEntity, isAlreadyInCatalog: Boolean) {
        viewModelScope.launch {
            _isPreviewLoading.value = true
            _importSuccessEntity.value = null
            _importStepMessage.value = null

            val (fullEntity, exists) = mediaRepository.fetchTmdbPreview(entity.tmdbId, entity.mediaType)
            _selectedPreviewMedia.value = fullEntity ?: entity
            _selectedPreviewExists.value = exists
            _isPreviewLoading.value = false
        }
    }

    fun selectTmdbIdForPreview(tmdbIdStr: String, type: String) {
        val tmdbId = tmdbIdStr.toIntOrNull()
        if (tmdbId == null) {
            _importMessage.value = "Digite um TMDB ID numérico válido."
            return
        }

        viewModelScope.launch {
            _isPreviewLoading.value = true
            _importMessage.value = null
            _importSuccessEntity.value = null
            _importStepMessage.value = null

            val (entity, exists) = mediaRepository.fetchTmdbPreview(tmdbId, type)
            if (entity != null) {
                _selectedPreviewMedia.value = entity
                _selectedPreviewExists.value = exists
            } else {
                _selectedPreviewMedia.value = null
                _importMessage.value = "Conteúdo não encontrado no TMDB para o ID $tmdbId."
            }
            _isPreviewLoading.value = false
        }
    }

    fun clearPreview() {
        _selectedPreviewMedia.value = null
        _selectedPreviewExists.value = false
        _importSuccessEntity.value = null
        _importStepMessage.value = null
    }

    fun confirmImportSelectedMedia() {
        val entity = _selectedPreviewMedia.value ?: return

        viewModelScope.launch {
            _isImportingSingle.value = true
            _importStepMessage.value = "🚀 Iniciando processo de importação..."
            try {
                // 1. Verificando duplicidade
                _importStepMessage.value = "🔍 Verificando duplicidade no catálogo..."
                delay(300)
                val existsInDb = mediaRepository.getMediaByTmdbId(entity.tmdbId, entity.mediaType) != null
                if (existsInDb) {
                    _importMessage.value = "⚠️ Este conteúdo já existe no catálogo do RONYCINE."
                    _selectedPreviewExists.value = true
                    _importStepMessage.value = null
                    return@launch
                }

                // 2. Preparando Metadados
                _importStepMessage.value = "📦 Mapeando metadados e posters..."
                delay(400)
                
                // 3. Salvando no Banco
                _importStepMessage.value = "💾 Salvando no banco de dados do RONYCINE..."
                delay(300)

                val (success, msg) = mediaRepository.importMediaEntity(entity)

                if (success) {
                    _importStepMessage.value = "✅ Importação confirmada e finalizada!"
                    delay(500)
                    
                    // Update all pending requests for this TMDB ID
                    val allReqs = allMediaRequests.value
                    allReqs.filter { it.tmdbId == entity.tmdbId && it.mediaType == entity.mediaType && (it.status.uppercase() == "PENDENTE" || it.status == "pending") }
                        .forEach { req ->
                            mediaRepository.updateMediaRequestStatus(req.id, "ADICIONADO")
                        }

                    addHistory(entity.tmdbId, entity.title, entity.mediaType, if (msg?.contains("atualizado") == true) "existing" else "success")
                    addAuditLog("Importou o conteúdo", entity.title)
                    
                    // Automatic push notifications if enabled
                    if (_autoNotificationsEnabled.value && msg?.contains("atualizado") != true) {
                        _importStepMessage.value = "🔔 Disparando notificações push..."
                        try {
                            val notifId = "NEW_PUBLICATION:${entity.mediaType}:${entity.tmdbId}"
                            val isMovie = entity.mediaType.lowercase() == "movie"
                            val notifTitle = if (isMovie) "🎬 Nova publicação no RONYCINE" else "📺 Nova série no RONYCINE"
                            val notifMessage = if (isMovie) "“${entity.title}” Já está disponível para assistir." else "“${entity.title}” Já está disponível."
                            val notifType = "NEW_PUBLICATION"
                            val actionUrl = if (isMovie) "movie/${entity.tmdbId}" else "tv/${entity.tmdbId}"

                            val localNotif = NotificationEntity(
                                id = notifId,
                                title = notifTitle,
                                message = notifMessage,
                                imageUrl = entity.posterPath,
                                type = notifType,
                                timestamp = System.currentTimeMillis(),
                                scheduledAt = 0L,
                                buttonText = "Assistir Agora",
                                actionUrl = actionUrl,
                                targetSegment = if (isMovie) "MOVIES" else "SERIES",
                                isActive = true,
                                isRead = false,
                                isSent = true
                            )
                            mediaRepository.sendNotification(localNotif)

                            firebaseService.sendNotificationEventToCloud(
                                id = notifId,
                                title = localNotif.title,
                                message = localNotif.message,
                                imageUrl = localNotif.imageUrl,
                                type = localNotif.type,
                                actionUrl = localNotif.actionUrl,
                                targetSegment = localNotif.targetSegment,
                                buttonText = localNotif.buttonText
                            )
                        } catch (e: Exception) {
                            Log.e("AdminViewModel", "Error sending automatic notification: ${e.message}")
                        }
                    }

                    _importSuccessEntity.value = entity
                    _selectedPreviewExists.value = true
                    _importMessage.value = msg
                    loadStats()
                    // Refresh search results to update catalog tags
                    onSearchQueryChanged(_searchQuery.value)
                } else {
                    _importMessage.value = msg
                }
            } catch (e: Exception) {
                Log.e("AdminViewModel", "[IMPORT ERROR] ${e.message}", e)
                _importMessage.value = "Não foi possível concluir a importação: ${e.localizedMessage}"
            } finally {
                _isImportingSingle.value = false
                _importStepMessage.value = null
            }
        }
    }

    fun importSingleByTmdbId(tmdbIdStr: String, type: String) {
        selectTmdbIdForPreview(tmdbIdStr, type)
    }

    fun startMassImport(pages: Int) {
        massImportJob?.cancel()
        massImportJob = viewModelScope.launch {
            mediaRepository.startMassImport(pages).collect { progress ->
                massImportProgress.value = progress
                if (progress.isFinished) {
                    loadStats()
                    addHistory(0, "Importação em Massa (Páginas)", "bulk", if (progress.failed == 0) "success" else "failed")
                }
            }
        }
    }

    fun startMassImportByPastedIds(rawText: String, defaultType: String) {
        val idList = rawText.split(",", "\n", ";")
            .map { it.trim() }
            .mapNotNull {
                val id = it.toIntOrNull()
                if (id != null) Pair(id, defaultType) else null
            }

        if (idList.isEmpty()) {
            _importMessage.value = "Nenhum TMDB ID válido encontrado no texto informado."
            return
        }

        massImportJob?.cancel()
        massImportJob = viewModelScope.launch {
            mediaRepository.startMassImportByIds(idList).collect { progress ->
                massImportProgress.value = progress
                if (progress.isFinished) {
                    loadStats()
                    addHistory(0, "Importação em Massa (IDs)", "bulk", if (progress.failed == 0) "success" else "failed")
                }
            }
        }
    }

    fun cancelMassImport() {
        mediaRepository.cancelMassImport()
        massImportJob?.cancel()
        massImportProgress.value = massImportProgress.value.copy(cancelled = true, isFinished = true)
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    // --- TMDB PROFESSIONAL AUTO-SYNC SYSTEM ---

    val tmdbAutoSyncProgress: StateFlow<TmdbAutoSyncProgress> = mediaRepository.tmdbAutoSyncProgress

    val tmdbAutoSyncHistory: StateFlow<List<TmdbAutoSyncHistoryEntity>> = mediaRepository.observeTmdbAutoSyncHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestTmdbAutoSync: StateFlow<TmdbAutoSyncHistoryEntity?> = mediaRepository.observeLatestTmdbAutoSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadTmdbAutoSyncConfig() {
        viewModelScope.launch {
            try {
                val config = mediaRepository.getTmdbAutoSyncConfig()
                _tmdbAutoSyncConfig.value = config
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Error loading TMDB auto-sync config: ${e.message}")
            }
        }
    }

    fun startTmdbAutoSyncNow() {
        viewModelScope.launch {
            val currentCfg = _tmdbAutoSyncConfig.value
            mediaRepository.startTmdbAutoSync(isManual = true, customConfig = currentCfg).collect {
                // Progress is automatically bound to mediaRepository.tmdbAutoSyncProgress
                if (it.isFinished) {
                    loadStats()
                    loadTmdbAutoSyncConfig()
                }
            }
        }
    }

    fun cancelTmdbAutoSync() {
        mediaRepository.cancelTmdbAutoSync()
    }

    fun updateTmdbAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _tmdbAutoSyncConfig.value.copy(enabled = enabled)
            _tmdbAutoSyncConfig.value = updated
            mediaRepository.saveTmdbAutoSyncConfig(updated)
            addAuditLog(
                if (enabled) "Ativou a atualização automática do TMDB" else "Desativou a atualização automática do TMDB",
                "Importação"
            )
        }
    }

    fun updateTmdbAutoSyncFrequency(hours: Int) {
        viewModelScope.launch {
            val updated = _tmdbAutoSyncConfig.value.copy(frequencyHours = hours)
            _tmdbAutoSyncConfig.value = updated
            mediaRepository.saveTmdbAutoSyncConfig(updated)
            addAuditLog("Alterou frequência do TMDB para ${hours}h", "Importação")
        }
    }

    fun updateTmdbAutoSyncMaxItems(max: Int) {
        viewModelScope.launch {
            val updated = _tmdbAutoSyncConfig.value.copy(maxItemsPerSync = max)
            _tmdbAutoSyncConfig.value = updated
            mediaRepository.saveTmdbAutoSyncConfig(updated)
            addAuditLog("Alterou limite do TMDB para $max itens", "Importação")
        }
    }

    fun updateTmdbAutoSyncContentType(type: String) {
        viewModelScope.launch {
            val updated = _tmdbAutoSyncConfig.value.copy(contentType = type)
            _tmdbAutoSyncConfig.value = updated
            mediaRepository.saveTmdbAutoSyncConfig(updated)
            addAuditLog("Alterou tipo de conteúdo do TMDB para $type", "Importação")
        }
    }

    fun updateTmdbAutoSyncCriterion(criterion: String) {
        viewModelScope.launch {
            val updated = _tmdbAutoSyncConfig.value.copy(criterion = criterion)
            _tmdbAutoSyncConfig.value = updated
            mediaRepository.saveTmdbAutoSyncConfig(updated)
            addAuditLog("Alterou critério do TMDB para $criterion", "Importação")
        }
    }

    fun updateTmdbAutoSyncUpdateExisting(updateExisting: Boolean) {
        viewModelScope.launch {
            val updated = _tmdbAutoSyncConfig.value.copy(updateExistingMetadata = updateExisting)
            _tmdbAutoSyncConfig.value = updated
            mediaRepository.saveTmdbAutoSyncConfig(updated)
            addAuditLog(
                if (updateExisting) "Habilitou atualização de metadados existentes" else "Desabilitou atualização de metadados existentes",
                "Importação"
            )
        }
    }

    fun clearTmdbAutoSyncHistory() {
        viewModelScope.launch {
            mediaRepository.clearTmdbAutoSyncHistory()
            addAuditLog("Limpou o histórico de atualizações do TMDB", "Importação")
        }
    }


    // --- FEATURED MEDIA MANAGEMENT (MANUAL & AUTOMATIC TMDB) ---
    val featuredConfig: StateFlow<com.example.data.remote.FeaturedConfigEntity> = firebaseService.featuredConfig
    val featuredHistory: StateFlow<List<com.example.data.remote.FeaturedHistoryEntity>> = firebaseService.featuredHistory

    val allFeaturedItems: StateFlow<List<FeaturedMediaItem>> = mediaRepository.allFeaturedItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isUpdatingFeatured = MutableStateFlow(false)
    val isUpdatingFeatured: StateFlow<Boolean> = _isUpdatingFeatured.asStateFlow()

    private val _featuredUpdatingProgress = MutableStateFlow("")
    val featuredUpdatingProgress: StateFlow<String> = _featuredUpdatingProgress.asStateFlow()

    private val _featuredManualSearchResults = MutableStateFlow<List<com.example.data.remote.FeaturedItemConfigEntity>>(emptyList())
    val featuredManualSearchResults: StateFlow<List<com.example.data.remote.FeaturedItemConfigEntity>> = _featuredManualSearchResults.asStateFlow()

    private val _isSearchingManualFeatured = MutableStateFlow(false)
    val isSearchingManualFeatured: StateFlow<Boolean> = _isSearchingManualFeatured.asStateFlow()

    private val _featuredSearchQuery = MutableStateFlow("")
    val featuredSearchQuery: StateFlow<String> = _featuredSearchQuery.asStateFlow()

    fun setFeaturedSearchQuery(query: String) {
        _featuredSearchQuery.value = query
    }

    val searchCatalogForFeatured: StateFlow<List<MediaEntity>> = combine(
        allCatalogMedia,
        _featuredSearchQuery
    ) { catalog: List<MediaEntity>, query: String ->
        if (query.isBlank()) catalog.take(20)
        else {
            val q = query.trim().lowercase()
            catalog.filter { item ->
                item.title.lowercase().contains(q) || 
                item.genres.lowercase().contains(q) ||
                item.tmdbId.toString() == q
            }.take(30)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFeaturedMode(mode: String) {
        viewModelScope.launch {
            val current = featuredConfig.value
            val updated = current.copy(mode = mode, source = if (mode == "automatic") "TMDB" else "MANUAL")
            firebaseService.updateFeaturedConfigInCloud(updated)
            _catalogActionMessage.value = "Modo de destaque alterado para: ${mode.uppercase()}"
        }
    }

    fun saveFeaturedConfig(config: com.example.data.remote.FeaturedConfigEntity) {
        viewModelScope.launch {
            firebaseService.updateFeaturedConfigInCloud(config)
            _catalogActionMessage.value = "Configurações de destaque salvas com sucesso!"
        }
    }

    fun searchManualFeatured(query: String, filterType: String = "all") {
        val q = query.trim()
        if (q.isBlank()) {
            _featuredManualSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSearchingManualFeatured.value = true
            try {
                val apiKey = com.example.BuildConfig.TMDB_API_KEY
                val tmdbApi = com.example.data.remote.TmdbNetwork.apiService

                val numericId = q.toIntOrNull()
                val results = mutableListOf<com.example.data.remote.FeaturedItemConfigEntity>()

                if (numericId != null && apiKey.isNotBlank()) {
                    if (filterType != "tv") {
                        try {
                            val movie = tmdbApi.getMovieDetails(numericId, apiKey)
                            val releaseYear = movie.releaseDate?.take(4)?.toIntOrNull() ?: 0
                            val trailerKey = movie.videos?.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key ?: ""
                            results.add(
                                com.example.data.remote.FeaturedItemConfigEntity(
                                    id = "manual_${movie.id}_movie",
                                    tmdbId = movie.id,
                                    mediaType = "movie",
                                    title = movie.title ?: movie.originalTitle ?: "Sem título",
                                    originalTitle = movie.originalTitle ?: "",
                                    posterPath = movie.posterPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it" },
                                    backdropPath = movie.backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" },
                                    rating = movie.voteAverage ?: 0.0,
                                    releaseYear = releaseYear,
                                    overview = movie.overview ?: "",
                                    genres = movie.genreIds?.joinToString() ?: "",
                                    trailerUrl = trailerKey,
                                    autoPlayTrailer = true
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    if (filterType != "movie") {
                        try {
                            val tv = tmdbApi.getSeriesDetails(numericId, apiKey)
                            val releaseYear = tv.firstAirDate?.take(4)?.toIntOrNull() ?: 0
                            val trailerKey = tv.videos?.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key ?: ""
                            results.add(
                                com.example.data.remote.FeaturedItemConfigEntity(
                                    id = "manual_${tv.id}_tv",
                                    tmdbId = tv.id,
                                    mediaType = "tv",
                                    title = tv.name ?: tv.originalName ?: "Sem título",
                                    originalTitle = tv.originalName ?: "",
                                    posterPath = tv.posterPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it" },
                                    backdropPath = tv.backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" },
                                    rating = tv.voteAverage ?: 0.0,
                                    releaseYear = releaseYear,
                                    overview = tv.overview ?: "",
                                    genres = tv.genreIds?.joinToString() ?: "",
                                    trailerUrl = trailerKey,
                                    autoPlayTrailer = true
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }

                if (results.isEmpty() && apiKey.isNotBlank()) {
                    val searchResponse = tmdbApi.searchMulti(apiKey, q)
                    val filtered = searchResponse.results.filter { dto ->
                        val mType = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
                        when (filterType) {
                            "movie" -> mType == "movie"
                            "tv" -> mType == "tv"
                            else -> mType == "movie" || mType == "tv"
                        }
                    }

                    results.addAll(
                        filtered.map { dto ->
                            val mType = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
                            val releaseYear = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull() ?: 0
                            com.example.data.remote.FeaturedItemConfigEntity(
                                id = "manual_${dto.id}_$mType",
                                tmdbId = dto.id,
                                mediaType = mType,
                                title = dto.title ?: dto.name ?: dto.originalTitle ?: dto.originalName ?: "Sem título",
                                originalTitle = dto.originalTitle ?: dto.originalName ?: "",
                                posterPath = dto.posterPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it" },
                                backdropPath = dto.backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" },
                                rating = dto.voteAverage ?: 0.0,
                                releaseYear = releaseYear,
                                overview = dto.overview ?: "",
                                genres = "",
                                trailerUrl = "",
                                autoPlayTrailer = true
                            )
                        }
                    )
                }

                // Also check local catalog items matching query
                val localMatches = allCatalogMedia.value.filter { media ->
                    val mType = media.mediaType
                    val matchType = when (filterType) {
                        "movie" -> mType == "movie"
                        "tv" -> mType == "tv"
                        else -> true
                    }
                    matchType && (media.title.contains(q, ignoreCase = true) || media.originalTitle.contains(q, ignoreCase = true) || media.tmdbId.toString() == q)
                }.map { media ->
                    com.example.data.remote.FeaturedItemConfigEntity(
                        id = "manual_${media.tmdbId}_${media.mediaType}",
                        tmdbId = media.tmdbId,
                        mediaType = media.mediaType,
                        title = media.title,
                        originalTitle = media.originalTitle,
                        posterPath = media.posterPath,
                        backdropPath = media.backdropPath,
                        rating = media.rating,
                        releaseYear = media.releaseYear.toIntOrNull() ?: 0,
                        overview = media.overview,
                        genres = media.genres,
                        trailerUrl = media.trailerKey ?: "",
                        autoPlayTrailer = true
                    )
                }

                val combined = (localMatches + results).distinctBy { it.uniqueKey }
                _featuredManualSearchResults.value = combined
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error searching manual featured: ${e.message}")
            } finally {
                _isSearchingManualFeatured.value = false
            }
        }
    }

    fun addManualFeaturedItem(item: com.example.data.remote.FeaturedItemConfigEntity) {
        viewModelScope.launch {
            val current = featuredConfig.value
            val existing = current.items.toMutableList()
            if (existing.any { it.uniqueKey == item.uniqueKey }) {
                _catalogActionMessage.value = "Este conteúdo já está nos destaques!"
                return@launch
            }

            val newItem = item.copy(
                displayOrder = existing.size + 1,
                isActive = true,
                addedAt = System.currentTimeMillis()
            )
            existing.add(newItem)

            // Save in Room FeaturedMediaEntity & MediaEntity
            val featuredEntity = FeaturedMediaEntity(
                id = 0,
                mediaTmdbId = newItem.tmdbId,
                mediaType = newItem.mediaType,
                trailerUrl = newItem.trailerUrl,
                autoPlayTrailer = newItem.autoPlayTrailer,
                displayOrder = newItem.displayOrder,
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )
            mediaRepository.saveFeaturedMedia(featuredEntity)

            // If not in catalog, add basic MediaEntity so Home/Detail can render
            val inCatalog = allCatalogMedia.value.any { it.tmdbId == newItem.tmdbId && it.mediaType == newItem.mediaType }
            if (!inCatalog) {
                val media = MediaEntity(
                    tmdbId = newItem.tmdbId,
                    title = newItem.title,
                    originalTitle = newItem.originalTitle,
                    mediaType = newItem.mediaType,
                    posterPath = newItem.posterPath,
                    backdropPath = newItem.backdropPath,
                    overview = newItem.overview,
                    releaseYear = newItem.releaseYear.toString(),
                    rating = newItem.rating,
                    genres = newItem.genres,
                    trailerKey = newItem.trailerUrl,
                    isHeroFeatured = true,
                    addedAt = System.currentTimeMillis()
                )
                mediaRepository.importMediaEntity(media)
            }

            val updatedConfig = current.copy(
                items = existing,
                lastKnownGoodItems = existing,
                lastUpdatedAt = System.currentTimeMillis()
            )
            firebaseService.updateFeaturedConfigInCloud(updatedConfig)
            _catalogActionMessage.value = "✓ Adicionado aos destaques: ${newItem.title}"
        }
    }

    fun removeFeaturedItemByKey(uniqueKey: String) {
        viewModelScope.launch {
            val current = featuredConfig.value
            val target = current.items.find { it.uniqueKey == uniqueKey } ?: current.previewItems.find { it.uniqueKey == uniqueKey }
            val newItems = current.items.filterNot { it.uniqueKey == uniqueKey }.mapIndexed { idx, itm ->
                itm.copy(displayOrder = idx + 1)
            }
            val newPreview = current.previewItems.filterNot { it.uniqueKey == uniqueKey }.mapIndexed { idx, itm ->
                itm.copy(displayOrder = idx + 1)
            }

            // Remove from Room FeaturedMediaEntity ONLY (does NOT delete from Catalog/MediaEntity)
            if (target != null) {
                mediaRepository.deleteFeaturedMedia(id = 0, mediaTmdbId = target.tmdbId)
            }

            val updatedConfig = current.copy(
                items = newItems,
                previewItems = newPreview,
                lastKnownGoodItems = if (newItems.isNotEmpty()) newItems else current.lastKnownGoodItems,
                lastUpdatedAt = System.currentTimeMillis()
            )
            firebaseService.updateFeaturedConfigInCloud(updatedConfig)
            _catalogActionMessage.value = "Destaque removido com sucesso!"
        }
    }

    fun moveFeaturedItemByKey(uniqueKey: String, up: Boolean) {
        viewModelScope.launch {
            val current = featuredConfig.value
            val list = current.items.toMutableList()
            val index = list.indexOfFirst { it.uniqueKey == uniqueKey }
            if (index == -1) return@launch

            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex in list.indices) {
                val itemA = list[index]
                val itemB = list[targetIndex]
                list[index] = itemB
                list[targetIndex] = itemA

                val reordered = list.mapIndexed { idx, itm -> itm.copy(displayOrder = idx + 1) }

                // Update Room display orders
                reordered.forEach { itm ->
                    val feat = FeaturedMediaEntity(
                        id = 0,
                        mediaTmdbId = itm.tmdbId,
                        mediaType = itm.mediaType,
                        trailerUrl = itm.trailerUrl,
                        autoPlayTrailer = itm.autoPlayTrailer,
                        displayOrder = itm.displayOrder,
                        isActive = itm.isActive,
                        updatedAt = System.currentTimeMillis()
                    )
                    mediaRepository.saveFeaturedMedia(feat)
                }

                val updatedConfig = current.copy(items = reordered, lastUpdatedAt = System.currentTimeMillis())
                firebaseService.updateFeaturedConfigInCloud(updatedConfig)
                _catalogActionMessage.value = "Ordem atualizada com sucesso!"
            }
        }
    }

    fun toggleFeaturedItemActiveByKey(uniqueKey: String) {
        viewModelScope.launch {
            val current = featuredConfig.value
            val updatedItems = current.items.map { itm ->
                if (itm.uniqueKey == uniqueKey) itm.copy(isActive = !itm.isActive) else itm
            }
            val target = updatedItems.find { it.uniqueKey == uniqueKey }
            if (target != null) {
                mediaRepository.toggleFeaturedActive(id = 0, mediaTmdbId = target.tmdbId, isActive = target.isActive)
            }
            val updatedConfig = current.copy(items = updatedItems, lastUpdatedAt = System.currentTimeMillis())
            firebaseService.updateFeaturedConfigInCloud(updatedConfig)
            val st = if (target?.isActive == true) "ativado" else "desativado"
            _catalogActionMessage.value = "Destaque $st com sucesso!"
        }
    }

    fun addExcludedTmdbKey(uniqueKey: String) {
        viewModelScope.launch {
            val current = featuredConfig.value
            if (!current.excludedTmdbKeys.contains(uniqueKey)) {
                val updatedKeys = current.excludedTmdbKeys + uniqueKey
                // Also remove if currently in featured
                val updatedItems = current.items.filterNot { it.uniqueKey == uniqueKey }
                val updatedPreview = current.previewItems.filterNot { it.uniqueKey == uniqueKey }
                val updatedConfig = current.copy(
                    excludedTmdbKeys = updatedKeys,
                    items = updatedItems,
                    previewItems = updatedPreview,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                firebaseService.updateFeaturedConfigInCloud(updatedConfig)
                _catalogActionMessage.value = "Conteúdo adicionado à lista de exclusões!"
            }
        }
    }

    fun removeExcludedTmdbKey(uniqueKey: String) {
        viewModelScope.launch {
            val current = featuredConfig.value
            val updatedKeys = current.excludedTmdbKeys.filterNot { it == uniqueKey }
            val updatedConfig = current.copy(excludedTmdbKeys = updatedKeys, lastUpdatedAt = System.currentTimeMillis())
            firebaseService.updateFeaturedConfigInCloud(updatedConfig)
            _catalogActionMessage.value = "Exclusão removida com sucesso!"
        }
    }

    fun publishPreviewFeaturedItems() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val current = featuredConfig.value
            val preview = current.previewItems
            if (preview.isEmpty()) {
                _catalogActionMessage.value = "Nenhuma pré-visualização para publicar."
                return@launch
            }

            _isUpdatingFeatured.value = true
            _featuredUpdatingProgress.value = "Publicando destaques..."

            try {
                // Sync to Room FeaturedMediaEntity table
                preview.forEachIndexed { idx, itm ->
                    val feat = FeaturedMediaEntity(
                        id = 0,
                        mediaTmdbId = itm.tmdbId,
                        mediaType = itm.mediaType,
                        trailerUrl = itm.trailerUrl,
                        autoPlayTrailer = itm.autoPlayTrailer,
                        displayOrder = idx + 1,
                        isActive = itm.isActive,
                        updatedAt = System.currentTimeMillis()
                    )
                    mediaRepository.saveFeaturedMedia(feat)

                    // Ensure basic media entry exists
                    val inCatalog = allCatalogMedia.value.any { it.tmdbId == itm.tmdbId && it.mediaType == itm.mediaType }
                    if (!inCatalog) {
                        val media = MediaEntity(
                            tmdbId = itm.tmdbId,
                            title = itm.title,
                            originalTitle = itm.originalTitle,
                            mediaType = itm.mediaType,
                            posterPath = itm.posterPath,
                            backdropPath = itm.backdropPath,
                            overview = itm.overview,
                            releaseYear = itm.releaseYear.toString(),
                            rating = itm.rating,
                            genres = itm.genres,
                            trailerKey = itm.trailerUrl,
                            isHeroFeatured = true,
                            addedAt = System.currentTimeMillis()
                        )
                        mediaRepository.importMediaEntity(media)
                    }
                }

                // Register history
                val history = com.example.data.remote.FeaturedHistoryEntity(
                    id = "",
                    timestamp = System.currentTimeMillis(),
                    source = current.source,
                    mode = current.mode,
                    period = current.period,
                    count = preview.size,
                    itemsSummary = preview.take(5).map { it.title },
                    addedItemsCount = preview.size,
                    removedItemsCount = current.items.size,
                    triggeredBy = "admin"
                )
                firebaseService.addFeaturedHistoryInCloud(history)

                val updatedConfig = current.copy(
                    items = preview,
                    previewItems = emptyList(),
                    lastKnownGoodItems = preview,
                    lastUpdatedAt = System.currentTimeMillis(),
                    lastSyncStatus = "SUCCESS",
                    lastSyncError = ""
                )
                firebaseService.updateFeaturedConfigInCloud(updatedConfig)
                _catalogActionMessage.value = "✓ Destaques publicados com sucesso (${preview.size} itens)!"
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error publishing featured: ${e.message}")
                _catalogActionMessage.value = "Erro ao publicar: ${e.localizedMessage}"
            } finally {
                _isUpdatingFeatured.value = false
                _featuredUpdatingProgress.value = ""
            }
        }
    }

    fun fetchTmdbFeaturedAutomation(configOverride: com.example.data.remote.FeaturedConfigEntity? = null, forcePublish: Boolean = false) {
        if (_isUpdatingFeatured.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isUpdatingFeatured.value = true
            val cfg = configOverride ?: featuredConfig.value
            _featuredUpdatingProgress.value = "Conectando ao TMDB (${cfg.periodDisplay})..."

            try {
                val apiKey = com.example.BuildConfig.TMDB_API_KEY
                if (apiKey.isBlank()) {
                    throw IllegalStateException("Chave de API do TMDB não configurada!")
                }

                val tmdbApi = com.example.data.remote.TmdbNetwork.apiService
                delay(400)
                _featuredUpdatingProgress.value = "Buscando conteúdos TMDB..."

                val rawList = mutableListOf<com.example.data.remote.TmdbMediaDto>()

                when (cfg.mediaType) {
                    "movie" -> {
                        val resp = when (cfg.period) {
                            "today" -> tmdbApi.getTrendingMoviesDay(apiKey)
                            "week" -> tmdbApi.getTrendingMoviesWeek(apiKey)
                            "popular" -> tmdbApi.getPopularMovies(apiKey)
                            "top_rated" -> tmdbApi.getTopRatedMovies(apiKey)
                            "trending" -> tmdbApi.getTrendingMoviesDay(apiKey)
                            else -> tmdbApi.getTrendingMoviesDay(apiKey)
                        }
                        rawList.addAll(resp.results.map { it.copy(mediaType = "movie") })
                    }
                    "tv" -> {
                        val resp = when (cfg.period) {
                            "today" -> tmdbApi.getTrendingTvDay(apiKey)
                            "week" -> tmdbApi.getTrendingTvWeek(apiKey)
                            "popular" -> tmdbApi.getPopularSeries(apiKey)
                            "top_rated" -> tmdbApi.getTopRatedSeries(apiKey)
                            "trending" -> tmdbApi.getTrendingTvDay(apiKey)
                            else -> tmdbApi.getTrendingTvDay(apiKey)
                        }
                        rawList.addAll(resp.results.map { it.copy(mediaType = "tv") })
                    }
                    else -> { // "all" -> Filmes + Séries equilibrados
                        when (cfg.period) {
                            "today" -> {
                                val movies = tmdbApi.getTrendingMoviesDay(apiKey).results.map { it.copy(mediaType = "movie") }
                                val series = tmdbApi.getTrendingTvDay(apiKey).results.map { it.copy(mediaType = "tv") }
                                // Interleave movie and series
                                val max = maxOf(movies.size, series.size)
                                for (i in 0 until max) {
                                    if (i < movies.size) rawList.add(movies[i])
                                    if (i < series.size) rawList.add(series[i])
                                }
                            }
                            "week" -> {
                                val movies = tmdbApi.getTrendingMoviesWeek(apiKey).results.map { it.copy(mediaType = "movie") }
                                val series = tmdbApi.getTrendingTvWeek(apiKey).results.map { it.copy(mediaType = "tv") }
                                val max = maxOf(movies.size, series.size)
                                for (i in 0 until max) {
                                    if (i < movies.size) rawList.add(movies[i])
                                    if (i < series.size) rawList.add(series[i])
                                }
                            }
                            "popular" -> {
                                val movies = tmdbApi.getPopularMovies(apiKey).results.map { it.copy(mediaType = "movie") }
                                val series = tmdbApi.getPopularSeries(apiKey).results.map { it.copy(mediaType = "tv") }
                                val max = maxOf(movies.size, series.size)
                                for (i in 0 until max) {
                                    if (i < movies.size) rawList.add(movies[i])
                                    if (i < series.size) rawList.add(series[i])
                                }
                            }
                            "top_rated" -> {
                                val movies = tmdbApi.getTopRatedMovies(apiKey).results.map { it.copy(mediaType = "movie") }
                                val series = tmdbApi.getTopRatedSeries(apiKey).results.map { it.copy(mediaType = "tv") }
                                val max = maxOf(movies.size, series.size)
                                for (i in 0 until max) {
                                    if (i < movies.size) rawList.add(movies[i])
                                    if (i < series.size) rawList.add(series[i])
                                }
                            }
                            "trending" -> {
                                val trendingAll = tmdbApi.getTrending(apiKey).results
                                rawList.addAll(trendingAll)
                            }
                            else -> {
                                val trendingAll = tmdbApi.getTrending(apiKey).results
                                rawList.addAll(trendingAll)
                            }
                        }
                    }
                }

                _featuredUpdatingProgress.value = "Filtrando e validando itens..."
                delay(300)

                val excludedSet = cfg.excludedTmdbKeys.toSet()
                val catalog = database.playFilmeDao().getAllMediaSync()

                val candidates = mutableListOf<com.example.data.remote.FeaturedItemConfigEntity>()

                for (dto in rawList) {
                    val mType = dto.mediaType ?: if (dto.title != null) "movie" else "tv"
                    val key = "${dto.id}_$mType"

                    // Check exclusions
                    if (excludedSet.contains(key)) continue

                    // Check rating filter
                    val rating = dto.voteAverage ?: 0.0
                    if (cfg.minRating > 0.0 && rating < cfg.minRating) continue

                    // Check year filter
                    val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull() ?: 0
                    if (cfg.minYear > 0 && year < cfg.minYear) continue

                    val title = dto.title ?: dto.name ?: dto.originalTitle ?: dto.originalName ?: "Sem título"
                    val originalTitle = dto.originalTitle ?: dto.originalName ?: ""

                    // Fetch trailer key if needed
                    var trailerKey = ""
                    if (mType == "movie") {
                        try {
                            val vids = tmdbApi.getMovieVideos(dto.id, apiKey)
                            trailerKey = vids.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key ?: ""
                        } catch (_: Exception) {}
                    } else {
                        try {
                            val vids = tmdbApi.getSeriesVideos(dto.id, apiKey)
                            trailerKey = vids.results?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key ?: ""
                        } catch (_: Exception) {}
                    }

                    val posterUrl = dto.posterPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it" }
                    val backdropUrl = dto.backdropPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it" }

                    val itemConfig = com.example.data.remote.FeaturedItemConfigEntity(
                        id = "auto_${dto.id}_$mType",
                        tmdbId = dto.id,
                        mediaType = mType,
                        title = title,
                        originalTitle = originalTitle,
                        posterPath = posterUrl,
                        backdropPath = backdropUrl,
                        rating = rating,
                        releaseYear = year,
                        overview = dto.overview ?: "",
                        genres = "",
                        trailerUrl = trailerKey,
                        autoPlayTrailer = true,
                        displayOrder = candidates.size + 1,
                        isActive = true,
                        addedAt = System.currentTimeMillis()
                    )

                    if (candidates.none { it.uniqueKey == itemConfig.uniqueKey }) {
                        candidates.add(itemConfig)
                    }

                    if (candidates.size >= cfg.limit) break
                }

                if (candidates.isEmpty()) {
                    throw IllegalStateException("Nenhum conteúdo atendeu aos filtros selecionados no TMDB.")
                }

                // If auto-import is enabled, import to catalog
                if (cfg.autoImportToCatalog) {
                    _featuredUpdatingProgress.value = "Importando metadados no catálogo..."
                    candidates.forEach { cand ->
                        val existing = catalog.find { it.tmdbId == cand.tmdbId && it.mediaType == cand.mediaType }
                        if (existing == null) {
                            val media = MediaEntity(
                                tmdbId = cand.tmdbId,
                                title = cand.title,
                                originalTitle = cand.originalTitle,
                                mediaType = cand.mediaType,
                                posterPath = cand.posterPath,
                                backdropPath = cand.backdropPath,
                                overview = cand.overview,
                                releaseYear = cand.releaseYear.toString(),
                                rating = cand.rating,
                                genres = cand.genres,
                                trailerKey = cand.trailerUrl,
                                isHeroFeatured = true,
                                addedAt = System.currentTimeMillis()
                            )
                            database.playFilmeDao().insertMedia(media)
                        }
                    }
                }

                val shouldPublish = cfg.autoPublish || forcePublish

                if (shouldPublish) {
                    _featuredUpdatingProgress.value = "Salvando destaques ativos..."

                    // Update Room FeaturedMediaEntity table
                    candidates.forEachIndexed { idx, itm ->
                        val feat = FeaturedMediaEntity(
                            id = 0,
                            mediaTmdbId = itm.tmdbId,
                            mediaType = itm.mediaType,
                            trailerUrl = itm.trailerUrl,
                            autoPlayTrailer = itm.autoPlayTrailer,
                            displayOrder = idx + 1,
                            isActive = true,
                            updatedAt = System.currentTimeMillis()
                        )
                        mediaRepository.saveFeaturedMedia(feat)

                        // If not in catalog, also ensure minimal MediaEntity
                        val inCat = catalog.any { it.tmdbId == itm.tmdbId && it.mediaType == itm.mediaType }
                        if (!inCat) {
                            val media = MediaEntity(
                                tmdbId = itm.tmdbId,
                                title = itm.title,
                                originalTitle = itm.originalTitle,
                                mediaType = itm.mediaType,
                                posterPath = itm.posterPath,
                                backdropPath = itm.backdropPath,
                                overview = itm.overview,
                                releaseYear = itm.releaseYear.toString(),
                                rating = itm.rating,
                                genres = itm.genres,
                                trailerKey = itm.trailerUrl,
                                isHeroFeatured = true,
                                addedAt = System.currentTimeMillis()
                            )
                            mediaRepository.importMediaEntity(media)
                        }
                    }

                    // Register history
                    val hist = com.example.data.remote.FeaturedHistoryEntity(
                        id = "",
                        timestamp = System.currentTimeMillis(),
                        source = "TMDB",
                        mode = "automatic",
                        period = cfg.period,
                        count = candidates.size,
                        itemsSummary = candidates.take(5).map { it.title },
                        addedItemsCount = candidates.size,
                        removedItemsCount = cfg.items.size,
                        triggeredBy = "admin"
                    )
                    firebaseService.addFeaturedHistoryInCloud(hist)

                    val nextTime = if (cfg.autoUpdateIntervalHours > 0) {
                        System.currentTimeMillis() + (cfg.autoUpdateIntervalHours * 3600 * 1000L)
                    } else 0L

                    val updatedConfig = cfg.copy(
                        mode = "automatic",
                        source = "TMDB",
                        items = candidates,
                        previewItems = emptyList(),
                        lastKnownGoodItems = candidates,
                        lastUpdatedAt = System.currentTimeMillis(),
                        nextUpdateAt = nextTime,
                        lastSyncStatus = "SUCCESS",
                        lastSyncError = ""
                    )
                    firebaseService.updateFeaturedConfigInCloud(updatedConfig)
                    _catalogActionMessage.value = "✓ Destaques atualizados via TMDB (${candidates.size} conteúdos)!"
                } else {
                    // Save as PREVIEW for admin confirmation
                    val updatedConfig = cfg.copy(
                        previewItems = candidates,
                        lastUpdatedAt = System.currentTimeMillis(),
                        lastSyncStatus = "SUCCESS",
                        lastSyncError = ""
                    )
                    firebaseService.updateFeaturedConfigInCloud(updatedConfig)
                    _catalogActionMessage.value = "Pré-visualização gerada (${candidates.size} itens)! Clique em 'Publicar' para aplicar."
                }
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error in TMDB featured automation: ${e.message}")
                val current = featuredConfig.value
                val fallbackItems = if (current.items.isNotEmpty()) current.items else current.lastKnownGoodItems
                val updatedConfig = current.copy(
                    items = fallbackItems,
                    lastSyncStatus = "ERROR",
                    lastSyncError = e.localizedMessage ?: "Falha ao consultar TMDB",
                    lastUpdatedAt = System.currentTimeMillis()
                )
                firebaseService.updateFeaturedConfigInCloud(updatedConfig)
                _catalogActionMessage.value = "Erro TMDB: ${e.localizedMessage}. Destaques anteriores foram mantidos."
            } finally {
                delay(600)
                _isUpdatingFeatured.value = false
                _featuredUpdatingProgress.value = ""
            }
        }
    }

    fun saveFeaturedContent(
        id: Int = 0,
        mediaTmdbId: Int,
        mediaType: String,
        trailerUrl: String,
        autoPlayTrailer: Boolean,
        displayOrder: Int,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            val featured = FeaturedMediaEntity(
                id = id,
                mediaTmdbId = mediaTmdbId,
                mediaType = mediaType,
                trailerUrl = trailerUrl.trim(),
                autoPlayTrailer = autoPlayTrailer,
                displayOrder = displayOrder,
                isActive = isActive,
                updatedAt = System.currentTimeMillis()
            )
            mediaRepository.saveFeaturedMedia(featured)
            firebaseService.logAdminAction(
                adminEmail = com.example.data.remote.FirebaseService.ADMIN_EMAIL,
                action = if (id == 0) "ADD_FEATURED" else "UPDATE_FEATURED",
                target = "tmdbId:$mediaTmdbId",
                result = "Sucesso"
            )
            _catalogActionMessage.value = "Conteúdo em destaque salvo com sucesso!"
        }
    }

    fun toggleFeaturedActiveStatus(featuredItem: FeaturedMediaItem) {
        viewModelScope.launch {
            val newStatus = !featuredItem.featured.isActive
            mediaRepository.toggleFeaturedActive(
                id = featuredItem.featured.id,
                mediaTmdbId = featuredItem.featured.mediaTmdbId,
                isActive = newStatus
            )
            val statusText = if (newStatus) "ativado" else "desativado"
            _catalogActionMessage.value = "Destaque $statusText com sucesso!"
        }
    }

    fun removeFeaturedContent(featuredItem: FeaturedMediaItem) {
        viewModelScope.launch {
            mediaRepository.deleteFeaturedMedia(
                id = featuredItem.featured.id,
                mediaTmdbId = featuredItem.featured.mediaTmdbId
            )
            firebaseService.logAdminAction(
                adminEmail = com.example.data.remote.FirebaseService.ADMIN_EMAIL,
                action = "REMOVE_FEATURED",
                target = "tmdbId:${featuredItem.featured.mediaTmdbId}",
                result = "Sucesso"
            )
            _catalogActionMessage.value = "Conteúdo removido dos destaques!"
        }
    }

    fun moveFeaturedItem(featuredItem: FeaturedMediaItem, up: Boolean) {
        viewModelScope.launch {
            val currentList = allFeaturedItems.value.sortedBy { it.featured.displayOrder }
            val index = currentList.indexOfFirst { it.featured.id == featuredItem.featured.id }
            if (index == -1) return@launch

            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex in currentList.indices) {
                val targetItem = currentList[targetIndex]

                val orderTemp = featuredItem.featured.displayOrder
                val orderTarget = if (targetItem.featured.displayOrder == orderTemp) {
                    if (up) orderTemp - 1 else orderTemp + 1
                } else {
                    targetItem.featured.displayOrder
                }

                val updatedCurrent = featuredItem.featured.copy(
                    displayOrder = orderTarget,
                    updatedAt = System.currentTimeMillis()
                )
                val updatedTarget = targetItem.featured.copy(
                    displayOrder = orderTemp,
                    updatedAt = System.currentTimeMillis()
                )

                mediaRepository.saveFeaturedMedia(updatedCurrent)
                mediaRepository.saveFeaturedMedia(updatedTarget)
                _catalogActionMessage.value = "Ordem atualizada com sucesso!"
            }
        }
    }

    // --- CENTRALIZED SYNC & PUBLISH CONTROL ---
    fun forceGlobalSync() {
        if (_isCatalogActionRunning.value) {
            Log.d("AdminViewModel", "[SYNC] Sincronização já em andamento, ignorando chamada duplicada.")
            return
        }
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _catalogActionStage.value = "Conectando ao servidor e sincronizando..."
            try {
                val completed = kotlinx.coroutines.withTimeoutOrNull(25000L) {
                    firebaseService.forceRealtimeSyncCheck(database.playFilmeDao())
                    true
                } ?: false

                if (!completed) {
                    Log.w("AdminViewModel", "[SYNC] Timeout atingido durante sincronização global (25s).")
                    _catalogActionMessage.value = "⚠️ Sincronização concluída com resposta parcial do servidor."
                } else {
                    _catalogActionMessage.value = "⚡ Sincronização global concluída! Todos os dados atualizados."
                }
                loadStats()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "[SYNC] Erro na sincronização global: ${e.message}", e)
                _catalogActionMessage.value = "Não foi possível concluir a sincronização neste momento."
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
            }
        }
    }

    fun publishChangesToCloud() {
        if (_isCatalogActionRunning.value) return
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _catalogActionStage.value = "Publicando versão do catálogo no servidor..."
            try {
                val completed = kotlinx.coroutines.withTimeoutOrNull(20000L) {
                    firebaseService.publishAndBumpCatalogVersion()
                    true
                } ?: false

                if (!completed) {
                    _catalogActionMessage.value = "⚠️ Tempo limite excedido ao publicar no servidor."
                } else {
                    _catalogActionMessage.value = "🚀 Alterações publicadas no servidor! Nova versão transmitida em tempo real."
                }
                loadStats()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Erro ao publicar no servidor: ${e.message}", e)
                _catalogActionMessage.value = "Erro ao publicar no servidor: ${e.localizedMessage}"
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
            }
        }
    }

    // --- ADMIN NOTIFICATIONS SYSTEM ---
    val adminNotifications: StateFlow<List<NotificationEntity>> = mediaRepository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createAndSendNotification(
        title: String,
        message: String,
        imageUrl: String? = null,
        type: String = "AVISO",
        buttonText: String? = null,
        actionUrl: String? = null,
        targetSegment: String = "ALL",
        isScheduled: Boolean = false,
        scheduledAt: Long = 0L,
        isActive: Boolean = true
    ) {
        if (_isCatalogActionRunning.value) return
        viewModelScope.launch {
            _isCatalogActionRunning.value = true
            _catalogActionStage.value = "Enviando notificação em tempo real..."
            try {
                val id = "notif_${System.currentTimeMillis()}"
                val notif = NotificationEntity(
                    id = id,
                    title = title.ifBlank { "Notificação RONYCINE" },
                    message = message.ifBlank { "Nova mensagem disponível." },
                    imageUrl = imageUrl?.ifBlank { null },
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    scheduledAt = if (isScheduled) scheduledAt else 0L,
                    buttonText = buttonText?.ifBlank { null },
                    actionUrl = actionUrl?.ifBlank { null },
                    targetSegment = targetSegment,
                    isActive = isActive,
                    isRead = false,
                    isSent = !isScheduled
                )
                mediaRepository.sendNotification(notif)
                // Also register notification event in the cloud for backend push processing
                firebaseService.sendNotificationEventToCloud(
                    id = id,
                    title = notif.title,
                    message = notif.message,
                    imageUrl = notif.imageUrl,
                    type = notif.type,
                    actionUrl = notif.actionUrl,
                    targetSegment = notif.targetSegment,
                    buttonText = notif.buttonText
                )
                _catalogActionMessage.value = "🔔 Notificação enviada em tempo real para os dispositivos!"
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Erro ao enviar notificação: ${e.message}", e)
                _catalogActionMessage.value = "Erro ao enviar notificação: ${e.localizedMessage}"
            } finally {
                _isCatalogActionRunning.value = false
                _catalogActionStage.value = null
            }
        }
    }

    fun deleteNotificationAdmin(id: String) {
        viewModelScope.launch {
            mediaRepository.deleteNotification(id)
            _catalogActionMessage.value = "Notificação removida com sucesso!"
        }
    }

    fun loadAutoNotificationsConfig() {
        viewModelScope.launch {
            try {
                _autoNotificationsEnabled.value = firebaseService.getNotificationConfigAutoEnabled()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error loading auto notifications config: ${e.message}")
            }
        }
    }

    fun setAutoNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _autoNotificationsEnabled.value = enabled
                firebaseService.setNotificationConfigAutoEnabled(enabled)
                addAuditLog("Alterou config de notificações automáticas", if (enabled) "Ativado" else "Desativado")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error updating auto notifications config: ${e.message}")
            }
        }
    }

    // --- Backup & Restore States ---
    private var restoreJob: kotlinx.coroutines.Job? = null
    @Volatile private var isRestoreCancelled = false

    private val _restorePreviewInfo = MutableStateFlow<BackupPreviewInfo?>(null)
    val restorePreviewInfo: StateFlow<BackupPreviewInfo?> = _restorePreviewInfo.asStateFlow()

    private val _restoreRealProgress = MutableStateFlow<RestoreRealProgress?>(null)
    val restoreRealProgress: StateFlow<RestoreRealProgress?> = _restoreRealProgress.asStateFlow()

    // Backward compatibility for existing UI bindings
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress.asStateFlow()

    private val _restorePreview = MutableStateFlow<BackupData?>(null)
    val restorePreview: StateFlow<BackupData?> = _restorePreview.asStateFlow()

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = database.playFilmeDao().getAllBackupHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class BackupProgress(
        val type: String, // "EXPORT" or "RESTORE"
        val progress: Float = 0f,
        val message: String = "",
        val moviesCount: Int = 0,
        val moviesTotal: Int = 0,
        val seriesCount: Int = 0,
        val seriesTotal: Int = 0,
        val channelsCount: Int = 0,
        val channelsTotal: Int = 0,
        val isFinished: Boolean = false,
        val errors: Int = 0,
        val duplicates: Int = 0
    )

    fun exportBackupStreaming(uri: Uri) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress("EXPORT", 0.05f, "Iniciando exportação em streaming...")
            val success = BackupManager.exportBackupStreaming(
                context = getApplication(),
                uri = uri,
                dao = database.playFilmeDao(),
                firebaseService = firebaseService,
                onProgress = { p, msg ->
                    _backupProgress.value = BackupProgress("EXPORT", p, msg, isFinished = p >= 1.0f)
                }
            )
            if (success) {
                _importMessage.value = "Backup exportado com sucesso no dispositivo!"
                loadStats()
            } else {
                _backupProgress.value = BackupProgress("EXPORT", 1f, "Erro ao gerar arquivo de backup.", isFinished = true, errors = 1)
                _importMessage.value = "Erro ao exportar o backup."
            }
        }
    }

    fun prepareRestore(uri: Uri) {
        if (_restoreRealProgress.value?.status == RestoreStatus.RESTORING) {
            _importMessage.value = "Uma restauração já está em andamento."
            return
        }

        viewModelScope.launch {
            _isPreviewLoading.value = true
            _importMessage.value = null
            _restorePreviewInfo.value = null

            val preview = BackupManager.inspectBackupFile(getApplication(), uri)
            _isPreviewLoading.value = false
            _restorePreviewInfo.value = preview

            if (!preview.isValid) {
                _importMessage.value = preview.errorMessage ?: "Arquivo de backup inválido ou corrompido."
            }
        }
    }

    fun cancelRestorePreview() {
        _restorePreviewInfo.value = null
    }

    fun cancelActiveRestore() {
        isRestoreCancelled = true
        restoreJob?.cancel()
        _restoreRealProgress.value = _restoreRealProgress.value?.copy(
            status = RestoreStatus.CANCELLED,
            currentPhase = "Restauração cancelada pelo usuário."
        )
    }

    fun confirmRestore() {
        val preview = _restorePreviewInfo.value ?: return
        if (!preview.isValid) return

        if (_restoreRealProgress.value?.status == RestoreStatus.RESTORING) {
            _importMessage.value = "Uma restauração já está em andamento."
            return
        }

        isRestoreCancelled = false
        _restorePreviewInfo.value = null

        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            val result = BackupManager.restoreBackupStreaming(
                context = getApplication(),
                uri = preview.uri,
                previewInfo = preview,
                dao = database.playFilmeDao(),
                firebaseService = firebaseService,
                onProgress = { progress ->
                    _restoreRealProgress.value = progress
                },
                isCancelled = { isRestoreCancelled }
            )

            if (result.status == RestoreStatus.COMPLETED) {
                loadStats()
                // Atualiza busca e catálogo
                onSearchQueryChanged(_searchQuery.value)
            }
        }
    }

    fun dismissRestoreProgress() {
        _restoreRealProgress.value = null
        _backupProgress.value = null
    }

    fun clearBackupProgress() {
        _backupProgress.value = null
        _restoreRealProgress.value = null
    }

    // --- Media Requests Management ---
    val allMediaRequests: StateFlow<List<com.example.data.remote.MediaRequest>> = mediaRepository.allMediaRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _requestFilter = MutableStateFlow("TODOS") // "TODOS", "PENDENTE", "ADICIONADO", "IGNORADO"
    val requestFilter: StateFlow<String> = _requestFilter.asStateFlow()

    fun setRequestFilter(filter: String) {
        _requestFilter.value = filter
    }

    fun ignoreRequest(requestId: String) {
        viewModelScope.launch {
            mediaRepository.updateMediaRequestStatus(requestId, "IGNORADO")
            addAuditLog("Ignorou pedido de conteúdo", requestId)
        }
    }

    fun addRequestToCatalog(request: com.example.data.remote.MediaRequest) {
        viewModelScope.launch {
            // This will open the import preview with the request data
            selectTmdbIdForPreview(request.tmdbId.toString(), request.mediaType)
            _currentSection.value = AdminSection.IMPORTACAO
        }
    }

    fun markRequestAsAdded(requestId: String) {
        viewModelScope.launch {
            mediaRepository.updateMediaRequestStatus(requestId, "ADICIONADO")
        }
    }

    override fun onCleared() {
        super.onCleared()
        firebaseService.stopAdminListeners()
    }
}

