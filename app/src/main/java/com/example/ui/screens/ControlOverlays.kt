package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import java.util.Locale
import com.example.data.remote.FirebaseService
import com.example.data.remote.RemoteConfigEntity

@Composable
fun MaintenanceScreen(
    remoteConfig: RemoteConfigEntity,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }

    // Real-time countdown calculation
    var timeLeftSeconds by remember { mutableStateOf(0L) }
    var totalDurationSeconds by remember { mutableStateOf(1L) }

    LaunchedEffect(remoteConfig.maintenanceStartedAt, remoteConfig.maintenanceEndAt) {
        val start = remoteConfig.maintenanceStartedAt
        val end = remoteConfig.maintenanceEndAt
        if (end > start && start > 0L) {
            totalDurationSeconds = maxOf(1L, (end - start) / 1000L)
        } else {
            totalDurationSeconds = 7200L // default 2 hours
        }

        while (true) {
            val now = System.currentTimeMillis()
            if (end > now) {
                timeLeftSeconds = (end - now) / 1000L
            } else {
                timeLeftSeconds = 0L
                if (remoteConfig.maintenanceMode) {
                    onRetry()
                }
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val hours = (timeLeftSeconds / 3600).coerceAtLeast(0)
    val minutes = ((timeLeftSeconds % 3600) / 60).coerceAtLeast(0)
    val seconds = (timeLeftSeconds % 60).coerceAtLeast(0)

    val progress = if (totalDurationSeconds > 0) {
        val elapsed = totalDurationSeconds - timeLeftSeconds
        (elapsed.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0.5f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Cinematic Background with Image & Gradients
        Box(modifier = Modifier.fillMaxSize()) {
            // Hero background image (simulating a dark cinematic scene)
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF0A0A0A)
                        )
                    )
                )
            }
            
            // Subtle animated glow
            val infiniteTransition = rememberInfiniteTransition(label = "glow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = androidx.compose.animation.core.EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE50914).copy(alpha = glowAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2, size.height / 3),
                                radius = 1200f
                            )
                        )
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Maintenance Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, Color(0xFFE50914).copy(alpha = 0.4f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SISTEMA EM MANUTENÇÃO",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = remoteConfig.maintenanceMessage.ifBlank {
                    "Estamos preparando uma nova experiência para você."
                },
                fontSize = 16.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (remoteConfig.maintenanceEndAt > 0L) {
                Spacer(modifier = Modifier.height(48.dp))

                // Premium Countdown Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountdownUnit(value = String.format(Locale.getDefault(), "%02d", hours), label = "HORAS")
                    Text(":", color = Color(0xFFE50914), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    CountdownUnit(value = String.format(Locale.getDefault(), "%02d", minutes), label = "MIN")
                    Text(":", color = Color(0xFFE50914), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    CountdownUnit(value = String.format(Locale.getDefault(), "%02d", seconds), label = "SEG")
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Cinematic Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333333))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Color(0xFFE50914))
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = {
                    isChecking = true
                    onRetry()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = "VERIFICAR NOVAMENTE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-1).sp
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE50914),
            letterSpacing = 1.sp
        )
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
                .padding(24.dp)
        ) {
            // Ícone de Status do Estado (Requisitos 11, 12, 14)
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

            // Título do Estado de Download (Requisito 12)
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

            Spacer(modifier = Modifier.height(12.dp))

            // 1. INFORMAÇÕES DA ATUALIZAÇÃO (Requisito 11)
            val pInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) { null }
            val currentVerName = pInfo?.versionName ?: "1.1.0"
            val currentVerCode = if (pInfo != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") pInfo.versionCode
                }
            } else 11

            Text(
                text = "Versão atual: v$currentVerName ($currentVerCode) ➔ Nova versão: v$effectiveVersionName ($effectiveVersionCode)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE50914),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lógica do progresso / ações dependendo do estado
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
                        text = "Baixando atualização: $dlStr / $totStr",
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
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 2. BOTÃO PRINCIPAL "INSTALAR AGORA" (Requisito 11)
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
                        text = "Não foi possível concluir a atualização:\n${state.message}",
                        fontSize = 13.sp,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 2. BOTÃO PRINCIPAL "TENTAR NOVAMENTE" (Requisito 11)
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
                    Text(
                        text = remoteConfig.latestReleaseNotes.ifBlank {
                            "Esta versão inclui atualizações críticas de estabilidade, segurança e novos recursos. A atualização é obrigatória para continuar utilizando o aplicativo."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFA0A0A0),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    if (!isApkConfigured) {
                        Spacer(modifier = Modifier.height(16.dp))
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
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. BOTÃO PRINCIPAL "ATUALIZAR AGORA" (Requisito 11)
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

            // 4. ÁREA "PRECISA DE AJUDA?" (Requisitos 11, 14, 17)
            val isError = downloadState is com.example.util.UpdateDownloadState.Error
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                color = if (isError) Color(0xFF3B1E1E) else Color(0xFF161616),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isError) Color(0xFFE50914) else Color(0xFF2E2E2E)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PRECISA DE AJUDA?",
                        color = if (isError) Color(0xFFE50914) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Se ocorrer algum problema durante o download ou a instalação da atualização, entre em contacto com o nosso suporte.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // 5. BOTÃO "FALAR COM O SUPORTE" (WhatsApp) (Requisitos 11, 14)
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/qr/EWME3ZBA552PM1")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Não foi possível abrir o WhatsApp.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), // WhatsApp Green
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "FALAR COM O SUPORTE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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
