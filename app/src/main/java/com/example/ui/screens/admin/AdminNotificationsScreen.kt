package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AppNotification
import com.example.data.remote.AppNotificationType
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.NotificationViewModel

@Composable
fun AdminNotificationsScreen(
    adminViewModel: AdminViewModel,
    notificationViewModel: NotificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val notifications by notificationViewModel.notifications.collectAsState()
    val autoConfig by adminViewModel.autoNotificationConfig.collectAsState()
    var showCreateModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        adminViewModel.loadAutoNotificationsConfig()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header com Estatísticas ---
        NotificationStatsCard()

        // --- Configuração Automática ---
        AutoNotificationSettingsCard(
            config = autoConfig,
            onToggleEnabled = { adminViewModel.setAutoNotificationsEnabled(it) },
            onUpdateRules = { m, s, e -> adminViewModel.updateAutoNotificationDetailedConfig(m, s, e) }
        )

        // --- Botões de Ação ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showCreateModal = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CRIAR AVISO", fontWeight = FontWeight.Bold)
            }
            
            OutlinedButton(
                onClick = { /* Refresh stats */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RECARREGAR", color = Color.White)
            }
        }

        Text(
            text = "HISTÓRICO DE NOTIFICAÇÕES",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )

        // --- Lista de Notificações ---
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma notificação enviada ainda.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { item ->
                    AdminNotificationItemCard(item.notification)
                }
            }
        }
    }

    if (showCreateModal) {
        CreateNotificationModal(
            onDismiss = { showCreateModal = false },
            onConfirm = { notification ->
                notificationViewModel.createNotification(notification)
                showCreateModal = false
            }
        )
    }
}

@Composable
fun NotificationStatsCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 ESTATÍSTICAS DE HOJE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(label = "Enviados", value = "1.245", color = Color.Cyan, modifier = Modifier.weight(1f))
                StatItem(label = "Entregues", value = "1.198", color = Color.Green, modifier = Modifier.weight(1f))
                StatItem(label = "Falhas", value = "47", color = BrandRed, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(text = label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AutoNotificationSettingsCard(
    config: com.example.data.remote.AutoNotificationConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateRules: (Boolean, Boolean, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "🤖 NOTIFICAÇÕES INTELIGENTES",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Disparar avisos automaticamente ao importar",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrandRed,
                        checkedTrackColor = BrandRed.copy(alpha = 0.5f)
                    )
                )
            }

            if (config.enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleToggle(
                        label = "Filmes",
                        active = config.notifyNewMovies,
                        onToggle = { onUpdateRules(!config.notifyNewMovies, config.notifyNewSeries, config.notifyNewEpisodes) },
                        modifier = Modifier.weight(1f)
                    )
                    RuleToggle(
                        label = "Séries",
                        active = config.notifyNewSeries,
                        onToggle = { onUpdateRules(config.notifyNewMovies, !config.notifyNewSeries, config.notifyNewEpisodes) },
                        modifier = Modifier.weight(1f)
                    )
                    RuleToggle(
                        label = "Episódios",
                        active = config.notifyNewEpisodes,
                        onToggle = { onUpdateRules(config.notifyNewMovies, config.notifyNewSeries, !config.notifyNewEpisodes) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun RuleToggle(
    label: String,
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) BrandRed.copy(alpha = 0.1f) else Color.Transparent,
        border = BorderStroke(1.dp, if (active) BrandRed else CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = null,
                tint = if (active) BrandRed else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (active) Color.White else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdminNotificationItemCard(notification: AppNotification) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = BrandRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = when(notification.type) {
                        "MOVIE" -> Icons.Default.Movie
                        "SERIE" -> Icons.Default.Tv
                        "EPISODE" -> Icons.Default.PlayCircleFilled
                        else -> Icons.Default.Notifications
                    }
                    Icon(icon, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notification.message,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(text = notification.dateFormatted, color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color.DarkGray,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = notification.type,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.LightGray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotificationModal(
    onDismiss: () -> Unit,
    onConfirm: (AppNotification) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AppNotificationType.ANNOUNCEMENT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("ENVIAR NOTIFICAÇÃO MANUAL", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder
                    )
                )
                
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Mensagem") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder
                    )
                )

                OutlinedTextField(
                    value = actionUrl,
                    onValueChange = { actionUrl = it },
                    label = { Text("URL de Ação (Opcional)") },
                    placeholder = { Text("ex: movie/12345", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder
                    )
                )
                
                Text("Tipo de Notificação:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppNotificationType.values().take(4).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                labelColor = Color.White,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && message.isNotBlank()) {
                        onConfirm(
                            AppNotification(
                                title = title,
                                message = message,
                                actionUrl = actionUrl.ifBlank { null },
                                type = type.name,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) {
                Text("ENVIAR PUSH AGORA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}
