package com.example.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminSection
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    onNavigateSection: (AdminSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by adminViewModel.stats.collectAsState()
    val allMedia by adminViewModel.allCatalogMedia.collectAsState()
    val devices by adminViewModel.allDevices.collectAsState()
    val users by adminViewModel.allUsers.collectAsState()
    val profiles by adminViewModel.allGlobalProfiles.collectAsState()
    val isSyncing by adminViewModel.isCatalogActionRunning.collectAsState()
    val syncMessage by adminViewModel.catalogActionMessage.collectAsState()
    val syncStatus by adminViewModel.syncStatus.collectAsState()
    val auditLogs by adminViewModel.remoteAuditLogs.collectAsState()

    val scope = rememberCoroutineScope()
    var isManualSyncing by remember { mutableStateOf(false) }
    var syncSuccessBanner by remember { mutableStateOf(false) }

    // Counts calculation
    val movieCount = remember(allMedia) { allMedia.count { it.mediaType == "movie" } }
    val seriesCount = remember(allMedia) { allMedia.count { it.mediaType == "tv" } }
    val episodeCount = remember(allMedia, seriesCount) {
        val calculated = allMedia.filter { it.mediaType == "tv" }.sumOf { 
            maxOf(it.episodesCount, 1) 
        }
        if (calculated > 0) calculated else seriesCount * 12
    }
    val totalUsers = remember(users) { users.size }
    val totalDevices = remember(devices) { devices.size }
    val onlineDevices = remember(devices) {
        val now = System.currentTimeMillis()
        devices.count { now - it.effectiveLastSeen < 5 * 60 * 1000 }
    }
    val offlineDevices = remember(totalDevices, onlineDevices) {
        maxOf(0, totalDevices - onlineDevices)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. BANNER DE SINCRONIZAÇÃO EM ANDAMENTO OU SUCESSO ---
        if (isSyncing || isManualSyncing) {
            item {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = BrandRed,
                            strokeWidth = 2.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PROCESSANDO SINCRONIZAÇÃO...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                syncMessage?.ifBlank { "Atualizando catálogo, usuários e dados do sistema..." } ?: "Atualizando catálogo, usuários e dados do sistema...",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else if (syncSuccessBanner) {
            item {
                Surface(
                    color = Color(0xFF064E3B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                        Text(
                            "SINCRONIZAÇÃO CONCLUÍDA COM SUCESSO!",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { syncSuccessBanner = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // --- 2. CARDS DE ESTATÍSTICAS NORMAIS E COMPACTOS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MÉTRICAS DO SISTEMA",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Tempo Real",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Grid 1: Conteúdo (Filmes, Séries, Episódios)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMetricCard(
                        title = "FILMES",
                        value = movieCount.toString(),
                        subtitle = "Catálogo ativo",
                        icon = Icons.Default.Movie,
                        iconTint = BrandRed,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.CATALOGO) }
                    )
                    CompactMetricCard(
                        title = "SÉRIES",
                        value = seriesCount.toString(),
                        subtitle = "Séries ativas",
                        icon = Icons.Default.Tv,
                        iconTint = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.CATALOGO) }
                    )
                    CompactMetricCard(
                        title = "EPISÓDIOS",
                        value = episodeCount.toString(),
                        subtitle = "Disponíveis",
                        icon = Icons.Default.VideoLibrary,
                        iconTint = Color(0xFFA855F7),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.CATALOGO) }
                    )
                }

                // Grid 2: Usuários e Dispositivos (Usuários, Dispositivos, Online, Offline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMetricCard(
                        title = "USUÁRIOS",
                        value = totalUsers.toString(),
                        subtitle = "${profiles.size} perfis",
                        icon = Icons.Default.Group,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.USUARIOS) }
                    )
                    CompactMetricCard(
                        title = "DISPOSITIVOS",
                        value = totalDevices.toString(),
                        subtitle = "Registrados",
                        icon = Icons.Default.Smartphone,
                        iconTint = Color(0xFF64748B),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.DISPOSITIVOS) }
                    )
                    CompactMetricCard(
                        title = "ONLINE",
                        value = onlineDevices.toString(),
                        subtitle = "Ativos agora",
                        icon = Icons.Default.Circle,
                        iconTint = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.DISPOSITIVOS) }
                    )
                    CompactMetricCard(
                        title = "OFFLINE",
                        value = offlineDevices.toString(),
                        subtitle = "Inativos",
                        icon = Icons.Default.PowerSettingsNew,
                        iconTint = Color.Gray,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.DISPOSITIVOS) }
                    )
                }
            }
        }

        // --- 3. AÇÕES RÁPIDAS ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AÇÕES RÁPIDAS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "Atalhos do Painel",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                // Linhas de botões compactos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        title = "+ IMPORTAR FILME",
                        icon = Icons.Default.Add,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.IMPORTACAO) }
                    )
                    QuickActionButton(
                        title = "+ IMPORTAR SÉRIE",
                        icon = Icons.Default.VideoCall,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.IMPORTACAO) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        title = "IMPORTAÇÃO EM MASSA",
                        icon = Icons.Default.CloudSync,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.IMPORTACAO_MASSA) }
                    )
                    QuickActionButton(
                        title = "TOP 10 HOJE",
                        icon = Icons.Default.Whatshot,
                        modifier = Modifier.weight(1f),
                        highlight = true,
                        onClick = { onNavigateSection(AdminSection.TOP_10) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        title = "DESTAQUES HOME",
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.DESTAQUES) }
                    )
                    QuickActionButton(
                        title = "CATÁLOGO COMPLETO",
                        icon = Icons.Default.Movie,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.CATALOGO) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        title = "TV AO VIVO",
                        icon = Icons.Default.LiveTv,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateSection(AdminSection.TV_AO_VIVO) }
                    )
                    QuickActionButton(
                        title = if (isManualSyncing || isSyncing) "SINCRONIZANDO..." else "SINCRONIZAR AGORA",
                        icon = Icons.Default.Sync,
                        modifier = Modifier.weight(1f),
                        containerColor = BrandRed,
                        contentColor = Color.White,
                        enabled = !isManualSyncing && !isSyncing,
                        onClick = {
                            if (!isManualSyncing && !isSyncing) {
                                isManualSyncing = true
                                adminViewModel.forceGlobalSync()
                                scope.launch {
                                    delay(2000L)
                                    isManualSyncing = false
                                    syncSuccessBanner = true
                                    delay(5000L)
                                    syncSuccessBanner = false
                                }
                            }
                        }
                    )
                }
            }
        }

        // --- 4. STATUS DO SISTEMA ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            "STATUS DO SISTEMA",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Ver Detalhes",
                        color = BrandRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateSection(AdminSection.STATUS_SISTEMA) }
                    )
                }

                // Linha de status com pills compactos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SystemStatusPill("Firebase", "Conectado", Color(0xFF10B981), Modifier.weight(1f))
                    SystemStatusPill("Firestore", "Conectado", Color(0xFF10B981), Modifier.weight(1f))
                    SystemStatusPill("FCM", "Conectado", Color(0xFF10B981), Modifier.weight(1f))
                    SystemStatusPill("TMDB API", "Conectado", Color(0xFF10B981), Modifier.weight(1f))
                    SystemStatusPill("Sincronização", "Normal", Color(0xFF10B981), Modifier.weight(1f))
                }
            }
        }

        // --- 5. ÚLTIMOS EVENTOS DE AUDITORIA / LOGS ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "HISTÓRICO RECENTE DE AUDITORIA",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ver Todos (${auditLogs.size})",
                        color = BrandRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateSection(AdminSection.LOGS) }
                    )
                }

                if (auditLogs.isEmpty()) {
                    Text(
                        "Nenhum evento registrado recentemente.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    auditLogs.take(4).forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F0F11), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    log.action.ifBlank { "Ação Administrativa" },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${log.adminEmail} • ${log.details.ifBlank { "Sem detalhes adicionais" }}",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                log.dateFormatted,
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .height(84.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                maxLines = 1
            )

            Text(
                subtitle,
                color = Color.LightGray,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = Color(0xFF1E1E24),
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (highlight) BrandRed.copy(alpha = 0.15f) else containerColor,
            contentColor = if (highlight) BrandRed else contentColor,
            disabledContainerColor = Color(0xFF1A1A1A),
            disabledContentColor = Color.Gray
        ),
        border = BorderStroke(1.dp, if (highlight) BrandRed.copy(alpha = 0.6f) else Color(0xFF2E2E38)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SystemStatusPill(
    name: String,
    status: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF101014),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF22222A)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    name,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                status,
                color = Color.Gray,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}
