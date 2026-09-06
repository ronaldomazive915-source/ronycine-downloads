package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.UsernameCheckState
import com.example.util.UsernameUtils

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Collect auth state
    val currentUser by (authViewModel?.currentUser ?: remember { mutableStateOf(null) }).let {
        if (authViewModel != null) authViewModel.currentUser.collectAsState() else remember { mutableStateOf(null) }
    }
    val authState by (authViewModel?.authState ?: remember { mutableStateOf(AuthState.Idle) }).let {
        if (authViewModel != null) authViewModel.authState.collectAsState() else remember { mutableStateOf(AuthState.Idle) }
    }
    val usernameCheckState by (authViewModel?.usernameCheckState ?: remember { mutableStateOf(UsernameCheckState.Idle) }).let {
        if (authViewModel != null) authViewModel.usernameCheckState.collectAsState() else remember { mutableStateOf(UsernameCheckState.Idle) }
    }

    // Collect settings flows from ViewModel
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val newMoviesEnabled by viewModel.newMoviesEnabled.collectAsState()
    val newSeriesEnabled by viewModel.newSeriesEnabled.collectAsState()
    val newEpisodesEnabled by viewModel.newEpisodesEnabled.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val preferredQuality by viewModel.preferredQuality.collectAsState()
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()
    val dataSaverEnabled by viewModel.dataSaverEnabled.collectAsState()

    // Dialog States
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var newUsernameInput by remember { mutableStateOf("") }
    var usernameSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
            Toast.makeText(context, "Notificações ativadas com sucesso!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setNotificationsEnabled(false)
            Toast.makeText(context, "Permissão de notificações recusada.", Toast.LENGTH_SHORT).show()
        }
    }

    // Quality Dialog
    if (showQualityDialog) {
        val qualities = listOf("Automática", "1080p", "720p", "480p")
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("🎥 Qualidade de Reprodução", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Escolha a preferência de qualidade. O player aplicará a resolução escolhida quando o link/fonte permitir.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setPreferredQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = quality,
                                color = if (preferredQuality == quality) BrandRed else Color.White,
                                fontWeight = if (preferredQuality == quality) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            if (preferredQuality == quality) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Fechar", color = Color.White)
                }
            }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        val languages = listOf("Português", "Português (Brasil)", "Automático")
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("🌐 Idioma Preferido", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Selecione o idioma preferido para áudio/legenda no aplicativo.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    languages.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setPreferredLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = lang,
                                color = if (preferredLanguage == lang) BrandRed else Color.White,
                                fontWeight = if (preferredLanguage == lang) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            if (preferredLanguage == lang) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Fechar", color = Color.White)
                }
            }
        )
    }

    // Clear Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("🗑 Limpar Cache", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Deseja realmente limpar o cache?\n\nIsso removerá apenas arquivos temporários da memória. O catálogo, lista de favoritos e histórico de visualização serão mantidos intactos.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearAppCache(context)
                        Toast.makeText(context, "Cache limpo com sucesso.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Limpar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearCacheDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    // Permission Rationale Dialog (Android 13+)
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("🔔 Permissão de Notificações", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Deseja receber avisos quando novos filmes e séries forem adicionados ao RONYCINE?",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("PERMITIR NOTIFICAÇÕES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        viewModel.setNotificationsEnabled(false)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("AGORA NÃO", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RONY", color = BrandRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("CINE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aplicativo Oficial RONYCINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Versão Instalada: 1.5.0 (Build 105)", color = TextSecondary, fontSize = 12.sp)
                    Text("Fundador: RONALDO MAZIVE", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "A plataforma completa de filmes, séries, TV ao vivo e catálogo sempre atualizado.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal/Dialog: ALTERAR / DEFINIR NOME DE USUÁRIO
    if (showUsernameDialog) {
        val currentUsernameDisplay = if (!currentUser?.username.isNullOrBlank()) currentUser!!.username!! else "Ainda não definido"
        val isDefiningFirstTime = currentUser?.username.isNullOrBlank()

        AlertDialog(
            onDismissRequest = {
                showUsernameDialog = false
                authViewModel?.clearUsernameCheckState()
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDefiningFirstTime) "DEFINIR NOME DE USUÁRIO" else "ALTERAR NOME DE USUÁRIO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Nome de usuário atual
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Nome de usuário atual:",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = currentUsernameDisplay,
                            color = if (!isDefiningFirstTime) BrandRed else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Text(
                        text = "Novo nome de usuário:",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = newUsernameInput,
                        onValueChange = { raw ->
                            val clean = raw.trim()
                            newUsernameInput = clean
                            authViewModel?.checkUsernameAvailability(clean)
                        },
                        placeholder = { Text("@novo_nome", color = Color.DarkGray, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_username"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground.copy(alpha = 0.85f),
                            unfocusedContainerColor = DarkBackground.copy(alpha = 0.65f),
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = BrandRed,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = BrandRed
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = null,
                                tint = if (newUsernameInput.isNotEmpty()) BrandRed else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // Feedback de disponibilidade em tempo real
                    when (val state = usernameCheckState) {
                        is UsernameCheckState.Checking -> {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = BrandRed,
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verificando disponibilidade...",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        is UsernameCheckState.Available -> {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "✓ ${state.formattedUsername} está disponível",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is UsernameCheckState.Unavailable -> {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "✕ ${state.reason}",
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = newUsernameInput.trim()
                        if (authViewModel != null) {
                            authViewModel.claimOrUpdateUsername(
                                rawUsername = input,
                                onSuccess = {
                                    val formatted = UsernameUtils.formatDisplay(input)
                                    showUsernameDialog = false
                                    authViewModel.clearUsernameCheckState()
                                    usernameSuccessMessage = formatted
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp),
                    enabled = authState !is AuthState.Loading && usernameCheckState is UsernameCheckState.Available,
                    modifier = Modifier.testTag("dialog_save_username_button")
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("SALVAR", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.5.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUsernameDialog = false
                        authViewModel?.clearUsernameCheckState()
                    }
                ) {
                    Text("CANCELAR", color = Color.Gray, fontSize = 12.5.sp)
                }
            }
        )
    }

    // Modal/Dialog de Confirmação de Sucesso
    if (usernameSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { usernameSuccessMessage = null },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nome de usuário alterado",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Seu novo nome de usuário é:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = usernameSuccessMessage ?: "",
                        color = BrandRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Você já pode usá-lo imediatamente para entrar e ser reconhecido no RONYCINE.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { usernameSuccessMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "DEFINIÇÕES",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }

        HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

        // Settings Content List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 0: CONTA
            SettingsSectionHeader("CONTA")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // Nome
                    SettingsInfoRow(
                        icon = Icons.Default.Person,
                        iconTint = BrandRed,
                        title = "Nome",
                        valueText = currentUser?.displayName?.ifBlank { "Usuário RONYCINE" } ?: (if (currentUser != null) "Usuário RONYCINE" else "Não autenticado"),
                        testTag = "row_account_name"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // E-mail
                    SettingsInfoRow(
                        icon = Icons.Default.Email,
                        iconTint = BrandRed,
                        title = "E-mail",
                        valueText = currentUser?.email?.ifBlank { "Não informado" } ?: "Não autenticado",
                        testTag = "row_account_email"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // @Nome de usuário
                    val hasUsername = !currentUser?.username.isNullOrBlank()
                    val displayUsername = if (hasUsername) currentUser!!.username!! else "Ainda não definido"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("row_account_username"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BrandRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nome de usuário",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = displayUsername,
                                color = if (hasUsername) Color(0xFF10B981) else Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = if (hasUsername) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                newUsernameInput = currentUser?.username?.removePrefix("@") ?: ""
                                authViewModel?.clearUsernameCheckState()
                                showUsernameDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("button_change_username")
                        ) {
                            Text(
                                text = if (hasUsername) "ALTERAR" else "CRIAR NOME DE USUÁRIO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Section 1: NOTIFICAÇÕES
            SettingsSectionHeader("NOTIFICAÇÕES")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // 1. Notificações Geral
                    SettingsSwitchRow(
                        icon = Icons.Default.Notifications,
                        iconTint = BrandRed,
                        title = "Notificações Geral",
                        description = "Receber notificações sobre novos filmes, séries e novidades.",
                        checked = notificationsEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (!hasPerm) {
                                        showPermissionRationaleDialog = true
                                    } else {
                                        viewModel.setNotificationsEnabled(true)
                                    }
                                } else {
                                    viewModel.setNotificationsEnabled(true)
                                }
                            } else {
                                viewModel.setNotificationsEnabled(false)
                            }
                        },
                        testTag = "switch_notifications_general"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 2. Novos filmes
                    SettingsSwitchRow(
                        icon = Icons.Default.Movie,
                        iconTint = BrandRed,
                        title = "Novos filmes",
                        description = "Receber notificações quando novos filmes forem adicionados.",
                        checked = newMoviesEnabled && notificationsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = { viewModel.setNewMoviesEnabled(it) },
                        testTag = "switch_new_movies"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 3. Novas séries
                    SettingsSwitchRow(
                        icon = Icons.Default.Tv,
                        iconTint = BrandRed,
                        title = "Novas séries",
                        description = "Receber notificações quando novas séries forem adicionadas.",
                        checked = newSeriesEnabled && notificationsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = { viewModel.setNewSeriesEnabled(it) },
                        testTag = "switch_new_series"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 4. Novos episódios
                    SettingsSwitchRow(
                        icon = Icons.Default.PlayCircle,
                        iconTint = BrandRed,
                        title = "Novos episódios",
                        description = "Receber notificações quando novos episódios forem adicionados.",
                        checked = newEpisodesEnabled && notificationsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = { viewModel.setNewEpisodesEnabled(it) },
                        testTag = "switch_new_episodes"
                    )
                }
            }

            // Section 2: REPRODUÇÃO E MÍDIA
            SettingsSectionHeader("REPRODUÇÃO E MÍDIA")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // 5. Reprodução automática
                    SettingsSwitchRow(
                        icon = Icons.Default.PlayArrow,
                        iconTint = BrandRed,
                        title = "Reprodução automática",
                        description = "Controlar reprodução automática de trailers e prévias.",
                        checked = autoplayEnabled,
                        onCheckedChange = { viewModel.setAutoplayEnabled(it) },
                        testTag = "switch_autoplay"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 6. Qualidade de reprodução
                    SettingsValueRow(
                        icon = Icons.Default.HighQuality,
                        iconTint = Color(0xFF38BDF8),
                        title = "Qualidade de reprodução",
                        description = "Preferência aplicada quando o player permitir.",
                        valueText = preferredQuality,
                        onClick = { showQualityDialog = true },
                        testTag = "row_quality"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 7. Idioma
                    SettingsValueRow(
                        icon = Icons.Default.Language,
                        iconTint = Color(0xFF38BDF8),
                        title = "Idioma",
                        description = "Idioma preferido no aplicativo.",
                        valueText = preferredLanguage,
                        onClick = { showLanguageDialog = true },
                        testTag = "row_language"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 8. Economia de dados
                    SettingsSwitchRow(
                        icon = Icons.Default.DataUsage,
                        iconTint = Color(0xFF38BDF8),
                        title = "Economia de dados",
                        description = "Reduzir carregamentos desnecessários e economizar dados.",
                        checked = dataSaverEnabled,
                        onCheckedChange = { viewModel.setDataSaverEnabled(it) },
                        testTag = "switch_datasaver"
                    )
                }
            }

            // Section 3: ARMAZENAMENTO E DADOS
            SettingsSectionHeader("ARMAZENAMENTO")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // 9. Downloads
                    SettingsClickableRow(
                        icon = Icons.Default.Download,
                        iconTint = Color(0xFF38BDF8),
                        title = "Downloads",
                        description = "Local de armazenamento e gerenciar downloads.",
                        actionContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = onNavigateToDownloads,
                        testTag = "row_downloads"
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // 10. Limpar cache
                    SettingsClickableRow(
                        icon = Icons.Default.DeleteSweep,
                        iconTint = BrandRed,
                        title = "Limpar cache",
                        description = "Apagar apenas arquivos temporários da memória.",
                        actionContent = {
                            Button(
                                onClick = { showClearCacheDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("button_clear_cache")
                            ) {
                                Text("LIMPAR CACHE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        },
                        onClick = { showClearCacheDialog = true },
                        testTag = "row_clear_cache"
                    )
                }
            }

            // Section 4: INFORMACÕES
            SettingsSectionHeader("INFORMAÇÕES")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                // 11. Sobre
                SettingsClickableRow(
                    icon = Icons.Default.Info,
                    iconTint = Color.White,
                    title = "Sobre o RONYCINE",
                    description = "Versão 1.5.0 (Build 105) • Fundador: RONALDO MAZIVE",
                    actionContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        if (onNavigateToInfo != null) {
                            onNavigateToInfo()
                        } else {
                            showAboutDialog = true
                        }
                    },
                    testTag = "row_about"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        color = BrandRed,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = if (enabled) 0.15f else 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Color.White else Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = if (enabled) TextSecondary else Color.DarkGray,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandRed,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    valueText: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = Color(0xFF222222),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueText,
                    color = BrandRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    actionContent: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        actionContent()
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    valueText: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                color = TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
