package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AppUpdate
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class UpdateScreenUiState {
    object Loading : UpdateScreenUiState()
    data class UpdateAvailable(val update: AppUpdate) : UpdateScreenUiState()
    data class UpToDate(val update: AppUpdate) : UpdateScreenUiState()
    data class Error(val message: String) : UpdateScreenUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) { null }
    }
    val installedVersionName = pInfo?.versionName ?: "1.1.1"
    val installedVersionCode = if (pInfo != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") pInfo.versionCode
        }
    } else 12

    // Estado da tela gerenciado estritamente pela fonte oficial appUpdates/current
    var uiState by remember { mutableStateOf<UpdateScreenUiState>(UpdateScreenUiState.Loading) }
    var isCheckingManually by remember { mutableStateOf(false) }

    // Conexão reativa com appUpdates/current em tempo real com limpeza adequada do listener
    DisposableEffect(Unit) {
        // Inicializa com cache se disponível para exibição rápida sem flash
        val cached = viewModel.getCachedAppUpdate()
        if (cached != null) {
            uiState = if (cached.active && cached.versionCode > installedVersionCode) {
                UpdateScreenUiState.UpdateAvailable(cached)
            } else {
                UpdateScreenUiState.UpToDate(cached)
            }
        }

        val registration = viewModel.listenAppUpdatesCurrent(
            onUpdate = { update ->
                isCheckingManually = false
                uiState = if (update.active && update.versionCode > installedVersionCode) {
                    UpdateScreenUiState.UpdateAvailable(update)
                } else {
                    UpdateScreenUiState.UpToDate(update)
                }
            },
            onError = { err ->
                isCheckingManually = false
                if (uiState is UpdateScreenUiState.Loading) {
                    uiState = UpdateScreenUiState.Error("Não foi possível verificar agora.")
                }
            }
        )

        onDispose {
            registration?.remove()
        }
    }

    fun triggerManualCheck() {
        isCheckingManually = true
        viewModel.fetchAppUpdatesCurrent(fromServer = true) { success ->
            isCheckingManually = false
            if (!success && uiState is UpdateScreenUiState.Loading) {
                uiState = UpdateScreenUiState.Error("Não foi possível conectar ao servidor de atualizações.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ATUALIZAÇÃO DO RONYCINE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("update_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { triggerManualCheck() },
                        enabled = !isCheckingManually,
                        modifier = Modifier.testTag("update_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = if (isCheckingManually) Color.Gray else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is UpdateScreenUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RonycineSmileLoader(
                            size = 56.dp,
                            color = BrandRed,
                            isVisible = true
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Verificando atualizações...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Consultando a versão oficial no servidor",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }

                is UpdateScreenUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(Color(0xFF2B1D1D), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Não foi possível verificar agora.",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                uiState = UpdateScreenUiState.Loading
                                triggerManualCheck()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(48.dp)
                                .testTag("retry_update_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tentar novamente", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                is UpdateScreenUiState.UpdateAvailable -> {
                    val update = state.update
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hero Icon
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(BrandRed.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Nova atualização disponível",
                            color = BrandRed,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = BrandRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Versão v${update.version} (Build ${update.versionCode})",
                                color = BrandRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (update.title.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = update.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (update.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = update.description,
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Changelog Card
                        if (update.changelog.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF262626))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ListAlt,
                                            contentDescription = null,
                                            tint = BrandRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "O QUE HÁ DE NOVO",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = update.changelog,
                                        color = Color(0xFFCCCCCC),
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Download Options
                        val cleanApk = update.apkUrl.trim()
                        val cleanApp = update.appDownloadUrl.trim()
                        val hasApk = cleanApk.isNotBlank()
                        val hasApp = cleanApp.isNotBlank()

                        // Main Download Button (Requirement 2, 10, 20)
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanApk))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Não foi possível abrir o navegador", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("main_download_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("BAIXAR ATUALIZAÇÃO", fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Download Options
                        Text(
                            text = "Outras opções de download:",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Opção 1: APK Link (Navegador sem modificar URL)
                        if (hasApk) {
                            DownloadOptionButton(
                                title = "BAIXAR PELO LINK",
                                description = "Abrir link direto no navegador",
                                icon = Icons.Default.Link,
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanApk))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Não foi possível abrir o navegador", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                testTag = "download_via_link_button",
                                isPrimary = false
                            )
                        }

                        // Opção 2: App Direct Download (Navegador)
                        if (hasApp) {
                            if (hasApk) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            DownloadOptionButton(
                                title = "BAIXAR APLICATIVO",
                                description = "Abrir endereço do aplicativo no navegador",
                                icon = Icons.Default.FileDownload,
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanApp))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Não foi possível abrir o navegador", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                testTag = "download_direct_button",
                                isPrimary = true
                            )
                        }

                        // Se nenhum link estiver configurado
                        if (!hasApk && !hasApp) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF333333))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Links de download sendo preparados pelo administrador.",
                                        color = Color.LightGray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        Text(
                            text = "Versão atual instalada: v$installedVersionName (Build $installedVersionCode)",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                is UpdateScreenUiState.UpToDate -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Color(0xFF064E3B), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Você está usando a versão mais recente.",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Não há novas atualizações pendentes no momento.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF262626))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Versão instalada",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "v$installedVersionName (Build $installedVersionCode)",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = { triggerManualCheck() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("check_again_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            if (isCheckingManually) {
                                RonycineSmileLoader(size = 20.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verificando...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VERIFICAR NOVAMENTE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "RONYCINE v$installedVersionName",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadOptionButton(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    isPrimary: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (isPrimary) BrandRed else Color(0xFF161616),
        border = if (isPrimary) null else BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isPrimary) Color.White.copy(alpha = 0.2f) else BrandRed.copy(alpha = 0.1f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) Color.White else BrandRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = description,
                    color = if (isPrimary) Color.White.copy(alpha = 0.8f) else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = if (isPrimary) Color.White.copy(alpha = 0.6f) else Color.DarkGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
