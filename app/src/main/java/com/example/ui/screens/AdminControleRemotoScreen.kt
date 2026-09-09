package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AppVersionEntity
import com.example.data.remote.RemoteConfigEntity
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AdminControleRemotoScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val remoteConfig by viewModel.remoteConfig.collectAsState()
    val publishedVersions by viewModel.publishedVersions.collectAsState()
    val auditLogs by viewModel.remoteAuditLogs.collectAsState()
    val devices by viewModel.allDevices.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Versões, 1: Configurações, 2: Logs

    // Form state for publishing new version
    var versionNameInput by remember { mutableStateOf("1.6.0") }
    var versionCodeInput by remember { mutableStateOf("106") }
    var apkUrlInput by remember { mutableStateOf("") }
    var releaseNotesInput by remember { mutableStateOf("") }
    var minVersionCodeInput by remember { mutableStateOf("100") }
    var isMandatoryInput by remember { mutableStateOf(false) }
    var isPublishedInput by remember { mutableStateOf(true) }

    // Form state for remote config
    var maintenanceMode by remember { mutableStateOf(remoteConfig.maintenanceMode) }
    var maintenanceMsg by remember { mutableStateOf(remoteConfig.maintenanceMessage) }
    var selectedDurationMinutes by remember { mutableStateOf(120) } // default 2 hours
    var showMaintenanceDialog by remember { mutableStateOf(false) }
    var pendingMaintenanceState by remember { mutableStateOf(false) }
    var enableDownloads by remember { mutableStateOf(remoteConfig.enableDownloads) }
    var enableRequests by remember { mutableStateOf(remoteConfig.enableRequests) }
    var enableLiveTV by remember { mutableStateOf(remoteConfig.enableLiveTV) }
    var enableTrailers by remember { mutableStateOf(remoteConfig.enableTrailers) }
    var announcementText by remember { mutableStateOf(remoteConfig.announcement) }

    LaunchedEffect(remoteConfig) {
        maintenanceMode = remoteConfig.maintenanceMode
        maintenanceMsg = remoteConfig.maintenanceMessage
        enableDownloads = remoteConfig.enableDownloads
        enableRequests = remoteConfig.enableRequests
        enableLiveTV = remoteConfig.enableLiveTV
        enableTrailers = remoteConfig.enableTrailers
        announcementText = remoteConfig.announcement
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Header Metrics Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "VERSÃO ATUAL",
                value = "v1.5.0 (105)",
                icon = Icons.Default.Smartphone,
                accentColor = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "MANUTENÇÃO",
                value = if (remoteConfig.maintenanceMode) "ATIVA" else "INATIVA",
                icon = Icons.Default.Build,
                accentColor = if (remoteConfig.maintenanceMode) Color(0xFFE50914) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "VERSÕES APK",
                value = "${publishedVersions.size}",
                icon = Icons.Default.SystemUpdate,
                accentColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
        }

        // Section Navigation Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Gestão de Versões APK", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Configurações Remotas", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Logs de Auditoria", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (activeTab) {
            0 -> {
                // Versões Tab
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        // Form to publish version
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Publicar Nova Versão do APK",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = versionNameInput,
                                        onValueChange = { versionNameInput = it },
                                        label = { Text("Versão (ex: 1.6.0)") },
                                        colors = textFieldColors(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = versionCodeInput,
                                        onValueChange = { versionCodeInput = it },
                                        label = { Text("Build (ex: 106)") },
                                        colors = textFieldColors(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = apkUrlInput,
                                    onValueChange = { apkUrlInput = it },
                                    label = { Text("URL do APK (HTTPS Direto ou Firebase Storage)") },
                                    colors = textFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = releaseNotesInput,
                                    onValueChange = { releaseNotesInput = it },
                                    label = { Text("Notas do Lançamento (Changelog)") },
                                    colors = textFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isMandatoryInput,
                                            onCheckedChange = { isMandatoryInput = it },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE50914))
                                        )
                                        Text("Atualização Obrigatória", color = Color.White, fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val vCode = versionCodeInput.toIntOrNull() ?: 106
                                            val minCode = minVersionCodeInput.toIntOrNull() ?: 100
                                            if (apkUrlInput.isBlank()) {
                                                Toast.makeText(context, "Informe a URL do APK!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            val newVersion = AppVersionEntity(
                                                id = "v_$vCode",
                                                versionName = versionNameInput,
                                                versionCode = vCode,
                                                apkUrl = apkUrlInput,
                                                releaseNotes = releaseNotesInput,
                                                minimumVersionCode = minCode,
                                                mandatory = isMandatoryInput,
                                                published = isPublishedInput
                                            )
                                            viewModel.publishAppVersion(newVersion)
                                            Toast.makeText(context, "Versão v$versionNameInput publicada!", Toast.LENGTH_SHORT).show()
                                            apkUrlInput = ""
                                            releaseNotesInput = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("PUBLICAR VERSÃO", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Histórico de Versões no Servidor",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (publishedVersions.isEmpty()) {
                        item {
                            Text("Nenhuma versão registrada no Firestore.", color = Color(0xFFA0A0A0), fontSize = 14.sp)
                        }
                    } else {
                        items(publishedVersions) { ver ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "v${ver.versionName} (${ver.versionCode})",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (ver.mandatory) {
                                                BadgeChip(text = "OBRIGATÓRIA", color = Color(0xFFE50914))
                                            } else {
                                                BadgeChip(text = "OPCIONAL", color = Color(0xFF4CAF50))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ver.releaseNotes.ifBlank { "Sem notas de versão." },
                                            fontSize = 13.sp,
                                            color = Color(0xFFA0A0A0)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Publicado em: ${ver.dateFormatted}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF707070)
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("APK URL", ver.apkUrl)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "URL copiada!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.White)
                                        }

                                        IconButton(onClick = {
                                            viewModel.deleteAppVersion(ver.id)
                                            Toast.makeText(context, "Versão removida", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFE50914))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Configurações Remotas & Feature Flags Tab
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Modo Manutenção do Aplicativo",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (maintenanceMode) "Modo Manutenção ATIVADO" else "Modo Manutenção DESATIVADO",
                                            color = if (maintenanceMode) Color(0xFFE50914) else Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Bloqueia o acesso geral ao aplicativo exibindo tela de manutenção.",
                                            color = Color(0xFFA0A0A0),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Switch(
                                        checked = maintenanceMode,
                                        onCheckedChange = { maintenanceMode = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE50914))
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = maintenanceMsg,
                                    onValueChange = { maintenanceMsg = it },
                                    label = { Text("Mensagem Exibida no Modo Manutenção") },
                                    colors = textFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Controle de Funcionalidades (Feature Flags)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                FeatureSwitchItem("Habilitar Downloads Offline", enableDownloads) { enableDownloads = it }
                                FeatureSwitchItem("Habilitar Pedidos de Filmes/Séries", enableRequests) { enableRequests = it }
                                FeatureSwitchItem("Habilitar Guia TV ao Vivo", enableLiveTV) { enableLiveTV = it }
                                FeatureSwitchItem("Habilitar Playback de Trailers", enableTrailers) { enableTrailers = it }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Aviso Global (Banner na Tela Inicial)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = announcementText,
                                    onValueChange = { announcementText = it },
                                    label = { Text("Texto do Anúncio (Deixe em branco para ocultar)") },
                                    colors = textFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val newConfig = remoteConfig.copy(
                                            maintenanceMode = maintenanceMode,
                                            maintenanceMessage = maintenanceMsg,
                                            enableDownloads = enableDownloads,
                                            enableRequests = enableRequests,
                                            enableLiveTV = enableLiveTV,
                                            enableTrailers = enableTrailers,
                                            announcement = announcementText,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        viewModel.updateRemoteConfig(newConfig)
                                        Toast.makeText(context, "Configurações remotas salvas!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SALVAR CONFIGURAÇÕES REMOTAS", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Logs de Auditoria Tab
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (auditLogs.isEmpty()) {
                        item {
                            Text("Nenhum log de auditoria registrado.", color = Color(0xFFA0A0A0), fontSize = 14.sp)
                        }
                    } else {
                        items(auditLogs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.action,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = log.details,
                                            fontSize = 12.sp,
                                            color = Color(0xFFA0A0A0)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = log.dateFormatted,
                                            fontSize = 11.sp,
                                            color = Color(0xFF707070)
                                        )
                                        Text(
                                            text = log.adminEmail,
                                            fontSize = 10.sp,
                                            color = Color(0xFF2196F3)
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
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0A0A0))
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun FeatureSwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE50914))
        )
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFE50914),
    unfocusedBorderColor = Color(0xFF333333),
    focusedLabelColor = Color(0xFFE50914),
    unfocusedLabelColor = Color(0xFFA0A0A0),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)
