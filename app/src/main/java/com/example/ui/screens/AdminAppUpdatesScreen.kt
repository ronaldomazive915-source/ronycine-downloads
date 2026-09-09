package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.remote.AppVersionEntity
import com.example.data.remote.DeviceEntity
import com.example.data.remote.UpdateControlEntity
import com.example.data.remote.UpdateEventEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import com.example.util.ApkMetadata
import com.example.util.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAppUpdatesScreen(adminViewModel: AdminViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val publishedVersions by adminViewModel.publishedVersions.collectAsState()
    val allDevices by adminViewModel.allDevices.collectAsState()
    val updateEvents by adminViewModel.updateEvents.collectAsState()
    val updateControl by adminViewModel.updateControl.collectAsState()
    val remoteAppConfig by adminViewModel.remoteAppConfig.collectAsState()
    val remoteUpdateHistory by adminViewModel.remoteUpdateHistory.collectAsState()

    var showPublishRemoteDialog by remember { mutableStateOf(false) }
    var remoteChangelogInput by remember { mutableStateOf("") }
    var remoteForceRefreshInput by remember { mutableStateOf(false) }

    // Versão atualmente instalada no app
    val pInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) { null }
    }
    val installedVersionName = pInfo?.versionName ?: "1.0.0"
    val installedVersionCode = if (pInfo != null) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") pInfo.versionCode
        }
    } else 1

    // Versões publicadas ativas
    val activeVersions = remember(publishedVersions) {
        publishedVersions.filter { it.published && it.status != "DEPRECATED" }
    }

    var selectedTargetVersion by remember { mutableStateOf<AppVersionEntity?>(null) }
    LaunchedEffect(activeVersions) {
        if (selectedTargetVersion == null && activeVersions.isNotEmpty()) {
            selectedTargetVersion = activeVersions.firstOrNull()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Distribuição, 1: Histórico, 2: Auditoria
    var selectedDeviceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filterMode by remember { mutableStateOf("Todos") } // "Todos", "Pendentes", "Online", "Offline"
    var expandedDeviceId by remember { mutableStateOf<String?>(null) }

    var showPublishDialog by remember { mutableStateOf(false) }
    var showActivateUpdateDialog by remember { mutableStateOf(false) }
    var showDeactivateUpdateDialog by remember { mutableStateOf(false) }
    var selectedVersionForActivation by remember { mutableStateOf<AppVersionEntity?>(null) }
    var versionDetailModal by remember { mutableStateOf<AppVersionEntity?>(null) }
    var showConfirmUpdateAllDialog by remember { mutableStateOf(false) }
    var showConfirmUpdateSelectedDialog by remember { mutableStateOf(false) }

    // Dispositivos filtrados
    val targetCode = selectedTargetVersion?.versionCode ?: 0
    val filteredDevices = remember(allDevices, filterMode, targetCode) {
        when (filterMode) {
            "Pendentes" -> allDevices.filter { it.buildNumber < targetCode }
            "Online" -> allDevices.filter { it.isOnline }
            "Offline" -> allDevices.filter { !it.isOnline }
            else -> allDevices
        }
    }

    val pendingCount = remember(allDevices, targetCode) {
        allDevices.count { it.buildNumber < targetCode }
    }
    val updatedCount = remember(allDevices, targetCode) {
        allDevices.count { it.buildNumber >= targetCode && targetCode > 0 }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWideScreen = maxWidth > 680.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isWideScreen) 20.dp else 12.dp, vertical = 8.dp)
            ) {
                // ==========================================
                // 1. CABEÇALHO COMPACTO
                // ==========================================
                AdminUpdatesHeader(
                    isWideScreen = isWideScreen,
                    onPublishNew = { showPublishDialog = true },
                    onRefresh = { adminViewModel.refreshDevices() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // 2. RESUMO DA VERSÃO (3 CARDS COMPACTOS)
                // ==========================================
                CompactMetricsGrid(
                    isWideScreen = isWideScreen,
                    remoteVersion = remoteAppConfig.remoteVersion,
                    remoteBuild = remoteAppConfig.build,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    devicesCount = allDevices.size,
                    isMaintenanceActive = updateControl.enabled
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ==========================================
                // 3. TABS COMPACTAS
                // ==========================================
                CompactAdminTabs(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    historyCount = publishedVersions.size,
                    distributionCount = allDevices.size,
                    auditCount = updateEvents.size
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ==========================================
                // 4. CONTEÚDO DAS ABAS
                // ==========================================
                when (selectedTab) {
                    0 -> {
                        // ABA 0: ATUALIZAÇÃO REMOTA (CONTEÚDO & INTERFACE)
                        RemoteUpdateTabContent(
                            remoteAppConfig = remoteAppConfig,
                            remoteUpdateHistory = remoteUpdateHistory,
                            changelogInput = remoteChangelogInput,
                            onChangelogChange = { remoteChangelogInput = it },
                            forceRefreshInput = remoteForceRefreshInput,
                            onForceRefreshChange = { remoteForceRefreshInput = it },
                            onPublishClick = { showPublishRemoteDialog = true },
                            onCheckNowClick = {
                                adminViewModel.checkRemoteUpdateNow { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            }
                        )
                    }

                    1 -> {
                        // ABA 1: GESTÃO DE VERSÕES APK (Formulário + Histórico)
                        VersionManagementTabContent(
                            isWideScreen = isWideScreen,
                            publishedVersions = publishedVersions,
                            updateControl = updateControl,
                            installedVersionCode = installedVersionCode,
                            adminViewModel = adminViewModel,
                            onShowSnackbar = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            onViewDetails = { versionDetailModal = it },
                            onActivateVersion = { ver ->
                                selectedVersionForActivation = ver
                                showActivateUpdateDialog = true
                            }
                        )
                    }

                    2 -> {
                        // ABA 2: CONFIGURAÇÕES E DISPOSITIVOS
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactUpdateControlCard(
                                updateControl = updateControl,
                                onActivate = { showActivateUpdateDialog = true },
                                onDeactivate = { showDeactivateUpdateDialog = true },
                                onChangeVersion = { showActivateUpdateDialog = true }
                            )

                            DistributionTabCompact(
                                allDevices = allDevices,
                                filteredDevices = filteredDevices,
                                activeVersions = activeVersions,
                                selectedTargetVersion = selectedTargetVersion,
                                onSelectTargetVersion = { selectedTargetVersion = it },
                                selectedDeviceIds = selectedDeviceIds,
                                onToggleDeviceSelection = { id ->
                                    selectedDeviceIds = if (selectedDeviceIds.contains(id)) {
                                        selectedDeviceIds - id
                                    } else {
                                        selectedDeviceIds + id
                                    }
                                },
                                onSelectAllFiltered = {
                                    val ids = filteredDevices.map { it.deviceId }.toSet()
                                    selectedDeviceIds = if (selectedDeviceIds.containsAll(ids)) emptySet() else ids
                                },
                                filterMode = filterMode,
                                onFilterChange = { filterMode = it },
                                expandedDeviceId = expandedDeviceId,
                                onToggleExpandDevice = { id ->
                                    expandedDeviceId = if (expandedDeviceId == id) null else id
                                },
                                onUpdateAllCompatible = { showConfirmUpdateAllDialog = true },
                                onUpdateSelected = { showConfirmUpdateSelectedDialog = true },
                                onUpdateIndividualDevice = { dev, targetVer ->
                                    adminViewModel.updateSelectedDevices(setOf(dev.deviceId), targetVer)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Atualização enviada para ${dev.name.ifBlank { dev.model }}")
                                    }
                                },
                                onToggleBlockDevice = { dev ->
                                    val newStatus = !dev.isBlocked
                                    adminViewModel.setDeviceBlockedStatus(dev.deviceId, newStatus)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(if (newStatus) "Dispositivo ${dev.name.ifBlank { dev.model }} bloqueado." else "Dispositivo desbloqueado.")
                                    }
                                },
                                onToggleAdminAccess = { dev ->
                                    val newAdmin = !dev.adminAccess
                                    adminViewModel.setDeviceAdminAccess(dev.deviceId, newAdmin)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(if (newAdmin) "Acesso de administrador concedido." else "Acesso de administrador revogado.")
                                    }
                                },
                                onForceDeviceSync = { dev ->
                                    adminViewModel.requestDeviceSync(dev.deviceId)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Sincronização solicitada para ${dev.name.ifBlank { dev.model }}.")
                                    }
                                }
                            )
                        }
                    }

                    3 -> {
                        // ABA 3: AUDITORIA DE ATUALIZAÇÕES
                        UpdateAuditTabContent(events = updateEvents)
                    }
                }
            }
        }
    }

    // Modal de Ativação Explícita de Atualização
    if (showActivateUpdateDialog) {
        ActivateUpdateDialog(
            publishedVersions = publishedVersions,
            initialVersion = selectedVersionForActivation,
            onDismiss = {
                showActivateUpdateDialog = false
                selectedVersionForActivation = null
            },
            onActivate = { versionToActivate, mandatory ->
                adminViewModel.activateAppUpdate(versionToActivate, mandatory)
                showActivateUpdateDialog = false
                selectedVersionForActivation = null
                scope.launch {
                    snackbarHostState.showSnackbar("✓ Atualização para v${versionToActivate.versionName} ativada globalmente com sucesso!")
                }
            }
        )
    }

    // Modal de Desativação de Atualização
    if (showDeactivateUpdateDialog) {
        DeactivateUpdateDialog(
            onDismiss = { showDeactivateUpdateDialog = false },
            onConfirm = {
                adminViewModel.deactivateAppUpdate()
                showDeactivateUpdateDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("✓ Atualização desativada. Nenhuma atualização será exibida aos usuários.")
                }
            }
        )
    }

    // Modal de Publicação de Atualização Remota (Conteúdo/Interface)
    if (showPublishRemoteDialog) {
        PublishRemoteUpdateConfirmationDialog(
            currentVersion = remoteAppConfig.remoteVersion,
            changelog = remoteChangelogInput,
            forceRefresh = remoteForceRefreshInput,
            onDismiss = { showPublishRemoteDialog = false },
            onConfirm = {
                showPublishRemoteDialog = false
                adminViewModel.publishRemoteUpdate(
                    changelog = remoteChangelogInput,
                    forceRefresh = remoteForceRefreshInput
                ) { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                    if (success) {
                        remoteChangelogInput = ""
                        remoteForceRefreshInput = false
                    }
                }
            }
        )
    }

    // Modal de Publicação Simplificado
    if (showPublishDialog) {
        PublishVersionDialog(
            adminViewModel = adminViewModel,
            installedVersionCode = installedVersionCode,
            onDismiss = { showPublishDialog = false },
            onPublished = { newVer ->
                showPublishDialog = false
                selectedTargetVersion = newVer
                scope.launch {
                    snackbarHostState.showSnackbar("✓ Versão v${newVer.versionName} publicada com sucesso!")
                }
            }
        )
    }

    // Modal de Detalhes da Versão
    versionDetailModal?.let { ver ->
        VersionDetailsDialog(
            version = ver,
            onDismiss = { versionDetailModal = null },
            onStatusChanged = { newStatus ->
                adminViewModel.setAppVersionStatus(ver.id, newStatus)
                versionDetailModal = null
                scope.launch {
                    snackbarHostState.showSnackbar("Status atualizado para $newStatus")
                }
            },
            onDelete = {
                adminViewModel.deleteAppVersion(ver.id)
                versionDetailModal = null
                scope.launch {
                    snackbarHostState.showSnackbar("Versão v${ver.versionName} removida com sucesso")
                }
            }
        )
    }

    // Confirmação: Atualizar Todos
    if (showConfirmUpdateAllDialog && selectedTargetVersion != null) {
        val target = selectedTargetVersion!!
        val count = allDevices.count { it.buildNumber < target.versionCode }
        AlertDialog(
            onDismissRequest = { showConfirmUpdateAllDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("ATUALIZAR DISPOSITIVOS?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Deseja enviar a notificação de atualização para v${target.versionName} (Build ${target.versionCode}) para todos os $count dispositivos compatíveis?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.updateAllCompatibleDevices(target)
                        showConfirmUpdateAllDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Notificação enviada para $count dispositivos compatíveis!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("CONFIRMAR ATUALIZAÇÃO EM MASSA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmUpdateAllDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    // Confirmação: Atualizar Selecionados
    if (showConfirmUpdateSelectedDialog && selectedTargetVersion != null) {
        val target = selectedTargetVersion!!
        AlertDialog(
            onDismissRequest = { showConfirmUpdateSelectedDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("ATUALIZAR SELECIONADOS?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Enviar atualização v${target.versionName} para os ${selectedDeviceIds.size} dispositivos selecionados?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.updateSelectedDevices(selectedDeviceIds, target)
                        val size = selectedDeviceIds.size
                        selectedDeviceIds = emptySet()
                        showConfirmUpdateSelectedDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Atualização enviada para $size dispositivos!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("CONFIRMAR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmUpdateSelectedDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

// =========================================================================
// 1. CABEÇALHO COMPACTO & RESPONSIVO
// =========================================================================
@Composable
private fun AdminUpdatesHeader(
    isWideScreen: Boolean,
    onPublishNew: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "GESTÃO DE VERSÕES APK",
                color = Color.White,
                fontSize = if (isWideScreen) 18.sp else 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Gerencie as versões disponíveis e publique novas atualizações.",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onPublishNew,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = if (isWideScreen) 14.dp else 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("admin_header_publish_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "+ PUBLICAR VERSÃO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(48.dp)
                    .background(DarkSurface, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Recarregar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// =========================================================================
// 2. CARDS DE RESUMO COMPACTOS
// =========================================================================
@Composable
private fun CompactMetricsGrid(
    isWideScreen: Boolean,
    remoteVersion: String,
    remoteBuild: Int,
    installedVersionName: String,
    installedVersionCode: Int,
    devicesCount: Int,
    isMaintenanceActive: Boolean
) {
    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryCard(
                title = "VERSÃO REMOTA",
                mainValue = remoteVersion,
                subValue = "Build $remoteBuild • Nuvem",
                mainColor = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "VERSÃO APK",
                mainValue = "v$installedVersionName",
                subValue = "Build $installedVersionCode • Local",
                mainColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "DISPOSITIVOS",
                mainValue = "$devicesCount",
                subValue = "✓ Sincronizados",
                mainColor = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "STATUS SISTEMA",
                mainValue = if (isMaintenanceActive) "MANUTENÇÃO" else "OPERACIONAL",
                subValue = if (isMaintenanceActive) "Bloqueado" else "Online",
                mainColor = if (isMaintenanceActive) BrandRed else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    title = "VERSÃO REMOTA",
                    mainValue = remoteVersion,
                    subValue = "Build $remoteBuild",
                    mainColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "VERSÃO APK",
                    mainValue = "v$installedVersionName",
                    subValue = "Build $installedVersionCode",
                    mainColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    title = "DISPOSITIVOS",
                    mainValue = "$devicesCount ativos",
                    subValue = "Conectados",
                    mainColor = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "STATUS",
                    mainValue = if (isMaintenanceActive) "MANUTENÇÃO" else "OPERACIONAL",
                    subValue = if (isMaintenanceActive) "Bloqueado" else "Online",
                    mainColor = if (isMaintenanceActive) BrandRed else Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    mainValue: String,
    subValue: String,
    mainColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 72.dp, max = 92.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF26262E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = mainValue,
                color = mainColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subValue,
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =========================================================================
// 4. TABS COMPACTAS
// =========================================================================
@Composable
private fun CompactAdminTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    historyCount: Int,
    distributionCount: Int,
    auditCount: Int
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = DarkSurface,
        contentColor = Color.White,
        edgePadding = 0.dp,
        indicator = { tabPositions ->
            if (selectedTab < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = BrandRed,
                    height = 2.5.dp
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            text = {
                Text(
                    text = "⚡ Atualização Remota",
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selectedTab == 0) Color.White else Color.Gray
                )
            }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            text = {
                Text(
                    text = "📦 Versões APK ($historyCount)",
                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selectedTab == 1) Color.White else Color.Gray
                )
            }
        )
        Tab(
            selected = selectedTab == 2,
            onClick = { onSelectTab(2) },
            text = {
                Text(
                    text = "📱 Dispositivos ($distributionCount)",
                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selectedTab == 2) Color.White else Color.Gray
                )
            }
        )
        Tab(
            selected = selectedTab == 3,
            onClick = { onSelectTab(3) },
            text = {
                Text(
                    text = "📋 Auditoria ($auditCount)",
                    fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selectedTab == 3) Color.White else Color.Gray
                )
            }
        )
    }
}

// =========================================================================
// 5. ABA 1: DISTRIBUIÇÃO COMPACTA (LISTA DE DISPOSITIVOS EM DESTAQUE)
// =========================================================================
@Composable
private fun DistributionTabCompact(
    allDevices: List<DeviceEntity>,
    filteredDevices: List<DeviceEntity>,
    activeVersions: List<AppVersionEntity>,
    selectedTargetVersion: AppVersionEntity?,
    onSelectTargetVersion: (AppVersionEntity) -> Unit,
    selectedDeviceIds: Set<String>,
    onToggleDeviceSelection: (String) -> Unit,
    onSelectAllFiltered: () -> Unit,
    filterMode: String,
    onFilterChange: (String) -> Unit,
    expandedDeviceId: String?,
    onToggleExpandDevice: (String) -> Unit,
    onUpdateAllCompatible: () -> Unit,
    onUpdateSelected: () -> Unit,
    onUpdateIndividualDevice: (DeviceEntity, AppVersionEntity) -> Unit,
    onToggleBlockDevice: (DeviceEntity) -> Unit,
    onToggleAdminAccess: (DeviceEntity) -> Unit,
    onForceDeviceSync: (DeviceEntity) -> Unit
) {
    var showVersionPickerDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Linha 1: Seletor de Versão de Destino + Atualizar Compatíveis
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dropdown Versão de Destino
            Box(modifier = Modifier.weight(1.3f)) {
                Surface(
                    onClick = { showVersionPickerDropdown = true },
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selectedTargetVersion != null) BrandRed.copy(alpha = 0.6f) else Color.DarkGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.Center) {
                            Text("VERSÃO DE DESTINO", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (selectedTargetVersion != null) "v${selectedTargetVersion.versionName} (${selectedTargetVersion.versionCode})" else "Nenhuma ativa",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
                    }
                }

                DropdownMenu(
                    expanded = showVersionPickerDropdown,
                    onDismissRequest = { showVersionPickerDropdown = false },
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    if (activeVersions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nenhuma versão publicada", color = Color.Gray) },
                            onClick = { showVersionPickerDropdown = false }
                        )
                    } else {
                        activeVersions.forEach { ver ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("v${ver.versionName} (Build ${ver.versionCode})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (ver.mandatory) {
                                            Text("Obrigatória", color = BrandRed, fontSize = 10.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectTargetVersion(ver)
                                    showVersionPickerDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Botão: Atualizar Compatíveis
            Button(
                onClick = onUpdateAllCompatible,
                enabled = selectedTargetVersion != null && allDevices.any { it.buildNumber < (selectedTargetVersion.versionCode) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed, disabledContainerColor = Color(0xFF333333)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .weight(1.5f)
                    .height(42.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ATUALIZAR COMPATÍVEIS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Linha 2: Filtros Compactos + Ação em Lote
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Chips de Filtro
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Todos", "Pendentes", "Online", "Offline").forEach { f ->
                    val isSelected = filterMode == f
                    Surface(
                        onClick = { onFilterChange(f) },
                        color = if (isSelected) BrandRed else DarkSurface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = f,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Ação em massa com selecionados
            if (selectedDeviceIds.isNotEmpty() && selectedTargetVersion != null) {
                Button(
                    onClick = onUpdateSelected,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ATUALIZAR (${selectedDeviceIds.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Linha 3: Barra de Seleção Rápida
        Surface(
            color = Color(0xFF141414),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSelectAllFiltered() }
                ) {
                    val allFilteredIds = filteredDevices.map { it.deviceId }.toSet()
                    val isAllSelected = allFilteredIds.isNotEmpty() && selectedDeviceIds.containsAll(allFilteredIds)
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { onSelectAllFiltered() },
                        colors = CheckboxDefaults.colors(checkedColor = BrandRed),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selecionar filtrados (${filteredDevices.size})", color = Color.LightGray, fontSize = 11.sp)
                }

                Text(
                    text = "${filteredDevices.size} dispositivos",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Linha 4: Lista de Dispositivos (Começa cedo!)
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum dispositivo encontrado neste filtro.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredDevices, key = { it.deviceId }) { dev ->
                    val isSelected = selectedDeviceIds.contains(dev.deviceId)
                    val targetCode = selectedTargetVersion?.versionCode ?: 0
                    val isUpdated = targetCode > 0 && dev.buildNumber >= targetCode
                    val isExpanded = expandedDeviceId == dev.deviceId

                    CompactDeviceCard(
                        dev = dev,
                        isSelected = isSelected,
                        isUpdated = isUpdated,
                        isExpanded = isExpanded,
                        selectedTargetVersion = selectedTargetVersion,
                        onToggleSelection = { onToggleDeviceSelection(dev.deviceId) },
                        onToggleExpand = { onToggleExpandDevice(dev.deviceId) },
                        onUpdateIndividual = {
                            selectedTargetVersion?.let { target ->
                                onUpdateIndividualDevice(dev, target)
                            }
                        },
                        onToggleBlock = { onToggleBlockDevice(dev) },
                        onToggleAdmin = { onToggleAdminAccess(dev) },
                        onForceSync = { onForceDeviceSync(dev) }
                    )
                }
            }
        }
    }
}

// =========================================================================
// 6. CARD DO DISPOSITIVO COMPACTO COM EXPANSÃO INTELIGENTE
// =========================================================================
@Composable
private fun CompactDeviceCard(
    dev: DeviceEntity,
    isSelected: Boolean,
    isUpdated: Boolean,
    isExpanded: Boolean,
    selectedTargetVersion: AppVersionEntity?,
    onToggleSelection: () -> Unit,
    onToggleExpand: () -> Unit,
    onUpdateIndividual: () -> Unit,
    onToggleBlock: () -> Unit,
    onToggleAdmin: () -> Unit,
    onForceSync: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF221415) else DarkSurface
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Linha Principal do Dispositivo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = BrandRed),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Indicador Online / Offline
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (dev.isOnline) Color(0xFF4CAF50) else Color(0xFF757575), CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Info Resumida (Clicável para expandir)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = dev.name.ifBlank { dev.model }.ifBlank { "Dispositivo" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (dev.isBlocked) {
                            Surface(
                                color = BrandRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text("BLOQUEADO", color = BrandRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                        }
                        if (dev.adminAccess) {
                            Surface(
                                color = Color(0xFF1976D2).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text("ADMIN", color = Color(0xFF64B5F6), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "v${dev.appVersion} (${dev.buildNumber})",
                            color = if (isUpdated) Color(0xFF81C784) else Color(0xFFFFB74D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("•", color = Color.Gray, fontSize = 10.sp)
                        Text(
                            text = "ID: ${dev.deviceId.take(8)}...",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text("•", color = Color.Gray, fontSize = 10.sp)
                        Text(
                            text = if (dev.isOnline) "Online" else dev.lastActivityFormatted.take(10),
                            color = if (dev.isOnline) Color(0xFF81C784) else Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Ação Rápida de Atualizar
                if (selectedTargetVersion != null) {
                    if (isUpdated) {
                        Surface(
                            color = Color(0xFF1B5E20).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "✓ ATUALIZADO",
                                color = Color(0xFF81C784),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onUpdateIndividual,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("ATUALIZAR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Chevron para Expandir
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Recolher detalhes" else "Ver detalhes",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ==========================================
            // DETALHES EXPANSÍVEIS (SOMENTE DISPOSITIVO ATIVO)
            // ==========================================
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Grade de Detalhes
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DetailRow(label = "Plataforma / Modelo:", value = "${dev.platform} • ${dev.model.ifBlank { "N/D" }} (${dev.osVersion})")
                            DetailRow(
                                label = "Device ID:",
                                value = dev.deviceId,
                                copyable = true,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Device ID", dev.deviceId))
                                }
                            )
                            DetailRow(label = "Primeira Conexão:", value = dev.firstConnectionFormatted)
                            DetailRow(label = "Último Heartbeat:", value = dev.lastActivityFormatted)
                            DetailRow(label = "Status de Acesso:", value = dev.accessStatus)
                            DetailRow(label = "FCM Token:", value = if (dev.fcmToken.isNotBlank()) "Configurado (${dev.fcmStatus})" else "Não registrado")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Ações Administrativas Adicionais
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleBlock,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (dev.isBlocked) Color(0xFF4CAF50) else BrandRed),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (dev.isBlocked) Color(0xFF81C784) else BrandRed
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Text(if (dev.isBlocked) "DESBLOQUEAR" else "BLOQUEAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onToggleAdmin,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color.Gray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Text(if (dev.adminAccess) "REVOGAR ADMIN" else "TORNAR ADMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onForceSync,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("SINCRONIZAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// CARD COMPACTO: CONTROLE DA ATUALIZAÇÃO
// =========================================================================
@Composable
private fun CompactUpdateControlCard(
    updateControl: UpdateControlEntity,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onChangeVersion: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (updateControl.enabled) Color(0xFF1E1114) else Color(0xFF111813)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (updateControl.enabled) BrandRed.copy(alpha = 0.5f) else Color(0xFF2E7D32).copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (updateControl.enabled) BrandRed else Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONTROLE DA ATUALIZAÇÃO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (updateControl.enabled) BrandRed.copy(alpha = 0.15f) else Color(0xFF2E7D32).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (updateControl.enabled) "ATUALIZAÇÃO ATIVA" else "INATIVA",
                        color = if (updateControl.enabled) Color(0xFFFF8A80) else Color(0xFF81C784),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!updateControl.enabled) {
                Text(
                    text = "Nenhuma campanha de atualização ativa no momento.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onActivate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ATIVAR ATUALIZAÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Versão: v${updateControl.activeVersionName ?: ""} (Build ${updateControl.activeVersionCode}) • ${if (updateControl.mandatory) "Obrigatória" else "Opcional"}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (updateControl.apkUrl.isNotBlank()) {
                            Text(
                                text = "APK: ${updateControl.apkUrl.take(38)}...",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onDeactivate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("DESATIVAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onChangeVersion,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("ALTERAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (copyable && onCopy != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar",
                    tint = BrandRed,
                    modifier = Modifier
                        .size(12.dp)
                        .clickable { onCopy() }
                )
            }
        }
    }
}
@Composable
private fun VersionManagementTabContent(
    isWideScreen: Boolean,
    publishedVersions: List<AppVersionEntity>,
    updateControl: UpdateControlEntity,
    installedVersionCode: Int,
    adminViewModel: AdminViewModel,
    onShowSnackbar: (String) -> Unit,
    onViewDetails: (AppVersionEntity) -> Unit,
    onActivateVersion: (AppVersionEntity) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. CARD DE FORMULÁRIO COMPACTO "PUBLICAR NOVA VERSÃO"
        InlinePublishVersionCard(
            isWideScreen = isWideScreen,
            publishedVersions = publishedVersions,
            adminViewModel = adminViewModel,
            onPublishedSuccess = { newVer ->
                onShowSnackbar("✓ Versão v${newVer.versionName} publicada com sucesso!")
            }
        )

        // 2. SEÇÃO HISTÓRICO DE VERSÕES
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTÓRICO DE VERSÕES",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                Surface(
                    color = Color(0xFF1E1E24),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${publishedVersions.size} cadastradas",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (publishedVersions.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma versão cadastrada no histórico.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                publishedVersions.forEach { ver ->
                    val dateStr = remember(ver.createdAt) {
                        if (ver.createdAt > 0) {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ver.createdAt))
                        } else "S/D"
                    }
                    val isCurrentlyActive = updateControl.enabled && updateControl.activeVersionCode == ver.versionCode
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentlyActive) Color(0xFF231416) else DarkSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrentlyActive) BrandRed.copy(alpha = 0.6f) else Color(0xFF26262E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Linha superior: Versão, Build e Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "v${ver.versionName}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Build ${ver.versionCode}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (isCurrentlyActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = BrandRed,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "● ATUAL",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (ver.mandatory) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = BrandRed.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "OBRIGATÓRIA",
                                                color = BrandRed,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF333333),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "OPCIONAL",
                                                color = Color.LightGray,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = if (ver.status == "PUBLISHED") Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFF333333),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (ver.status == "PUBLISHED") "PUBLICA" else ver.status,
                                        color = if (ver.status == "PUBLISHED") Color(0xFF81C784) else Color.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Notas da Versão
                            Text(
                                text = if (ver.releaseNotes.isNotBlank()) ver.releaseNotes else "Sem notas de versão fornecidas.",
                                color = if (ver.releaseNotes.isNotBlank()) Color.LightGray else Color.Gray,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Linha inferior: Data e Ações Compactas [COPIAR] [EXCLUIR]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Publicado em $dateStr",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(ver.apkUrl))
                                            onShowSnackbar("✓ URL copiada")
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFF3A3A42)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("COPIAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showDeleteConfirm = true },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                                        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp), tint = BrandRed)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("EXCLUIR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Diálogo de Confirmação de Exclusão
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            containerColor = Color(0xFF1E1E1E),
                            title = {
                                Text(
                                    text = "EXCLUIR VERSÃO?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Tem certeza de que deseja excluir permanentemente a versão v${ver.versionName} (Build ${ver.versionCode})?",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                    if (isCurrentlyActive) {
                                        Text(
                                            text = "⚠ ATENÇÃO: Esta versão está atualmente configurada como a versão ativa de atualização do aplicativo!",
                                            color = BrandRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteConfirm = false
                                        adminViewModel.deleteAppVersion(ver.id)
                                        onShowSnackbar("✓ Versão v${ver.versionName} excluída com sucesso.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("EXCLUIR", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// FORMULÁRIO INLINE: PUBLICAR NOVA VERSÃO
// =========================================================================
@Composable
private fun InlinePublishVersionCard(
    isWideScreen: Boolean,
    publishedVersions: List<AppVersionEntity>,
    adminViewModel: AdminViewModel,
    onPublishedSuccess: (AppVersionEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val nextSuggestedBuild = remember(publishedVersions) {
        (publishedVersions.maxOfOrNull { it.versionCode } ?: 100) + 1
    }

    var versionName by remember { mutableStateOf("") }
    var versionCodeText by remember { mutableStateOf(nextSuggestedBuild.toString()) }
    var apkUrl by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var isMandatory by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Validações em tempo real
    val versionCodeInt = versionCodeText.toIntOrNull() ?: 0
    val isBuildAlreadyExists = remember(versionCodeInt, publishedVersions) {
        publishedVersions.any { it.versionCode == versionCodeInt }
    }
    val isValidVersionName = versionName.isNotBlank()
    val isValidUrl = apkUrl.isNotBlank() && (apkUrl.startsWith("http://") || apkUrl.startsWith("https://"))

    val isFormValid = isValidVersionName && versionCodeInt > 0 && !isBuildAlreadyExists && isValidUrl && !isSubmitting

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF26262E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header do Formulário
            Column {
                Text(
                    text = "PUBLICAR NOVA VERSÃO",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Adicione uma nova versão do aplicativo para distribuição.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Campos Versão e Build
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VERSÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            placeholder = { Text("Ex.: 1.6.0", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color(0xFF33333D)
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("BUILD", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = versionCodeText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) versionCodeText = it },
                            placeholder = { Text("Ex.: 106", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = if (isBuildAlreadyExists) BrandRed else BrandRed,
                                unfocusedBorderColor = Color(0xFF33333D)
                            )
                        )
                        if (isBuildAlreadyExists) {
                            Text("⚠ Esta build já existe", color = Color(0xFFFF8A80), fontSize = 10.sp)
                        } else if (versionCodeInt > 0) {
                            Text("✓ Build disponível", color = Color(0xFF81C784), fontSize = 10.sp)
                        }
                    }
                }
            } else {
                // Mobile: empilhados
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text("VERSÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            placeholder = { Text("Ex.: 1.6.0", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color(0xFF33333D)
                            )
                        )
                    }

                    Column {
                        Text("BUILD", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = versionCodeText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) versionCodeText = it },
                            placeholder = { Text("Ex.: 106", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color(0xFF33333D)
                            )
                        )
                        if (isBuildAlreadyExists) {
                            Text("⚠ Esta build já existe", color = Color(0xFFFF8A80), fontSize = 10.sp)
                        } else if (versionCodeInt > 0) {
                            Text("✓ Build disponível", color = Color(0xFF81C784), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Campo URL DO APK
            Column {
                Text("URL DO APK", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apkUrl,
                    onValueChange = { apkUrl = it },
                    placeholder = { Text("https://servidor.com/app-v1.6.0.apk", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0xFF33333D)
                    )
                )
                Text(
                    text = "Use uma URL HTTPS direta para o arquivo APK.",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                if (apkUrl.isNotBlank()) {
                    if (isValidUrl) {
                        Text("✓ URL válida", color = Color(0xFF81C784), fontSize = 10.sp)
                    } else {
                        Text("⚠ URL HTTPS inválida", color = Color(0xFFFF8A80), fontSize = 10.sp)
                    }
                }
            }

            // Textarea NOTAS DA VERSÃO
            Column {
                Text("NOTAS DA VERSÃO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = releaseNotes,
                    onValueChange = { releaseNotes = it },
                    placeholder = { Text("Ex.: Correções, melhorias e novidades desta versão...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0xFF33333D)
                    )
                )
            }

            // ATUALIZAÇÃO OBRIGATÓRIA Switch
            Surface(
                color = Color(0xFF16161C),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Atualização obrigatória",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Exigir esta versão para continuar.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isMandatory,
                        onCheckedChange = { isMandatory = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandRed,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
            }

            submitError?.let { err ->
                Text(text = "⚠ $err", color = Color(0xFFFF8A80), fontSize = 11.sp)
            }

            // Botão CTA Principal PUBLICAR VERSÃO
            Button(
                onClick = { showConfirmDialog = true },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    disabledContainerColor = Color(0xFF33333D)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_inline_publish_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PUBLICANDO...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PUBLICAR VERSÃO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    // Modal de Confirmação Seguro
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "PUBLICAR NOVA VERSÃO?",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Versão: v${versionName.trim()}", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("• Build Code: $versionCodeInt", color = Color.LightGray, fontSize = 12.sp)
                    Text("• Modalidade: ${if (isMandatory) "Obrigatória (Bloqueante)" else "Opcional"}", color = if (isMandatory) BrandRed else Color.LightGray, fontSize = 12.sp)
                    Text("• URL: ${apkUrl.take(40)}...", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Esta ação registrará a nova versão na plataforma para disponibilidade geral.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isSubmitting = true
                        submitError = null

                        scope.launch {
                            val newVersion = AppVersionEntity(
                                id = "v_${versionName.replace(".", "_")}_$versionCodeInt",
                                versionName = versionName.trim(),
                                versionCode = versionCodeInt,
                                packageName = context.packageName,
                                apkUrl = apkUrl.trim(),
                                releaseNotes = releaseNotes.trim(),
                                sha256 = "",
                                fileSize = "",
                                mandatory = isMandatory,
                                published = true,
                                status = "PUBLISHED",
                                minimumVersionCode = 100,
                                publishedAt = System.currentTimeMillis(),
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )

                            adminViewModel.publishAppVersion(newVersion)
                            isSubmitting = false
                            onPublishedSuccess(newVersion)

                            // Limpar formulário
                            versionName = ""
                            apkUrl = ""
                            releaseNotes = ""
                            isMandatory = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("PUBLICAR", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        )
    }
}

// =========================================================================
// 7. ABA 2: HISTÓRICO DE VERSÕES COMPACTO
// =========================================================================
@Composable
fun VersionHistoryTabContent(
    versions: List<AppVersionEntity>,
    updateControl: UpdateControlEntity,
    onViewDetails: (AppVersionEntity) -> Unit,
    onPublishNew: () -> Unit,
    onActivateVersion: (AppVersionEntity) -> Unit
) {
    if (versions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nenhuma versão publicada até o momento.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onPublishNew,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("PUBLICAR PRIMEIRA VERSÃO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(versions, key = { it.id }) { ver ->
                val dateStr = remember(ver.createdAt) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ver.createdAt))
                }
                val isCurrentlyActive = updateControl.enabled && updateControl.activeVersionCode == ver.versionCode

                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isCurrentlyActive) Color(0xFF231416) else DarkSurface),
                    border = if (isCurrentlyActive) BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f)) else null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "v${ver.versionName}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color.DarkGray,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Build ${ver.versionCode}",
                                        color = Color.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                if (isCurrentlyActive) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        color = BrandRed,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("★ ATIVA GLOBAL", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                } else if (ver.mandatory) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        color = BrandRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("OBRIGATÓRIA", color = BrandRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            // Status Tag
                            val (statusBg, statusFg) = when (ver.status) {
                                "DEPRECATED" -> Color(0xFF424242) to Color.LightGray
                                "DRAFT" -> Color(0xFFF57C00).copy(alpha = 0.2f) to Color(0xFFFFB74D)
                                else -> Color(0xFF2E7D32).copy(alpha = 0.2f) to Color(0xFF81C784)
                            }
                            Surface(color = statusBg, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = ver.status,
                                    color = statusFg,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (ver.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ver.releaseNotes,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Criada em: $dateStr", color = Color.Gray, fontSize = 10.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (ver.hasConfiguredApk && ver.status != "DEPRECATED" && !isCurrentlyActive) {
                                    Button(
                                        onClick = { onActivateVersion(ver) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("ATIVAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { onViewDetails(ver) },
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp), tint = BrandRed)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("GERENCIAR", color = BrandRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. ABA 3: AUDITORIA DE ATUALIZAÇÕES COMPACTA
// =========================================================================
@Composable
fun UpdateAuditTabContent(
    events: List<UpdateEventEntity>
) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nenhum evento de atualização registrado recentemente.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(events, key = { it.id }) { ev ->
                val dateStr = remember(ev.startedAt) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ev.startedAt))
                }

                val (badgeBg, badgeFg) = when (ev.status) {
                    "UPDATED" -> Color(0xFF2E7D32).copy(alpha = 0.25f) to Color(0xFF81C784)
                    "DOWNLOADING", "DOWNLOADED", "INSTALLING" -> Color(0xFFF57C00).copy(alpha = 0.25f) to Color(0xFFFFB74D)
                    "NOTIFIED", "PENDING" -> Color(0xFF1976D2).copy(alpha = 0.25f) to Color(0xFF64B5F6)
                    "FAILED" -> Color(0xFFC62828).copy(alpha = 0.25f) to Color(0xFFEF9A9A)
                    else -> Color.DarkGray to Color.LightGray
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ev.deviceName.ifBlank { "Dispositivo ${ev.deviceId.take(8)}" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "v${ev.fromVersion} ➔ v${ev.toVersion}",
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Disparo: ${ev.trigger} • $dateStr",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            if (!ev.error.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Erro: ${ev.error}", color = Color(0xFFEF9A9A), fontSize = 9.sp)
                            }
                        }

                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = ev.status,
                                color = badgeFg,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 9. DIÁLOGOS DE ATUALIZAÇÃO E GERENCIAMENTO
// =========================================================================
@Composable
fun PublishVersionDialog(
    adminViewModel: AdminViewModel,
    installedVersionCode: Int,
    onDismiss: () -> Unit,
    onPublished: (AppVersionEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var versionName by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var isMandatory by remember { mutableStateOf(false) }
    var isPublished by remember { mutableStateOf(true) }

    var isUploadingApk by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    // Metadados do APK inspecionado
    var selectedApkFile by remember { mutableStateOf<File?>(null) }
    var apkMetadata by remember { mutableStateOf<ApkMetadata?>(null) }
    var isProcessingApk by remember { mutableStateOf(false) }

    // Opções Avançadas (Colapsáveis)
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var customApkUrl by remember { mutableStateOf("") }
    var customVersionCodeText by remember { mutableStateOf("") }
    var customSha256 by remember { mutableStateOf("") }
    var minVersionCodeText by remember { mutableStateOf("100") }
    var isTestingUrl by remember { mutableStateOf(false) }
    var urlTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Confirmação de Publicação
    var showConfirmPublishDialog by remember { mutableStateOf(false) }

    // Launcher do SAF para selecionar APK
    val apkFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingApk = true
            scope.launch(Dispatchers.IO) {
                val tempFile = UpdateManager.copyUriToTempFile(context, uri, "candidate_update.apk")
                if (tempFile != null) {
                    val meta = UpdateManager.inspectApkFile(context, tempFile)
                    withContext(Dispatchers.Main) {
                        selectedApkFile = tempFile
                        apkMetadata = meta
                        isProcessingApk = false
                        if (meta.isValid) {
                            versionName = meta.versionName
                            customVersionCodeText = meta.versionCode.toString()
                            customSha256 = meta.sha256
                            if (customApkUrl.isBlank()) {
                                customApkUrl = "https://ronycine.app/download/ronycine-${meta.versionName}.apk"
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isProcessingApk = false
                    }
                }
            }
        }
    }

    val finalVersionCode = customVersionCodeText.toIntOrNull() ?: apkMetadata?.versionCode ?: 0
    val finalSha256 = customSha256.ifBlank { apkMetadata?.sha256 ?: "" }
    val finalApkUrl = customApkUrl.trim()

    val existingVersions = adminViewModel.publishedVersions.value
    val isDuplicateVersionCode = existingVersions.any { it.versionCode == finalVersionCode }
    val isDuplicateVersionName = existingVersions.any { it.versionName.equals(versionName.trim(), ignoreCase = true) }
    val isLowerThanInstalled = finalVersionCode < installedVersionCode
    
    val validationError = when {
        versionName.isBlank() -> "O nome da versão não pode estar vazio."
        finalVersionCode <= 0 -> "O código da versão (Build) deve ser um número maior que zero."
        isLowerThanInstalled -> "O código da versão ($finalVersionCode) não pode ser inferior à versão instalada atual ($installedVersionCode)."
        isDuplicateVersionCode -> "Já existe uma versão cadastrada com o código de build $finalVersionCode."
        isDuplicateVersionName -> "Já existe uma versão cadastrada com o nome $versionName."
        finalApkUrl.isNotBlank() && !finalApkUrl.startsWith("https://", ignoreCase = true) -> "A URL do APK deve começar com https:// para ser segura."
        finalApkUrl.isNotBlank() && finalApkUrl.contains(" ") -> "A URL do APK não pode conter espaços."
        isPublished && selectedApkFile == null && finalApkUrl.isBlank() -> "Informe uma URL do APK ou selecione um arquivo APK para publicar."
        else -> null
    }

    val isValidToSubmit = validationError == null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (isUploadingApk) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ENVIANDO APK PARA O STORAGE...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Calculando SHA-256 e tamanho real...", color = Color.Gray, fontSize = 11.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Título
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "NOVA VERSÃO",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PUBLICAR NO RONYCINE",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Área do APK
                    Surface(
                        onClick = { apkFileLauncher.launch("application/vnd.android.package-archive") },
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                apkMetadata?.isValid == true -> Color(0xFF4CAF50)
                                apkMetadata?.isValid == false -> BrandRed
                                else -> Color.DarkGray
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (apkMetadata?.isValid == true) Color(0xFF1B5E20) else Color(0xFF2C2C2C),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isProcessingApk) {
                                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (apkMetadata?.isValid == true) Icons.Default.Check else Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = if (apkMetadata?.isValid == true) Color(0xFF81C784) else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedApkFile != null) "APK SELECIONADO" else "SELECIONAR APK",
                                    color = if (apkMetadata?.isValid == true) Color(0xFF81C784) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (selectedApkFile != null) selectedApkFile!!.name else "Toque para carregar o arquivo .apk",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { apkFileLauncher.launch("application/vnd.android.package-archive") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("PROCURAR", fontSize = 9.sp, color = Color.White)
                            }
                        }
                    }

                    apkMetadata?.let { meta ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (meta.isValid) {
                            Surface(
                                color = Color(0xFF1B5E20).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("APK Válido e Compatível", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Tamanho: ${meta.fileSizeFormatted} • Build: ${meta.versionCode}", color = Color.LightGray, fontSize = 10.sp)
                                    Text("SHA-256: ${meta.sha256.take(16)}... (calculado)", color = Color.Gray, fontSize = 9.sp)
                                }
                            }
                        } else {
                            Surface(
                                color = BrandRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(meta.errorMessage ?: "APK inválido.", color = Color(0xFFFF8A80), fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nome da Versão
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text("Nome da Versão (ex: 1.4.0)", fontSize = 11.sp) },
                        placeholder = { Text("1.4.0", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notas da Versão
                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        label = { Text("Notas da Versão", fontSize = 11.sp) },
                        placeholder = { Text("O que mudou nesta versão?", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 65.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Checkbox Obrigatória
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMandatory = !isMandatory }
                    ) {
                        Checkbox(
                            checked = isMandatory,
                            onCheckedChange = { isMandatory = it },
                            colors = CheckboxDefaults.colors(checkedColor = BrandRed),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Atualização Obrigatória", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Impede o uso do app em versões antigas", color = Color.Gray, fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Opções Avançadas Colapsáveis
                    Surface(
                        onClick = { showAdvancedOptions = !showAdvancedOptions },
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OPÇÕES AVANÇADAS", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAdvancedOptions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customApkUrl,
                                onValueChange = {
                                    customApkUrl = it
                                    urlTestResult = null
                                },
                                label = { Text("URL HTTPS do APK", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )

                            // Testar URL
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (customApkUrl.isNotBlank()) {
                                            isTestingUrl = true
                                            scope.launch(Dispatchers.IO) {
                                                val res = UpdateManager.verifyApkUrl(customApkUrl.trim())
                                                withContext(Dispatchers.Main) {
                                                    isTestingUrl = false
                                                    urlTestResult = if (res.isValid) {
                                                        Pair(true, "✓ URL acessível! HTTP ${res.httpStatusCode}")
                                                    } else {
                                                        Pair(false, "✗ Falha: ${res.message}")
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = customApkUrl.isNotBlank() && !isTestingUrl,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    if (isTestingUrl) {
                                        CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text("TESTAR URL", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            urlTestResult?.let { (success, message) ->
                                Surface(
                                    color = if (success) Color(0xFF1B5E20).copy(alpha = 0.2f) else BrandRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = message,
                                        color = if (success) Color(0xFF81C784) else Color(0xFFFF8A80),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = customVersionCodeText,
                                onValueChange = { if (it.all { c -> c.isDigit() }) customVersionCodeText = it },
                                label = { Text("Version Code (Build)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )

                            OutlinedTextField(
                                value = customSha256,
                                onValueChange = { customSha256 = it },
                                label = { Text("Hash SHA-256", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )

                            OutlinedTextField(
                                value = minVersionCodeText,
                                onValueChange = { if (it.all { c -> c.isDigit() }) minVersionCodeText = it },
                                label = { Text("Versão Mínima Suportada (Build)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("STATUS:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { isPublished = true }
                                ) {
                                    RadioButton(
                                        selected = isPublished,
                                        onClick = { isPublished = true },
                                        colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                                    )
                                    Text("PUBLICADA", color = if (isPublished) Color.White else Color.Gray, fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { isPublished = false }
                                ) {
                                    RadioButton(
                                        selected = !isPublished,
                                        onClick = { isPublished = false },
                                        colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                                    )
                                    Text("RASCUNHO", color = if (!isPublished) Color.White else Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    if (validationError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = BrandRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(validationError, color = Color(0xFFFF8A80), fontSize = 10.sp)
                            }
                        }
                    }

                    if (uploadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = BrandRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(uploadError!!, color = Color(0xFFFF8A80), fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Botão Publicar
                    Button(
                        onClick = { showConfirmPublishDialog = true },
                        enabled = isValidToSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed, disabledContainerColor = Color.DarkGray),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text(
                            text = if (isPublished) "PUBLICAR VERSÃO" else "SALVAR RASCUNHO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showConfirmPublishDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmPublishDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "PUBLICAR RONYCINE $versionName?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• APK: ${selectedApkFile?.name ?: "ronycine-$versionName.apk"}", color = Color.LightGray, fontSize = 12.sp)
                    Text("• Tamanho: ${apkMetadata?.fileSizeFormatted ?: "N/D"}", color = Color.LightGray, fontSize = 12.sp)
                    Text("• Build Code: $finalVersionCode", color = Color.LightGray, fontSize = 12.sp)
                    Text("• Status: ${if (isPublished) "PUBLICAR PARA DISPOSITIVOS" else "RASCUNHO"}", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ao confirmar, os dispositivos instalados poderão receber e baixar esta versão.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmPublishDialog = false
                        scope.launch {
                            isUploadingApk = true
                            uploadError = null
                            val uploadResult = if (selectedApkFile != null) {
                                adminViewModel.uploadApk(versionName.trim(), selectedApkFile!!)
                            } else {
                                Result.success(finalApkUrl)
                            }
                            
                            if (uploadResult.isSuccess) {
                                val realUrl = uploadResult.getOrThrow()
                                val newVersion = AppVersionEntity(
                                    id = "v_${versionName.replace(".", "_")}_$finalVersionCode",
                                    versionName = versionName.trim(),
                                    versionCode = finalVersionCode,
                                    packageName = apkMetadata?.packageName ?: context.packageName,
                                    apkUrl = realUrl,
                                    releaseNotes = releaseNotes.trim(),
                                    sha256 = finalSha256.trim(),
                                    fileSize = apkMetadata?.fileSizeFormatted ?: "",
                                    mandatory = isMandatory,
                                    published = isPublished,
                                    status = if (isPublished) "PUBLISHED" else "DRAFT",
                                    minimumVersionCode = minVersionCodeText.toIntOrNull() ?: 100,
                                    publishedAt = if (isPublished) System.currentTimeMillis() else 0L,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                adminViewModel.publishAppVersion(newVersion)
                                isUploadingApk = false
                                onPublished(newVersion)
                            } else {
                                isUploadingApk = false
                                uploadError = uploadResult.exceptionOrNull()?.message ?: "Falha ao enviar o APK para o Firebase Storage."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("CONFIRMAR PUBLICAÇÃO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmPublishDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun VersionDetailsDialog(
    version: AppVersionEntity,
    onDismiss: () -> Unit,
    onStatusChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isTestingUrl by remember { mutableStateOf(false) }
    var urlTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETALHES DA VERSÃO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• Nome da Versão: v${version.versionName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("• Version Code: ${version.versionCode}", color = Color.LightGray, fontSize = 11.sp)
                        Text("• Status: ${version.status}", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("• Obrigatória: ${if (version.mandatory) "Sim" else "Não"}", color = Color.LightGray, fontSize = 11.sp)
                        Text("• Tamanho: ${version.fileSize.ifBlank { "N/D" }}", color = Color.LightGray, fontSize = 11.sp)
                        Text("• URL: ${version.apkUrl}", color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (version.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notas da Versão:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = version.releaseNotes,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (version.apkUrl.isNotBlank()) {
                                isTestingUrl = true
                                scope.launch(Dispatchers.IO) {
                                    val res = UpdateManager.verifyApkUrl(version.apkUrl.trim())
                                    withContext(Dispatchers.Main) {
                                        isTestingUrl = false
                                        urlTestResult = if (res.isValid) {
                                            Pair(true, "✓ URL Válida: HTTP ${res.httpStatusCode}")
                                        } else {
                                            Pair(false, "✗ Falha: ${res.message}")
                                        }
                                    }
                                }
                            }
                        },
                        enabled = version.apkUrl.isNotBlank() && !isTestingUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        if (isTestingUrl) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("TESTAR URL DO APK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    urlTestResult?.let { (success, message) ->
                        Surface(
                            color = if (success) Color(0xFF1B5E20).copy(alpha = 0.25f) else BrandRed.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message,
                                color = if (success) Color(0xFF81C784) else Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }

                    if (version.status != "DEPRECATED") {
                        Button(
                            onClick = { onStatusChanged("DEPRECATED") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DEPRECAR VERSÃO (ROLLBACK)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onStatusChanged("PUBLISHED") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("REATIVAR VERSÃO (PUBLICAR)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                        border = BorderStroke(1.dp, BrandRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = BrandRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXCLUIR VERSÃO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Excluir Versão?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("A versão v${version.versionName} será excluída permanentemente.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("EXCLUIR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ActivateUpdateDialog(
    publishedVersions: List<AppVersionEntity>,
    initialVersion: AppVersionEntity? = null,
    onDismiss: () -> Unit,
    onActivate: (AppVersionEntity, Boolean) -> Unit
) {
    val validVersions = remember(publishedVersions) {
        publishedVersions.filter { it.hasConfiguredApk && it.status != "DEPRECATED" }
    }
    var selectedVersion by remember {
        mutableStateOf(initialVersion ?: validVersions.firstOrNull())
    }
    var isMandatory by remember {
        mutableStateOf(selectedVersion?.mandatory ?: false)
    }
    var showDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ATIVAR ATUALIZAÇÃO", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A versão selecionada passará a ser exigida/oferecida aos usuários.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("SELECIONE A VERSÃO A SER ATIVADA", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                Box {
                    Surface(
                        onClick = { showDropdown = true },
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedVersion != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("v${selectedVersion!!.versionName} (Build ${selectedVersion!!.versionCode})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("APK: ${selectedVersion!!.apkUrl.take(30)}...", color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                Text("Nenhuma versão com APK válido", color = Color.Gray, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = BrandRed)
                        }
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.background(Color(0xFF252525))
                    ) {
                        if (validVersions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Nenhuma versão com APK disponível", color = Color.Gray) },
                                onClick = { showDropdown = false }
                            )
                        } else {
                            validVersions.forEach { ver ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("v${ver.versionName} (Build ${ver.versionCode})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Status: ${ver.displayStatus}", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedVersion = ver
                                        isMandatory = ver.mandatory
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("MODALIDADE DA ATUALIZAÇÃO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isMandatory = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isMandatory,
                                onClick = { isMandatory = false },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Opcional", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Exibe diálogo permitindo usar o app normalmente.", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isMandatory = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isMandatory,
                                onClick = { isMandatory = true },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Obrigatória (Bloqueante)", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Bloqueia o app caso a versão instalada seja anterior.", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            selectedVersion?.let { ver ->
                                onActivate(ver, isMandatory)
                            }
                        },
                        enabled = selectedVersion != null && selectedVersion!!.hasConfiguredApk,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ATIVAR AGORA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeactivateUpdateDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("DESATIVAR ATUALIZAÇÕES?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Deseja desativar o sistema de atualização global?",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = "• Nenhum usuário receberá avisos ou pop-ups\n• Nenhuma tela de bloqueio obrigatório será exibida\n• O app continuará funcionando normalmente em todas as versões",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("DESATIVAR AGORA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    )
}

// =========================================================================
// ATUALIZAÇÃO REMOTA COMPOSABLES (CONTEÚDO & INTERFACE)
// =========================================================================
@Composable
private fun RemoteUpdateTabContent(
    remoteAppConfig: com.example.data.remote.RemoteAppConfigEntity,
    remoteUpdateHistory: List<com.example.data.remote.RemoteUpdateHistoryEntity>,
    changelogInput: String,
    onChangelogChange: (String) -> Unit,
    forceRefreshInput: Boolean,
    onForceRefreshChange: (Boolean) -> Unit,
    onPublishClick: () -> Unit,
    onCheckNowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card de Publicação Remota
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C38))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "ATUALIZAÇÃO REMOTA (CONTEÚDO & INTERFACE)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Versão Remota Ativa: v${remoteAppConfig.remoteVersion} (Build ${remoteAppConfig.build})",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E2D1E)
                    ) {
                        Text(
                            text = "● ATIVA",
                            color = Color(0xFF4CAF50),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Publique alterações de layout, Home, Explore, Banners, TMDB e configurações sem solicitar instalação de novo APK.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                HorizontalDivider(color = Color(0xFF26262E))

                OutlinedTextField(
                    value = changelogInput,
                    onValueChange = onChangelogChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    label = { Text("NOTAS DA PUBLICAÇÃO (CHANGELOG)", color = Color.Gray, fontSize = 11.sp) },
                    placeholder = { Text("Ex: - Nova organização da Explore\n- Pesquisa local da Home ativada\n- Ajustes de catálogo", color = Color.DarkGray, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0xFF333340),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF14141A),
                        unfocusedContainerColor = Color(0xFF14141A)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF14141A))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Forçar Atualização de Cache (forceRefresh)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Limpa o cache da interface nos dispositivos para garantir exibição imediata.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = forceRefreshInput,
                        onCheckedChange = onForceRefreshChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandRed
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPublishClick,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🚀 PUBLICAR ALTERAÇÕES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onCheckNowClick,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF333340))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("VERIFICAR AGORA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Histórico de Atualizações Remotas
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C38))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "HISTÓRICO DE ATUALIZAÇÕES REMOTAS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${remoteUpdateHistory.size} publicações",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = Color(0xFF26262E))

                if (remoteUpdateHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma atualização remota registrada ainda.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    remoteUpdateHistory.take(15).forEach { item ->
                        RemoteUpdateHistoryCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteUpdateHistoryCard(item: com.example.data.remote.RemoteUpdateHistoryEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF14141A),
        border = BorderStroke(1.dp, Color(0xFF22222B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "v${item.remoteVersion}",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Build ${item.build}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    if (item.forceRefresh) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BrandRed.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "CACHE REFRESH",
                                color = BrandRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = item.dateFormatted,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            if (item.changelog.isNotBlank()) {
                Text(
                    text = item.changelog,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Publicado por: ${item.publishedBy}",
                color = Color.DarkGray,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun PublishRemoteUpdateConfirmationDialog(
    currentVersion: String,
    changelog: String,
    forceRefresh: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C24),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF4CAF50))
                Text("PUBLICAR ALTERAÇÕES REMOTAS?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "As alterações de conteúdo e interface serão publicadas instantaneamente no servidor para todos os APKs instalados do RONYCINE.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF121218),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Versão Remota Atual: v$currentVersion", color = Color.Gray, fontSize = 11.sp)
                        if (changelog.isNotBlank()) {
                            Text("Changelog:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(changelog, color = Color.LightGray, fontSize = 11.sp)
                        }
                        if (forceRefresh) {
                            Text("✓ Invalidação forçada de cache ativada", color = BrandRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) {
                Text("PUBLICAR AGORA", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}
