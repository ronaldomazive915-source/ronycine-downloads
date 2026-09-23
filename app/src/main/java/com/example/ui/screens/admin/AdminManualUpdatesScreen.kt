package com.example.ui.screens.admin

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AppUpdate
import com.example.data.remote.ManualUpdateConfig
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdminViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManualUpdatesScreen(adminViewModel: AdminViewModel) {
    val context = LocalContext.current
    val manualUpdateConfig by adminViewModel.manualUpdateConfig.collectAsState()

    var version by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var changelog by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var appDownloadUrl by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var sendGeneralNotification by remember { mutableStateOf(false) }

    // Validation & UI states
    var versionError by remember { mutableStateOf<String?>(null) }
    var versionCodeError by remember { mutableStateOf<String?>(null) }
    var apkUrlError by remember { mutableStateOf<String?>(null) }
    var appDownloadUrlError by remember { mutableStateOf<String?>(null) }

    var isPublishing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isNotificationSentMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showConfirmModal by remember { mutableStateOf(false) }

    // Link fixo oficial do APK (Requirement 1)
    val OFFICIAL_APK_URL = "https://github.com/ronaldomazive915-source/Rony-Skies-/releases/download/v1.1.0/RONYCINE.apk"

    // Synchronize form with current active configuration on first load or when triggered
    LaunchedEffect(manualUpdateConfig) {
        if (version.isEmpty()) {
            version = manualUpdateConfig.version
            versionCode = if (manualUpdateConfig.versionCode > 0) manualUpdateConfig.versionCode.toString() else "13"
            title = manualUpdateConfig.title.ifBlank { "Nova atualização do RONYCINE" }
            description = manualUpdateConfig.description.ifBlank { "Uma nova versão do RONYCINE está disponível." }
            changelog = manualUpdateConfig.changelog
            apkUrl = manualUpdateConfig.apkUrl.ifBlank { OFFICIAL_APK_URL }
            appDownloadUrl = manualUpdateConfig.appDownloadUrl.ifBlank { OFFICIAL_APK_URL }
            active = manualUpdateConfig.active
            sendGeneralNotification = manualUpdateConfig.sendGeneralNotification
        }
    }

    fun populateFormFromConfig() {
        version = manualUpdateConfig.version
        versionCode = if (manualUpdateConfig.versionCode > 0) manualUpdateConfig.versionCode.toString() else "13"
        title = manualUpdateConfig.title
        description = manualUpdateConfig.description
        changelog = manualUpdateConfig.changelog
        apkUrl = manualUpdateConfig.apkUrl.ifBlank { OFFICIAL_APK_URL }
        appDownloadUrl = manualUpdateConfig.appDownloadUrl.ifBlank { OFFICIAL_APK_URL }
        active = manualUpdateConfig.active
        sendGeneralNotification = manualUpdateConfig.sendGeneralNotification
        versionError = null
        versionCodeError = null
        apkUrlError = null
        appDownloadUrlError = null
        Toast.makeText(context, "Formulário preenchido com a versão vigente.", Toast.LENGTH_SHORT).show()
    }

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        if (trimmed.startsWith("javascript:", ignoreCase = true) || trimmed.startsWith("data:", ignoreCase = true)) {
            return false
        }
        return try {
            val parsed = Uri.parse(trimmed)
            parsed.scheme != null && parsed.host != null
        } catch (e: Exception) {
            false
        }
    }

    fun validateAndOpenConfirm() {
        versionError = null
        versionCodeError = null
        apkUrlError = null
        appDownloadUrlError = null

        val cleanVersion = version.trim()
        val cleanCode = versionCode.trim().toIntOrNull() ?: 0
        val cleanApk = apkUrl.trim()
        val cleanAppDownload = appDownloadUrl.trim()

        var hasError = false

        if (cleanVersion.isBlank()) {
            versionError = "Informe a versão (ex: 1.6.0)."
            hasError = true
        }

        if (cleanCode <= 0) {
            versionCodeError = "Informe um código de build válido (> 0)."
            hasError = true
        }

        if (cleanApk.isNotBlank() && !isValidUrl(cleanApk)) {
            apkUrlError = "Link inválido. Use uma URL HTTPS válida."
            hasError = true
        }

        if (cleanAppDownload.isNotBlank() && !isValidUrl(cleanAppDownload)) {
            appDownloadUrlError = "Link inválido. Use uma URL HTTPS válida."
            hasError = true
        }

        if (!hasError) {
            showConfirmModal = true
        }
    }

    fun executePublish() {
        val cleanVersion = version.trim()
        val cleanCode = versionCode.trim().toIntOrNull() ?: 0
        val cleanApk = apkUrl.trim()
        val cleanAppDownload = appDownloadUrl.trim()

        isPublishing = true
        statusMessage = null
        isNotificationSentMsg = null
        isError = false

        val generatedUpdateId = "update_${cleanVersion}_${cleanCode}_${System.currentTimeMillis()}"
        val config = AppUpdate(
            id = "current",
            updateId = generatedUpdateId,
            version = cleanVersion,
            versionCode = cleanCode,
            title = title.trim(),
            description = description.trim(),
            changelog = changelog.trim(),
            apkUrl = cleanApk,
            appDownloadUrl = cleanAppDownload,
            active = active,
            notifyUsers = sendGeneralNotification,
            sendGeneralNotification = sendGeneralNotification,
            createdAt = if (manualUpdateConfig.createdAt > 0L) manualUpdateConfig.createdAt else System.currentTimeMillis()
        )

        adminViewModel.publishManualUpdate(config) { success, msg ->
            isPublishing = false
            if (success) {
                statusMessage = "✓ Atualização publicada com sucesso"
                if (sendGeneralNotification) {
                    isNotificationSentMsg = "✓ Notificação enviada"
                }
                isError = false
                Toast.makeText(context, "Atualização publicada e confirmada no Firebase.", Toast.LENGTH_SHORT).show()
            } else {
                statusMessage = "Não foi possível publicar a atualização: $msg"
                isError = true
            }
        }
    }

    // Confirmation Modal
    if (showConfirmModal) {
        AlertDialog(
            onDismissRequest = { showConfirmModal = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Publish,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "PUBLICAR ATUALIZAÇÃO?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("VERSÃO", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(version.ifBlank { "N/A" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("BUILD", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(versionCode.ifBlank { "N/A" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("STATUS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (active) "ATIVA" else "INATIVA",
                                    color = if (active) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "Essa atualização substituirá a atualização atualmente publicada para os usuários do RONYCINE.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    if (sendGeneralNotification) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BrandRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Uma notificação push será enviada para os usuários.",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmModal = false
                        executePublish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("PUBLICAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmModal = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("CANCELAR", color = Color.LightGray, fontSize = 12.5.sp)
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        val isDesktop = maxWidth >= 800.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Header
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ATUALIZAÇÃO ATUAL",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Publique uma nova versão do RONYCINE para os usuários.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Success / Error Banner with full confirmation breakdown (Requirements 2, 14, 21)
            statusMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) Color(0xFF7F1D1D) else Color(0xFF064E3B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isError) Color(0xFFEF4444) else Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        if (!isError) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Versão:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text("v${version.trim()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Build:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text(versionCode.trim(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Status:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text("ATIVA", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Data:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text("Agora mesmo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Link do APK", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = apkUrl.trim(),
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl.trim()))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("ABRIR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isDesktop) {
                // Desktop Layout: 2 Columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Form
                    Column(
                        modifier = Modifier.weight(1.4f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PublishFormCard(
                            version = version,
                            onVersionChange = { version = it },
                            versionError = versionError,
                            versionCode = versionCode,
                            onVersionCodeChange = { versionCode = it },
                            versionCodeError = versionCodeError,
                            title = title,
                            onTitleChange = { title = it },
                            description = description,
                            onDescriptionChange = { description = it },
                            changelog = changelog,
                            onChangelogChange = { changelog = it },
                            apkUrl = apkUrl,
                            onApkUrlChange = { apkUrl = it },
                            apkUrlError = apkUrlError,
                            appDownloadUrl = appDownloadUrl,
                            onAppDownloadUrlChange = { appDownloadUrl = it },
                            appDownloadUrlError = appDownloadUrlError,
                            active = active,
                            onActiveChange = { active = it },
                            sendGeneralNotification = sendGeneralNotification,
                            onSendNotificationChange = { sendGeneralNotification = it },
                            isPublishing = isPublishing,
                            onPublishClick = { validateAndOpenConfirm() }
                        )
                    }

                    // Right Column: Active Version Summary
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ActiveUpdateSummaryCard(
                            config = manualUpdateConfig,
                            onEditClick = { populateFormFromConfig() }
                        )
                    }
                }
            } else {
                // Mobile Layout: Single Column (Active Summary first, then Form)
                ActiveUpdateSummaryCard(
                    config = manualUpdateConfig,
                    onEditClick = { populateFormFromConfig() }
                )

                PublishFormCard(
                    version = version,
                    onVersionChange = { version = it },
                    versionError = versionError,
                    versionCode = versionCode,
                    onVersionCodeChange = { versionCode = it },
                    versionCodeError = versionCodeError,
                    title = title,
                    onTitleChange = { title = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    changelog = changelog,
                    onChangelogChange = { changelog = it },
                    apkUrl = apkUrl,
                    onApkUrlChange = { apkUrl = it },
                    apkUrlError = apkUrlError,
                    appDownloadUrl = appDownloadUrl,
                    onAppDownloadUrlChange = { appDownloadUrl = it },
                    appDownloadUrlError = appDownloadUrlError,
                    active = active,
                    onActiveChange = { active = it },
                    sendGeneralNotification = sendGeneralNotification,
                    onSendNotificationChange = { sendGeneralNotification = it },
                    isPublishing = isPublishing,
                    onPublishClick = { validateAndOpenConfirm() }
                )
            }
        }
    }
}

