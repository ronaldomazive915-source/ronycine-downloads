package com.example.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AppVersionEntity
import com.example.data.remote.UpdateControlEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAtualizacoesCampanhasScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val publishedVersions by adminViewModel.publishedVersions.collectAsState()
    val updateControl by adminViewModel.updateControl.collectAsState()
    val allDevices by adminViewModel.allDevices.collectAsState()
    val scope = rememberCoroutineScope()

    var isBroadcasting by remember { mutableStateOf(false) }
    var broadcastSuccessMsg by remember { mutableStateOf<String?>(null) }
    var isSavingCampaign by remember { mutableStateOf(false) }

    // Form inputs initialized from current updateControl
    var campaignEnabled by remember(updateControl) { mutableStateOf(updateControl.enabled) }
    var isMandatory by remember(updateControl) { mutableStateOf(updateControl.mandatory) }
    var selectedVersionId by remember(updateControl, publishedVersions) {
        val matching = publishedVersions.find { it.versionCode == updateControl.activeVersionCode }
        mutableStateOf(matching?.id ?: publishedVersions.firstOrNull()?.id ?: "")
    }

    // Devices metrics
    val totalDevices = allDevices.size
    val activeVersionCode = updateControl.activeVersionCode ?: 0
    val updatedDevicesCount = remember(allDevices, activeVersionCode) {
        allDevices.count { it.buildNumber >= activeVersionCode && activeVersionCode > 0 }
    }
    val pendingDevicesCount = remember(totalDevices, updatedDevicesCount) {
        maxOf(0, totalDevices - updatedDevicesCount)
    }
    val adoptionRate = remember(totalDevices, updatedDevicesCount) {
        if (totalDevices > 0) (updatedDevicesCount.toFloat() / totalDevices.toFloat()) else 0f
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_atualizacoes_campanhas_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CAMPANHAS DE ATUALIZAÇÃO",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Controle global de distribuição de versões e forçamento de updates",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    // Status Pill
                    Surface(
                        color = if (updateControl.enabled) Color(0xFF065F46) else Color(0xFF374151),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (updateControl.enabled) Color(0xFF10B981) else Color(0xFF6B7280))
                    ) {
                        Text(
                            text = if (updateControl.enabled) "CAMPANHA ATIVA" else "DESATIVADA",
                            color = if (updateControl.enabled) Color(0xFF34D399) else Color(0xFFD1D5DB),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Metric Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CampaignMetricCard(
                        title = "DISPOSITIVOS ATUALIZADOS",
                        value = updatedDevicesCount.toString(),
                        subtitle = "${(adoptionRate * 100).toInt()}% da base",
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    CampaignMetricCard(
                        title = "VERSÕES DEFASADAS",
                        value = pendingDevicesCount.toString(),
                        subtitle = "Aguardando update",
                        icon = Icons.Default.Pending,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    CampaignMetricCard(
                        title = "TOTAL BASE",
                        value = totalDevices.toString(),
                        subtitle = "Aparelhos registrados",
                        icon = Icons.Default.Devices,
                        iconTint = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Adoption Progress Bar
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Progresso de Adoção da Versão Atual", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(adoptionRate * 100).toInt()}%", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                        LinearProgressIndicator(
                            progress = { adoptionRate },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF262626)
                        )
                    }
                }
            }
        }

        // --- 2. CONFIGURAÇÃO DA CAMPANHA ATIVA ---
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "PARÂMETROS DA CAMPANHA DE ATUALIZAÇÃO",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    // Toggle: Campanha Ativa
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ativar Campanha Global", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Avisa os aparelhos conectados quando uma nova versão for publicada", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = campaignEnabled,
                            onCheckedChange = { campaignEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandRed,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF262626)
                            ),
                            modifier = Modifier.testTag("switch_campaign_enabled")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF262626))

                    // Toggle: Obrigatória
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Atualização Obrigatória", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Bloqueia o uso do app até que o usuário realize o download do novo APK", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isMandatory,
                            onCheckedChange = { isMandatory = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFEF4444),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF262626)
                            ),
                            modifier = Modifier.testTag("switch_campaign_mandatory")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF262626))

                    // Seleção da Versão de Destino
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Versão de Destino da Campanha", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (publishedVersions.isEmpty()) {
                            Text("Nenhuma versão publicada no repositório. Publique um APK em 'Versões do Aplicativo'.", color = Color(0xFFF59E0B), fontSize = 12.sp)
                        } else {
                            publishedVersions.forEach { version ->
                                val isSelected = selectedVersionId == version.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedVersionId = version.id },
                                    color = if (isSelected) Color(0xFF1E293B) else DarkBackground,
                                    border = BorderStroke(1.dp, if (isSelected) BrandRed else CardBorder),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedVersionId = version.id },
                                            colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("v${version.versionName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("(Build ${version.versionCode})", color = Color.Gray, fontSize = 11.sp)
                                                if (version.mandatory) {
                                                    Surface(color = Color(0xFFEF4444).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                        Text("OBRIGATÓRIO", color = Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                            Text(version.releaseNotes.ifBlank { "Sem notas de versão informadas" }, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botão Salvar Campanha
                    Button(
                        onClick = {
                            isSavingCampaign = true
                            val chosenVersion = publishedVersions.find { it.id == selectedVersionId }
                            if (campaignEnabled && chosenVersion != null) {
                                adminViewModel.activateAppUpdate(chosenVersion, isMandatory)
                            } else {
                                adminViewModel.deactivateAppUpdate()
                            }
                            scope.launch {
                                delay(600L)
                                isSavingCampaign = false
                                broadcastSuccessMsg = if (campaignEnabled) "Campanha ativada com sucesso no Firebase!" else "Campanha desativada com sucesso!"
                                delay(4000L)
                                broadcastSuccessMsg = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_salvar_campanha"),
                        enabled = !isSavingCampaign
                    ) {
                        if (isSavingCampaign) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar Campanha no Firebase", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // --- 3. DISPARO EM MASSA PARA DISPOSITIVOS ---
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "COMANDO: ATUALIZAR TODOS OS DISPOSITIVOS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Envia uma notificação e ordem de atualização imediata para todos os aparelhos registrados atualizarem para a versão ativa.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            isBroadcasting = true
                            scope.launch {
                                val chosenVersion = publishedVersions.find { it.id == selectedVersionId }
                                val verName = chosenVersion?.versionName ?: updateControl.activeVersionName ?: "Recente"
                                adminViewModel.createAndSendNotification(
                                    title = "Nova Atualização Disponível! (v$verName)",
                                    message = "Uma nova versão do RONYCINE está disponível. Atualize para continuar aproveitando com máxima velocidade e estabilidade.",
                                    imageUrl = "",
                                    type = "ATUALIZACAO",
                                    buttonText = "Atualizar Agora",
                                    actionUrl = "update",
                                    targetSegment = "ALL",
                                    isScheduled = false,
                                    scheduledAt = 0L,
                                    isActive = true
                                )
                                delay(1200L)
                                isBroadcasting = false
                                broadcastSuccessMsg = "Ordem de atualização transmitida para todos os ${totalDevices} aparelhos!"
                                delay(4000L)
                                broadcastSuccessMsg = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_atualizar_todos_dispositivos"),
                        enabled = !isBroadcasting && totalDevices > 0
                    ) {
                        if (isBroadcasting) {
                            CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.SendToMobile, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Transmitir Comando 'Atualizar Todos' (${totalDevices} aparelhos)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    broadcastSuccessMsg?.let { msg ->
                        Surface(
                            color = Color(0xFF064E3B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                Text(msg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            }
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF757575), fontSize = 10.sp)
        }
    }
}
