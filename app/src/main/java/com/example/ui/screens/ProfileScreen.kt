package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.config.AppShareConfig
import com.example.data.remote.UserProfile
import com.example.data.remote.UpdateFetchStatus
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.ProfilePhotoActionSheet
import com.example.ui.components.AvatarSelectionSheet
import com.example.ui.components.ShareAppModal
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ProfileOpState
import com.example.ui.viewmodel.UsernameCheckState

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToMyList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToProfileSelection: () -> Unit = {},
    onNavigateToCreateProfile: () -> Unit = {},
    onNavigateToInfo: () -> Unit = {},
    onNavigateToUpdateScreen: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showShareModal by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showPhotoActionSheet by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDiagnosticDetailsModal by remember { mutableStateOf(false) }
    var newUsernameInput by remember { mutableStateOf("") }
    var profileForPinEntry by remember { mutableStateOf<UserProfile?>(null) }

    // Pull-To-Refresh State with Smile Loader
    val maxPullPx = with(LocalDensity.current) { 80.dp.toPx() }
    val triggerPx = with(LocalDensity.current) { 60.dp.toPx() }
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isRefreshSuccess by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && pullOffsetPx > 0f && !isRefreshing) {
                    val newOffset = (pullOffsetPx + available.y).coerceAtLeast(0f)
                    val consumed = pullOffsetPx - newOffset
                    pullOffsetPx = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0 && !isRefreshing) {
                    val newOffset = (pullOffsetPx + available.y * 0.5f).coerceAtMost(maxPullPx * 1.5f)
                    val consumedY = newOffset - pullOffsetPx
                    pullOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshing) {
                    if (pullOffsetPx >= triggerPx) {
                        isRefreshing = true
                        pullOffsetPx = maxPullPx
                        coroutineScope.launch {
                            viewModel.manualRefreshCatalog()
                            delay(700L)
                            isRefreshSuccess = true
                            delay(400L)
                            isRefreshSuccess = false
                            isRefreshing = false
                            pullOffsetPx = 0f
                        }
                    } else {
                        pullOffsetPx = 0f
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val authState by authViewModel.authState.collectAsState()
    val usernameCheckState by authViewModel.usernameCheckState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val activeProfile by authViewModel.activeProfile.collectAsState()
    val userProfiles by authViewModel.userProfiles.collectAsState()
    val profileOpState by authViewModel.profileOpState.collectAsState()

    // Activity stats
    val myList by viewModel.myList.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()

    // Feedback on profile operations
    LaunchedEffect(profileOpState) {
        when (val state = profileOpState) {
            is ProfileOpState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                authViewModel.clearProfileOpState()
            }
            is ProfileOpState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                authViewModel.clearProfileOpState()
            }
            else -> {}
        }
    }

    if (showShareModal) {
        ShareAppModal(
            installUrl = AppShareConfig.defaultInstallUrl,
            onDismissRequest = { showShareModal = false }
        )
    }

    // Main Profile Photo Action Sheet (Camera, Gallery, Catalog, Download, Remove)
    if (showPhotoActionSheet && activeProfile != null) {
        ProfilePhotoActionSheet(
            profile = activeProfile,
            onDismissRequest = { showPhotoActionSheet = false },
            onPhotoSelected = { bytes ->
                authViewModel.updateActiveProfileAvatar(presetUrl = null, imageBytes = bytes)
            },
            onOpenPresetCatalog = {
                showAvatarPickerSheet = true
            },
            onRemovePhoto = {
                authViewModel.removeActiveProfilePhoto()
            },
            onDownloadPhoto = {
                authViewModel.downloadProfilePhoto(context, activeProfile!!) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Avatar Picker Sheet for Active Profile
    if (showAvatarPickerSheet) {
        AvatarSelectionSheet(
            currentAvatarUrl = activeProfile?.avatarUrl,
            onDismissRequest = { showAvatarPickerSheet = false },
            onAvatarSelected = { presetUrl, imageBytes ->
                authViewModel.updateActiveProfileAvatar(presetUrl, imageBytes)
            }
        )
    }

    // Edit Active Profile Name Dialog
    if (showEditProfileDialog && activeProfile != null) {
        var editNameInput by remember { mutableStateOf(activeProfile?.name ?: "") }
        var editNameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EDITAR PERFIL",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Altere o nome e preferências do perfil ativo.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = {
                            if (it.length <= 30) {
                                editNameInput = it
                                editNameError = null
                            }
                        },
                        label = { Text("Nome do Perfil", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = BrandRed
                        )
                    )

                    if (editNameError != null) {
                        Text(
                            text = editNameError!!,
                            color = Color(0xFFEF4444),
                            fontSize = 11.5.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showEditProfileDialog = false
                            showAvatarPickerSheet = true
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alterar Foto / Avatar", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editNameInput.trim()
                        if (trimmed.length < 2) {
                            editNameError = "Digite um nome com pelo menos 2 caracteres."
                            return@Button
                        }
                        showEditProfileDialog = false
                        activeProfile?.let { prof ->
                            authViewModel.updateProfile(prof, trimmed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("CANCELAR", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Encerrar Sessão",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Deseja realmente sair da sua conta no RONYCINE?",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        authViewModel.signOut()
                        Toast.makeText(context, "Sessão encerrada com sucesso", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SAIR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("CANCELAR", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        )
    }

    if (showUsernameDialog) {
        val currentUsernameDisplay = if (!currentUser?.username.isNullOrBlank()) currentUser!!.username!! else "Ainda não definido"
        val isDefiningFirstTime = currentUser?.username.isNullOrBlank()

        AlertDialog(
            onDismissRequest = {
                showUsernameDialog = false
                authViewModel.clearUsernameCheckState()
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
                            authViewModel.checkUsernameAvailability(clean)
                        },
                        placeholder = { Text("@novo_nome", color = Color.DarkGray, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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

                    when (val state = usernameCheckState) {
                        is UsernameCheckState.Checking -> {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RonycineSmileLoader(
                                    size = 14.dp,
                                    color = BrandRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verificando disponibilidade...",
                                    color = TextSecondary,
                                    fontSize = 11.sp
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
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${state.formattedUsername} disponível",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.5.sp,
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
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = state.reason,
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.5.sp,
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
                        authViewModel.claimOrUpdateUsername(
                            rawUsername = input,
                            onSuccess = {
                                showUsernameDialog = false
                                authViewModel.clearUsernameCheckState()
                                Toast.makeText(context, "Nome de usuário atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp),
                    enabled = authState !is AuthState.Loading && usernameCheckState is UsernameCheckState.Available
                ) {
                    if (authState is AuthState.Loading) {
                        RonycineSmileLoader(
                            color = Color.White,
                            size = 16.dp
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
                        authViewModel.clearUsernameCheckState()
                    }
                ) {
                    Text("CANCELAR", color = Color.Gray, fontSize = 12.5.sp)
                }
            }
        )
    }

    if (showDiagnosticDetailsModal) {
        val devModelModal = remember { android.os.Build.MODEL ?: "Android Device" }
        val devIdModal = remember {
            com.example.data.remote.FirebaseService.getInstance(context.applicationContext).getOrGeneratePersistentDeviceId()
        }
        val isBlockedModal by viewModel.isDeviceBlocked.collectAsState()
        val isAdminAuthorizedModal by viewModel.isAdminAuthorized.collectAsState()

        var isFirebaseConnectedModal by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(showDiagnosticDetailsModal) {
            val fbService = com.example.data.remote.FirebaseService.getInstance(context.applicationContext)
            isFirebaseConnectedModal = fbService.testFirestoreConnection()
        }

        var adminRecordStatusModal by remember { mutableStateOf("VERIFICANDO...") }
        LaunchedEffect(showDiagnosticDetailsModal) {
            val fbService = com.example.data.remote.FirebaseService.getInstance(context.applicationContext)
            val dId = fbService.getOrGeneratePersistentDeviceId()
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("devices").document(dId).get().addOnSuccessListener { snap ->
                    if (snap != null && snap.exists()) {
                        val hasAdmin = snap.getBoolean("adminAccess") ?: false
                        adminRecordStatusModal = if (hasAdmin) "ENCONTRADO (ADMIN)" else "ENCONTRADO (COMUM)"
                    } else {
                        adminRecordStatusModal = "NÃO ENCONTRADO"
                    }
                }.addOnFailureListener {
                    adminRecordStatusModal = "ERRO AO BUSCAR"
                }
            } catch (e: Exception) {
                adminRecordStatusModal = "ERRO"
            }
        }

        val connectionTextModal = when (isFirebaseConnectedModal) {
            true -> "CONECTADO"
            false -> "ERRO DE CONEXÃO"
            null -> "TESTANDO..."
        }
        val connectionColorModal = when (isFirebaseConnectedModal) {
            true -> Color(0xFF10B981)
            false -> Color(0xFFEF4444)
            null -> Color.Gray
        }

        AlertDialog(
            onDismissRequest = { showDiagnosticDetailsModal = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "DETALHES DO DIAGNÓSTICO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiagnosticRow("Dispositivo", devModelModal)
                    DiagnosticRow("Device ID", devIdModal)
                    DiagnosticRow("Admin State", if (isAdminAuthorizedModal) "AUTORIZADO" else "NÃO AUTORIZADO", valueColor = if (isAdminAuthorizedModal) Color(0xFF10B981) else Color(0xFFEF4444))
                    DiagnosticRow("Acesso", if (isBlockedModal) "BLOQUEADO" else "ATIVO", valueColor = if (isBlockedModal) Color(0xFFEF4444) else Color(0xFF10B981))
                    DiagnosticRow("Registro Firestore", adminRecordStatusModal)
                    DiagnosticRow("Firebase API", connectionTextModal, valueColor = connectionColorModal)

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.initDeviceManager()
                            Toast.makeText(context, "Re-sincronizando autorização...", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Forçar Re-sincronização", color = Color.White, fontSize = 11.5.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDiagnosticDetailsModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .nestedScroll(nestedScrollConnection)
            .testTag("profile_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pull-To-Refresh Smile Loader Header
            AnimatedVisibility(
                visible = pullOffsetPx > 0f || isRefreshing || isRefreshSuccess,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isRefreshSuccess -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Perfil e catálogo atualizados!",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        isRefreshing -> {
                            RonycineSmileLoader(
                                size = 32.dp,
                                color = BrandRed
                            )
                        }
                        else -> {
                            val progress = (pullOffsetPx / triggerPx).coerceIn(0f, 1f)
                            RonycineSmileLoader(
                                size = 32.dp,
                                color = BrandRed,
                                progress = progress
                            )
                        }
                    }
                }
            }
        // ---------------------------------------------------------
        // Top Header
        // ---------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "MEU PERFIL",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Central da sua conta e preferências",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ---------------------------------------------------------
        // 1. Unified Profile Header / Card
        // ---------------------------------------------------------
        if (currentUser != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar with Camera Click Overlay
                        ProfileAvatar(
                            profile = activeProfile,
                            size = 68.dp,
                            borderWidth = 2.dp,
                            borderColor = BrandRed,
                            showCameraBadge = true,
                            testTag = "profile_avatar_box",
                            onClick = { showPhotoActionSheet = true }
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeProfile?.name ?: currentUser?.displayName ?: "Perfil Principal",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (activeProfile?.isVerified == true || currentUser?.isVerified == true) {
                                    VerifiedBadge(size = 18.dp)
                                }

                                IconButton(
                                    onClick = { showEditProfileDialog = true },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar Perfil",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (!currentUser?.username.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = currentUser!!.username!!,
                                        color = BrandRed,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (activeProfile?.isVerified == true || currentUser?.isVerified == true) {
                                        VerifiedBadge(size = 14.dp)
                                    }
                                }
                            }

                            Text(
                                text = currentUser!!.email,
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Badges Row (Role + Kids + Status)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = if (currentUser?.role == "FOUNDER") Color(0xFF7C3AED).copy(alpha = 0.25f) else Color(0xFF0F172A),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (currentUser?.role == "FOUNDER") Color(0xFFC084FC) else Color(0xFF38BDF8).copy(alpha = 0.6f)
                                    )
                                ) {
                                    Text(
                                        text = when (currentUser?.role) {
                                            "FOUNDER" -> "⭐ FUNDADOR"
                                            "ADMIN" -> "🛡️ ADMINISTRADOR"
                                            "USER" -> "👤 MEMBRO VIP"
                                            else -> "👤 CONTA ATIVA"
                                        },
                                        color = if (currentUser?.role == "FOUNDER") Color(0xFFE9D5FF) else Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                if (activeProfile?.isKidsProfile == true) {
                                    Surface(
                                        color = Color(0xFF0284C7).copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFF38BDF8))
                                    ) {
                                        Text(
                                            text = "👶 MODO INFANTIL",
                                            color = Color(0xFF7DD3FC),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // Quick Profile Selector Row (Perfis da Conta)
                    // ---------------------------------------------------------
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PERFIS DA CONTA (${userProfiles.size}/5)",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )

                            Text(
                                text = "Toque para alternar",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(userProfiles, key = { it.id }) { prof ->
                                val isActive = activeProfile?.id == prof.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(62.dp)
                                        .clickable {
                                            if (!isActive) {
                                                if (!prof.pinHash.isNullOrBlank()) {
                                                    profileForPinEntry = prof
                                                } else {
                                                    authViewModel.selectProfile(prof)
                                                    Toast.makeText(context, "Perfil alterado para ${prof.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .testTag("profile_item_${prof.id}")
                                ) {
                                    ProfileAvatar(
                                        profile = prof,
                                        size = 50.dp,
                                        borderWidth = if (isActive) 2.5.dp else 1.dp,
                                        borderColor = if (isActive) BrandRed else CardBorder,
                                        showActiveCheck = isActive,
                                        testTag = "profile_avatar_${prof.id}"
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = prof.name,
                                            color = if (isActive) Color.White else TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (prof.isVerified) {
                                            VerifiedBadge(size = 11.dp, showToastOnClick = false)
                                        }
                                        if (!prof.pinHash.isNullOrBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Protegido por PIN",
                                                tint = BrandRed,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }

                                    if (prof.isKidsProfile) {
                                        Text(
                                            text = if (isActive) "👶 Ativo" else "👶 Kids",
                                            color = if (isActive) Color(0xFF38BDF8) else TextSecondary.copy(alpha = 0.8f),
                                            fontSize = 8.5.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    } else if (isActive) {
                                        Text(
                                            text = "✓ Ativo",
                                            color = BrandRed,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Add profile button if under max (5)
                            if (userProfiles.size < 5) {
                                item {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(62.dp)
                                            .clickable { onNavigateToCreateProfile() }
                                            .testTag("add_profile_quick_button")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(DarkBackground)
                                                .border(1.dp, CardBorder, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Adicionar Perfil",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "+ Novo",
                                            color = TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons below profile info
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                authViewModel.lockCurrentProfile()
                                onNavigateToProfileSelection()
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchAccount,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Trocar Perfil",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                newUsernameInput = currentUser?.username?.removePrefix("@") ?: ""
                                authViewModel.clearUsernameCheckState()
                                showUsernameDialog = true
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!currentUser?.username.isNullOrBlank()) "Editar @" else "Definir @",
                                color = BrandRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Visitor / Login Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLogin() },
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(BrandRed.copy(alpha = 0.15f), DarkSurface)
                            )
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.2f))
                            .border(1.5.dp, BrandRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Entrar no RONYCINE",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sincronize favoritos, histórico e perfis exclusivos.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    Button(
                        onClick = onNavigateToLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("ENTRAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 2. Activity Metrics Bar
        // ---------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActivityStatCard(
                label = "Favoritos",
                count = myList.size.toString(),
                icon = Icons.Default.Bookmark,
                tint = BrandRed,
                onClick = onNavigateToMyList,
                modifier = Modifier.weight(1f)
            )
            ActivityStatCard(
                label = "Progresso",
                count = continueWatching.size.toString(),
                icon = Icons.Default.PlayCircle,
                tint = Color(0xFF38BDF8),
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f)
            )
            ActivityStatCard(
                label = "Histórico",
                count = watchHistory.size.toString(),
                icon = Icons.Default.History,
                tint = Color(0xFF10B981),
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f)
            )
        }

        // ---------------------------------------------------------
        // 3. Minha Atividade / Navegação Central
        // ---------------------------------------------------------
        SectionHeader(title = "MINHA ATIVIDADE")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column {
                ProfileMenuItem(
                    icon = Icons.Default.Bookmark,
                    iconTint = BrandRed,
                    title = "Minha Lista de Favoritos",
                    badgeText = if (myList.isNotEmpty()) "${myList.size}" else null,
                    onClick = onNavigateToMyList,
                    testTag = "profile_my_list_button"
                )
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                ProfileMenuItem(
                    icon = Icons.Default.PlayCircleOutline,
                    iconTint = Color(0xFF38BDF8),
                    title = "Continuar Assistindo",
                    badgeText = if (continueWatching.isNotEmpty()) "${continueWatching.size}" else null,
                    onClick = onNavigateToHistory,
                    testTag = "profile_continue_watching_button"
                )
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                ProfileMenuItem(
                    icon = Icons.Default.History,
                    iconTint = Color(0xFF10B981),
                    title = "Histórico de Visualizações",
                    badgeText = if (watchHistory.isNotEmpty()) "${watchHistory.size}" else null,
                    onClick = onNavigateToHistory,
                    testTag = "profile_history_button"
                )
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                ProfileMenuItem(
                    icon = Icons.Default.FileDownload,
                    iconTint = Color(0xFF38BDF8),
                    title = "Meus Downloads",
                    subtitle = "Filmes e séries salvos para offline",
                    onClick = onNavigateToDownloads,
                    testTag = "profile_downloads_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------
        // New Premium Update Card
        // ---------------------------------------------------------
        UpdateStatusCard(
            viewModel = viewModel,
            onNavigateToUpdateScreen = onNavigateToUpdateScreen
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------------------------------------------------
        // 4. Compartilhar Aplicativo Card
        // ---------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_share_app_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B0D18)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f)),
            onClick = {
                AppShareConfig.shareAppViaAndroidSharesheet(context)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BrandRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compartilhar Aplicativo",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Envie o link oficial do RONYCINE para seus amigos",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // ---------------------------------------------------------
        // 5. Preferências & Sistema
        // ---------------------------------------------------------
        SectionHeader(title = "PREFERÊNCIAS & SISTEMA")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column {
                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    iconTint = BrandRed,
                    title = "Configurações do Aplicativo",
                    subtitle = "Notificações, qualidade, idioma e cache",
                    onClick = onNavigateToSettings,
                    testTag = "profile_settings_button"
                )

                val isAdminAuthorizedState by viewModel.isAdminAuthorized.collectAsState()
                val isAdminRole = currentUser?.role == "ADMIN"
                val isFounderRole = currentUser?.role == "FOUNDER"

                if (isAdminAuthorizedState || isAdminRole || isFounderRole) {
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.AdminPanelSettings,
                        iconTint = BrandRed,
                        title = "Painel Administrativo",
                        subtitle = "Gerenciamento do catálogo, usuários e avisos",
                        onClick = onNavigateToAdmin,
                        testTag = "profile_admin_button"
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // 7. Sair da Conta (Quando Autenticado)
        // ---------------------------------------------------------
        if (currentUser != null) {
            Button(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1212)),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ENCERRAR SESSÃO",
                    color = Color(0xFFF87171),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ---------------------------------------------------------
        // 8. Diagnóstico de Acesso (Compacto)
        // ---------------------------------------------------------
        val devModel = remember { android.os.Build.MODEL ?: "Android Device" }
        val isAdminAuthorized by viewModel.isAdminAuthorized.collectAsState()
        val isBlocked by viewModel.isDeviceBlocked.collectAsState()
        val isEligibleForDebug = devModel.contains("MAO-LX9", ignoreCase = true) ||
                                 devModel.contains("sdk_gphone64_arm64", ignoreCase = true) ||
                                 isAdminAuthorized

        if (isEligibleForDebug) {
            var isFirebaseConnected by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                val fbService = com.example.data.remote.FirebaseService.getInstance(context.applicationContext)
                isFirebaseConnected = fbService.testFirestoreConnection()
            }

            val connectionText = when (isFirebaseConnected) {
                true -> "CONECTADO"
                false -> "OFFLINE"
                null -> "VERIFICANDO..."
            }
            val connectionColor = when (isFirebaseConnected) {
                true -> Color(0xFF10B981)
                false -> Color(0xFFEF4444)
                null -> Color.Gray
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Text(
                                text = "🔧 Diagnóstico de acesso",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isBlocked || isFirebaseConnected == false) Color(0xFFEF4444) else Color(0xFF10B981),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    DiagnosticRow("Dispositivo", devModel)
                    DiagnosticRow("Acesso", if (isBlocked) "BLOQUEADO" else "ATIVO", valueColor = if (isBlocked) Color(0xFFEF4444) else Color(0xFF10B981))
                    DiagnosticRow("Firebase", connectionText, valueColor = connectionColor)

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDiagnosticDetailsModal = true }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ver detalhes ›",
                            color = BrandRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Modal de verificação de PIN para troca rápida de perfil
    profileForPinEntry?.let { targetProfile ->
        com.example.ui.components.ProfilePinDialog(
            profile = targetProfile,
            onDismissRequest = { profileForPinEntry = null },
            onPinVerified = {
                profileForPinEntry = null
                authViewModel.unlockAndSelectProfile(targetProfile)
                Toast.makeText(context, "Perfil alterado para ${targetProfile.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun ActivityStatCard(
    label: String,
    count: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun UpdateStatusCard(
    viewModel: com.example.ui.viewmodel.MainViewModel,
    onNavigateToUpdateScreen: () -> Unit
) {
    val hasUpdateAvailable by viewModel.shouldShowUpdateDot.collectAsState()
    val fetchStatus by viewModel.updateFetchStatus.collectAsState()
    val currentVersion = remember { viewModel.getInstalledVersionName() }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToUpdateScreen() }
            .testTag("profile_update_card"),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (hasUpdateAvailable) BrandRed.copy(alpha = 0.5f) else CardBorder
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasUpdateAvailable) BrandRed.copy(alpha = 0.15f) 
                            else Color.White.copy(alpha = 0.05f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (hasUpdateAvailable) BrandRed else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Buscar atualização",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val subtitle = when {
                        fetchStatus is UpdateFetchStatus.Loading -> "Verificando atualizações..."
                        hasUpdateAvailable -> "Nova atualização disponível"
                        fetchStatus is UpdateFetchStatus.Error -> "Não foi possível verificar agora"
                        else -> "Verifique se há uma nova versão"
                    }
                    
                    Text(
                        text = subtitle,
                        color = if (hasUpdateAvailable) BrandRed else TextSecondary,
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!hasUpdateAvailable && fetchStatus !is UpdateFetchStatus.Loading) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Você está atualizado • $currentVersion",
                                color = Color(0xFF4ADE80),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (hasUpdateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                                    .background(BrandRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Toque para baixar nova versão",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (fetchStatus is UpdateFetchStatus.Error) {
                            Text(
                                text = "Tentar novamente",
                                color = BrandRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Pulsing Red Dot
            if (hasUpdateAvailable) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = alpha))
                        .border(1.dp, Color.Red.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    showDot: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )

                if (showDot) {
                    // Glow effect
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha * 0.4f))
                    )
                    
                    // Main dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                            .border(1.dp, DarkBackground, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (showDot && title == "Buscar atualizações") {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Pulsing red dot next to title
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = alpha))
                        )
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = if (showDot && title == "Buscar atualizações") Color.White.copy(alpha = 0.9f) else TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (badgeText != null) {
                Surface(
                    color = BrandRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, BrandRed.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        color = BrandRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

