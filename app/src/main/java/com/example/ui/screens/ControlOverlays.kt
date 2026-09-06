package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FirebaseService
import com.example.data.remote.RemoteConfigEntity

@Composable
fun MaintenanceScreen(
    remoteConfig: RemoteConfigEntity,
    onRetry: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE50914).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SISTEMA EM MANUTENÇÃO",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = remoteConfig.maintenanceMessage.ifBlank {
                    "Estamos realizando melhorias no aplicativo. Por favor, tente novamente mais tarde."
                },
                fontSize = 14.sp,
                color = Color(0xFFA0A0A0),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "VERIFICAR NOVAMENTE",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun MandatoryUpdateScreen(
    remoteConfig: RemoteConfigEntity,
    sha256: String = "",
    targetApkUrl: String = "",
    targetVersionName: String = "",
    targetVersionCode: Int = 0,
    firebaseService: FirebaseService? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadState by com.example.util.UpdateManager.downloadState.collectAsState()

    val effectiveApkUrl = targetApkUrl.ifBlank { remoteConfig.latestApkUrl }
    val effectiveVersionName = targetVersionName.ifBlank { remoteConfig.latestVersionName }
    val effectiveVersionCode = if (targetVersionCode > 0) targetVersionCode else remoteConfig.latestVersionCode
    val isApkConfigured = effectiveApkUrl.isNotBlank() && effectiveApkUrl.startsWith("https://", ignoreCase = true)

    DisposableEffect(Unit) {
        onDispose {
            com.example.util.UpdateManager.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE50914).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (downloadState) {
                        is com.example.util.UpdateDownloadState.Downloading -> Icons.Default.Download
                        is com.example.util.UpdateDownloadState.Error -> Icons.Default.Error
                        else -> Icons.Default.SystemUpdate
                    },
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (downloadState) {
                    is com.example.util.UpdateDownloadState.Downloading -> "BAIXANDO ATUALIZAÇÃO"
                    is com.example.util.UpdateDownloadState.Verifying -> "VERIFICANDO ARQUIVO"
                    is com.example.util.UpdateDownloadState.ReadyToInstall -> "PRONTO PARA INSTALAR"
                    is com.example.util.UpdateDownloadState.Error -> "FALHA NO DOWNLOAD"
                    else -> "ATUALIZAÇÃO OBRIGATÓRIA"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nova versão v${remoteConfig.latestVersionName} disponível",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE50914)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = downloadState) {
                is com.example.util.UpdateDownloadState.Downloading -> {
                    Text(
                        text = "${state.progressPercent}%",
                        color = Color(0xFFE50914),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFFE50914),
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val dlStr = com.example.util.UpdateManager.formatFileSize(state.downloadedBytes)
                    val totStr = if (state.totalBytes > 0) com.example.util.UpdateManager.formatFileSize(state.totalBytes) else "..."
                    Text(
                        text = "$dlStr / $totStr",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                is com.example.util.UpdateDownloadState.Verifying -> {
                    CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Validando integridade SHA-256 do pacote...",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }

                is com.example.util.UpdateDownloadState.ReadyToInstall -> {
                    Text(
                        text = "Download concluído com sucesso. Clique abaixo para abrir o instalador do sistema.",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { com.example.util.UpdateManager.installApk(context, state.file) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("INSTALAR AGORA", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                is com.example.util.UpdateDownloadState.Error -> {
                    Text(
                        text = state.message,
                        fontSize = 14.sp,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (isApkConfigured) {
                                com.example.util.UpdateManager.startUpdateFlow(
                                    context = context,
                                    scope = scope,
                                    apkUrl = effectiveApkUrl,
                                    expectedSha256 = sha256,
                                    targetVersionName = effectiveVersionName,
                                    targetVersionCode = effectiveVersionCode,
                                    firebaseService = firebaseService,
                                    trigger = "mandatory_retry"
                                )
                            }
                        },
                        enabled = isApkConfigured,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("TENTAR NOVAMENTE", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                com.example.util.UpdateDownloadState.Idle -> {
                    if (!isApkConfigured) {
                        Surface(
                            color = Color(0xFF332200),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠ Nenhum link de APK configurado para esta versão pelo administrador.",
                                color = Color(0xFFFFB300),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = remoteConfig.latestReleaseNotes.ifBlank {
                            "Esta versão inclui atualizações críticas de estabilidade, segurança e novos recursos. A atualização é obrigatória para continuar utilizando o aplicativo."
                        },
                        fontSize = 14.sp,
                        color = Color(0xFFA0A0A0),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (isApkConfigured) {
                                com.example.util.UpdateManager.startUpdateFlow(
                                    context = context,
                                    scope = scope,
                                    apkUrl = effectiveApkUrl,
                                    expectedSha256 = sha256,
                                    targetVersionName = effectiveVersionName,
                                    targetVersionCode = effectiveVersionCode,
                                    firebaseService = firebaseService,
                                    trigger = "mandatory_screen"
                                )
                            }
                        },
                        enabled = isApkConfigured,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isApkConfigured) "ATUALIZAR AGORA" else "ATUALIZAÇÃO INDISPONÍVEL",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(
    remoteConfig: RemoteConfigEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = Color(0xFFE50914),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Nova Versão Disponível!",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                Text(
                    text = "Versão v${remoteConfig.latestVersionName} está pronta para instalação.",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (remoteConfig.latestReleaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = remoteConfig.latestReleaseNotes,
                        color = Color(0xFFA0A0A0),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (remoteConfig.latestApkUrl.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteConfig.latestApkUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
            ) {
                Text("ATUALIZAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("MAIS TARDE", color = Color(0xFFA0A0A0))
            }
        }
    )
}
