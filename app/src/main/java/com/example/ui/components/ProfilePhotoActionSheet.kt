package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.remote.UserProfile
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.util.ProfilePhotoUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePhotoActionSheet(
    profile: UserProfile?,
    currentPreviewBytes: ByteArray? = null,
    currentPresetUrl: String? = null,
    onDismissRequest: () -> Unit,
    onPhotoSelected: (ByteArray) -> Unit,
    onOpenPresetCatalog: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDownloadPhoto: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var rawSelectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    val hasCustomPhoto = remember(profile, currentPreviewBytes, currentPresetUrl) {
        currentPreviewBytes != null ||
                !currentPresetUrl.isNullOrBlank() ||
                !profile?.photoUrl.isNullOrBlank() ||
                !profile?.avatarUrl.isNullOrBlank()
    }

    // Camera Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val bitmap = ProfilePhotoUtils.loadBitmapFromUri(context, tempCameraUri!!)
            if (bitmap != null) {
                rawSelectedBitmap = bitmap
                showCropDialog = true
            } else {
                Toast.makeText(context, "Não foi possível processar a foto da câmera.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val (uri, file) = ProfilePhotoUtils.createTempCameraUri(context)
                tempCameraUri = uri
                tempCameraFile = file
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao abrir câmera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permissão de câmera necessária para tirar fotos.", Toast.LENGTH_LONG).show()
        }
    }

    // Gallery Picker Launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = ProfilePhotoUtils.loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                rawSelectedBitmap = bitmap
                showCropDialog = true
            } else {
                Toast.makeText(context, "Não foi possível carregar a imagem selecionada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = ProfilePhotoUtils.loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                rawSelectedBitmap = bitmap
                showCropDialog = true
            } else {
                Toast.makeText(context, "Não foi possível carregar a imagem selecionada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Crop Dialog
    if (showCropDialog && rawSelectedBitmap != null) {
        ImageCropDialog(
            sourceBitmap = rawSelectedBitmap!!,
            onDismissRequest = {
                showCropDialog = false
                rawSelectedBitmap = null
                ProfilePhotoUtils.cleanTempCameraFiles(context)
            },
            onCropConfirmed = { croppedBytes ->
                showCropDialog = false
                rawSelectedBitmap = null
                ProfilePhotoUtils.cleanTempCameraFiles(context)
                onPhotoSelected(croppedBytes)
                onDismissRequest()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = DarkSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FOTO DO PERFIL",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Escolha como deseja atualizar sua foto",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Options List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Tirar foto com a câmera
                ProfilePhotoActionRow(
                    icon = Icons.Default.PhotoCamera,
                    title = "Tirar Foto com a Câmera",
                    subtitle = "Usar a câmera para uma nova foto",
                    onClick = {
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val (uri, file) = ProfilePhotoUtils.createTempCameraUri(context)
                                tempCameraUri = uri
                                tempCameraFile = file
                                takePictureLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro ao abrir câmera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )

                // 2. Escolher da Galeria
                ProfilePhotoActionRow(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Escolher da Galeria",
                    subtitle = "Selecionar imagem JPG, PNG ou WEBP",
                    onClick = {
                        try {
                            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (_: Exception) {
                            getContentLauncher.launch("image/*")
                        }
                    }
                )

                // 3. Catálogo de Avatares
                ProfilePhotoActionRow(
                    icon = Icons.Default.Face,
                    title = "Catálogo de Avatares",
                    subtitle = "Personagens de filmes, animes e VIPs",
                    onClick = {
                        onDismissRequest()
                        onOpenPresetCatalog()
                    }
                )

                // 4. Salvar / Baixar foto no celular
                if (hasCustomPhoto) {
                    ProfilePhotoActionRow(
                        icon = Icons.Default.Download,
                        title = "Salvar Foto no Celular",
                        subtitle = "Baixar cópia para a galeria do aparelho",
                        onClick = {
                            if (onDownloadPhoto != null) {
                                onDownloadPhoto()
                            } else {
                                coroutineScope.launch {
                                    val res = ProfilePhotoUtils.savePhotoToDeviceGallery(
                                        context = context,
                                        imageUrl = currentPresetUrl ?: profile?.photoUrl ?: profile?.avatarUrl,
                                        imageBytes = currentPreviewBytes,
                                        profileName = profile?.name ?: "ronycine"
                                    )
                                    res.onSuccess { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Erro ao salvar foto.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            onDismissRequest()
                        }
                    )
                }

                // 5. Remover Foto
                if (hasCustomPhoto) {
                    ProfilePhotoActionRow(
                        icon = Icons.Default.DeleteOutline,
                        title = "Remover Foto",
                        subtitle = "Voltar ao avatar padrão do RONYCINE",
                        isDestructive = true,
                        onClick = {
                            onRemovePhoto()
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePhotoActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isDestructive) Color(0xFF2E1517) else DarkBackground,
        border = BorderStroke(1.dp, if (isDestructive) Color(0xFF5A1E22) else CardBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
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
                    .background(if (isDestructive) Color(0xFF5A1E22) else DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) Color(0xFFFF5252) else BrandRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isDestructive) Color(0xFFFF5252) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = subtitle,
                    color = if (isDestructive) Color(0xFFFFB4B4).copy(alpha = 0.7f) else TextSecondary,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFFF5252).copy(alpha = 0.7f) else TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
