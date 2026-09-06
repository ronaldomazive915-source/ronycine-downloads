package com.example

import android.os.Bundle
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.BottomNav
import com.example.ui.components.CinematicIntro
import com.example.ui.components.ScreenRoute
import com.example.ui.components.TopBar
import com.example.ui.screens.*
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.PlayFilmeTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.data.local.NotificationEntity
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.components.TopBar
import com.example.ui.screens.NotificationScreen

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    private val pendingActionUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            mainViewModel.updateNotificationsEnabled(true)
            fetchFcmToken()
        } else {
            mainViewModel.updateNotificationsEnabled(false)
        }
    }

    private fun fetchFcmToken() {
        try {
            val firebaseService = com.example.data.remote.FirebaseService.getInstance(applicationContext)
            val app = firebaseService.firebaseApp
            if (app == null) {
                android.util.Log.d("MainActivity", "[FCM] FirebaseApp não inicializado, adiando token.")
                return
            }

            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                android.util.Log.d("MainActivity", "[FCM] Google Play Services indisponível (código: $resultCode), ignorando registro FCM.")
                updatePushStatusInFirestore(null)
                return
            }

            val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            try {
                messaging.isAutoInitEnabled = false
            } catch (_: Exception) {}

            messaging.token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val ex = task.exception
                    android.util.Log.d("MainActivity", "[FCM] Token FCM não disponível no momento: ${ex?.message}")
                    updatePushStatusInFirestore(null)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (!token.isNullOrBlank()) {
                    android.util.Log.d("MainActivity", "[FCM] Token recuperado com sucesso: $token")
                    mainViewModel.updateFcmToken(token)
                    updatePushStatusInFirestore(token)
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("MainActivity", "[FCM] Inicialização de FCM ignorada ou indisponível: ${e.message}")
        }
    }

    private fun updatePushStatusInFirestore(token: String? = null) {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        mainViewModel.updatePushStatus(hasPermission, token)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("actionUrl")?.let {
            pendingActionUrl.value = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Capture push actionUrl if present
        intent?.getStringExtra("actionUrl")?.let {
            pendingActionUrl.value = it
        }

        // Configure WebView settings
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val processName = android.app.Application.getProcessName()
                if (packageName != processName) {
                    val suffix = processName.substringAfterLast(":").ifEmpty { "worker" }
                    WebView.setDataDirectorySuffix(suffix)
                }
            }
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "WebView static config error: ${e.message}")
        }

        // Inicialização automática do Device Manager e registro persistente no Firestore
        val prefs = getSharedPreferences("playfilme_prefs", android.content.Context.MODE_PRIVATE)
        mainViewModel.initDeviceManager(prefs)

        // Solicitando permissão de notificações em tempo de execução no Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                    android.widget.Toast.makeText(
                        this,
                        "O RONYCINE precisa da permissão de notificações para avisar você sobre novos filmes e episódios adicionados!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                fetchFcmToken()
            }
        } else {
            fetchFcmToken()
        }

        // Handle Deep Link / App Link intent when opened via share URL
        val appLinkData = intent?.data
        if (appLinkData != null && (appLinkData.scheme == "https" || appLinkData.scheme == "ronycine")) {
            android.widget.Toast.makeText(
                this,
                "🎬 Bem-vindo ao RONYCINE! O aplicativo já está instalado no seu celular.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        setContent {
            PlayFilmeTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: ScreenRoute.HOME.route

                val pushActionUrl by pendingActionUrl.collectAsState()

                 LaunchedEffect(pushActionUrl, navBackStackEntry) {
                    val url = pushActionUrl
                    if (url != null) {
                        try {
                            if (runCatching { navController.graph }.getOrNull() == null) {
                                return@LaunchedEffect
                            }
                            val isApkUrl = url.endsWith(".apk") || url.contains("/updates/") || url.contains("appVersions") || url.contains(".apk?") || url == "update"
                            if (isApkUrl) {
                                val active = mainViewModel.activeAppVersion.value
                                val targetVer = if (active != null) {
                                    active
                                } else {
                                    com.example.data.remote.AppVersionEntity(
                                        id = "custom_${System.currentTimeMillis()}",
                                        versionName = "Nova Versão",
                                        versionCode = 10000,
                                        apkUrl = if (url == "update") "" else url,
                                        releaseNotes = "Nova atualização recebida via notificação.",
                                        published = true,
                                        status = "PUBLISHED"
                                    )
                                }
                                mainViewModel.triggerForceUpdateDialog(targetVer)
                            } else if (url.startsWith("movie/")) {
                                val tmdbId = url.substringAfter("movie/").toIntOrNull()
                                if (tmdbId != null) navController.navigate("detail/$tmdbId/movie")
                            } else if (url.startsWith("tv/")) {
                                val tmdbId = url.substringAfter("tv/").toIntOrNull()
                                if (tmdbId != null) {
                                    navController.navigate("detail/$tmdbId/tv")
                                } else {
                                    navController.navigate(ScreenRoute.TV_LIVE.route)
                                }
                            } else if (url.startsWith("watch/")) {
                                navController.navigate(url)
                            } else if (url == "livetv" || url == "tv") {
                                navController.navigate(ScreenRoute.TV_LIVE.route)
                            } else {
                                val id = url.toIntOrNull()
                                if (id != null) {
                                    navController.navigate("detail/$id/movie")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error navigating to push actionUrl: ${e.message}")
                        }
                        pendingActionUrl.value = null // Reset
                    }
                }
                
                val context = LocalContext.current
                val isFirstTime = remember { !prefs.getBoolean("playfilme_intro_seen", false) }
                var showIntro by remember { mutableStateOf(true) }

                val isDeviceBlocked by mainViewModel.isDeviceBlocked.collectAsState()
                val currentUser by authViewModel.currentUser.collectAsState()
                val activeProfile by authViewModel.activeProfile.collectAsState()
                val userProfiles by authViewModel.userProfiles.collectAsState()
                
                var navigationInitiated by remember { mutableStateOf(false) }

                LaunchedEffect(currentUser, activeProfile, userProfiles, navBackStackEntry?.destination?.route, isDeviceBlocked) {
                    if (currentUser != null && !navigationInitiated && !isDeviceBlocked) {
                        val hasGraph = runCatching { navController.graph }.getOrNull() != null
                        if (!hasGraph) return@LaunchedEffect

                        if (activeProfile == null) {
                            try {
                                if (userProfiles.isEmpty()) {
                                    // Se logado mas sem perfis, vai para criação
                                    navController.navigate(ScreenRoute.CREATE_PROFILE.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                } else {
                                    // Se logado mas sem perfil selecionado, vai para seleção
                                    navController.navigate(ScreenRoute.PROFILE_SELECTION.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                                navigationInitiated = true
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error navigating to profile setup: ${e.message}")
                            }
                        }
                    }
                }

                val remoteConfig by mainViewModel.remoteConfig.collectAsState()
                val updateControl by mainViewModel.updateControl.collectAsState()
                val activeAppVersion by mainViewModel.activeAppVersion.collectAsState()
                val updateOverrideVersion by mainViewModel.updateOverrideVersion.collectAsState()
                val syncStatus by mainViewModel.syncStatus.collectAsState()
                val unreadNotifCount by mainViewModel.unreadNotificationsCount.collectAsState()
                var activeInAppNotification by remember { mutableStateOf<NotificationEntity?>(null) }
                var showUpdateDialog by remember { mutableStateOf(true) }

                val pInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (e: Exception) { null }
                }
                val currentVersionName = remember(pInfo) { pInfo?.versionName ?: "1.0.0" }
                val currentBuildCode = remember(pInfo) {
                    if (pInfo != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pInfo.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION") pInfo.versionCode
                        }
                    } else 1
                }

                // REGRA FUNDAMENTAL: Atualização só existe se updateControl.enabled == true E activeAppVersion != null
                val isUpdateActive = updateControl.enabled && activeAppVersion != null && activeAppVersion!!.hasConfiguredApk
                val targetVersion = if (isUpdateActive) activeAppVersion else null
                val effectiveTargetCode = targetVersion?.versionCode ?: currentBuildCode
                val effectiveTargetName = targetVersion?.versionName ?: currentVersionName
                val effectiveApkUrl = targetVersion?.apkUrl ?: ""
                val effectiveSha256 = targetVersion?.sha256 ?: ""
                val isUpdateAvailable = isUpdateActive && currentBuildCode < effectiveTargetCode && effectiveApkUrl.isNotBlank()
                val isMandatoryUpdate = isUpdateActive && (targetVersion?.mandatory == true || updateControl.mandatory) && currentBuildCode < effectiveTargetCode && effectiveApkUrl.isNotBlank()

                LaunchedEffect(currentBuildCode, isUpdateActive, effectiveTargetCode, effectiveApkUrl) {
                    com.example.util.UpdateManager.logUpdateCheckDiagnostics(
                        installedVersionName = currentVersionName,
                        installedVersionCode = currentBuildCode,
                        targetVersionName = effectiveTargetName,
                        targetVersionCode = effectiveTargetCode,
                        apkUrl = effectiveApkUrl,
                        status = when {
                            !isUpdateActive -> "UPDATES_DISABLED_BY_ADMIN"
                            isMandatoryUpdate -> "MANDATORY"
                            isUpdateAvailable -> "UPDATE_AVAILABLE"
                            else -> "UP_TO_DATE"
                        },
                        isMandatory = isMandatoryUpdate,
                        minimumVersionCode = if (isMandatoryUpdate) effectiveTargetCode else 100,
                        sha256 = effectiveSha256,
                        fileSize = targetVersion?.fileSize ?: ""
                    )
                }

                LaunchedEffect(Unit) {
                    mainViewModel.inAppNotificationEvent.collect { notification ->
                        activeInAppNotification = notification
                    }
                }

                if (isDeviceBlocked) {
                    com.example.ui.screens.BlockedScreen()
                } else if (remoteConfig.maintenanceMode && !remoteConfig.allowedVersionsDuringMaintenance.contains(currentBuildCode)) {
                    com.example.ui.screens.MaintenanceScreen(
                        remoteConfig = remoteConfig,
                        onRetry = { mainViewModel.initDeviceManager() }
                    )
                } else if (isMandatoryUpdate) {
                    com.example.ui.screens.MandatoryUpdateScreen(
                        remoteConfig = remoteConfig,
                        sha256 = effectiveSha256,
                        targetApkUrl = effectiveApkUrl,
                        targetVersionName = effectiveTargetName,
                        targetVersionCode = effectiveTargetCode,
                        firebaseService = com.example.data.remote.FirebaseService.getInstance(context.applicationContext)
                    )
                } else {
                    if (isUpdateAvailable && showUpdateDialog) {
                        com.example.ui.screens.UpdateDialog(
                            remoteConfig = remoteConfig,
                            sha256 = effectiveSha256,
                            targetApkUrl = effectiveApkUrl,
                            targetVersionName = effectiveTargetName,
                            targetVersionCode = effectiveTargetCode,
                            onDismiss = { showUpdateDialog = false },
                            onUpdate = { /* Update flow triggered within dialog */ },
                            firebaseService = com.example.data.remote.FirebaseService.getInstance(context.applicationContext)
                        )
                    }

                    if (updateOverrideVersion != null) {
                        val override = updateOverrideVersion!!
                        com.example.ui.screens.UpdateDialog(
                            remoteConfig = remoteConfig,
                            sha256 = override.sha256 ?: "",
                            targetApkUrl = override.apkUrl ?: "",
                            targetVersionName = override.versionName ?: "",
                            targetVersionCode = override.versionCode,
                            onDismiss = { mainViewModel.clearForceUpdateDialog() },
                            onUpdate = { /* Update flow triggered within dialog */ },
                            firebaseService = com.example.data.remote.FirebaseService.getInstance(context.applicationContext),
                            autoStart = true
                        )
                    }
                    val isAuthOrProfileScreen = currentRoute == ScreenRoute.LOGIN.route ||
                        currentRoute == ScreenRoute.PROFILE_SELECTION.route ||
                        currentRoute == ScreenRoute.CREATE_PROFILE.route ||
                        currentRoute == "login" ||
                        currentRoute == "register" ||
                        currentRoute == "signup" ||
                        currentRoute == "forgot_password" ||
                        currentRoute == "reset_password" ||
                        currentRoute == "create_profile" ||
                        currentRoute == "profile_selection"

                    val hideTopAndBottomBars = isAuthOrProfileScreen ||
                        currentRoute.startsWith("watch") ||
                        currentRoute.startsWith("detail") ||
                        currentRoute == "admin" ||
                        currentRoute == "notifications" ||
                        currentRoute == "settings"

                    var showQuickMenuSheet by remember { mutableStateOf(false) }
                    val isAdminAuthorized by mainViewModel.isAdminAuthorized.collectAsState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = DarkBackground,
                            contentWindowInsets = if (hideTopAndBottomBars) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                            topBar = {
                                if (!hideTopAndBottomBars) {
                                    TopBar(
                                        onNavigateToSearch = { navController.navigate(ScreenRoute.SEARCH.route) },
                                        onNavigateToProfile = { navController.navigate(ScreenRoute.PROFILE.route) },
                                        onNavigateToRequest = { navController.navigate(ScreenRoute.REQUEST.route) },
                                        onNavigateToNotifications = { navController.navigate("notifications") },
                                        onMenuClick = { showQuickMenuSheet = true },
                                        unreadNotificationCount = unreadNotifCount,
                                        syncStatus = syncStatus
                                    )
                                }
                            },
                            bottomBar = {
                                if (!hideTopAndBottomBars) {
                                    BottomNav(
                                        currentRoute = currentRoute,
                                        onNavigate = { route ->
                                            if (route == ScreenRoute.HOME.route) {
                                                navController.navigate(route) {
                                                    popUpTo(ScreenRoute.HOME.route) { 
                                                        inclusive = false
                                                        saveState = false 
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            } else {
                                                navController.navigate(route) {
                                                    popUpTo(ScreenRoute.HOME.route) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = ScreenRoute.HOME.route,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                            composable(ScreenRoute.HOME.route) {
                                HomeScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    },
                                    onNavigateToWatch = { tmdbId, type, season, episode ->
                                        if (season != null && episode != null) {
                                            navController.navigate("watch/$tmdbId/$type?season=$season&episode=$episode")
                                        } else {
                                            navController.navigate("watch/$tmdbId/$type")
                                        }
                                    },
                                    onNavigateToLiveTv = {
                                        navController.navigate(ScreenRoute.TV_LIVE.route)
                                    },
                                    onNavigateToLiveChannel = { channelId ->
                                        navController.navigate("tv/$channelId")
                                    },
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo(ScreenRoute.HOME.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable(ScreenRoute.TRENDING.route) {
                                TrendingScreen(
                                    viewModel = mainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    }
                                )
                            }

                            composable(ScreenRoute.MOVIES.route) {
                                MoviesScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    }
                                )
                            }

                            composable(ScreenRoute.TV_LIVE.route) {
                                LiveTvScreen(
                                    onNavigateToChannel = { channelId ->
                                        navController.navigate("tv/$channelId")
                                    }
                                )
                            }

                            composable(
                                route = "tv/{channelId}",
                                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val channelId = backStackEntry.arguments?.getString("channelId") ?: "globo"
                                LiveTvScreen(
                                    initialChannelId = channelId,
                                    onNavigateToChannel = { newChannelId ->
                                        navController.navigate("tv/$newChannelId") {
                                            popUpTo("tv/{channelId}") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(ScreenRoute.SERIES.route) {
                                SeriesScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    }
                                )
                            }

                            composable(ScreenRoute.SEARCH.route) {
                                SearchScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    },
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo(ScreenRoute.HOME.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable(ScreenRoute.MY_LIST.route) {
                                MyListScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    }
                                )
                            }

                            composable(ScreenRoute.PROFILE.route) {
                                ProfileScreen(
                                    viewModel = mainViewModel,
                                    authViewModel = authViewModel,
                                    onNavigateToHistory = { navController.navigate("history") },
                                    onNavigateToMyList = { navController.navigate(ScreenRoute.MY_LIST.route) },
                                    onNavigateToDownloads = { navController.navigate("downloads") },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToAdmin = { navController.navigate("admin") },
                                    onNavigateToLogin = { navController.navigate(ScreenRoute.LOGIN.route) }
                                )
                            }

                            composable(ScreenRoute.PROFILE_SELECTION.route) {
                                ProfileSelectionScreen(
                                    authViewModel = authViewModel,
                                    onNavigateToCreateProfile = { 
                                        authViewModel.setProfileToEdit(null)
                                        navController.navigate(ScreenRoute.CREATE_PROFILE.route) 
                                    },
                                    onNavigateToEditProfile = { profile ->
                                        authViewModel.setProfileToEdit(profile)
                                        navController.navigate(ScreenRoute.CREATE_PROFILE.route)
                                    },
                                    onProfileSelected = {
                                        navController.navigate(ScreenRoute.HOME.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(ScreenRoute.CREATE_PROFILE.route) {
                                val profileToEdit by authViewModel.currentProfileToEdit.collectAsState()
                                CreateProfileScreen(
                                    authViewModel = authViewModel,
                                    profileToEdit = profileToEdit,
                                    onBack = {
                                        authViewModel.setProfileToEdit(null)
                                        if (!navController.popBackStack()) {
                                            navController.navigate(ScreenRoute.PROFILE_SELECTION.route) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    },
                                    onSuccess = {
                                        authViewModel.setProfileToEdit(null)
                                        if (!navController.popBackStack()) {
                                            navController.navigate(ScreenRoute.PROFILE_SELECTION.route) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }

                            composable(ScreenRoute.LOGIN.route) {
                                LoginScreen(
                                    viewModel = authViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onLoginSuccess = {
                                        navController.navigate(ScreenRoute.PROFILE_SELECTION.route) {
                                            popUpTo(ScreenRoute.LOGIN.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("settings") {
                                com.example.ui.screens.SettingsScreen(
                                    viewModel = mainViewModel,
                                    authViewModel = authViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDownloads = { navController.navigate("downloads") },
                                    onNavigateToInfo = { navController.navigate("info") }
                                )
                            }

                            composable("info") {
                                com.example.ui.screens.InfoScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigate = { route -> navController.navigate(route) }
                                )
                            }

                            composable("downloads") {
                                DownloadsScreen(
                                    viewModel = mainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToWatch = { tmdbId, type, season, episode ->
                                        if (season != null && episode != null) {
                                            navController.navigate("watch/$tmdbId/$type?season=$season&episode=$episode")
                                        } else {
                                            navController.navigate("watch/$tmdbId/$type")
                                        }
                                    },
                                    onNavigateToExplore = {
                                        navController.navigate(ScreenRoute.SEARCH.route)
                                    }
                                )
                            }

                            composable(ScreenRoute.REQUEST.route) {
                                RequestScreen(
                                    viewModel = mainViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("history") {
                                HistoryScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToWatch = { tmdbId, type, season, episode ->
                                        if (season != null && episode != null) {
                                            navController.navigate("watch/$tmdbId/$type?season=$season&episode=$episode")
                                        } else {
                                            navController.navigate("watch/$tmdbId/$type")
                                        }
                                    }
                                )
                            }

                            composable("admin") {
                                val isAdminAuthorized by mainViewModel.isAdminAuthorized.collectAsState()
                                if (isAdminAuthorized) {
                                    AdminScreen(
                                        adminViewModel = adminViewModel,
                                        mainViewModel = mainViewModel,
                                        onNavigateBack = {
                                            if (!navController.popBackStack()) {
                                                navController.navigate(ScreenRoute.HOME.route)
                                            }
                                        },
                                        onNavigateToMedia = { tmdbId, type ->
                                            navController.navigate("detail/$tmdbId/$type")
                                        }
                                    )
                                } else {
                                    RestrictedAccessScreen(
                                        onNavigateBack = {
                                            if (!navController.popBackStack()) {
                                                navController.navigate(ScreenRoute.HOME.route)
                                            }
                                        }
                                    )
                                }
                            }

                            composable("notifications") {
                                NotificationScreen(
                                    viewModel = mainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { tmdbId, type ->
                                        navController.navigate("detail/$tmdbId/$type")
                                    },
                                    onNavigateToLiveTv = {
                                        navController.navigate(ScreenRoute.TV_LIVE.route)
                                    }
                                )
                            }

                            composable(
                                route = "detail/{tmdbId}/{type}",
                                arguments = listOf(
                                    navArgument("tmdbId") { type = NavType.IntType },
                                    navArgument("type") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val tmdbId = backStackEntry.arguments?.getInt("tmdbId") ?: 0
                                val type = backStackEntry.arguments?.getString("type") ?: "movie"
                                DetailScreen(
                                    tmdbId = tmdbId,
                                    mediaType = type,
                                    viewModel = mainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToWatch = { id, watchType, s, e ->
                                        if (s != null && e != null) {
                                            navController.navigate("watch/$id/$watchType?season=$s&episode=$e")
                                        } else {
                                            navController.navigate("watch/$id/$watchType")
                                        }
                                    },
                                    onNavigateToDetail = { id, watchType ->
                                        navController.navigate("detail/$id/$watchType")
                                    }
                                )
                            }

                            composable(
                                route = "watch/{tmdbId}/{type}?season={season}&episode={episode}",
                                arguments = listOf(
                                    navArgument("tmdbId") { type = NavType.IntType },
                                    navArgument("type") { type = NavType.StringType },
                                    navArgument("season") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("episode") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    }
                                )
                            ) { backStackEntry ->
                                val tmdbId = backStackEntry.arguments?.getInt("tmdbId") ?: 0
                                val type = backStackEntry.arguments?.getString("type") ?: "movie"
                                val seasonArg = backStackEntry.arguments?.getInt("season")
                                val episodeArg = backStackEntry.arguments?.getInt("episode")

                                val season = if (seasonArg != null && seasonArg > 0) seasonArg else null
                                val episode = if (episodeArg != null && episodeArg > 0) episodeArg else null

                                PlayerScreen(
                                    tmdbId = tmdbId,
                                    mediaType = type,
                                    seasonNumber = season,
                                    episodeNumber = episode,
                                    viewModel = mainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { id, watchType ->
                                        navController.navigate("detail/$id/$watchType")
                                    }
                                )
                            }
                        }
                    }

                    if (showQuickMenuSheet) {
                        com.example.ui.components.QuickMenuSheet(
                            activeProfile = activeProfile,
                            isAdmin = isAdminAuthorized,
                            currentRoute = currentRoute,
                            onDismiss = { showQuickMenuSheet = false },
                            onNavigate = { targetRoute ->
                                navController.navigate(targetRoute) {
                                    popUpTo(ScreenRoute.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    InAppNotificationBanner(
                        notification = activeInAppNotification,
                        onDismiss = { activeInAppNotification = null },
                        onClick = { notif ->
                            mainViewModel.markNotificationAsRead(notif.id)
                            val isUpdateType = notif.type == "APP_UPDATE" || notif.type == "ATUALIZACAO"
                            if (isUpdateType || notif.actionUrl == "update") {
                                val active = mainViewModel.activeAppVersion.value
                                val targetVer = if (active != null) {
                                    active
                                } else {
                                    com.example.data.remote.AppVersionEntity(
                                        id = "custom_${System.currentTimeMillis()}",
                                        versionName = if (notif.title.contains("v")) notif.title.substringAfter("v").substringBefore(" ") else "Nova Versão",
                                        versionCode = 10000,
                                        apkUrl = if (notif.actionUrl == "update") "" else (notif.actionUrl ?: ""),
                                        releaseNotes = notif.message,
                                        published = true,
                                        status = "PUBLISHED"
                                    )
                                }
                                mainViewModel.triggerForceUpdateDialog(targetVer)
                            } else if (notif.actionUrl != null) {
                                val url = notif.actionUrl
                                if (url.startsWith("movie/")) {
                                    val tmdbId = url.substringAfter("movie/").toIntOrNull()
                                    if (tmdbId != null) navController.navigate("detail/$tmdbId/movie")
                                } else if (url.startsWith("tv/")) {
                                    val tmdbId = url.substringAfter("tv/").toIntOrNull()
                                    if (tmdbId != null) {
                                        navController.navigate("detail/$tmdbId/tv")
                                    } else {
                                        navController.navigate(ScreenRoute.TV_LIVE.route)
                                    }
                                } else if (url == "livetv" || url == "tv") {
                                    navController.navigate(ScreenRoute.TV_LIVE.route)
                                } else {
                                    val id = url.toIntOrNull()
                                    if (id != null) {
                                        navController.navigate("detail/$id/movie")
                                    }
                                }
                            } else {
                                navController.navigate("notifications")
                            }
                        },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                    )

                    if (showIntro && currentRoute == ScreenRoute.HOME.route) {
                        CinematicIntro(
                            isFirstTime = isFirstTime,
                            onIntroFinished = {
                                if (isFirstTime) {
                                    prefs.edit().putBoolean("playfilme_intro_seen", true).apply()
                                }
                                showIntro = false
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            }
        }
    }
}