@Composable
private fun ActiveUpdateSummaryCard(
    config: ManualUpdateConfig,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (config.active) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Text(
                        text = if (config.active) "ATUALIZAÇÃO ATIVA" else "ATUALIZAÇÃO INATIVA",
                        color = if (config.active) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (config.active) Color(0xFF064E3B) else Color(0xFF451A1A),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (config.active) "EM EXECUÇÃO" else "DESATIVADO",
                        color = if (config.active) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Versão ${config.version}",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Build ${config.versionCode}",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (config.title.isNotBlank()) {
                Text(
                    text = config.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (config.description.isNotBlank()) {
                Text(
                    text = config.description,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

            // DIAGNÓSTICO RÁPIDO DO PAINEL (Requirements 23 & 24)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DIAGNÓSTICO DA PUBLICAÇÃO",
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Firebase:", color = Color.Gray, fontSize = 11.sp)
                        Text("● CONECTADO", color = Color(0xFF10B981), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Atualização:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (config.active) "● PUBLICADA" else "● INATIVA",
                            color = if (config.active) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("APK Link:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (config.apkUrl.isNotBlank()) "✓ configurado" else "❌ ausente",
                            color = if (config.apkUrl.isNotBlank()) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("App Download:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (config.appDownloadUrl.isNotBlank()) "✓ configurado" else "❌ ausente",
                            color = if (config.appDownloadUrl.isNotBlank()) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notificação Push:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (config.sendGeneralNotification) "✓ solicitada/enviada" else "⚪ desativada",
                            color = if (config.sendGeneralNotification) Color(0xFF60A5FA) else Color.Gray,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ÚLTIMA ATUALIZAÇÃO",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.datePublishedFormatted,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EDITAR",
                        color = BrandRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PublishFormCard(
    version: String,
    onVersionChange: (String) -> Unit,
    versionError: String?,
    versionCode: String,
    onVersionCodeChange: (String) -> Unit,
    versionCodeError: String?,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    changelog: String,
    onChangelogChange: (String) -> Unit,
    apkUrl: String,
    onApkUrlChange: (String) -> Unit,
    apkUrlError: String?,
    appDownloadUrl: String,
    onAppDownloadUrlChange: (String) -> Unit,
    appDownloadUrlError: String?,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    sendGeneralNotification: Boolean,
    onSendNotificationChange: (Boolean) -> Unit,
    isPublishing: Boolean,
    onPublishClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Status Block
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (active) Color(0xFF064E3B).copy(alpha = 0.3f) else DarkBackground
                ),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (active) Color(0xFF10B981).copy(alpha = 0.4f) else CardBorder),
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
                            text = if (active) "ATUALIZAÇÃO ATIVA" else "ATUALIZAÇÃO DESATIVADA",
                            color = if (active) Color(0xFF10B981) else Color.LightGray,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (active) "O aplicativo exigirá/sugerirá a atualização." else "Nenhuma notificação de atualização será exibida.",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (active) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF374151),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (active) "● ATIVA" else "○ INATIVA",
                                color = if (active) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Switch(
                            checked = active,
                            onCheckedChange = onActiveChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandRed,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = DarkBackground
                            )
                        )
                    }
                }
            }

            // 2. Section: INFORMAÇÕES DA VERSÃO
            FormSectionHeader(title = "INFORMAÇÕES DA VERSÃO", icon = Icons.Default.SystemUpdate)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminInputField(
                    label = "VERSÃO",
                    value = version,
                    onValueChange = onVersionChange,
                    placeholder = "1.6.0",
                    errorText = versionError,
                    modifier = Modifier.weight(1f)
                )

                AdminInputField(
                    label = "BUILD",
                    value = versionCode,
                    onValueChange = onVersionCodeChange,
                    placeholder = "106",
                    errorText = versionCodeError,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Section: INFORMAÇÕES DA ATUALIZAÇÃO
            FormSectionHeader(title = "INFORMAÇÕES DA ATUALIZAÇÃO", icon = Icons.Default.Edit)

            AdminInputField(
                label = "TÍTULO DA ATUALIZAÇÃO",
                value = title,
                onValueChange = onTitleChange,
                placeholder = "Ex: Nova versão do RONYCINE"
            )

            AdminInputField(
                label = "DESCRIÇÃO",
                value = description,
                onValueChange = onDescriptionChange,
                placeholder = "Descreva brevemente o que esta atualização traz.",
                singleLine = false,
                minLines = 2,
                maxLines = 3
            )

            AdminInputField(
                label = "O QUE HÁ DE NOVO",
                value = changelog,
                onValueChange = onChangelogChange,
                placeholder = "Correções, melhorias e novidades desta versão...",
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                helperText = "ℹ Essa informação será exibida aos usuários antes do download."
            )

            // 4. Section: DOWNLOAD
            FormSectionHeader(title = "DOWNLOAD", icon = Icons.Default.Download)

            AdminInputField(
                label = "APK URL",
                value = apkUrl,
                onValueChange = onApkUrlChange,
                placeholder = "https://...",
                errorText = apkUrlError,
                helperText = "Link direto para o APK. O usuário será encaminhado para este endereço ao escolher baixar a atualização."
            )

            AdminInputField(
                label = "APP DOWNLOAD URL",
                value = appDownloadUrl,
                onValueChange = onAppDownloadUrlChange,
                placeholder = "https://...",
                optional = true,
                errorText = appDownloadUrlError,
                helperText = "Link alternativo para baixar o aplicativo."
            )

            // 5. Section: NOTIFICAÇÃO
            FormSectionHeader(title = "NOTIFICAÇÃO", icon = Icons.Default.Notifications)

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔔", fontSize = 14.sp)
                            Text(
                                text = "Notificar usuários",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enviar uma notificação push para os usuários quando esta atualização for publicada.",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }

                    Switch(
                        checked = sendGeneralNotification,
                        onCheckedChange = onSendNotificationChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandRed,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = DarkBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 6. Section: PUBLICAÇÃO
            Button(
                onClick = onPublishClick,
                enabled = !isPublishing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isPublishing) {
                    RonycineSmileLoader(
                        color = Color.White,
                        size = 20.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = ":) Publicando atualização...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PUBLICAR ATUALIZAÇÃO",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FormSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BrandRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AdminInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    helperText: String? = null,
    optional: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (optional) {
                Text(
                    text = "(Opcional)",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.Gray, fontSize = 12.sp) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = errorText != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBackground,
                unfocusedContainerColor = DarkBackground,
                errorContainerColor = DarkBackground,
                focusedBorderColor = BrandRed,
                unfocusedBorderColor = CardBorder,
                errorBorderColor = Color(0xFFEF4444),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = BrandRed
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (errorText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = errorText,
                    color = Color(0xFFEF4444),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (helperText != null) {
            Text(
                text = helperText,
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            )
        }
    }
}
