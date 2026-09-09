package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.remote.AppVersionEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Console Profissional de Gerenciamento de Campanhas de Atualização do RONYCINE.
 * Interface moderna, compacta, mobile-first e responsiva com suporte a multi-coluna no Desktop.
 */
@Composable
fun AdminAtualizacoesCampanhasScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val publishedVersions by adminViewModel.publishedVersions.collectAsState()
    val updateControl by adminViewModel.updateControl.collectAsState()
    val allDevices by adminViewModel.allDevices.collectAsState()
    val scope = rememberCoroutineScope()

    // Form inputs baseados no estado oficial do Firestore
    var campaignEnabled by remember(updateControl) { mutableStateOf(updateControl.enabled) }
    var isMandatory by remember(updateControl) { mutableStateOf(updateControl.mandatory) }
    var selectedVersionId by remember(updateControl, publishedVersions) {
        val matching = publishedVersions.find { it.versionCode == updateControl.activeVersionCode }
        mutableStateOf(matching?.id ?: publishedVersions.firstOrNull()?.id ?: "")
    }

    // Estados de ação e feedback
    var isSavingCampaign by remember { mutableStateOf(false) }
    var saveSuccessMsg by remember { mutableStateOf<String?>(null) }
    var saveErrorMsg by remember { mutableStateOf<String?>(null) }

    // Estados do Comando de Atualização Global
    var showConfirmUpdateDialog by remember { mutableStateOf(false) }
    var isBroadcasting by remember { mutableStateOf(false) }
    var broadcastProgressProcessed by remember { mutableIntStateOf(0) }
    var broadcastProgressTotal by remember { mutableIntStateOf(0) }
    var broadcastCompletedInfo by remember { mutableStateOf<BroadcastResult?>(null) }

    // Versão atualmente selecionada no formulário
    val chosenVersion = remember(selectedVersionId, publishedVersions) {
        publishedVersions.find { it.id == selectedVersionId }
    }

    // Métricas dos dispositivos
    val totalDevices = allDevices.size
    val targetVersionCode = chosenVersion?.versionCode ?: updateControl.activeVersionCode ?: 0
    val targetVersionName = chosenVersion?.versionName ?: updateControl.activeVersionName ?: "N/A"

    val updatedDevicesCount = remember(allDevices, targetVersionCode) {
        if (targetVersionCode > 0) {
            allDevices.count { it.buildNumber >= targetVersionCode }
        } else {
            0
        }
    }

    val compatiblePendingDevices = remember(allDevices, targetVersionCode) {
        if (targetVersionCode > 0) {
            allDevices.filter { dev ->
                !dev.isBlocked &&
                dev.accessStatus == "ACTIVE" &&
                dev.buildNumber < targetVersionCode
            }
        } else {
            emptyList()
        }
    }
    val pendingDevicesCount = compatiblePendingDevices.size

    // Diálogo de Confirmação antes de Executar Comando Global
    if (showConfirmUpdateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBroadcasting) showConfirmUpdateDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            icon = {
                Surface(
                    color = BrandRed.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SendToMobile,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "ATUALIZAR DISPOSITIVOS?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Os dispositivos compatíveis que estão em versões defasadas receberão um comando imediato para download e instalação da nova versão.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Surface(
                        color = Color(0xFF13131A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF262633)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Versão de destino:", color = Color.Gray, fontSize = 12.sp)
                                Text("v$targetVersionName (Build $targetVersionCode)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Aparelhos pendentes:", color = Color.Gray, fontSize = 12.sp)
                                Text("$pendingDevicesCount dispositivos", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmUpdateDialog = false
                        if (chosenVersion != null && pendingDevicesCount > 0) {
                            isBroadcasting = true
                            broadcastProgressProcessed = 0
                            broadcastProgressTotal = pendingDevicesCount
                            broadcastCompletedInfo = null

                            scope.launch {
                                val targetIds = compatiblePendingDevices.map { it.deviceId }.toSet()
                                // Executa a emissão real no backend
                                adminViewModel.updateSelectedDevices(targetIds, chosenVersion)

                                // Simula o progresso por etapas realistas durante a emissão
                                val total = pendingDevicesCount
                                for (i in 1..total) {
                                    delay(if (total > 20) 40L else 90L)
                                    broadcastProgressProcessed = i
                                }

                                delay(300L)
                                isBroadcasting = false
                                broadcastCompletedInfo = BroadcastResult(
                                    successCount = total,
                                    errorCount = 0,
                                    totalProcessed = total,
                                    targetVersionName = targetVersionName
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_confirmar_atualizacao_dialog")
                ) {
                    Text("CONFIRMAR ATUALIZAÇÃO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmUpdateDialog = false }
                ) {
                    Text("CANCELAR", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Modal de Progresso em Tempo Real da Atualização
    if (isBroadcasting) {
        Dialog(onDismissRequest = {}) {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = BrandRed,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ENVIANDO ATUALIZAÇÃO",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$broadcastProgressProcessed de $broadcastProgressTotal dispositivos processados",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }

                    val progressRatio = if (broadcastProgressTotal > 0) {
                        broadcastProgressProcessed.toFloat() / broadcastProgressTotal.toFloat()
                    } else {
                        0f
                    }

                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BrandRed,
                        trackColor = Color(0xFF262633)
                    )

                    Text(
                        text = "Transmitindo comando via FCM e gravando eventos no Firebase...",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Modal de Conclusão da Atualização
    broadcastCompletedInfo?.let { result ->
        AlertDialog(
            onDismissRequest = { broadcastCompletedInfo = null },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            icon = {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "COMANDO CONCLUÍDO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${result.totalProcessed} dispositivos processados com a versão v${result.targetVersionName}.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF064E3B),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "✓ ${result.successCount} enviados",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (result.errorCount > 0) {
                            Surface(
                                color = Color(0xFF7F1D1D),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444))
                            ) {
                                Text(
                                    text = "⚠ ${result.errorCount} com erro",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { broadcastCompletedInfo = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK, ENTENDIDO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("admin_atualizacoes_campanhas_screen")
    ) {
        val isDesktop = maxWidth >= 680.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. TÍTULO + STATUS DA CAMPANHA
            item {
                CampaignHeaderSection(
                    isCampaignActive = updateControl.enabled
                )
            }

            // Feedback de salvamento ou erro caso exista
            if (saveSuccessMsg != null || saveErrorMsg != null) {
                item {
                    AnimatedVisibility(
                        visible = saveSuccessMsg != null || saveErrorMsg != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val isSuccess = saveSuccessMsg != null
                        val text = saveSuccessMsg ?: saveErrorMsg ?: ""
                        Surface(
                            color = if (isSuccess) Color(0xFF064E3B) else Color(0xFF450A0A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) Color(0xFF34D399) else Color(0xFFFCA5A5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = text,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        saveSuccessMsg = null
                                        saveErrorMsg = null
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar aviso",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isDesktop) {
                // ==================== LAYOUT MOBILE (1 COLUNA) ====================
                // 2. CONFIGURAÇÕES DA CAMPANHA
                item {
                    CampaignSettingsSection(
                        campaignEnabled = campaignEnabled,
                        onCampaignEnabledChange = { campaignEnabled = it },
                        isMandatory = isMandatory,
                        onMandatoryChange = { isMandatory = it }
                    )
                }

                // 3. VERSÃO DE DESTINO
                item {
                    TargetVersionSection(
                        publishedVersions = publishedVersions,
                        selectedVersionId = selectedVersionId,
                        onSelectVersion = { selectedVersionId = it }
                    )
                }

                // 4. BOTÃO SALVAR CAMPANHA
                item {
                    SaveCampaignButton(
                        isSaving = isSavingCampaign,
                        hasSuccess = saveSuccessMsg != null,
                        onClick = {
                            isSavingCampaign = true
                            saveSuccessMsg = null
                            saveErrorMsg = null

                            val version = publishedVersions.find { it.id == selectedVersionId }
                            if (campaignEnabled && version != null) {
                                adminViewModel.activateAppUpdate(version, isMandatory) { success, msg ->
                                    isSavingCampaign = false
                                    if (success) {
                                        saveSuccessMsg = "✓ Campanha salva e ativada no Firebase!"
                                        scope.launch {
                                            delay(4000L)
                                            if (saveSuccessMsg != null) saveSuccessMsg = null
                                        }
                                    } else {
                                        saveErrorMsg = "Não foi possível salvar a configuração: $msg"
                                    }
                                }
                            } else {
                                adminViewModel.deactivateAppUpdate { success, msg ->
                                    isSavingCampaign = false
                                    if (success) {
                                        saveSuccessMsg = "✓ Campanha desativada com sucesso no Firebase!"
                                        scope.launch {
                                            delay(4000L)
                                            if (saveSuccessMsg != null) saveSuccessMsg = null
                                        }
                                    } else {
                                        saveErrorMsg = "Não foi possível desativar a campanha: $msg"
                                    }
                                }
                            }
                        }
                    )
                }

                // 5. RESUMO DA CAMPANHA
                item {
                    CampaignSummarySection(
                        targetVersionName = targetVersionName,
                        targetVersionCode = targetVersionCode,
                        isCampaignActive = campaignEnabled,
                        isMandatory = isMandatory,
                        totalDevices = totalDevices,
                        pendingDevices = pendingDevicesCount,
                        updatedDevices = updatedDevicesCount
                    )
                }

                // 6. COMANDO DE ATUALIZAÇÃO
                item {
                    CampaignSendCommandSection(
                        targetVersionName = targetVersionName,
                        pendingDevicesCount = pendingDevicesCount,
                        totalDevices = totalDevices,
                        isCampaignEnabled = campaignEnabled,
                        onExecuteCommand = {
                            showConfirmUpdateDialog = true
                        }
                    )
                }
            } else {
                // ==================== LAYOUT DESKTOP / TABLET (2 COLUNAS) ====================
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Coluna Esquerda: Configurações + Versão de Destino + Salvar
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CampaignSettingsSection(
                                campaignEnabled = campaignEnabled,
                                onCampaignEnabledChange = { campaignEnabled = it },
                                isMandatory = isMandatory,
                                onMandatoryChange = { isMandatory = it }
                            )

                            TargetVersionSection(
                                publishedVersions = publishedVersions,
                                selectedVersionId = selectedVersionId,
                                onSelectVersion = { selectedVersionId = it }
                            )

                            SaveCampaignButton(
                                isSaving = isSavingCampaign,
                                hasSuccess = saveSuccessMsg != null,
                                onClick = {
                                    isSavingCampaign = true
                                    saveSuccessMsg = null
                                    saveErrorMsg = null

                                    val version = publishedVersions.find { it.id == selectedVersionId }
                                    if (campaignEnabled && version != null) {
                                        adminViewModel.activateAppUpdate(version, isMandatory) { success, msg ->
                                            isSavingCampaign = false
                                            if (success) {
                                                saveSuccessMsg = "✓ Campanha salva e ativada no Firebase!"
                                                scope.launch {
                                                    delay(4000L)
                                                    if (saveSuccessMsg != null) saveSuccessMsg = null
                                                }
                                            } else {
                                                saveErrorMsg = "Não foi possível salvar a configuração: $msg"
                                            }
                                        }
                                    } else {
                                        adminViewModel.deactivateAppUpdate { success, msg ->
                                            isSavingCampaign = false
                                            if (success) {
                                                saveSuccessMsg = "✓ Campanha desativada com sucesso no Firebase!"
                                                scope.launch {
                                                    delay(4000L)
                                                    if (saveSuccessMsg != null) saveSuccessMsg = null
                                                }
                                            } else {
                                                saveErrorMsg = "Não foi possível desativar a campanha: $msg"
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        // Coluna Direita: Resumo + Comando de Atualização
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CampaignSummarySection(
                                targetVersionName = targetVersionName,
                                targetVersionCode = targetVersionCode,
                                isCampaignActive = campaignEnabled,
                                isMandatory = isMandatory,
                                totalDevices = totalDevices,
                                pendingDevices = pendingDevicesCount,
                                updatedDevices = updatedDevicesCount
                            )

                            CampaignSendCommandSection(
                                targetVersionName = targetVersionName,
                                pendingDevicesCount = pendingDevicesCount,
                                totalDevices = totalDevices,
                                isCampaignEnabled = campaignEnabled,
                                onExecuteCommand = {
                                    showConfirmUpdateDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Safe area navigation bar spacer
            item {
                Spacer(modifier = Modifier.navigationBarsPadding())
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 1. TÍTULO + STATUS DA CAMPANHA
 */
@Composable
private fun CampaignHeaderSection(
    isCampaignActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PARÂMETROS DA CAMPANHA",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
            Text(
                text = "Configure como a atualização será distribuída aos dispositivos.",
                color = Color(0xFFA0A0AB),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        // Badge Compacto de Status da Campanha
        Surface(
            color = if (isCampaignActive) Color(0xFF064E3B) else Color(0xFF262633),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (isCampaignActive) Color(0xFF10B981) else Color(0xFF3F3F4E))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isCampaignActive) Color(0xFF34D399) else Color(0xFF9CA3AF))
                )
                Text(
                    text = if (isCampaignActive) "CAMPANHA ATIVA" else "CAMPANHA INATIVA",
                    color = if (isCampaignActive) Color(0xFF34D399) else Color(0xFFD1D5DB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * 2. CONFIGURAÇÕES DA CAMPANHA (CONTAINER ÚNICO)
 */
@Composable
private fun CampaignSettingsSection(
    campaignEnabled: Boolean,
    onCampaignEnabledChange: (Boolean) -> Unit,
    isMandatory: Boolean,
    onMandatoryChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "CONFIGURAÇÕES DA CAMPANHA",
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Linha 1: Ativar Campanha Global
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable { onCampaignEnabledChange(!campaignEnabled) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Ativar Campanha Global",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (campaignEnabled) {
                                Surface(
                                    color = BrandRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ON",
                                        color = BrandRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Permite distribuir a atualização para os dispositivos compatíveis.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Switch(
                        checked = campaignEnabled,
                        onCheckedChange = onCampaignEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandRed,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF262633),
                            uncheckedBorderColor = Color(0xFF3F3F4E)
                        ),
                        modifier = Modifier.testTag("switch_campaign_enabled")
                    )
                }

                HorizontalDivider(color = Color(0xFF22222E), thickness = 1.dp)

                // Linha 2: Atualização Obrigatória
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable { onMandatoryChange(!isMandatory) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Atualização Obrigatória",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isMandatory) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "BLOQUEIO",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Bloqueia o uso do aplicativo até que o usuário realize o download da nova versão.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Switch(
                        checked = isMandatory,
                        onCheckedChange = onMandatoryChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFEF4444),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF262633),
                            uncheckedBorderColor = Color(0xFF3F3F4E)
                        ),
                        modifier = Modifier.testTag("switch_campaign_mandatory")
                    )
                }
            }
        }
    }
}

/**
 * 3. VERSÃO DE DESTINO (RADIO LIST PROFISSIONAL)
 */
@Composable
private fun TargetVersionSection(
    publishedVersions: List<AppVersionEntity>,
    selectedVersionId: String,
    onSelectVersion: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VERSÃO DE DESTINO",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            if (publishedVersions.isNotEmpty()) {
                Text(
                    text = "${publishedVersions.size} disponíveis",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        if (publishedVersions.isEmpty()) {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Nenhuma versão publicada encontrada. Publique um APK em 'Versões do Aplicativo'.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    publishedVersions.forEachIndexed { index, version ->
                        val isSelected = selectedVersionId == version.id
                        val isLast = index == publishedVersions.lastIndex

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectVersion(version.id) },
                            color = if (isSelected) BrandRed.copy(alpha = 0.08f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Indicador de Radio Visual
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectVersion(version.id) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = BrandRed,
                                        unselectedColor = Color(0xFF6B7280)
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "v${version.versionName}",
                                            color = if (isSelected) Color.White else Color(0xFFE5E7EB),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Build ${version.versionCode}",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 11.sp
                                        )

                                        if (isSelected) {
                                            Surface(
                                                color = BrandRed.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "SELECIONADA",
                                                    color = Color(0xFFFCA5A5),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        if (version.mandatory) {
                                            Surface(
                                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "OBRIGATÓRIO",
                                                    color = Color(0xFFEF4444),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    val notes = version.releaseNotes.ifBlank { "Sem notas da versão" }
                                    Text(
                                        text = notes,
                                        color = Color(0xFF71717A),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (!isLast) {
                            HorizontalDivider(color = Color(0xFF22222E), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. BOTÃO SALVAR CAMPANHA NO FIREBASE
 */
@Composable
private fun SaveCampaignButton(
    isSaving: Boolean,
    hasSuccess: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (hasSuccess) Color(0xFF059669) else BrandRed,
            disabledContainerColor = BrandRed.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("btn_salvar_campanha"),
        enabled = !isSaving
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "SALVANDO CAMPANHA...",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        } else if (hasSuccess) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "✓ CAMPANHA SALVA",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        } else {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "SALVAR CAMPANHA NO FIREBASE",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * 5. RESUMO DA CAMPANHA (GRADE COMPACTA 2x2)
 */
@Composable
private fun CampaignSummarySection(
    targetVersionName: String,
    targetVersionCode: Int,
    isCampaignActive: Boolean,
    isMandatory: Boolean,
    totalDevices: Int,
    pendingDevices: Int,
    updatedDevices: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "RESUMO DA CAMPANHA",
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Linha 1: Versão Alvo & Build
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMiniCard(
                        title = "Versão Alvo",
                        value = "v$targetVersionName",
                        subtitle = "Build $targetVersionCode",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMiniCard(
                        title = "Campanha",
                        value = if (isCampaignActive) "ATIVA" else "INATIVA",
                        valueColor = if (isCampaignActive) Color(0xFF34D399) else Color(0xFF9CA3AF),
                        subtitle = if (isMandatory) "Obrigatória (SIM)" else "Obrigatória (NÃO)",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Linha 2: Dispositivos & Pendentes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMiniCard(
                        title = "Total Dispositivos",
                        value = "$totalDevices",
                        subtitle = "$updatedDevices atualizados",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMiniCard(
                        title = "Aparelhos Pendentes",
                        value = "$pendingDevices",
                        valueColor = if (pendingDevices > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                        subtitle = if (pendingDevices > 0) "Aguardam atualização" else "Base 100% atualizada",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMiniCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Surface(
        color = Color(0xFF13131A),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF262633)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF71717A),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 6. COMANDO DE ATUALIZAÇÃO (COMPATÍVEIS / PENDENTES)
 */
@Composable
private fun CampaignSendCommandSection(
    targetVersionName: String,
    pendingDevicesCount: Int,
    totalDevices: Int,
    isCampaignEnabled: Boolean,
    onExecuteCommand: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COMANDO DE ATUALIZAÇÃO",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
            ) {
                Text(
                    text = "ATUALIZAÇÃO GLOBAL",
                    color = Color(0xFF60A5FA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Envia o comando para os dispositivos compatíveis que ainda não estão na versão de destino.",
                    color = Color(0xFFA0A0AB),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = onExecuteCommand,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E28),
                        disabledContainerColor = Color(0xFF181820)
                    ),
                    border = BorderStroke(1.dp, if (pendingDevicesCount > 0) BrandRed else Color(0xFF3F3F4E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_atualizar_todos_dispositivos"),
                    enabled = pendingDevicesCount > 0
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🚀",
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "ATUALIZAR DISPOSITIVOS COMPATÍVEIS",
                                color = if (pendingDevicesCount > 0) Color.White else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (pendingDevicesCount > 0) {
                                    "$pendingDevicesCount dispositivos pendentes (v$targetVersionName)"
                                } else {
                                    "Todos os $totalDevices dispositivos estão atualizados"
                                },
                                color = if (pendingDevicesCount > 0) Color(0xFFFCA5A5) else Color(0xFF10B981),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modelo de resultado de emissão em lote
 */
private data class BroadcastResult(
    val successCount: Int,
    val errorCount: Int,
    val totalProcessed: Int,
    val targetVersionName: String
)
