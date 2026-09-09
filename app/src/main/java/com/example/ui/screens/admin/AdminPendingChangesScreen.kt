package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.PendingChangeEntity
import com.example.data.remote.ReleaseRevisionEntity
import com.example.data.remote.UpdateEventEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminPendingChangesScreen(adminViewModel: AdminViewModel) {
    val pendingChanges by adminViewModel.pendingChanges.collectAsState()
    val releaseVersions by adminViewModel.releaseVersions.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Alterações, 1: Histórico, 2: Distribuição
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateEvents by adminViewModel.updateEvents.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Tab Selector
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = BrandRed,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = BrandRed
                    )
                }
            },
            divider = { HorizontalDivider(color = Color(0xFF262626)) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("PENDENTES", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("HISTÓRICO", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("DISTRIBUIÇÃO", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> {
                    PendingChangesList(
                        adminViewModel = adminViewModel,
                        pendingChanges = pendingChanges,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }
                1 -> {
                    ReleaseHistoryList(
                        adminViewModel = adminViewModel,
                        releaseVersions = releaseVersions,
                        updateEvents = updateEvents,
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }
                2 -> {
                    DistributionDashboard(
                        adminViewModel = adminViewModel,
                        updateEvents = updateEvents
                    )
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun PendingChangesList(
    adminViewModel: AdminViewModel,
    pendingChanges: List<PendingChangeEntity>,
    onShowMessage: (String) -> Unit
) {
    var filterStatus by remember { mutableStateOf("ALL") } // ALL, PENDING, APPROVED, REJECTED
    var filterCategory by remember { mutableStateOf("TODAS") }
    var filterDate by remember { mutableStateOf("TODAS") } // TODAS, HOJE

    val categories = remember {
        listOf("TODAS", "NOVA FUNCIONALIDADE", "MELHORIA VISUAL", "CORREÇÃO DE BUG", "ALTERAÇÃO DE CATÁLOGO", "ALTERAÇÃO DE PLAYER", "ALTERAÇÃO DE HOME", "ALTERAÇÃO DE EXPLORE", "PERFIS", "LOGIN", "ADMIN", "NOTIFICAÇÕES", "DISPOSITIVOS", "ATUALIZAÇÕES", "FIREBASE", "SEGURANÇA", "CONFIGURAÇÃO", "OUTRA")
    }

    val filteredList = remember(pendingChanges, filterStatus, filterCategory, filterDate) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        pendingChanges.filter {
            (filterStatus == "ALL" || it.status == filterStatus) &&
            (filterCategory == "TODAS" || it.category == filterCategory) &&
            (filterDate == "TODAS" || it.createdAt >= todayStart)
        }
    }

    val selectedChangeIds = remember { mutableStateListOf<String>() }
    var showPublishModal by remember { mutableStateOf(false) }
    var selectedChangeDetail by remember { mutableStateOf<PendingChangeEntity?>(null) }
    var isDetecting by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dashboard Stats
        item(span = { GridItemSpan(maxLineSpan) }) {
            DashboardStats(pendingChanges)
        }

        // Actions Bar
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        isDetecting = true
                        adminViewModel.detectRealChanges { count ->
                            isDetecting = false
                            onShowMessage(if (count > 0) "$count novas alterações detectadas!" else "Nenhuma alteração nova detectada.")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isDetecting
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandRed, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DETECTANDO...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DETECTAR ALTERAÇÕES DE HOJE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = { onShowMessage("Lista atualizada.") },
                    modifier = Modifier
                        .size(48.dp)
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF262626), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
        }

        // Filters
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterBar(
                currentStatus = filterStatus,
                onStatusChange = { filterStatus = it },
                currentCategory = filterCategory,
                categories = categories,
                onCategoryChange = { filterCategory = it },
                currentDate = filterDate,
                onDateChange = { filterDate = it }
            )
        }

        // Bulk Actions
        if (selectedChangeIds.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = BrandRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${selectedChangeIds.size} alterações selecionadas",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selectedChangeIds.clear() }) {
                                Text("LIMPAR", color = Color.Gray)
                            }
                            Button(
                                onClick = { showPublishModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("PUBLICAR SELECIONADAS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // List
        if (filteredList.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val message = if (pendingChanges.isEmpty()) {
                    "Nenhuma alteração registrada. Clique em 'DETECTAR ALTERAÇÕES DE HOJE' para buscar modificações recentes no projeto."
                } else {
                    "Nenhuma alteração encontrada com os filtros atuais."
                }
                EmptyState(message)
            }
        } else {
            items(filteredList) { change ->
                ChangeCard(
                    change = change,
                    isSelected = selectedChangeIds.contains(change.id),
                    onToggleSelect = {
                        if (selectedChangeIds.contains(change.id)) {
                            selectedChangeIds.remove(change.id)
                        } else {
                            selectedChangeIds.add(change.id)
                        }
                    },
                    onClick = { selectedChangeDetail = change }
                )
            }
        }
    }

    // Modals
    if (showPublishModal) {
        PublishRevisionModal(
            selectedChangeIds = selectedChangeIds.toList(),
            onDismiss = { showPublishModal = false },
            onConfirm = { title, desc, type ->
                adminViewModel.publishSelectedChanges(title, desc, selectedChangeIds.toList(), type) { success, msg ->
                    onShowMessage(msg)
                    if (success) {
                        selectedChangeIds.clear()
                        showPublishModal = false
                    }
                }
            }
        )
    }

    if (selectedChangeDetail != null) {
        ChangeDetailModal(
            change = selectedChangeDetail!!,
            onDismiss = { selectedChangeDetail = null },
            onStatusUpdate = { id, status ->
                adminViewModel.updatePendingChangeStatus(id, status)
                selectedChangeDetail = null
            }
        )
    }
}

@Composable
private fun DashboardStats(changes: List<PendingChangeEntity>) {
    val pending = changes.count { it.status == "PENDING" }
    val approved = changes.count { it.status == "APPROVED" }
    val published = changes.count { it.status == "PUBLISHED" }
    val rejected = changes.count { it.status == "REJECTED" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("PENDENTES", pending, Color(0xFFFBBF24), Modifier.weight(1f))
        StatCard("APROVADAS", approved, Color(0xFF34D399), Modifier.weight(1f))
        StatCard("PUBLICADAS", published, Color(0xFF60A5FA), Modifier.weight(1f))
        StatCard("REJEITADAS", rejected, Color(0xFFF87171), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF262626))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterBar(
    currentStatus: String,
    onStatusChange: (String) -> Unit,
    currentCategory: String,
    categories: List<String>,
    onCategoryChange: (String) -> Unit,
    currentDate: String,
    onDateChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date Filter
            FilterChip(
                selected = currentDate == "HOJE",
                onClick = { onDateChange(if (currentDate == "HOJE") "TODAS" else "HOJE") },
                label = { Text("HOJE") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandRed.copy(alpha = 0.2f),
                    selectedLabelColor = BrandRed,
                    containerColor = DarkSurface,
                    labelColor = Color.Gray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentDate == "HOJE",
                    borderColor = Color(0xFF262626),
                    selectedBorderColor = BrandRed.copy(alpha = 0.5f)
                )
            )

            VerticalDivider(modifier = Modifier.height(24.dp), color = Color(0xFF262626))

            // Status Filter
            ScrollableTabRow(
                selectedTabIndex = when (currentStatus) {
                    "PENDING" -> 0
                    "APPROVED" -> 1
                    "PUBLISHED" -> 2
                    "REJECTED" -> 3
                    "ALL" -> 4
                    else -> 4
                },
                containerColor = Color.Transparent,
                contentColor = BrandRed,
                edgePadding = 0.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.weight(1f)
            ) {
                val statusList = listOf("PENDING", "APPROVED", "PUBLISHED", "REJECTED", "ALL")
                val statusLabels = listOf("Pendente", "Aprovada", "Publicada", "Rejeitada", "Tudo")

                statusList.forEachIndexed { index, s ->
                    FilterChip(
                        selected = currentStatus == s,
                        onClick = { onStatusChange(s) },
                        label = { Text(statusLabels[index]) },
                        enabled = true,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandRed.copy(alpha = 0.2f),
                            selectedLabelColor = BrandRed,
                            containerColor = DarkSurface,
                            labelColor = Color.Gray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = currentStatus == s,
                            borderColor = Color(0xFF262626),
                            selectedBorderColor = BrandRed.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // Category Filter
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(currentCategory).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = BrandRed,
            edgePadding = 0.dp,
            divider = {},
            indicator = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = currentCategory == cat,
                    onClick = { onCategoryChange(cat) },
                    label = { Text(cat, fontSize = 10.sp) },
                    enabled = true,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandRed.copy(alpha = 0.2f),
                        selectedLabelColor = BrandRed,
                        containerColor = DarkSurface,
                        labelColor = Color.Gray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = currentCategory == cat,
                        borderColor = Color(0xFF262626),
                        selectedBorderColor = BrandRed.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
private fun ChangeCard(
    change: PendingChangeEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) BrandRed else Color(0xFF262626)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(change.category)
                        Spacer(modifier = Modifier.width(8.dp))
                        AdminStatusBadge(change.status)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = change.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BrandRed,
                        uncheckedColor = Color.Gray
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = change.description,
                color = Color.LightGray,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(change.dateFormatted, color = Color.Gray, fontSize = 11.sp)
                }
                
                if (change.requiresApk) {
                    Surface(
                        color = Color(0xFF7C3AED).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF7C3AED).copy(alpha = 0.4f))
                    ) {
                        Text(
                            "REQUER APK",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color(0xFFA78BFA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFF059669).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF059669).copy(alpha = 0.4f))
                    ) {
                        Text(
                            "PUBLICAÇÃO REMOTA",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color(0xFF34D399),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val color = when(category) {
        "NOVA FUNCIONALIDADE" -> Color(0xFF3B82F6)
        "MELHORIA VISUAL" -> Color(0xFF8B5CF6)
        "CORREÇÃO DE BUG" -> Color(0xFFEF4444)
        "ALTERAÇÃO DE CATÁLOGO" -> Color(0xFFF59E0B)
        else -> Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            category,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AdminStatusBadge(status: String) {
    val (label, color) = when(status) {
        "PENDING" -> "Pendente" to Color(0xFFFBBF24)
        "APPROVED" -> "Aprovada" to Color(0xFF34D399)
        "PUBLISHED" -> "Publicada" to Color(0xFF60A5FA)
        "REJECTED" -> "Rejeitada" to Color(0xFFF87171)
        "CANCELLED" -> "Cancelada" to Color.Gray
        else -> status to Color.Gray
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChangeDetailModal(
    change: PendingChangeEntity,
    onDismiss: () -> Unit,
    onStatusUpdate: (String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF262626))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Detalhes da Alteração", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                CategoryBadge(change.category)
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadgeDetailComponent(change.status)
                Spacer(modifier = Modifier.height(8.dp))
                Text(change.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(12.dp))
                Text(change.description, color = Color.LightGray, fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                DetailItem("Criado por", change.createdBy)
                DetailItem("Data", change.dateFormatted)
                DetailItem("Impacto", change.impact)
                DetailItem("Requer APK?", if (change.requiresApk) "SIM" else "NÃO")
                
                if (change.affectedComponents.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Componentes Afetados", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        change.affectedComponents.forEach { comp ->
                            Surface(color = Color(0xFF262626), shape = RoundedCornerShape(4.dp)) {
                                Text(comp, modifier = Modifier.padding(4.dp), color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (change.status == "PENDING" || change.status == "REJECTED") {
                        Button(
                            onClick = { onStatusUpdate(change.id, "APPROVED") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("APROVAR")
                        }
                    }
                    
                    if (change.status == "PENDING" || change.status == "APPROVED") {
                        OutlinedButton(
                            onClick = { onStatusUpdate(change.id, "REJECTED") },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("REJEITAR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PublishRevisionModal(
    selectedChangeIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("Nova Revisão de Catálogo") }
    var description by remember { mutableStateOf("Correções e melhorias gerais.") }
    var releaseType by remember { mutableStateOf("WEB") } // WEB, APK, HYBRID
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF262626))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Publicar Revisão", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("${selectedChangeIds.size} alterações selecionadas para publicação.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título da Publicação") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0xFF262626)
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição / Changelog") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0xFF262626)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Tipo de Lançamento", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminTypeChip("WEB", releaseType == "WEB") { releaseType = "WEB" }
                    AdminTypeChip("APK", releaseType == "APK") { releaseType = "APK" }
                    AdminTypeChip("HÍBRIDO", releaseType == "HYBRID") { releaseType = "HYBRID" }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                    Button(
                        onClick = { onConfirm(title, description, releaseType) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONFIRMAR PUBLICAÇÃO", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeDetailComponent(status: String) {
    val (label, color) = when(status) {
        "PENDING" -> "Pendente" to Color(0xFFFBBF24)
        "APPROVED" -> "Aprovada" to Color(0xFF34D399)
        "PUBLISHED" -> "Publicada" to Color(0xFF60A5FA)
        "REJECTED" -> "Rejeitada" to Color(0xFFF87171)
        "CANCELLED" -> "Cancelada" to Color.Gray
        else -> status to Color.Gray
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (selected) BrandRed.copy(alpha = 0.2f) else Color(0xFF1E1E1E),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) BrandRed else Color(0xFF262626))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) BrandRed else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReleaseHistoryList(
    adminViewModel: AdminViewModel,
    releaseVersions: List<ReleaseRevisionEntity>,
    updateEvents: List<UpdateEventEntity>,
    onShowMessage: (String) -> Unit
) {
    if (releaseVersions.isEmpty()) {
        EmptyState("Nenhum histórico de publicação encontrado.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(releaseVersions) { release ->
            val releaseEvents = updateEvents.filter { it.toVersion == release.revision }
            ReleaseCard(
                release = release,
                events = releaseEvents,
                onRevert = {
                    adminViewModel.revertToRevision(release.id) { success, msg ->
                        onShowMessage(msg)
                    }
                }
            )
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseRevisionEntity, 
    events: List<UpdateEventEntity>,
    onRevert: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF262626))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(release.revision, color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(release.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Surface(
                    color = Color(0xFF059669).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        release.status,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color(0xFF34D399),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(release.description, color = Color.LightGray, fontSize = 13.sp)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(release.dateFormatted, color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.Layers, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${release.changes.size} alterações", color = Color.Gray, fontSize = 11.sp)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (release.releaseType != "APK") {
                        TextButton(onClick = onRevert) {
                            Text("REVERTER", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = Color.Gray
                        )
                    }
                }
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color(0xFF262626))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ALTERAÇÕES NESTA REVISÃO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    release.changes.forEach { change ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(change.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(change.category, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("DISTRIBUIÇÃO REAL", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    DistributionMiniSummary(events)
                }
            }
        }
    }
}

@Composable
private fun DistributionMiniSummary(events: List<UpdateEventEntity>) {
    val applied = events.count { it.status == "APPLIED" }
    val total = events.size
    val progress = if (total > 0) applied.toFloat() / total else 0f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Status da Propagação", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("$applied de $total dispositivos confirmados", color = Color.Gray, fontSize = 11.sp)
            }
            Text("${(progress * 100).toInt()}%", color = BrandRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = BrandRed,
            trackColor = Color(0xFF262626)
        )
    }
}

@Composable
private fun DistributionDashboard(
    adminViewModel: AdminViewModel,
    updateEvents: List<UpdateEventEntity>
) {
    if (updateEvents.isEmpty()) {
        EmptyState("Nenhum evento de distribuição registrado ainda.")
        return
    }

    val appliedCount = updateEvents.count { it.status == "APPLIED" }
    val errorCount = updateEvents.count { it.status == "ERROR" }
    val pendingCount = updateEvents.count { it.status == "PENDING" }
    val totalCount = updateEvents.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("VISÃO GERAL DA REDE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Confirmados", appliedCount.toString(), Color(0xFF34D399), Modifier.weight(1f))
                StatCard("Pendentes", pendingCount.toString(), Color(0xFFFBBF24), Modifier.weight(1f))
                StatCard("Erros", errorCount.toString(), Color(0xFFEF4444), Modifier.weight(1f))
            }
        }

        item {
            Text("ÚLTIMOS DISPOSITIVOS ATUALIZADOS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        items(updateEvents.take(50)) { event ->
            DeviceUpdateRow(event)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF262626))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.Gray, fontSize = 10.sp)
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DeviceUpdateRow(event: UpdateEventEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (event.status == "APPLIED") Icons.Default.Check else Icons.Default.Smartphone,
                null,
                tint = if (event.status == "APPLIED") Color(0xFF34D399) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(event.deviceName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Revisão: ${event.toVersion}", color = Color.Gray, fontSize = 11.sp)
        }
        
        Column(horizontalAlignment = Alignment.End) {
            val statusColor = when(event.status) {
                "APPLIED" -> Color(0xFF34D399)
                "ERROR" -> Color(0xFFEF4444)
                else -> Color(0xFFFBBF24)
            }
            Text(
                if (event.status == "APPLIED") "APLICADA" else event.status,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(event.completedAt.takeIf { it > 0 } ?: event.startedAt)),
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color(0xFF262626))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
