package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.NotificationEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateToLiveTv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notifications by viewModel.allNotifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Todas, 1 = Não Lidas

    val filteredNotifications = remember(notifications, selectedTab) {
        if (selectedTab == 1) {
            notifications.filter { !it.isRead }
        } else {
            notifications
        }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("notification_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "NOTIFICAÇÕES",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = BrandRed,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$unreadCount",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (unreadCount > 0) {
                TextButton(
                    onClick = { viewModel.markAllNotificationsAsRead() },
                    modifier = Modifier.testTag("mark_all_read_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Lidas",
                            color = BrandRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Tabs (Todas / Não Lidas)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = BrandRed,
            divider = { HorizontalDivider(color = CardBorder, thickness = 1.dp) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Todas (${notifications.size})",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                },
                selectedContentColor = BrandRed,
                unselectedContentColor = TextSecondary
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Não Lidas ($unreadCount)",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                },
                selectedContentColor = BrandRed,
                unselectedContentColor = TextSecondary
            )
        }

        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedTab == 1) "Nenhuma notificação não lida" else "Nenhuma notificação no momento",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Avisos e novidades sobre novos filmes e séries aparecerão aqui.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(
                    items = filteredNotifications,
                    key = { it.id }
                ) { notif ->
                    NotificationCard(
                        notification = notif,
                        onMarkRead = { viewModel.markNotificationAsRead(notif.id) },
                        onDelete = { viewModel.deleteNotification(notif.id) },
                        onAction = { actionUrl ->
                            viewModel.markNotificationAsRead(notif.id)
                            val isUpdateType = notif.type == "APP_UPDATE" || notif.type == "ATUALIZACAO"
                            val isApkUrl = actionUrl != null && (actionUrl.endsWith(".apk") || actionUrl.contains("/updates/") || actionUrl.contains("appVersions") || actionUrl.contains(".apk?") || actionUrl == "update")
                            if (isUpdateType || isApkUrl) {
                                val active = viewModel.activeAppVersion.value
                                val targetVer = if (active != null) {
                                    active
                                } else {
                                    com.example.data.remote.AppVersionEntity(
                                        id = "custom_${System.currentTimeMillis()}",
                                        versionName = if (notif.title.contains("v")) notif.title.substringAfter("v").substringBefore(" ") else "Nova Versão",
                                        versionCode = 10000,
                                        apkUrl = if (actionUrl == "update") "" else (actionUrl ?: ""),
                                        releaseNotes = notif.message,
                                        published = true,
                                        status = "PUBLISHED"
                                    )
                                }
                                viewModel.triggerForceUpdateDialog(targetVer)
                            } else {
                                handleNotificationAction(actionUrl, onNavigateToDetail, onNavigateToLiveTv)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationEntity,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    onAction: (String) -> Unit
) {
    val formattedTime = remember(notification.timestamp) {
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(notification.timestamp))
        } catch (_: Exception) {
            "Recente"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!notification.isRead) {
                    onMarkRead()
                }
                notification.actionUrl?.let { onAction(it) }
            },
        shape = RoundedCornerShape(16.dp),
        color = if (notification.isRead) DarkSurface.copy(alpha = 0.6f) else DarkSurface,
        border = BorderStroke(
            1.dp,
            if (!notification.isRead) BrandRed.copy(alpha = 0.8f) else CardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon Type Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (notification.type) {
                            "NOVO_FILME", "NOVA_SERIE" -> BrandRed.copy(alpha = 0.2f)
                            "EM_ALTA" -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                            "MANUTENCAO" -> Color(0xFFEF4444).copy(alpha = 0.2f)
                            else -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (notification.type) {
                                    "NOVO_FILME" -> "🎬 Novo Filme"
                                    "NOVA_SERIE" -> "📺 Nova Série"
                                    "EM_ALTA" -> "🔥 Em Alta"
                                    "ATUALIZACAO" -> "🆕 Atualização"
                                    "MANUTENCAO" -> "⚠️ Manutenção"
                                    "INFORMACAO" -> "ℹ️ Informação"
                                    else -> "📢 Aviso"
                                },
                                color = when (notification.type) {
                                    "NOVO_FILME", "NOVA_SERIE" -> BrandRed
                                    "EM_ALTA" -> Color(0xFFF59E0B)
                                    "MANUTENCAO" -> Color(0xFFEF4444)
                                    else -> Color(0xFF38BDF8)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BrandRed)
                        )
                    }
                }

                // Time + Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = notification.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Message Body
            Text(
                text = notification.message,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Optional Image Preview
            if (!notification.imageUrl.isNull_or_blank()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = notification.imageUrl,
                    contentDescription = notification.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                )
            }

            // Optional Button Action
            if (!notification.buttonText.isNull_or_blank() || !notification.actionUrl.isNull_or_blank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onMarkRead()
                        notification.actionUrl?.let { onAction(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Text(
                        text = notification.buttonText ?: "Conferir Agora",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.trim().isEmpty()
}

private fun handleNotificationAction(
    actionUrl: String?,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateToLiveTv: () -> Unit
) {
    if (actionUrl == null) return
    try {
        if (actionUrl.startsWith("movie/")) {
            val tmdbId = actionUrl.substringAfter("movie/").toIntOrNull()
            if (tmdbId != null) onNavigateToDetail(tmdbId, "movie")
        } else if (actionUrl.startsWith("tv/")) {
            val tmdbId = actionUrl.substringAfter("tv/").toIntOrNull()
            if (tmdbId != null) {
                onNavigateToDetail(tmdbId, "tv")
            } else {
                onNavigateToLiveTv()
            }
        } else if (actionUrl == "livetv" || actionUrl == "tv") {
            onNavigateToLiveTv()
        } else {
            val id = actionUrl.toIntOrNull()
            if (id != null) {
                onNavigateToDetail(id, "movie")
            }
        }
    } catch (_: Exception) {}
}
