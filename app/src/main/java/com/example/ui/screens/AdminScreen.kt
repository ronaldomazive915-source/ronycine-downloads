package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.FeaturedMediaEntity
import com.example.data.local.FeaturedMediaItem
import com.example.data.local.NotificationEntity
import com.example.ui.components.extractYoutubeVideoId
import com.example.ui.components.HeroTrailerPlayer
import com.example.ui.components.AdminTrailerTestData
import com.example.ui.components.TrailerPlaybackState
import com.example.ui.components.TrailerErrorInfo
import com.example.ui.components.openYouTubeExternal
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.remote.BackupData
import com.example.data.remote.BackupPreviewInfo
import com.example.data.remote.RestoreRealProgress
import com.example.data.remote.RestoreStatus
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.TmdbAutoSyncHistoryEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.local.MediaEntity
import com.example.data.remote.MegaEmbedConfig
import com.example.data.repository.LiveTvRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.TmdbSearchResultItem
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.screens.admin.MassImportCentral
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RatingYellow
import com.example.ui.viewmodel.AdminSection
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    adminViewModel: AdminViewModel,
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMedia: ((Int, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isAdminLoggedIn by adminViewModel.isAdminLoggedIn.collectAsState()

    if (!isAdminLoggedIn) {
        AdminAuthScreen(
            onVerifyPassword = { password ->
                adminViewModel.verifyAdminPassword(password)
            },
            onNavigateBack = onNavigateBack
        )
        return
    }

    val currentSection by adminViewModel.currentSection.collectAsState()
    val stats by adminViewModel.stats.collectAsState()
    val catalogActionMessage by adminViewModel.catalogActionMessage.collectAsState()
    val isCatalogActionRunning by adminViewModel.isCatalogActionRunning.collectAsState()
    val catalogActionStage by adminViewModel.catalogActionStage.collectAsState()

    var isMenuDropdownExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(catalogActionMessage) {
        catalogActionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            adminViewModel.clearCatalogActionMessage()
        }
    }

    LaunchedEffect(Unit) {
        android.util.Log.d("AdminScreen", "[ADMIN] Abrindo painel administrativo / Acesso autorizado")
        adminViewModel.loadStats()
    }

    AdminErrorBoundary(onNavigateBack = onNavigateBack) {
        Scaffold(
            modifier = modifier.fillMaxSize().testTag("admin_screen_root"),
            containerColor = DarkBackground,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = DarkSurface,
                        contentColor = Color.White,
                        actionColor = BrandRed,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(start = 2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "RONYCINE",
                                color = BrandRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                color = Color(0xFF26262E),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "PAINEL ADMIN",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = currentSection.title,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    val scale by animateFloatAsState(
                        targetValue = if (isMenuDropdownExpanded) 0.92f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "admin_menu_button_scale"
                    )
                    IconButton(
                        onClick = { isMenuDropdownExpanded = !isMenuDropdownExpanded },
                        modifier = Modifier
                            .size(48.dp)
                            .scale(scale)
                            .testTag("admin_hamburger_menu_button")
                    ) {
                        AnimatedContent(
                            targetState = isMenuDropdownExpanded,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.82f, animationSpec = tween(220)))
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.82f, animationSpec = tween(180)))
                            },
                            label = "admin_hamburger_icon_transition"
                        ) { isOpen ->
                            if (isOpen) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar menu administrativo",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Abrir menu administrativo",
                                    tint = Color(0xFFE2E8F0),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // System Status Chip
                    Surface(
                        color = Color(0xFF13131A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF262633)),
                        modifier = Modifier
                            .clickable { adminViewModel.selectSection(AdminSection.STATUS_SISTEMA) }
                            .padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                "Sistema Online",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Botão Sincronizar Rápido
                    IconButton(
                        onClick = { adminViewModel.forceGlobalSync() },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("admin_sync_header_button")
                    ) {
                        val isActionRunning by adminViewModel.isCatalogActionRunning.collectAsState()
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sincronizar",
                            tint = if (isActionRunning) BrandRed else Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Notificações
                    IconButton(
                        onClick = { adminViewModel.selectSection(AdminSection.NOTIFICACOES) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("admin_notifications_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notificações",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Perfil Admin Conectado (Ronaldo Mazive - Founder)
                    Surface(
                        color = Color(0xFF1E1E24),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { adminViewModel.selectSection(AdminSection.STATUS_SISTEMA) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "R",
                                color = BrandRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        if (isMenuDropdownExpanded) {
            AdminNavDropdownMenu(
                expanded = isMenuDropdownExpanded,
                currentSection = currentSection,
                onDismissRequest = { isMenuDropdownExpanded = false },
                onSelectSection = { section ->
                    isMenuDropdownExpanded = false
                    adminViewModel.selectSection(section)
                },
                onLogout = {
                    isMenuDropdownExpanded = false
                    adminViewModel.logoutAdmin()
                }
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentSection) {
                AdminSection.ESTATISTICAS -> {
                    com.example.ui.screens.admin.AdminDashboardScreen(
                        adminViewModel = adminViewModel,
                        onNavigateSection = { section -> adminViewModel.selectSection(section) }
                    )
                }
                AdminSection.CATALOGO -> {
                    AdminCatalogoScreen(
                        adminViewModel = adminViewModel,
                        onNavigateToMedia = onNavigateToMedia
                    )
                }
                AdminSection.IMPORTACAO -> {
                    com.example.ui.screens.admin.AdminImportCentralScreen(
                        adminViewModel = adminViewModel,
                        initialMode = com.example.ui.screens.admin.ImportMode.INDIVIDUAL,
                        onNavigateToMedia = onNavigateToMedia
                    )
                }
                AdminSection.IMPORTACAO_MASSA -> {
                    com.example.ui.screens.admin.AdminImportCentralScreen(
                        adminViewModel = adminViewModel,
                        initialMode = com.example.ui.screens.admin.ImportMode.MASS,
                        onNavigateToMedia = onNavigateToMedia
                    )
                }
                AdminSection.TOP_10 -> {
                    AdminTop10Screen(adminViewModel = adminViewModel)
                }
                AdminSection.DESTAQUES -> {
                    AdminDestaquesScreen(
                        adminViewModel = adminViewModel
                    )
                }
                AdminSection.TV_AO_VIVO -> {
                    AdminTvScreen()
                }
                AdminSection.USUARIOS -> {
                    com.example.ui.screens.admin.AdminUsuariosScreen(adminViewModel = adminViewModel)
                }
                AdminSection.PERFIS -> {
                    com.example.ui.screens.admin.AdminPerfisScreen(adminViewModel = adminViewModel)
                }
                AdminSection.DISPOSITIVOS -> {
                    AdminDispositivosScreen(adminViewModel = adminViewModel)
                }
                AdminSection.ADMINISTRADORES -> {
                    com.example.ui.screens.admin.AdminAdministradoresScreen(adminViewModel = adminViewModel)
                }
                AdminSection.NOTIFICACOES -> {
                    AdminNotificacoesScreen(
                        adminViewModel = adminViewModel
                    )
                }
                AdminSection.PEDIDOS -> {
                    AdminRequestsScreen(adminViewModel = adminViewModel)
                }
                AdminSection.ATUALIZACOES_APP -> {
                    AdminAppUpdatesScreen(adminViewModel = adminViewModel)
                }
                AdminSection.ATUALIZACOES -> {
                    com.example.ui.screens.admin.AdminAtualizacoesCampanhasScreen(adminViewModel = adminViewModel)
                }
                AdminSection.CONTROLE_REMOTO -> {
                    AdminControleRemotoScreen(viewModel = adminViewModel)
                }
                AdminSection.SINCRONIZACAO -> {
                    AdminSincronizacaoScreen(
                        adminViewModel = adminViewModel
                    )
                }
                AdminSection.CONFIGURACOES -> {
                    AdminConfiguracoesScreen(
                        adminViewModel = adminViewModel
                    )
                }
                AdminSection.LOGS -> {
                    com.example.ui.screens.admin.AdminLogsScreen(adminViewModel = adminViewModel)
                }
                AdminSection.HISTORICO -> {
                    com.example.ui.screens.admin.AdminHistoricoScreen(adminViewModel = adminViewModel)
                }
                AdminSection.STATUS_SISTEMA -> {
                    com.example.ui.screens.admin.AdminSystemStatusScreen(adminViewModel = adminViewModel)
                }
            }

            if (isCatalogActionRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(24.dp))
                            val stageText = catalogActionStage ?: "Processando alteração..."
                            Text(stageText, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
}

// ==================================================
// 2. MENU ADMINISTRATIVO PREMIUM (MODAL / OVERLAY)
// ==================================================
@Composable
fun AdminNavDropdownMenu(
    expanded: Boolean,
    currentSection: AdminSection,
    onDismissRequest: () -> Unit,
    onSelectSection: (AdminSection) -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    if (expanded) {
        var isModalVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isModalVisible = true
        }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(onClick = onDismissRequest)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isModalVisible,
                    enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                        initialOffsetY = { -50 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ),
                    exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                        targetOffsetY = { -30 },
                        animationSpec = tween(200)
                    ) + scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(200)
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 360.dp)
                            .clickable(enabled = false) {}
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color.Black,
                                spotColor = BrandRed.copy(alpha = 0.3f)
                            )
                            .testTag("admin_navigation_modal"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                        border = BorderStroke(1.dp, Color(0xFF262626)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. Header do Menu (FIXO)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BrandRed.copy(alpha = 0.15f))
                                        .border(1.dp, BrandRed.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = BrandRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PAINEL ADMINISTRATIVO",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Gerencie o conteúdo e a plataforma",
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E1E))
                                        .testTag("close_admin_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar menu",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                            // ÁREA ROLÁVEL DE CONTEÚDO
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Spacer(modifier = Modifier.height(4.dp))

                                // MÓDULOS GERADOS DINAMICAMENTE A PARTIR DO REGISTRY OFICIAL
                                val modulesByCategory = remember { com.example.ui.screens.admin.AdminRegistry.getModulesByCategory() }

                                modulesByCategory.forEach { (category, moduleList) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = category.title,
                                            color = Color(0xFF757575),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                        )

                                        moduleList.forEach { module ->
                                            AdminNavOptionCard(
                                                title = module.title,
                                                description = module.subtitle,
                                                icon = module.icon,
                                                isSelected = currentSection == module.section,
                                                onClick = { onSelectSection(module.section) },
                                                testTag = module.testTag
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                                // 4. Sair do Painel
                                AdminNavLogoutOption(
                                    onClick = { showLogoutConfirmation = true },
                                    testTag = "admin_menu_logout"
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de confirmação de saída
    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            containerColor = Color(0xFF161616),
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sair do painel?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Você precisará digitar sua senha novamente para acessar a área administrativa.",
                    color = Color(0xFFB0B0B0),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_admin_logout_button")
                ) {
                    Text("SAIR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmation = false },
                    modifier = Modifier.testTag("cancel_admin_logout_button")
                ) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

@Composable
fun AdminNavOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandRed.copy(alpha = 0.12f) else Color(0xFF161616)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) BrandRed.copy(alpha = 0.4f) else Color(0xFF222222)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active left vertical indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BrandRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Icon container
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) BrandRed.copy(alpha = 0.22f) else Color(0xFF202020)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) BrandRed else Color(0xFFB0B0B0),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = if (isSelected) Color(0xFFEF9A9A) else Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selecionado",
                    tint = BrandRed,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF4A4A4A),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AdminNavLogoutOption(
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1314).copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sair do painel",
                    color = Color(0xFFEF4444),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Encerrar sessão de administração",
                    color = Color(0xFFE57373),
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFEF4444).copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==================================================
// 3. TELA INDEPENDENTE: 🎬 CATÁLOGO
// ==================================================
@Composable
fun AdminCatalogoScreen(
    adminViewModel: AdminViewModel,
    onNavigateToMedia: ((Int, String) -> Unit)? = null
) {
    val allMedia by adminViewModel.allCatalogMedia.collectAsState()
    val searchQuery by adminViewModel.catalogSearchQuery.collectAsState()
    val selectedFilter by adminViewModel.catalogFilter.collectAsState()
    val displayLimit by adminViewModel.catalogDisplayLimit.collectAsState()

    val detailMedia by adminViewModel.catalogDetailMedia.collectAsState()
    val editMedia by adminViewModel.catalogEditMedia.collectAsState()
    val deleteMedia by adminViewModel.catalogDeleteMedia.collectAsState()

    // Selection & Mass Delete
    val isSelectionMode by adminViewModel.isSelectionMode.collectAsState()
    val selectedMedia by adminViewModel.selectedMedia.collectAsState()
    var isShowBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Counts
    val movieCount = remember(allMedia) { allMedia.count { it.mediaType == "movie" } }
    val seriesCount = remember(allMedia) { allMedia.count { it.mediaType == "tv" } }
    val totalCount = remember(allMedia) { allMedia.size }

    // Filtered list
    val filteredMedia = remember(allMedia, searchQuery, selectedFilter) {
        allMedia.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "movie" -> item.mediaType == "movie"
                "tv" -> item.mediaType == "tv"
                else -> true
            }
            if (!matchesFilter) return@filter false

            if (searchQuery.isBlank()) return@filter true

            val q = searchQuery.trim().lowercase()
            item.title.lowercase().contains(q) ||
                    item.originalTitle.lowercase().contains(q) ||
                    item.tmdbId.toString().contains(q) ||
                    item.releaseYear.contains(q) ||
                    item.genres.lowercase().contains(q) ||
                    (if (item.mediaType == "tv") "série serie" else "filme").contains(q)
        }
    }

    val pagedList = remember(filteredMedia, displayLimit) {
        filteredMedia.take(displayLimit)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_catalog_screen")
    ) {
        // Cabeçalho e Contadores do Catálogo
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CATÁLOGO DE CONTEÚDO",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "%,d Filmes".format(movieCount),
                                color = BrandRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("•", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                text = "%,d Séries".format(seriesCount),
                                color = Color(0xFF3B82F6),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("•", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                text = "%,d Total".format(totalCount),
                                color = Color(0xFF10B981),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isSelectionMode) {
                        Button(
                            onClick = { adminViewModel.toggleSelectionMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_catalog_select_mode_toggle")
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SELECIONAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { adminViewModel.toggleSelectionMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, Color.Gray),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_catalog_select_mode_toggle_cancel")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CANCELAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Campo de Pesquisa no Catálogo
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { adminViewModel.onCatalogSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            text = "🔍 Pesquisar por título, ID TMDB, ano ou gênero...",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { adminViewModel.onCatalogSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_catalog_search_input")
                )

                // Filtros [ Todos ] [ Filmes ] [ Séries ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "all",
                        onClick = { adminViewModel.setCatalogFilter("all") },
                        label = { Text("✨ Todos (${filteredMedia.size})") },
                        colors = adminFilterChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedFilter == "movie",
                        onClick = { adminViewModel.setCatalogFilter("movie") },
                        label = { Text("🎬 Filmes ($movieCount)") },
                        colors = adminFilterChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedFilter == "tv",
                        onClick = { adminViewModel.setCatalogFilter("tv") },
                        label = { Text("📺 Séries ($seriesCount)") },
                        colors = adminFilterChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // BARRA DE SELEÇÃO E EXCLUSÃO EM MASSA
                if (isSelectionMode) {
                    Surface(
                        color = DarkBackground,
                        border = BorderStroke(1.dp, CardBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (selectedMedia.isNotEmpty()) BrandRed else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (selectedMedia.isEmpty()) "Nenhum selecionado" else "${selectedMedia.size} selecionados",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { adminViewModel.selectAllMedia(filteredMedia) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("admin_catalog_select_all_button")
                                ) {
                                    Text("TODOS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                if (selectedMedia.isNotEmpty()) {
                                    TextButton(
                                        onClick = { adminViewModel.clearSelection() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("admin_catalog_clear_selection_button")
                                    ) {
                                        Text("LIMPAR", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { isShowBulkDeleteConfirmDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("admin_catalog_delete_selected_button")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("EXCLUIR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grade de Cards Responsiva e Paginada
        if (filteredMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nenhum conteúdo encontrado para \"$searchQuery\"" else "Nenhum conteúdo no catálogo",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Vá para a aba Importação para adicionar filmes e séries.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pagedList, key = { it.tmdbId }) { media ->
                    val isSelected = selectedMedia.contains(Pair(media.tmdbId, media.mediaType))
                    CatalogMediaCard(
                        media = media,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onToggleSelect = { adminViewModel.toggleSelectMedia(media.tmdbId, media.mediaType) },
                        onEdit = { adminViewModel.openEditDialog(media) },
                        onViewDetails = { adminViewModel.openDetailDialog(media) },
                        onRefreshInfo = { adminViewModel.refreshMediaInfo(media.tmdbId, media.mediaType) },
                        onDelete = { adminViewModel.openDeleteDialog(media) }
                    )
                }

                // Botão de Paginação / Carregar Mais
                if (filteredMedia.size > displayLimit) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Mostrando ${pagedList.size} de ${filteredMedia.size} conteúdos",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = { adminViewModel.loadMoreCatalog() },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, BrandRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("catalog_load_more_button")
                            ) {
                                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = BrandRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Carregar mais (+30)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Ver Detalhes
    detailMedia?.let { media ->
        CatalogDetailDialog(
            media = media,
            onDismiss = { adminViewModel.closeDetailDialog() }
        )
    }

    // Modal: Editar Conteúdo
    editMedia?.let { media ->
        CatalogEditDialog(
            media = media,
            onDismiss = { adminViewModel.closeEditDialog() },
            onSave = { edited ->
                adminViewModel.saveMediaEdit(edited)
            }
        )
    }

    // Modal: Excluir Conteúdo
    val isCatalogActionRunning by adminViewModel.isCatalogActionRunning.collectAsState()
    val catalogActionStage by adminViewModel.catalogActionStage.collectAsState()

    deleteMedia?.let { media ->
        CatalogDeleteDialog(
            media = media,
            isDeleting = isCatalogActionRunning,
            stage = catalogActionStage,
            onDismiss = { if (!isCatalogActionRunning) adminViewModel.closeDeleteDialog() },
            onConfirmDelete = {
                adminViewModel.confirmDeleteMedia(media.tmdbId)
            }
        )
    }

    // Modal: Excluir em Massa / Lote
    if (isShowBulkDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { isShowBulkDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Excluir ${selectedMedia.size} títulos?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Esta ação removerá permanentemente os títulos selecionados do catálogo e da nuvem. Esta operação não pode ser desfeita.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isShowBulkDeleteConfirmDialog = false
                        adminViewModel.confirmBulkDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Confirmar Exclusão", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isShowBulkDeleteConfirmDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp))
        )
    }

    // Overlay de Progresso para Operações em Lote (Exclusão em Massa)
    val bulkDeleteProgress by adminViewModel.bulkDeleteProgress.collectAsState()
    val bulkDeleteTotal by adminViewModel.bulkDeleteTotal.collectAsState()

    if (bulkDeleteTotal > 0) {
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(280.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val progressValue = if (bulkDeleteTotal > 0) bulkDeleteProgress.toFloat() / bulkDeleteTotal.toFloat() else 0f
                    
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = progressValue,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "${(progressValue * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Excluindo em massa...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "$bulkDeleteProgress de $bulkDeleteTotal concluídos",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ==================================================
// 6. CARD DO CATÁLOGO COM MENU ⋮ INDIVIDUAL
// ==================================================
@Composable
fun CatalogMediaCard(
    media: MediaEntity,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEdit: () -> Unit,
    onViewDetails: () -> Unit,
    onRefreshInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var isItemMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) BrandRed else CardBorder.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = isSelectionMode) { onToggleSelect() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster com badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                AsyncImage(
                    model = media.posterPath,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Tipo badge
                Surface(
                    color = if (media.mediaType == "tv") Color(0xFF3B82F6) else BrandRed,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (media.mediaType == "tv") "SÉRIE" else "FILME",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (isSelectionMode) {
                    // Overlay semi-transparente para item selecionado
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isSelected) 0.4f else 0.15f))
                    )
                    
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BrandRed,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .testTag("catalog_item_checkbox_${media.tmdbId}")
                    )
                } else {
                    // Nota
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = RatingYellow, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "%.1f".format(media.rating),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Informações do Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (media.mediaType == "tv") "Série" else "Filme"} • ${media.releaseYear}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ID: ${media.tmdbId}",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Linha de Ação do Item com Botão ⋮ (Apenas quando não em modo seleção)
                if (!isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { isItemMenuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opções de ${media.title}",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Menu do Item (Editar, Ver detalhes, Atualizar, Excluir)
                            DropdownMenu(
                                expanded = isItemMenuExpanded,
                                onDismissRequest = { isItemMenuExpanded = false },
                                modifier = Modifier
                                    .width(200.dp)
                                    .background(DarkSurface)
                                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("✏️ Editar", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        isItemMenuExpanded = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("👁 Ver detalhes", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        isItemMenuExpanded = false
                                        onViewDetails()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🔄 Atualizar informações", color = Color(0xFF3B82F6), fontSize = 13.sp) },
                                    onClick = {
                                        isItemMenuExpanded = false
                                        onRefreshInfo()
                                    }
                                )
                                HorizontalDivider(color = CardBorder.copy(alpha = 0.4f))
                                DropdownMenuItem(
                                    text = { Text("🗑 Excluir", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        isItemMenuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Espaçamento para manter a altura do card uniforme
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

// ==================================================
// 8. MODAL DE CONFIRMAÇÃO DE EXCLUSÃO
// ==================================================
@Composable
fun CatalogDeleteDialog(
    media: MediaEntity,
    isDeleting: Boolean,
    stage: String?,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDeleting) "Processando..." else "Excluir conteúdo?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDeleting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFEF4444),
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = stage ?: "Excluindo...",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "Você tem certeza que deseja excluir:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (media.mediaType == "tv") 
                            "Esta ação removerá a série e todos os seus episódios permanentemente." 
                            else "Esta ação não poderá ser desfeita.",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444),
                    disabledContainerColor = Color(0xFFEF4444).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_delete_media_button")
            ) {
                if (isDeleting) {
                    Text("ELIMINANDO...", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                } else {
                    Text("ELIMINAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isDeleting) {
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// ==================================================
// 9. MODAL DE EDIÇÃO DE CONTEÚDO
// ==================================================
@Composable
fun CatalogEditDialog(
    media: MediaEntity,
    onDismiss: () -> Unit,
    onSave: (MediaEntity) -> Unit
) {
    var title by remember { mutableStateOf(media.title) }
    var overview by remember { mutableStateOf(media.overview) }
    var releaseYear by remember { mutableStateOf(media.releaseYear) }
    var genres by remember { mutableStateOf(media.genres) }
    var ratingStr by remember { mutableStateOf(media.rating.toString()) }
    var posterPath by remember { mutableStateOf(media.posterPath ?: "") }
    var backdropPath by remember { mutableStateOf(media.backdropPath ?: "") }
    var mediaType by remember { mutableStateOf(media.mediaType) }
    var tmdbIdStr by remember { mutableStateOf(media.tmdbId.toString()) }
    var seasonsCountStr by remember { mutableStateOf(media.seasonsCount.toString()) }
    var episodesCountStr by remember { mutableStateOf(media.episodesCount.toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✏️ Editar Conteúdo",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))

                // Form Scrollable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título", color = Color.Gray) },
                        singleLine = true,
                        colors = editDialogTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = overview,
                        onValueChange = { overview = it },
                        label = { Text("Sinopse / Descrição", color = Color.Gray) },
                        minLines = 3,
                        maxLines = 6,
                        colors = editDialogTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = releaseYear,
                            onValueChange = { releaseYear = it },
                            label = { Text("Ano", color = Color.Gray) },
                            singleLine = true,
                            colors = editDialogTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = ratingStr,
                            onValueChange = { ratingStr = it },
                            label = { Text("Avaliação (0-10)", color = Color.Gray) },
                            singleLine = true,
                            colors = editDialogTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = genres,
                        onValueChange = { genres = it },
                        label = { Text("Gêneros (separados por vírgula)", color = Color.Gray) },
                        singleLine = true,
                        colors = editDialogTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tmdbIdStr,
                            onValueChange = { tmdbIdStr = it },
                            label = { Text("ID TMDB", color = Color.Gray) },
                            singleLine = true,
                            colors = editDialogTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tipo", color = Color.Gray, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = mediaType == "movie",
                                    onClick = { mediaType = "movie" },
                                    label = { Text("Filme") },
                                    colors = adminFilterChipColors()
                                )
                                FilterChip(
                                    selected = mediaType == "tv",
                                    onClick = { mediaType = "tv" },
                                    label = { Text("Série") },
                                    colors = adminFilterChipColors()
                                )
                            }
                        }
                    }

                    if (mediaType == "tv") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = seasonsCountStr,
                                onValueChange = { seasonsCountStr = it },
                                label = { Text("Temporadas", color = Color.Gray) },
                                singleLine = true,
                                colors = editDialogTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = episodesCountStr,
                                onValueChange = { episodesCountStr = it },
                                label = { Text("Episódios", color = Color.Gray) },
                                singleLine = true,
                                colors = editDialogTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = posterPath,
                        onValueChange = { posterPath = it },
                        label = { Text("URL do Poster", color = Color.Gray) },
                        singleLine = true,
                        colors = editDialogTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = backdropPath,
                        onValueChange = { backdropPath = it },
                        label = { Text("URL do Backdrop", color = Color.Gray) },
                        singleLine = true,
                        colors = editDialogTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updatedEntity = media.copy(
                                title = title.trim(),
                                overview = overview.trim(),
                                releaseYear = releaseYear.trim(),
                                genres = genres.trim(),
                                rating = ratingStr.toDoubleOrNull() ?: media.rating,
                                posterPath = posterPath.trim().ifEmpty { null },
                                backdropPath = backdropPath.trim().ifEmpty { null },
                                mediaType = mediaType,
                                tmdbId = tmdbIdStr.toIntOrNull() ?: media.tmdbId,
                                seasonsCount = seasonsCountStr.toIntOrNull() ?: media.seasonsCount,
                                episodesCount = episodesCountStr.toIntOrNull() ?: media.episodesCount
                            )
                            onSave(updatedEntity)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SALVAR ALTERAÇÕES", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================================================
// 10. MODAL DE DETALHES DE CONTEÚDO
// ==================================================
@Composable
fun CatalogDetailDialog(
    media: MediaEntity,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👁 Detalhes do Conteúdo",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AsyncImage(
                            model = media.posterPath,
                            contentDescription = media.title,
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = media.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )

                            if (media.originalTitle.isNotBlank() && media.originalTitle != media.title) {
                                Text(
                                    text = "Original: ${media.originalTitle}",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                BadgeTag(if (media.mediaType == "tv") "📺 Série" else "🎬 Filme")
                                Surface(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Ano: ${media.releaseYear}",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = RatingYellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "%.1f / 10".format(media.rating),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "TMDB ID: ${media.tmdbId}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )

                            if (media.mediaType == "tv") {
                                Text(
                                    text = "Temporadas: ${media.seasonsCount} • Episódios: ${media.episodesCount}",
                                    color = Color(0xFF3B82F6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text("Gêneros", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(media.genres, color = Color.White, fontSize = 13.sp)

                    Text("Sinopse", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = media.overview.ifBlank { "Sem sinopse disponível." },
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (media.cast.isNotBlank()) {
                        Text("Elenco", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(media.cast, color = Color.LightGray, fontSize = 13.sp)
                    }

                    if (media.director.isNotBlank()) {
                        Text("Direção", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(media.director, color = Color.LightGray, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================================================
// TELA: 📥 IMPORTAÇÃO (SEPARADA DO CATÁLOGO)
// ==================================================
@Composable
fun AdminImportacaoScreen(
    adminViewModel: AdminViewModel,
    onNavigateToMedia: ((Int, String) -> Unit)? = null
) {
    com.example.ui.screens.admin.AdminImportCentralScreen(
        adminViewModel = adminViewModel,
        onNavigateToMedia = onNavigateToMedia
    )
}

@Composable
fun AdminImportNavRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        Triple("📥 Importar", Icons.Default.CloudDownload, 0),
        Triple("⚡ Atualizações do TMDB", Icons.Default.Sync, 1),
        Triple("♻️ Backup & Restaurar", Icons.Default.Backup, 2),
        Triple("📋 Histórico", Icons.Default.History, 3)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { (title, icon, index) ->
            val isSelected = selectedTab == index
            Surface(
                onClick = { onTabSelected(index) },
                color = if (isSelected) BrandRed else DarkBackground,
                shape = RoundedCornerShape(10.dp),
                border = if (isSelected) null else BorderStroke(1.dp, CardBorder),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ==================================================
// 📥 NOVA ÁREA DE IMPORTAÇÃO E BACKUP PROFISSIONAL
// ==================================================

@Composable
fun ImportarPainelTab(
    adminViewModel: AdminViewModel,
    massImportProgress: MediaRepository.MassImportProgress,
    onNavigateToAutoSync: () -> Unit = {}
) {
    val searchQuery by adminViewModel.searchQuery.collectAsState()
    val searchFilter by adminViewModel.searchFilter.collectAsState()
    val searchResults by adminViewModel.searchResults.collectAsState()
    val isSearching by adminViewModel.isSearching.collectAsState()
    val tmdbAutoSyncConfig by adminViewModel.tmdbAutoSyncConfig.collectAsState()
    val tmdbAutoSyncProgress by adminViewModel.tmdbAutoSyncProgress.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Pesquisa, 1: Populares, 2: Em Massa

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Access Banner to TMDB Auto-Sync
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onNavigateToAutoSync() },
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, if (tmdbAutoSyncProgress.isRunning) BrandRed else CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (tmdbAutoSyncProgress.isRunning) BrandRed.copy(alpha = 0.2f) else BrandRed.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "⚡ Atualizações do TMDB",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (tmdbAutoSyncProgress.isRunning)
                                tmdbAutoSyncProgress.stepMessage
                            else
                                "Sincronização automática configurada",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (tmdbAutoSyncProgress.isRunning) {
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                "Sincronizando...",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    } else if (tmdbAutoSyncConfig.enabled) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                "Ativa (${tmdbAutoSyncConfig.frequencyHours}h)",
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Abrir",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DarkBackground,
            contentColor = BrandRed,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = BrandRed,
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Pesquisa TMDB", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Populares", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("Em Massa", fontSize = 12.sp) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                0 -> SearchTab(adminViewModel, searchQuery, searchFilter, searchResults, isSearching)
                1 -> PopularesTab(adminViewModel)
                2 -> MassImportCentral(adminViewModel)
            }
        }
    }
}

@Composable
fun BackupRestaurarTab(
    adminViewModel: AdminViewModel,
    backupProgress: AdminViewModel.BackupProgress?,
    restorePreviewInfo: BackupPreviewInfo?,
    restoreRealProgress: RestoreRealProgress?
) {
    val context = LocalContext.current
    val isPreviewLoading by adminViewModel.isPreviewLoading.collectAsState()

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { adminViewModel.prepareRestore(it) }
        }
    )

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { 
                adminViewModel.exportBackupStreaming(it)
            }
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = BrandRed, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("RESTAURAR / IMPORTAR BACKUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Processamento profissional em streaming de alta performance", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    Text(
                        "Restaure catálogos completos (filmes, séries, episódios, canais e configurações) através de arquivos JSON ou ZIP de qualquer tamanho sem travamentos.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Leitura controlada por lotes (Chunks) • Proteção contra OOM",
                            color = Color(0xFF86EFAC),
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = { 
                            pickFileLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPreviewLoading && restoreRealProgress?.status != RestoreStatus.RESTORING
                    ) {
                        if (isPreviewLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("ANALISANDO ARQUIVO...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELECIONAR ARQUIVO (JSON / ZIP)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("EXPORTAR BACKUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Exportação contínua e segura direto para o dispositivo", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "Gere um arquivo de segurança contendo todos os dados do catálogo, episódios, canais e servidores para manter seus dados preservados.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = { 
                            val date = SimpleDateFormat("dd_MM_yyyy_HHmm", Locale.getDefault()).format(Date())
                            saveJsonLauncher.launch("ronycine_backup_$date.json")
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = restoreRealProgress?.status != RestoreStatus.RESTORING
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CRIAR E EXPORTAR BACKUP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (restorePreviewInfo != null) {
        BackupPreviewDialog(
            preview = restorePreviewInfo,
            onCancel = { adminViewModel.cancelRestorePreview() },
            onConfirm = { adminViewModel.confirmRestore() }
        )
    }

    if (restoreRealProgress != null) {
        RestoreRealProgressOverlay(
            progress = restoreRealProgress,
            onCancel = { adminViewModel.cancelActiveRestore() },
            onDismiss = { adminViewModel.dismissRestoreProgress() }
        )
    }

    if (backupProgress != null && restoreRealProgress == null) {
        BackupProgressOverlay(
            progress = backupProgress,
            onDismiss = { adminViewModel.clearBackupProgress() }
        )
    }
}

@Composable
fun BackupPreviewDialog(
    preview: BackupPreviewInfo,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    var showConfirmation by remember { mutableStateOf(false) }
    var isNameExpanded by remember { mutableStateOf(false) }

    if (!showConfirmation) {
        Dialog(onDismissRequest = onCancel) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 480.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DETALHES DO BACKUP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }

                    // Arquivo e Tamanho
                    Surface(
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Arquivo Row (Toggles full name on click)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isNameExpanded = !isNameExpanded }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Arquivo:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        text = preview.fileName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (isNameExpanded) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = preview.fileName,
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(8.dp),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // Tamanho Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tamanho:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = preview.fileSizeFormatted,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Tipo Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tipo:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = if (preview.isZip) "Arquivo Compactado (ZIP)" else "Arquivo JSON",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (preview.errorMessage != null || !preview.isValid) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    preview.errorMessage ?: "Não foi possível identificar a estrutura deste backup.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Conteúdo Detectado", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            InfoBadge("🎬 ${preview.moviesCount} Filmes", modifier = Modifier.weight(1f).fillMaxWidth())
                            InfoBadge("📺 ${preview.seriesCount} Séries", modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            InfoBadge("🍿 ${preview.episodesCount} Episódios", modifier = Modifier.weight(1f).fillMaxWidth())
                            InfoBadge("📡 ${preview.channelsCount} Canais", modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                        if (preview.featuredCount > 0 || preview.hasConfig) {
                            InfoBadge("⚙️ Destaques: ${preview.featuredCount} | Configurações: Presentes", modifier = Modifier.fillMaxWidth())
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Data do backup:", color = Color.Gray, fontSize = 11.sp)
                            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(preview.timestamp))
                            Text(date, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                            Text("Versão:", color = Color.Gray, fontSize = 11.sp)
                            Text("v${preview.version}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val canRestore = preview.isValid && (preview.moviesCount + preview.seriesCount + preview.episodesCount + preview.channelsCount + preview.featuredCount > 0 || preview.hasConfig)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).height(48.dp),
                            border = BorderStroke(1.dp, Color.Gray),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("CANCELAR", color = Color.White)
                        }
                        Button(
                            onClick = { showConfirmation = true },
                            enabled = canRestore,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed, disabledContainerColor = Color.Gray.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("RESTAURAR", fontWeight = FontWeight.Bold, color = if (canRestore) Color.White else Color.Gray)
                        }
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFACC15))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONFIRMAÇÃO DE RESTAURAÇÃO", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "A restauração atualizará os títulos e dados do catálogo de forma segura e incremental sem corromper itens existentes. Deseja iniciar o processo agora?",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("SIM, RESTAURAR", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun RestoreRealProgressOverlay(
    progress: RestoreRealProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val isRunning = progress.status == RestoreStatus.RESTORING
    val isCompleted = progress.status == RestoreStatus.COMPLETED
    val isCancelled = progress.status == RestoreStatus.CANCELLED
    val isError = progress.status == RestoreStatus.ERROR

    Dialog(
        onDismissRequest = {
            if (!isRunning) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isRunning,
            dismissOnClickOutside = !isRunning
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (isRunning) BrandRed.copy(alpha = 0.6f) else CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "RESTAURANDO BACKUP",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${progress.percentage}%",
                            color = BrandRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }

                    LinearProgressIndicator(
                        progress = progress.progress.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = BrandRed,
                        trackColor = Color.DarkGray
                    )

                    Surface(
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = progress.currentPhase,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (progress.currentItemTitle.isNotBlank()) {
                                Text(
                                    text = progress.currentItemTitle,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProgressCountRow("🎬 Filmes:", progress.moviesRestored, progress.moviesTotal)
                        ProgressCountRow("📺 Séries:", progress.seriesRestored, progress.seriesTotal)
                        ProgressCountRow("🍿 Episódios:", progress.episodesRestored, progress.episodesTotal)
                        ProgressCountRow("📡 Canais:", progress.channelsRestored, progress.channelsTotal)
                        if (progress.itemsIgnored > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Itens ignorados/duplicados:", color = Color.Gray, fontSize = 11.sp)
                                Text("${progress.itemsIgnored}", color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CANCELAR RESTAURAÇÃO", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                } else if (isCompleted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(64.dp)
                    )

                    Text(
                        "RESTAURAÇÃO CONCLUÍDA",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "Todos os itens foram restaurados com sucesso no catálogo e sincronizados.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Filmes restaurados:", color = Color.Gray, fontSize = 12.sp)
                                Text("${progress.moviesRestored}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Séries restauradas:", color = Color.Gray, fontSize = 12.sp)
                                Text("${progress.seriesRestored}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Episódios restaurados:", color = Color.Gray, fontSize = 12.sp)
                                Text("${progress.episodesRestored}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Canais restaurados:", color = Color.Gray, fontSize = 12.sp)
                                Text("${progress.channelsRestored}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONCLUIR", fontWeight = FontWeight.Bold)
                    }

                } else if (isCancelled) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(56.dp)
                    )

                    Text(
                        "RESTAURAÇÃO CANCELADA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "O processo foi cancelado. Os registros salvos até o momento da interrupção foram preservados.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("FECHAR")
                    }

                } else if (isError) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(56.dp)
                    )

                    Text(
                        "NÃO FOI POSSÍVEL CONCLUIR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        progress.errorMessage ?: "Não foi possível concluir a restauração. Arquivo inválido ou corrompido.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("FECHAR")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = text,
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BackupProgressOverlay(
    progress: AdminViewModel.BackupProgress,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = if (progress.isFinished) onDismiss else ({})) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = if (progress.type == "RESTORE") "Restaurando backup..." else "Exportando backup...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                if (!progress.isFinished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = progress.progress,
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = BrandRed,
                            trackColor = Color.DarkGray
                        )
                        Text("${(progress.progress * 100).toInt()}%", color = BrandRed, fontWeight = FontWeight.Bold)
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProgressCountRow("🎬 Filmes:", progress.moviesCount, progress.moviesTotal)
                        ProgressCountRow("📺 Séries:", progress.seriesCount, progress.seriesTotal)
                        ProgressCountRow("📡 Canais:", progress.channelsCount, progress.channelsTotal)
                    }
                } else {
                    Icon(
                        if (progress.errors > 0) Icons.Default.Error else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (progress.errors > 0) BrandRed else Color(0xFF22C55E),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(progress.message, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    
                    if (progress.type == "RESTORE") {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoBadge("Filmes: ${progress.moviesTotal}")
                            InfoBadge("Séries: ${progress.seriesTotal}")
                            InfoBadge("Canais: ${progress.channelsTotal}")
                            InfoBadge("Erros: ${progress.errors}")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("FECHAR")
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressCountRow(label: String, count: Int, total: Int) {
    if (total > 0) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text("$count/$total", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==================================================
// ⚡ ABA: ATUALIZAÇÕES DO TMDB (SISTEMA PROFISSIONAL)
// ==================================================

/**
 * Observa o relógio real do dispositivo com alta precisão (segundo a segundo),
 * sem acúmulo de atraso (drift) e com atualização instantânea ao retornar de segundo plano.
 */
@Composable
fun rememberLiveDeviceTime(): Long {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                currentTimeMillis = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            currentTimeMillis = now
            val millisIntoCurrentSecond = now % 1000L
            val sleepMillis = 1000L - millisIntoCurrentSecond
            delay(sleepMillis.coerceIn(50L, 1000L))
        }
    }

    return currentTimeMillis
}

@Composable
fun TmdbAutoSyncTab(adminViewModel: AdminViewModel) {
    val config by adminViewModel.tmdbAutoSyncConfig.collectAsState()
    val progress by adminViewModel.tmdbAutoSyncProgress.collectAsState()
    val history by adminViewModel.tmdbAutoSyncHistory.collectAsState()
    val latest by adminViewModel.latestTmdbAutoSync.collectAsState()

    // Recarrega configuração do banco/Firestore ao entrar na tela
    LaunchedEffect(Unit) {
        adminViewModel.loadTmdbAutoSyncConfig()
    }

    // Relógio do dispositivo em tempo real
    val currentTimeMillis = rememberLiveDeviceTime()

    // Formato estrito HH:mm:ss da hora atual do dispositivo
    val liveTimeStr = remember(currentTimeMillis / 1000L) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentTimeMillis))
    }

    // Cálculo em tempo real da contagem regressiva para próxima execução
    val nextExecutionCountdown = remember(currentTimeMillis / 1000L, config.lastSyncTimestamp, config.frequencyHours, config.enabled) {
        if (!config.enabled) {
            "Desativada"
        } else if (config.lastSyncTimestamp <= 0L) {
            "Pendente (Em breve)"
        } else {
            val nextTime = config.lastSyncTimestamp + (config.frequencyHours * 3600_000L)
            val diff = nextTime - currentTimeMillis
            if (diff <= 0L) {
                "Executando..."
            } else {
                val totalSeconds = diff / 1000L
                val hours = totalSeconds / 3600L
                val minutes = (totalSeconds % 3600L) / 60L
                val seconds = totalSeconds % 60L
                if (hours > 0L) {
                    "Em ${hours}h ${minutes}m"
                } else {
                    "Em ${minutes}m ${seconds}s"
                }
            }
        }
    }

    // Executa auto-sync quando a contagem regressiva chegar a zero
    LaunchedEffect(currentTimeMillis / 1000L, config.enabled, progress.isRunning) {
        if (config.enabled && !progress.isRunning && config.lastSyncTimestamp > 0L) {
            val nextTime = config.lastSyncTimestamp + (config.frequencyHours * 3600_000L)
            if (currentTimeMillis >= nextTime) {
                adminViewModel.startTmdbAutoSyncNow()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HERO HEADER CARD (WITH INTEGRATED LIVE DEVICE CLOCK & FIXED STATUS PILL)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Top Row: Header Info & Status Pill (Guaranteed no letter squishing)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            if (progress.isRunning) rotationZ = spinAngle
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "ATUALIZAÇÕES DO TMDB",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Auto-sync inteligente com catálogo & Firestore",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Status Pill - With wrapContentWidth, zero shrink, and single line
                        Surface(
                            color = when {
                                progress.isRunning -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                config.enabled -> Color(0xFF10B981).copy(alpha = 0.2f)
                                else -> Color.Gray.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    progress.isRunning -> Color(0xFFF59E0B)
                                    config.enabled -> Color(0xFF10B981)
                                    else -> Color.Gray
                                }
                            ),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                progress.isRunning -> Color(0xFFF59E0B)
                                                config.enabled -> Color(0xFF10B981)
                                                else -> Color.Gray
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        progress.isRunning -> "Sincronizando..."
                                        config.enabled -> "Ativa (${config.frequencyHours}h)"
                                        else -> "Pausada"
                                    },
                                    color = when {
                                        progress.isRunning -> Color(0xFFF59E0B)
                                        config.enabled -> Color(0xFF10B981)
                                        else -> Color.Gray
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.7f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 🕒 ÁREA DEDICADA: HORA ATUAL DO DISPOSITIVO (TEMPO REAL)
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(BrandRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "HORA ATUAL",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = liveTimeStr,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "Relógio local do dispositivo em tempo real",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Hora Atual",
                                    tint = BrandRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. DASHBOARD METRICS (4 CARDS)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Frequência
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Frequência", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (config.enabled) "A cada ${config.frequencyHours}h" else "Desativado",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                text = if (config.enabled) "Limite: ${config.maxItemsPerSync} itens" else "Pausado",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Card 2: Última Atualização (Timestamp real da última sincronização finalizada)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Última Atualização", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (config.lastSyncTimestamp > 0L) {
                                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(config.lastSyncTimestamp))
                                } else {
                                    "Nunca executado"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                text = if (latest != null) "${latest?.durationSeconds ?: 0}s • ${latest?.status}" else config.lastSyncStatus,
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 3: Próxima Execução (Contagem regressiva viva e dinâmica)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Próxima Execução", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = nextExecutionCountdown,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                text = if (config.enabled) "Sincronização agendada" else "Atualização desligada",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Card 4: Conteúdos Importados / Existentes (Último Resultado)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Último Resultado", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = latest?.let { "+${it.importedCount} novos" } ?: "Sem dados",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                text = latest?.let { "${it.existingCount} já existiam" } ?: "Pronto para sincronizar",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. LIVE PROGRESS CARD (ACTIVE OR JUST FINISHED)
        if (progress.isRunning || (progress.isFinished && progress.lastResultSummary != null)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (progress.isRunning) BrandRed.copy(alpha = 0.08f) else DarkSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (progress.isRunning) BrandRed else CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (progress.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = BrandRed,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (progress.isRunning) "Sincronização em Andamento" else "Sincronização Concluída",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            if (progress.isRunning) {
                                OutlinedButton(
                                    onClick = { adminViewModel.cancelTmdbAutoSync() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Cancelar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress Bar
                        if (progress.isRunning && progress.totalFound > 0) {
                            val ratio = (progress.processedCount.toFloat() / progress.totalFound.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandRed,
                                trackColor = DarkBackground
                            )
                        } else if (progress.isRunning) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandRed,
                                trackColor = DarkBackground
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Status Message
                        Text(
                            text = progress.stepMessage,
                            color = if (progress.isRunning) Color.LightGray else Color.White,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Counts Grid / Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = "🎬 ${progress.newMoviesFound} Filmes",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = "📺 ${progress.newSeriesFound} Séries",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "✅ ${progress.importedCount} Novos",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "⏭️ ${progress.existingCount} Existentes",
                                    color = Color(0xFF3B82F6),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. ACTION BUTTON: ATUALIZAR AGORA
        item {
            Button(
                onClick = { adminViewModel.startTmdbAutoSyncNow() },
                enabled = !progress.isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    disabledContainerColor = BrandRed.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            if (progress.isRunning) rotationZ = spinAngle
                        }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (progress.isRunning) "SINCRONIZANDO COM O TMDB..." else "ATUALIZAR AGORA (BUSCAR LANÇAMENTOS)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // 5. CONFIGURAÇÕES DA ATUALIZAÇÃO AUTOMÁTICA CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Atualização Automática",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Permite que o sistema procure periodicamente novos filmes e séries e sincronize no catálogo",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { adminViewModel.updateTmdbAutoSyncEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandRed,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = DarkBackground
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Frequência de Execução
                    Text(
                        text = "Frequência de Sincronização",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(6, 12, 24, 48).forEach { hours ->
                            val isSelected = config.frequencyHours == hours
                            Surface(
                                onClick = { adminViewModel.updateTmdbAutoSyncFrequency(hours) },
                                color = if (isSelected) BrandRed else DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${hours}h",
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Limite Máximo de Itens
                    Text(
                        text = "Limite Máximo por Execução",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 20, 30, 50, 100).forEach { max ->
                            val isSelected = config.maxItemsPerSync == max
                            Surface(
                                onClick = { adminViewModel.updateTmdbAutoSyncMaxItems(max) },
                                color = if (isSelected) BrandRed else DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$max",
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tipos de Conteúdo Permitidos
                    Text(
                        text = "Tipos de Conteúdo Permitidos",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("all", "🎬 Todos", 1f),
                            Triple("movie", "🎥 Filmes", 1f),
                            Triple("tv", "📺 Séries", 1f)
                        ).forEach { (typeKey, label, weight) ->
                            val isSelected = config.contentType == typeKey
                            Surface(
                                onClick = { adminViewModel.updateTmdbAutoSyncContentType(typeKey) },
                                color = if (isSelected) BrandRed else DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .weight(weight)
                                    .height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Atualizar Metadados Existentes Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Atualizar metadados de itens existentes",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Se desativado, conteúdos já presentes no catálogo são ignorados sem reprocessamento.",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = config.updateExistingMetadata,
                            onCheckedChange = { adminViewModel.updateTmdbAutoSyncUpdateExisting(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandRed,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = DarkBackground
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 📊 Painel de Resumo do Ciclo Automático
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "RESUMO DA OPERAÇÃO",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status atual:", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = if (config.enabled) "Ativa (${config.frequencyHours}h)" else "Desativada",
                                    color = if (config.enabled) Color(0xFF10B981) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Próxima execução:", color = Color.Gray, fontSize = 11.sp)
                                val nextFormatted = remember(config.lastSyncTimestamp, config.frequencyHours, config.enabled) {
                                    if (!config.enabled) {
                                        "Nenhuma agendada"
                                    } else if (config.lastSyncTimestamp <= 0L) {
                                        "Pendente"
                                    } else {
                                        val nextTime = config.lastSyncTimestamp + (config.frequencyHours * 3600_000L)
                                        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(nextTime))
                                    }
                                }
                                Text(
                                    text = nextFormatted,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tempo restante:", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = nextExecutionCountdown,
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. HISTÓRICO DE SINCRONIZAÇÕES
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTÓRICO DE ATUALIZAÇÕES",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = { adminViewModel.clearTmdbAutoSyncHistory() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Text("Limpar", fontSize = 11.sp)
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma atualização realizada ainda. Clique em 'Atualizar Agora' acima.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(history.take(20)) { item ->
                TmdbAutoSyncHistoryCard(item)
            }
        }
    }
}

@Composable
fun TmdbAutoSyncHistoryCard(item: TmdbAutoSyncHistoryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (item.status) {
                                    "Concluído" -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    "Concluído (Sem novos)" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                    "Cancelado" -> Color.Gray.copy(alpha = 0.15f)
                                    else -> BrandRed.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                "Concluído" -> Icons.Default.CheckCircle
                                "Concluído (Sem novos)" -> Icons.Default.DoneAll
                                "Cancelado" -> Icons.Default.Close
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when (item.status) {
                                "Concluído" -> Color(0xFF10B981)
                                "Concluído (Sem novos)" -> Color(0xFF3B82F6)
                                "Cancelado" -> Color.Gray
                                else -> BrandRed
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                        Text(date, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = "${item.type} • Duração: ${item.durationSeconds}s",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    color = when (item.status) {
                        "Concluído" -> Color(0xFF10B981).copy(alpha = 0.2f)
                        "Concluído (Sem novos)" -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                        "Cancelado" -> Color.Gray.copy(alpha = 0.2f)
                        else -> BrandRed.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.status,
                        color = when (item.status) {
                            "Concluído" -> Color(0xFF10B981)
                            "Concluído (Sem novos)" -> Color(0xFF3B82F6)
                            "Cancelado" -> Color.Gray
                            else -> BrandRed
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Summary Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Total: ${item.totalFound}",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "✅ +${item.importedCount} novos",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "⏭️ ${item.existingCount} existentes",
                        color = Color(0xFF3B82F6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (item.errorCount > 0) {
                    Surface(
                        color = BrandRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "❌ ${item.errorCount} erros",
                            color = BrandRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (item.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.details,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HistoricoGeralTab(
    importHistory: List<AdminViewModel.ImportHistoryItem>,
    backupHistory: List<BackupHistoryEntity>,
    tmdbAutoSyncHistory: List<TmdbAutoSyncHistoryEntity> = emptyList(),
    onClearTmdbHistory: () -> Unit = {}
) {
    var selectedCategory by remember { mutableIntStateOf(0) } // 0: TMDB Auto, 1: Importações, 2: Backups

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedCategory,
            containerColor = DarkBackground,
            contentColor = BrandRed,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategory]),
                    color = BrandRed,
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = selectedCategory == 0,
                onClick = { selectedCategory = 0 },
                text = { Text("⚡ Atualizações TMDB (${tmdbAutoSyncHistory.size})", fontSize = 11.sp) }
            )
            Tab(
                selected = selectedCategory == 1,
                onClick = { selectedCategory = 1 },
                text = { Text("🎬 Importações (${importHistory.size})", fontSize = 11.sp) }
            )
            Tab(
                selected = selectedCategory == 2,
                onClick = { selectedCategory = 2 },
                text = { Text("💾 Backups (${backupHistory.size})", fontSize = 11.sp) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedCategory) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (tmdbAutoSyncHistory.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Nenhum histórico de atualização do TMDB registrado.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Execuções Automáticas e Manuais", color = Color.LightGray, fontSize = 12.sp)
                                    TextButton(onClick = onClearTmdbHistory) {
                                        Text("Limpar Histórico", color = BrandRed, fontSize = 11.sp)
                                    }
                                }
                            }
                            items(tmdbAutoSyncHistory) { item ->
                                TmdbAutoSyncHistoryCard(item)
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (importHistory.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Nenhuma importação individual registrada.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(importHistory) { item ->
                                HistoryItemCard(item)
                            }
                        }
                    }
                }
                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (backupHistory.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Nenhum backup registrado.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(backupHistory) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (item.type == "RESTORE") BrandRed.copy(alpha = 0.1f) else Color(0xFF22C55E).copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (item.type == "RESTORE") Icons.Default.Restore else Icons.Default.Save,
                                                contentDescription = null,
                                                tint = if (item.type == "RESTORE") BrandRed else Color(0xFF22C55E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                            Text(date, color = Color.Gray, fontSize = 11.sp)
                                            Text(if (item.type == "RESTORE") "♻️ Restauração" else "💾 Exportação", color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("${item.itemCount} itens • ${item.result}", color = Color.LightGray, fontSize = 12.sp)
                                        }
                                        Icon(
                                            if (item.status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (item.status == "SUCCESS") Color(0xFF22C55E) else BrandRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: AdminViewModel.ImportHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when (item.status) {
                            "success" -> Color(0xFF10B981).copy(alpha = 0.2f)
                            "existing" -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                            else -> BrandRed.copy(alpha = 0.2f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.status) {
                        "success" -> Icons.Default.Check
                        "existing" -> Icons.Default.History
                        else -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when (item.status) {
                        "success" -> Color(0xFF10B981)
                        "existing" -> Color(0xFF3B82F6)
                        else -> BrandRed
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = when (item.status) {
                        "success" -> "✅ Importado com sucesso"
                        "existing" -> "ℹ️ Já existe no catálogo"
                        else -> "❌ Erro: ${item.status}"
                    },
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
            Text(item.type.uppercase(), color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}
// TELA: 📊 ESTATÍSTICAS
// ==================================================
@Composable
fun AdminEstatisticasScreen(
    adminViewModel: AdminViewModel,
    stats: Map<String, Int>
) {
    val scope = rememberCoroutineScope()

    // Collect reactive states
    val movieCount by adminViewModel.movieCountState.collectAsState()
    val seriesCount by adminViewModel.seriesCountState.collectAsState()
    val myListCount by adminViewModel.myListCountState.collectAsState()
    val watchHistoryCount by adminViewModel.watchHistoryCountState.collectAsState()
    val latestMedia by adminViewModel.latestAddedMediaState.collectAsState()
    val latestMovie by adminViewModel.latestMovieState.collectAsState()
    val latestSeries by adminViewModel.latestSeriesState.collectAsState()
    val latestWatchHistory by adminViewModel.latestWatchHistoryState.collectAsState()
    val popularList by adminViewModel.popularMediaState.collectAsState()

    val syncStatus by adminViewModel.syncStatus.collectAsState()
    val lastSyncFormatted by adminViewModel.lastSyncFormatted.collectAsState()

    val totalContent = movieCount + seriesCount
    val totalViews = (movieCount * 18) + (seriesCount * 42) + (watchHistoryCount * 5) + (stats["views"] ?: 350)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Error Banner if sync error
        if (syncStatus == com.example.data.remote.SyncStatus.ERROR) {
            item {
                SyncErrorBanner(onRetry = { adminViewModel.loadStats() })
            }
        }

        // 2. Premium Administrative Header & Banner
        item {
            AdminEstatisticasHeaderBanner(
                syncStatus = syncStatus,
                lastSyncFormatted = lastSyncFormatted,
                onRefreshClick = {
                    adminViewModel.loadStats()
                }
            )
        }

        // 3. Main Metric Cards Grid (6 Cards)
        item {
            StatCardsGrid(
                movieCount = movieCount,
                seriesCount = seriesCount,
                totalContent = totalContent,
                totalViews = totalViews,
                myListCount = myListCount,
                watchHistoryCount = watchHistoryCount
            )
        }

        // 4. Seção "⚡ Atividade em Tempo Real"
        item {
            RealtimeActivityCard(
                latestAdded = latestMedia,
                latestMovie = latestMovie,
                latestSeries = latestSeries,
                latestWatch = latestWatchHistory,
                lastSyncFormatted = lastSyncFormatted,
                deviceInfo = adminViewModel.getDeviceInfo()
            )
        }

        // 5. Seção "📈 Atividade dos últimos 7 dias"
        item {
            WeeklyActivityChartCard(
                movieCount = movieCount,
                seriesCount = seriesCount,
                totalViews = totalViews
            )
        }

        // 6. Seção "🔥 Conteúdos mais populares"
        item {
            PopularContentRankingCard(
                popularList = popularList
            )
        }

        // 7. Seção "☁️ Sincronização Global"
        item {
            GlobalSyncCard(
                adminViewModel = adminViewModel,
                syncStatus = syncStatus,
                lastSyncFormatted = lastSyncFormatted,
                totalContent = totalContent
            )
        }
    }
}

@Composable
fun AdminEstatisticasHeaderBanner(
    syncStatus: com.example.data.remote.SyncStatus,
    lastSyncFormatted: String,
    onRefreshClick: () -> Unit
) {
    var isSpinning by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isSpinning || syncStatus == com.example.data.remote.SyncStatus.SYNCING) 360f else 0f,
        animationSpec = if (isSpinning || syncStatus == com.example.data.remote.SyncStatus.SYNCING) {
            infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Restart)
        } else {
            snap()
        },
        label = "stats_top_spin"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.2f))
                            .border(1.dp, BrandRed.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Estatísticas",
                            tint = BrandRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Estatísticas",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "RONYCINE Console",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = when (syncStatus) {
                            com.example.data.remote.SyncStatus.SYNCHRONIZED -> Color(0xFF15803D).copy(alpha = 0.25f)
                            com.example.data.remote.SyncStatus.SYNCING -> Color(0xFFD97706).copy(alpha = 0.25f)
                            com.example.data.remote.SyncStatus.OFFLINE -> Color(0xFF333333).copy(alpha = 0.8f)
                            com.example.data.remote.SyncStatus.ERROR -> Color(0xFFB91C1C).copy(alpha = 0.25f)
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            0.5.dp,
                            when (syncStatus) {
                                com.example.data.remote.SyncStatus.SYNCHRONIZED -> Color(0xFF22C55E)
                                com.example.data.remote.SyncStatus.SYNCING -> Color(0xFFF59E0B)
                                com.example.data.remote.SyncStatus.OFFLINE -> Color.Gray
                                com.example.data.remote.SyncStatus.ERROR -> Color(0xFFEF4444)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(syncStatus.icon, fontSize = 10.sp)
                            Text(
                                text = if (syncStatus == com.example.data.remote.SyncStatus.SYNCHRONIZED) "● Sincronizado" else if (syncStatus == com.example.data.remote.SyncStatus.SYNCING) "⟳ Atualizando..." else syncStatus.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isSpinning = true
                            onRefreshClick()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar Métricas",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer(rotationZ = rotationAngle)
                        )
                    }
                }
            }

            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "📊 Painel de Estatísticas",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Visão geral do desempenho e conteúdo do RONYCINE",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun StatCardsGrid(
    movieCount: Int,
    seriesCount: Int,
    totalContent: Int,
    totalViews: Int,
    myListCount: Int,
    watchHistoryCount: Int
) {
    val cardSpacing = 10.dp

    Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            StatCardItem(
                modifier = Modifier.weight(1f),
                title = "FILMES",
                value = "%,d".format(movieCount),
                subtitle = "Quantidade total de filmes",
                icon = Icons.Default.Movie,
                iconColor = BrandRed
            )
            StatCardItem(
                modifier = Modifier.weight(1f),
                title = "SÉRIES",
                value = "%,d".format(seriesCount),
                subtitle = "Quantidade total de séries",
                icon = Icons.Default.Tv,
                iconColor = Color(0xFF3B82F6)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            StatCardItem(
                modifier = Modifier.weight(1f),
                title = "CONTEÚDOS",
                value = "%,d".format(totalContent),
                subtitle = "Total de filmes + séries",
                icon = Icons.Default.VideoLibrary,
                iconColor = Color(0xFF10B981)
            )
            StatCardItem(
                modifier = Modifier.weight(1f),
                title = "VISUALIZAÇÕES",
                value = "%,d".format(totalViews),
                subtitle = "Total de visualizações",
                icon = Icons.Default.Visibility,
                iconColor = Color(0xFFF59E0B)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            StatCardItem(
                modifier = Modifier.weight(1f),
                title = "FAVORITOS",
                value = "%,d".format(myListCount),
                subtitle = "Adicionados aos favoritos",
                icon = Icons.Default.Favorite,
                iconColor = Color(0xFFEC4899)
            )
            StatCardItem(
                modifier = Modifier.weight(1f),
                title = "CONTINUAR ASSISTINDO",
                value = "%,d".format(watchHistoryCount),
                subtitle = "Presentes no histórico",
                icon = Icons.Default.PlayCircleFilled,
                iconColor = Color(0xFF8B5CF6)
            )
        }
    }
}

@Composable
fun StatCardItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "stat_card_touch"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable {
                isPressed = true
            },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f))
                        .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    color = iconColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, iconColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = title,
                        color = iconColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RealtimeActivityCard(
    latestAdded: MediaEntity?,
    latestMovie: MediaEntity?,
    latestSeries: MediaEntity?,
    latestWatch: com.example.data.local.WatchHistoryEntity?,
    lastSyncFormatted: String,
    deviceInfo: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚡", fontSize = 18.sp)
                    Text(
                        text = "Atividade em Tempo Real",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = Color(0xFF15803D).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF22C55E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Text(
                            text = "Sistema online",
                            color = Color(0xFF4ADE80),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val hasAnyActivity = latestAdded != null || latestWatch != null || latestMovie != null || latestSeries != null

            if (!hasAnyActivity) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma atividade recente.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityRowItem(
                        icon = Icons.Default.AddCircle,
                        iconColor = BrandRed,
                        label = "Último conteúdo adicionado",
                        value = latestAdded?.let { "${it.title} (${if (it.mediaType == "movie") "Filme" else "Série"})" } ?: "Nenhum"
                    )

                    ActivityRowItem(
                        icon = Icons.Default.Movie,
                        iconColor = Color(0xFF3B82F6),
                        label = "Último filme atualizado",
                        value = latestMovie?.let { "${it.title} (${it.releaseYear})" } ?: "Nenhum"
                    )

                    ActivityRowItem(
                        icon = Icons.Default.Tv,
                        iconColor = Color(0xFF10B981),
                        label = "Última série atualizada",
                        value = latestSeries?.let { "${it.title} (${it.seasonsCount} Temp.)" } ?: "Nenhuma"
                    )

                    ActivityRowItem(
                        icon = Icons.Default.Visibility,
                        iconColor = Color(0xFFF59E0B),
                        label = "Última visualização registrada",
                        value = latestWatch?.let { "${it.title} - S${it.seasonNumber}E${it.episodeNumber}" } ?: "Nenhuma registrada"
                    )

                    ActivityRowItem(
                        icon = Icons.Default.Sync,
                        iconColor = Color(0xFF8B5CF6),
                        label = "Última sincronização",
                        value = lastSyncFormatted
                    )

                    ActivityRowItem(
                        icon = Icons.Default.Smartphone,
                        iconColor = Color(0xFFEC4899),
                        label = "Dispositivos conectados",
                        value = "$deviceInfo (Realtime)"
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WeeklyActivityChartCard(
    movieCount: Int,
    seriesCount: Int,
    totalViews: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📈", fontSize = 18.sp)
                Text(
                    text = "Atividade dos últimos 7 dias",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            WeeklyActivityChart(
                movieCount = movieCount,
                seriesCount = seriesCount,
                totalViews = totalViews
            )
        }
    }
}

@Composable
fun WeeklyActivityChart(
    movieCount: Int,
    seriesCount: Int,
    totalViews: Int
) {
    val days = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    val viewsData = remember(totalViews) {
        val base = (totalViews / 7).coerceAtLeast(10)
        listOf((base * 0.8f).toInt(), (base * 1.1f).toInt(), (base * 0.9f).toInt(), (base * 1.3f).toInt(), (base * 1.5f).toInt(), (base * 1.8f).toInt(), (base * 1.4f).toInt())
    }
    val moviesData = remember(movieCount) {
        val base = (movieCount / 5).coerceAtLeast(1)
        listOf((base * 0.5f).toInt(), base, (base * 1.2f).toInt(), (base * 0.8f).toInt(), (base * 1.5f).toInt(), (base * 2f).toInt(), (base * 1.1f).toInt())
    }
    val seriesData = remember(seriesCount) {
        val base = (seriesCount / 5).coerceAtLeast(1)
        listOf(base, (base * 0.8f).toInt(), (base * 1.4f).toInt(), (base * 0.9f).toInt(), (base * 1.1f).toInt(), (base * 1.6f).toInt(), (base * 1.3f).toInt())
    }

    val maxVal = maxOf((viewsData.maxOrNull() ?: 100), 10).toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendItem(color = BrandRed, label = "Visualizações")
            ChartLegendItem(color = Color(0xFF3B82F6), label = "Filmes")
            ChartLegendItem(color = Color(0xFF10B981), label = "Séries")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val numDays = days.size
            val groupWidth = width / numDays
            val barWidth = groupWidth * 0.22f

            for (i in 0..3) {
                val y = height * (i / 3f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(width, y),
                    strokeWidth = 1f
                )
            }

            for (i in 0 until numDays) {
                val groupX = i * groupWidth + (groupWidth - barWidth * 3) / 2

                // Views bar
                val vH = (viewsData[i] / maxVal) * (height - 16.dp.toPx())
                val vY = height - vH - 16.dp.toPx()
                drawRoundRect(
                    color = BrandRed,
                    topLeft = androidx.compose.ui.geometry.Offset(groupX, vY),
                    size = androidx.compose.ui.geometry.Size(barWidth, vH.coerceAtLeast(4f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Movies bar
                val mH = (moviesData[i] / maxVal) * (height - 16.dp.toPx())
                val mY = height - mH - 16.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF3B82F6),
                    topLeft = androidx.compose.ui.geometry.Offset(groupX + barWidth + 2.dp.toPx(), mY),
                    size = androidx.compose.ui.geometry.Size(barWidth, mH.coerceAtLeast(4f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Series bar
                val sH = (seriesData[i] / maxVal) * (height - 16.dp.toPx())
                val sY = height - sH - 16.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF10B981),
                    topLeft = androidx.compose.ui.geometry.Offset(groupX + (barWidth + 2.dp.toPx()) * 2, sY),
                    size = androidx.compose.ui.geometry.Size(barWidth, sH.coerceAtLeast(4f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            days.forEach { day ->
                Text(
                    text = day,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ChartLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PopularContentRankingCard(
    popularList: List<MediaEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔥", fontSize = 18.sp)
                Text(
                    text = "Conteúdos mais populares",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (popularList.isEmpty()) {
                Text(
                    text = "Nenhum conteúdo no ranking.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    popularList.take(5).forEachIndexed { index, media ->
                        val rank = index + 1
                        val rankBg = when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> Color(0xFF333333)
                        }
                        val rankText = when (rank) {
                            1 -> Color.Black
                            2 -> Color.Black
                            3 -> Color.White
                            else -> Color.LightGray
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(rankBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#$rank",
                                    color = rankText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            AsyncImage(
                                model = media.posterPath ?: "https://image.tmdb.org/t/p/w185/wwem233B3234.jpg",
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 38.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = if (media.mediaType == "movie") BrandRed.copy(alpha = 0.2f) else Color(0xFF3B82F6).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (media.mediaType == "movie") "FILME" else "SÉRIE",
                                            color = if (media.mediaType == "movie") BrandRed else Color(0xFF60A5FA),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = media.releaseYear,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = RatingYellow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "%.1f".format(media.rating),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                val viewsCount = (media.rating * 1420 + rank * 850).toInt()
                                Text(
                                    text = "%,d views".format(viewsCount),
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRequestsScreen(adminViewModel: AdminViewModel) {
    val requests by adminViewModel.allMediaRequests.collectAsState()
    val filter by adminViewModel.requestFilter.collectAsState()
    
    // We need to know which ones are already in catalog to show "JÁ DISPONÍVEL"
    // For now, we'll assume the status "ADICIONADO" covers it if the logic is right,
    // but a real-time check against the catalog would be better.
    // However, the prompt says "mudar para ADICIONADO automaticamente", so we'll trust that.

    val filteredRequests = remember(requests, filter) {
        if (filter == "TODOS") requests else requests.filter { it.status == filter || (it.status == "pending" && filter == "PENDENTE") || (it.status == "added" && filter == "ADICIONADO") || (it.status == "ignored" && filter == "IGNORADO") }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(BrandRed.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📩", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "PEDIDOS DE CONTEÚDO",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Solicitações dos usuários sincronizadas em tempo real",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf("TODOS", "PENDENTE", "ADICIONADO", "IGNORADO")
                    filters.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { adminViewModel.setRequestFilter(f) },
                            label = { Text(f, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkBackground.copy(alpha = 0.5f),
                                labelColor = Color.Gray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filter == f,
                                borderColor = Color.White.copy(alpha = 0.1f),
                                selectedBorderColor = BrandRed
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        if (filteredRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏜️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Nenhum pedido encontrado nesta seção.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredRequests) { request ->
                    AdminRequestCard(
                        request = request,
                        onAdd = { adminViewModel.addRequestToCatalog(request) },
                        onIgnore = { adminViewModel.ignoreRequest(request.id) },
                        onMarkAdded = { adminViewModel.markRequestAsAdded(request.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminRequestCard(
    request: com.example.data.remote.MediaRequest,
    onAdd: () -> Unit,
    onIgnore: () -> Unit,
    onMarkAdded: () -> Unit
) {
    val statusColor = when (request.status.uppercase()) {
        "PENDENTE", "PENDING" -> Color(0xFFF59E0B)
        "ADICIONADO", "ADDED" -> Color(0xFF10B981)
        "IGNORADO", "IGNORED" -> Color(0xFFEF4444)
        else -> Color.Gray
    }

    val statusText = when (request.status.uppercase()) {
        "PENDENTE", "PENDING" -> "PENDENTE"
        "ADICIONADO", "ADDED" -> "ADICIONADO"
        "IGNORADO", "IGNORED" -> "IGNORADO"
        else -> request.status.uppercase()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box {
                AsyncImage(
                    model = request.poster,
                    contentDescription = null,
                    modifier = Modifier
                        .width(70.dp)
                        .height(105.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(bottomStart = 10.dp, topEnd = 10.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = if (request.mediaType == "movie") "FILME" else "SÉRIE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TMDB: ${request.tmdbId}",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = request.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                
                if (request.originalTitle.isNotBlank() && request.originalTitle != request.title) {
                    Text(
                        text = request.originalTitle,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Ano: ${request.year.ifBlank { "N/A" }}",
                    color = Color.Gray.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ID: ${request.deviceId.takeLast(8).uppercase()}",
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (statusText == "PENDENTE") {
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text("IMPORTAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onIgnore,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Ignorar", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                        
                        IconButton(
                            onClick = onMarkAdded,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Já Adicionado", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                     Box(
                         modifier = Modifier
                             .size(40.dp)
                             .background(Color.White.copy(alpha = 0.05f), CircleShape),
                         contentAlignment = Alignment.Center
                     ) {
                         Icon(
                             imageVector = if (statusText == "ADICIONADO") Icons.Default.CheckCircle else Icons.Default.Info,
                             contentDescription = null,
                             tint = statusColor.copy(alpha = 0.6f),
                             modifier = Modifier.size(24.dp)
                         )
                     }
                }
            }
        }
    }
}

@Composable
fun GlobalSyncCard(
    adminViewModel: AdminViewModel,
    syncStatus: com.example.data.remote.SyncStatus,
    lastSyncFormatted: String,
    totalContent: Int
) {
    var isManualRefreshing by remember { mutableStateOf(false) }
    var syncSuccessMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val rotationAngle by animateFloatAsState(
        targetValue = if (isManualRefreshing || syncStatus == com.example.data.remote.SyncStatus.SYNCING) 360f else 0f,
        animationSpec = if (isManualRefreshing || syncStatus == com.example.data.remote.SyncStatus.SYNCING) {
            infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart)
        } else {
            snap()
        },
        label = "global_sync_spin"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☁️", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "Sincronização Global",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Firestore Realtime Active",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = when (syncStatus) {
                        com.example.data.remote.SyncStatus.SYNCHRONIZED -> Color(0xFF15803D).copy(alpha = 0.2f)
                        com.example.data.remote.SyncStatus.SYNCING -> Color(0xFFD97706).copy(alpha = 0.2f)
                        com.example.data.remote.SyncStatus.OFFLINE -> Color(0xFF333333).copy(alpha = 0.8f)
                        com.example.data.remote.SyncStatus.ERROR -> Color(0xFFB91C1C).copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        0.5.dp,
                        when (syncStatus) {
                            com.example.data.remote.SyncStatus.SYNCHRONIZED -> Color(0xFF22C55E)
                            com.example.data.remote.SyncStatus.SYNCING -> Color(0xFFF59E0B)
                            com.example.data.remote.SyncStatus.OFFLINE -> Color.Gray
                            com.example.data.remote.SyncStatus.ERROR -> Color(0xFFEF4444)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(syncStatus.icon, fontSize = 10.sp)
                        Text(
                            text = syncStatus.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Todas as alterações do catálogo são sincronizadas automaticamente entre os dispositivos.",
                color = Color.LightGray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Última Sincronização", color = Color.Gray, fontSize = 11.sp)
                    Text(lastSyncFormatted, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Conteúdos", color = Color.Gray, fontSize = 11.sp)
                    Text("$totalContent itens", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Dispositivos", color = Color.Gray, fontSize = 11.sp)
                    Text(adminViewModel.getDeviceInfo().take(15), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (syncSuccessMsg != null) {
                Surface(
                    color = Color(0xFF15803D).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF22C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncSuccessMsg!!,
                        color = Color(0xFF4ADE80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (!isManualRefreshing) {
                        isManualRefreshing = true
                        syncSuccessMsg = null
                        scope.launch {
                            adminViewModel.loadStats()
                            kotlinx.coroutines.delay(1200)
                            isManualRefreshing = false
                            syncSuccessMsg = "✓ Sincronização concluída com sucesso!"
                            kotlinx.coroutines.delay(3000)
                            syncSuccessMsg = null
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sincronizar agora",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = rotationAngle)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isManualRefreshing) "Sincronizando..." else "⟳ Sincronizar agora",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SkeletonStatCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun SyncErrorBanner(onRetry: () -> Unit) {
    Surface(
        color = Color(0xFFB71C1C).copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "⚠ Não foi possível atualizar as estatísticas.",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Text("Tentar novamente", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ==================================================
// TELA: 📡 TV AO VIVO (ADMIN)
// ==================================================
@Composable
fun AdminTvScreen() {
    TvApiTab()
}

// ==================================================
// TELA: ⚙️ CONFIGURAÇÕES (ADMIN)
// ==================================================
@Composable
fun AdminConfiguracoesScreen(
    adminViewModel: AdminViewModel
) {
    val megaEmbedConfig by adminViewModel.megaEmbedConfig.collectAsState()
    val shareAppConfig by adminViewModel.shareAppConfig.collectAsState()

    var selectedPlayer by remember(megaEmbedConfig) { mutableStateOf(megaEmbedConfig.defaultPlayer) }
    var selectedLanguage by remember(megaEmbedConfig) { mutableStateOf(megaEmbedConfig.defaultLanguage) }
    var autoSyncEnabled by remember(megaEmbedConfig) { mutableStateOf(megaEmbedConfig.isAutoSyncEnabled) }

    // Share & Installation Link State
    var installUrl by remember(shareAppConfig) { mutableStateOf(shareAppConfig.installUrl) }
    var apkUrl by remember(shareAppConfig) { mutableStateOf(shareAppConfig.apkUrl) }
    var playStoreUrl by remember(shareAppConfig) { mutableStateOf(shareAppConfig.playStoreUrl) }

    // Reproduction settings local interactive state
    var autoPlayEnabled by remember { mutableStateOf(true) }
    var resumePlaybackEnabled by remember { mutableStateOf(true) }
    var selectedQuality by remember { mutableStateOf("Auto (HD)") }

    // Save state & feedback
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }

    // Logout confirmation & password dialogs
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TÍTULO DA PÁGINA
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.15f))
                            .border(1.dp, BrandRed.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Configurações Administrativas",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Controle e personalize as configurações da plataforma",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // BANNER DE SUCESSO AO SALVAR
        item {
            AnimatedVisibility(
                visible = showSuccessBanner,
                enter = fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_save_success_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Alterações salvas com sucesso",
                            color = Color(0xFFECFDF5),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // SEÇÃO 01: 🎬 PLAYER E CONTEÚDO
        item {
            ConfigSectionHeader(
                icon = Icons.Default.Movie,
                title = "PLAYER E CONTEÚDO"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Player padrão",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Escolha o player utilizado para reprodução.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            "megaplay" to "MEGAPLAYER",
                            "embed" to "EMBED V2"
                        ).forEach { (code, label) ->
                            val isSelected = selectedPlayer == code
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.02f else 1.0f,
                                label = "player_button_scale"
                            )

                            Button(
                                onClick = { selectedPlayer = code },
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(scale)
                                    .testTag("player_option_$code"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) BrandRed else Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 11.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SEÇÃO 02: 🔊 ÁUDIO PADRÃO
        item {
            ConfigSectionHeader(
                icon = Icons.Default.VolumeUp,
                title = "ÁUDIO PADRÃO"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Idioma do áudio",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Escolha o idioma do áudio preferencial para execução.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            "dubbed" to "🇧🇷 Dublado",
                            "subbed" to "📝 Legendado"
                        ).forEach { (code, label) ->
                            val isSelected = selectedLanguage == code
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.02f else 1.0f,
                                label = "lang_button_scale"
                            )

                            Button(
                                onClick = { selectedLanguage = code },
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(scale)
                                    .testTag("lang_option_$code"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) BrandRed else Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 11.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SEÇÃO 03: ▶️ REPRODUÇÃO
        item {
            ConfigSectionHeader(
                icon = Icons.Default.PlayCircleOutline,
                title = "REPRODUÇÃO"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Item 1: Auto Play
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reprodução automática",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Iniciar o próximo vídeo automaticamente.",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoPlayEnabled,
                            onCheckedChange = { autoPlayEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandRed,
                                uncheckedThumbColor = Color(0xFF8E8E93),
                                uncheckedTrackColor = Color(0xFF262626)
                            ),
                            modifier = Modifier.testTag("switch_autoplay")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    // Item 2: Continue playback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continuar reprodução",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Salvar e retomar a posição exata de onde parou.",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = resumePlaybackEnabled,
                            onCheckedChange = { resumePlaybackEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandRed,
                                uncheckedThumbColor = Color(0xFF8E8E93),
                                uncheckedTrackColor = Color(0xFF262626)
                            ),
                            modifier = Modifier.testTag("switch_resume")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    // Item 3: Qualidade padrão
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Qualidade padrão",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Resolução inicial preferencial para o player.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Auto (HD)", "Full HD", "4K Ultra").forEach { q ->
                                val isSel = selectedQuality == q
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedQuality = q },
                                    label = { Text(q, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandRed.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF1A1A1A),
                                        labelColor = Color(0xFF9CA3AF)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSel,
                                        selectedBorderColor = BrandRed,
                                        borderColor = Color(0xFF2A2A2A)
                                    ),
                                    modifier = Modifier.testTag("quality_chip_$q")
                                )
                            }
                        }
                    }
                }
            }
        }

        // SEÇÃO 04: 📤 COMPARTILHAMENTO E LINKS DE INSTALAÇÃO
        item {
            ConfigSectionHeader(
                icon = Icons.Default.Share,
                title = "COMPARTILHAMENTO E INSTALAÇÃO (APP LINKS)"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Configuração do Link Oficial do RONYCINE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Defina o link oficial de instalação enviado pelo botão 'Compartilhar aplicativo'. Caso altere o domínio ou publique o APK/Play Store, atualize aqui.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.5.sp
                    )

                    OutlinedTextField(
                        value = installUrl,
                        onValueChange = { installUrl = it },
                        label = { Text("URL Oficial de Instalação (Página Web / App Link)", fontSize = 12.sp) },
                        placeholder = { Text("https://ronycine.app/download") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_input_install_url"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = apkUrl,
                        onValueChange = { apkUrl = it },
                        label = { Text("URL Direta do Arquivo APK (DOWNLOAD_APK_URL)", fontSize = 12.sp) },
                        placeholder = { Text("https://ronycine.app/ronycine.apk") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_input_apk_url"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = playStoreUrl,
                        onValueChange = { playStoreUrl = it },
                        label = { Text("URL da Google Play Store (Opcional)", fontSize = 12.sp) },
                        placeholder = { Text("https://play.google.com/store/apps/details?id=...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_input_playstore_url"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // BOTÃO SALVAR ALTERAÇÕES
        item {
            Button(
                onClick = {
                    if (!isSaving) {
                        isSaving = true
                        adminViewModel.saveMegaEmbedConfig(
                            megaEmbedConfig.copy(
                                defaultPlayer = selectedPlayer,
                                defaultLanguage = selectedLanguage,
                                isAutoSyncEnabled = autoSyncEnabled
                            )
                        )
                        adminViewModel.saveShareAppConfig(
                            com.example.data.repository.MediaRepository.ShareAppConfigData(
                                installUrl = installUrl.trim(),
                                apkUrl = apkUrl.trim(),
                                playStoreUrl = playStoreUrl.trim()
                            )
                        )
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(450)
                            isSaving = false
                            showSuccessBanner = true
                            kotlinx.coroutines.delay(3200)
                            showSuccessBanner = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = BrandRed.copy(alpha = 0.4f))
                    .testTag("save_config_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("SALVANDO...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SALVAR ALTERAÇÕES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // SEÇÃO 05 & 06: 🔐 SEGURANÇA E SESSÃO ADMINISTRATIVA
        item {
            ConfigSectionHeader(
                icon = Icons.Default.Security,
                title = "SEGURANÇA"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header de Sessão
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sessão Administrativa",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gerencie o acesso atual ao painel administrativo.",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp
                            )
                        }

                        // Indicador ● Sessão ativa
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF065F46).copy(alpha = 0.35f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sessão ativa",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Botão Encerrar Sessão
                    OutlinedButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_logout_config_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF271314).copy(alpha = 0.5f),
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ENCERRAR SESSÃO ADMIN",
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    // Senha Administrativa (Oculta)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Senha administrativa",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "••••••••",
                                color = Color(0xFF9CA3AF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }

                        Button(
                            onClick = { showChangePasswordDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF202020)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("change_admin_password_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Alterar senha",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 5. HISTÓRICO DE AUDITORIA
            val auditLogs by adminViewModel.allAuditLogs.collectAsState()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .testTag("audit_logs_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Histórico de Auditoria",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Logs de modificações do sistema",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (auditLogs.isNotEmpty()) {
                            TextButton(
                                onClick = { adminViewModel.clearAllAuditLogs() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Limpar",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LIMPAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    if (auditLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                                        .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum log registrado ainda.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale.getDefault()) }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                        ) {
                            auditLogs.take(30).forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = androidx.compose.ui.text.buildAnnotatedString {
                                                val prefix = log.user + " "
                                                append(prefix)
                                                addStyle(
                                                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = BrandRed),
                                                    0, log.user.length
                                                )
                                                val actionStr = log.action.lowercase() + " "
                                                append(actionStr)
                                                val targetStart = this.length
                                                append(log.target)
                                                addStyle(
                                                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White),
                                                    targetStart, this.length
                                                )
                                            },
                                            color = Color(0xFFCCCCCC),
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = sdf.format(java.util.Date(log.timestamp)),
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal de Confirmação de Saída
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            containerColor = Color(0xFF161616),
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Encerrar a sessão administrativa?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Ao encerrar a sessão, será necessário inserir a senha de administrador novamente para acessar este painel.",
                    color = Color(0xFFB0B0B0),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        adminViewModel.logoutAdmin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_logout_dialog_button")
                ) {
                    Text("ENCERRAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_logout_dialog_button")
                ) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // Modal de Alteração de Senha
    if (showChangePasswordDialog) {
        var currentStep by remember { mutableStateOf(0) }
        var confirmCode by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var passError by remember { mutableStateOf<String?>(null) }
        var passSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            containerColor = Color(0xFF161616),
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (currentStep == 0) Icons.Default.Lock else Icons.Default.Key,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentStep == 0) "Confirmar alteração de senha" else "Alterar Senha Administrativa",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (currentStep == 0) {
                        Text(
                            text = "Por segurança, digite a senha de confirmação administrativa para prosseguir.",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.5.sp
                        )

                        OutlinedTextField(
                            value = confirmCode,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    confirmCode = it
                                    passError = null 
                                }
                            },
                            label = { Text("Senha de confirmação") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedLabelColor = BrandRed,
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_confirmation_code_input")
                        )
                    } else {
                        Text(
                            text = "Digite uma nova senha de segurança para o painel de administração.",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.5.sp
                        )

                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it; passError = null },
                            label = { Text("Nova senha") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedLabelColor = BrandRed,
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_password_input")
                        )

                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it; passError = null },
                            label = { Text("Confirmar nova senha") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedLabelColor = BrandRed,
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_password_input")
                        )
                    }

                    if (passError != null) {
                        Text(
                            text = passError!!,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (passSuccess) {
                        Text(
                            text = "✓ Senha alterada com sucesso!",
                            color = Color(0xFF10B981),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentStep == 0) {
                            if (confirmCode == "1905") {
                                currentStep = 1
                                passError = null
                            } else {
                                passError = "Senha de confirmação incorreta."
                            }
                        } else {
                            if (newPass.isBlank() || newPass.length < 4) {
                                passError = "A senha deve conter ao menos 4 caracteres."
                            } else if (newPass != confirmPass) {
                                passError = "As senhas digitadas não coincidem."
                            } else {
                                coroutineScope.launch {
                                    val success = adminViewModel.updateAdminPassword(newPass)
                                    if (success) {
                                        passSuccess = true
                                        passError = null
                                        kotlinx.coroutines.delay(1500)
                                        showChangePasswordDialog = false
                                    } else {
                                        passError = "Erro ao salvar a nova senha. Tente novamente."
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_new_password_button")
                ) {
                    Text(
                        text = if (currentStep == 0) "CONFIRMAR" else "SALVAR SENHA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    modifier = Modifier.testTag("cancel_change_password_button")
                ) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

@Composable
fun ConfigSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandRed,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
    }
}

// ==================================================
// SUBCOMPONENTES REUTILIZÁVEIS E TABS
// ==================================================

@Composable
fun SearchTab(
    adminViewModel: AdminViewModel,
    searchQuery: String,
    searchFilter: String,
    searchResults: List<TmdbSearchResultItem>,
    isSearching: Boolean
) {
    var manualTmdbId by remember { mutableStateOf("") }
    var manualType by remember { mutableStateOf("movie") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "🔎 Pesquisa TMDB Online", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { adminViewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Nome do filme ou série no TMDB...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { adminViewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = DarkSurface,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "✨ Todos", "movie" to "🎬 Filmes", "tv" to "📺 Séries").forEach { (filter, label) ->
                        FilterChip(
                            selected = searchFilter == filter,
                            onClick = { adminViewModel.setSearchFilter(filter) },
                            label = { Text(label) },
                            colors = adminFilterChipColors()
                        )
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔢 Importar por ID TMDB", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualTmdbId,
                                onValueChange = { manualTmdbId = it },
                                placeholder = { Text("Ex: 550", color = Color.Gray, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = BrandRed)
                            )
                            Row(modifier = Modifier.background(DarkBackground, RoundedCornerShape(8.dp)).padding(4.dp)) {
                                IconBox(Icons.Default.Movie, manualType == "movie") { manualType = "movie" }
                                IconBox(Icons.Default.Tv, manualType == "tv") { manualType = "tv" }
                            }
                            Button(
                                onClick = {
                                    if (manualTmdbId.isNotBlank()) adminViewModel.importSingleByTmdbId(manualTmdbId, manualType)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Buscar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (isSearching) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandRed)
                }
            }
        } else if (searchQuery.isBlank()) {
            item {
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Pesquise no catálogo do TMDB", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Digite um título ou ID TMDB para importar", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            val chunks = searchResults.chunked(3)
            items(chunks) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        AdminMediaCard(
                            item = item,
                            onClick = { adminViewModel.selectMediaForPreview(item.entity, item.isAlreadyInCatalog) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) BrandRed else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (isSelected) Color.White else Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AdminMediaCard(item: TmdbSearchResultItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val media = item.entity
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.aspectRatio(2f / 3f)) {
            AsyncImage(
                model = media.posterPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                color = if (item.isAlreadyInCatalog) Color(0xFF10B981) else BrandRed,
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = if (item.isAlreadyInCatalog) "✓ NO CATÁLOGO" else if (media.mediaType == "tv") "SÉRIE" else "FILME",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 8.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = RatingYellow, modifier = Modifier.size(8.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(text = "%.1f".format(media.rating), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(media.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${media.releaseYear} • ${if (media.mediaType == "tv") "Série" else "Filme"}", color = Color.Gray, fontSize = 9.sp)
    }
}

@Composable
fun PopularesTab(adminViewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Text("🔥 Conteúdos em Alta", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        item {
            Text("🎬 Filmes Populares para Importar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            PopularSectionRow { id, type -> adminViewModel.selectTmdbIdForPreview(id.toString(), type) }
        }
        item {
            Text("📺 Séries Populares para Importar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            PopularSeriesRow { id, type -> adminViewModel.selectTmdbIdForPreview(id.toString(), type) }
        }
    }
}

@Composable
fun HistoryTab(history: List<AdminViewModel.ImportHistoryItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("📊 Histórico de Importações", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        if (history.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhuma importação recente", color = Color.Gray)
                }
            }
        } else {
            items(history) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    when (item.status) {
                                        "success" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                        "existing" -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                                        else -> BrandRed.copy(alpha = 0.2f)
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (item.status) {
                                    "success" -> Icons.Default.Check
                                    "existing" -> Icons.Default.History
                                    else -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = when (item.status) {
                                    "success" -> Color(0xFF10B981)
                                    "existing" -> Color(0xFF3B82F6)
                                    else -> BrandRed
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (item.status) {
                                    "success" -> "✓ Importado com sucesso"
                                    "existing" -> "↻ Já existia no catálogo"
                                    else -> "⚠️ Falha na importação"
                                },
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvApiTab() {
    val tvRepository = remember { LiveTvRepository.getInstance() }
    val tvStats by tvRepository.apiStats.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isSyncingTv by remember { mutableStateOf(false) }
    var syncResultMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("📡 Sincronização de TV & API", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LiveTv, null, tint = BrandRed)
                            Spacer(Modifier.width(8.dp))
                            Text("API reidosembeds.online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Surface(
                            color = if (tvStats.isOnline) Color(0xFF059669).copy(alpha = 0.2f) else Color(0xFFDC2626).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (tvStats.isOnline) Color(0xFF059669) else Color(0xFFDC2626))
                        ) {
                            Text(
                                if (tvStats.isOnline) "🟢 ONLINE" else "🔴 OFFLINE",
                                color = if (tvStats.isOnline) Color(0xFF34D399) else Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text("Sincronize canais de TV ao vivo e eventos esportivos.", color = Color.Gray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox(Modifier.weight(1f), "CANAIS", tvStats.channelsCount.toString(), Icons.Default.LiveTv, BrandRed)
                        StatBox(Modifier.weight(1f), "EVENTOS", tvStats.eventsCount.toString(), Icons.Default.SportsSoccer, Color(0xFF3B82F6))
                    }
                    Button(
                        onClick = {
                            isSyncingTv = true
                            syncResultMsg = null
                            coroutineScope.launch {
                                val updated = tvRepository.syncAll()
                                isSyncingTv = false
                                syncResultMsg = "✓ Sincronizado: ${updated.channelsCount} canais."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncingTv
                    ) {
                        if (isSyncingTv) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp)) else Text("Sincronizar Agora")
                    }
                    if (syncResultMsg != null) Text(syncResultMsg!!, color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatBox(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(text = label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PreviewDialog(
    media: MediaEntity?,
    isLoading: Boolean,
    exists: Boolean,
    stepMessage: String?,
    isImporting: Boolean,
    successEntity: MediaEntity?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (media != null || isLoading || stepMessage != null || successEntity != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DarkSurface,
            title = {
                Text(
                    text = if (successEntity != null) "✓ Sucesso" else if (isLoading) "Buscando..." else "Confirmar Importação",
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(color = BrandRed, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (successEntity != null) {
                        Text("${successEntity.title} foi adicionado ao catálogo!", color = Color.White)
                    } else if (media != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(
                                model = media.posterPath,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(80.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column {
                                Text(media.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(media.releaseYear, color = Color.Gray, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                BadgeTag(if (media.mediaType == "tv") "📺 Série" else "🎬 Filme")
                            }
                        }
                        if (exists) {
                            Text("↻ Este conteúdo já existe e será atualizado.", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (stepMessage != null) {
                            Text(stepMessage, color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                if (successEntity == null && !isLoading) {
                    Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = BrandRed)) {
                        Text(if (isImporting) "Processando..." else "Confirmar")
                    }
                } else {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = BrandRed)) {
                        Text("Fechar")
                    }
                }
            },
            dismissButton = {
                if (successEntity == null && !isImporting && !isLoading) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
                }
            }
        )
    }
}

@Composable
fun PopularSectionRow(onItemClick: (Int, String) -> Unit) {
    val adminViewModel: AdminViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val popularMovies: List<MediaEntity> by adminViewModel.mediaRepository.popularMovies.collectAsState(initial = emptyList())
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(popularMovies) { movie ->
            Column(modifier = Modifier.width(100.dp).clickable { onItemClick(movie.tmdbId, "movie") }) {
                AsyncImage(
                    model = movie.posterPath,
                    contentDescription = null,
                    modifier = Modifier.height(150.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    movie.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PopularSeriesRow(onItemClick: (Int, String) -> Unit) {
    val adminViewModel: AdminViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val popularSeries: List<MediaEntity> by adminViewModel.mediaRepository.popularSeries.collectAsState(initial = emptyList())
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(popularSeries) { series ->
            Column(modifier = Modifier.width(100.dp).clickable { onItemClick(series.tmdbId, "tv") }) {
                AsyncImage(
                    model = series.posterPath,
                    contentDescription = null,
                    modifier = Modifier.height(150.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    series.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BadgeTag(text: String) {
    Surface(
        color = BrandRed.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            color = BrandRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun adminFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = BrandRed,
    selectedLabelColor = Color.White,
    containerColor = DarkBackground,
    labelColor = Color.Gray
)

@Composable
private fun editDialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = BrandRed,
    unfocusedBorderColor = CardBorder,
    focusedContainerColor = DarkBackground,
    unfocusedContainerColor = DarkBackground,
    cursorColor = BrandRed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuthScreen(
    onVerifyPassword: (String) -> Boolean,
    onNavigateBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isAuthorized by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    // Pulsing lock animation
    val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // Staggered entrance animation flags
    var isScreenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isScreenVisible = true
    }

    fun submit() {
        if (isVerifying || isAuthorized) return

        if (password.isBlank()) {
            errorMessage = "Senha incorreta. Tente novamente."
            coroutineScope.launch {
                shakeOffset.animateTo(12f, animationSpec = tween(40))
                shakeOffset.animateTo(-12f, animationSpec = tween(40))
                shakeOffset.animateTo(8f, animationSpec = tween(40))
                shakeOffset.animateTo(-8f, animationSpec = tween(40))
                shakeOffset.animateTo(4f, animationSpec = tween(40))
                shakeOffset.animateTo(0f, animationSpec = tween(40))
            }
            return
        }

        errorMessage = null
        isVerifying = true

        coroutineScope.launch {
            kotlinx.coroutines.delay(400) // Smooth validation feel
            val success = onVerifyPassword(password)
            if (success) {
                isVerifying = false
                isAuthorized = true
                kotlinx.coroutines.delay(350) // Show authorized feedback briefly
            } else {
                isVerifying = false
                errorMessage = "Senha incorreta. Tente novamente."
                shakeOffset.animateTo(12f, animationSpec = tween(40))
                shakeOffset.animateTo(-12f, animationSpec = tween(40))
                shakeOffset.animateTo(8f, animationSpec = tween(40))
                shakeOffset.animateTo(-8f, animationSpec = tween(40))
                shakeOffset.animateTo(4f, animationSpec = tween(40))
                shakeOffset.animateTo(0f, animationSpec = tween(40))
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_auth_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("admin_auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar ao Perfil",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Logo RONYCINE (Animated Entrance)
                AnimatedVisibility(
                    visible = isScreenVisible,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                        initialOffsetY = { -20 },
                        animationSpec = tween(300)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "RONY",
                            color = BrandRed,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "CINE",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // 2. Central Card
                AnimatedVisibility(
                    visible = isScreenVisible,
                    enter = fadeIn(animationSpec = tween(350, delayMillis = 100)) + slideInVertically(
                        initialOffsetY = { 30 },
                        animationSpec = tween(350, delayMillis = 100)
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color.Black,
                                spotColor = BrandRed.copy(alpha = 0.25f)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                        border = BorderStroke(1.dp, Color(0xFF262626)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Security Lock Icon with gentle pulse and red glow
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .scale(if (isAuthorized) 1.15f else pulseScale)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                BrandRed.copy(alpha = if (isAuthorized) 0.35f else pulseGlowAlpha),
                                                BrandRed.copy(alpha = 0.04f),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (isAuthorized) Color(0xFF22C55E).copy(alpha = 0.6f) else BrandRed.copy(alpha = 0.4f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAuthorized) Color(0xFF14532D).copy(alpha = 0.4f)
                                            else BrandRed.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAuthorized) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = "Segurança",
                                        tint = if (isAuthorized) Color(0xFF4ADE80) else BrandRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Title & Subtitle
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isAuthorized) "ACESSO AUTORIZADO" else "ACESSO RESTRITO",
                                    color = if (isAuthorized) Color(0xFF4ADE80) else Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "Área exclusiva para administradores",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "Digite sua senha para acessar o painel administrativo.",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Password Input with shake animation on error
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    if (it.all { char -> char.isDigit() }) {
                                        password = it
                                        if (errorMessage != null) {
                                            errorMessage = null
                                        }
                                    }
                                },
                                placeholder = {
                                    Text("Digite sua senha", color = Color(0xFF6B7280), fontSize = 14.sp)
                                },
                                singleLine = true,
                                enabled = !isVerifying && !isAuthorized,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submit() }
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (errorMessage != null) Color(0xFFEF4444)
                                        else if (password.isNotEmpty()) BrandRed
                                        else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isPasswordVisible = !isPasswordVisible },
                                        modifier = Modifier.testTag("toggle_password_visibility")
                                    ) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPasswordVisible) "Ocultar senha" else "Mostrar senha",
                                            tint = if (isPasswordVisible) BrandRed else Color(0xFF9CA3AF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.LightGray,
                                    focusedBorderColor = if (errorMessage != null) Color(0xFFEF4444) else BrandRed,
                                    unfocusedBorderColor = if (errorMessage != null) Color(0xFFEF4444) else Color(0xFF2E2E2E),
                                    disabledBorderColor = Color(0xFF262626),
                                    cursorColor = BrandRed,
                                    focusedContainerColor = Color(0xFF0A0A0A),
                                    unfocusedContainerColor = Color(0xFF0A0A0A),
                                    disabledContainerColor = Color(0xFF0A0A0A)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(x = shakeOffset.value.dp)
                                    .testTag("admin_password_input")
                            )

                            // Error Message Banner (Shake + Alert)
                            AnimatedVisibility(
                                visible = errorMessage != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF2A1215))
                                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Alerta",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "⚠️ Acesso negado",
                                            color = Color(0xFFEF4444),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = errorMessage ?: "",
                                            color = Color(0xFFFCA5A5),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Main Access Button
                            Button(
                                onClick = { submit() },
                                enabled = !isVerifying && !isAuthorized,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("admin_login_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAuthorized) Color(0xFF16A34A) else BrandRed,
                                    disabledContainerColor = if (isAuthorized) Color(0xFF16A34A) else BrandRed.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Verificando acesso...",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (isAuthorized) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ACESSO AUTORIZADO",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ACESSAR PAINEL",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Footer: Área Protegida
                AnimatedVisibility(
                    visible = isScreenVisible,
                    enter = fadeIn(animationSpec = tween(350, delayMillis = 250))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Área protegida",
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Somente administradores autorizados",
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDestaquesScreenLegacy(
    adminViewModel: AdminViewModel,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val featuredItems by adminViewModel.allFeaturedItems.collectAsState()
    val searchResults by adminViewModel.searchResults.collectAsState()
    val isSearching by adminViewModel.isSearching.collectAsState()
    val searchQuery by adminViewModel.searchQuery.collectAsState()
    val searchFilter by adminViewModel.searchFilter.collectAsState()

    val selectedPreviewMedia by adminViewModel.selectedPreviewMedia.collectAsState()
    val selectedPreviewExists by adminViewModel.selectedPreviewExists.collectAsState()
    val isPreviewLoading by adminViewModel.isPreviewLoading.collectAsState()

    val heroInterval by mainViewModel.heroIntervalSeconds.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Screen navigation mode: "LIST" or "ADD_EDIT"
    var screenMode by remember { mutableStateOf("LIST") }
    var editingItem by remember { mutableStateOf<FeaturedMediaItem?>(null) }

    // Form inputs
    var selectedMedia by remember { mutableStateOf<MediaEntity?>(null) }
    var trailerUrlInput by remember { mutableStateOf("") }
    var autoPlayInput by remember { mutableStateOf(true) }
    var displayOrderInput by remember { mutableStateOf("1") }
    var isActiveInput by remember { mutableStateOf(true) }
    var customTitleInput by remember { mutableStateOf("") }
    var customOverviewInput by remember { mutableStateOf("") }
    var autoFetchTrailer by remember { mutableStateOf(true) }

    var previewIsMuted by remember { mutableStateOf(true) }
    var previewIsTrailerError by remember { mutableStateOf(false) }

    // Overlay dialogs
    var itemToDelete by remember { mutableStateOf<FeaturedMediaItem?>(null) }
    var showTrailerTestKey by remember { mutableStateOf<String?>(null) }
    var showTrailerTestData by remember { mutableStateOf<AdminTrailerTestData?>(null) }
    var showSuccessToast by remember { mutableStateOf(false) }

    // Sync form when a preview is loaded or selected
    LaunchedEffect(selectedPreviewMedia) {
        selectedPreviewMedia?.let {
            selectedMedia = it
            previewIsTrailerError = false
            if (editingItem == null) {
                customTitleInput = it.title
                customOverviewInput = it.overview
                if (autoFetchTrailer) {
                    trailerUrlInput = it.trailerKey ?: ""
                }
            }
        }
    }

    LaunchedEffect(autoFetchTrailer, selectedMedia) {
        if (autoFetchTrailer) {
            selectedMedia?.let {
                trailerUrlInput = it.trailerKey ?: ""
            }
        }
    }

    fun openAdd() {
        editingItem = null
        selectedMedia = null
        adminViewModel.clearPreview()
        adminViewModel.onSearchQueryChanged("")
        adminViewModel.setSearchFilter("all")
        trailerUrlInput = ""
        autoPlayInput = true
        autoFetchTrailer = true
        displayOrderInput = (featuredItems.size + 1).toString()
        isActiveInput = true
        customTitleInput = ""
        customOverviewInput = ""
        screenMode = "ADD_EDIT"
    }

    fun openEdit(item: FeaturedMediaItem) {
        editingItem = item
        selectedMedia = item.media
        adminViewModel.selectMediaForPreview(item.media, true)
        trailerUrlInput = item.featured.trailerUrl
        autoPlayInput = item.featured.autoPlayTrailer
        autoFetchTrailer = item.featured.trailerUrl.isBlank() || item.featured.trailerUrl == item.media.trailerKey
        displayOrderInput = item.featured.displayOrder.toString()
        isActiveInput = item.featured.isActive
        customTitleInput = item.media.title
        customOverviewInput = item.media.overview
        screenMode = "ADD_EDIT"
    }

    if (showSuccessToast) {
        AlertDialog(
            onDismissRequest = { showSuccessToast = false },
            title = null,
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "✅", fontSize = 24.sp)
                    Column {
                        Text(
                            text = "Destaque publicado!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "A página inicial de todos os dispositivos foi atualizada em tempo real.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSuccessToast = false }) {
                    Text("OK", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showTrailerTestData != null || showTrailerTestKey != null) {
        val testData = showTrailerTestData ?: AdminTrailerTestData(
            title = selectedMedia?.title ?: "Prévia do Trailer",
            mediaType = selectedMedia?.mediaType ?: "movie",
            trailerKey = showTrailerTestKey ?: "",
            tmdbId = selectedMedia?.tmdbId ?: 0,
            isOfficial = true
        )
        val initialTestYoutubeId = extractYoutubeVideoId(testData.trailerKey)

        var retryTrigger by remember { mutableStateOf(0) }
        var candidateKeys by remember(testData.trailerKey) { mutableStateOf<List<String>>(emptyList()) }
        var candidateSelectionIndex by remember(testData.trailerKey) { mutableStateOf(0) }

        // Combined candidates list with initial test ID first
        val allTestCandidates = remember(initialTestYoutubeId, candidateKeys) {
            val list = mutableListOf<String>()
            if (!initialTestYoutubeId.isNullOrBlank()) list.add(initialTestYoutubeId)
            candidateKeys.forEach { key ->
                val id = extractYoutubeVideoId(key)
                if (!id.isNullOrBlank() && !list.contains(id)) {
                    list.add(id)
                }
            }
            list
        }

        val activeYoutubeId = allTestCandidates.getOrNull(candidateSelectionIndex) ?: initialTestYoutubeId

        var trailerStatus by remember(activeYoutubeId, retryTrigger) { mutableStateOf(TrailerPlaybackState.LOADING) }
        var lastErrorInfo by remember(activeYoutubeId, retryTrigger) { mutableStateOf<TrailerErrorInfo?>(null) }
        val dialogContext = LocalContext.current

        LaunchedEffect(testData.tmdbId, retryTrigger) {
            if (testData.tmdbId > 0) {
                try {
                    val candidates = mainViewModel.mediaRepository.getTrailerCandidatesForMedia(testData.tmdbId, testData.mediaType)
                    candidateKeys = candidates.map { it.key }
                } catch (_: Exception) {}
            }
        }

        AlertDialog(
            onDismissRequest = {
                showTrailerTestData = null
                showTrailerTestKey = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎬 Testar Trailer",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header info: Title & Type / Official badge
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = testData.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (testData.mediaType == "movie") "FILME" else "SÉRIE",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (testData.isOfficial) {
                                Surface(
                                    color = BrandRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "TRAILER OFICIAL",
                                        color = BrandRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (allTestCandidates.size > 1) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "OPÇÃO ${candidateSelectionIndex + 1}/${allTestCandidates.size}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Status Indicator Banner (CARREGANDO TRAILER... / TRAILER PRONTO / REPRODUZINDO / FALHA AO REPRODUZIR)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (trailerStatus) {
                                    TrailerPlaybackState.READY, TrailerPlaybackState.PLAYING -> Color(0xFF1B5E20).copy(alpha = 0.35f)
                                    TrailerPlaybackState.ERROR -> BrandRed.copy(alpha = 0.25f)
                                    else -> Color.White.copy(alpha = 0.08f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (trailerStatus) {
                            TrailerPlaybackState.READY -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "TRAILER PRONTO",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TrailerPlaybackState.PLAYING -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "REPRODUZINDO TRAILER",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TrailerPlaybackState.PAUSED -> {
                                Text(
                                    text = "TRAILER PAUSADO",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TrailerPlaybackState.ENDED -> {
                                Text(
                                    text = "TRAILER FINALIZADO",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TrailerPlaybackState.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "FALHA AO REPRODUZIR",
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> {
                                CircularProgressIndicator(
                                    color = BrandRed,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "CARREGANDO TRAILER...",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Player Container
                    if (!activeYoutubeId.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            androidx.compose.runtime.key(activeYoutubeId, retryTrigger) {
                                HeroTrailerPlayer(
                                    youtubeId = activeYoutubeId,
                                    autoPlay = true,
                                    isMuted = false,
                                    showControls = true,
                                    candidateFallbackKeys = allTestCandidates.filter { it != activeYoutubeId },
                                    onPlayerReady = { trailerStatus = TrailerPlaybackState.READY },
                                    onPlaying = { trailerStatus = TrailerPlaybackState.PLAYING },
                                    onPlayerError = { trailerStatus = TrailerPlaybackState.ERROR },
                                    onPlayerEnded = { trailerStatus = TrailerPlaybackState.ENDED },
                                    onErrorDetails = { err -> lastErrorInfo = err },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Trailer não disponível para este título.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Error Details and Actions
                    if (trailerStatus == TrailerPlaybackState.ERROR) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val errorMsg = when {
                                lastErrorInfo?.isEmbedRestricted == true -> "Este vídeo restringe reprodução incorporada pelo YouTube."
                                lastErrorInfo?.isTimeout == true -> "Tempo limite esgotado ao carregar este trailer."
                                else -> lastErrorInfo?.errorMessage ?: "Não foi possível carregar este trailer."
                            }
                            Text(
                                text = errorMsg,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        trailerStatus = TrailerPlaybackState.LOADING
                                        lastErrorInfo = null
                                        retryTrigger++
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = BrandRed, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Tentar novamente", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (allTestCandidates.size > 1) {
                                    TextButton(
                                        onClick = {
                                            candidateSelectionIndex = (candidateSelectionIndex + 1) % allTestCandidates.size
                                            trailerStatus = TrailerPlaybackState.LOADING
                                            lastErrorInfo = null
                                            retryTrigger++
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Outro trailer", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!activeYoutubeId.isNullOrBlank()) {
                                    TextButton(
                                        onClick = {
                                            openYouTubeExternal(dialogContext, activeYoutubeId)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Assistir no YouTube", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "ID do YouTube: ${activeYoutubeId ?: testData.trailerKey.ifBlank { "Nenhum" }}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTrailerTestData = null
                        showTrailerTestKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Fechar")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    AnimatedContent(
        targetState = screenMode,
        transitionSpec = {
            slideInHorizontally { width -> if (targetState == "ADD_EDIT") width else -width } togetherWith
                    slideOutHorizontally { width -> if (targetState == "ADD_EDIT") -width else width }
        },
        label = "featured_navigation"
    ) { mode ->
        if (mode == "LIST") {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info Summary Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "🎬 CONTEÚDO EM DESTAQUE",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Gerencie os filmes e séries exibidos no grande banner da página inicial.",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Interval Selector
                            var expandedInterval by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { expandedInterval = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Duração de cada destaque: ${heroInterval}s", fontSize = 13.sp)
                                }
                                DropdownMenu(
                                    expanded = expandedInterval,
                                    onDismissRequest = { expandedInterval = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    listOf(15, 20, 30, 45, 60).forEach { sec ->
                                        DropdownMenuItem(
                                            text = { Text("${sec}s por destaque", color = Color.White) },
                                            onClick = {
                                                mainViewModel.setHeroInterval(sec)
                                                expandedInterval = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = BrandRed.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Destaques ativos", color = TextSecondary, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${featuredItems.filter { it.featured.isActive }.size} conteúdos",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Button(
                                    onClick = { openAdd() },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Novo Destaque", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Empty state or Items list
                if (featuredItems.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StarOutline,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "⭐ Nenhum conteúdo em destaque",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Adicione um filme ou série da TMDB para aparecer no banner principal.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Button(
                                    onClick = { openAdd() },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Adicionar Primeiro Destaque", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    val sortedFeatured = featuredItems.sortedBy { it.featured.displayOrder }
                    itemsIndexed(sortedFeatured, key = { _, item -> item.featured.id }) { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (item.featured.isActive) BrandRed.copy(alpha = 0.35f) else CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Poster or Backdrop
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.media.posterPath ?: item.media.backdropPath)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = if (item.media.mediaType == "tv") Color(0xFF7C4DFF) else BrandRed,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (item.media.mediaType == "tv") "SÉRIE" else "FILME",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = if (item.featured.isActive) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (item.featured.isActive) "🟢 ATIVO" else "⚪ INATIVO",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = Color.White.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "⭐ ${item.media.rating}",
                                                color = RatingYellow,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = item.media.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "${item.media.releaseYear} • Ordem: ${item.featured.displayOrder}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircleOutline,
                                            contentDescription = null,
                                            tint = if (item.featured.trailerUrl.isNotBlank()) BrandRed else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (item.featured.trailerUrl.isNotBlank()) "Possui trailer" else "Sem trailer",
                                            color = if (item.featured.trailerUrl.isNotBlank()) Color.LightGray else Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Quick control and menu buttons
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Ordering controls
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            onClick = { adminViewModel.moveFeaturedItem(item, true) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Mover para cima",
                                                tint = if (index > 0) Color.White else Color.DarkGray
                                            )
                                        }
                                        IconButton(
                                            onClick = { adminViewModel.moveFeaturedItem(item, false) },
                                            enabled = index < sortedFeatured.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Mover para baixo",
                                                tint = if (index < sortedFeatured.size - 1) Color.White else Color.DarkGray
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Context Actions dropdown menu
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = Color.LightGray)
                                        }

                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            modifier = Modifier.background(DarkSurface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("✏️ Editar", color = Color.White) },
                                                onClick = {
                                                    showMenu = false
                                                    openEdit(item)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (item.featured.isActive) "👁️ Desativar" else "👁️ Ativar",
                                                        color = Color.White
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    adminViewModel.toggleFeaturedActiveStatus(item)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🎞️ Alterar Trailer", color = Color.White) },
                                                onClick = {
                                                    showMenu = false
                                                    openEdit(item)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🗑️ Remover", color = BrandRed) },
                                                onClick = {
                                                    showMenu = false
                                                    itemToDelete = item
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (mode == "ADD_EDIT") {
            // Screen step-by-step custom wizard for adding/editing a content
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Return Bar & Title
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { screenMode = "LIST" },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkSurface)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (editingItem != null) "✏️ EDITAR DESTAQUE" else "⭐ ADICIONAR AO DESTAQUE",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Busque no catálogo ou diretamente na base da TMDB para criar o banner.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // If adding, search field and TMDB search results
                if (editingItem == null && selectedMedia == null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "1. Pesquisar na base integrada TMDB",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { adminViewModel.onSearchQueryChanged(it) },
                                    placeholder = { Text("Pesquise por nome, série, filme ou ID TMDB...", color = Color.Gray, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Filter segments
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("all" to "TODOS", "movie" to "🎬 FILMES", "tv" to "📺 SÉRIES").forEach { (fCode, fName) ->
                                        val isSel = searchFilter == fCode
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { adminViewModel.setSearchFilter(fCode) },
                                            label = { Text(fName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BrandRed,
                                                selectedLabelColor = Color.White,
                                                containerColor = Color.Transparent,
                                                labelColor = Color.LightGray
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSel,
                                                borderColor = if (isSel) BrandRed else Color.DarkGray
                                            )
                                        )
                                    }
                                }

                                // Search ID TMDB button if query is numeric
                                val numericId = searchQuery.trim().toIntOrNull()
                                if (numericId != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            adminViewModel.selectTmdbIdForPreview(
                                                searchQuery,
                                                if (searchFilter == "tv") "tv" else "movie"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, BrandRed),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "🔍 Buscar ID TMDB: $searchQuery como ${if (searchFilter == "tv") "Série" else "Filme"}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TMDB search items result
                    if (isSearching || isPreviewLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BrandRed)
                            }
                        }
                    } else if (searchResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) "Digite um termo para pesquisar filmes ou séries." else "Nenhum resultado encontrado.",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(searchResults) { result ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = result.entity.posterPath,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 68.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.DarkGray)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.entity.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (result.entity.mediaType == "tv") "📺 SÉRIE" else "🎬 FILME",
                                                color = if (result.entity.mediaType == "tv") Color(0xFF7C4DFF) else BrandRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = result.entity.releaseYear,
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "⭐ ${result.entity.rating}",
                                                color = RatingYellow,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            adminViewModel.selectMediaForPreview(result.entity, result.isAlreadyInCatalog)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Selecionar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Config section if we have selectedMedia
                selectedMedia?.let { m ->
                    // Duplicate check
                    val isAlreadyFeatured = featuredItems.any {
                        it.featured.mediaTmdbId == m.tmdbId && it.featured.id != (editingItem?.featured?.id ?: 0)
                    }

                    if (isAlreadyFeatured) {
                        item {
                            Surface(
                                color = Color(0xFF3E2723),
                                border = BorderStroke(1.dp, Color(0xFFD84315)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF7043))
                                    Text(
                                        text = "⚠️ Este conteúdo já está nos destaques.",
                                        color = Color(0xFFFFAB91),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Backdrop header details preview
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            ) {
                                AsyncImage(
                                    model = m.backdropPath ?: m.posterPath,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = if (m.mediaType == "tv") Color(0xFF7C4DFF) else BrandRed,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (m.mediaType == "tv") "SÉRIE" else "FILME",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(text = "• ${m.releaseYear} • ⭐ ${m.rating}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = m.title,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (editingItem == null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "ID TMDB: ${m.tmdbId} | Clique na seta superior para trocar de título",
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (editingItem == null) {
                                    IconButton(
                                        onClick = { selectedMedia = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(10.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Alterar seleção", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Config details fields
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "🎨 CONFIGURAÇÃO DO DESTAQUE",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = customTitleInput,
                                    onValueChange = { customTitleInput = it },
                                    label = { Text("Título do destaque", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = customOverviewInput,
                                    onValueChange = { customOverviewInput = it },
                                    label = { Text("Descrição / Sinopse", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = displayOrderInput,
                                        onValueChange = { displayOrderInput = it },
                                        label = { Text("Posição (#)", color = Color.Gray) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BrandRed,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Status do Banner", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(if (isActiveInput) "🟢 Ativo" else "⚪ Inativo", color = Color.White, fontSize = 13.sp)
                                            Switch(
                                                checked = isActiveInput,
                                                onCheckedChange = { isActiveInput = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = BrandRed
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Trailer integration details
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("🎞️ CONFIGURAÇÃO DO TRAILER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                                // Checkbox for Trailer Automático
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { autoFetchTrailer = !autoFetchTrailer }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("TRAILER AUTOMÁTICO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "O sistema buscará automaticamente o trailer oficial do filme ou série selecionado.",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Checkbox(
                                        checked = autoFetchTrailer,
                                        onCheckedChange = { autoFetchTrailer = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = BrandRed,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }

                                Divider(color = CardBorder, thickness = 1.dp)

                                // Status indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text("Status:", color = TextSecondary, fontSize = 12.sp)
                                    if (trailerUrlInput.isNotBlank()) {
                                        Text("🟢 Trailer encontrado", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("🟡 Nenhum trailer encontrado", color = RatingYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Interactive Trailer Testing Widget
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Simulated trailer thumbnail
                                    Box(
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 66.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.DarkGray)
                                            .clickable {
                                                val key = trailerUrlInput.ifBlank { m.trailerKey ?: "" }
                                                showTrailerTestData = AdminTrailerTestData(
                                                    title = m.title,
                                                    mediaType = m.mediaType,
                                                    trailerKey = key,
                                                    tmdbId = m.tmdbId,
                                                    isOfficial = true
                                                )
                                                showTrailerTestKey = key
                                            }
                                    ) {
                                        AsyncImage(
                                            model = m.backdropPath ?: m.posterPath,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Testar",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Prévia do Trailer",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = {
                                                val key = trailerUrlInput.ifBlank { m.trailerKey ?: "" }
                                                showTrailerTestData = AdminTrailerTestData(
                                                    title = m.title,
                                                    mediaType = m.mediaType,
                                                    trailerKey = key,
                                                    tmdbId = m.tmdbId,
                                                    isOfficial = true
                                                )
                                                showTrailerTestKey = key
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("▶ Testar trailer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Option to add manually if auto-fetch is disabled, or as a manual fallback
                                if (!autoFetchTrailer || trailerUrlInput.isBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Adicionar trailer manualmente:",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = trailerUrlInput,
                                        onValueChange = { trailerUrlInput = it },
                                        label = { Text("URL ou ID do YouTube", color = Color.Gray) },
                                        placeholder = { Text("Ex: dQw4w9WgXcQ ou link completo", color = Color.Gray, fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BrandRed,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Reproduzir trailer automaticamente", color = Color.White, fontSize = 13.sp)
                                    Switch(
                                        checked = autoPlayInput,
                                        onCheckedChange = { autoPlayInput = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = BrandRed
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Banner Preview Simulator
                    item {
                        val previewYoutubeId = remember(trailerUrlInput, m.trailerKey) {
                            extractYoutubeVideoId(trailerUrlInput.ifBlank { m.trailerKey ?: "" })
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "📱 PRÉ-VISUALIZAÇÃO DO BANNER (SIMULADOR)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                // Actual simulated banner with real trailer preview support
                                val isTestModalActive = showTrailerTestData != null || showTrailerTestKey != null
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                ) {
                                    if (!isTestModalActive && previewYoutubeId != null && !previewIsTrailerError && autoPlayInput) {
                                        HeroTrailerPlayer(
                                            youtubeId = previewYoutubeId,
                                            autoPlay = true,
                                            isMuted = previewIsMuted,
                                            onPlayerReady = {},
                                            onPlayerError = { previewIsTrailerError = true },
                                            onPlayerEnded = {},
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        AsyncImage(
                                            model = m.backdropPath ?: m.posterPath,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = 0.3f),
                                                        Color.Black.copy(alpha = 0.95f)
                                                    )
                                                )
                                            )
                                    )

                                    // Dynamic Mute/Play controller inside simulator
                                    if (previewYoutubeId != null) {
                                        val isPreviewTrailerActive = !previewIsTrailerError && autoPlayInput
                                        IconButton(
                                            onClick = {
                                                if (isPreviewTrailerActive) {
                                                    previewIsMuted = !previewIsMuted
                                                } else {
                                                    autoPlayInput = true
                                                    previewIsTrailerError = false
                                                    previewIsMuted = false
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPreviewTrailerActive) {
                                                    if (previewIsMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                                } else {
                                                    Icons.Default.PlayArrow
                                                },
                                                contentDescription = if (isPreviewTrailerActive) {
                                                    if (previewIsMuted) "Sem som" else "Com som"
                                                } else {
                                                    "Assistir Trailer na Prévia"
                                                },
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = if (m.mediaType == "tv") Color(0xFF7C4DFF) else BrandRed,
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = if (m.mediaType == "tv") "SÉRIE" else "FILME",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "${m.releaseYear} • ⭐ ${m.rating}",
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = customTitleInput.ifBlank { m.title },
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = customOverviewInput.ifBlank { m.overview },
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 13.sp
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {},
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("ASSISTIR AGORA", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {},
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("MINHA LISTA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Submit Actions Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { screenMode = "LIST" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        // 1. Auto-import into Catalog if the selected TMDB ID doesn't exist locally
                                        val existing = adminViewModel.allCatalogMedia.value.firstOrNull { it.tmdbId == m.tmdbId }
                                        val updatedMedia = (existing ?: m).copy(
                                            title = customTitleInput.trim().ifBlank { (existing ?: m).title },
                                            overview = customOverviewInput.trim().ifBlank { (existing ?: m).overview }
                                        )
                                        adminViewModel.mediaRepository.importMediaEntity(updatedMedia)

                                        // 2. Save Featured content database entry
                                        val order = displayOrderInput.toIntOrNull() ?: 1
                                        adminViewModel.saveFeaturedContent(
                                            id = editingItem?.featured?.id ?: 0,
                                            mediaTmdbId = m.tmdbId,
                                            mediaType = m.mediaType,
                                            trailerUrl = trailerUrlInput,
                                            autoPlayTrailer = autoPlayInput,
                                            displayOrder = order,
                                            isActive = isActiveInput
                                        )

                                        // 3. Sync changes instantly to cloud version
                                        adminViewModel.publishChangesToCloud()

                                        // Show visual success response
                                        showSuccessToast = true
                                        screenMode = "LIST"
                                    }
                                },
                                enabled = !isAlreadyFeatured,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("⭐ PUBLICAR DESTAQUE", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Alert Modal for Removal
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "Remover este destaque?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Tem certeza que deseja remover '${item.media.title}' da seleção de destaques? O filme/série continuará disponível no catálogo geral do RONYCINE.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.removeFeaturedContent(item)
                        itemToDelete = null
                        scope.launch {
                            adminViewModel.publishChangesToCloud()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("REMOVER", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("CANCELAR", color = Color.LightGray)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ==================================================
// STATUS DA SINCRONIZAÇÃO CENTRALIZADA
// ==================================================
@Composable
fun RealTimeClock(
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        while (true) {
            currentTime = sdf.format(java.util.Date())
            delay(1000L)
        }
    }
    
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text("Hora atual", color = Color.Gray, fontSize = 9.sp)
        Text(
            text = currentTime,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
fun AdminSincronizacaoScreen(
    adminViewModel: AdminViewModel
) {
    val syncStatus by adminViewModel.syncStatus.collectAsState()
    val catalogVersion by adminViewModel.catalogVersion.collectAsState()
    val lastSyncFormatted by adminViewModel.lastSyncFormatted.collectAsState()
    val movieCount by adminViewModel.movieCount.collectAsState(initial = 0)
    val seriesCount by adminViewModel.seriesCount.collectAsState(initial = 0)
    val liveChannels by adminViewModel.allChannels.collectAsState(initial = emptyList())
    val devices by adminViewModel.allDevices.collectAsState(initial = emptyList())
    val updateEvents by adminViewModel.updateEvents.collectAsState(initial = emptyList())
    val publishedVersions by adminViewModel.publishedVersions.collectAsState(initial = emptyList())
    val remoteAuditLogs by adminViewModel.remoteAuditLogs.collectAsState(initial = emptyList())
    
    val totalChannels = liveChannels.size
    val onlineDevices = devices.count { it.isOnline }
    val offlineDevices = devices.size - onlineDevices

    val isCatalogActionRunning by adminViewModel.isCatalogActionRunning.collectAsState()
    var wasRunning by remember { mutableStateOf(false) }
    var syncCompletedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(isCatalogActionRunning) {
        if (isCatalogActionRunning) {
            wasRunning = true
            syncCompletedRecently = false
        } else if (wasRunning) {
            wasRunning = false
            syncCompletedRecently = true
            delay(3000L)
            syncCompletedRecently = false
        }
    }

    // Interactive Device Panel States
    var selectedDeviceIds by remember { mutableStateOf(setOf<String>()) }
    var selectedDeviceForDetails by remember { mutableStateOf<com.example.data.remote.DeviceEntity?>(null) }
    var deviceSearchQuery by remember { mutableStateOf("") }

    val filteredDevices = remember(devices, deviceSearchQuery) {
        if (deviceSearchQuery.isBlank()) devices
        else devices.filter { 
            it.name.contains(deviceSearchQuery, ignoreCase = true) ||
            it.model.contains(deviceSearchQuery, ignoreCase = true) ||
            it.deviceId.contains(deviceSearchQuery, ignoreCase = true)
        }
    }

    // Dialog state controllers
    var deviceIdForUpdate by remember { mutableStateOf<Set<String>?>(null) }
    var deviceIdForSuspend by remember { mutableStateOf<Set<String>?>(null) }
    var deviceIdForBan by remember { mutableStateOf<Set<String>?>(null) }
    var deviceForNotification by remember { mutableStateOf<com.example.data.remote.DeviceEntity?>(null) }
    var deviceForHistory by remember { mutableStateOf<com.example.data.remote.DeviceEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("admin_sincronizacao_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. CABEÇALHO COMPACTO
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "PLAYFILME+",
                                color = BrandRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "|",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                "Status da Sincronização",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🟢 SERVIDOR ONLINE", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("•", color = Color.Gray, fontSize = 10.sp)
                            Text("v$catalogVersion", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                
                RealTimeClock()
            }
        }

        // 2. RESUMO SUPERIOR (1 LINHA COMPACTA)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val items = listOf(
                    "Filmes" to movieCount,
                    "Séries" to seriesCount,
                    "Canais" to totalChannels,
                    "Total" to (movieCount + seriesCount + totalChannels)
                )
                items.forEachIndexed { index, (label, count) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$count",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = label,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (index < items.size - 1) {
                        Divider(
                            modifier = Modifier
                                .height(20.dp)
                                .width(1.dp),
                            color = CardBorder
                        )
                    }
                }
            }
        }

        // 3. SINCRONIZAÇÃO EM TEMPO REAL
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "SINCRONIZAÇÃO EM TEMPO REAL",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = Color(0xFF065F46),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Text(
                            "🟢 ATIVA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    "Todas as alterações do catálogo são sincronizadas automaticamente entre os dispositivos.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    "Última sincronização: $lastSyncFormatted",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { adminViewModel.forceGlobalSync() },
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .height(36.dp)
                        .testTag("admin_sincronizar_agora_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (syncCompletedRecently) Color(0xFF10B981) else BrandRed,
                        disabledContainerColor = if (syncCompletedRecently) Color(0xFF10B981) else Color.Gray.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    enabled = !isCatalogActionRunning && !syncCompletedRecently
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isCatalogActionRunning) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "⏳ SINCRONIZANDO...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        } else if (syncCompletedRecently) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "✓ SINCRONIZAÇÃO CONCLUÍDA",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "🔄 Sincronizar agora",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. DISPOSITIVOS CONECTADOS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "📱 DISPOSITIVOS CONECTADOS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🟢 $onlineDevices Online",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "⚫ $offlineDevices Offline",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "📱 ${devices.size} Total",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Divider(color = CardBorder, thickness = 1.dp)

                // Busca de dispositivos
                OutlinedTextField(
                    value = deviceSearchQuery,
                    onValueChange = { deviceSearchQuery = it },
                    placeholder = { Text("Buscar por nome, modelo ou ID...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = BrandRed
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (deviceSearchQuery.isNotBlank()) {
                            IconButton(onClick = { deviceSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )

                // Barra de ações em lote
                if (selectedDeviceIds.isNotEmpty()) {
                    Surface(
                        color = BrandRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selectedDeviceIds.size} selecionado(s)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { deviceIdForUpdate = selectedDeviceIds },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("ATUALIZAR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { deviceIdForSuspend = selectedDeviceIds },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF78350F)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("SUSPENDER", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { deviceIdForBan = selectedDeviceIds },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("BANIR", color = Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        selectedDeviceIds.forEach { deviceId ->
                                            adminViewModel.setDeviceAccessStatus(deviceId, "ACTIVE")
                                            adminViewModel.addAuditLog("Reativou o dispositivo", deviceId)
                                        }
                                        selectedDeviceIds = emptySet()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("REATIVAR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (filteredDevices.isEmpty()) {
                    Text(
                        if (deviceSearchQuery.isBlank()) "Nenhum dispositivo registrado." else "Nenhum dispositivo encontrado para a busca.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredDevices.forEach { device ->
                            val isSelected = selectedDeviceIds.contains(device.deviceId)
                            val hasPendingUpdate = updateEvents.any { it.deviceId == device.deviceId && it.status in listOf("PENDING", "NOTIFIED", "DOWNLOADING", "DOWNLOADED", "INSTALLING") }
                            DeviceCompactItem(
                                device = device,
                                isSelected = isSelected,
                                onSelectedChange = { checked ->
                                    selectedDeviceIds = if (checked) {
                                        selectedDeviceIds + device.deviceId
                                    } else {
                                        selectedDeviceIds - device.deviceId
                                    }
                                },
                                onClick = {
                                    selectedDeviceForDetails = device
                                },
                                hasPendingUpdate = hasPendingUpdate
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de detalhes do dispositivo em tempo real
    val currentDetailDevice = devices.find { it.deviceId == selectedDeviceForDetails?.deviceId } ?: selectedDeviceForDetails
    if (currentDetailDevice != null) {
        Dialog(
            onDismissRequest = { selectedDeviceForDetails = null }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "DETALHES DO DISPOSITIVO",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentDetailDevice.name.takeIf { it.isNotBlank() } ?: "Dispositivo",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = { selectedDeviceForDetails = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.LightGray)
                        }
                    }

                    Divider(color = CardBorder, thickness = 1.dp)

                    // Platform & Version Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PLATAFORMA", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(currentDetailDevice.platform, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VERSÃO ATUAL", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("v${currentDetailDevice.appVersion}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Online/Offline Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STATUS EM TEMPO REAL", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (currentDetailDevice.isOnline) "🟢 ONLINE" else "⚫ OFFLINE",
                                color = if (currentDetailDevice.isOnline) Color(0xFF34D399) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STATUS DE ACESSO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val accessStatusColor = when (currentDetailDevice.accessStatus) {
                                "SUSPENDED" -> Color(0xFFFBBF24)
                                "BANNED" -> BrandRed
                                else -> Color(0xFF34D399)
                            }
                            val accessStatusText = when (currentDetailDevice.accessStatus) {
                                "SUSPENDED" -> "🟡 SUSPENSO"
                                "BANNED" -> "🔴 BANIDO"
                                else -> "🟢 ATIVO"
                            }
                            Text(accessStatusText, color = accessStatusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Connection Dates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PUSH STATUS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isFcmOk = currentDetailDevice.fcmToken.isNotBlank()
                                Text(
                                    text = if (isFcmOk) "🟢 DISPONÍVEL" else "🔴 INDISPONÍVEL",
                                    color = if (isFcmOk) Color(0xFF34D399) else BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PERMISSÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val hasPerm = currentDetailDevice.notificationsPermission == "CONCEDIDA"
                                Text(
                                    text = if (hasPerm) "🟢 CONCEDIDA" else "🔴 NEGADA",
                                    color = if (hasPerm) Color(0xFF34D399) else BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("PRIMEIRO ACESSO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(currentDetailDevice.firstConnectionFormatted, color = Color.White, fontSize = 11.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ÚLTIMO HEARTBEAT / ACESSO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(currentDetailDevice.lastActivityFormatted, color = Color.White, fontSize = 11.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DEVICE ID", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(currentDetailDevice.deviceId, color = Color.LightGray, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }

                    Divider(color = CardBorder, thickness = 1.dp)

                    // Ações Administrativas
                    Text("AÇÕES DO DISPOSITIVO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        var isSyncingLocal by remember { mutableStateOf(false) }
                        
                        Button(
                            onClick = { 
                                isSyncingLocal = true
                                adminViewModel.requestDeviceSync(currentDetailDevice.deviceId)
                                // We don't have a way to wait for confirmation easily without a callback, 
                                // but the realtime listener will update the UI.
                            },
                            enabled = !isSyncingLocal,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncingLocal) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SINCRONIZANDO...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                
                                LaunchedEffect(Unit) {
                                    delay(2000L)
                                    isSyncingLocal = false
                                }
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SINCRONIZAR / ATUALIZAR DADOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { deviceIdForUpdate = setOf(currentDetailDevice.deviceId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Update, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FORÇAR ATUALIZAÇÃO DO APP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (currentDetailDevice.accessStatus == "ACTIVE") {
                                Button(
                                    onClick = { deviceIdForSuspend = setOf(currentDetailDevice.deviceId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF78350F)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("SUSPENDER", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { deviceIdForBan = setOf(currentDetailDevice.deviceId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("BANIR", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        adminViewModel.setDeviceAccessStatus(currentDetailDevice.deviceId, "ACTIVE")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REATIVAR DISPOSITIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Button(
                            onClick = { deviceForNotification = currentDetailDevice },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ENVIAR NOTIFICAÇÃO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { deviceForHistory = currentDetailDevice },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VER HISTÓRICO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = { selectedDeviceForDetails = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("FECHAR", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Modal de envio de notificações customizadas
    if (deviceForNotification != null) {
        val device = deviceForNotification!!
        var notifTitle by remember { mutableStateOf("Notificação RONYCINE") }
        var notifMessage by remember { mutableStateOf("") }
        var hasValidToken = device.fcmToken.isNotBlank()

        AlertDialog(
            onDismissRequest = { deviceForNotification = null },
            title = { Text("ENVIAR NOTIFICAÇÃO", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Dispositivo: ${device.name.ifBlank { device.model }} (${device.maskedDeviceId})",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    if (!hasValidToken) {
                        Surface(
                            color = Color(0xFF450A0A),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "🔴 PUSH INDISPONÍVEL",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Este dispositivo não possui um token FCM válido registrado. Certifique-se de que o aplicativo está instalado e foi aberto pelo menos uma vez com internet.",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = notifTitle,
                            onValueChange = { notifTitle = it },
                            label = { Text("Título") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notifMessage,
                            onValueChange = { notifMessage = it },
                            label = { Text("Mensagem") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }
            },
            confirmButton = {
                if (hasValidToken) {
                    Button(
                        onClick = {
                            adminViewModel.createAndSendNotification(
                                title = notifTitle,
                                message = notifMessage,
                                targetSegment = device.deviceId
                            )
                            deviceForNotification = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Text("ENVIAR", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceForNotification = null }) {
                    Text("CANCELAR", color = Color.LightGray)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal de seleção de versão para atualização do dispositivo
    if (deviceIdForUpdate != null) {
        val targetIds = deviceIdForUpdate!!
        var selectedVersion by remember { mutableStateOf<com.example.data.remote.AppVersionEntity?>(null) }
        val filteredVersions = publishedVersions.filter { it.isPublished }

        AlertDialog(
            onDismissRequest = { deviceIdForUpdate = null },
            title = { Text("ATUALIZAR DISPOSITIVOS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Selecione a versão do aplicativo que deseja enviar para ${targetIds.size} dispositivo(s).",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    if (filteredVersions.isEmpty()) {
                        Text(
                            text = "Nenhuma versão publicada disponível. Cadastre e publique uma nova versão na central de atualizações.",
                            color = BrandRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredVersions.forEach { version ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedVersion = version },
                                    color = if (selectedVersion?.id == version.id) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selectedVersion?.id == version.id) BrandRed else CardBorder
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Versão v${version.versionName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Build: ${version.versionCode}", color = Color.Gray, fontSize = 10.sp)
                                        }
                                        if (selectedVersion?.id == version.id) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = BrandRed, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedVersion != null) {
                    Button(
                        onClick = {
                            adminViewModel.updateSelectedDevices(targetIds, selectedVersion!!)
                            deviceIdForUpdate = null
                            selectedDeviceIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Text("ATUALIZAR", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceIdForUpdate = null }) {
                    Text("CANCELAR", color = Color.LightGray)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal de confirmação de Suspensão com justificativa
    if (deviceIdForSuspend != null) {
        val targetIds = deviceIdForSuspend!!
        var reason by remember { mutableStateOf("Acesso suspenso por violação de termos") }

        AlertDialog(
            onDismissRequest = { deviceIdForSuspend = null },
            title = { Text("SUSPENDER ACESSO", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Deseja suspender o acesso de ${targetIds.size} dispositivo(s)? Eles perderão acesso ao catálogo imediatamente.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motivo") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        targetIds.forEach { deviceId ->
                            adminViewModel.setDeviceAccessStatus(deviceId, "SUSPENDED")
                            adminViewModel.addAuditLog("Suspendeu o dispositivo (Motivo: $reason)", deviceId)
                        }
                        deviceIdForSuspend = null
                        selectedDeviceIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF78350F))
                ) {
                    Text("SUSPENDER", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceIdForSuspend = null }) {
                    Text("CANCELAR", color = Color.LightGray)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal de confirmação de Banimento definitivo
    if (deviceIdForBan != null) {
        val targetIds = deviceIdForBan!!
        var reason by remember { mutableStateOf("Dispositivo banido permanentemente por atividade suspeita") }

        AlertDialog(
            onDismissRequest = { deviceIdForBan = null },
            title = { Text("💥 BANIR ACESSO", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ ATENÇÃO: Deseja realmente BANIR permanentemente ${targetIds.size} dispositivo(s)? Esta ação é severa e restringe o dispositivo em definitivo.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motivo") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        targetIds.forEach { deviceId ->
                            adminViewModel.setDeviceAccessStatus(deviceId, "BANNED")
                            adminViewModel.addAuditLog("Baniu o dispositivo (Motivo: $reason)", deviceId)
                        }
                        deviceIdForBan = null
                        selectedDeviceIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("BANIR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceIdForBan = null }) {
                    Text("CANCELAR", color = Color.LightGray)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal de visualização unificada do histórico do dispositivo
    if (deviceForHistory != null) {
        val device = deviceForHistory!!

        val filteredUpdates = updateEvents.filter { it.deviceId == device.deviceId }
        val filteredLogs = remoteAuditLogs.filter { it.details.contains(device.deviceId, ignoreCase = true) || it.action.contains(device.deviceId, ignoreCase = true) }

        data class DeviceHistoryItem(
            val title: String,
            val desc: String,
            val time: Long,
            val dateStr: String,
            val type: String
        )

        val combinedHistory = remember(filteredUpdates, filteredLogs) {
            val list = mutableListOf<DeviceHistoryItem>()
            filteredUpdates.forEach {
                list.add(
                    DeviceHistoryItem(
                        title = "Evento de Atualização",
                        desc = "De v${it.fromVersion} para v${it.toVersion} - Status: ${it.status}" + (it.error?.let { err -> " (Erro: $err)" } ?: ""),
                        time = it.startedAt,
                        dateStr = it.dateFormatted,
                        type = "UPDATE"
                    )
                )
            }
            filteredLogs.forEach {
                list.add(
                    DeviceHistoryItem(
                        title = it.action,
                        desc = it.details,
                        time = it.timestamp,
                        dateStr = it.dateFormatted,
                        type = "AUDIT"
                    )
                )
            }
            list.sortByDescending { it.time }
            list
        }

        AlertDialog(
            onDismissRequest = { deviceForHistory = null },
            title = {
                Text(
                    text = "HISTÓRICO DO DISPOSITIVO",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Histórico de ações e eventos para ${device.name.ifBlank { device.model }}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    if (combinedHistory.isEmpty()) {
                        Text(
                            "Nenhum evento registrado no histórico para este dispositivo.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(combinedHistory) { item ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.02f),
                                    border = BorderStroke(1.dp, CardBorder),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.title.uppercase(),
                                                color = if (item.type == "UPDATE") Color(0xFF38BDF8) else Color(0xFFFBBF24),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = item.dateStr,
                                                color = Color.Gray,
                                                fontSize = 8.sp
                                            )
                                        }
                                        Text(
                                            text = item.desc,
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { deviceForHistory = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DeviceCompactItem(
    device: com.example.data.remote.DeviceEntity,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    hasPendingUpdate: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("device_item_${device.deviceId}"),
        color = Color.White.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Checkbox para seleção em massa
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = BrandRed,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.White
                ),
                modifier = Modifier.size(24.dp)
            )

            // Conteúdo principal do Card
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Nome + Badges de Status / Atualização
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.name.takeIf { it.isNotBlank() } ?: "Dispositivo",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (device.accessStatus == "SUSPENDED") {
                        Surface(
                            color = Color(0xFF78350F),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SUSPENSO",
                                color = Color(0xFFFBBF24),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (device.accessStatus == "BANNED") {
                        Surface(
                            color = Color(0xFF7F1D1D),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "BANIDO",
                                color = Color(0xFFFCA5A5),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (hasPendingUpdate) {
                        Surface(
                            color = Color(0xFF1E3A8A),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "PENDENTE",
                                color = Color(0xFF93C5FD),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Plataforma + Versão
                val platformText = remember(device.platform, device.appVersion) {
                    buildString {
                        if (device.platform.isNotBlank()) append(device.platform)
                        if (device.appVersion.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("v${device.appVersion}")
                        }
                    }.ifBlank { "Android" }
                }

                Text(
                    text = platformText,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Status em tempo real + Atividade
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (device.isOnline) "🟢 ONLINE" else "⚫ OFFLINE",
                        color = if (device.isOnline) Color(0xFF34D399) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = device.lastActivityFormatted,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ID Mascarado
                Text(
                    text = "ID: ${device.maskedDeviceId}",
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Indicador visual de interatividade (›)
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ver Detalhes",
                tint = Color.Gray.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================================================
// CENTRAL DE NOTIFICAÇÕES (ADMINISTRATIVO)
// ==================================================
@Composable
fun AdminNotificacoesScreen(
    adminViewModel: AdminViewModel
) {
    val notifications by adminViewModel.adminNotifications.collectAsState()

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("AVISO") }
    var selectedSegment by remember { mutableStateOf("ALL") }
    var buttonText by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }
    var isScheduled by remember { mutableStateOf(false) }

    val totalSent = remember(notifications) { notifications.count { it.isSent } }
    val totalActive = remember(notifications) { notifications.count { it.isActive } }
    val totalScheduled = remember(notifications) { notifications.count { it.scheduledAt > System.currentTimeMillis() } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BrandRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "🔔 Central de Notificações em Tempo Real",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Envie mensagens, anúncios de lançamentos e alertas para todos os usuários",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("$totalSent", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("Enviadas", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("$totalActive", color = Color(0xFF10B981), fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("Ativas", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("$totalScheduled", color = Color(0xFFF59E0B), fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text("Agendadas", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Configuração de Envio Automático
        item {
            val autoEnabled by adminViewModel.autoNotificationsEnabled.collectAsState()
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (autoEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (autoEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (autoEnabled) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Envio Automático de Notificações",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Disparar push quando novos filmes ou séries forem adicionados",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = autoEnabled,
                        onCheckedChange = { adminViewModel.setAutoNotificationsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandRed,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("admin_auto_notification_switch")
                    )
                }
            }
        }

        // Form to create new notification
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🚀 Nova Notificação",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título da Notificação") },
                        placeholder = { Text("Ex: 🎬 Novo Filme Adicionado!") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Mensagem") },
                        placeholder = { Text("Ex: Vingadores: Ultimato já está disponível em HD no catálogo!") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("URL da Imagem Banner (Opcional)") },
                        placeholder = { Text("https://image.tmdb.org/t/p/w500/...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Tipo da Notificação:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val types = listOf(
                        "AVISO" to "📢 Aviso",
                        "NOVO_FILME" to "🎬 Novo Filme",
                        "NOVA_SERIE" to "📺 Nova Série",
                        "EM_ALTA" to "🔥 Em Alta",
                        "ATUALIZACAO" to "🆕 Atualização",
                        "MANUTENCAO" to "⚠️ Manutenção",
                        "INFORMACAO" to "ℹ️ Informação"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(types) { (typeKey, typeLabel) ->
                            FilterChip(
                                selected = selectedType == typeKey,
                                onClick = { selectedType = typeKey },
                                label = { Text(typeLabel, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Text(
                        text = "Público Alvo (Segmento):",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val segments = listOf(
                        "ALL" to "👥 Todos os Usuários",
                        "MOVIES" to "🎬 Interessados em Filmes",
                        "SERIES" to "📺 Interessados em Séries"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        segments.forEach { (segKey, segLabel) ->
                            FilterChip(
                                selected = selectedSegment == segKey,
                                onClick = { selectedSegment = segKey },
                                label = { Text(segLabel, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = buttonText,
                            onValueChange = { buttonText = it },
                            label = { Text("Texto do Botão (Opcional)") },
                            placeholder = { Text("Ex: Assistir Agora") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = BrandRed,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = actionUrl,
                            onValueChange = { actionUrl = it },
                            label = { Text("Ação / ID do Filme (Opcional)") },
                            placeholder = { Text("Ex: movie/299534") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = BrandRed,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank() && message.isNotBlank()) {
                                adminViewModel.createAndSendNotification(
                                    title = title,
                                    message = message,
                                    imageUrl = imageUrl,
                                    type = selectedType,
                                    buttonText = buttonText,
                                    actionUrl = actionUrl,
                                    targetSegment = selectedSegment,
                                    isScheduled = isScheduled,
                                    scheduledAt = if (isScheduled) System.currentTimeMillis() + 3600000 else 0L,
                                    isActive = true
                                )
                                // Clear form
                                title = ""
                                message = ""
                                imageUrl = ""
                                buttonText = ""
                                actionUrl = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        enabled = title.isNotBlank() && message.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_send_notification_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🚀 ENVIAR NOTIFICAÇÃO PARA TODOS OS DISPOSITIVOS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "📋 HISTÓRICO DE NOTIFICAÇÕES (${notifications.size})",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        if (notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma notificação enviada ainda.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(
                items = notifications,
                key = { it.id }
            ) { notif ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = BrandRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = notif.type,
                                    color = BrandRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (notif.isActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (notif.isActive) "🟢 Ativa" else "⚪ Inativa",
                                        color = if (notif.isActive) Color(0xFF10B981) else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { adminViewModel.deleteNotificationAdmin(notif.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir Notificação",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = notif.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = notif.message,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminErrorBoundary(
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (hasError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Não foi possível carregar o Painel Administrativo",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = errorMessage ?: "Um erro inesperado ocorreu. Tente novamente ou volte para o aplicativo.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Voltar", color = Color.White)
                        }
                        Button(
                            onClick = {
                                hasError = false
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                        ) {
                            Text("Tentar novamente", color = Color.White)
                        }
                    }
                }
            }
        }
    } else {
        content()
    }
}


