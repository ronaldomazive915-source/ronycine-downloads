package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.UserProfile
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ProfileOpState
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    authViewModel: AuthViewModel,
    profileToEdit: UserProfile? = null,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf(profileToEdit?.name ?: "") }
    var localErrorMsg by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    val profileOpState by authViewModel.profileOpState.collectAsState()

    val isEditing = profileToEdit != null

    // Reset profileOpState when screen launches or back is pressed
    LaunchedEffect(Unit) {
        authViewModel.clearProfileOpState()
    }

    // Handle delayed navigation on operation success
    LaunchedEffect(profileOpState) {
        if (profileOpState is ProfileOpState.Success) {
            delay(900)
            authViewModel.clearProfileOpState()
            onSuccess()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 80, outputStream)
                    selectedImageBytes = outputStream.toByteArray()
                }
            } catch (e: Exception) {
                localErrorMsg = "Erro ao processar imagem selecionada."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Editar Perfil" else "Criar Perfil",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            authViewModel.clearProfileOpState()
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Avatar Photo Selection Section
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .border(2.5.dp, BrandRed, CircleShape)
                        .clickable(enabled = profileOpState !is ProfileOpState.Loading) {
                            localErrorMsg = null
                            launcher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Foto do perfil selecionada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!profileToEdit?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profileToEdit?.avatarUrl,
                            contentDescription = "Foto do perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Sem foto",
                            tint = Color.Gray,
                            modifier = Modifier.size(60.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Alterar foto",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (selectedImageUri != null || !profileToEdit?.avatarUrl.isNullOrBlank()) "Alterar Foto" else "Adicionar Foto",
                    color = BrandRed,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = profileOpState !is ProfileOpState.Loading) {
                        localErrorMsg = null
                        launcher.launch("image/*")
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Input Field for Profile Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { input ->
                        if (input.length <= 30) {
                            name = input
                            localErrorMsg = null
                            authViewModel.clearProfileOpState()
                        }
                    },
                    label = { Text("Nome do Perfil", color = Color.Gray) },
                    placeholder = { Text("Ex: Ronaldo, Crianças, Filmes", color = Color.Gray.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0xFF33333C),
                        focusedLabelColor = BrandRed,
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = BrandRed,
                        focusedContainerColor = DarkSurface.copy(alpha = 0.5f),
                        unfocusedContainerColor = DarkSurface.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = profileOpState !is ProfileOpState.Loading && profileOpState !is ProfileOpState.Success
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${name.trim().length}/30",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Error Message Handling (Local or OpState)
                val activeError = localErrorMsg ?: (profileOpState as? ProfileOpState.Error)?.message
                if (activeError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF2A1215),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B0000)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = activeError,
                                color = Color(0xFFFFD2D2),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Primary Action Button (CRIAR PERFIL / SALVAR ALTERAÇÕES)
                val isLoading = profileOpState is ProfileOpState.Loading
                val isSuccess = profileOpState is ProfileOpState.Success
                val isButtonEnabled = name.trim().length >= 2 && !isLoading && !isSuccess

                Button(
                    onClick = {
                        keyboardController?.hide()
                        val trimmed = name.trim()

                        if (trimmed.isBlank()) {
                            localErrorMsg = "Digite um nome para o perfil."
                            return@Button
                        }

                        if (trimmed.length < 2) {
                            localErrorMsg = "Digite um nome com pelo menos 2 caracteres."
                            return@Button
                        }

                        localErrorMsg = null

                        if (isEditing && profileToEdit != null) {
                            authViewModel.updateProfile(
                                profile = profileToEdit,
                                newName = trimmed,
                                newImage = selectedImageBytes
                            )
                        } else {
                            authViewModel.createProfile(
                                name = trimmed,
                                image = selectedImageBytes
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSuccess) Color(0xFF2E7D32) else BrandRed,
                        disabledContainerColor = if (isLoading) BrandRed.copy(alpha = 0.6f) else Color(0xFF33333D),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isButtonEnabled || isLoading || isSuccess
                ) {
                    AnimatedContent(
                        targetState = profileOpState,
                        label = "button_state"
                    ) { state ->
                        when (state) {
                            is ProfileOpState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isEditing) "SALVANDO..." else "CRIANDO PERFIL...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            is ProfileOpState.Success -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isEditing) "Perfil atualizado!" else "✓ Perfil criado!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                }
                            }
                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (!isEditing) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (isEditing) "SALVAR ALTERAÇÕES" else "CRIAR PERFIL",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Delete Profile Button (only in Edit mode)
                if (isEditing && profileToEdit != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            authViewModel.deleteProfile(profileToEdit.id)
                        },
                        enabled = !isLoading && !isSuccess,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "EXCLUIR PERFIL",
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
