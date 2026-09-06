package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AdminAuditLogEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLogsScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val auditLogs by adminViewModel.remoteAuditLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") } // "TODOS", "IMPORTAÇÃO", "DISPOSITIVO", "VERSÃO", "NOTIFICAÇÃO"
    var selectedLogDetails by remember { mutableStateOf<AdminAuditLogEntity?>(null) }

    val totalLogs = auditLogs.size
    val successLogs = remember(auditLogs) { auditLogs.count { it.result.contains("SUCESSO", ignoreCase = true) } }
    val failureLogs = remember(totalLogs, successLogs) { maxOf(0, totalLogs - successLogs) }

    val filteredLogs = remember(auditLogs, searchQuery, selectedFilter) {
        auditLogs.filter { log ->
            val matchesFilter = when (selectedFilter) {
                "IMPORTAÇÃO" -> log.action.contains("import", ignoreCase = true)
                "DISPOSITIVO" -> log.action.contains("dispositivo", ignoreCase = true) || log.targetDeviceId.isNotBlank()
                "VERSÃO" -> log.action.contains("versão", ignoreCase = true) || log.action.contains("update", ignoreCase = true)
                "NOTIFICAÇÃO" -> log.action.contains("notifica", ignoreCase = true)
                else -> true
            }
            if (!matchesFilter) return@filter false

            if (searchQuery.isBlank()) return@filter true
            val q = searchQuery.trim().lowercase()
            log.action.lowercase().contains(q) ||
                    log.adminEmail.lowercase().contains(q) ||
                    log.details.lowercase().contains(q) ||
                    log.targetDeviceId.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_logs_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER E MÉTRICAS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "LOGS DO SISTEMA",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "Histórico de ações administrativas e auditoria",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { adminViewModel.loadStats() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar Logs", tint = Color.LightGray)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip("TOTAL EVENTOS", totalLogs.toString(), Color.White, Modifier.weight(1f))
                    MetricChip("SUCESSOS", successLogs.toString(), Color(0xFF10B981), Modifier.weight(1f))
                    MetricChip("ALERTAS / ERROS", failureLogs.toString(), if (failureLogs > 0) Color(0xFFEF4444) else Color.Gray, Modifier.weight(1f))
                }
            }
        }

        // --- 2. BUSCA E FILTROS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por ação, admin ou detalhes...", fontSize = 13.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Filtros rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("TODOS", "IMPORTAÇÃO", "DISPOSITIVO", "VERSÃO", "NOTIFICAÇÃO").forEach { filterName ->
                        val isSelected = selectedFilter == filterName
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filterName },
                            label = { Text(filterName, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurface,
                                labelColor = Color.Gray
                            ),
                            border = BorderStroke(1.dp, if (isSelected) BrandRed else CardBorder),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // --- 3. LISTA DE LOGS ---
        if (filteredLogs.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Text("Nenhum registro encontrado", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Ações administrativas realizadas aparecerão aqui.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id.ifBlank { "${it.timestamp}_${it.action}" } }) { log ->
                AuditLogRowCard(
                    log = log,
                    onViewDetails = { selectedLogDetails = log }
                )
            }
        }
    }

    // Modal de detalhes do log
    selectedLogDetails?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogDetails = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = BrandRed)
                    Text("Detalhes da Auditoria", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailField("Ação:", log.action)
                    DetailField("Administrador:", log.adminEmail)
                    DetailField("Data / Hora:", log.dateFormatted)
                    DetailField("Resultado:", log.result)
                    if (log.targetDeviceId.isNotBlank()) {
                        DetailField("Alvo / Dispositivo:", log.targetDeviceId)
                    }
                    DetailField("Detalhes:", log.details.ifBlank { "Nenhum detalhe adicional informado" })
                    DetailField("ID do Registro:", log.id)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLogDetails = null }) {
                    Text("FECHAR", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun AuditLogRowCard(
    log: AdminAuditLogEntity,
    onViewDetails: () -> Unit
) {
    val isSuccess = log.result.contains("SUCESSO", ignoreCase = true)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        log.action.ifBlank { "Ação do Sistema" },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badge de Resultado
                Surface(
                    color = if (isSuccess) Color(0xFF064E3B) else Color(0xFF450A0A),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444))
                ) {
                    Text(
                        log.result.ifBlank { "REGISTRADO" },
                        color = if (isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                log.details.ifBlank { "Sem detalhes adicionais" },
                color = Color.LightGray,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Admin: ${log.adminEmail} • ${log.dateFormatted}",
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                OutlinedButton(
                    onClick = onViewDetails,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Detalhes", fontSize = 10.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp))
        Text(value, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}
