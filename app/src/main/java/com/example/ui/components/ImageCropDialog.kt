package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.util.ProfilePhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageCropDialog(
    sourceBitmap: Bitmap,
    onDismissRequest: () -> Unit,
    onCropConfirmed: (ByteArray) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismissRequest,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "AJUSTAR FOTO",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Arraste e ajuste o enquadramento",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }

                    // Reset button
                    IconButton(
                        onClick = {
                            scale = 1.0f
                            offsetX = 0f
                            offsetY = 0f
                            rotationAngle = 0f
                        },
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Redefinir",
                            tint = if (scale != 1.0f || offsetX != 0f || offsetY != 0f || rotationAngle != 0f) BrandRed else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Crop View Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (!isProcessing) {
                                        scale = (scale * zoom).coerceIn(1.0f, 4.0f)
                                        // Bound pan within zoom
                                        val maxPan = 150f * (scale - 0.9f)
                                        offsetX = (offsetX + pan.x).coerceIn(-maxPan, maxPan)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxPan, maxPan)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Image to transform
                        Image(
                            bitmap = sourceBitmap.asImageBitmap(),
                            contentDescription = "Imagem para recorte",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                    rotationZ = rotationAngle
                                }
                        )

                        // Circular / Square Mask Overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val radius = canvasWidth / 2f

                            val maskPath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                // Outer full rectangle
                                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                // Inner circle cutout
                                addOval(Rect(center = Offset(canvasWidth / 2f, canvasHeight / 2f), radius = radius))
                            }

                            drawPath(
                                path = maskPath,
                                color = Color.Black.copy(alpha = 0.65f)
                            )
                        }

                        // Circular Border Guide
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, BrandRed, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Adjustment Controls (Rotate, Zoom in/out)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom Out Button
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceIn(1.0f, 4.0f) },
                        enabled = scale > 1.0f && !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Diminuir zoom",
                            tint = if (scale > 1.0f) Color.White else Color.Gray
                        )
                    }

                    // Rotate 90 deg Left
                    IconButton(
                        onClick = { rotationAngle = (rotationAngle - 90f) % 360f },
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Girar para esquerda",
                            tint = Color.White
                        )
                    }

                    // Rotate 90 deg Right
                    IconButton(
                        onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Girar para direita",
                            tint = Color.White
                        )
                    }

                    // Zoom In Button
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceIn(1.0f, 4.0f) },
                        enabled = scale < 4.0f && !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Aumentar zoom",
                            tint = if (scale < 4.0f) Color.White else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons (Confirm and Cancel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "CANCELAR",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            isProcessing = true
                            coroutineScope.launch(Dispatchers.Default) {
                                // Normalized pan relative to viewport (300dp)
                                val normalizedX = - (offsetX / (300f * scale))
                                val normalizedY = - (offsetY / (300f * scale))

                                val croppedBytes = ProfilePhotoUtils.cropAndCompressBitmap(
                                    sourceBitmap = sourceBitmap,
                                    cropX = normalizedX,
                                    cropY = normalizedY,
                                    scale = scale,
                                    rotationDegrees = rotationAngle
                                )

                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    onCropConfirmed(croppedBytes)
                                }
                            }
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandRed,
                            disabledContainerColor = BrandRed.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_crop_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CONFIRMAR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
