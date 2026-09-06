package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.NotificationEntity
import com.example.ui.theme.BrandRed
import kotlinx.coroutines.delay

private data class FormattedNotificationData(
    val categoryHeader: String,
    val mediaTitle: String,
    val subMessage: String,
    val buttonText: String,
    val posterUrl: String?
)

private fun parseNotificationData(notif: NotificationEntity): FormattedNotificationData {
    // 1. Poster URL formatting
    val rawImage = notif.imageUrl?.trim()
    val posterUrl = when {
        rawImage.isNullOrBlank() -> null
        rawImage.startsWith("http://") || rawImage.startsWith("https://") -> rawImage
        rawImage.startsWith("/") -> "https://image.tmdb.org/t/p/w500$rawImage"
        else -> "https://image.tmdb.org/t/p/w500/$rawImage"
    }

    // 2. Determine type & category header
    val typeUpper = notif.type.uppercase()
    val isSeries = typeUpper == "NOVA_SERIE" || typeUpper == "SERIE" || notif.actionUrl?.startsWith("tv/") == true
    val isEpisode = typeUpper == "NOVO_EPISODIO" || typeUpper == "EPISODIO" || notif.title.contains("Episódio", ignoreCase = true)
    val isEmAlta = typeUpper == "EM_ALTA" || typeUpper == "TRENDING"

    val categoryHeader = when {
        isEpisode -> "📺 NOVO EPISÓDIO"
        isSeries -> "📺 NOVA SÉRIE"
        isEmAlta -> "🔥 EM ALTA NO RONYCINE"
        else -> "🎬 NOVO NO RONYCINE"
    }

    val defaultBtnText = when {
        isSeries -> "VER AGORA"
        else -> "ASSISTIR"
    }

    val buttonText = notif.buttonText?.ifBlank { null } ?: defaultBtnText

    // 3. Extract title and message cleanly
    var title = notif.title.trim()
    var message = notif.message.trim()

    // If title is generic (e.g. "🎬 Novo Filme Adicionado" or "📺 Nova Série Adicionada")
    if (title.contains("Novo Filme", ignoreCase = true) ||
        title.contains("Nova Série", ignoreCase = true) ||
        title.contains("Notificação RONYCINE", ignoreCase = true) ||
        title.contains("Novo Conteúdo", ignoreCase = true)) {

        if (message.contains("Já está disponível:", ignoreCase = true)) {
            val extracted = message.substringAfter("Já está disponível:").substringBefore(".").substringBefore("!").trim()
            if (extracted.isNotBlank()) {
                title = extracted
            }
        } else if (message.contains("'") && message.count { it == '\'' } >= 2) {
            val extracted = message.substringAfter("'").substringBefore("'").trim()
            if (extracted.isNotBlank()) {
                title = extracted
            }
        }
    }

    // Determine subtitle message
    val subMessage = when {
        message.contains("Já está disponível:", ignoreCase = true) -> {
            if (isSeries) "Nova série disponível no catálogo" else "Acabou de chegar ao catálogo"
        }
        message.contains("foi adicionado ao catálogo", ignoreCase = true) -> "Acabou de chegar ao catálogo"
        message.isNotBlank() && !message.contains("Toque para assistir", ignoreCase = true) -> message
        isEpisode -> "Novo episódio disponível"
        isSeries -> "Nova série disponível no catálogo"
        else -> "Acabou de chegar ao catálogo"
    }

    return FormattedNotificationData(
        categoryHeader = categoryHeader,
        mediaTitle = title,
        subMessage = subMessage,
        buttonText = buttonText,
        posterUrl = posterUrl
    )
}

@Composable
fun InAppNotificationBanner(
    notification: NotificationEntity?,
    onDismiss: () -> Unit,
    onClick: (NotificationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember(notification) { mutableStateOf(notification != null) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(notification, isInteracting) {
        if (notification != null && !isInteracting) {
            visible = true
            delay(6000) // 6 seconds auto dismissal
            visible = false
            delay(240)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible && notification != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutLinearInEasing)
        ) + fadeOut(animationSpec = tween(220)),
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .widthIn(max = 440.dp)
            .testTag("in_app_notification_banner")
    ) {
        notification?.let { notif ->
            val formatted = remember(notif) { parseNotificationData(notif) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = BrandRed.copy(alpha = 0.25f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isInteracting = true
                                tryAwaitRelease()
                                isInteracting = false
                            },
                            onTap = {
                                onClick(notif)
                                visible = false
                            }
                        )
                    },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF141418), // Dark graphite background
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            BrandRed.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.12f),
                            BrandRed.copy(alpha = 0.3f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. POSTER PEQUENO DO CONTEÚDO (Left)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF222228),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .width(44.dp)
                            .height(58.dp)
                    ) {
                        if (!formatted.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = formatted.posterUrl,
                                contentDescription = formatted.mediaTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                BrandRed.copy(alpha = 0.4f),
                                                Color(0xFF1A1A1E)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 2. CONTEÚDO PRINCIPAL (Middle)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Cabeçalho (ex: 🎬 NOVO NO RONYCINE ou 📺 NOVA SÉRIE)
                        Text(
                            text = formatted.categoryHeader,
                            color = BrandRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(1.dp))

                        // Título do filme/série
                        Text(
                            text = formatted.mediaTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(1.dp))

                        // Mensagem/Subtítulo (ex: Acabou de chegar ao catálogo)
                        Text(
                            text = formatted.subMessage,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 3. AÇÕES (AGORA + FECHAR X)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Botão / Badge AGORA
                        Surface(
                            onClick = {
                                onClick(notif)
                                visible = false
                            },
                            shape = RoundedCornerShape(13.dp),
                            color = BrandRed,
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("in_app_notif_watch_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "AGORA",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Botão Fechar X
                        IconButton(
                            onClick = {
                                visible = false
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("in_app_notif_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Notificação",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
