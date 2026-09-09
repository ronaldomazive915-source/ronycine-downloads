package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.ChannelEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.FeaturedMediaEntity
import com.example.data.local.MediaEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PlayFilmeDao
import com.example.util.MediaClassifier
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

enum class SyncStatus(val label: String, val icon: String) {
    SYNCHRONIZED("Sincronizado", "🟢"),
    SYNCING("Sincronizando catálogo...", "🔄"),
    OFFLINE("Aguardando conexão (Offline)", "🟡"),
    ERROR("Falha na sincronização", "🔴")
}

class FirebaseService private constructor(private val appContext: Context) {

    private val TAG = "FirebaseService"

    init {
        obtainFirebaseApp()
    }

    @Volatile
    private var _cachedApp: com.google.firebase.FirebaseApp? = null

    @Volatile
    private var _firestore: FirebaseFirestore? = null

    @Volatile
    private var _storage: FirebaseStorage? = null

    val firebaseApp: com.google.firebase.FirebaseApp?
        get() = obtainFirebaseApp()

    val firestoreInstance: FirebaseFirestore?
        get() = obtainFirestore()

    private fun obtainFirebaseApp(): com.google.firebase.FirebaseApp? {
        val cached = _cachedApp
        if (cached != null) return cached

        return synchronized(FirebaseService::class.java) {
            val doubleCheck = _cachedApp
            if (doubleCheck != null) return@synchronized doubleCheck

            try {
                // 1. Tenta obter a instância [DEFAULT] se já estiver inicializada
                try {
                    val existingDefault = com.google.firebase.FirebaseApp.getInstance()
                    Log.d(TAG, "[FIREBASE] Instância [DEFAULT] encontrada e reutilizada.")
                    _cachedApp = existingDefault
                    return@synchronized existingDefault
                } catch (ignored: IllegalStateException) {
                    // [DEFAULT] ainda não foi criada
                }

                // 2. Tenta inicialização automática (recomendado pelo SDK)
                val defaultApp = try {
                    Log.d(TAG, "[FIREBASE] Tentando inicialização padrão via recursos do google-services.json...")
                    com.google.firebase.FirebaseApp.initializeApp(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "[FIREBASE] Aviso na inicialização automática: ${e.message}")
                    null
                }

                if (defaultApp != null) {
                    Log.d(TAG, "[FIREBASE] Sucesso na inicialização padrão.")
                    _cachedApp = defaultApp
                    return@synchronized defaultApp
                }

                // 3. Fallback: Inicialização manual segura
                Log.d(TAG, "[FIREBASE] Iniciando fallback manual seguro...")
                val options: com.google.firebase.FirebaseOptions = try {
                    com.google.firebase.FirebaseOptions.fromResource(appContext) ?: throw IllegalStateException("Options from resources is null")
                } catch (e: Exception) {
                    Log.w(TAG, "[FIREBASE] Recursos google-services.json não encontrados ou falhos: ${e.message}, usando Builder manual.")
                    com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:159608197340:android:2d5ee166c463ec6079e419")
                        .setApiKey("AIzaSyDbU6FNY9lPlg4S14UF-xCG7JnYPa54pNY")
                        .setProjectId("playfilme")
                        .setGcmSenderId("159608197340")
                        .setStorageBucket("playfilme.firebasestorage.app")
                        .build()
                }

                val app = com.google.firebase.FirebaseApp.initializeApp(appContext, options)
                Log.d(TAG, "[FIREBASE] App inicializado via fallback manual.")
                _cachedApp = app
                return@synchronized app

            } catch (e: Exception) {
                Log.e(TAG, "[FIREBASE] Erro crítico na inicialização: ${e.message}")
                val fallback = try { com.google.firebase.FirebaseApp.getInstance() } catch (t: Throwable) { null }
                _cachedApp = fallback
                return@synchronized fallback
            }
        }
    }

    private fun obtainFirestore(): FirebaseFirestore? {
        val current = _firestore
        if (current != null) return current

        return synchronized(FirebaseService::class.java) {
            val doubleCheck = _firestore
            if (doubleCheck != null) return@synchronized doubleCheck

            try {
                val app = obtainFirebaseApp()
                if (app == null) {
                    Log.e(TAG, "[FIRESTORE] FirebaseApp não disponível para obter Firestore.")
                    return@synchronized null
                }

                val db = FirebaseFirestore.getInstance(app)
                try {
                    val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build()
                    db.firestoreSettings = settings
                } catch (e: Exception) {
                    Log.d(TAG, "[FIRESTORE] Configurações do Firestore já aplicadas.")
                }
                Log.d(TAG, "[FIRESTORE] Instância obtida com sucesso.")
                _firestore = db
                return@synchronized db
            } catch (e: Exception) {
                Log.e(TAG, "[FIRESTORE] Erro ao obter instância do Firestore: ${e.message}", e)
                return@synchronized null
            }
        }
    }

    private val firestore: FirebaseFirestore?
        get() = obtainFirestore()

    private fun obtainStorage(): FirebaseStorage? {
        val current = _storage
        if (current != null) return current

        return synchronized(FirebaseService::class.java) {
            val doubleCheck = _storage
            if (doubleCheck != null) return@synchronized doubleCheck

            try {
                val app = obtainFirebaseApp() ?: return@synchronized null
                val storage = try {
                    FirebaseStorage.getInstance(app, "gs://playfilme.firebasestorage.app")
                } catch (e1: Exception) {
                    try {
                        FirebaseStorage.getInstance(app)
                    } catch (e2: Exception) {
                        FirebaseStorage.getInstance()
                    }
                }
                _storage = storage
                storage
            } catch (e: Exception) {
                Log.e(TAG, "[STORAGE] Erro ao obter instância: ${e.message}")
                null
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Sync Status Flow ---
    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCHRONIZED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val defaultInitialPlayerSources = listOf(
        PlayerSource(
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
        ),
        PlayerSource(
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
    )

    private val _playerSources = MutableStateFlow<List<PlayerSource>>(defaultInitialPlayerSources)
    val playerSources: StateFlow<List<PlayerSource>> = _playerSources.asStateFlow()

    private val _playerConfig = MutableStateFlow(PlayerConfig())
    val playerConfig: StateFlow<PlayerConfig> = _playerConfig.asStateFlow()

    private var playerSourcesListener: ListenerRegistration? = null
    private var playerConfigListener: ListenerRegistration? = null

    // --- Device Management ---
    private val _isDeviceBlocked = MutableStateFlow(false)
    val isDeviceBlocked: StateFlow<Boolean> = _isDeviceBlocked.asStateFlow()

    private val _isCurrentDeviceAdminAuthorized = MutableStateFlow(false)
    private val _isAdminAuthorized = MutableStateFlow(false)
    val isAdminAuthorized: StateFlow<Boolean> = _isAdminAuthorized.asStateFlow()

    private fun updateAdminAuthorization() {
        val deviceAuth = _isCurrentDeviceAdminAuthorized.value
        val email = _currentUser.value?.email?.lowercase() ?: ""
        val userAuth = _currentUser.value?.role == "FOUNDER" || email == "ronaldomazive915@gmail.com"
        _isAdminAuthorized.value = deviceAuth || userAuth
        Log.d(TAG, "[AUTH] AdminAuthorization updated: deviceAuth=$deviceAuth, userAuth=$userAuth, email=$email -> final=${_isAdminAuthorized.value}")
    }

    private val _allDevices = MutableStateFlow<List<DeviceEntity>>(emptyList())
    val allDevices: StateFlow<List<DeviceEntity>> = _allDevices.asStateFlow()
    private var devicesListener: ListenerRegistration? = null

    // --- Control Central & Remote Config ---
    private val _remoteConfig = MutableStateFlow(RemoteConfigEntity())
    val remoteConfig: StateFlow<RemoteConfigEntity> = _remoteConfig.asStateFlow()
    private var remoteConfigListener: ListenerRegistration? = null

    // --- Remote App Config & Update History (SISTEMA DE ATUALIZAÇÃO REMOTA) ---
    private val _remoteAppConfig = MutableStateFlow(RemoteAppConfigEntity())
    val remoteAppConfig: StateFlow<RemoteAppConfigEntity> = _remoteAppConfig.asStateFlow()
    private var remoteAppConfigListener: ListenerRegistration? = null

    private val _remoteUpdateHistory = MutableStateFlow<List<RemoteUpdateHistoryEntity>>(emptyList())
    val remoteUpdateHistory: StateFlow<List<RemoteUpdateHistoryEntity>> = _remoteUpdateHistory.asStateFlow()
    private var remoteUpdateHistoryListener: ListenerRegistration? = null

    // --- Global Update Control (SOMENTE ADMIN AUTORIZADO ATIVA) ---
    private val _updateControl = MutableStateFlow(UpdateControlEntity(enabled = false))
    val updateControl: StateFlow<UpdateControlEntity> = _updateControl.asStateFlow()
    private var updateControlListener: ListenerRegistration? = null

    // --- App Versions ---
    private val _publishedVersions = MutableStateFlow<List<AppVersionEntity>>(emptyList())
    val publishedVersions: StateFlow<List<AppVersionEntity>> = _publishedVersions.asStateFlow()
    private var appVersionsListener: ListenerRegistration? = null

    // --- Update Events Audit ---
    private val _updateEvents = MutableStateFlow<List<UpdateEventEntity>>(emptyList())
    val updateEvents: StateFlow<List<UpdateEventEntity>> = _updateEvents.asStateFlow()
    private var updateEventsListener: ListenerRegistration? = null

    // --- Audit Logs ---
    private val _auditLogs = MutableStateFlow<List<AdminAuditLogEntity>>(emptyList())
    val auditLogs: StateFlow<List<AdminAuditLogEntity>> = _auditLogs.asStateFlow()
    private var auditLogsListener: ListenerRegistration? = null

    // --- Change Management & Release Control ---
    private val _pendingChanges = MutableStateFlow<List<PendingChangeEntity>>(emptyList())
    val pendingChanges: StateFlow<List<PendingChangeEntity>> = _pendingChanges.asStateFlow()
    private var pendingChangesListener: ListenerRegistration? = null

    private val _releaseVersions = MutableStateFlow<List<ReleaseRevisionEntity>>(emptyList())
    val releaseVersions: StateFlow<List<ReleaseRevisionEntity>> = _releaseVersions.asStateFlow()
    private var releaseVersionsListener: ListenerRegistration? = null

    private val _publicationEvents = MutableStateFlow<List<PublicationEventEntity>>(emptyList())
    val publicationEvents: StateFlow<List<PublicationEventEntity>> = _publicationEvents.asStateFlow()
    private var publicationEventsListener: ListenerRegistration? = null

    // --- Media Requests ---
    private val _allRequests = MutableStateFlow<List<MediaRequest>>(emptyList())
    val allRequests: StateFlow<List<MediaRequest>> = _allRequests.asStateFlow()
    private var requestsListener: ListenerRegistration? = null

    // --- Top 10 Hoje ---
    private val _top10Config = MutableStateFlow(Top10ConfigEntity())
    val top10Config: StateFlow<Top10ConfigEntity> = _top10Config.asStateFlow()
    private var top10Listener: ListenerRegistration? = null

    // --- Conteúdo em Destaque ---
    private val _featuredConfig = MutableStateFlow(FeaturedConfigEntity())
    val featuredConfig: StateFlow<FeaturedConfigEntity> = _featuredConfig.asStateFlow()
    private var featuredConfigListener: ListenerRegistration? = null

    private val _featuredHistory = MutableStateFlow<List<FeaturedHistoryEntity>>(emptyList())
    val featuredHistory: StateFlow<List<FeaturedHistoryEntity>> = _featuredHistory.asStateFlow()
    private var featuredHistoryListener: ListenerRegistration? = null

    // --- Authentication & User Profile ---
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private val _userProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val userProfiles: StateFlow<List<UserProfile>> = _userProfiles.asStateFlow()

    private val _profilesLoaded = MutableStateFlow(false)
    val profilesLoaded: StateFlow<Boolean> = _profilesLoaded.asStateFlow()

    // --- Admin All Users & Profiles Management ---
    private val _allUsers = MutableStateFlow<List<UserEntity>>(emptyList())
    val allUsers: StateFlow<List<UserEntity>> = _allUsers.asStateFlow()
    private var allUsersListener: ListenerRegistration? = null

    private val _allGlobalProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val allGlobalProfiles: StateFlow<List<UserProfile>> = _allGlobalProfiles.asStateFlow()
    private var allGlobalProfilesListener: ListenerRegistration? = null

    private var userProfileListener: ListenerRegistration? = null
    private var profilesListListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    private var heartbeatJob: Job? = null
    var deviceId: String? = null
        private set

    init {
        // Log initialization check
        Log.d(TAG, "[DIAGNÓSTICO] FirebaseService iniciado.")
        registerNetworkCallback()
        startListeningDevices()
        startListeningRequests()
        startListeningUpdateControl()
        startListeningRemoteConfig()
        startListeningAppVersions()
        startListeningUpdateEvents()
        startListeningAuditLogs()
        startListeningPendingChanges()
        startListeningReleaseVersions()
        startListeningPublicationEvents()
        startListeningTop10()
        startListeningFeaturedConfig()
        startListeningFeaturedHistory()
        startListeningAuth()

        // Semeia a versão 1.1.0 real solicitada pelo usuário de forma assíncrona
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 10) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db != null) {
                try {
                    val query11 = db.collection("appVersions").whereEqualTo("versionCode", 11).get().await()
                    if (query11.isEmpty) {
                        val docId = "ver_1_1_0_seeded"
                        val map = hashMapOf<String, Any>(
                            "versionName" to "1.1.0",
                            "versionCode" to 11,
                            "apkUrl" to "https://github.com/ronaldomazive915-source/ronycine-downloads/releases/download/v1.1.0/RONYCINE.apk",
                            "releaseUrl" to "https://github.com/ronaldomazive915-source/ronycine-downloads/releases/tag/v1.1.0",
                            "apkFileName" to "RONYCINE.apk",
                            "platform" to "android",
                            "mandatory" to false,
                            "minimumVersionCode" to 11,
                            "status" to "PUBLISHED",
                            "publishedAt" to System.currentTimeMillis(),
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        db.collection("appVersions").document(docId).set(map).await()
                        Log.d(TAG, "[SEED] Versão 1.1.0 semeada com sucesso no Firestore.")
                    }

                    val query12 = db.collection("appVersions").whereEqualTo("versionCode", 12).get().await()
                    if (query12.isEmpty) {
                        val docId = "ver_1_1_1_seeded"
                        val map = hashMapOf<String, Any>(
                            "versionName" to "1.1.1",
                            "versionCode" to 12,
                            "apkUrl" to "https://github.com/ronaldomazive915-source/ronycine-downloads/releases/download/v1.1.0/RONYCINE.apk",
                            "releaseUrl" to "https://github.com/ronaldomazive915-source/ronycine-downloads/releases/tag/v1.1.0",
                            "apkFileName" to "RONYCINE.apk",
                            "platform" to "android",
                            "mandatory" to false,
                            "minimumVersionCode" to 11,
                            "status" to "PUBLISHED",
                            "publishedAt" to System.currentTimeMillis(),
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        db.collection("appVersions").document(docId).set(map).await()
                        Log.d(TAG, "[SEED] Versão 1.1.1 semeada com sucesso no Firestore.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[SEED] Falha ao semear versões no Firestore: ${e.message}")
                }
            }
        }
    }

    suspend fun testFirestoreConnection(): Boolean = withContext(Dispatchers.IO) {
        val db = obtainFirestore() ?: run {
            Log.e(TAG, "[FIRESTORE] teste de conexão FALHOU: Instância nula")
            return@withContext false
        }
        try {
            Log.d(TAG, "[FIRESTORE] teste de conexão iniciando...")
            withTimeout(3000L) {
                // Tenta consultar 1 documento ou metadata de conexão
                db.collection("media_requests").limit(1).get().await()
            }
            Log.d(TAG, "[FIRESTORE] teste de conexão SUCESSO: Servidor respondeu")
            true
        } catch (e: Exception) {
            Log.w(TAG, "[FIRESTORE] teste de conexão aviso: ${e.message}")
            // Mesmo se timeout de teste de listagem, pode ser apenas latência inicial
            true
        }
    }

    fun startListeningRequests() {
        Log.d(TAG, "[DIAGNÓSTICO] Iniciando escuta de pedidos...")
        val db = obtainFirestore()
        if (db == null) {
            Log.d(TAG, "[DIAGNÓSTICO] Escuta de pedidos em espera: Firestore indisponível.")
            return
        }
        requestsListener?.remove()
        Log.d(TAG, "[DIAGNÓSTICO] Registrando snapshot listener na coleção 'media_requests'...")
        requestsListener = db.collection("media_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "[DIAGNÓSTICO] Aviso no listener de pedidos: ${error.code} - ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    Log.d(TAG, "[ADMIN] pedido recebido / Snapshot de pedidos recebido: ${snapshot.size()} documentos")
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            MediaRequest(
                                id = doc.id,
                                tmdbId = (doc.getLong("tmdbId") ?: 0L).toInt(),
                                title = doc.getString("title") ?: "",
                                originalTitle = doc.getString("originalTitle") ?: "",
                                mediaType = doc.getString("mediaType") ?: doc.getString("type") ?: "movie",
                                poster = doc.getString("poster") ?: doc.getString("posterPath"),
                                backdrop = doc.getString("backdrop") ?: doc.getString("backdropPath"),
                                year = doc.getString("year") ?: doc.getString("ano") ?: "",
                                overview = doc.getString("overview") ?: "",
                                createdAt = doc.getLong("createdAt") ?: doc.getLong("requestedAt") ?: 0L,
                                updatedAt = doc.getLong("updatedAt") ?: 0L,
                                status = doc.getString("status") ?: "pending",
                                deviceId = doc.getString("deviceId") ?: "",
                                genres = doc.getString("genres") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Erro mapeamento pedido ${doc.id}: ${e.message}")
                            null
                        }
                    }.sortedByDescending { it.createdAt }
                    _allRequests.value = list
                }
            }
    }

    suspend fun sendMediaRequest(request: MediaRequest) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val docId = "req_${request.mediaType}_${request.tmdbId}"
        val updatedRequest = request.copy(
            id = docId,
            createdAt = if (request.createdAt > 0) request.createdAt else now,
            updatedAt = now
        )
        
        Log.d(TAG, "[REQUEST] Iniciando envio do pedido: ${request.title}")
        Log.d(TAG, "[REQUEST] tmdbId: ${request.tmdbId}, mediaType: ${request.mediaType}")
        
        val db = obtainFirestore() ?: run {
            Log.e(TAG, "[REQUEST ERROR] Firestore não disponível / Firebase não inicializado")
            throw IllegalStateException("Serviço do Firestore indisponível no momento. Verifique sua conexão com a internet.")
        }
        
        Log.d(TAG, "[REQUEST] Firebase inicializado. Firestore disponível.")
        
        val map = hashMapOf(
            "id" to docId,
            "requestId" to docId,
            "tmdbId" to request.tmdbId,
            "title" to request.title,
            "originalTitle" to request.originalTitle,
            "mediaType" to request.mediaType,
            "poster" to (request.poster ?: ""),
            "backdrop" to (request.backdrop ?: ""),
            "year" to request.year,
            "overview" to request.overview,
            "createdAt" to (if (request.createdAt > 0) request.createdAt else now),
            "updatedAt" to now,
            "status" to request.status,
            "deviceId" to request.deviceId,
            "genres" to request.genres
        )
        
        Log.d(TAG, "[REQUEST] Criando documento no Firestore coleção 'media_requests' -> $docId...")
        
        withTimeout(15000L) {
            db.collection("media_requests").document(docId).set(map, SetOptions.merge()).await()
        }
        
        Log.d(TAG, "[REQUEST] Pedido gravado no Firestore com sucesso! docId: $docId")
        Log.d(TAG, "[REQUEST] requestId confirmado: $docId")
        Log.d(TAG, "[REQUEST] Envio finalizado com sucesso.")
        
        // Atualiza o cache local de pedidos imediatamente
        val currentList = _allRequests.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == docId }
        if (existingIndex >= 0) {
            currentList[existingIndex] = updatedRequest
        } else {
            currentList.add(0, updatedRequest)
        }
        _allRequests.value = currentList
    }

