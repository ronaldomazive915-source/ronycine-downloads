package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.remote.FirebaseService
import com.example.data.remote.RemoteConfigEntity
import com.example.ui.theme.BrandRed
import com.example.util.UpdateDownloadState
import com.example.util.UpdateManager

@Composable
fun UpdateDialog(
    remoteConfig: RemoteConfigEntity,
    sha256: String = "",
    targetApkUrl: String = "",
    targetVersionName: String = "",
    targetVersionCode: Int = 0,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit = {},
    firebaseService: FirebaseService? = null,
    autoStart: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadState by UpdateManager.downloadState.collectAsState()

    val effectiveApkUrl = targetApkUrl.ifBlank { remoteConfig.latestApkUrl }.trim()
    val effectiveVersionName = targetVersionName.ifBlank { remoteConfig.latestVersionName }
    val effectiveVersionCode = if (targetVersionCode > 0) targetVersionCode else remoteConfig.latestVersionCode
    val isApkConfigured = effectiveApkUrl.isNotBlank() && effectiveApkUrl.startsWith("https://", ignoreCase = true)

    LaunchedEffect(autoStart, isApkConfigured) {
        if (autoStart && isApkConfigured && downloadState is UpdateDownloadState.Idle) {
            UpdateManager.startUpdateFlow(
                context = context,
                scope = scope,
                apkUrl = effectiveApkUrl,
                expectedSha256 = sha256,
                targetVersionName = effectiveVersionName,
                targetVersionCode = effectiveVersionCode,
                firebaseService = firebaseService,
                trigger = "notification_autostart"
            )
            onUpdate(sha256)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            UpdateManager.resetState(force = false)
        }
    }

    Dialog(onDismissRequest = {
        if (downloadState !is UpdateDownloadState.Downloading && downloadState !is UpdateDownloadState.Verifying) {
            onDismiss()
        }
    }) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(BrandRed.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (downloadState) {
                        is UpdateDownloadState.Error -> Icons.Default.ErrorOutline
                        is UpdateDownloadState.Verifying -> Icons.Default.Security
                        is UpdateDownloadState.Downloading -> Icons.Default.Download
                        else -> Icons.Default.SystemUpdate
                    },
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (downloadState) {
                    is UpdateDownloadState.Downloading -> "BAIXANDO ATUALIZAÇÃO"
                    is UpdateDownloadState.Verifying -> "VERIFICANDO INTEGRIDADE"
                    is UpdateDownloadState.ReadyToInstall -> "INSTALAÇÃO PRONTA"
                    is UpdateDownloadState.Error -> "FALHA NO DOWNLOAD"
                    else -> "NOVA ATUALIZAÇÃO DISPONÍVEL"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = downloadState) {
                is UpdateDownloadState.Downloading -> {
                    Text(
                        text = "${state.progressPercent}%",
                        color = BrandRed,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = BrandRed,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val downloadedStr = UpdateManager.formatFileSize(state.downloadedBytes)
                    val totalStr = if (state.totalBytes > 0) UpdateManager.formatFileSize(state.totalBytes) else "..."
                    Text(
                        text = "$downloadedStr / $totalStr",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                is UpdateDownloadState.Verifying -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Validando assinatura digital SHA-256...",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Arquivo verificado com sucesso. O instalador do Android será aberto para confirmar a atualização.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { UpdateManager.installApk(context, state.file) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("INSTALAR AGORA", fontWeight = FontWeight.Bold)
                    }
                }

                is UpdateDownloadState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (isApkConfigured) {
                                UpdateManager.startUpdateFlow(
                                    context = context,
                                    scope = scope,
                                    apkUrl = effectiveApkUrl,
                                    expectedSha256 = sha256,
                                    targetVersionName = effectiveVersionName,
                                    targetVersionCode = effectiveVersionCode,
                                    firebaseService = firebaseService,
                                    trigger = "in_app_retry"
                                )
                            }
                        },
                        enabled = isApkConfigured,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TENTAR NOVAMENTE", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("FECHAR", color = Color.Gray)
                    }
                }

                UpdateDownloadState.Idle -> {
                    Text(
                        text = "Uma nova versão do RONYCINE está disponível.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Versão v$effectiveVersionName (Build $effectiveVersionCode)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    if (!isApkConfigured) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF332200),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠ Nenhum link de APK configurado para esta versão pelo administrador.",
                                color = Color(0xFFFFB300),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    val notes = remoteConfig.latestReleaseNotes.ifBlank { "Melhorias de desempenho, correções e novos recursos." }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = notes,
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (isApkConfigured) {
                                UpdateManager.startUpdateFlow(
                                    context = context,
                                    scope = scope,
                                    apkUrl = effectiveApkUrl,
                                    expectedSha256 = sha256,
                                    targetVersionName = effectiveVersionName,
                                    targetVersionCode = effectiveVersionCode,
                                    firebaseService = firebaseService,
                                    trigger = "in_app_dialog"
                                )
                                onUpdate(sha256)
                            }
                        },
                        enabled = isApkConfigured,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isApkConfigured) "ATUALIZAR AGORA" else "ATUALIZAÇÃO INDISPONÍVEL",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onDismiss) {
                        Text("MAIS TARDE", color = Color.Gray)
                    }
                }
            }
        }
    }
}
