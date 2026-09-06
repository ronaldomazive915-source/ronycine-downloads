package com.example.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TmdbAutoSyncHistoryEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHistoricoScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Importações, 1: Auto-Sync TMDB, 2: Backups
    var searchQuery by remember { mutableStateOf("") }

    val importHistory by adminViewModel.importHistory.collectAsState()
    val autoSyncHistory by adminViewModel.tmdbAutoSyncHistory.collectAsState()
    val auditLogs by adminViewModel.remoteAuditLogs.collectAsState()

    // Filter audit logs for backup / restore actions
    val backupLogs = remember(auditLogs) {
        auditLogs.filter {
            it.action.contains("backup", ignoreCase = true) ||
            it.action.contains("restaur", ignoreCase = true) ||
            it.action.contains("sincroniz", ignoreCase = true)
        }
    }

    val filteredImports = remember(importHistory, searchQuery) {
        if (searchQuery.isBlank()) importHistory
        else {
            val q = searchQuery.trim().lowercase()
            importHistory.filter { it.title.lowercase().contains(q) || it.status.lowercase().contains(q) }
        }
    }

    val filteredAutoSync = remember(autoSyncHistory, searchQuery) {
        if (searchQuery.isBlank()) autoSyncHistory
        else {
            val q = searchQuery.trim().lowercase()
            autoSyncHistory.filter { it.status.lowercase().contains(q) || it.type.lowercase().contains(q) }
        }
    }

    val filteredBackups = remember(backupLogs, searchQuery) {
        if (searchQuery.isBlank()) backupLogs
        else {
            val q = searchQuery.trim().lowercase()
            backupLogs.filter { it.action.lowercase().contains(q) || it.details.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_historico_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CENTRAL DE HISTÓRICO",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Histórico de importações, auto-sincronizações do TMDB e backups",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            adminViewModel.loadTmdbAutoSyncConfig()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, CardBorder, CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar Histórico", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    }
                }

                // Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistorySummaryCard(
                        title = "IMPORTAÇÕES",
                        value = importHistory.size.toString(),
                        icon = Icons.Default.CloudDownload,
                        iconTint = BrandRed,
                        modifier = Modifier.weight(1f)
                    )
                    HistorySummaryCard(
                        title = "AUTO-SYNCS",
                        value = autoSyncHistory.size.toString(),
                        icon = Icons.Default.Sync,
                        iconTint = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    HistorySummaryCard(
                        title = "BACKUPS/SISTEMA",
                        value = backupLogs.size.toString(),
                        icon = Icons.Default.Backup,
                        iconTint = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Navigation Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryTabButton(
                        title = "Importações (${importHistory.size})",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    HistoryTabButton(
                        title = "Auto-Sync (${autoSyncHistory.size})",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    HistoryTabButton(
                        title = "Backups (${backupLogs.size})",
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar registros por palavra-chave...", color = Color.Gray, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.Gray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 2. LISTA CONFORME A ABA SELECIONADA ---
        when (selectedTab) {
            0 -> {
                // Aba 1: Histórico de Importações
                if (filteredImports.isEmpty()) {
                    item {
                        EmptyHistoryNotice("Nenhum registro de importação individual ou em massa encontrado.")
                    }
                } else {
                    items(filteredImports) { item ->
                        ImportHistoryRowCard(item)
                    }
                }
            }

            1 -> {
                // Aba 2: Histórico de Auto-Sync TMDB
                if (filteredAutoSync.isEmpty()) {
                    item {
                        EmptyHistoryNotice("Nenhum histórico de auto-sincronização automática registrado.")
                    }
                } else {
                    items(filteredAutoSync, key = { it.id }) { syncItem ->
                        AutoSyncHistoryCard(syncItem)
                    }
                }
            }

            2 -> {
                // Aba 3: Backups e Restaurações
                if (filteredBackups.isEmpty()) {
                    item {
                        EmptyHistoryNotice("Nenhum registro de backup ou restauração de catálogo localizado.")
                    }
                } else {
                    items(filteredBackups, key = { it.id }) { log ->
                        BackupLogRowCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HistoryTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (selected) BrandRed else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (selected) Color.White else Color.Gray,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ImportHistoryRowCard(item: AdminViewModel.ImportHistoryItem) {
    val isSuccess = item.status.contains("sucess", ignoreCase = true) || item.status.contains("exist", ignoreCase = true)
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(item.timestamp) { sdf.format(Date(item.timestamp)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSuccess) Color(0xFF065F46) else Color(0xFF7F1D1D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${item.type.uppercase()} • ${item.status.uppercase()}",
                    color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(dateStr, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AutoSyncHistoryCard(item: TmdbAutoSyncHistoryEntity) {
    val isSuccess = item.status.lowercase().contains("conclu") || item.status.lowercase().contains("sucess") || item.status.lowercase().contains("ok")
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val dateStr = remember(item.timestamp) { sdf.format(Date(item.timestamp)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isSuccess) Color(0xFF065F46) else Color(0xFF7F1D1D),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isSuccess) "SINCRONIA CONCLUÍDA" else "FALHA / ALERTA",
                        color = if (isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(dateStr, color = Color.Gray, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tipo: ${item.type.uppercase()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("+${item.importedCount} novos", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("${item.existingCount} existentes", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (item.errorCount > 0) {
                    Text("${item.errorCount} erros", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Total analisados: ${item.totalFound} títulos • Status: ${item.status}",
                color = Color.LightGray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BackupLogRowCard(log: com.example.data.remote.AdminAuditLogEntity) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val dateStr = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.action, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(log.details.ifBlank { "Sem detalhes adicionais" }, color = Color.LightGray, fontSize = 11.sp)
                Text("Por: ${log.adminEmail} • $dateStr", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun EmptyHistoryNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.Gray, fontSize = 13.sp)
    }
}