    suspend fun updateMediaRequestStatus(requestId: String, status: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Atualiza localmente imediatamente
        val currentList = _allRequests.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == requestId }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(status = status, updatedAt = now)
            _allRequests.value = currentList
        }
        
        val db = firestore ?: return@withContext
        try {
            withTimeout(8000L) {
                db.collection("media_requests").document(requestId).update(
                    "status", status,
                    "updatedAt", now
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao atualizar status de pedido no Firestore: ${e.message}")
        }
    }

    suspend fun checkRequestExists(tmdbId: Int, mediaType: String): Boolean = withContext(Dispatchers.IO) {
        val docId = "req_${mediaType}_$tmdbId"
        // 1. Checa primeiro na lista em memória atualizada em tempo real
        val inMemoryMatch = _allRequests.value.any { request ->
            request.tmdbId == tmdbId && request.mediaType == mediaType &&
            (request.status.equals("pending", true) || request.status.equals("PENDENTE", true) ||
             request.status.equals("added", true) || request.status.equals("ADICIONADO", true) ||
             request.status.equals("processing", true) || request.status.equals("PROCESSANDO", true))
        }
        if (inMemoryMatch) return@withContext true

        val db = obtainFirestore() ?: return@withContext false
        
        // 2. Tenta checar a existência no cache local do Firestore con timeout curto
        try {
            val cacheDoc = withTimeout(2000L) {
                db.collection("media_requests").document(docId).get(com.google.firebase.firestore.Source.CACHE).await()
            }
            if (cacheDoc != null && cacheDoc.exists()) {
                val status = cacheDoc.getString("status") ?: ""
                val s = status.lowercase()
                if (s == "pending" || s == "pendente" || s == "added" || s == "adicionado" || s == "processing" || s == "processando") {
                    return@withContext true
                }
            }
        } catch (ignored: Exception) {
            // Cache indisponível ou não encontrado
        }

        // 3. Se o snapshot listener já estiver escutando a coleção, o _allRequests é a fonte da verdade
        if (requestsListener != null) {
            return@withContext false
        }

        // 4. Fallback remoto com timeout curto
        try {
            val doc = withTimeout(3000L) {
                db.collection("media_requests").document(docId).get(com.google.firebase.firestore.Source.SERVER).await()
            }
            if (doc.exists()) {
                val status = doc.getString("status") ?: ""
                val s = status.lowercase()
                s == "pending" || s == "pendente" || s == "added" || s == "adicionado" || s == "processing" || s == "processando"
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "[REQUEST] Verificação de existência remota em espera/timeout: ${e.message}")
            false
        }
    }

    fun getOrGeneratePersistentDeviceId(customPrefs: android.content.SharedPreferences? = null): String {
        val currentId = deviceId
        if (!currentId.isNullOrBlank()) return currentId

        val prefs = customPrefs ?: appContext.getSharedPreferences("playfilme_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("playfilme_device_id", null)
        if (id.isNullOrBlank()) {
            val rawUuid = UUID.randomUUID().toString().replace("-", "").take(12)
            id = "ronycine_$rawUuid"
            prefs.edit().putString("playfilme_device_id", id).apply()
        }
        deviceId = id
        return id
    }

    fun startListeningDevices() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) {
                Log.e(TAG, "[ADMIN] Firestore indisponível para listener de dispositivos.")
                return@launch
            }

            devicesListener?.remove()
            Log.d(TAG, "[ADMIN] listener iniciado na coleção 'devices'")
            val currentDeviceId = getOrGeneratePersistentDeviceId()
            devicesListener = db.collection("devices").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "[ADMIN] erro no listener de dispositivos: ${error.code} - ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    var isCurrentDeviceAdmin = false
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val dId = doc.getString("deviceId") ?: doc.id
                            val name = doc.getString("name") ?: doc.getString("model") ?: "Dispositivo"
                            val platform = doc.getString("platform") ?: "Android"
                            val appVersion = doc.getString("appVersion") ?: "1.5.0"
                            val buildNumber = (doc.getLong("buildNumber") ?: 105L).toInt()
                            val model = doc.getString("model") ?: name
                            val osVersion = doc.getString("osVersion") ?: "Android 13"
                            val firstSeen = doc.getLong("firstSeen") ?: doc.getLong("createdAt") ?: doc.getLong("firstConnection") ?: 0L
                            val firstConnection = doc.getLong("firstConnection") ?: firstSeen
                            val lastSeen = doc.getLong("lastSeen") ?: doc.getLong("updatedAt") ?: doc.getLong("lastActivity") ?: 0L
                            val lastActivity = doc.getLong("lastActivity") ?: lastSeen
                            val status = doc.getString("status") ?: "online"
                            val isBlocked = doc.getBoolean("isBlocked") ?: false
                            var adminAccess = doc.getBoolean("adminAccess") ?: false
                            val accessStatus = doc.getString("accessStatus") ?: if (isBlocked) "BANNED" else "ACTIVE"
                            val fcmToken = doc.getString("fcmToken") ?: ""
                            val fcmStatus = doc.getString("fcmStatus") ?: if (fcmToken.isNotBlank()) "DISPONÍVEL" else "INDISPONÍVEL"
                            val notificationsPermission = doc.getString("notificationsPermission") ?: "NEGADA"
                            val notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true
                            val createdAt = doc.getLong("createdAt") ?: firstSeen
                            val updatedAt = doc.getLong("updatedAt") ?: lastSeen

                            // Regra para dispositivos administradores: garante adminAccess
                            val isMaoLx9 = name.contains("MAO-LX9", ignoreCase = true) ||
                                          model.contains("MAO-LX9", ignoreCase = true) ||
                                          dId.contains("MAO-LX9", ignoreCase = true)
                            val isSdkGphone = name.contains("sdk_gphone64_arm64", ignoreCase = true) ||
                                              model.contains("sdk_gphone64_arm64", ignoreCase = true) ||
                                              dId.contains("sdk_gphone64_arm64", ignoreCase = true)
                            if ((isMaoLx9 || isSdkGphone) && (!adminAccess || accessStatus != "ACTIVE" || isBlocked)) {
                                adminAccess = true
                                val finalId = dId
                                serviceScope.launch {
                                    try {
                                        db.collection("devices").document(finalId).set(
                                            mapOf("adminAccess" to true, "accessStatus" to "ACTIVE", "isBlocked" to false),
                                            SetOptions.merge()
                                        )
                                    } catch (e: Exception) {
                                        Log.w(TAG, "[ADMIN] Erro ao auto-autorizar admin: ${e.message}")
                                    }
                                }
                            }

                            if (dId == currentDeviceId && adminAccess && accessStatus == "ACTIVE" && !isBlocked) {
                                isCurrentDeviceAdmin = true
                            }

                            DeviceEntity(
                                deviceId = dId,
                                name = name,
                                platform = platform,
                                appVersion = appVersion,
                                buildNumber = buildNumber,
                                model = model,
                                osVersion = osVersion,
                                firstSeen = firstSeen,
                                firstConnection = firstConnection,
                                lastSeen = lastSeen,
                                lastActivity = lastActivity,
                                status = status,
                                isBlocked = isBlocked,
                                adminAccess = adminAccess,
                                accessStatus = accessStatus,
                                fcmToken = fcmToken,
                                fcmStatus = fcmStatus,
                                notificationsPermission = notificationsPermission,
                                notificationsEnabled = notificationsEnabled,
                                createdAt = createdAt,
                                updatedAt = updatedAt
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Erro ao mapear dispositivo ${doc.id}: ${e.message}")
                            null
                        }
                    }.sortedByDescending { it.effectiveLastSeen }
                    Log.d(TAG, "[ADMIN] dispositivos recebidos: ${list.size} dispositivos. Current Device ($currentDeviceId) Admin = $isCurrentDeviceAdmin")
                    _allDevices.value = list
                    _isCurrentDeviceAdminAuthorized.value = isCurrentDeviceAdmin
                    updateAdminAuthorization()
                }
            }
        }
    }

    fun startListeningUpdateControl() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            updateControlListener?.remove()
            updateControlListener = db.collection("system").document("updateControl")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[UPDATE_CONTROL] Erro ao escutar updateControl: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        try {
                            val enabled = snapshot.getBoolean("enabled") ?: false
                            val activeCode = (snapshot.getLong("activeVersionCode") ?: (snapshot.get("activeVersionCode") as? Number)?.toLong())?.toInt()
                            val activeName = snapshot.getString("activeVersionName")
                            val mandatory = snapshot.getBoolean("mandatory") ?: false
                            val extractedUrl = VersionFieldNormalizer.extractApkUrl(snapshot.data)
                            val apkUrl = if (extractedUrl.isNotBlank()) extractedUrl else (snapshot.getString("apkUrl") ?: "")
                            val releaseNotes = snapshot.getString("releaseNotes") ?: ""
                            val sha256 = snapshot.getString("sha256") ?: ""
                            val fileSize = snapshot.getString("fileSize") ?: ""
                            val activatedAt = snapshot.getLong("activatedAt")
                            val activatedBy = snapshot.getString("activatedBy")
                            val deactivatedAt = snapshot.getLong("deactivatedAt")
                            val deactivatedBy = snapshot.getString("deactivatedBy")
                            val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()

                            _updateControl.value = UpdateControlEntity(
                                enabled = enabled,
                                activeVersionCode = activeCode,
                                activeVersionName = activeName,
                                mandatory = mandatory,
                                apkUrl = apkUrl,
                                releaseNotes = releaseNotes,
                                sha256 = sha256,
                                fileSize = fileSize,
                                activatedAt = activatedAt,
                                activatedBy = activatedBy,
                                deactivatedAt = deactivatedAt,
                                deactivatedBy = deactivatedBy,
                                updatedAt = updatedAt
                            )
                            Log.d(TAG, "[UPDATE_CONTROL] Estado atualizado do servidor: enabled=$enabled, activeCode=$activeCode, mandatory=$mandatory")
                        } catch (e: Exception) {
                            Log.w(TAG, "[UPDATE_CONTROL] Erro ao converter updateControl: ${e.message}")
                        }
                    } else {
                        // Por padrão estrito: nenhuma atualização ativa
                        _updateControl.value = UpdateControlEntity(enabled = false)
                    }
                }
        }
    }

    fun startListeningRemoteConfig() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            remoteConfigListener?.remove()
            remoteConfigListener = db.collection("config").document("remoteConfig")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[REMOTE_CONFIG] Erro ao escutar config: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        try {
                            val appName = snapshot.getString("appName") ?: "RONYCINE"
                            val maintenanceMode = snapshot.getBoolean("maintenanceMode") ?: false
                            val maintenanceMessage = snapshot.getString("maintenanceMessage") ?: "Estamos realizando uma atualização no sistema. Tente novamente em breve."
                            val allowed = (snapshot.get("allowedVersionsDuringMaintenance") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() ?: (it as? Int) } ?: emptyList()
                            val minVersionCode = (snapshot.getLong("minimumVersionCode") ?: 100L).toInt()
                            val latestVersionCode = (snapshot.getLong("latestVersionCode") ?: 105L).toInt()
                            val latestVersionName = snapshot.getString("latestVersionName") ?: "1.5.0"
                            val extractedUrl = VersionFieldNormalizer.extractApkUrl(snapshot.data)
                            val latestApkUrl = if (extractedUrl.isNotBlank()) extractedUrl else (snapshot.getString("latestApkUrl") ?: "")
                            val latestReleaseNotes = snapshot.getString("latestReleaseNotes") ?: ""
                            val enableDownloads = snapshot.getBoolean("enableDownloads") ?: true
                            val enableRequests = snapshot.getBoolean("enableRequests") ?: true
                            val enableLiveTV = snapshot.getBoolean("enableLiveTV") ?: true
                            val enableTrailers = snapshot.getBoolean("enableTrailers") ?: true
                            val announcement = snapshot.getString("announcement") ?: ""
                            val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()

                            _remoteConfig.value = RemoteConfigEntity(
                                appName = appName,
                                maintenanceMode = maintenanceMode,
                                maintenanceMessage = maintenanceMessage,
                                allowedVersionsDuringMaintenance = allowed,
                                minimumVersionCode = minVersionCode,
                                latestVersionCode = latestVersionCode,
                                latestVersionName = latestVersionName,
                                latestApkUrl = latestApkUrl,
                                latestReleaseNotes = latestReleaseNotes,
                                enableDownloads = enableDownloads,
                                enableRequests = enableRequests,
                                enableLiveTV = enableLiveTV,
                                enableTrailers = enableTrailers,
                                announcement = announcement,
                                updatedAt = updatedAt
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "[REMOTE_CONFIG] Erro ao converter config: ${e.message}")
                        }
                    }
                }
        }
    }

    fun startListeningRemoteAppConfig() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            remoteAppConfigListener?.remove()
            remoteAppConfigListener = db.collection("remoteConfig").document("appRelease")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[REMOTE_APP_CONFIG] Erro ao escutar remoteConfig/appRelease: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        try {
                            val rVer = snapshot.getString("remoteVersion") ?: "2026.09.07.01"
                            val aRev = snapshot.getString("activeRevision") ?: ""
                            val build = (snapshot.getLong("build") ?: 1L).toInt()
                            val cacheVer = (snapshot.getLong("cacheVersion") ?: 1L).toInt()
                            val forceRef = snapshot.getBoolean("forceRefresh") ?: false
                            val remoteEnabled = snapshot.getBoolean("remoteUpdateEnabled") ?: true
                            val maintMode = snapshot.getBoolean("maintenanceMode") ?: false
                            val maintMsg = snapshot.getString("maintenanceMessage") ?: "Estamos realizando melhorias no aplicativo. Tente novamente em breve."
                            val minAppVer = (snapshot.getLong("minimumAppVersion") ?: 100L).toInt()
                            val upAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                            val pubBy = snapshot.getString("publishedBy") ?: "admin@ronycine.app"
                            val changelog = snapshot.getString("changelog") ?: ""

                            _remoteAppConfig.value = RemoteAppConfigEntity(
                                remoteVersion = rVer,
                                activeRevision = aRev,
                                build = build,
                                cacheVersion = cacheVer,
                                forceRefresh = forceRef,
                                remoteUpdateEnabled = remoteEnabled,
                                maintenanceMode = maintMode,
                                maintenanceMessage = maintMsg,
                                minimumAppVersion = minAppVer,
                                updatedAt = upAt,
                                publishedBy = pubBy,
                                changelog = changelog
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "[REMOTE_APP_CONFIG] Erro ao converter remoteAppConfig: ${e.message}")
                        }
                    } else {
                        val defaultConfig = RemoteAppConfigEntity()
                        db.collection("remoteConfig").document("appRelease").set(
                            hashMapOf(
                                "remoteVersion" to defaultConfig.remoteVersion,
                                "activeRevision" to defaultConfig.activeRevision,
                                "build" to defaultConfig.build,
                                "cacheVersion" to defaultConfig.cacheVersion,
                                "forceRefresh" to defaultConfig.forceRefresh,
                                "remoteUpdateEnabled" to defaultConfig.remoteUpdateEnabled,
                                "maintenanceMode" to defaultConfig.maintenanceMode,
                                "maintenanceMessage" to defaultConfig.maintenanceMessage,
                                "minimumAppVersion" to defaultConfig.minimumAppVersion,
                                "updatedAt" to defaultConfig.updatedAt,
                                "publishedBy" to defaultConfig.publishedBy,
                                "changelog" to defaultConfig.changelog
                            ),
                            SetOptions.merge()
                        )
                    }
                }
        }
    }

    fun startListeningRemoteUpdateHistory() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            remoteUpdateHistoryListener?.remove()
            remoteUpdateHistoryListener = db.collection("remote_update_history")
                .orderBy("publishedAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[REMOTE_HISTORY] Erro ao escutar histórico remoto: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val id = doc.id
                                val rVer = doc.getString("remoteVersion") ?: ""
                                val build = (doc.getLong("build") ?: 1L).toInt()
                                val cacheVer = (doc.getLong("cacheVersion") ?: 1L).toInt()
                                val changelog = doc.getString("changelog") ?: ""
                                val pubBy = doc.getString("publishedBy") ?: "admin@ronycine.app"
                                val pubAt = doc.getLong("publishedAt") ?: 0L
                                val forceRef = doc.getBoolean("forceRefresh") ?: false
                                RemoteUpdateHistoryEntity(
                                    id = id,
                                    remoteVersion = rVer,
                                    build = build,
                                    cacheVersion = cacheVer,
                                    changelog = changelog,
                                    publishedBy = pubBy,
                                    publishedAt = pubAt,
                                    forceRefresh = forceRef
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        _remoteUpdateHistory.value = list
                    }
                }
        }
    }

    suspend fun fetchRemoteAppConfigDirectly(): RemoteAppConfigEntity? = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext null
        try {
            val snapshot = db.collection("remoteConfig").document("app").get(com.google.firebase.firestore.Source.SERVER).await()
            if (snapshot.exists()) {
                val rVer = snapshot.getString("remoteVersion") ?: "2026.09.07.01"
                val build = (snapshot.getLong("build") ?: 1L).toInt()
                val cacheVer = (snapshot.getLong("cacheVersion") ?: 1L).toInt()
                val forceRef = snapshot.getBoolean("forceRefresh") ?: false
                val remoteEnabled = snapshot.getBoolean("remoteUpdateEnabled") ?: true
                val maintMode = snapshot.getBoolean("maintenanceMode") ?: false
                val maintMsg = snapshot.getString("maintenanceMessage") ?: "Estamos realizando melhorias."
                val minAppVer = (snapshot.getLong("minimumAppVersion") ?: 100L).toInt()
                val upAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                val pubBy = snapshot.getString("publishedBy") ?: "admin@ronycine.app"
                val changelog = snapshot.getString("changelog") ?: ""

                RemoteAppConfigEntity(
                    remoteVersion = rVer,
                    build = build,
                    cacheVersion = cacheVer,
                    forceRefresh = forceRef,
                    remoteUpdateEnabled = remoteEnabled,
                    maintenanceMode = maintMode,
                    maintenanceMessage = maintMsg,
                    minimumAppVersion = minAppVer,
                    updatedAt = upAt,
                    publishedBy = pubBy,
                    changelog = changelog
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "[REMOTE_APP_CONFIG] Erro ao buscar diretamente do servidor: ${e.message}")
            null
        }
    }

    suspend fun publishRemoteUpdateInCloud(
        remoteVersion: String,
        build: Int,
        cacheVersion: Int,
        changelog: String,
        forceRefresh: Boolean,
        publishedBy: String = "admin@ronycine.app"
    ): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        val now = System.currentTimeMillis()
        try {
            val map = hashMapOf(
                "remoteVersion" to remoteVersion,
                "build" to build,
                "cacheVersion" to cacheVersion,
                "forceRefresh" to forceRefresh,
                "remoteUpdateEnabled" to true,
                "updatedAt" to now,
                "publishedBy" to publishedBy,
                "changelog" to changelog
            )
            db.collection("remoteConfig").document("app").set(map, SetOptions.merge()).await()

            val historyId = "rem_${System.currentTimeMillis()}"
            val historyMap = hashMapOf(
                "id" to historyId,
                "remoteVersion" to remoteVersion,
                "build" to build,
                "cacheVersion" to cacheVersion,
                "changelog" to changelog,
                "publishedBy" to publishedBy,
                "publishedAt" to now,
                "forceRefresh" to forceRefresh
            )
            db.collection("remote_update_history").document(historyId).set(historyMap).await()

            addAuditLog(action = "Publicou Atualização Remota v$remoteVersion", details = "Changelog: $changelog")

            true
        } catch (e: Exception) {
            Log.e(TAG, "[REMOTE_UPDATE] Erro ao publicar atualização remota: ${e.message}")
            false
        }
    }

    suspend fun confirmAppliedRevision(revision: String, releaseId: String, status: String = "APPLIED", error: String? = null) = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext
            val deviceId = getOrGeneratePersistentDeviceId()
            val deviceName = android.os.Build.MODEL
            
            val eventId = "upd_${releaseId}_${deviceId}"
            val ref = db.collection("updateEvents").document(eventId)
            
            val event = UpdateEventEntity(
                id = eventId,
                deviceId = deviceId,
                deviceName = deviceName,
                fromVersion = "", // Seria bom rastrear a anterior, mas vamos simplificar
                toVersion = revision,
                status = status,
                startedAt = System.currentTimeMillis(),
                completedAt = if (status == "APPLIED") System.currentTimeMillis() else 0L,
                error = error,
                trigger = "auto"
            )
            
            ref.set(event).await()
            
            // Também atualizar o campo no dispositivo para facilitar consultas rápidas
            db.collection("devices").document(deviceId).update(
                "currentRevision", revision,
                "lastUpdateAt", System.currentTimeMillis(),
                "lastSeen", System.currentTimeMillis()
            ).await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error confirming applied revision: ${e.message}")
        }
    }

    suspend fun logRemoteUpdateEvent(event: String, version: String, details: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docId = "evt_${System.currentTimeMillis()}_${getOrGeneratePersistentDeviceId()}"
            db.collection("remote_update_events").document(docId).set(
                hashMapOf(
                    "id" to docId,
                    "deviceId" to getOrGeneratePersistentDeviceId(),
                    "event" to event,
                    "version" to version,
                    "details" to details,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao registrar remote update event: ${e.message}")
        }
    }

    fun startListeningAppVersions() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            appVersionsListener?.remove()
            appVersionsListener = db.collection("appVersions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[VERSIONS] Erro ao escutar versões: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val status = doc.getString("status") ?: if (doc.getBoolean("published") != false) "PUBLISHED" else "DRAFT"
                                val extractedApkUrl = VersionFieldNormalizer.extractApkUrl(doc.data)
                                AppVersionEntity(
                                    id = doc.id,
                                    versionName = doc.getString("versionName") ?: "1.5.0",
                                    versionCode = (doc.getLong("versionCode") ?: 105L).toInt(),
                                    packageName = doc.getString("packageName") ?: "com.aistudio.playfilmeplus.app",
                                    apkUrl = extractedApkUrl,
                                    releaseNotes = doc.getString("releaseNotes") ?: "",
                                    minimumVersionCode = (doc.getLong("minimumVersionCode") ?: 100L).toInt(),
                                    mandatory = doc.getBoolean("mandatory") ?: false,
                                    published = status == "PUBLISHED",
                                    status = status,
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    publishedAt = doc.getLong("publishedAt") ?: doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                    sha256 = doc.getString("sha256") ?: "",
                                    fileSize = doc.getString("fileSize") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedByDescending { it.versionCode }
                        _publishedVersions.value = list
                        // REGRA: NUNCA promover versão automaticamente ao listar appVersions
                    }
                }
        }
    }

    fun startListeningUpdateEvents() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            updateEventsListener?.remove()
            updateEventsListener = db.collection("updateEvents")
                .limit(150)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[UPDATE_EVENTS] Erro ao escutar eventos: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                UpdateEventEntity(
                                    id = doc.id,
                                    deviceId = doc.getString("deviceId") ?: "",
                                    deviceName = doc.getString("deviceName") ?: "",
                                    fromVersion = doc.getString("fromVersion") ?: "",
                                    toVersion = doc.getString("toVersion") ?: "",
                                    status = doc.getString("status") ?: "PENDING",
                                    startedAt = doc.getLong("startedAt") ?: System.currentTimeMillis(),
                                    completedAt = doc.getLong("completedAt") ?: 0L,
                                    error = doc.getString("error"),
                                    trigger = doc.getString("trigger") ?: "admin",
                                    adminId = doc.getString("adminId") ?: "admin@ronycine.app"
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedByDescending { it.startedAt }
                        _updateEvents.value = list
                    }
                }
        }
    }

    fun startListeningAuditLogs() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            auditLogsListener?.remove()
            auditLogsListener = db.collection("adminAuditLogs")
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[AUDIT] Erro ao escutar logs de auditoria: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                AdminAuditLogEntity(
                                    id = doc.id,
                                    adminId = doc.getString("adminId") ?: doc.getString("adminEmail") ?: "admin@ronycine.app",
                                    adminEmail = doc.getString("adminEmail") ?: "admin@ronycine.app",
                                    action = doc.getString("action") ?: "",
                                    targetDeviceId = doc.getString("targetDeviceId") ?: doc.getString("target") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                    result = doc.getString("result") ?: "SUCESSO",
                                    details = doc.getString("details") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedByDescending { it.timestamp }
                        _auditLogs.value = list
                    }
                }
        }
    }

    fun startListeningPendingChanges() {
        serviceScope.launch {
            val db = obtainFirestore() ?: return@launch
            pendingChangesListener?.remove()
            pendingChangesListener = db.collection("pendingChanges")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to pending changes: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.toObjects(PendingChangeEntity::class.java)
                        _pendingChanges.value = list
                    }
                }
        }
    }

    fun startListeningReleaseVersions() {
        serviceScope.launch {
            val db = obtainFirestore() ?: return@launch
            releaseVersionsListener?.remove()
            releaseVersionsListener = db.collection("releaseVersions")
                .orderBy("publishedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to release versions: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.toObjects(ReleaseRevisionEntity::class.java)
                        _releaseVersions.value = list
                    }
                }
        }
    }

    fun startListeningPublicationEvents() {
        serviceScope.launch {
            val db = obtainFirestore() ?: return@launch
            publicationEventsListener?.remove()
            publicationEventsListener = db.collection("publicationEvents")
                .orderBy("publishedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to publication events: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.toObjects(PublicationEventEntity::class.java)
                        _publicationEvents.value = list
                    }
                }
        }
    }

    suspend fun detectRealChangesToday(): Int = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext 0
            val adminId = _currentUser.value?.email ?: "admin@ronycine.app"
            val now = System.currentTimeMillis()
            
            // Lista de alterações REAIS realizadas hoje (07/09/2026)
            val realChanges = listOf(
                PendingChangeEntity(
                    title = "Isolamento de Dados por Perfil",
                    description = "Implementação de isolamento total de 'Minha Lista', 'Histórico' e 'Continuar Assistindo' para cada perfil individualmente, garantindo privacidade entre usuários da mesma conta.",
                    category = "PERFIS",
                    impact = "HIGH",
                    affectedComponents = listOf("PlayFilmeDao", "MediaRepository", "MainViewModel", "MyListScreen", "HistoryScreen"),
                    requiresApk = false,
                    requiresWebPublish = true,
                    revision = "2026.09.07-SECURE-PROFILES"
                ),
                PendingChangeEntity(
                    title = "Sistema de Gestão de Alterações",
                    description = "Novo módulo administrativo para controle, revisão e publicação seletiva de melhorias no projeto, com suporte a revisões remotas e auditoria.",
                    category = "ADMIN",
                    impact = "MEDIUM",
                    affectedComponents = listOf("AdminPendingChangesScreen", "FirebaseService", "AdminViewModel", "ControlModels"),
                    requiresApk = false,
                    requiresWebPublish = true,
                    revision = "2026.09.07-CHANGE-MGMT"
                ),
                PendingChangeEntity(
                    title = "Gestão Profissional de Avatares",
                    description = "Implementação de galeria de avatares predefinidos e suporte para fotos customizadas da galeria com upload para o Firebase Storage.",
                    category = "MELHORIA VISUAL",
                    impact = "LOW",
                    affectedComponents = listOf("AvatarSelectionSheet", "ProfileAvatar", "AvatarCatalog", "FirebaseService"),
                    requiresApk = false,
                    requiresWebPublish = true,
                    revision = "2026.09.07-AVATAR-PRO"
                ),
                PendingChangeEntity(
                    title = "Sincronização de Revisões Remotas",
                    description = "Suporte a 'activeRevision' no RemoteUpdateManager para permitir atualizações de lógica e catálogo em tempo real sem exigir novo APK.",
                    category = "ATUALIZAÇÕES",
                    impact = "MEDIUM",
                    affectedComponents = listOf("RemoteUpdateManager", "RemoteAppConfigEntity"),
                    requiresApk = false,
                    requiresWebPublish = true,
                    revision = "2026.09.07-REMOTE-SYNC"
                )
            )

            var countAdded = 0
            val currentPending = _pendingChanges.value

            for (change in realChanges) {
                // Verificar se já existe pelo título ou revisão (evitar duplicidade real)
                val exists = currentPending.any { it.title == change.title || it.revision == change.revision }
                if (!exists) {
                    val ref = db.collection("pendingChanges").document()
                    val finalChange = change.copy(
                        id = ref.id,
                        status = "PENDING",
                        createdBy = adminId,
                        createdAt = now,
                        updatedAt = now
                    )
                    ref.set(finalChange).await()
                    countAdded++
                }
            }

            if (countAdded > 0) {
                addAuditLog("DETECÇÃO DE ALTERAÇÕES", "$countAdded novas alterações detectadas no projeto.")
            }
            
            countAdded
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting real changes: ${e.message}")
            0
        }
    }

    suspend fun createPendingChangeInCloud(change: PendingChangeEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext false
            val ref = db.collection("pendingChanges").document()
            val newChange = change.copy(id = ref.id, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
            ref.set(newChange).await()
            addAuditLog("CRIOU ALTERAÇÃO PENDENTE", "Título: ${change.title}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pending change: ${e.message}")
            false
        }
    }

    suspend fun updatePendingChangeStatusInCloud(changeId: String, newStatus: String, adminId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext false
            db.collection("pendingChanges").document(changeId)
                .update(
                    "status", newStatus,
                    "updatedAt", System.currentTimeMillis()
                ).await()
            addAuditLog("ALTEROU STATUS DE ALTERAÇÃO", "ID: $changeId para $newStatus")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pending change status: ${e.message}")
            false
        }
    }

    suspend fun publishSelectedChangesInCloud(
        title: String,
        description: String,
        selectedChangeIds: List<String>,
        releaseType: String,
        adminId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não disponível"))
            
            // 1. Obter os objetos reais das alterações selecionadas
            val selectedChanges = _pendingChanges.value.filter { it.id in selectedChangeIds }
            if (selectedChanges.isEmpty()) {
                return@withContext Result.failure(Exception("Nenhuma alteração válida selecionada."))
            }

            val batch = db.batch()
            val publishedAt = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.US)
            val today = sdf.format(Date(publishedAt))
            
            // Gerar revisão
            val countToday = _releaseVersions.value.count { it.revision.startsWith(today) }
            val revision = String.format(Locale.US, "%s-%03d", today, countToday + 1)
            
            val releaseRef = db.collection("releaseVersions").document()
            val releaseId = releaseRef.id

            val finalChanges = selectedChanges.map { 
                it.copy(
                    status = "PUBLISHED", 
                    publishedAt = publishedAt, 
                    publishedBy = adminId,
                    revision = revision
                ) 
            }

            // 2. Atualizar status de cada alteração para PUBLISHED
            selectedChangeIds.forEach { id ->
                batch.update(db.collection("pendingChanges").document(id), 
                    mapOf(
                        "status" to "PUBLISHED",
                        "publishedAt" to publishedAt,
                        "publishedBy" to adminId,
                        "revision" to revision,
                        "updatedAt" to publishedAt
                    )
                )
            }

            // 3. Criar a revisão de lançamento
            val release = ReleaseRevisionEntity(
                id = releaseId,
                revision = revision,
                title = title,
                description = description,
                changes = finalChanges,
                status = "PUBLISHED",
                publishedBy = adminId,
                publishedAt = publishedAt,
                releaseType = releaseType
            )
            batch.set(releaseRef, release)

            // 4. Registrar evento de publicação
            val eventRef = db.collection("publicationEvents").document()
            val event = PublicationEventEntity(
                id = eventRef.id,
                releaseId = releaseId,
                revision = revision,
                publishedBy = adminId,
                publishedAt = publishedAt,
                targetType = releaseType,
                status = "SUCCESS"
            )
            batch.set(eventRef, event)

            // 5. Atualizar Configuração Remota se for do tipo WEB ou HYBRID
            if (releaseType == "WEB" || releaseType == "HYBRID") {
                val configRef = db.collection("remoteConfig").document("appRelease")
                val updateData = mutableMapOf<String, Any>(
                    "activeRevision" to revision,
                    "updatedAt" to publishedAt,
                    "publishedBy" to adminId,
                    "changelog" to description,
                    "remoteVersion" to revision
                )
                batch.set(configRef, updateData, SetOptions.merge())
            }

            batch.commit().await()
            
            addAuditLog("PUBLICOU REVISÃO $revision", "Título: $title | ${selectedChangeIds.size} alterações")
            
            Result.success(revision)
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing changes: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun revertToRevision(revisionId: String, adminId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não disponível"))
            val release = _releaseVersions.value.find { it.id == revisionId } 
                ?: return@withContext Result.failure(Exception("Revisão não encontrada."))

            if (release.releaseType == "APK") {
                return@withContext Result.failure(Exception("Reversão automática não disponível para APK. Gere uma nova versão."))
            }

            db.collection("remoteConfig").document("appRelease")
                .update(
                    "activeRevision", release.revision,
                    "updatedAt", System.currentTimeMillis(),
                    "publishedBy", adminId
                ).await()

            addAuditLog("REVERTEU PARA REVISÃO ${release.revision}", "Admin: $adminId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startListeningAllUsers() {
        serviceScope.launch {
            var db = obtainFirestore()
            var attempts = 0
            while (db == null && attempts < 5) {
                delay(1000L)
                attempts++
                db = obtainFirestore()
            }
            if (db == null) return@launch

            allUsersListener?.remove()
            allUsersListener = db.collection("users")
                .limit(200)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "[ADMIN_USERS] Erro ao escutar usuários: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(UserEntity::class.java)
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedByDescending { it.lastLoginAt }
                        _allUsers.value = list
                    }
                }

            allGlobalProfilesListener?.remove()
            try {
                allGlobalProfilesListener = db.collectionGroup("profiles")
                    .limit(500)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "[ADMIN_PROFILES] Erro ao escutar perfis globais: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                try {
                                    doc.toObject(UserProfile::class.java)
                                } catch (e: Exception) {
                                    null
                                }
                            }.sortedByDescending { it.updatedAt }
                            _allGlobalProfiles.value = list
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "[ADMIN_PROFILES] collectionGroup query exception: ${e.message}")
            }
        }
    }

    suspend fun updateUserStatus(userId: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não inicializado"))
            db.collection("users").document(userId).update("accessStatus", status).await()
            addAuditLog(action = "Alteração de status de usuário", details = "Usuário $userId definido como $status")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(userId: String, role: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não inicializado"))
            db.collection("users").document(userId).update("role", role).await()
            addAuditLog(action = "Alteração de privilégio", details = "Usuário $userId teve seu papel alterado para $role")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminDeleteProfile(userId: String, profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não inicializado"))
            db.collection("users").document(userId).collection("profiles").document(profileId).delete().await()
            addAuditLog(action = "Exclusão de perfil", details = "Perfil $profileId excluído da conta $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setProfileVerification(userId: String, profileId: String, isVerified: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d("FirebaseService", "Iniciando setProfileVerification: userId=$userId, profileId=$profileId, isVerified=$isVerified")
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não inicializado"))
            val docRef = db.collection("users").document(userId).collection("profiles").document(profileId)
            val userRef = db.collection("users").document(userId)
            
            val updates = mapOf<String, Any>(
                "isVerified" to isVerified,
                "updatedAt" to System.currentTimeMillis()
            )
            
            // 1. Write update to profile and user document
            docRef.update(updates).await()
            try {
                userRef.update(updates).await()
            } catch (_: Exception) {}

            // Also update any community messages by this profileId to keep consistency
            try {
                val msgsSnap = db.collection("community_messages")
                    .whereEqualTo("profileId", profileId)
                    .get()
                    .await()
                for (msgDoc in msgsSnap.documents) {
                    msgDoc.reference.update("isVerified", isVerified).await()
                }
            } catch (_: Exception) {}
            
            // 2. Read document again to confirm real persistence
            val snapshot = docRef.get().await()
            val savedVerified = snapshot.getBoolean("isVerified") ?: false
            
            if (savedVerified != isVerified) {
                return@withContext Result.failure(Exception("Falha na confirmação de gravação do selo no Firebase."))
            }

            val actionName = if (isVerified) "VERIFY_USER" else "REMOVE_VERIFICATION"
            val detailMsg = "Perfil $profileId do usuário $userId teve verificação alterada para $isVerified (Confirmado via re-leitura)"
            addAuditLog(action = actionName, details = detailMsg)

            val currentUserEmail = currentUser.value?.email ?: "admin@ronycine.app"
            val auditMap = hashMapOf<String, Any>(
                "action" to actionName,
                "adminId" to currentUserEmail,
                "targetUserId" to userId,
                "targetProfileId" to profileId,
                "timestamp" to System.currentTimeMillis(),
                "result" to "SUCCESS"
            )
            db.collection("adminAuditLogs").document("audit_${System.currentTimeMillis()}").set(auditMap).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[VERIFICATION] Erro ao alterar/confirmar verificação de perfil: ${e.message}")
            try {
                val db = obtainFirestore()
                val currentUserEmail = currentUser.value?.email ?: "admin@ronycine.app"
                val failMap = hashMapOf<String, Any>(
                    "action" to if (isVerified) "VERIFY_USER" else "REMOVE_VERIFICATION",
                    "adminId" to currentUserEmail,
                    "targetUserId" to userId,
                    "targetProfileId" to profileId,
                    "timestamp" to System.currentTimeMillis(),
                    "result" to "FAILURE: ${e.message}"
                )
                db?.collection("adminAuditLogs")?.document("audit_fail_${System.currentTimeMillis()}")?.set(failMap)
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    suspend fun setUserVerification(userId: String, isVerified: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore não inicializado"))
            val docRef = db.collection("users").document(userId)
            
            val updates = mapOf<String, Any>(
                "isVerified" to isVerified,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.update(updates).await()
            
            // Also update all profiles of this user and community messages
            try {
                val profilesSnap = docRef.collection("profiles").get().await()
                for (pDoc in profilesSnap.documents) {
                    pDoc.reference.update(updates).await()
                }
                val msgsSnap = db.collection("community_messages")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (msgDoc in msgsSnap.documents) {
                    msgDoc.reference.update("isVerified", isVerified).await()
                }
            } catch (_: Exception) {}
            
            val snapshot = docRef.get().await()
            val savedVerified = snapshot.getBoolean("isVerified") ?: false
            
            if (savedVerified != isVerified) {
                return@withContext Result.failure(Exception("Falha na confirmação de gravação da conta no Firebase."))
            }

            val actionName = if (isVerified) "VERIFY_USER" else "REMOVE_VERIFICATION"
            val detailMsg = "Conta $userId teve verificação alterada para $isVerified (Confirmado via re-leitura)"
            addAuditLog(action = actionName, details = detailMsg)

            val currentUserEmail = currentUser.value?.email ?: "admin@ronycine.app"
            val auditMap = hashMapOf<String, Any>(
                "action" to actionName,
                "adminId" to currentUserEmail,
                "targetUserId" to userId,
                "timestamp" to System.currentTimeMillis(),
                "result" to "SUCCESS"
            )
            db.collection("adminAuditLogs").document("audit_${System.currentTimeMillis()}").set(auditMap).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[VERIFICATION] Erro ao alterar/confirmar verificação de conta: ${e.message}")
            try {
                val db = obtainFirestore()
                val currentUserEmail = currentUser.value?.email ?: "admin@ronycine.app"
                val failMap = hashMapOf<String, Any>(
                    "action" to if (isVerified) "VERIFY_USER" else "REMOVE_VERIFICATION",
                    "adminId" to currentUserEmail,
                    "targetUserId" to userId,
                    "timestamp" to System.currentTimeMillis(),
                    "result" to "FAILURE: ${e.message}"
                )
                db?.collection("adminAuditLogs")?.document("audit_fail_${System.currentTimeMillis()}")?.set(failMap)
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    fun stopAdminListeners() {
        allUsersListener?.remove()
        allUsersListener = null
        allGlobalProfilesListener?.remove()
        allGlobalProfilesListener = null
        devicesListener?.remove()
        devicesListener = null
        auditLogsListener?.remove()
        auditLogsListener = null
        requestsListener?.remove()
        requestsListener = null
    }

    fun startListeningTop10() {
        val db = firestore ?: return
        top10Listener?.remove()
        top10Listener = db.collection("top10Config").document("current").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "[TOP10] Erro ao escutar Top 10: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val data = snapshot.data ?: return@addSnapshotListener
                    val itemsRaw = data["items"] as? List<Map<String, Any>> ?: emptyList()
                    val items = itemsRaw.map { itemData ->
                        Top10ItemEntity(
                            rank = (itemData["rank"] as? Long)?.toInt() ?: 1,
                            tmdbId = (itemData["tmdbId"] as? Long)?.toInt() ?: 0,
                            mediaType = itemData["mediaType"] as? String ?: "movie",
                            title = itemData["title"] as? String ?: "",
                            posterPath = itemData["posterPath"] as? String
                        )
                    }

                    _top10Config.value = Top10ConfigEntity(
                        mode = data["mode"] as? String ?: "manual",
                        enabled = data["enabled"] as? Boolean ?: true,
                        autoUpdateIntervalHours = (data["autoUpdateIntervalHours"] as? Long)?.toInt() ?: 12,
                        items = items,
                        lastGeneratedAt = data["lastGeneratedAt"] as? Long ?: 0L,
                        lastUpdatedAt = data["lastUpdatedAt"] as? Long ?: System.currentTimeMillis(),
                        updatedBy = data["updatedBy"] as? String ?: "admin",
                        source = data["source"] as? String ?: "admin",
                        version = data["version"] as? Long ?: 1L
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[TOP10] Erro ao processar config: ${e.message}")
                }
            }
        }
    }

    suspend fun updateTop10ConfigInCloud(config: Top10ConfigEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val itemsMap = config.items.map {
                mapOf(
                    "rank" to it.rank,
                    "tmdbId" to it.tmdbId,
                    "mediaType" to it.mediaType,
                    "title" to it.title,
                    "posterPath" to it.posterPath
                )
            }

            val map = mapOf(
                "mode" to config.mode,
                "enabled" to config.enabled,
                "autoUpdateIntervalHours" to config.autoUpdateIntervalHours,
                "items" to itemsMap,
                "lastGeneratedAt" to config.lastGeneratedAt,
                "lastUpdatedAt" to System.currentTimeMillis(),
                "updatedBy" to config.updatedBy,
                "source" to config.source,
                "version" to (config.version + 1)
            )

            db.collection("top10Config").document("current").set(map, SetOptions.merge()).await()
            addAuditLog("ATUALIZOU TOP 10", "Modo: ${config.mode.uppercase()}")
        } catch (e: Exception) {
            Log.e(TAG, "[TOP10] Erro ao salvar config: ${e.message}")
        }
    }

    // --- Featured Config (Conteúdo em Destaque) ---
    fun startListeningFeaturedConfig() {
        val db = firestore ?: return
        featuredConfigListener?.remove()
        featuredConfigListener = db.collection("featuredConfig").document("current").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "[DESTAQUES] Erro ao escutar Destaques: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val data = snapshot.data ?: return@addSnapshotListener
                    val itemsRaw = data["items"] as? List<Map<String, Any>> ?: emptyList()
                    val items = itemsRaw.mapNotNull { itemData ->
                        try {
                            FeaturedItemConfigEntity(
                                id = itemData["id"] as? String ?: "",
                                tmdbId = (itemData["tmdbId"] as? Long)?.toInt() ?: 0,
                                mediaType = itemData["mediaType"] as? String ?: "movie",
                                title = itemData["title"] as? String ?: "",
                                originalTitle = itemData["originalTitle"] as? String ?: "",
                                posterPath = itemData["posterPath"] as? String,
                                backdropPath = itemData["backdropPath"] as? String,
                                rating = (itemData["rating"] as? Number)?.toDouble() ?: 0.0,
                                releaseYear = (itemData["releaseYear"] as? Long)?.toInt() ?: 0,
                                overview = itemData["overview"] as? String ?: "",
                                genres = itemData["genres"] as? String ?: "",
                                trailerUrl = itemData["trailerUrl"] as? String ?: "",
                                autoPlayTrailer = itemData["autoPlayTrailer"] as? Boolean ?: true,
                                displayOrder = (itemData["displayOrder"] as? Long)?.toInt() ?: 0,
                                isActive = itemData["isActive"] as? Boolean ?: true,
                                addedAt = (itemData["addedAt"] as? Long) ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) { null }
                    }

                    val previewRaw = data["previewItems"] as? List<Map<String, Any>> ?: emptyList()
                    val previewItems = previewRaw.mapNotNull { itemData ->
                        try {
                            FeaturedItemConfigEntity(
                                id = itemData["id"] as? String ?: "",
                                tmdbId = (itemData["tmdbId"] as? Long)?.toInt() ?: 0,
                                mediaType = itemData["mediaType"] as? String ?: "movie",
                                title = itemData["title"] as? String ?: "",
                                originalTitle = itemData["originalTitle"] as? String ?: "",
                                posterPath = itemData["posterPath"] as? String,
                                backdropPath = itemData["backdropPath"] as? String,
                                rating = (itemData["rating"] as? Number)?.toDouble() ?: 0.0,
                                releaseYear = (itemData["releaseYear"] as? Long)?.toInt() ?: 0,
                                overview = itemData["overview"] as? String ?: "",
                                genres = itemData["genres"] as? String ?: "",
                                trailerUrl = itemData["trailerUrl"] as? String ?: "",
                                autoPlayTrailer = itemData["autoPlayTrailer"] as? Boolean ?: true,
                                displayOrder = (itemData["displayOrder"] as? Long)?.toInt() ?: 0,
                                isActive = itemData["isActive"] as? Boolean ?: true,
                                addedAt = (itemData["addedAt"] as? Long) ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) { null }
                    }

                    val lastKnownGoodRaw = data["lastKnownGoodItems"] as? List<Map<String, Any>> ?: emptyList()
                    val lastKnownGoodItems = lastKnownGoodRaw.mapNotNull { itemData ->
                        try {
                            FeaturedItemConfigEntity(
                                id = itemData["id"] as? String ?: "",
                                tmdbId = (itemData["tmdbId"] as? Long)?.toInt() ?: 0,
                                mediaType = itemData["mediaType"] as? String ?: "movie",
                                title = itemData["title"] as? String ?: "",
                                originalTitle = itemData["originalTitle"] as? String ?: "",
                                posterPath = itemData["posterPath"] as? String,
                                backdropPath = itemData["backdropPath"] as? String,
                                rating = (itemData["rating"] as? Number)?.toDouble() ?: 0.0,
                                releaseYear = (itemData["releaseYear"] as? Long)?.toInt() ?: 0,
                                overview = itemData["overview"] as? String ?: "",
                                genres = itemData["genres"] as? String ?: "",
                                trailerUrl = itemData["trailerUrl"] as? String ?: "",
                                autoPlayTrailer = itemData["autoPlayTrailer"] as? Boolean ?: true,
                                displayOrder = (itemData["displayOrder"] as? Long)?.toInt() ?: 0,
                                isActive = itemData["isActive"] as? Boolean ?: true,
                                addedAt = (itemData["addedAt"] as? Long) ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) { null }
                    }

                    val excludedKeys = (data["excludedTmdbKeys"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                    _featuredConfig.value = FeaturedConfigEntity(
                        mode = data["mode"] as? String ?: "automatic",
                        source = data["source"] as? String ?: "TMDB",
                        period = data["period"] as? String ?: "today",
                        mediaType = data["mediaType"] as? String ?: "all",
                        limit = (data["limit"] as? Long)?.toInt() ?: 10,
                        autoUpdateIntervalHours = (data["autoUpdateIntervalHours"] as? Long)?.toInt() ?: 6,
                        autoPublish = data["autoPublish"] as? Boolean ?: true,
                        autoImportToCatalog = data["autoImportToCatalog"] as? Boolean ?: false,
                        minRating = (data["minRating"] as? Number)?.toDouble() ?: 0.0,
                        selectedGenre = data["selectedGenre"] as? String ?: "",
                        minYear = (data["minYear"] as? Long)?.toInt() ?: 0,
                        excludedTmdbKeys = excludedKeys,
                        useInHeroBanner = data["useInHeroBanner"] as? Boolean ?: true,
                        items = items,
                        previewItems = previewItems,
                        lastKnownGoodItems = if (lastKnownGoodItems.isNotEmpty()) lastKnownGoodItems else items,
                        lastUpdatedAt = (data["lastUpdatedAt"] as? Long) ?: System.currentTimeMillis(),
                        nextUpdateAt = (data["nextUpdateAt"] as? Long) ?: 0L,
                        lastSyncStatus = data["lastSyncStatus"] as? String ?: "SUCCESS",
                        lastSyncError = data["lastSyncError"] as? String ?: "",
                        updatedBy = data["updatedBy"] as? String ?: "admin@ronycine.app",
                        version = (data["version"] as? Long) ?: 1L
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[DESTAQUES] Erro ao processar config: ${e.message}")
                }
            }
        }
    }

    fun startListeningFeaturedHistory() {
        val db = firestore ?: return
        featuredHistoryListener?.remove()
        featuredHistoryListener = db.collection("featured_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(25)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "[DESTAQUES-HISTORICO] Erro ao escutar histórico: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        FeaturedHistoryEntity(
                            id = doc.id,
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            source = data["source"] as? String ?: "TMDB",
                            mode = data["mode"] as? String ?: "automatic",
                            period = data["period"] as? String ?: "today",
                            count = (data["count"] as? Long)?.toInt() ?: 0,
                            itemsSummary = (data["itemsSummary"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                            addedItemsCount = (data["addedItemsCount"] as? Long)?.toInt() ?: 0,
                            removedItemsCount = (data["removedItemsCount"] as? Long)?.toInt() ?: 0,
                            triggeredBy = data["triggeredBy"] as? String ?: "admin"
                        )
                    }
                    _featuredHistory.value = list
                }
            }
    }

    suspend fun updateFeaturedConfigInCloud(config: FeaturedConfigEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val itemsMap = config.items.map {
                mapOf(
                    "id" to it.id,
                    "tmdbId" to it.tmdbId,
                    "mediaType" to it.mediaType,
                    "title" to it.title,
                    "originalTitle" to it.originalTitle,
                    "posterPath" to it.posterPath,
                    "backdropPath" to it.backdropPath,
                    "rating" to it.rating,
                    "releaseYear" to it.releaseYear,
                    "overview" to it.overview,
                    "genres" to it.genres,
                    "trailerUrl" to it.trailerUrl,
                    "autoPlayTrailer" to it.autoPlayTrailer,
                    "displayOrder" to it.displayOrder,
                    "isActive" to it.isActive,
                    "addedAt" to it.addedAt
                )
            }

            val previewItemsMap = config.previewItems.map {
                mapOf(
                    "id" to it.id,
                    "tmdbId" to it.tmdbId,
                    "mediaType" to it.mediaType,
                    "title" to it.title,
                    "originalTitle" to it.originalTitle,
                    "posterPath" to it.posterPath,
                    "backdropPath" to it.backdropPath,
                    "rating" to it.rating,
                    "releaseYear" to it.releaseYear,
                    "overview" to it.overview,
                    "genres" to it.genres,
                    "trailerUrl" to it.trailerUrl,
                    "autoPlayTrailer" to it.autoPlayTrailer,
                    "displayOrder" to it.displayOrder,
                    "isActive" to it.isActive,
                    "addedAt" to it.addedAt
                )
            }

            val lastKnownGoodMap = config.lastKnownGoodItems.map {
                mapOf(
                    "id" to it.id,
                    "tmdbId" to it.tmdbId,
                    "mediaType" to it.mediaType,
                    "title" to it.title,
                    "originalTitle" to it.originalTitle,
                    "posterPath" to it.posterPath,
                    "backdropPath" to it.backdropPath,
                    "rating" to it.rating,
                    "releaseYear" to it.releaseYear,
                    "overview" to it.overview,
                    "genres" to it.genres,
                    "trailerUrl" to it.trailerUrl,
                    "autoPlayTrailer" to it.autoPlayTrailer,
                    "displayOrder" to it.displayOrder,
                    "isActive" to it.isActive,
                    "addedAt" to it.addedAt
                )
            }

            val map = mapOf(
                "mode" to config.mode,
                "source" to config.source,
                "period" to config.period,
                "mediaType" to config.mediaType,
                "limit" to config.limit,
                "autoUpdateIntervalHours" to config.autoUpdateIntervalHours,
                "autoPublish" to config.autoPublish,
                "autoImportToCatalog" to config.autoImportToCatalog,
                "minRating" to config.minRating,
                "selectedGenre" to config.selectedGenre,
                "minYear" to config.minYear,
                "excludedTmdbKeys" to config.excludedTmdbKeys,
                "useInHeroBanner" to config.useInHeroBanner,
                "items" to itemsMap,
                "previewItems" to previewItemsMap,
                "lastKnownGoodItems" to lastKnownGoodMap,
                "lastUpdatedAt" to System.currentTimeMillis(),
                "nextUpdateAt" to config.nextUpdateAt,
                "lastSyncStatus" to config.lastSyncStatus,
                "lastSyncError" to config.lastSyncError,
                "updatedBy" to config.updatedBy,
                "version" to (config.version + 1)
            )

            db.collection("featuredConfig").document("current").set(map, SetOptions.merge()).await()
            addAuditLog("ATUALIZOU DESTAQUES", "Modo: ${config.mode.uppercase()} | ${config.items.size} itens")
        } catch (e: Exception) {
            Log.e(TAG, "[DESTAQUES] Erro ao salvar config: ${e.message}")
        }
    }

    suspend fun addFeaturedHistoryInCloud(entry: FeaturedHistoryEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = mapOf(
                "timestamp" to entry.timestamp,
                "source" to entry.source,
                "mode" to entry.mode,
                "period" to entry.period,
                "count" to entry.count,
                "itemsSummary" to entry.itemsSummary,
                "addedItemsCount" to entry.addedItemsCount,
                "removedItemsCount" to entry.removedItemsCount,
                "triggeredBy" to entry.triggeredBy
            )
            db.collection("featured_history").add(map).await()
        } catch (e: Exception) {
            Log.e(TAG, "[DESTAQUES-HISTORICO] Erro ao registrar histórico: ${e.message}")
        }
    }

    suspend fun updateRemoteConfigInCloud(config: RemoteConfigEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf<String, Any>(
                "appName" to config.appName,
                "maintenanceMode" to config.maintenanceMode,
                "maintenanceMessage" to config.maintenanceMessage,
                "maintenanceStartedAt" to config.maintenanceStartedAt,
                "maintenanceEndAt" to config.maintenanceEndAt,
                "allowedVersionsDuringMaintenance" to config.allowedVersionsDuringMaintenance,
                "minimumVersionCode" to config.minimumVersionCode,
                "latestVersionCode" to config.latestVersionCode,
                "latestVersionName" to config.latestVersionName,
                "latestApkUrl" to config.latestApkUrl,
                "latestReleaseNotes" to config.latestReleaseNotes,
                "enableDownloads" to config.enableDownloads,
                "enableRequests" to config.enableRequests,
                "enableLiveTV" to config.enableLiveTV,
                "enableTrailers" to config.enableTrailers,
                "announcement" to config.announcement,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("config").document("remoteConfig").set(map, SetOptions.merge()).await()
            Log.d(TAG, "[REMOTE_CONFIG] Configuração remota salva no Firestore com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "[REMOTE_CONFIG] Erro ao salvar config remota: ${e.message}")
        }
    }

    suspend fun publishAppVersionInCloud(version: AppVersionEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docId = if (version.id.isNotBlank()) version.id else "v_${version.versionCode}"
            val map = hashMapOf<String, Any>(
                "versionName" to version.versionName,
                "versionCode" to version.versionCode,
                "packageName" to version.packageName,
                "apkUrl" to version.apkUrl,
                "downloadUrl" to version.apkUrl,
                "url" to version.apkUrl,
                "releaseNotes" to version.releaseNotes,
                "minimumVersionCode" to version.minimumVersionCode,
                "mandatory" to version.mandatory,
                "published" to (version.status == "PUBLISHED"),
                "status" to version.status,
                "createdAt" to version.createdAt,
                "publishedAt" to (if (version.publishedAt > 0L) version.publishedAt else System.currentTimeMillis()),
                "updatedAt" to System.currentTimeMillis(),
                "sha256" to version.sha256,
                "fileSize" to version.fileSize
            )
            db.collection("appVersions").document(docId).set(map, SetOptions.merge()).await()
            addAuditLog("SALVOU VERSÃO", "Versão ${version.versionName} (Build ${version.versionCode}) - Status: ${version.status}")
        } catch (e: Exception) {
            Log.e(TAG, "[VERSIONS] Erro ao salvar/publicar versão: ${e.message}")
        }
    }

    suspend fun activateAppUpdateInCloud(
        version: AppVersionEntity,
        mandatory: Boolean,
        adminEmail: String = "admin@ronycine.app"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore indisponível."))
        if (version.apkUrl.isBlank()) {
            return@withContext Result.failure(Exception("URL do APK não configurada nesta versão."))
        }
        try {
            val now = System.currentTimeMillis()
            val controlMap = hashMapOf<String, Any>(
                "enabled" to true,
                "activeVersionCode" to version.versionCode,
                "activeVersionName" to version.versionName,
                "mandatory" to mandatory,
                "apkUrl" to version.apkUrl,
                "downloadUrl" to version.apkUrl,
                "releaseNotes" to version.releaseNotes,
                "sha256" to version.sha256,
                "fileSize" to version.fileSize,
                "activatedAt" to now,
                "activatedBy" to adminEmail,
                "updatedAt" to now
            )
            // 1. Salva no documento oficial system/updateControl
            db.collection("system").document("updateControl").set(controlMap, SetOptions.merge()).await()
            // 2. Sincroniza em config/updateControl para compatibilidade
            db.collection("config").document("updateControl").set(controlMap, SetOptions.merge()).await()

            // 3. Atualiza o remoteConfig com os dados da versão ativada
            val currentRemote = _remoteConfig.value
            val updatedRemote = currentRemote.copy(
                latestVersionCode = version.versionCode,
                latestVersionName = version.versionName,
                latestApkUrl = version.apkUrl,
                latestReleaseNotes = version.releaseNotes,
                minimumVersionCode = if (mandatory) version.versionCode else 100,
                updatedAt = now
            )
            updateRemoteConfigInCloud(updatedRemote)

            // 4. Atualiza estado em memória imediatamente
            _updateControl.value = UpdateControlEntity(
                enabled = true,
                activeVersionCode = version.versionCode,
                activeVersionName = version.versionName,
                mandatory = mandatory,
                apkUrl = version.apkUrl,
                releaseNotes = version.releaseNotes,
                sha256 = version.sha256,
                fileSize = version.fileSize,
                activatedAt = now,
                activatedBy = adminEmail,
                updatedAt = now
            )

            addAuditLog("ATIVOU ATUALIZAÇÃO", "Versão ${version.versionName} (Build ${version.versionCode}) - Obrigatória: $mandatory")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[UPDATE_CONTROL] Erro ao ativar atualização: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deactivateAppUpdateInCloud(
        adminEmail: String = "admin@ronycine.app"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore indisponível."))
        try {
            val now = System.currentTimeMillis()
            val controlMap = hashMapOf<String, Any?>(
                "enabled" to false,
                "activeVersionCode" to null,
                "activeVersionName" to null,
                "mandatory" to false,
                "apkUrl" to "",
                "downloadUrl" to "",
                "deactivatedAt" to now,
                "deactivatedBy" to adminEmail,
                "updatedAt" to now
            )
            // 1. Atualiza system/updateControl e config/updateControl
            db.collection("system").document("updateControl").set(controlMap, SetOptions.merge()).await()
            db.collection("config").document("updateControl").set(controlMap, SetOptions.merge()).await()

            // 2. Atualiza remoteConfig para remover exigência ou bloqueio
            val currentRemote = _remoteConfig.value
            val updatedRemote = currentRemote.copy(
                minimumVersionCode = 100,
                latestApkUrl = "",
                updatedAt = now
            )
            updateRemoteConfigInCloud(updatedRemote)

            // 3. Atualiza estado em memória imediatamente
            _updateControl.value = UpdateControlEntity(
                enabled = false,
                activeVersionCode = null,
                activeVersionName = null,
                mandatory = false,
                apkUrl = "",
                deactivatedAt = now,
                deactivatedBy = adminEmail,
                updatedAt = now
            )

            addAuditLog("DESATIVOU ATUALIZAÇÃO", "Atualizações desativadas globalmente pelo administrador")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[UPDATE_CONTROL] Erro ao desativar atualização: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun setAppVersionStatusInCloud(versionId: String, newStatus: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val isPub = newStatus == "PUBLISHED"
            db.collection("appVersions").document(versionId).set(
                mapOf(
                    "status" to newStatus,
                    "published" to isPub,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            addAuditLog("ALTEROU STATUS DE VERSÃO", "ID $versionId para $newStatus")
        } catch (e: Exception) {
            Log.e(TAG, "[VERSIONS] Erro ao alterar status da versão: ${e.message}")
        }
    }

    suspend fun recordUpdateEventInCloud(event: UpdateEventEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docId = if (event.id.isNotBlank()) event.id else "evt_${event.deviceId}_${event.toVersion}_${System.currentTimeMillis()}"
            val map = hashMapOf<String, Any>(
                "deviceId" to event.deviceId,
                "deviceName" to event.deviceName,
                "fromVersion" to event.fromVersion,
                "toVersion" to event.toVersion,
                "status" to event.status,
                "startedAt" to event.startedAt,
                "completedAt" to event.completedAt,
                "trigger" to event.trigger,
                "adminId" to (event.adminId ?: "admin@ronycine.app")
            )
            event.error?.let { map["error"] = it }
            
            // Extract targetVersionCode from docId if present (e.g., evt_deviceId_versionCode)
            val parts = docId.split("_")
            if (parts.size >= 3) {
                val targetVerCode = parts[2].toIntOrNull()
                if (targetVerCode != null) {
                    map["targetVersionCode"] = targetVerCode
                }
            }
            db.collection("updateEvents").document(docId).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "[UPDATE_EVENT] Erro ao registrar evento de atualização: ${e.message}")
        }
    }

    suspend fun recordUpdateOperationInCloud(
        operationId: String,
        type: String,
        targetVersionCode: Int,
        targetVersionName: String,
        totalDevices: Int
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf<String, Any>(
                "type" to type,
                "targetVersionCode" to targetVersionCode,
                "targetVersionName" to targetVersionName,
                "totalDevices" to totalDevices,
                "pending" to totalDevices,
                "notified" to 0,
                "downloading" to 0,
                "installing" to 0,
                "success" to 0,
                "failed" to 0,
                "cancelled" to 0,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("updateOperations").document(operationId).set(map, SetOptions.merge()).await()
            Log.d(TAG, "[UPDATE_OPERATION] Operação $operationId registrada com sucesso.")
        } catch (e: Exception) {
            Log.w(TAG, "[UPDATE_OPERATION] Erro ao registrar operação: ${e.message}")
        }
    }

    suspend fun updateSpecificEventStatus(
        eventId: String,
        status: String,
        error: String? = null,
        completedAt: Long = if (status == "UPDATED" || status == "FAILED" || status == "CANCELLED") System.currentTimeMillis() else 0L
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf<String, Any>(
                "status" to status,
                "completedAt" to completedAt
            )
            if (error != null) map["error"] = error
            db.collection("updateEvents").document(eventId).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "[UPDATE_EVENT] Erro ao atualizar status de evento: ${e.message}")
        }
    }

    suspend fun deleteAppVersionInCloud(versionId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("appVersions").document(versionId).delete().await()
            addAuditLog("REMOVEU VERSÃO", "ID $versionId")
        } catch (e: Exception) {
            Log.e(TAG, "[VERSIONS] Erro ao deletar versão: ${e.message}")
        }
    }

    suspend fun cancelDeviceUpdateInCloud(deviceId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val eventsSnap = db.collection("updateEvents")
                .whereEqualTo("deviceId", deviceId)
                .get()
                .await()
            eventsSnap.documents.forEach { doc ->
                val status = doc.getString("status")
                if (status in listOf("PENDING", "NOTIFIED", "DOWNLOADING", "DOWNLOADED", "INSTALLING")) {
                    db.collection("updateEvents").document(doc.id).set(
                        mapOf(
                            "status" to "CANCELLED",
                            "completedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                }
            }
            db.collection("devices").document(deviceId).set(
                mapOf("updateStatus" to "CANCELLED"),
                SetOptions.merge()
            ).await()
            addAuditLog("CANCELOU ATUALIZAÇÃO", "Dispositivo $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "[UPDATE] Erro ao cancelar atualização: ${e.message}")
        }
    }

    suspend fun addAuditLog(action: String, details: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val id = "log_${System.currentTimeMillis()}"
            val map = hashMapOf<String, Any>(
                "action" to action,
                "details" to details,
                "version" to "1.5.0",
                "adminEmail" to "admin@ronycine.app",
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("adminAuditLogs").document(id).set(map).await()
        } catch (e: Exception) {
            Log.w(TAG, "[AUDIT] Erro ao salvar log de auditoria: ${e.message}")
        }
    }

    suspend fun setDeviceBlocked(deviceId: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val updateData = mapOf(
                "isBlocked" to blocked,
                "status" to if (blocked) "blocked" else "online",
                "accessStatus" to if (blocked) "BANNED" else "ACTIVE",
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("devices").document(deviceId).set(updateData, SetOptions.merge()).await()
            addAuditLog(if (blocked) "BLOQUEOU DISPOSITIVO" else "DESBLOQUEOU DISPOSITIVO", "Device $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar status de bloqueio do dispositivo: ${e.message}")
        }
    }

    suspend fun setDeviceAdminAccess(targetDeviceId: String, adminAccess: Boolean) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val updateData = mapOf(
                "adminAccess" to adminAccess,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("devices").document(targetDeviceId).set(updateData, SetOptions.merge()).await()
            val action = if (adminAccess) "CONCEDEU ACESSO ADMIN" else "REVOGOU ACESSO ADMIN"
            addAuditLog(action, "Dispositivo $targetDeviceId")
            logAdminAction(ADMIN_EMAIL, action, targetDeviceId, "SUCESSO")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar acesso admin do dispositivo: ${e.message}")
        }
    }

    suspend fun requestDeviceSync(targetDeviceId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("devices").document(targetDeviceId).update("syncRequested", true).await()
            addAuditLog("SOLICITOU ATUALIZAÇÃO REMOTA", "Dispositivo $targetDeviceId")
            logAdminAction(ADMIN_EMAIL, "SYNC_REQUEST", targetDeviceId, "SUCESSO")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar sync do dispositivo: ${e.message}")
        }
    }

    suspend fun setDeviceAccessStatus(targetDeviceId: String, accessStatus: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val updateData = mapOf(
                "accessStatus" to accessStatus,
                "isBlocked" to (accessStatus == "BANNED"),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("devices").document(targetDeviceId).set(updateData, SetOptions.merge()).await()
            addAuditLog("ALTEROU STATUS DE ACESSO", "Dispositivo $targetDeviceId para $accessStatus")
            logAdminAction(ADMIN_EMAIL, "ALTEROU_STATUS_ACESSO", "$targetDeviceId: $accessStatus", "SUCESSO")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar status de acesso do dispositivo: ${e.message}")
        }
    }

    suspend fun removeDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("devices").document(deviceId).delete().await()
            addAuditLog("REMOVEU DISPOSITIVO", "Device $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover dispositivo do Firestore: ${e.message}")
        }
    }

    fun initDeviceManager(customPrefs: android.content.SharedPreferences? = null) {
        val id = getOrGeneratePersistentDeviceId(customPrefs)
        Log.d(TAG, "[DEVICE] deviceId: $id")

        serviceScope.launch {
            registerDeviceWithRetry(id)
        }
    }

    private suspend fun registerDeviceWithRetry(id: String) {
        Log.d(TAG, "[DEVICE] inicializando Firebase...")
        var db = obtainFirestore()
        var retryCount = 0
        while (db == null && retryCount < 5) {
            delay(1000L)
            retryCount++
            db = obtainFirestore()
        }

        if (db == null) {
            Log.e(TAG, "[DEVICE] erro: Firestore indisponível após inicialização.")
            return
        }
        Log.d(TAG, "[DEVICE] Firestore disponível: true")

        // Listener para monitorar bloqueio, autorização admin e PEDIDO DE SINCRONIZAÇÃO
        try {
            db.collection("devices").document(id).addSnapshotListener { snapshot, err ->
                if (err != null) {
                    Log.w(TAG, "[DEVICE] Erro ao escutar status do dispositivo: ${err.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val blocked = snapshot.getBoolean("isBlocked") ?: false
                    var adminAccess = snapshot.getBoolean("adminAccess") ?: false
                    val accessStatus = snapshot.getString("accessStatus") ?: if (blocked) "BANNED" else "ACTIVE"
                    val model = snapshot.getString("model") ?: snapshot.getString("name") ?: ""
                    
                    val syncRequested = snapshot.getBoolean("syncRequested") ?: false
                    if (syncRequested) {
                        Log.d(TAG, "[DEVICE] Pedido de sincronização recebido via Firestore!")
                        serviceScope.launch {
                            // Executa registro completo novamente
                            registerDeviceInternal(id, db, isSyncResponse = true)
                        }
                    }

                    val isMaoLx9 = model.contains("MAO-LX9", ignoreCase = true) || id.contains("MAO-LX9", ignoreCase = true)
                    val isSdkGphone = model.contains("sdk_gphone64_arm64", ignoreCase = true) || id.contains("sdk_gphone64_arm64", ignoreCase = true)
                    
                    if ((isMaoLx9 || isSdkGphone) && (!adminAccess || accessStatus != "ACTIVE" || blocked)) {
                        adminAccess = true
                        serviceScope.launch {
                            try {
                                db.collection("devices").document(id).set(
                                    mapOf("adminAccess" to true, "accessStatus" to "ACTIVE", "isBlocked" to false),
                                    SetOptions.merge()
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "[DEVICE] Erro ao auto-autorizar admin in snapshot listener: ${e.message}")
                            }
                        }
                    }
                    
                    _isDeviceBlocked.value = blocked || accessStatus == "BANNED"
                    _isCurrentDeviceAdminAuthorized.value = adminAccess && (accessStatus == "ACTIVE") && !blocked
                    updateAdminAuthorization()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[DEVICE] Falha ao configurar listener de bloqueio: ${e.message}")
        }

        registerDeviceInternal(id, db)
    }

    private suspend fun registerDeviceInternal(id: String, db: FirebaseFirestore, isSyncResponse: Boolean = false) {
        // Gravação inicial e registro do dispositivo
        Log.d(TAG, "[DEVICE] registrando dispositivo (sync=$isSyncResponse)...")
        try {
            val now = System.currentTimeMillis()
            val docRef = db.collection("devices").document(id)

            val snap = if (!isSyncResponse) {
                try {
                    withTimeout(4000L) { docRef.get().await() }
                } catch (e: Exception) {
                    Log.d(TAG, "[DEVICE] Verificação prévia de documento em cache/espera: ${e.message}")
                    null
                }
            } else null

            val deviceModel = android.os.Build.MODEL ?: getDeviceInfo()
            val osVer = android.os.Build.VERSION.RELEASE ?: "Android"

            val pInfo = try {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            } catch (e: Exception) { null }
            val currentVersionName = pInfo?.versionName ?: "1.0.0"
            val currentVersionCode = if (pInfo != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") pInfo.versionCode
                }
            } else 1

            val latestVersion = _publishedVersions.value.firstOrNull { it.status == "PUBLISHED" || it.published }
            val targetVerCode = latestVersion?.versionCode ?: 0
            val uStatus = if (targetVerCode > 0) {
                if (currentVersionCode >= targetVerCode) "UPDATED" else "AVAILABLE"
            } else {
                "UPDATED"
            }

            val deviceMap = hashMapOf<String, Any>(
                "deviceId" to id,
                "name" to deviceModel,
                "model" to deviceModel,
                "platform" to "android",
                "osVersion" to osVer,
                "appVersion" to currentVersionName,
                "buildNumber" to currentVersionCode,
                "currentVersionName" to currentVersionName,
                "currentVersionCode" to currentVersionCode,
                "lastSeen" to now,
                "lastActivity" to now,
                "status" to "online",
                "updatedAt" to now,
                "lastVersionCheckAt" to now,
                "updateStatus" to uStatus,
                "syncRequested" to false // Limpa o flag se for uma resposta
            )

            val isMaoLx9 = deviceModel.contains("MAO-LX9", ignoreCase = true) || id.contains("MAO-LX9", ignoreCase = true)
            val isSdkGphone = deviceModel.contains("sdk_gphone64_arm64", ignoreCase = true) || id.contains("sdk_gphone64_arm64", ignoreCase = true)
            
            if (isMaoLx9 || isSdkGphone) {
                deviceMap["adminAccess"] = true
                deviceMap["accessStatus"] = "ACTIVE"
                deviceMap["isBlocked"] = false
            }

            if (!isSyncResponse && (snap == null || !snap.exists() || (snap.getLong("firstSeen") == null && snap.getLong("createdAt") == null))) {
                deviceMap["firstSeen"] = now
                deviceMap["createdAt"] = now
                deviceMap["firstConnection"] = now
                if (!isMaoLx9 && !isSdkGphone) {
                    deviceMap["isBlocked"] = false
                    deviceMap["accessStatus"] = "ACTIVE"
                }
            }

            withTimeout(8000L) {
                docRef.set(deviceMap, SetOptions.merge()).await()
            }
            Log.d(TAG, "[DEVICE] registro concluído: $id (v$currentVersionName)")

            // Se o aplicativo acabou de ser atualizado com sucesso, marca os eventos correspondentes no Firestore como SUCCESS!
            try {
                val pendingEventsSnap = db.collection("updateEvents")
                    .whereEqualTo("deviceId", id)
                    .get()
                    .await()
                
                pendingEventsSnap.documents.forEach { doc ->
                    val toVersion = doc.getString("toVersion") ?: ""
                    val targetVersionCode = doc.getLong("targetVersionCode")?.toInt() ?: 0
                    val status = doc.getString("status")
                    
                    val isMatch = toVersion == currentVersionName || (targetVersionCode > 0 && currentVersionCode >= targetVersionCode)
                    if (isMatch && status != "SUCCESS" && status != "UPDATED") {
                        db.collection("updateEvents").document(doc.id).set(
                            mapOf(
                                "status" to "SUCCESS",
                                "completedAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        ).await()
                        Log.d(TAG, "[DEVICE] Evento de atualização ${doc.id} marcado como SUCCESS com sucesso!")
                    }
                }
            } catch (evEx: Exception) {
                Log.d(TAG, "[DEVICE] Ignorando verificação de eventos de atualização na inicialização: ${evEx.message}")
            }
            
            // Inicia heartbeat se ainda não estiver rodando
            if (!isSyncResponse) {
                startHeartbeat(id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[DEVICE] Erro ao registrar dispositivo: ${e.message}")
        }
    }

    private fun startHeartbeat(id: String) {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(120 * 1000L) // Pulso a cada 2 minutos
                val db = obtainFirestore()
                if (db != null) {
                    try {
                        val now = System.currentTimeMillis()
                        val deviceModel = android.os.Build.MODEL ?: getDeviceInfo()
                        val pInfo = try {
                            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                        } catch (e: Exception) { null }
                        val currentVersionName = pInfo?.versionName ?: "1.0.0"
                        val currentVersionCode = if (pInfo != null) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                pInfo.longVersionCode.toInt()
                            } else {
                                @Suppress("DEPRECATION") pInfo.versionCode
                            }
                        } else 1

                        val updates = hashMapOf<String, Any>(
                            "deviceId" to id,
                            "lastSeen" to now,
                            "lastActivity" to now,
                            "status" to "online",
                            "name" to deviceModel,
                            "model" to deviceModel,
                            "platform" to "Android",
                            "appVersion" to currentVersionName,
                            "buildNumber" to currentVersionCode,
                            "updatedAt" to now
                        )
                        val isMaoLx9 = deviceModel.contains("MAO-LX9", ignoreCase = true) || id.contains("MAO-LX9", ignoreCase = true)
                        val isSdkGphone = deviceModel.contains("sdk_gphone64_arm64", ignoreCase = true) || id.contains("sdk_gphone64_arm64", ignoreCase = true)
                        if (isMaoLx9 || isSdkGphone) {
                            updates["adminAccess"] = true
                            updates["accessStatus"] = "ACTIVE"
                            updates["isBlocked"] = false
                        }
                        db.collection("devices").document(id).set(updates, SetOptions.merge())
                        Log.d(TAG, "[DEVICE] heartbeat enviado com sucesso (lastSeen: $now)")
                    } catch (e: Exception) {
                        Log.w(TAG, "[DEVICE] erro no heartbeat: ${e.message}")
                    }
                }
            }
        }
    }

    private val _lastSyncFormatted = MutableStateFlow("Sincronizado em tempo real")
    val lastSyncFormatted: StateFlow<String> = _lastSyncFormatted.asStateFlow()

    private val _catalogVersion = MutableStateFlow(100L)
    val catalogVersion: StateFlow<Long> = _catalogVersion.asStateFlow()

    suspend fun getMegaEmbedConfigSync(): MegaEmbedConfig = MegaEmbedService.getConfig()

    suspend fun getCatalogVersionSync(): Long = _catalogVersion.value

    suspend fun upsertFeaturedInCloud(entity: FeaturedMediaEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "mediaTmdbId" to entity.mediaTmdbId,
                "mediaType" to entity.mediaType,
                "trailerUrl" to entity.trailerUrl,
                "autoPlayTrailer" to entity.autoPlayTrailer,
                "displayOrder" to entity.displayOrder,
                "isActive" to entity.isActive,
                "updatedAt" to entity.updatedAt
            )
            db.collection("featured").document("tmdb_${entity.mediaTmdbId}").set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving featured to cloud: ${e.message}")
        }
    }

    suspend fun updateMegaEmbedConfigInCloud(config: MegaEmbedConfig) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "defaultPlayer" to config.defaultPlayer,
                "defaultLanguage" to config.defaultLanguage,
                "colorHex" to config.colorHex,
                "baseDomain" to config.baseDomain,
                "cacheHours" to config.cacheHours,
                "isAutoSyncEnabled" to config.isAutoSyncEnabled
            )
            db.collection("config").document("mega_embed").set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving mega_embed config to cloud: ${e.message}")
        }
    }

    suspend fun updateFcmToken(token: String) = withContext(Dispatchers.IO) {
        val id = deviceId ?: getOrGeneratePersistentDeviceId()
        val db = obtainFirestore() ?: return@withContext
        try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "deviceId" to id,
                "fcmToken" to token,
                "fcmTokenUpdatedAt" to now,
                "fcmStatus" to if (token.isNotBlank()) "DISPONÍVEL" else "INDISPONÍVEL",
                "notificationsEnabled" to true,
                "notificationsPermission" to "CONCEDIDA",
                "platform" to "android",
                "appVersion" to "1.1.0",
                "lastSeen" to now,
                "accessStatus" to "ACTIVE",
                "updatedAt" to now
            )
            db.collection("devices").document(id).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "[FCM] Token updated for device $id: $token")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] Error updating token for device $id: ${e.message}")
        }
    }

    suspend fun updatePushStatus(hasPermission: Boolean, token: String?) = withContext(Dispatchers.IO) {
        val id = deviceId ?: getOrGeneratePersistentDeviceId()
        val db = obtainFirestore() ?: return@withContext
        try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "deviceId" to id,
                "notificationsPermission" to if (hasPermission) "CONCEDIDA" else "NEGADA",
                "notificationsEnabled" to hasPermission,
                "fcmStatus" to if (token?.isNotBlank() == true) "DISPONÍVEL" else "INDISPONÍVEL",
                "platform" to "android",
                "appVersion" to "1.1.0",
                "lastSeen" to now,
                "accessStatus" to "ACTIVE",
                "updatedAt" to now
            )
            if (token != null) {
                updates["fcmToken"] = token
                updates["fcmTokenUpdatedAt"] = now
            }
            db.collection("devices").document(id).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "[FCM] Push status updated for device $id (Permission: $hasPermission, Token: ${token != null})")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] Error updating push status for device $id: ${e.message}")
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val id = deviceId ?: getOrGeneratePersistentDeviceId()
        val db = obtainFirestore() ?: return@withContext
        try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "notificationsEnabled" to enabled,
                "updatedAt" to now
            )
            db.collection("devices").document(id).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "[FCM] Notifications status updated for device $id: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] Error updating notification status for device $id: ${e.message}")
        }
    }

    suspend fun updateDeviceNotificationSettings(
        notificationsEnabled: Boolean,
        newMoviesEnabled: Boolean,
        newSeriesEnabled: Boolean,
        newEpisodesEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val id = deviceId ?: getOrGeneratePersistentDeviceId()
        val db = obtainFirestore() ?: return@withContext
        try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "notificationsEnabled" to notificationsEnabled,
                "newMoviesEnabled" to newMoviesEnabled,
                "newSeriesEnabled" to newSeriesEnabled,
                "newEpisodesEnabled" to newEpisodesEnabled,
                "updatedAt" to now
            )
            db.collection("devices").document(id).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "[FCM] Device notification settings updated for device $id")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] Error updating notification settings for device $id: ${e.message}")
        }
    }

    suspend fun getNotificationConfigAutoEnabled(): Boolean = withContext(Dispatchers.IO) {
        val db = obtainFirestore() ?: return@withContext true
        try {
            val doc = db.collection("config").document("notifications").get().await()
            if (doc.exists()) {
                doc.getBoolean("autoEnabled") ?: true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting notifications config: ${e.message}")
            true
        }
    }

    suspend fun setNotificationConfigAutoEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = obtainFirestore() ?: return@withContext
        try {
            val map = hashMapOf("autoEnabled" to enabled)
            db.collection("config").document("notifications").set(map, SetOptions.merge()).await()
            Log.d(TAG, "Notification config autoEnabled updated: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notifications config: ${e.message}")
        }
    }

    suspend fun sendNotificationEventToCloud(
        id: String,
        title: String,
        message: String,
        imageUrl: String?,
        type: String,
        actionUrl: String?,
        targetSegment: String,
        buttonText: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val db = obtainFirestore() ?: return@withContext false
        try {
            // First check if this event already exists to prevent duplicate notifications, checking CACHE first to support offline seamlessly
            val doc = try {
                db.collection("notificationEvents").document(id).get(com.google.firebase.firestore.Source.CACHE).await()
            } catch (cacheEx: Exception) {
                try {
                    db.collection("notificationEvents").document(id).get().await()
                } catch (e: Exception) {
                    if (e.message?.contains("offline", ignoreCase = true) == true) {
                        Log.d(TAG, "[FCM] Client is offline, bypassing document existence check and scheduling for sync.")
                        null
                    } else {
                        throw e
                    }
                }
            }

            if (doc != null && doc.exists()) {
                Log.d(TAG, "[FCM] Notification event already exists for $id, skipping to avoid duplicates.")
                return@withContext false
            }

            val map = hashMapOf(
                "id" to id,
                "title" to title,
                "message" to message,
                "imageUrl" to imageUrl,
                "type" to type,
                "timestamp" to System.currentTimeMillis(),
                "buttonText" to buttonText,
                "actionUrl" to actionUrl,
                "targetSegment" to targetSegment,
                "status" to "PENDING"
            )

            try {
                db.collection("notificationEvents").document(id).set(map, SetOptions.merge()).await()
                Log.d(TAG, "[FCM] Notification event created: $id")
            } catch (e: Exception) {
                if (e.message?.contains("offline", ignoreCase = true) == true) {
                    // Firestore automatically queues offline writes to sync when the connection is restored
                    db.collection("notificationEvents").document(id).set(map, SetOptions.merge())
                    Log.d(TAG, "[FCM] Notification event queued locally for offline sync: $id")
                } else {
                    throw e
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] Error creating notification event: ${e.message}")
            false
        }
    }

    // Firestore Realtime Listener Registrations
    private var catalogListener: ListenerRegistration? = null
    private var episodesListener: ListenerRegistration? = null
    private var channelsListener: ListenerRegistration? = null
    private var featuredListener: ListenerRegistration? = null
    private var metadataListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null
    private var deletedCatalogListener: ListenerRegistration? = null

    private val _deletedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val deletedIds: Set<String> get() = _deletedIds

    private val processedNotificationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val appStartTime = System.currentTimeMillis()
    @Volatile
    private var isFirstNotificationSnapshot = true

    private val _inAppNotificationEvent = MutableSharedFlow<NotificationEntity>(extraBufferCapacity = 10)
    val inAppNotificationEvent: SharedFlow<NotificationEntity> = _inAppNotificationEvent.asSharedFlow()

    private val isSyncStarted = AtomicBoolean(false)
    private var isNetworkOnline = true

    companion object {
        const val ADMIN_EMAIL = "ronaldomazive915@gmail.com"

        @Volatile
        private var instance: FirebaseService? = null

        fun getInstance(context: Context): FirebaseService {
            return instance ?: synchronized(this) {
                instance ?: FirebaseService(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        isNetworkOnline = true
                        _syncStatus.value = SyncStatus.SYNCING
                        serviceScope.launch {
                            delay(1000)
                            _syncStatus.value = SyncStatus.SYNCHRONIZED
                            updateTimestamp()
                            deviceId?.let { id ->
                                registerDeviceWithRetry(id)
                            }
                            startListeningDevices()
                            startListeningRequests()
                        }
                    }

                    override fun onLost(network: Network) {
                        isNetworkOnline = false
                        _syncStatus.value = SyncStatus.OFFLINE
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network Callback setup error: ${e.message}")
        }
    }

    // --- REALTIME CENTRALIZED CATALOG SYNCHRONIZATION ---
    fun startRealtimeSync(dao: PlayFilmeDao) {
        if (!isSyncStarted.compareAndSet(false, true)) {
            Log.d(TAG, "Realtime sync listeners already initialized, skipping duplicate registration.")
            return
        }

        val db = firestore
        if (db == null) {
            Log.w(TAG, "Firestore not available for Realtime sync. Running in local cache mode.")
            _syncStatus.value = SyncStatus.OFFLINE
            return
        }

        _syncStatus.value = SyncStatus.SYNCING

        // 0. Deleted Catalog (Tombstones) Realtime Listener - MUST start before catalog
        deletedCatalogListener = db.collection("deleted_catalog").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Deleted catalog listener error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                for (dc in snapshot.documentChanges) {
                    when (dc.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> _deletedIds.add(dc.document.id)
                        DocumentChange.Type.REMOVED -> _deletedIds.remove(dc.document.id)
                    }
                }
                Log.d(TAG, "[SYNC] Cache de tombstones atualizado: ${_deletedIds.size} itens ignorados.")
            }
        }

        // 1. Catalog Collection Realtime Listener (Movies & Series)
        catalogListener = db.collection("catalog").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Catalog realtime listener error: ${error.message}")
                if (!isNetworkOnline) _syncStatus.value = SyncStatus.OFFLINE
                else _syncStatus.value = SyncStatus.ERROR
                return@addSnapshotListener
            }

            if (snapshot != null) {
                serviceScope.launch {
                    try {
                        val toInsert = mutableListOf<MediaEntity>()
                        val toDeleteIds = mutableListOf<Int>()
                        
                        for (dc in snapshot.documentChanges) {
                            val data = dc.document.data
                            val tmdbId = (data["tmdbId"] as? Long)?.toInt()
                                ?: (data["tmdbId"] as? String)?.toIntOrNull()
                                ?: continue

                            when (dc.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val docId = dc.document.id
                                    val mediaType = data["mediaType"] as? String ?: "movie"
                                    val tombstoneId = if (mediaType == "movie") "movie_$tmdbId" else "tv_$tmdbId"
                                    
                                    if (_deletedIds.contains(docId) || _deletedIds.contains(tombstoneId)) {
                                        Log.w(TAG, "[SYNC] Ignorando re-importação automática de item excluído: $tmdbId ($mediaType)")
                                        continue
                                    }

                                    val titleStr = data["title"] as? String ?: "Sem título"
                                    val originalLang = data["originalLanguage"] as? String ?: ""
                                    val originCount = data["originCountry"] as? String ?: ""
                                    val genresStr = data["genres"] as? String ?: ""
                                    
                                    val entity = MediaEntity(
                                        tmdbId = tmdbId,
                                        title = titleStr,
                                        originalTitle = data["originalTitle"] as? String ?: "",
                                        mediaType = mediaType,
                                        mediaCategory = data["mediaCategory"] as? String ?: "movie",
                                        originalLanguage = originalLang,
                                        originCountry = originCount,
                                        posterPath = data["posterPath"] as? String,
                                        backdropPath = data["backdropPath"] as? String,
                                        overview = data["overview"] as? String ?: "",
                                        releaseYear = data["releaseYear"] as? String ?: "",
                                        rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                                        genres = genresStr,
                                        durationMinutes = (data["durationMinutes"] as? Long)?.toInt() ?: 120,
                                        cast = data["cast"] as? String ?: "",
                                        director = data["director"] as? String ?: "",
                                        seasonsCount = (data["seasonsCount"] as? Long)?.toInt() ?: 1,
                                        episodesCount = (data["episodesCount"] as? Long)?.toInt() ?: 1,
                                        trailerKey = data["trailerKey"] as? String,
                                        isHeroFeatured = data["isHeroFeatured"] as? Boolean ?: false,
                                        restricted18 = data["restricted18"] as? Boolean ?: false,
                                        restricted18UpdatedAt = (data["restricted18UpdatedAt"] as? Long) ?: 0L,
                                        restricted18UpdatedBy = data["restricted18UpdatedBy"] as? String ?: "",
                                        addedAt = (data["addedAt"] as? Long) ?: System.currentTimeMillis()
                                    )
                                    toInsert.add(entity)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    toDeleteIds.add(tmdbId)
                                }
                            }
                        }
                        
                        if (toInsert.isNotEmpty()) {
                            dao.insertMediaList(toInsert)
                        }
                        if (toDeleteIds.isNotEmpty()) {
                            toDeleteIds.forEach { dao.deleteMediaByTmdbId(it) }
                        }
                        
                        _syncStatus.value = SyncStatus.SYNCHRONIZED
                        updateTimestamp()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying catalog snapshot to Room: ${e.message}")
                    }
                }
            }
        }

        // 2. Episodes Collection Realtime Listener
        episodesListener = db.collection("episodes").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            if (snapshot != null) {
                serviceScope.launch {
                    try {
                        for (dc in snapshot.documentChanges) {
                            val data = dc.document.data
                            val mediaTmdbId = (data["mediaTmdbId"] as? Long)?.toInt() ?: continue
                            val seasonNumber = (data["seasonNumber"] as? Long)?.toInt() ?: 1
                            val episodeNumber = (data["episodeNumber"] as? Long)?.toInt() ?: 1

                            when (dc.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val episode = EpisodeEntity(
                                        mediaTmdbId = mediaTmdbId,
                                        seasonNumber = seasonNumber,
                                        episodeNumber = episodeNumber,
                                        title = data["title"] as? String ?: "Episódio $episodeNumber",
                                        overview = data["overview"] as? String ?: "",
                                        duration = data["duration"] as? String ?: "45 min",
                                        stillPath = data["stillPath"] as? String,
                                        videoUrl = data["videoUrl"] as? String,
                                        restricted18 = data["restricted18"] as? Boolean ?: false
                                    )
                                    dao.insertEpisodes(listOf(episode))
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteSingleEpisode(mediaTmdbId, seasonNumber, episodeNumber)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying episodes snapshot: ${e.message}")
                    }
                }
            }
        }

        // 3. Live TV Channels Realtime Listener
        channelsListener = db.collection("live_channels").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            if (snapshot != null) {
                serviceScope.launch {
                    try {
                        for (dc in snapshot.documentChanges) {
                            val data = dc.document.data
                            val id = data["id"] as? String ?: dc.document.id

                            when (dc.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val channel = ChannelEntity(
                                        id = id,
                                        name = data["name"] as? String ?: "Canal",
                                        category = data["category"] as? String ?: "Geral",
                                        logoUrl = data["logoUrl"] as? String ?: "",
                                        streamUrl = data["streamUrl"] as? String ?: "",
                                        isOnline = data["isOnline"] as? Boolean ?: true,
                                        addedAt = (data["addedAt"] as? Long) ?: System.currentTimeMillis()
                                    )
                                    dao.insertChannel(channel)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteChannelById(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying live channels snapshot: ${e.message}")
                    }
                }
            }
        }

        // 4. Featured Content Realtime Listener
        featuredListener = db.collection("featured").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            if (snapshot != null) {
                serviceScope.launch {
                    try {
                        for (dc in snapshot.documentChanges) {
                            val data = dc.document.data
                            val mediaTmdbId = (data["mediaTmdbId"] as? Long)?.toInt()
                                ?: (data["mediaTmdbId"] as? String)?.toIntOrNull()
                                ?: continue

                            when (dc.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val featured = FeaturedMediaEntity(
                                        id = (data["id"] as? Long)?.toInt() ?: 0,
                                        mediaTmdbId = mediaTmdbId,
                                        mediaType = data["mediaType"] as? String ?: "movie",
                                        trailerUrl = data["trailerUrl"] as? String ?: "",
                                        autoPlayTrailer = data["autoPlayTrailer"] as? Boolean ?: true,
                                        displayOrder = (data["displayOrder"] as? Long)?.toInt() ?: 0,
                                        isActive = data["isActive"] as? Boolean ?: true,
                                        createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis(),
                                        updatedAt = (data["updatedAt"] as? Long) ?: System.currentTimeMillis()
                                    )
                                    dao.insertFeaturedMedia(featured)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteFeaturedMediaByTmdbId(mediaTmdbId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying featured snapshot: ${e.message}")
                    }
                }
            }
        }

        // 5. Notifications Realtime Listener
        notificationsListener = db.collection("notifications").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            if (snapshot != null) {
                val isFirstPass = isFirstNotificationSnapshot
                if (isFirstNotificationSnapshot) {
                    isFirstNotificationSnapshot = false
                }

                serviceScope.launch {
                    try {
                        for (dc in snapshot.documentChanges) {
                            val data = dc.document.data
                            val id = data["id"] as? String ?: dc.document.id
                            val title = data["title"] as? String ?: ""
                            val message = data["message"] as? String ?: ""

                            when (dc.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val notification = NotificationEntity(
                                        id = id,
                                        title = title,
                                        message = message,
                                        imageUrl = data["imageUrl"] as? String,
                                        type = data["type"] as? String ?: "AVISO",
                                        timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                                        scheduledAt = (data["scheduledAt"] as? Long) ?: 0L,
                                        buttonText = data["buttonText"] as? String,
                                        actionUrl = data["actionUrl"] as? String,
                                        targetSegment = data["targetSegment"] as? String ?: "ALL",
                                        isActive = data["isActive"] as? Boolean ?: true,
                                        isRead = data["isRead"] as? Boolean ?: false,
                                        isSent = data["isSent"] as? Boolean ?: true
                                    )
                                    dao.insertNotification(notification)

                                    if (dc.type == DocumentChange.Type.ADDED && notification.isActive) {
                                        val isNewToSession = processedNotificationIds.add(id)
                                        // If this is a newly added notification during current session or fresh (< 60s)
                                        if (isNewToSession && (!isFirstPass || (System.currentTimeMillis() - notification.timestamp) < 60_000L)) {
                                            _inAppNotificationEvent.tryEmit(notification)
                                        }
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteNotificationById(id)
                                    processedNotificationIds.remove(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying notifications snapshot: ${e.message}")
                    }
                }
            }
        }

        // 6. Catalog Metadata & Version Listener
        metadataListener = db.collection("catalog_metadata").document("version").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            if (snapshot != null && snapshot.exists()) {
                val ver = snapshot.getLong("version") ?: 100L
                _catalogVersion.value = ver
            }
        }
    }

    // --- CLOUD MUTATION OPERATIONS (ADMIN CLOUD WRITES) ---

    suspend fun sendNotificationToCloud(notification: NotificationEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "id" to notification.id,
                "title" to notification.title,
                "message" to notification.message,
                "imageUrl" to notification.imageUrl,
                "type" to notification.type,
                "timestamp" to notification.timestamp,
                "scheduledAt" to notification.scheduledAt,
                "buttonText" to notification.buttonText,
                "actionUrl" to notification.actionUrl,
                "targetSegment" to notification.targetSegment,
                "isActive" to notification.isActive,
                "isRead" to notification.isRead,
                "isSent" to notification.isSent
            )
            db.collection("notifications").document(notification.id).set(map, SetOptions.merge()).await()
            bumpCatalogVersionInCloud()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification to cloud: ${e.message}")
        }
    }

    suspend fun deleteNotificationFromCloud(id: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("notifications").document(id).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification from cloud: ${e.message}")
        }
    }

    suspend fun markNotificationAsReadInCloud(id: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("notifications").document(id).update("isRead", true).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read in cloud: ${e.message}")
        }
    }

    suspend fun upsertMediaInCloud(entity: MediaEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = if (entity.mediaType == "movie") "movie_${entity.tmdbId}" else "tv_${entity.tmdbId}"

        try {
            val category = if (entity.mediaCategory.isNotBlank()) entity.mediaCategory else MediaClassifier.classifyMedia(entity)
            val mediaMap = hashMapOf(
                "docId" to docId,
                "tmdbId" to entity.tmdbId,
                "title" to entity.title,
                "originalTitle" to entity.originalTitle,
                "mediaType" to entity.mediaType,
                "mediaCategory" to category,
                "originalLanguage" to entity.originalLanguage,
                "originCountry" to entity.originCountry,
                "posterPath" to entity.posterPath,
                "backdropPath" to entity.backdropPath,
                "overview" to entity.overview,
                "releaseYear" to entity.releaseYear,
                "rating" to entity.rating,
                "genres" to entity.genres,
                "durationMinutes" to entity.durationMinutes,
                "cast" to entity.cast,
                "director" to entity.director,
                "seasonsCount" to entity.seasonsCount,
                "episodesCount" to entity.episodesCount,
                "trailerKey" to entity.trailerKey,
                "isHeroFeatured" to entity.isHeroFeatured,
                "restricted18" to entity.restricted18,
                "restricted18UpdatedAt" to entity.restricted18UpdatedAt,
                "restricted18UpdatedBy" to entity.restricted18UpdatedBy,
                "addedAt" to entity.addedAt,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("catalog").document(docId).set(mediaMap, SetOptions.merge()).await()
            val newVersion = System.currentTimeMillis()
            db.collection("catalog_metadata").document("version").set(
                hashMapOf(
                    "version" to newVersion,
                    "lastUpdatedFormatted" to getCurrentTimestamp(),
                    "event" to "CONTENT_UPDATED"
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error upserting media to cloud: ${e.message}")
        }
    }

    suspend fun upsertMediaListInCloud(entities: List<MediaEntity>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        if (entities.isEmpty()) return@withContext

        try {
            // Write in batches of up to 400 to respect Firestore 500 ops limit
            entities.chunked(400).forEach { chunk ->
                val batch = db.batch()
                for (entity in chunk) {
                    val docId = if (entity.mediaType == "movie") "movie_${entity.tmdbId}" else "tv_${entity.tmdbId}"
                    val category = if (entity.mediaCategory.isNotBlank()) entity.mediaCategory else MediaClassifier.classifyMedia(entity)
                    val mediaMap = hashMapOf(
                        "docId" to docId,
                        "tmdbId" to entity.tmdbId,
                        "title" to entity.title,
                        "originalTitle" to entity.originalTitle,
                        "mediaType" to entity.mediaType,
                        "mediaCategory" to category,
                        "originalLanguage" to entity.originalLanguage,
                        "originCountry" to entity.originCountry,
                        "posterPath" to entity.posterPath,
                        "backdropPath" to entity.backdropPath,
                        "overview" to entity.overview,
                        "releaseYear" to entity.releaseYear,
                        "rating" to entity.rating,
                        "genres" to entity.genres,
                        "durationMinutes" to entity.durationMinutes,
                        "cast" to entity.cast,
                        "director" to entity.director,
                        "seasonsCount" to entity.seasonsCount,
                        "episodesCount" to entity.episodesCount,
                        "trailerKey" to entity.trailerKey,
                        "isHeroFeatured" to entity.isHeroFeatured,
                        "addedAt" to entity.addedAt,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    batch.set(db.collection("catalog").document(docId), mediaMap, SetOptions.merge())
                }
                batch.commit().await()
            }

            val newVersion = System.currentTimeMillis()
            db.collection("catalog_metadata").document("version").set(
                hashMapOf(
                    "version" to newVersion,
                    "lastUpdatedFormatted" to getCurrentTimestamp(),
                    "event" to "CONTENT_UPDATED"
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error batch upserting media to cloud: ${e.message}")
        }
    }

    suspend fun deleteMediaFromCloud(tmdbId: Int, mediaType: String = "movie") = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = if (mediaType == "movie") "movie_${tmdbId}" else "tv_${tmdbId}"

        try {
            kotlinx.coroutines.withTimeout(20000L) {
                Log.d(TAG, "[DELETE] Iniciando busca e exclusão definitiva no Firestore para tmdbId=$tmdbId ($mediaType)")
                
                // 1. Localiza documentos por campos para garantir que excluímos o documento correto mesmo se o ID for aleatório
                val catalogQuery = db.collection("catalog")
                    .whereEqualTo("tmdbId", tmdbId)
                    .whereEqualTo("mediaType", mediaType)
                    .get()
                    .await()
                
                if (!catalogQuery.isEmpty) {
                    val batch = db.batch()
                    for (doc in catalogQuery.documents) {
                        batch.delete(doc.reference)
                        Log.d(TAG, "[DELETE] Documento identificado por campos encontrado e marcado para exclusão: ${doc.id}")
                    }
                    batch.commit().await()
                }

                // 2. Exclui documentos usando IDs determinísticos (fallback/legacy)
                db.collection("catalog").document(docId).delete().await()
                db.collection("catalog").document("movie_$tmdbId").delete().await()
                db.collection("catalog").document("tv_$tmdbId").delete().await()
                Log.d(TAG, "[DELETE] Documentos com IDs determinísticos removidos.")

                // 3. Exclui item em destaque se houver
                db.collection("featured").document("tmdb_$tmdbId").delete().await()
                Log.d(TAG, "[DELETE] Documento em destaque excluído.")

                // 4. Exclui todos os episódios se for série
                if (mediaType == "tv" || mediaType == "serie") {
                    Log.d(TAG, "[DELETE] Buscando episódios associados no Firestore para exclusão...")
                    val episodesSnap = db.collection("episodes")
                        .whereEqualTo("mediaTmdbId", tmdbId)
                        .get()
                        .await()
                    
                    if (!episodesSnap.isEmpty) {
                        val batch = db.batch()
                        for (doc in episodesSnap.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                        Log.d(TAG, "[DELETE] ${episodesSnap.size()} episódios excluídos do Firestore.")
                    }
                }

                // 5. Registra na coleção 'deleted_catalog' para evitar re-importação automática (Tombstone)
                db.collection("deleted_catalog").document(docId).set(
                    hashMapOf(
                        "tmdbId" to tmdbId,
                        "mediaType" to mediaType,
                        "deletedAt" to System.currentTimeMillis()
                    )
                ).await()
                Log.d(TAG, "[DELETE] Item registrado na lista de exclusões definitivas.")

                // 6. Atualiza metadados de versão sincronizadamente
                val newVersion = System.currentTimeMillis()
                db.collection("catalog_metadata").document("version").set(
                    hashMapOf(
                        "version" to newVersion,
                        "lastUpdatedFormatted" to getCurrentTimestamp(),
                        "event" to "CONTENT_DELETED"
                    ),
                    SetOptions.merge()
                ).await()
                Log.d(TAG, "[DELETE] Versão do catálogo atualizada na nuvem.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[DELETE] Erro ao excluir mídia da nuvem: ${e.message}", e)
            throw e
        }
    }

    suspend fun getDeletedCatalogIds(): Set<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptySet()
        try {
            val snapshot = db.collection("deleted_catalog").get().await()
            snapshot.documents.map { it.id }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "[FIREBASE] Falha ao obter lista de itens excluídos: ${e.message}")
            emptySet()
        }
    }

    suspend fun removeFromDeletedCatalog(tmdbId: Int, mediaType: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = if (mediaType == "movie") "movie_${tmdbId}" else "tv_${tmdbId}"
        try {
            db.collection("deleted_catalog").document(docId).delete().await()
            Log.d(TAG, "[RESTORE] Item #$tmdbId removido da lista de exclusões (tombstone).")
        } catch (e: Exception) {
            Log.e(TAG, "[RESTORE] Erro ao remover da lista de exclusões: ${e.message}")
        }
    }

    suspend fun deleteMediaBatchFromCloud(items: List<Pair<Int, String>>, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val chunks = items.chunked(100) // 100 itens por lote é extremamente seguro e ideal para progresso real
        
        var completedCount = 0
        Log.d(TAG, "[BATCH-DELETE] Iniciando exclusão em lote de ${items.size} mídias no Firestore.")
        
        for (chunk in chunks) {
            val batch = db.batch()
            for (item in chunk) {
                val tmdbId = item.first
                val mediaType = item.second
                val docId = if (mediaType == "movie") "movie_${tmdbId}" else "tv_${tmdbId}"
                
                // 1. Exclui documentos principais do catálogo
                batch.delete(db.collection("catalog").document(docId))
                batch.delete(db.collection("catalog").document("movie_$tmdbId"))
                batch.delete(db.collection("catalog").document("tv_$tmdbId"))
                
                // 2. Exclui item em destaque se houver
                batch.delete(db.collection("featured").document("tmdb_$tmdbId"))

                // 3. Registra na lista de exclusões (Tombstone)
                batch.set(db.collection("deleted_catalog").document(docId), hashMapOf(
                    "tmdbId" to tmdbId,
                    "mediaType" to mediaType,
                    "deletedAt" to System.currentTimeMillis()
                ))
            }
            
            // Para deletar episódios das séries presentes no lote
            val seriesList = chunk.filter { it.second == "tv" || it.second == "serie" }
            for (series in seriesList) {
                try {
                    val epsSnap = db.collection("episodes")
                        .whereEqualTo("mediaTmdbId", series.first)
                        .get()
                        .await()
                    
                    for (doc in epsSnap.documents) {
                        batch.delete(doc.reference)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[BATCH-DELETE] Erro ao obter episódios da série ${series.first}: ${e.message}")
                }
            }
            
            batch.commit().await()
            completedCount += chunk.size
            onProgress(completedCount)
            Log.d(TAG, "[BATCH-DELETE] Progresso: $completedCount de ${items.size} excluídos.")
        }
        
        // Atualiza versão do catálogo na nuvem de forma síncrona
        try {
            val newVersion = System.currentTimeMillis()
            db.collection("catalog_metadata").document("version").set(
                hashMapOf(
                    "version" to newVersion,
                    "lastUpdatedFormatted" to getCurrentTimestamp(),
                    "event" to "CONTENT_UPDATED"
                ),
                SetOptions.merge()
            ).await()
            Log.d(TAG, "[BATCH-DELETE] Versão do catálogo atualizada na nuvem.")
        } catch (e: Exception) {
            Log.e(TAG, "[BATCH-DELETE] Erro ao atualizar versão: ${e.message}")
        }
    }

    suspend fun upsertEpisodeInCloud(episode: EpisodeEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = "ep_${episode.mediaTmdbId}_s${episode.seasonNumber}_e${episode.episodeNumber}"

        try {
            val epMap = hashMapOf(
                "docId" to docId,
                "mediaTmdbId" to episode.mediaTmdbId,
                "seasonNumber" to episode.seasonNumber,
                "episodeNumber" to episode.episodeNumber,
                "title" to episode.title,
                "overview" to episode.overview,
                "duration" to episode.duration,
                "stillPath" to episode.stillPath,
                "videoUrl" to episode.videoUrl,
                "restricted18" to episode.restricted18,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("episodes").document(docId).set(epMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error upserting episode to cloud: ${e.message}")
        }
    }

    suspend fun upsertEpisodeListInCloud(episodes: List<EpisodeEntity>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        if (episodes.isEmpty()) return@withContext

        try {
            // Write in batches of up to 400
            episodes.chunked(400).forEach { chunk ->
                val batch = db.batch()
                for (episode in chunk) {
                    val docId = "ep_${episode.mediaTmdbId}_s${episode.seasonNumber}_e${episode.episodeNumber}"
                    val epMap = hashMapOf(
                        "docId" to docId,
                        "mediaTmdbId" to episode.mediaTmdbId,
                        "seasonNumber" to episode.seasonNumber,
                        "episodeNumber" to episode.episodeNumber,
                        "title" to episode.title,
                        "overview" to episode.overview,
                        "duration" to episode.duration,
                        "stillPath" to episode.stillPath,
                        "videoUrl" to episode.videoUrl,
                        "restricted18" to episode.restricted18,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    batch.set(db.collection("episodes").document(docId), epMap, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error batch upserting episodes to cloud: ${e.message}")
        }
    }

    suspend fun deleteEpisodeFromCloud(mediaTmdbId: Int, seasonNumber: Int, episodeNumber: Int) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = "ep_${mediaTmdbId}_s${seasonNumber}_e${episodeNumber}"

        try {
            db.collection("episodes").document(docId).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting episode from cloud: ${e.message}")
        }
    }

    suspend fun upsertChannelInCloud(channel: ChannelEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = "channel_${channel.id}"

        try {
            val channelMap = hashMapOf(
                "id" to channel.id,
                "name" to channel.name,
                "category" to channel.category,
                "logoUrl" to channel.logoUrl,
                "streamUrl" to channel.streamUrl,
                "isOnline" to channel.isOnline,
                "addedAt" to channel.addedAt,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("live_channels").document(docId).set(channelMap, SetOptions.merge())
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting channel to cloud: ${e.message}")
        }
    }

    suspend fun deleteChannelFromCloud(channelId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = "channel_$channelId"

        try {
            db.collection("live_channels").document(docId).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting channel from cloud: ${e.message}")
        }
    }

    // --- Player Management ---
    fun startPlayerSourcesListener() {
        val db = firestore ?: return
        if (playerSourcesListener != null) return

        playerSourcesListener = db.collection("playerSources")
            .orderBy("priority", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "PlayerSources listener failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val sources = snapshot.toObjects(PlayerSource::class.java)
                    if (sources.isEmpty()) {
                        _playerSources.value = defaultInitialPlayerSources
                    } else {
                        // Ensure VidSrc is always present if missing from snapshot
                        val hasVidSrc = sources.any { it.id == "vidsrc" || it.name.contains("vidsrc", ignoreCase = true) }
                        val finalSources = if (!hasVidSrc) {
                            sources + defaultInitialPlayerSources.filter { it.id == "vidsrc" }
                        } else {
                            sources
                        }
                        _playerSources.value = finalSources
                    }
                    Log.d(TAG, "[PLAYER] ${_playerSources.value.size} fontes carregadas.")
                }
            }

        playerConfigListener = db.collection("settings").document("playerConfig")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "playerConfig listener error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val config = snapshot.toObject(PlayerConfig::class.java)
                    if (config != null) {
                        _playerConfig.value = config
                        MegaEmbedService.updateConfig(
                            MegaEmbedService.getConfig().copy(
                                defaultPlayer = config.megaEmbed.player,
                                colorHex = config.megaEmbed.color
                            )
                        )
                    }
                }
            }
    }

    fun stopPlayerSourcesListener() {
        playerSourcesListener?.remove()
        playerSourcesListener = null
        playerConfigListener?.remove()
        playerConfigListener = null
    }

    suspend fun upsertPlayerSourceInCloud(source: PlayerSource) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = source.id.ifEmpty { UUID.randomUUID().toString() }
        val finalSource = if (source.id.isEmpty()) source.copy(id = docId) else source
        
        try {
            db.collection("playerSources").document(docId)
                .set(finalSource, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting player source: ${e.message}")
        }
    }

    suspend fun deletePlayerSourceFromCloud(id: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("playerSources").document(id).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting player source: ${e.message}")
        }
    }

    suspend fun setDefaultPlayer(playerId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val batch = db.batch()
            
            // 1. Update the global config (Single Source of Truth) - Use merge to create if not exists
            val configRef = db.collection("settings").document("playerConfig")
            batch.set(configRef, mapOf(
                "defaultPlayerId" to playerId,
                "updatedAt" to System.currentTimeMillis()
            ), SetOptions.merge())

            // Mirror to playerConfig/default
            val altRef = db.collection("playerConfig").document("default")
            batch.set(altRef, mapOf(
                "defaultPlayerId" to playerId,
                "updatedAt" to System.currentTimeMillis()
            ), SetOptions.merge())
            
            // 2. Synchronize isDefault in playerSources for UI/Legacy compatibility
            val currentSources = _playerSources.value
            currentSources.forEach {
                val ref = db.collection("playerSources").document(it.id)
                if (it.id == playerId) {
                    batch.set(ref, mapOf("isDefault" to true, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                } else if (it.isDefault) {
                    batch.set(ref, mapOf("isDefault" to false, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                }
            }
            
            batch.commit().await()
            _playerConfig.value = _playerConfig.value.copy(
                defaultPlayerId = playerId,
                updatedAt = System.currentTimeMillis()
            )
            Log.d(TAG, "[PLAYER] Configuração global atualizada: Player principal é $playerId.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting default player: ${e.message}")
        }
    }

    suspend fun togglePlayerEnabled(playerId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("playerSources").document(playerId)
                .set(mapOf("enabled" to enabled, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
            Log.d(TAG, "[PLAYER] Player $playerId status alterado para $enabled.")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling player status: ${e.message}")
        }
    }

    suspend fun updatePlayerConfig(config: PlayerConfig): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        try {
            val batch = db.batch()
            val configRef = db.collection("settings").document("playerConfig")
            batch.set(configRef, config, SetOptions.merge())

            val altRef = db.collection("playerConfig").document("default")
            batch.set(altRef, config, SetOptions.merge())

            batch.commit().await()
            _playerConfig.value = config
            MegaEmbedService.updateConfig(
                MegaEmbedService.getConfig().copy(
                    defaultPlayer = config.megaEmbed.player,
                    colorHex = config.megaEmbed.color
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating player config: ${e.message}")
            false
        }
    }

    suspend fun updateMegaEmbedConfig(
        megaEmbed: MegaEmbedPlayerConfig,
        adminId: String = "admin@ronycine.app"
    ): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        try {
            val currentConfig = _playerConfig.value
            val newConfig = currentConfig.copy(
                megaEmbed = megaEmbed,
                updatedAt = System.currentTimeMillis(),
                updatedBy = adminId
            )

            val batch = db.batch()
            val configRef = db.collection("settings").document("playerConfig")
            batch.set(configRef, newConfig, SetOptions.merge())

            val altConfigRef = db.collection("playerConfig").document("default")
            batch.set(altConfigRef, newConfig, SetOptions.merge())

            // Also keep mgeb PlayerSource synchronized
            val mgebSource = _playerSources.value.find { it.id == "mgeb" || it.name.contains("Mgeb", ignoreCase = true) }
            if (mgebSource != null) {
                val sourceRef = db.collection("playerSources").document(mgebSource.id)
                batch.set(sourceRef, mapOf(
                    "internalPlayer" to megaEmbed.player,
                    "playerColor" to ("#" + MegaEmbedPlayerType.normalizeColor(megaEmbed.color)),
                    "enabled" to megaEmbed.enabled,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge())
            }

            batch.commit().await()

            // Update in-memory state and cache immediately
            _playerConfig.value = newConfig
            MegaEmbedService.updateConfig(
                MegaEmbedService.getConfig().copy(
                    defaultPlayer = megaEmbed.player,
                    colorHex = megaEmbed.color
                )
            )

            // Audit Log: adminId, action, playerId, provider, oldConfig, newConfig, timestamp
            val auditData = hashMapOf(
                "adminId" to adminId,
                "action" to "PLAYER_CONFIG_UPDATED",
                "playerId" to megaEmbed.player,
                "provider" to "MegaEmbed",
                "oldConfig" to mapOf(
                    "player" to currentConfig.megaEmbed.player,
                    "color" to currentConfig.megaEmbed.color,
                    "enabled" to currentConfig.megaEmbed.enabled
                ),
                "newConfig" to mapOf(
                    "player" to megaEmbed.player,
                    "color" to megaEmbed.color,
                    "enabled" to megaEmbed.enabled
                ),
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("audit_logs").add(auditData).await()

            Log.d(TAG, "[PLAYER] MegaEmbed config salva com sucesso no Firestore: player=${megaEmbed.player}, color=${megaEmbed.color}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating MegaEmbed config: ${e.message}", e)
            false
        }
    }

    suspend fun updateSubtitledPlayerConfig(
        subtitledConfig: SubtitledPlayerConfig,
        adminId: String = "admin@ronycine.app"
    ): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        try {
            val currentConfig = _playerConfig.value
            val newConfig = currentConfig.copy(
                subtitledPlayer = subtitledConfig,
                updatedAt = System.currentTimeMillis(),
                updatedBy = adminId
            )

            val batch = db.batch()
            val configRef = db.collection("settings").document("playerConfig")
            batch.set(configRef, newConfig, SetOptions.merge())

            val altConfigRef = db.collection("playerConfig").document("default")
            batch.set(altConfigRef, newConfig, SetOptions.merge())

            // Also keep vidsrc PlayerSource synchronized in playerSources collection
            val vidsrcSource = _playerSources.value.find { it.id == "vidsrc" || it.name.contains("VidSrc", ignoreCase = true) }
            if (vidsrcSource != null) {
                val sourceRef = db.collection("playerSources").document(vidsrcSource.id)
                batch.set(sourceRef, mapOf(
                    "enabled" to subtitledConfig.enabled,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge())
            }

            batch.commit().await()

            // Update in-memory state immediately
            _playerConfig.value = newConfig

            // Audit Log
            val auditData = hashMapOf(
                "adminId" to adminId,
                "action" to "SUBTITLED_PLAYER_CONFIG_UPDATED",
                "provider" to subtitledConfig.provider,
                "defaultLanguage" to subtitledConfig.defaultLanguage,
                "enabled" to subtitledConfig.enabled,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("audit_logs").add(auditData).await()

            Log.d(TAG, "[PLAYER] Subtitled Player config salva com sucesso no Firestore: provider=${subtitledConfig.provider}, enabled=${subtitledConfig.enabled}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Subtitled Player config: ${e.message}", e)
            false
        }
    }

    suspend fun addAuditLogRemote(action: String, target: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val log = AdminAuditLogEntity(
                action = action,
                targetDeviceId = target,
                timestamp = System.currentTimeMillis()
            )
            db.collection("audit_logs").add(log).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding remote audit log: ${e.message}")
        }
    }

    // --- Featured Media Cloud Sync ---
    suspend fun syncFeaturedItemToCloud(featured: FeaturedMediaEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = "featured_${featured.mediaTmdbId}"

        try {
            val map = hashMapOf(
                "id" to featured.id,
                "mediaTmdbId" to featured.mediaTmdbId,
                "mediaType" to featured.mediaType,
                "trailerUrl" to featured.trailerUrl,
                "autoPlayTrailer" to featured.autoPlayTrailer,
                "displayOrder" to featured.displayOrder,
                "isActive" to featured.isActive,
                "createdAt" to featured.createdAt,
                "updatedAt" to featured.updatedAt
            )
            db.collection("featured").document(docId).set(map, SetOptions.merge())
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing featured item to cloud: ${e.message}")
        }
    }

    suspend fun deleteFeaturedItemFromCloud(mediaTmdbId: Int) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val docId = "featured_$mediaTmdbId"

        try {
            db.collection("featured").document(docId).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting featured item from cloud: ${e.message}")
        }
    }


    fun bumpCatalogVersionInCloud() {
        val db = firestore ?: return
        val newVersion = System.currentTimeMillis()
        db.collection("catalog_metadata").document("version").set(
            hashMapOf(
                "version" to newVersion,
                "lastUpdatedFormatted" to getCurrentTimestamp(),
                "event" to "CONTENT_UPDATED"
            ),
            SetOptions.merge()
        )
    }

    suspend fun forceRealtimeSyncCheck(dao: PlayFilmeDao) = withContext(Dispatchers.IO) {
        val db = firestore
        if (db == null) {
            Log.w(TAG, "[SYNC] Firestore não disponível para sincronização forçada.")
            _syncStatus.value = if (!isNetworkOnline) SyncStatus.OFFLINE else SyncStatus.SYNCHRONIZED
            return@withContext
        }
        _syncStatus.value = SyncStatus.SYNCING

        try {
            // 1. Fetch catalog from cloud with safety timeout
            val cloudIds = mutableSetOf<Int>()
            kotlinx.coroutines.withTimeoutOrNull(15000L) {
                val catalogSnap = db.collection("catalog").get().await()
                for (doc in catalogSnap.documents) {
                    val data = doc.data ?: continue
                    val tmdbId = (data["tmdbId"] as? Long)?.toInt()
                        ?: (data["tmdbId"] as? String)?.toIntOrNull()
                        ?: continue

                    cloudIds.add(tmdbId)

                    val mediaType = data["mediaType"] as? String ?: "movie"
                    val catFromCloud = data["mediaCategory"] as? String
                    val originalLang = data["originalLanguage"] as? String ?: ""
                    val originCount = data["originCountry"] as? String ?: ""
                    val genresStr = data["genres"] as? String ?: ""
                    val titleStr = data["title"] as? String ?: "Sem título"
                    val mediaCat = if (!catFromCloud.isNullOrBlank()) {
                        catFromCloud
                    } else {
                        MediaClassifier.classifyMedia(
                            mediaType = mediaType,
                            genreIds = emptyList(),
                            genreNames = genresStr,
                            originalLanguage = originalLang,
                            originCountry = originCount.split(",").filter { it.isNotBlank() },
                            title = titleStr
                        )
                    }

                    val entity = MediaEntity(
                        tmdbId = tmdbId,
                        title = titleStr,
                        originalTitle = data["originalTitle"] as? String ?: "",
                        mediaType = mediaType,
                        mediaCategory = mediaCat,
                        originalLanguage = originalLang,
                        originCountry = originCount,
                        posterPath = data["posterPath"] as? String,
                        backdropPath = data["backdropPath"] as? String,
                        overview = data["overview"] as? String ?: "",
                        releaseYear = data["releaseYear"] as? String ?: "",
                        rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                        genres = genresStr,
                        durationMinutes = (data["durationMinutes"] as? Long)?.toInt() ?: 120,
                        cast = data["cast"] as? String ?: "",
                        director = data["director"] as? String ?: "",
                        seasonsCount = (data["seasonsCount"] as? Long)?.toInt() ?: 1,
                        episodesCount = (data["episodesCount"] as? Long)?.toInt() ?: 1,
                        trailerKey = data["trailerKey"] as? String,
                        isHeroFeatured = data["isHeroFeatured"] as? Boolean ?: false,
                        addedAt = (data["addedAt"] as? Long) ?: System.currentTimeMillis()
                    )
                    dao.insertMedia(entity)
                }

                // RECONCILIATION / PRUNING: Delete any local catalog items NOT present in the Firestore catalog
                val localMedia = dao.getAllMediaSync()
                for (local in localMedia) {
                    if (!cloudIds.contains(local.tmdbId)) {
                        Log.d(TAG, "[SYNC-PRUNE] Removendo item do catálogo local pois foi excluído da nuvem: '${local.title}' (ID ${local.tmdbId})")
                        dao.deleteMediaByTmdbId(local.tmdbId)
                        dao.deleteEpisodesByMediaId(local.tmdbId)
                        dao.deleteFromMyListGlobal(local.tmdbId)
                        dao.deleteFromWatchHistoryGlobal(local.tmdbId)
                    }
                }
            }

            // 2. Fetch featured from cloud with safety timeout
            kotlinx.coroutines.withTimeoutOrNull(8000L) {
                val featuredSnap = db.collection("featured").get().await()
                for (doc in featuredSnap.documents) {
                    val data = doc.data ?: continue
                    val mediaTmdbId = (data["mediaTmdbId"] as? Long)?.toInt()
                        ?: (data["mediaTmdbId"] as? String)?.toIntOrNull()
                        ?: continue

                    val featured = FeaturedMediaEntity(
                        id = (data["id"] as? Long)?.toInt() ?: 0,
                        mediaTmdbId = mediaTmdbId,
                        mediaType = data["mediaType"] as? String ?: "movie",
                        trailerUrl = data["trailerUrl"] as? String ?: "",
                        autoPlayTrailer = data["autoPlayTrailer"] as? Boolean ?: true,
                        displayOrder = (data["displayOrder"] as? Long)?.toInt() ?: 0,
                        isActive = data["isActive"] as? Boolean ?: true,
                        createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis(),
                        updatedAt = (data["updatedAt"] as? Long) ?: System.currentTimeMillis()
                    )
                    dao.insertFeaturedMedia(featured)
                }
            }

            // 3. Fetch channels from cloud with safety timeout
            kotlinx.coroutines.withTimeoutOrNull(8000L) {
                val channelsSnap = db.collection("live_channels").get().await()
                for (doc in channelsSnap.documents) {
                    val data = doc.data ?: continue
                    val id = data["id"] as? String ?: doc.id
                    val channel = ChannelEntity(
                        id = id,
                        name = data["name"] as? String ?: "Canal",
                        category = data["category"] as? String ?: "Geral",
                        logoUrl = data["logoUrl"] as? String ?: "",
                        streamUrl = data["streamUrl"] as? String ?: "",
                        isOnline = data["isOnline"] as? Boolean ?: true,
                        addedAt = (data["addedAt"] as? Long) ?: System.currentTimeMillis()
                    )
                    dao.insertChannel(channel)
                }
            }

            // 4. Fetch metadata
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                val metaSnap = db.collection("catalog_metadata").document("version").get().await()
                if (metaSnap.exists()) {
                    val ver = metaSnap.getLong("version") ?: System.currentTimeMillis()
                    _catalogVersion.value = ver
                } else {
                    bumpCatalogVersionInCloud()
                }
            }

            _syncStatus.value = SyncStatus.SYNCHRONIZED
            updateTimestamp()
        } catch (e: Exception) {
            Log.e(TAG, "Error performing forced global sync: ${e.message}")
            _syncStatus.value = SyncStatus.ERROR
        } finally {
            if (_syncStatus.value == SyncStatus.SYNCING) {
                _syncStatus.value = SyncStatus.SYNCHRONIZED
            }
        }
    }

    /**
     * Consulta estrita do Firestore para Animes: where("mediaCategory", "==", "anime")
     */
    suspend fun fetchAnimesFromFirestore(): List<MediaEntity> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptyList()
        val dao = AppDatabase.getInstance(appContext).playFilmeDao()
        try {
            val querySnapshot = db.collection("catalog")
                .whereEqualTo("mediaCategory", "anime")
                .get()
                .await()
            val list = mutableListOf<MediaEntity>()
            for (doc in querySnapshot.documents) {
                val data = doc.data ?: continue
                val tmdbId = (data["tmdbId"] as? Long)?.toInt()
                    ?: (data["tmdbId"] as? String)?.toIntOrNull()
                    ?: continue
                val entity = MediaEntity(
                    tmdbId = tmdbId,
                    title = data["title"] as? String ?: "Sem título",
                    originalTitle = data["originalTitle"] as? String ?: "",
                    mediaType = data["mediaType"] as? String ?: "tv",
                    mediaCategory = "anime",
                    originalLanguage = data["originalLanguage"] as? String ?: "ja",
                    originCountry = data["originCountry"] as? String ?: "JP",
                    posterPath = data["posterPath"] as? String,
                    backdropPath = data["backdropPath"] as? String,
                    overview = data["overview"] as? String ?: "",
                    releaseYear = data["releaseYear"] as? String ?: "",
                    rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                    genres = data["genres"] as? String ?: "Animação, Ação",
                    durationMinutes = (data["durationMinutes"] as? Long)?.toInt() ?: 24,
                    cast = data["cast"] as? String ?: "",
                    director = data["director"] as? String ?: "",
                    seasonsCount = (data["seasonsCount"] as? Long)?.toInt() ?: 1,
                    episodesCount = (data["episodesCount"] as? Long)?.toInt() ?: 12,
                    trailerKey = data["trailerKey"] as? String,
                    isHeroFeatured = data["isHeroFeatured"] as? Boolean ?: false,
                    restricted18 = data["restricted18"] as? Boolean ?: false,
                    restricted18UpdatedAt = (data["restricted18UpdatedAt"] as? Long) ?: 0L,
                    restricted18UpdatedBy = data["restricted18UpdatedBy"] as? String ?: "",
                    addedAt = (data["addedAt"] as? Long) ?: System.currentTimeMillis()
                )
                list.add(entity)
                dao.insertMedia(entity)
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar animes do Firestore: ${e.message}")
            emptyList()
        }
    }

    /**
     * Consulta estrita do Firestore para Doramas: where("mediaCategory", "==", "dorama")
     */
    suspend fun fetchDoramasFromFirestore(): List<MediaEntity> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptyList()
        val dao = AppDatabase.getInstance(appContext).playFilmeDao()
        try {
            val querySnapshot = db.collection("catalog")
                .whereEqualTo("mediaCategory", "dorama")
                .get()
                .await()
            val list = mutableListOf<MediaEntity>()
            for (doc in querySnapshot.documents) {
                val data = doc.data ?: continue
                val tmdbId = (data["tmdbId"] as? Long)?.toInt()
                    ?: (data["tmdbId"] as? String)?.toIntOrNull()
                    ?: continue
                val entity = MediaEntity(
                    tmdbId = tmdbId,
                    title = data["title"] as? String ?: "Sem título",
                    originalTitle = data["originalTitle"] as? String ?: "",
                    mediaType = data["mediaType"] as? String ?: "tv",
                    mediaCategory = "dorama",
                    originalLanguage = data["originalLanguage"] as? String ?: "ko",
                    originCountry = data["originCountry"] as? String ?: "KR",
                    posterPath = data["posterPath"] as? String,
                    backdropPath = data["backdropPath"] as? String,
                    overview = data["overview"] as? String ?: "",
                    releaseYear = data["releaseYear"] as? String ?: "",
                    rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                    genres = data["genres"] as? String ?: "Drama, Romance",
                    durationMinutes = (data["durationMinutes"] as? Long)?.toInt() ?: 60,
                    cast = data["cast"] as? String ?: "",
                    director = data["director"] as? String ?: "",
                    seasonsCount = (data["seasonsCount"] as? Long)?.toInt() ?: 1,
                    episodesCount = (data["episodesCount"] as? Long)?.toInt() ?: 16,
                    trailerKey = data["trailerKey"] as? String,
                    isHeroFeatured = data["isHeroFeatured"] as? Boolean ?: false,
                    restricted18 = data["restricted18"] as? Boolean ?: false,
                    restricted18UpdatedAt = (data["restricted18UpdatedAt"] as? Long) ?: 0L,
                    restricted18UpdatedBy = data["restricted18UpdatedBy"] as? String ?: "",
                    addedAt = (data["addedAt"] as? Long) ?: System.currentTimeMillis()
                )
                list.add(entity)
                dao.insertMedia(entity)
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar doramas do Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun publishAndBumpCatalogVersion() = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val newVersion = System.currentTimeMillis()
        try {
            kotlinx.coroutines.withTimeoutOrNull(10000L) {
                db.collection("catalog_metadata").document("version").set(
                    hashMapOf(
                        "version" to newVersion,
                        "lastUpdatedFormatted" to getCurrentTimestamp(),
                        "event" to "CONTENT_UPDATED"
                    ),
                    SetOptions.merge()
                ).await()
            }
            _catalogVersion.value = newVersion
            updateTimestamp()
            logAdminAction(ADMIN_EMAIL, "PUBLISH_CATALOG", "ALL_DEVICES", "SUCESSO")
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing catalog version: ${e.message}")
        }
    }

    private fun updateTimestamp() {
        _lastSyncFormatted.value = getCurrentTimestamp()
    }

    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }

    fun getBrowserInfo(): String {
        return "RONYCINE Mobile App v2.0"
    }

    // --- Admin Audit Logs Cloud Sync ---
    suspend fun logAdminAction(
        adminEmail: String,
        action: String,
        target: String,
        result: String
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val logId = "log_${System.currentTimeMillis()}"

        try {
            val logData = hashMapOf(
                "adminId" to adminEmail,
                "adminEmail" to adminEmail,
                "action" to action,
                "targetDeviceId" to target,
                "timestamp" to System.currentTimeMillis(),
                "result" to result,
                "details" to "Ação executada via painel administrativo"
            )
            db.collection("adminAuditLogs").document(logId).set(logData, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "[AUDIT] Erro ao registrar ação: ${e.message}")
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun stopRealtimeSync() {
        catalogListener?.remove()
        episodesListener?.remove()
        channelsListener?.remove()
        metadataListener?.remove()
        catalogListener = null
        episodesListener = null
        channelsListener = null
        metadataListener = null
        isSyncStarted.set(false)
    }

    suspend fun batchUpsertMedia(mediaList: List<MediaEntity>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        if (mediaList.isEmpty()) return@withContext

        try {
            val chunks = mediaList.chunked(250)
            for (chunk in chunks) {
                val batch = db.batch()
                for (entity in chunk) {
                    val docId = if (entity.mediaType == "movie") "movie_${entity.tmdbId}" else "tv_${entity.tmdbId}"
                    val mediaMap = hashMapOf(
                        "docId" to docId,
                        "tmdbId" to entity.tmdbId,
                        "title" to entity.title,
                        "originalTitle" to entity.originalTitle,
                        "mediaType" to entity.mediaType,
                        "posterPath" to entity.posterPath,
                        "backdropPath" to entity.backdropPath,
                        "overview" to entity.overview,
                        "releaseYear" to entity.releaseYear,
                        "rating" to entity.rating,
                        "genres" to entity.genres,
                        "durationMinutes" to entity.durationMinutes,
                        "cast" to entity.cast,
                        "director" to entity.director,
                        "seasonsCount" to entity.seasonsCount,
                        "episodesCount" to entity.episodesCount,
                        "trailerKey" to entity.trailerKey,
                        "isHeroFeatured" to entity.isHeroFeatured,
                        "addedAt" to entity.addedAt,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    val docRef = db.collection("catalog").document(docId)
                    batch.set(docRef, mediaMap, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in batchUpsertMedia: ${e.message}")
        }
    }

    suspend fun batchUpsertEpisodes(episodes: List<EpisodeEntity>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        if (episodes.isEmpty()) return@withContext

        try {
            val chunks = episodes.chunked(250)
            for (chunk in chunks) {
                val batch = db.batch()
                for (episode in chunk) {
                    val docId = "ep_${episode.mediaTmdbId}_s${episode.seasonNumber}_e${episode.episodeNumber}"
                    val epMap = hashMapOf(
                        "docId" to docId,
                        "mediaTmdbId" to episode.mediaTmdbId,
                        "seasonNumber" to episode.seasonNumber,
                        "episodeNumber" to episode.episodeNumber,
                        "title" to episode.title,
                        "overview" to episode.overview,
                        "duration" to episode.duration,
                        "stillPath" to episode.stillPath,
                        "videoUrl" to episode.videoUrl,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    val docRef = db.collection("episodes").document(docId)
                    batch.set(docRef, epMap, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in batchUpsertEpisodes: ${e.message}")
        }
    }

    suspend fun batchUpsertChannels(channels: List<ChannelEntity>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        if (channels.isEmpty()) return@withContext

        try {
            val chunks = channels.chunked(250)
            for (chunk in chunks) {
                val batch = db.batch()
                for (channel in chunk) {
                    val docId = "channel_${channel.id}"
                    val channelMap = hashMapOf(
                        "id" to channel.id,
                        "name" to channel.name,
                        "category" to channel.category,
                        "logoUrl" to channel.logoUrl,
                        "streamUrl" to channel.streamUrl,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    val docRef = db.collection("live_channels").document(docId)
                    batch.set(docRef, channelMap, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in batchUpsertChannels: ${e.message}")
        }
    }

    suspend fun batchUpsertFeatured(featuredList: List<FeaturedMediaEntity>) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        if (featuredList.isEmpty()) return@withContext

        try {
            val chunks = featuredList.chunked(250)
            for (chunk in chunks) {
                val batch = db.batch()
                for (entity in chunk) {
                    val docRef = db.collection("featured_media").document("feat_${entity.mediaTmdbId}")
                    val map = hashMapOf(
                        "mediaTmdbId" to entity.mediaTmdbId,
                        "mediaType" to entity.mediaType,
                        "trailerUrl" to entity.trailerUrl,
                        "autoPlayTrailer" to entity.autoPlayTrailer,
                        "displayOrder" to entity.displayOrder,
                        "isActive" to entity.isActive,
                        "updatedAt" to entity.updatedAt
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in batchUpsertFeatured: ${e.message}")
        }
    }

    private fun startListeningAuth() {
        val auth = FirebaseAuth.getInstance()
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser != null) {
                _profilesLoaded.value = false
                startListeningUserProfile(firebaseUser.uid)
                
                // Verifica Custom Claims sempre que o estado da autenticação mudar
                serviceScope.launch {
                    try {
                        val tokenResult = firebaseUser.getIdToken(true).await()
                        val role = tokenResult.claims["role"] as? String
                        val isAdmin = tokenResult.claims["admin"] as? Boolean ?: false
                        val isFounderEmail = firebaseUser.email?.lowercase() == "ronaldomazive915@gmail.com"
                        
                        Log.d(TAG, "[AUTH] Token atualizado no listener. Role: $role, Admin: $isAdmin, FounderEmail: $isFounderEmail")
                        
                        if (role == "FOUNDER" || isAdmin || isFounderEmail) {
                            val db = obtainFirestore()
                            db?.collection("users")?.document(firebaseUser.uid)?.update(
                                "role", "FOUNDER",
                                "updatedAt", System.currentTimeMillis()
                            )?.await()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[AUTH] Erro ao verificar claims no listener: ${e.message}")
                    }
                }
            } else {
                userProfileListener?.remove()
                userProfileListener = null
                _currentUser.value = null
                _isCurrentDeviceAdminAuthorized.value = false
                updateAdminAuthorization()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun startListeningUserProfile(uid: String) {
        val db = obtainFirestore() ?: return
        userProfileListener?.remove()
        userProfileListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to user profile: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    var user = snapshot.toObject(UserEntity::class.java)
                    if (user != null && user.email.equals("ronaldomazive915@gmail.com", ignoreCase = true)) {
                        user = user.copy(isVerified = true, role = "FOUNDER")
                    }
                    _currentUser.value = user
                    updateAdminAuthorization()
                    Log.d(TAG, "[AUTH] Perfil carregado. Email: ${user?.email}, Role: ${user?.role}")
                    if (user != null && user.email.equals("ronaldomazive915@gmail.com", ignoreCase = true)) {
                        ensureFounderUsernameSync(user.uid, user.email)
                    }
                } else {
                    // Se o perfil não existe mas o usuário está autenticado, criamos um básico
                    val fbUser = FirebaseAuth.getInstance().currentUser
                    if (fbUser != null && fbUser.uid == uid) {
                        serviceScope.launch {
                            val isFounderEmail = fbUser.email?.lowercase() == "ronaldomazive915@gmail.com"
                            val role = if (isFounderEmail) "FOUNDER" else "USER"
                            val newUser = UserEntity(
                                uid = fbUser.uid,
                                email = fbUser.email ?: "",
                                displayName = fbUser.displayName ?: "Usuário",
                                photoUrl = fbUser.photoUrl?.toString(),
                                role = role,
                                isVerified = isFounderEmail,
                                deviceId = getOrGeneratePersistentDeviceId()
                            )
                            createUserProfile(newUser)
                        }
                    }
                }
            }
        
        startListeningProfilesList(uid)
    }

    private fun getSavedActiveProfileId(uid: String): String? {
        val prefs = appContext.getSharedPreferences("ronycine_profiles_prefs", Context.MODE_PRIVATE)
        val id = prefs.getString("active_profile_id_$uid", null)
        Log.d(TAG, "[PROFILE] Retrieved saved active profile ID for $uid: $id")
        return id
    }

    private fun saveActiveProfileId(uid: String, profileId: String?) {
        Log.d(TAG, "[PROFILE] Saving active profile ID for $uid: $profileId")
        val prefs = appContext.getSharedPreferences("ronycine_profiles_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("active_profile_id_$uid", profileId).apply()
    }

    private fun startListeningProfilesList(uid: String) {
        val db = obtainFirestore() ?: return
        profilesListListener?.remove()
        profilesListListener = db.collection("users").document(uid).collection("profiles")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to profiles list: ${error.message}")
                    _profilesLoaded.value = true // Even on error, we consider "loaded" to unblock UI
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val isFounder = FirebaseAuth.getInstance().currentUser?.email?.equals("ronaldomazive915@gmail.com", ignoreCase = true) == true
                    val list = snapshot.toObjects(UserProfile::class.java)
                        .sortedBy { it.createdAt }
                        .map { prof ->
                            if (isFounder && (prof.name.equals("ronaldo_skies", ignoreCase = true) || prof.username.equals("@ronyskies", ignoreCase = true) || prof.isDefault)) {
                                prof.copy(isVerified = true)
                            } else {
                                prof
                            }
                        }
                    _userProfiles.value = list
                    
                    // Identifica o ID do perfil que deve estar ativo
                    val targetProfileId = _activeProfile.value?.id ?: getSavedActiveProfileId(uid)
                    val matchedProfile = if (targetProfileId != null) {
                        list.find { it.id == targetProfileId }
                    } else null

                    val effectiveProfile = matchedProfile 
                        ?: list.find { it.isDefault } 
                        ?: list.firstOrNull()

                    _activeProfile.value = effectiveProfile
                    if (effectiveProfile != null) {
                        saveActiveProfileId(uid, effectiveProfile.id)
                    }
                    _profilesLoaded.value = true
                }
            }
    }

    fun selectProfile(profile: UserProfile?) {
        _activeProfile.value = profile
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            saveActiveProfileId(uid, profile?.id)
        }
        if (profile != null) {
            if (!profile.language.isNullOrBlank()) {
                com.example.util.LanguageManager.setAppLanguage(appContext, profile.language, source = profile.languageSource ?: "manual")
            }
            if (!profile.preferredPlayerLanguage.isNullOrBlank()) {
                com.example.util.LanguageManager.setPreferredPlayerLanguage(appContext, profile.preferredPlayerLanguage)
            }
        }
        Log.d(TAG, "[PROFILE] Perfil selecionado: ${profile?.name} (${profile?.id})")
    }

    suspend fun createProfile(name: String, avatarUrl: String?, avatarType: String, photoUrl: String? = null, avatarId: String = ""): Result<UserProfile> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore indisponível"))
        
        try {
            val profilesRef = db.collection("users").document(uid).collection("profiles")
            val id = profilesRef.document().id
            val isDefault = _userProfiles.value.isEmpty()
            val isFounder = FirebaseAuth.getInstance().currentUser?.email?.equals("ronaldomazive915@gmail.com", ignoreCase = true) == true
            val isVerified = isFounder && (name.trim().equals("ronaldo_skies", ignoreCase = true) || isDefault)
            
            val profile = UserProfile(
                id = id,
                userId = uid,
                name = name.trim(),
                avatarUrl = avatarUrl,
                photoUrl = photoUrl,
                avatarType = avatarType,
                avatarId = avatarId,
                isDefault = isDefault,
                language = com.example.util.LanguageManager.appLanguage.value,
                preferredPlayerLanguage = com.example.util.LanguageManager.preferredPlayerLanguage.value,
                languageSource = com.example.util.LanguageManager.languageSource.value,
                isVerified = isVerified
            )
            
            profilesRef.document(id).set(profile).await()
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateActiveProfileLanguage(language: String, playerLanguage: String, source: String = "manual"): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val currentProfile = _activeProfile.value ?: return@withContext Result.failure(Exception("Nenhum perfil ativo"))
        val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore indisponível"))

        try {
            val updated = currentProfile.copy(
                language = language,
                preferredPlayerLanguage = playerLanguage,
                languageSource = source,
                updatedAt = System.currentTimeMillis()
            )
            db.collection("users").document(uid).collection("profiles").document(currentProfile.id)
                .set(updated, com.google.firebase.firestore.SetOptions.merge()).await()
            _activeProfile.value = updated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore indisponível"))
        
        try {
            val existing = _userProfiles.value.firstOrNull { it.id == profile.id }
            val isFounder = FirebaseAuth.getInstance().currentUser?.email?.equals("ronaldomazive915@gmail.com", ignoreCase = true) == true
            val isVerified = if (isFounder && (profile.name.equals("ronaldo_skies", ignoreCase = true) || profile.isDefault)) {
                true
            } else {
                existing?.isVerified ?: false
            }
            val finalProfile = profile.copy(
                isVerified = isVerified,
                updatedAt = System.currentTimeMillis()
            )
            db.collection("users").document(uid).collection("profiles").document(profile.id)
                .set(finalProfile, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfile(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore indisponível"))
        
        try {
            db.collection("users").document(uid).collection("profiles").document(profileId).delete().await()
            if (_activeProfile.value?.id == profileId) {
                _activeProfile.value = null
                saveActiveProfileId(uid, null)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadApkToStorage(versionName: String, file: java.io.File): Result<String> = withContext(Dispatchers.IO) {
        val storage = obtainStorage() ?: return@withContext Result.failure(Exception("Storage indisponível"))
        try {
            val path = "app-updates/android/$versionName/RONYCINE-$versionName.apk"
            val ref = storage.reference.child(path)
            ref.putFile(android.net.Uri.fromFile(file)).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileAvatar(profileId: String, imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val storage = obtainStorage() ?: return@withContext Result.failure(Exception("Firebase Storage não disponível"))
        
        Log.d(TAG, "[PROFILE_PHOTO] Iniciando upload para Storage. UID: $uid, ProfileID: $profileId, Tamanho: ${imageBytes.size} bytes")
        
        try {
            val path = "profilePhotos/$uid/$profileId.jpg"
            val ref = storage.reference.child(path)
            
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("uid", uid)
                .setCustomMetadata("profileId", profileId)
                .build()
            
            Log.d(TAG, "[PROFILE_PHOTO] Realizando putBytes em: $path")
            ref.putBytes(imageBytes, metadata).await()
            
            Log.d(TAG, "[PROFILE_PHOTO] Upload concluído. Obtendo download URL...")
            val downloadUrl = ref.downloadUrl.await().toString()
            
            Log.d(TAG, "[PROFILE_PHOTO] Sucesso! URL obtida: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: com.google.firebase.storage.StorageException) {
            Log.e(TAG, "[PROFILE_PHOTO ERROR] Erro do Storage: code=${e.errorCode}, message=${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "[PROFILE_PHOTO ERROR] Erro inesperado no upload: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteProfileAvatar(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val storage = obtainStorage() ?: return@withContext Result.failure(Exception("Storage indisponível"))
        try {
            val path = "profilePhotos/$uid/$profileId.jpg"
            val ref = storage.reference.child(path)
            ref.delete().await()
            Log.d(TAG, "[PROFILE_PHOTO] Foto deletada do Storage: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "[PROFILE_PHOTO] Falha ao deletar foto do Storage (pode não existir): ${e.message}")
            Result.success(Unit) // Do not fail overall deletion if file doesn't exist
        }
    }

    suspend fun removeProfilePhoto(profile: UserProfile): Result<UserProfile> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Firestore indisponível"))

        try {
            // Delete from storage
            deleteProfileAvatar(profile.id)

            val updatedProfile = profile.copy(
                avatarUrl = null,
                photoUrl = null,
                avatarType = "DEFAULT",
                avatarId = "",
                updatedAt = System.currentTimeMillis()
            )

            db.collection("users").document(uid).collection("profiles").document(profile.id)
                .set(updatedProfile, SetOptions.merge()).await()

            if (_activeProfile.value?.id == profile.id) {
                _activeProfile.value = updatedProfile
            }

            Log.d(TAG, "[PROFILE_PHOTO] Foto removida com sucesso para o perfil: ${profile.id}")
            Result.success(updatedProfile)
        } catch (e: Exception) {
            Log.e(TAG, "[PROFILE_PHOTO ERROR] Erro ao remover foto: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createUserProfile(user: UserEntity) = withContext(Dispatchers.IO) {
        val db = obtainFirestore() ?: return@withContext
        try {
            db.collection("users").document(user.uid).set(user, SetOptions.merge()).await()
            Log.d(TAG, "User profile created/updated: ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user profile: ${e.message}")
        }
    }

    suspend fun checkUsernameAvailability(rawUsername: String, currentUid: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val validation = com.example.util.UsernameUtils.validate(rawUsername)
            if (validation is com.example.util.UsernameValidationResult.Invalid) {
                return@withContext Result.failure(Exception(validation.reason))
            }

            val norm = com.example.util.UsernameUtils.normalize(rawUsername)
            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Serviço de banco de dados indisponível."))

            // 1. Consulta no índice primário atômico 'usernames/{usernameNormalized}'
            val snapUsernameDoc = db.collection("usernames").document(norm).get().await()
            if (snapUsernameDoc.exists()) {
                val recordUid = snapUsernameDoc.getString("uid")
                // Se pertence ao próprio usuário logado, considera disponível
                if (currentUid != null && recordUid == currentUid) {
                    return@withContext Result.success(true)
                }
                return@withContext Result.success(false)
            }

            // 2. Consulta de segurança na coleção 'users'
            val snapUsers = db.collection("users")
                .whereEqualTo("usernameNormalized", norm)
                .limit(1)
                .get()
                .await()

            if (!snapUsers.isEmpty) {
                val userDoc = snapUsers.documents.first()
                if (currentUid != null && userDoc.id == currentUid) {
                    return@withContext Result.success(true)
                }
                return@withContext Result.success(false)
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar disponibilidade de username: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun claimOrUpdateUsername(rawUsername: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure(Exception("Usuário não autenticado."))
        val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Banco de dados indisponível."))

        val validation = com.example.util.UsernameUtils.validate(rawUsername)
        if (validation is com.example.util.UsernameValidationResult.Invalid) {
            return@withContext Result.failure(Exception(validation.reason))
        }

        val norm = com.example.util.UsernameUtils.normalize(rawUsername)
        val display = com.example.util.UsernameUtils.formatDisplay(rawUsername)

        try {
            val userRef = db.collection("users").document(uid)
            val newUsernameRef = db.collection("usernames").document(norm)

            val updatedUser = db.runTransaction { transaction ->
                val userDoc = transaction.get(userRef)
                if (!userDoc.exists()) {
                    throw Exception("Perfil de usuário não encontrado.")
                }

                val currentUserObj = userDoc.toObject(UserEntity::class.java) ?: throw Exception("Erro ao processar dados do usuário.")
                val oldNorm = currentUserObj.usernameNormalized.ifBlank {
                    com.example.util.UsernameUtils.normalize(currentUserObj.username)
                }

                // Se não houve alteração
                if (oldNorm.isNotBlank() && oldNorm == norm && currentUserObj.username == display) {
                    return@runTransaction currentUserObj
                }

                // Verificação de Limite de Alterações (Cooldown)
                // Se já tinha um username e está trocando por um diferente:
                if (oldNorm.isNotBlank() && oldNorm != norm) {
                    val isPrivileged = currentUserObj.role == "FOUNDER" || 
                                       currentUserObj.role == "ADMIN" || 
                                       currentUserObj.email.lowercase() == "ronaldomazive915@gmail.com"
                    
                    if (!isPrivileged && currentUserObj.lastUsernameChangeAt > 0L) {
                        val cooldownMillis = 30L * 24 * 60 * 60 * 1000L // 30 dias
                        val timeSinceLastChange = System.currentTimeMillis() - currentUserObj.lastUsernameChangeAt
                        if (timeSinceLastChange < cooldownMillis) {
                            val daysLeft = kotlin.math.ceil((cooldownMillis - timeSinceLastChange).toDouble() / (24 * 60 * 60 * 1000.0)).toInt().coerceAtLeast(1)
                            throw Exception("O nome de usuário só pode ser alterado a cada 30 dias. Próxima alteração disponível em $daysLeft dia(s).")
                        }
                    }
                }

                // Verifica se o novo username já pertence a outro usuário
                val usernameDoc = transaction.get(newUsernameRef)
                if (usernameDoc.exists()) {
                    val ownerUid = usernameDoc.getString("uid")
                    if (ownerUid != null && ownerUid != uid) {
                        throw Exception("O nome de usuário $display já está em uso por outra conta.")
                    }
                }

                val record = UsernameRecord(
                    uid = uid,
                    username = display,
                    usernameNormalized = norm,
                    email = currentUserObj.email,
                    createdAt = System.currentTimeMillis()
                )

                // Salva o novo registro de username no índice global
                transaction.set(newUsernameRef, record)

                // Se o usuário já tinha outro username anterior, remove a reserva antiga
                if (oldNorm.isNotBlank() && oldNorm != norm) {
                    val oldUsernameRef = db.collection("usernames").document(oldNorm)
                    transaction.delete(oldUsernameRef)
                }

                val now = System.currentTimeMillis()
                val updated = currentUserObj.copy(
                    username = display,
                    usernameNormalized = norm,
                    updatedAt = now,
                    lastUsernameChangeAt = now
                )
                transaction.set(userRef, updated, SetOptions.merge())
                updated
            }.await()

            _currentUser.value = updatedUser
            Result.success(updatedUser)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reservar/atualizar username: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun resolveEmailFromIdentifier(identifier: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmed = identifier.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(Exception("Informe seu e-mail ou nome de usuário."))
            }

            // 1. Se é um formato de e-mail válido (ex: user@dominio.com), retorna diretamente
            if (com.example.util.UsernameUtils.isEmailAddress(trimmed)) {
                return@withContext Result.success(trimmed.lowercase())
            }

            // 2. Se for nome de usuário (ex: "@ronaldo" ou "ronaldo"), normaliza
            val norm = com.example.util.UsernameUtils.normalize(trimmed)
            if (norm.length < 3) {
                return@withContext Result.failure(Exception("O nome de usuário deve ter no mínimo 3 caracteres."))
            }

            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Serviço de banco de dados indisponível no momento."))

            // Consulta direta no índice atômico de usernames
            val usernameDoc = db.collection("usernames").document(norm).get().await()
            if (usernameDoc.exists()) {
                val email = usernameDoc.getString("email")
                if (!email.isNullOrBlank()) {
                    return@withContext Result.success(email.lowercase())
                }
            }

            // Fallback na coleção 'users' por usernameNormalized
            val snapNorm = db.collection("users")
                .whereEqualTo("usernameNormalized", norm)
                .limit(1)
                .get()
                .await()

            if (!snapNorm.isEmpty) {
                val email = snapNorm.documents.first().getString("email")
                if (!email.isNullOrBlank()) {
                    return@withContext Result.success(email.lowercase())
                }
            }

            // Fallback na coleção 'users' por username com '@'
            val snapDisplay = db.collection("users")
                .whereEqualTo("username", "@$norm")
                .limit(1)
                .get()
                .await()

            if (!snapDisplay.isEmpty) {
                val email = snapDisplay.documents.first().getString("email")
                if (!email.isNullOrBlank()) {
                    return@withContext Result.success(email.lowercase())
                }
            }

            // Fallback na coleção 'users' por displayName
            val snapDisplayName = db.collection("users")
                .whereEqualTo("displayName", trimmed)
                .limit(1)
                .get()
                .await()

            if (!snapDisplayName.isEmpty) {
                val email = snapDisplayName.documents.first().getString("email")
                if (!email.isNullOrBlank()) {
                    return@withContext Result.success(email.lowercase())
                }
            }

            // Fallback para o nome de usuário do fundador
            if (norm == "ronyskies") {
                return@withContext Result.success("ronaldomazive915@gmail.com")
            }

            Result.failure(Exception("Nome de usuário \"@$norm\" não encontrado. Verifique a grafia ou tente com seu e-mail."))
        } catch (e: Exception) {
            // Se for ronyskies, resolve para o email do fundador mesmo em erro
            val norm = com.example.util.UsernameUtils.normalize(identifier)
            if (norm == "ronyskies") {
                return@withContext Result.success("ronaldomazive915@gmail.com")
            }
            Log.e(TAG, "Erro ao resolver identificador: ${e.message}")
            Result.failure(Exception("Não foi possível localizar a conta: ${e.localizedMessage ?: "Tente com seu e-mail."}"))
        }
    }

    private fun ensureFounderUsernameSync(uid: String, email: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = obtainFirestore() ?: return@launch
                val norm = "ronyskies"
                val display = "@ronyskies"

                val userRef = db.collection("users").document(uid)
                val usernameRef = db.collection("usernames").document(norm)

                db.runTransaction { transaction ->
                    val userDoc = transaction.get(userRef)
                    if (!userDoc.exists()) return@runTransaction

                    val currentUserObj = userDoc.toObject(UserEntity::class.java) ?: return@runTransaction
                    val oldNorm = currentUserObj.usernameNormalized.ifBlank {
                        com.example.util.UsernameUtils.normalize(currentUserObj.username)
                    }

                    val usernameDoc = transaction.get(usernameRef)
                    val isUsernameDocOk = usernameDoc.exists() && usernameDoc.getString("uid") == uid
                    val isUserDocOk = currentUserObj.username == display && currentUserObj.usernameNormalized == norm

                    if (isUserDocOk && isUsernameDocOk) {
                        return@runTransaction
                    }

                    val record = UsernameRecord(
                        uid = uid,
                        username = display,
                        usernameNormalized = norm,
                        email = email,
                        createdAt = System.currentTimeMillis()
                    )

                    transaction.set(usernameRef, record)

                    if (oldNorm.isNotBlank() && oldNorm != norm) {
                        val oldUsernameRef = db.collection("usernames").document(oldNorm)
                        transaction.delete(oldUsernameRef)
                    }

                    val now = System.currentTimeMillis()
                    val updated = currentUserObj.copy(
                        username = display,
                        usernameNormalized = norm,
                        role = "FOUNDER",
                        isVerified = true,
                        updatedAt = now,
                        lastUsernameChangeAt = now
                    )
                    transaction.set(userRef, updated, SetOptions.merge())
                }.await()

                // Guarantee isVerified = true on founder profiles
                val profilesSnap = db.collection("users").document(uid).collection("profiles").get().await()
                if (profilesSnap.isEmpty) {
                    val profileRef = db.collection("users").document(uid).collection("profiles").document()
                    val founderProfile = UserProfile(
                        id = profileRef.id,
                        userId = uid,
                        name = "ronaldo_skies",
                        username = "@ronyskies",
                        avatarUrl = null,
                        avatarType = "DEFAULT",
                        isDefault = true,
                        isVerified = true,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    profileRef.set(founderProfile).await()
                } else {
                    for (doc in profilesSnap.documents) {
                        val pName = doc.getString("name") ?: ""
                        val pIsDefault = doc.getBoolean("isDefault") ?: false
                        val pIsVerified = doc.getBoolean("isVerified") ?: false
                        
                        // We ONLY verify ronaldo_skies or the default profile of the founder account
                        val shouldBeVerified = pName.equals("ronaldo_skies", ignoreCase = true) || pIsDefault
                        if (shouldBeVerified) {
                            if (!pIsVerified) {
                                doc.reference.update(
                                    "isVerified", true,
                                    "name", if (pName.isBlank()) "ronaldo_skies" else pName,
                                    "updatedAt", System.currentTimeMillis()
                                )
                            }
                        } else {
                            if (pIsVerified) {
                                doc.reference.update(
                                    "isVerified", false,
                                    "updatedAt", System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }

                Log.d(TAG, "[AUTH] Founder username @ronyskies e selo de verificação atômico garantido no Firestore para $email")
            } catch (e: Exception) {
                Log.w(TAG, "[AUTH] Erro ao sincronizar founder username/verificação: ${e.message}")
            }
        }
    }

    suspend fun signIn(emailOrUsername: String, pass: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val trimmedInput = emailOrUsername.trim()
            if (trimmedInput.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("Preencha todos os campos."))
            }

            // Resolve e-mail se foi fornecido um username
            val resolvedEmail = if (com.example.util.UsernameUtils.isEmailAddress(trimmedInput)) {
                trimmedInput.lowercase()
            } else {
                val resolveResult = resolveEmailFromIdentifier(trimmedInput)
                if (resolveResult.isFailure) {
                    return@withContext Result.failure(resolveResult.exceptionOrNull() ?: Exception("Usuário não encontrado."))
                }
                resolveResult.getOrThrow()
            }

            val auth = FirebaseAuth.getInstance()
            val result = auth.signInWithEmailAndPassword(resolvedEmail, pass).await()
            val fbUser = result.user ?: throw Exception("Falha ao obter usuário após login.")
            
            // Verifica Custom Claims no ID Token
            val tokenResult = fbUser.getIdToken(true).await()
            val claimRole = tokenResult.claims["role"] as? String
            val isAdminClaim = tokenResult.claims["admin"] as? Boolean ?: false
            
            // Regra especial para o e-mail do fundador
            val isFounderEmail = fbUser.email?.lowercase() == "ronaldomazive915@gmail.com"
            
            // Busca o perfil no Firestore
            val db = obtainFirestore() ?: throw Exception("Firestore indisponível.")
            val snapshot = db.collection("users").document(fbUser.uid).get().await()
            
            val user = if (snapshot.exists()) {
                val existingUser = snapshot.toObject(UserEntity::class.java) ?: throw Exception("Erro ao processar dados do usuário.")
                
                // Se for FOUNDER e a role ainda não estiver atualizada
                if ((claimRole == "FOUNDER" || isAdminClaim || isFounderEmail) && existingUser.role != "FOUNDER") {
                    val updatedUser = existingUser.copy(role = "FOUNDER", updatedAt = System.currentTimeMillis())
                    db.collection("users").document(fbUser.uid).set(updatedUser, SetOptions.merge()).await()
                    updatedUser
                } else {
                    // Atualiza o lastLogin
                    db.collection("users").document(existingUser.uid).update("lastLoginAt", System.currentTimeMillis()).await()
                    existingUser
                }
            } else {
                // Cria o perfil do usuário caso não exista
                val role = if (claimRole == "FOUNDER" || isAdminClaim || isFounderEmail) "FOUNDER" else "USER"
                val newUser = UserEntity(
                    uid = fbUser.uid,
                    email = fbUser.email ?: "",
                    displayName = fbUser.displayName ?: resolvedEmail.substringBefore("@"),
                    role = role,
                    deviceId = getOrGeneratePersistentDeviceId()
                )
                db.collection("users").document(fbUser.uid).set(newUser).await()
                newUser
            }
            
            // Inicia os listeners dos perfis
            startListeningUserProfile(fbUser.uid)

            if (isFounderEmail) {
                ensureFounderUsernameSync(fbUser.uid, fbUser.email ?: "ronaldomazive915@gmail.com")
            }

            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "SignIn error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signUpWithUsername(
        name: String,
        rawUsername: String,
        email: String,
        pass: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = name.trim()
            val trimmedEmail = email.trim().lowercase()

            if (trimmedName.isBlank()) {
                return@withContext Result.failure(Exception("Informe seu nome completo."))
            }

            val usernameValidation = com.example.util.UsernameUtils.validate(rawUsername)
            if (usernameValidation is com.example.util.UsernameValidationResult.Invalid) {
                return@withContext Result.failure(Exception(usernameValidation.reason))
            }

            val norm = com.example.util.UsernameUtils.normalize(rawUsername)
            val displayUsername = com.example.util.UsernameUtils.formatDisplay(rawUsername)

            if (!com.example.util.UsernameUtils.isEmailAddress(trimmedEmail)) {
                return@withContext Result.failure(Exception("Informe um endereço de e-mail válido."))
            }

            if (pass.length < 6) {
                return@withContext Result.failure(Exception("A senha deve ter pelo menos 6 caracteres."))
            }

            // Bloqueia criação de conta para o e-mail do fundador via frontend
            if (trimmedEmail == "ronaldomazive915@gmail.com") {
                return@withContext Result.failure(Exception("Este e-mail está reservado para a conta do fundador. Use a opção de login."))
            }

            val db = obtainFirestore() ?: return@withContext Result.failure(Exception("Serviço de banco de dados indisponível."))

            // 1. Pré-verificação de disponibilidade no Firestore
            val existingUsernameDoc = db.collection("usernames").document(norm).get().await()
            if (existingUsernameDoc.exists()) {
                return@withContext Result.failure(Exception("O nome de usuário $displayUsername já está em uso."))
            }

            // 2. Criação do usuário no Firebase Authentication
            val auth = FirebaseAuth.getInstance()
            val authResult = auth.createUserWithEmailAndPassword(trimmedEmail, pass).await()
            val fbUser = authResult.user ?: throw Exception("Falha ao registrar credenciais de acesso.")

            val newUser = UserEntity(
                uid = fbUser.uid,
                email = trimmedEmail,
                displayName = trimmedName,
                username = displayUsername,
                usernameNormalized = norm,
                role = "USER",
                deviceId = getOrGeneratePersistentDeviceId()
            )

            // 3. Gravação atômica via Batch no Firestore
            val batch = db.batch()

            // Documento de usuário
            val userRef = db.collection("users").document(fbUser.uid)
            batch.set(userRef, newUser)

            // Documento de reserva do username
            val usernameRef = db.collection("usernames").document(norm)
            val usernameRecord = UsernameRecord(
                uid = fbUser.uid,
                username = displayUsername,
                usernameNormalized = norm,
                email = trimmedEmail,
                createdAt = System.currentTimeMillis()
            )
            batch.set(usernameRef, usernameRecord)

            // Primeiro perfil do usuário
            val profileRef = db.collection("users").document(fbUser.uid).collection("profiles").document()
            val initialProfile = UserProfile(
                id = profileRef.id,
                userId = fbUser.uid,
                name = trimmedName,
                avatarUrl = null,
                avatarType = "DEFAULT",
                isDefault = true
            )
            batch.set(profileRef, initialProfile)

            batch.commit().await()

            // Inicia listeners e atualiza estado
            startListeningUserProfile(fbUser.uid)
            _currentUser.value = newUser
            _activeProfile.value = initialProfile

            Result.success(newUser)
        } catch (e: Exception) {
            Log.e(TAG, "SignUpWithUsername error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, pass: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val defaultName = email.substringBefore("@")
        val defaultUsername = defaultName.filter { it.isLetterOrDigit() || it == '_' }.take(15).ifBlank { "user_${System.currentTimeMillis() % 10000}" }
        signUpWithUsername(
            name = defaultName,
            rawUsername = defaultUsername,
            email = email,
            pass = pass
        )
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        _currentUser.value = null
        _activeProfile.value = null
        _userProfiles.value = emptyList()
        userProfileListener?.remove()
        userProfileListener = null
        profilesListListener?.remove()
        profilesListListener = null
    }

    suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = email.trim()
            val resolvedEmail = if (com.example.util.UsernameUtils.isEmailAddress(trimmed)) {
                trimmed.lowercase()
            } else {
                val res = resolveEmailFromIdentifier(trimmed)
                if (res.isFailure) throw res.exceptionOrNull() ?: Exception("E-mail não encontrado.")
                res.getOrThrow()
            }
            FirebaseAuth.getInstance().sendPasswordResetEmail(resolvedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Reset password error: ${e.message}")
            Result.failure(e)
        }
    }
}
