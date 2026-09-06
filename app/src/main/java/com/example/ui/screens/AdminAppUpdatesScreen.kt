package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Cabeçalho Principal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GERENCIAMENTO DE VERSÕES",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ATUALIZAÇÕES DO RONYCINE",
                        color = BrandRed,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showPublishDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PUBLICAR NOVA VERSÃO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { adminViewModel.refreshDevices() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // SEÇÃO 1: CONTROLE DA ATUALIZAÇÃO (GLOBAL)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (updateControl.enabled) Color(0xFF221114) else Color(0xFF141E16)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (updateControl.enabled) BrandRed.copy(alpha = 0.6f) else Color(0xFF2E7D32).copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (updateControl.enabled) BrandRed else Color(0xFF2E7D32),
                                shape = CircleShape,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONTROLE DA ATUALIZAÇÃO",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Surface(
                            color = if (updateControl.enabled) BrandRed.copy(alpha = 0.2f) else Color(0xFF2E7D32).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (updateControl.enabled) "ATUALIZAÇÃO ATIVA" else "NENHUMA ATUALIZAÇÃO ATIVA",
                                color = if (updateControl.enabled) Color(0xFFFF8A80) else Color(0xFF81C784),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!updateControl.enabled) {
                        Text(
                            text = "Por padrão seguro: o aplicativo NÃO exibe telas de atualização, diálogos de download ou bloqueios para nenhum usuário.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showActivateUpdateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ATIVAR UMA ATUALIZAÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Versão Ativa: v${updateControl.activeVersionName ?: ""} (Build ${updateControl.activeVersionCode}) • Modalidade: ${if (updateControl.mandatory) "Obrigatória (Bloqueante)" else "Opcional (Aviso)"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (updateControl.apkUrl.isNotBlank()) {
                            Text(
                                text = "APK: ${updateControl.apkUrl.take(45)}...",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (updateControl.activatedAt != null) {
                            Text(
                                text = "Ativado em: ${updateControl.dateActivatedFormatted} por ${updateControl.activatedBy ?: "Admin"}",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showDeactivateUpdateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DESATIVAR ATUALIZAÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showActivateUpdateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ALTERAR VERSÃO ATIVA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cards de Métricas do Sistema
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Versão Instalada
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VERSÃO INSTALADA", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("v$installedVersionName", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Build $installedVersionCode", color = Color.Gray, fontSize = 11.sp)
                    }

                    // Última Publicada
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ÚLTIMA NA NUVEM", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        val latest = activeVersions.firstOrNull()
                        if (latest != null) {
                            Text("v${latest.versionName}", color = BrandRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Build ${latest.versionCode}", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            Text("Nenhuma", color = Color.Gray, fontSize = 13.sp)
                        }
                    }

                    // Total Dispositivos
                    Column(modifier = Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DISPOSITIVOS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${allDevices.size}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${allDevices.count { it.isOnline }} online", color = Color(0xFF4CAF50), fontSize = 11.sp)
                    }

                    // Status Atualizados vs Pendentes
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("ATUALIZADOS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$updatedCount / ${allDevices.size}", color = Color(0xFF4CAF50), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("$pendingCount pendentes", color = if (pendingCount > 0) BrandRed else Color.Gray, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Abas de Navegação
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = BrandRed
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SendToMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DISTRIBUIÇÃO (${allDevices.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("HISTÓRICO (${publishedVersions.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AUDITORIA (${updateEvents.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Conteúdo da Aba Selecionada
            when (selectedTab) {
                0 -> DistributionTabContent(
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
                    onUpdateAllCompatible = { showConfirmUpdateAllDialog = true },
                    onUpdateSelected = { showConfirmUpdateSelectedDialog = true },
                    onUpdateIndividualDevice = { dev, targetVer ->
                        adminViewModel.updateSelectedDevices(setOf(dev.deviceId), targetVer)
                        scope.launch {
                            snackbarHostState.showSnackbar("Atualização enviada para ${dev.name.ifBlank { dev.model }}")
                        }
                    }
                )

                1 -> VersionHistoryTabContent(
                    versions = publishedVersions,
                    updateControl = updateControl,
                    onViewDetails = { versionDetailModal = it },
                    onPublishNew = { showPublishDialog = true },
                    onActivateVersion = { ver ->
                        selectedVersionForActivation = ver
                        showActivateUpdateDialog = true
                    }
                )

                2 -> UpdateAuditTabContent(
                    events = updateEvents
                )
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

// ---------------------------------------------------------
// ABA 1: DISTRIBUIÇÃO POR DISPOSITIVO
// ---------------------------------------------------------
@Composable
fun DistributionTabContent(
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
    onUpdateAllCompatible: () -> Unit,
    onUpdateSelected: () -> Unit,
    onUpdateIndividualDevice: (DeviceEntity, AppVersionEntity) -> Unit
) {
    var showVersionPickerDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Linha com Seletor de Versão de Destino e Ações
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Dropdown de Versão Alvo
            Box(modifier = Modifier.weight(1.3f)) {
                Surface(
                    onClick = { showVersionPickerDropdown = true },
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selectedTargetVersion != null) BrandRed else Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("VERSÃO DE DESTINO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (selectedTargetVersion != null) "v${selectedTargetVersion.versionName} (${selectedTargetVersion.versionCode})" else "Nenhuma versão ativa",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = BrandRed)
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
                                        Text("v${ver.versionName} (Build ${ver.versionCode})", color = Color.White, fontWeight = FontWeight.Bold)
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

            // Botão: Atualizar Todos os Compatíveis
            Button(
                onClick = onUpdateAllCompatible,
                enabled = selectedTargetVersion != null && allDevices.any { it.buildNumber < (selectedTargetVersion.versionCode) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed, disabledContainerColor = Color.DarkGray),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.7f)
            ) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ATUALIZAR COMPATÍVEIS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filtros e Ações em Massa
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Chips de Filtro
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Pendentes", "Online", "Offline").forEach { f ->
                    val isSelected = filterMode == f
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterChange(f) },
                        label = { Text(f, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandRed,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = Color.LightGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) BrandRed else Color.Transparent
                        )
                    )
                }
            }

            // Ação em massa com selecionados
            if (selectedDeviceIds.isNotEmpty() && selectedTargetVersion != null) {
                Button(
                    onClick = onUpdateSelected,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("ENVIAR P/ ${selectedDeviceIds.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Barra de Selecionar Todos
        Surface(
            color = Color(0xFF141414),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val allFilteredIds = filteredDevices.map { it.deviceId }.toSet()
                    val isAllSelected = allFilteredIds.isNotEmpty() && selectedDeviceIds.containsAll(allFilteredIds)
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { onSelectAllFiltered() },
                        colors = CheckboxDefaults.colors(checkedColor = BrandRed)
                    )
                    Text("Selecionar filtrados (${filteredDevices.size})", color = Color.Gray, fontSize = 12.sp)
                }

                Text(
                    text = "${filteredDevices.size} dispositivos listados",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lista de Dispositivos
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum dispositivo encontrado neste filtro.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredDevices, key = { it.deviceId }) { dev ->
                    val isSelected = selectedDeviceIds.contains(dev.deviceId)
                    val targetCode = selectedTargetVersion?.versionCode ?: 0
                    val isUpdated = targetCode > 0 && dev.buildNumber >= targetCode

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF241414) else DarkSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDeviceSelection(dev.deviceId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleDeviceSelection(dev.deviceId) },
                                colors = CheckboxDefaults.colors(checkedColor = BrandRed)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Status Dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (dev.isOnline) Color(0xFF4CAF50) else Color.Gray, CircleShape)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            // Device Info
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dev.name.ifBlank { dev.model },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (dev.isBlocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = BrandRed.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("BLOQUEADO", color = BrandRed, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Versão: v${dev.appVersion} (${dev.buildNumber})",
                                        color = if (isUpdated) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(" • ID: ${dev.deviceId.take(8)}...", color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            // Ação Individual
                            if (selectedTargetVersion != null) {
                                if (isUpdated) {
                                    Surface(
                                        color = Color(0xFF1B5E20).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ATUALIZADO", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onUpdateIndividualDevice(dev, selectedTargetVersion) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ATUALIZAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

// ---------------------------------------------------------
// ABA 2: HISTÓRICO DE VERSÕES
// ---------------------------------------------------------
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
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Nenhuma versão publicada até o momento.", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPublishNew,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("PUBLICAR PRIMEIRA VERSÃO", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(versions, key = { it.id }) { ver ->
                val dateStr = remember(ver.createdAt) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ver.createdAt))
                }
                val isCurrentlyActive = updateControl.enabled && updateControl.activeVersionCode == ver.versionCode

                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isCurrentlyActive) Color(0xFF231416) else DarkSurface),
                    border = if (isCurrentlyActive) BorderStroke(1.dp, BrandRed) else null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "v${ver.versionName}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color.DarkGray,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Build ${ver.versionCode}",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (isCurrentlyActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = BrandRed,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("★ ATIVA GLOBAL", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                } else if (ver.mandatory) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = BrandRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("OBRIGATÓRIA", color = BrandRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (ver.releaseNotes.isNotBlank()) {
                            Text(
                                text = ver.releaseNotes,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Publicada em: $dateStr", color = Color.Gray, fontSize = 11.sp)
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (ver.hasConfiguredApk && ver.status != "DEPRECATED" && !isCurrentlyActive) {
                                    Button(
                                        onClick = { onActivateVersion(ver) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ATIVAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { onViewDetails(ver) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp), tint = BrandRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GERENCIAR", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// ABA 3: AUDITORIA DE ATUALIZAÇÕES
// ---------------------------------------------------------
@Composable
fun UpdateAuditTabContent(
    events: List<UpdateEventEntity>
) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Nenhum evento de atualização registrado recentemente.", color = Color.Gray, fontSize = 13.sp)
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ev.deviceName.ifBlank { "Dispositivo ${ev.deviceId.take(8)}" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "v${ev.fromVersion} ➔ v${ev.toVersion}",
                                    color = BrandRed,
                                    fontSize = 12.sp,
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
                                Text("Erro: ${ev.error}", color = Color(0xFFEF9A9A), fontSize = 10.sp)
                            }
                        }

                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = ev.status,
                                color = badgeFg,
                                fontSize = 10.sp,
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

// ---------------------------------------------------------
// DIÁLOGO: PUBLICAR NOVA VERSÃO (LIMPO E SEM FORMULÁRIOS GIGANTES)
// ---------------------------------------------------------
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

    // Validações
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            if (isUploadingApk) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("ENVIANDO APK PARA O STORAGE REAL...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Calculando SHA-256 e tamanho real...", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "PUBLICAR NO RONYCINE",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Área do APK: Selecionar APK
                Surface(
                    onClick = { apkFileLauncher.launch("application/vnd.android.package-archive") },
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(10.dp),
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
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (apkMetadata?.isValid == true) Color(0xFF1B5E20) else Color(0xFF2C2C2C),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProcessingApk) {
                                CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = if (apkMetadata?.isValid == true) Icons.Default.Check else Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = if (apkMetadata?.isValid == true) Color(0xFF81C784) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedApkFile != null) "APK SELECIONADO" else "SELECIONAR ARQUIVO APK",
                                color = if (apkMetadata?.isValid == true) Color(0xFF81C784) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (selectedApkFile != null) selectedApkFile!!.name else "Clique para carregar e inspecionar o novo APK",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { apkFileLauncher.launch("application/vnd.android.package-archive") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("PROCURAR", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }

                // Resultado da Inspeção do APK (SHA-256 e Metadados automáticos)
                apkMetadata?.let { meta ->
                    Spacer(modifier = Modifier.height(10.dp))
                    if (meta.isValid) {
                        Surface(
                            color = Color(0xFF1B5E20).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("APK Válido e Compatível", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tamanho: ${meta.fileSizeFormatted} • Build: ${meta.versionCode}", color = Color.LightGray, fontSize = 11.sp)
                                Text("SHA-256: ${meta.sha256.take(16)}... (calculado)", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    } else {
                        Surface(
                            color = BrandRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(meta.errorMessage ?: "APK inválido.", color = Color(0xFFFF8A80), fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Nome da Versão
                OutlinedTextField(
                    value = versionName,
                    onValueChange = { versionName = it },
                    label = { Text("Nome da Versão (ex: 1.4.0)") },
                    placeholder = { Text("1.4.0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notas da Versão
                OutlinedTextField(
                    value = releaseNotes,
                    onValueChange = { releaseNotes = it },
                    label = { Text("Notas da Versão") },
                    placeholder = { Text("O que mudou nesta versão? (ex: Correções de players, novos recursos...)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Checkbox: Atualização Obrigatória
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isMandatory = !isMandatory }
                ) {
                    Checkbox(
                        checked = isMandatory,
                        onCheckedChange = { isMandatory = it },
                        colors = CheckboxDefaults.colors(checkedColor = BrandRed)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Atualização Obrigatória", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Impede o uso do app em versões antigas até atualizarem", color = Color.Gray, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Seção de Opções Avançadas (Colapsável)
                Surface(
                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OPÇÕES AVANÇADAS", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }

                AnimatedVisibility(visible = showAdvancedOptions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // URL HTTPS do APK
                        OutlinedTextField(
                            value = customApkUrl,
                            onValueChange = {
                                customApkUrl = it
                                urlTestResult = null
                            },
                            label = { Text("URL HTTPS do APK") },
                            placeholder = { Text("https://ronycine.app/download/ronycine.apk") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )

                        // Botão Testar URL
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
                                                    Pair(true, "✓ URL acessível! HTTP ${res.httpStatusCode}, Tamanho: ${res.fileSizeFormatted}, Tipo: ${res.contentType}")
                                                } else {
                                                    Pair(false, "✗ Falha: ${res.message} (HTTP ${res.httpStatusCode})")
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = customApkUrl.isNotBlank() && !isTestingUrl,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (isTestingUrl) {
                                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("TESTAR URL DO APK", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        urlTestResult?.let { (success, message) ->
                            Surface(
                                color = if (success) Color(0xFF1B5E20).copy(alpha = 0.2f) else BrandRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = message,
                                    color = if (success) Color(0xFF81C784) else Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Version Code
                        OutlinedTextField(
                            value = customVersionCodeText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) customVersionCodeText = it },
                            label = { Text("Version Code (Build)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )

                        // SHA-256
                        OutlinedTextField(
                            value = customSha256,
                            onValueChange = { customSha256 = it },
                            label = { Text("Hash SHA-256") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )

                        // Min Version Code
                        OutlinedTextField(
                            value = minVersionCodeText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) minVersionCodeText = it },
                            label = { Text("Versão Mínima Suportada (Build)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )

                        // Status da Publicação
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("STATUS:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isPublished = true }
                            ) {
                                RadioButton(
                                    selected = isPublished,
                                    onClick = { isPublished = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                                )
                                Text("PUBLICADA", color = if (isPublished) Color.White else Color.Gray, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isPublished = false }
                            ) {
                                RadioButton(
                                    selected = !isPublished,
                                    onClick = { isPublished = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                                )
                                Text("RASCUNHO", color = if (!isPublished) Color.White else Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Surface(
                        color = BrandRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(validationError, color = Color(0xFFFF8A80), fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (uploadError != null) {
                    Surface(
                        color = BrandRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uploadError!!, color = Color(0xFFFF8A80), fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botão Publicar
                Button(
                    onClick = { showConfirmPublishDialog = true },
                    enabled = isValidToSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed, disabledContainerColor = Color.DarkGray),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPublished) "PUBLICAR VERSÃO" else "SALVAR RASCUNHO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
    }

    // Modal de Confirmação de Publicação
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• APK: ${selectedApkFile?.name ?: "ronycine-$versionName.apk"}", color = Color.LightGray, fontSize = 13.sp)
                    Text("• Tamanho: ${apkMetadata?.fileSizeFormatted ?: "N/D"}", color = Color.LightGray, fontSize = 13.sp)
                    Text("• Build Code: $finalVersionCode", color = Color.LightGray, fontSize = 13.sp)
                    Text("• Status: ${if (isPublished) "PUBLICAR PARA DISPOSITIVOS" else "RASCUNHO"}", color = BrandRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ao confirmar, os dispositivos instalados poderão receber e baixar esta versão atualizando o mesmo aplicativo.",
                        color = Color.Gray,
                        fontSize = 12.sp
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

// ---------------------------------------------------------
// DIÁLOGO: DETALHES E GERENCIAMENTO DE UMA VERSÃO ESPECÍFICA
// ---------------------------------------------------------
@Composable
fun VersionDetailsDialog(
    version: AppVersionEntity,
    onDismiss: () -> Unit,
    onStatusChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isTestingUrl by remember { mutableStateOf(false) }
    var urlTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
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
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ficha Técnica
                Surface(
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("• Nome da Versão: v${version.versionName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("• Version Code: ${version.versionCode}", color = Color.LightGray, fontSize = 12.sp)
                        Text("• Package ID: ${version.packageName.ifBlank { "com.aistudio.playfilmeplus.app" }}", color = Color.LightGray, fontSize = 12.sp)
                        Text("• Status: ${version.status}", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("• Obrigatória: ${if (version.mandatory) "Sim" else "Não"}", color = Color.LightGray, fontSize = 12.sp)
                        Text("• Tamanho: ${version.fileSize.ifBlank { "N/D" }}", color = Color.LightGray, fontSize = 12.sp)
                        Text("• URL: ${version.apkUrl}", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (version.sha256.isNotBlank()) {
                            Text("• SHA-256: ${version.sha256}", color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (version.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Notas da Versão:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = version.releaseNotes,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Ações da Versão (Deprecar / Rollback / Excluir / Testar)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (version.apkUrl.isNotBlank()) {
                                isTestingUrl = true
                                scope.launch(Dispatchers.IO) {
                                    val res = UpdateManager.verifyApkUrl(version.apkUrl.trim())
                                    withContext(Dispatchers.Main) {
                                        isTestingUrl = false
                                        urlTestResult = if (res.isValid) {
                                            Pair(true, "✓ URL Válida: HTTP ${res.httpStatusCode} | ${res.fileSizeFormatted} | ${res.contentType}")
                                        } else {
                                            Pair(false, "✗ Falha: ${res.message} (HTTP ${res.httpStatusCode})")
                                        }
                                    }
                                }
                            }
                        },
                        enabled = version.apkUrl.isNotBlank() && !isTestingUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingUrl) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("TESTAR ACESSO À URL DO APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    urlTestResult?.let { (success, message) ->
                        Surface(
                            color = if (success) Color(0xFF1B5E20).copy(alpha = 0.25f) else BrandRed.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message,
                                color = if (success) Color(0xFF81C784) else Color(0xFFFF8A80),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (version.status != "DEPRECATED") {
                        Button(
                            onClick = { onStatusChanged("DEPRECATED") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MARCAR COMO DEPRECATED (ROLLBACK)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onStatusChanged("PUBLISHED") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REATIVAR VERSÃO (PUBLICAR)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                        border = BorderStroke(1.dp, BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EXCLUIR VERSÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            text = { Text("A versão v${version.versionName} será excluída permanentemente. Deseja continuar?", color = Color.LightGray) },
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

// ---------------------------------------------------------
// MODAL: ATIVAR ATUALIZAÇÃO (CONTROLE MANUAL DO ADMIN)
// ---------------------------------------------------------
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
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("ATIVAR ATUALIZAÇÃO", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A versão selecionada passará a ser exigida/oferecida aos usuários do aplicativo.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("SELECIONE A VERSÃO A SER ATIVADA", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Box {
                    Surface(
                        onClick = { showDropdown = true },
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedVersion != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("v${selectedVersion!!.versionName} (Build ${selectedVersion!!.versionCode})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("APK: ${selectedVersion!!.apkUrl.take(35)}...", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                Text("Nenhuma versão com APK válido", color = Color.Gray, fontSize = 13.sp)
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
                                            Text("v${ver.versionName} (Build ${ver.versionCode})", color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("Status: ${ver.displayStatus}", color = Color.Gray, fontSize = 11.sp)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Tipo de Atualização: Opcional vs Obrigatória
                Text("MODALIDADE DA ATUALIZAÇÃO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Opcional", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Exibe diálogo com botão 'Depois', permitindo usar o app normalmente.", color = Color.Gray, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Obrigatória (Bloqueante)", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Bloqueia o app caso a versão instalada seja anterior à versão ativa.", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedVersion?.let { ver ->
                                onActivate(ver, isMandatory)
                            }
                        },
                        enabled = selectedVersion != null && selectedVersion!!.hasConfiguredApk,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ATIVAR AGORA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// MODAL: DESATIVAR ATUALIZAÇÃO (CONTROLE MANUAL DO ADMIN)
// ---------------------------------------------------------
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
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DESATIVAR ATUALIZAÇÕES?", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Deseja desativar o sistema de atualização global?",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ao desativar:\n• Nenhum usuário receberá avisos ou pop-ups de atualização\n• Nenhuma tela de bloqueio obrigatório será exibida\n• O aplicativo continuará funcionando normalmente em todas as versões",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("DESATIVAR AGORA", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    )
}
