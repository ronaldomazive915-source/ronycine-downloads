package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.FeaturedConfigEntity
import com.example.data.remote.FeaturedItemConfigEntity
import com.example.ui.theme.BrandRed
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDestaquesScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val featuredConfig by adminViewModel.featuredConfig.collectAsState()
    val featuredHistory by adminViewModel.featuredHistory.collectAsState()
    val isUpdating by adminViewModel.isUpdatingFeatured.collectAsState()
    val progressMessage by adminViewModel.featuredUpdatingProgress.collectAsState()
    val searchResults by adminViewModel.featuredManualSearchResults.collectAsState()
    val isSearchingManual by adminViewModel.isSearchingManualFeatured.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Destaques, 1: Exclusões, 2: Histórico
    var searchQuery by remember { mutableStateOf("") }
    var searchFilterType by remember { mutableStateOf("all") } // all, movie, tv
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showTrailerDialog by remember { mutableStateOf<FeaturedItemConfigEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<FeaturedItemConfigEntity?>(null) }

    // Draft state for automatic config settings
    var draftPeriod by remember(featuredConfig.period) { mutableStateOf(featuredConfig.period) }
    var draftMediaType by remember(featuredConfig.mediaType) { mutableStateOf(featuredConfig.mediaType) }
    var draftLimit by remember(featuredConfig.limit) { mutableStateOf(featuredConfig.limit) }
    var draftAutoPublish by remember(featuredConfig.autoPublish) { mutableStateOf(featuredConfig.autoPublish) }
    var draftAutoImport by remember(featuredConfig.autoImportToCatalog) { mutableStateOf(featuredConfig.autoImportToCatalog) }
    var draftIntervalHours by remember(featuredConfig.autoUpdateIntervalHours) { mutableStateOf(featuredConfig.autoUpdateIntervalHours) }
    var draftMinRating by remember(featuredConfig.minRating) { mutableStateOf(featuredConfig.minRating) }
    var draftMinYear by remember(featuredConfig.minYear) { mutableStateOf(featuredConfig.minYear) }

    val context = LocalContext.current

    val primaryColor = BrandRed
    val darkBackground = Color(0xFF0F0F13)
    val cardBackground = Color(0xFF181820)
    val elevatedBackground = Color(0xFF22222E)
    val subtleBorder = Color(0xFF2D2D3D)
    val textColor = Color.White
    val textSecondary = Color(0xFF9E9EB2)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER SECTION ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(primaryColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Conteúdo em Destaque",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = "Gerenciamento inteligente do banner e destaques do app",
                                    fontSize = 13.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        // Mode status badge
                        Surface(
                            color = if (featuredConfig.isAutomatic) Color(0xFF1E3A8A) else Color(0xFF4C1D95),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (featuredConfig.isAutomatic) Color(0xFF3B82F6) else Color(0xFF8B5CF6))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (featuredConfig.isAutomatic) Color(0xFF60A5FA) else Color(0xFFA78BFA), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MODO ${featuredConfig.mode.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Mode switch buttons [ MANUAL ] [ AUTOMÁTICO ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(elevatedBackground, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { adminViewModel.updateFeaturedMode("manual") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!featuredConfig.isAutomatic) primaryColor else Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("MANUAL", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { adminViewModel.updateFeaturedMode("automatic") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (featuredConfig.isAutomatic) primaryColor else Color.Transparent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AUTOMÁTICO (TMDB)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 2. STATUS SUMMARY CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Status Atual do Sistema de Destaques",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusIndicatorItem(
                            label = "Modo Atual",
                            value = if (featuredConfig.isAutomatic) "Automático" else "Manual",
                            icon = if (featuredConfig.isAutomatic) Icons.Default.AutoAwesome else Icons.Default.TouchApp,
                            tint = if (featuredConfig.isAutomatic) Color(0xFF60A5FA) else Color(0xFFA78BFA)
                        )
                        StatusIndicatorItem(
                            label = "Fonte de Dados",
                            value = featuredConfig.source,
                            icon = Icons.Default.CloudDownload,
                            tint = Color(0xFF34D399)
                        )
                        StatusIndicatorItem(
                            label = "Destaques Ativos",
                            value = "${featuredConfig.items.count { it.isActive }} de ${featuredConfig.items.size}",
                            icon = Icons.Default.Visibility,
                            tint = Color(0xFFFBBF24)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = subtleBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusIndicatorItem(
                            label = "Critério Ativo",
                            value = "${featuredConfig.periodDisplay} (${featuredConfig.mediaTypeDisplay})",
                            icon = Icons.Default.FilterAlt,
                            tint = Color(0xFFF472B6)
                        )
                        val lastDateStr = remember(featuredConfig.lastUpdatedAt) {
                            if (featuredConfig.lastUpdatedAt > 0) {
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(featuredConfig.lastUpdatedAt))
                            } else "Nunca"
                        }
                        StatusIndicatorItem(
                            label = "Última Atualização",
                            value = lastDateStr,
                            icon = Icons.Default.History,
                            tint = Color(0xFF9CA3AF)
                        )
                    }

                    if (featuredConfig.lastSyncError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aviso TMDB: ${featuredConfig.lastSyncError}. Destaques preservados com segurança.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFECACA)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. SUB-TABS: [ Destaques & Configuração | Exclusões | Histórico ] ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBackground, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabButton(
                    title = "Destaques & Config",
                    icon = Icons.Default.FeaturedPlayList,
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    title = "Exclusões (${featuredConfig.excludedTmdbKeys.size})",
                    icon = Icons.Default.Block,
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    title = "Histórico",
                    icon = Icons.Default.Timeline,
                    selected = activeSubTab == 2,
                    onClick = { activeSubTab = 2 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 4. MAIN CONTENT BASED ON ACTIVE SUB-TAB ---
        when (activeSubTab) {
            0 -> {
                // If in Automatic Mode -> Show TMDB Automation Controls
                if (featuredConfig.isAutomatic) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = primaryColor)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Configuração Automática TMDB",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }

                                    if (isUpdating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = primaryColor,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }

                                if (progressMessage.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = progressMessage,
                                        fontSize = 12.sp,
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // TMDB Source / Period Selector
                                Text("Critério de Seleção TMDB", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val periods = listOf(
                                        "today" to "TMDB HOJE",
                                        "week" to "TMDB ESTA SEMANA",
                                        "popular" to "MAIS POPULARES",
                                        "top_rated" to "MELHOR AVALIADOS",
                                        "trending" to "EM ALTA"
                                    )
                                    items(periods) { (key, label) ->
                                        FilterChip(
                                            selected = draftPeriod == key,
                                            onClick = { draftPeriod = key },
                                            label = { Text(label, fontSize = 12.sp, fontWeight = if (draftPeriod == key) FontWeight.Bold else FontWeight.Normal) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = primaryColor,
                                                selectedLabelColor = Color.White,
                                                containerColor = elevatedBackground,
                                                labelColor = textSecondary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = if (draftPeriod == key) primaryColor else subtleBorder,
                                                selectedBorderColor = primaryColor,
                                                enabled = true,
                                                selected = draftPeriod == key
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Media Type Filter: Filmes / Séries / Filmes + Séries
                                Text("Tipo de Conteúdo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    val mediaTypes = listOf(
                                        "all" to "FILMES + SÉRIES",
                                        "movie" to "FILMES",
                                        "tv" to "SÉRIES"
                                    )
                                    mediaTypes.forEach { (typeKey, label) ->
                                        FilterChip(
                                            selected = draftMediaType == typeKey,
                                            onClick = { draftMediaType = typeKey },
                                            label = { Text(label, fontSize = 12.sp, fontWeight = if (draftMediaType == typeKey) FontWeight.Bold else FontWeight.Normal) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = primaryColor,
                                                selectedLabelColor = Color.White,
                                                containerColor = elevatedBackground,
                                                labelColor = textSecondary
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Limits & Filters (Sliders)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Quantidade de Conteúdos:", fontSize = 13.sp, color = textSecondary)
                                    Text("$draftLimit itens", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                }
                                Slider(
                                    value = draftLimit.toFloat(),
                                    onValueChange = { draftLimit = it.toInt() },
                                    valueRange = 5f..20f,
                                    steps = 14,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Switches: Auto-Publish, Auto-Import, Use in Hero
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Publicar Automaticamente", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                                        Text("Se desativado, gera pré-visualização para aprovação manual", fontSize = 12.sp, color = textSecondary)
                                    }
                                    Switch(
                                        checked = draftAutoPublish,
                                        onCheckedChange = { draftAutoPublish = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Importar Metadados no Catálogo", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                                        Text("Garante que itens do TMDB existam no catálogo do app", fontSize = 12.sp, color = textSecondary)
                                    }
                                    Switch(
                                        checked = draftAutoImport,
                                        onCheckedChange = { draftAutoImport = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Action Buttons: [ Atualizar Agora ] and [ Salvar Config ]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val newConfig = featuredConfig.copy(
                                                period = draftPeriod,
                                                mediaType = draftMediaType,
                                                limit = draftLimit,
                                                autoPublish = draftAutoPublish,
                                                autoImportToCatalog = draftAutoImport,
                                                autoUpdateIntervalHours = draftIntervalHours,
                                                minRating = draftMinRating,
                                                minYear = draftMinYear
                                            )
                                            adminViewModel.saveFeaturedConfig(newConfig)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = textColor)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salvar Config", color = textColor)
                                    }

                                    Button(
                                        onClick = {
                                            val newConfig = featuredConfig.copy(
                                                period = draftPeriod,
                                                mediaType = draftMediaType,
                                                limit = draftLimit,
                                                autoPublish = draftAutoPublish,
                                                autoImportToCatalog = draftAutoImport,
                                                autoUpdateIntervalHours = draftIntervalHours,
                                                minRating = draftMinRating,
                                                minYear = draftMinYear
                                            )
                                            adminViewModel.fetchTmdbFeaturedAutomation(configOverride = newConfig, forcePublish = false)
                                        },
                                        enabled = !isUpdating,
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isUpdating) "Buscando TMDB..." else "Atualizar Agora", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // MANUAL MODE SEARCH & ADD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = primaryColor)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Adicionar Conteúdo Manualmente",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }

                                    if (isSearchingManual) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primaryColor, strokeWidth = 2.dp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Search input
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        adminViewModel.searchManualFeatured(it, searchFilterType)
                                    },
                                    placeholder = { Text("Buscar por título, gênero ou ID TMDB...", color = textSecondary) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textSecondary) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchQuery = ""
                                                adminViewModel.searchManualFeatured("", searchFilterType)
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = textSecondary)
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = subtleBorder,
                                        focusedContainerColor = elevatedBackground,
                                        unfocusedContainerColor = elevatedBackground,
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Filter Row: Todos, Filmes, Séries
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("all" to "Todos", "movie" to "Filmes", "tv" to "Séries").forEach { (typeKey, typeLabel) ->
                                        FilterChip(
                                            selected = searchFilterType == typeKey,
                                            onClick = {
                                                searchFilterType = typeKey
                                                if (searchQuery.isNotBlank()) {
                                                    adminViewModel.searchManualFeatured(searchQuery, typeKey)
                                                }
                                            },
                                            label = { Text(typeLabel, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = primaryColor,
                                                selectedLabelColor = Color.White,
                                                containerColor = elevatedBackground,
                                                labelColor = textSecondary
                                            )
                                        )
                                    }
                                }

                                // Search results list
                                if (searchResults.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Resultados encontrados (${searchResults.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(searchResults) { resultItem ->
                                            SearchResultCard(
                                                item = resultItem,
                                                isAlreadyAdded = featuredConfig.items.any { it.uniqueKey == resultItem.uniqueKey },
                                                onAdd = { adminViewModel.addManualFeaturedItem(resultItem) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 5. PREVIEW BANNER (IF PENDING PREVIEW ITEMS EXIST) ---
                if (featuredConfig.previewItems.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF38BDF8))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Pré-visualização do TMDB (${featuredConfig.previewItems.size} novos itens)",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Button(
                                        onClick = { adminViewModel.publishPreviewFeaturedItems() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Publicar Estes Destaques", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Estes itens foram sugeridos pelo TMDB. Você pode revisar antes de aplicar no app oficial.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(featuredConfig.previewItems) { previewItem ->
                                        PreviewItemCard(
                                            item = previewItem,
                                            onRemove = { adminViewModel.removeFeaturedItemByKey(previewItem.uniqueKey) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 6. CURRENT ACTIVE / PUBLISHED FEATURED ITEMS ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Destaques Ativos no Aplicativo",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "${featuredConfig.items.size} conteúdos configurados na ordem de exibição",
                                fontSize = 13.sp,
                                color = textSecondary
                            )
                        }

                        if (featuredConfig.items.isNotEmpty()) {
                            Surface(
                                color = elevatedBackground,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${featuredConfig.items.count { it.isActive }} Ativos",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (featuredConfig.items.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MovieFilter,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Nenhum conteúdo em destaque configurado",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Clique em 'Atualizar Agora' para carregar do TMDB ou use a busca para adicionar manualmente.",
                                    fontSize = 13.sp,
                                    color = textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(featuredConfig.items, key = { _, item -> item.uniqueKey }) { index, featuredItem ->
                        FeaturedItemRowCard(
                            index = index,
                            totalCount = featuredConfig.items.size,
                            item = featuredItem,
                            onMoveUp = { adminViewModel.moveFeaturedItemByKey(featuredItem.uniqueKey, up = true) },
                            onMoveDown = { adminViewModel.moveFeaturedItemByKey(featuredItem.uniqueKey, up = false) },
                            onToggleActive = { adminViewModel.toggleFeaturedItemActiveByKey(featuredItem.uniqueKey) },
                            onExclude = { adminViewModel.addExcludedTmdbKey(featuredItem.uniqueKey) },
                            onPlayTrailer = { showTrailerDialog = featuredItem },
                            onDelete = { itemToDelete = featuredItem }
                        )
                    }
                }
            }

            // --- SUB-TAB 1: EXCLUSIONS LIST ---
            1 -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Lista de Exclusões TMDB",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "Conteúdos que nunca devem ser selecionados pela automação",
                                        fontSize = 13.sp,
                                        color = textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (featuredConfig.excludedTmdbKeys.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nenhuma exclusão cadastrada. Todos os itens qualificados do TMDB podem aparecer.",
                                        fontSize = 13.sp,
                                        color = textSecondary
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    featuredConfig.excludedTmdbKeys.forEach { key ->
                                        Surface(
                                            color = elevatedBackground,
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.NotInterested, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(text = "Chave TMDB: $key", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                                                }

                                                IconButton(
                                                    onClick = { adminViewModel.removeExcludedTmdbKey(key) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remover exclusão", tint = textSecondary, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SUB-TAB 2: HISTORY ---
            2 -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = Color(0xFF60A5FA))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Histórico de Atualizações de Destaques",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "Registro de sincronizações e alterações realizadas",
                                        fontSize = 13.sp,
                                        color = textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (featuredHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nenhum histórico registrado ainda.",
                                        fontSize = 13.sp,
                                        color = textSecondary
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    featuredHistory.forEach { histEntry ->
                                        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(histEntry.timestamp))
                                        Surface(
                                            color = elevatedBackground,
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .background(if (histEntry.mode == "automatic") Color(0xFF38BDF8) else Color(0xFFA855F7), CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Modo: ${histEntry.mode.uppercase()} (${histEntry.source})",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textColor
                                                        )
                                                    }
                                                    Text(text = dateStr, fontSize = 11.sp, color = textSecondary)
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Itens atualizados: ${histEntry.count} conteúdos | Período: ${histEntry.period}",
                                                    fontSize = 12.sp,
                                                    color = textSecondary
                                                )

                                                if (histEntry.itemsSummary.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Amostra: ${histEntry.itemsSummary.joinToString(", ")}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFCBD5E1),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Trailer Preview Dialog
    showTrailerDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showTrailerDialog = null },
            title = {
                Text(
                    text = "Trailer: ${item.title}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    if (item.trailerUrl.isNotBlank()) {
                        Text(
                            text = "URL/Chave do Trailer:",
                            fontSize = 13.sp,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.trailerUrl,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF60A5FA)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val url = if (item.trailerUrl.startsWith("http")) item.trailerUrl
                                else "https://www.youtube.com/watch?v=${item.trailerUrl}"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir Trailer no YouTube")
                        }
                    } else {
                        Text(
                            text = "Nenhum trailer do YouTube associado a este item.",
                            fontSize = 14.sp,
                            color = textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrailerDialog = null }) {
                    Text("Fechar", color = Color.White)
                }
            },
            containerColor = cardBackground
        )
    }

    // Confirmation Dialog for Delete from Featured
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text("Remover dos Destaques?", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    text = "Tem certeza que deseja remover '${item.title}' da lista de destaques?\n\nNota: O item NÃO será excluído do seu catálogo oficial.",
                    fontSize = 14.sp,
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.removeFeaturedItemByKey(item.uniqueKey)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Remover Destaque", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = cardBackground
        )
    }
}

@Composable
private fun StatusIndicatorItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color(0xFF9E9EB2))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) BrandRed else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF9E9EB2),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF9E9EB2)
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    item: FeaturedItemConfigEntity,
    isAlreadyAdded: Boolean,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D2D3D)),
        modifier = Modifier.width(130.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.posterPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Media type badge
                Surface(
                    color = if (item.mediaType == "movie") Color(0xFFDC2626) else Color(0xFF2563EB),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (item.mediaType == "movie") "FILME" else "SÉRIE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.releaseYear > 0) item.releaseYear.toString() else "-",
                    fontSize = 11.sp,
                    color = Color(0xFF9E9EB2)
                )
                if (item.rating > 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                        Text(text = String.format(Locale.US, "%.1f", item.rating), fontSize = 11.sp, color = Color(0xFFFBBF24))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onAdd,
                enabled = !isAlreadyAdded,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    disabledContainerColor = Color(0xFF333344)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            ) {
                Text(
                    text = if (isAlreadyAdded) "Adicionado" else "+ Destacar",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PreviewItemCard(
    item: FeaturedItemConfigEntity,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.width(120.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.posterPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${if (item.mediaType == "movie") "Filme" else "Série"} • ⭐ ${String.format(Locale.US, "%.1f", item.rating)}",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun FeaturedItemRowCard(
    index: Int,
    totalCount: Int,
    item: FeaturedItemConfigEntity,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleActive: () -> Unit,
    onExclude: () -> Unit,
    onPlayTrailer: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181820)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.isActive) Color(0xFF2D2D3D) else Color(0xFF3F1D1D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position Badge #1, #2...
            Surface(
                color = if (index < 3) BrandRed else Color(0xFF22222E),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Poster thumbnail
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.posterPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isActive) Color.White else Color(0xFF71717A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (item.mediaType == "movie") Color(0xFFDC2626).copy(alpha = 0.2f) else Color(0xFF2563EB).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (item.mediaType == "movie") "FILME" else "SÉRIE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.mediaType == "movie") Color(0xFFF87171) else Color(0xFF60A5FA),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.releaseYear > 0) {
                        Text(text = item.releaseYear.toString(), fontSize = 12.sp, color = Color(0xFF9E9EB2))
                    }
                    if (item.rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = String.format(Locale.US, "%.1f", item.rating), fontSize = 12.sp, color = Color(0xFFFBBF24))
                        }
                    }
                    if (item.trailerUrl.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onPlayTrailer() }
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Trailer", fontSize = 11.sp, color = Color(0xFF60A5FA))
                        }
                    }
                }

                if (item.overview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.overview,
                        fontSize = 11.sp,
                        color = Color(0xFF71717A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action Buttons: Move Up/Down, Visibility, Trailer, Delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Move Up
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Mover para cima",
                        tint = if (index > 0) Color.White else Color(0xFF444455),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Move Down
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Mover para baixo",
                        tint = if (index < totalCount - 1) Color.White else Color(0xFF444455),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Toggle Visibility
                IconButton(
                    onClick = onToggleActive,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (item.isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Alternar Visibilidade",
                        tint = if (item.isActive) Color(0xFF34D399) else Color(0xFF71717A),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete from Featured
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remover dos Destaques",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
