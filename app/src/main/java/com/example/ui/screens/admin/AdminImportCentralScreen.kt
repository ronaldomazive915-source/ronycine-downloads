package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.data.local.TmdbAutoSyncHistoryEntity
import com.example.data.remote.ImportConfig
import com.example.data.remote.ImportItem
import com.example.data.remote.ImportJob
import com.example.data.repository.TmdbSearchResultItem
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class ImportMode {
    INDIVIDUAL,
    MASS,
    MGEB,
    AUTOMATIC,
    RECENT,
    HISTORY
}

@Composable
fun AdminImportCentralScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier,
    initialMode: ImportMode = ImportMode.INDIVIDUAL,
    onNavigateToMedia: ((Int, String) -> Unit)? = null
) {
    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "movie", "tv"
    val importMessage by adminViewModel.importMessage.collectAsState()

    // Auto load config and recent candidates on start
    LaunchedEffect(Unit) {
        adminViewModel.loadTmdbAutoSyncConfig()
        adminViewModel.loadRecentCandidatesFromTmdb()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // --- HEADER PRINCIPAL DE IMPORTAÇÃO (DARK PREMIUM RONYCINE) ---
        Surface(
            color = DarkSurface,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "IMPORTAR CONTEÚDO",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Painel Profissional de Gestão e Importação do Catálogo TMDB",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Filters by media type (Todos / Filmes / Séries)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterBadge(
                            label = "Todos",
                            selected = selectedTypeFilter == "ALL",
                            onClick = { selectedTypeFilter = "ALL" }
                        )
                        FilterBadge(
                            label = "Filmes",
                            selected = selectedTypeFilter == "movie",
                            onClick = { selectedTypeFilter = "movie" }
                        )
                        FilterBadge(
                            label = "Séries",
                            selected = selectedTypeFilter == "tv",
                            onClick = { selectedTypeFilter = "tv" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- NAVEGAÇÃO DE MODOS DE IMPORTAÇÃO (SCROLLABLE TAB BAR) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModeTabButton(
                        icon = Icons.Default.Search,
                        label = "⚡ Inteligente",
                        selected = selectedMode == ImportMode.INDIVIDUAL,
                        onClick = { selectedMode = ImportMode.INDIVIDUAL }
                    )
                    ModeTabButton(
                        icon = Icons.Default.DynamicFeed,
                        label = "📦 Em Massa",
                        selected = selectedMode == ImportMode.MASS,
                        onClick = { selectedMode = ImportMode.MASS }
                    )
                    ModeTabButton(
                        icon = Icons.Default.Language,
                        label = "🌐 Mgeb API",
                        selected = selectedMode == ImportMode.MGEB,
                        onClick = { selectedMode = ImportMode.MGEB }
                    )
                    ModeTabButton(
                        icon = Icons.Default.AutoMode,
                        label = "🤖 Automática",
                        selected = selectedMode == ImportMode.AUTOMATIC,
                        onClick = { selectedMode = ImportMode.AUTOMATIC }
                    )
                    ModeTabButton(
                        icon = Icons.Default.NewReleases,
                        label = "🔥 Recentes",
                        selected = selectedMode == ImportMode.RECENT,
                        onClick = { selectedMode = ImportMode.RECENT }
                    )
                    ModeTabButton(
                        icon = Icons.Default.History,
                        label = "📋 Histórico",
                        selected = selectedMode == ImportMode.HISTORY,
                        onClick = { selectedMode = ImportMode.HISTORY }
                    )
                }
            }
        }

        // Global message notification banner
        importMessage?.let { msg ->
            Surface(
                color = when {
                    msg.contains("sucesso") || msg.contains("✓") || msg.contains("🚀") || msg.contains("Importado") -> Color(0xFF1B5E20)
                    msg.contains("já existe") || msg.contains("⚠️") -> Color(0xFFE65100)
                    else -> Color(0xFFB71C1C)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { adminViewModel.clearImportMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- CONTEÚDO DA CENTRAL DE IMPORTAÇÃO ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            when (selectedMode) {
                ImportMode.INDIVIDUAL -> SmartImportSection(
                    adminViewModel = adminViewModel,
                    typeFilter = selectedTypeFilter,
                    onNavigateToMedia = onNavigateToMedia
                )
                ImportMode.MASS -> MassImportSection(
                    adminViewModel = adminViewModel,
                    typeFilter = selectedTypeFilter
                )
                ImportMode.MGEB -> MgebImportSection(
                    adminViewModel = adminViewModel,
                    typeFilter = selectedTypeFilter
                )
                ImportMode.AUTOMATIC -> AutoImportSection(
                    adminViewModel = adminViewModel
                )
                ImportMode.RECENT -> RecentToImportSection(
                    adminViewModel = adminViewModel,
                    typeFilter = selectedTypeFilter,
                    onSwitchToMass = { selectedMode = ImportMode.MASS }
                )
                ImportMode.HISTORY -> HistoryImportSection(
                    adminViewModel = adminViewModel
                )
            }
        }
    }
}

// ============================================================================
// 1. REESTRUTURAÇÃO VISUAL E FUNCIONAL: IMPORTAÇÃO INTELIGENTE (SMART IMPORT)
// ============================================================================

@Composable
fun SmartImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String,
    onNavigateToMedia: ((Int, String) -> Unit)?
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by adminViewModel.searchResults.collectAsState()
    val isSearching by adminViewModel.isSearching.collectAsState()

    val allCatalogMedia by adminViewModel.allCatalogMedia.collectAsState()
    val recentCandidates by adminViewModel.recentCandidates.collectAsState()
    val isLoadingRecent by adminViewModel.isLoadingRecentCandidates.collectAsState()

    // Status filter: "ALL", "NEW", "CATALOG", "UPDATE", "ERROR"
    var selectedStatusFilter by remember { mutableStateOf("ALL") }

    // Sort option: "RELEVANCE", "RECENT", "AZ", "ZA", "RATING"
    var selectedSortOption by remember { mutableStateOf("RELEVANCE") }

    // Selection mode toggle
    var isSelectionModeActive by remember { mutableStateOf(false) }

    // Set of selected items: Pair(tmdbId, mediaType)
    var selectedItemIds by remember { mutableStateOf<Set<Pair<Int, String>>>(emptySet()) }

    // State for direct import processing (tmdbId_type -> Boolean)
    var activeImportingIds by remember { mutableStateOf<Set<Pair<Int, String>>>(emptySet()) }

    // Mass import confirmation modal state
    var showMassImportConfirmModal by remember { mutableStateOf(false) }

    // Prepare unified grid list from search results OR recent candidates
    val rawGridList = remember(searchResults, recentCandidates, searchQuery) {
        if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
            searchResults.map { item ->
                val entity = item.entity
                val exists = allCatalogMedia.any { it.tmdbId == entity.tmdbId && it.mediaType == entity.mediaType }
                AdminViewModel.MassCandidateItem(
                    tmdbId = entity.tmdbId,
                    mediaType = entity.mediaType,
                    title = entity.title,
                    originalTitle = entity.originalTitle,
                    year = entity.releaseYear,
                    rating = entity.rating,
                    posterPath = entity.posterPath,
                    overview = entity.overview,
                    genres = entity.genres,
                    isAlreadyInCatalog = exists
                )
            }
        } else {
            recentCandidates.map { cand ->
                val exists = allCatalogMedia.any { it.tmdbId == cand.tmdbId && it.mediaType == cand.mediaType }
                cand.copy(isAlreadyInCatalog = exists)
            }
        }
    }

    // Apply Type Filter
    val typeFilteredList = remember(rawGridList, typeFilter) {
        if (typeFilter == "ALL") rawGridList
        else rawGridList.filter { it.mediaType == typeFilter }
    }

    // Apply Status Filter
    val statusFilteredList = remember(typeFilteredList, selectedStatusFilter) {
        when (selectedStatusFilter) {
            "NEW" -> typeFilteredList.filter { !it.isAlreadyInCatalog }
            "CATALOG" -> typeFilteredList.filter { it.isAlreadyInCatalog }
            "UPDATE" -> typeFilteredList.filter { it.isAlreadyInCatalog && it.mediaType == "tv" }
            else -> typeFilteredList
        }
    }

    // Apply Sorting
    val finalDisplayList = remember(statusFilteredList, selectedSortOption) {
        when (selectedSortOption) {
            "AZ" -> statusFilteredList.sortedBy { it.title.lowercase() }
            "ZA" -> statusFilteredList.sortedByDescending { it.title.lowercase() }
            "RATING" -> statusFilteredList.sortedByDescending { it.rating }
            "RECENT" -> statusFilteredList.sortedByDescending { it.year }
            else -> statusFilteredList
        }
    }

    // Tab Counts calculation
    val allCount = rawGridList.size
    val moviesCount = rawGridList.count { it.mediaType == "movie" }
    val seriesCount = rawGridList.count { it.mediaType == "tv" }

    // Calculate selection statistics
    val selectedList = remember(selectedItemIds, finalDisplayList) {
        finalDisplayList.filter { Pair(it.tmdbId, it.mediaType) in selectedItemIds }
    }
    val selectedMoviesCount = selectedList.count { it.mediaType == "movie" }
    val selectedSeriesCount = selectedList.count { it.mediaType == "tv" }

    val selectedAlreadyInCatalogCount = selectedList.count { it.isAlreadyInCatalog }
    val selectedNewToImportCount = selectedList.size - selectedAlreadyInCatalogCount

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        // Responsive Grid: 6 on Desktop, 4 on Tablet, 3 on Mobile (User Request)
        val columns = when {
            screenWidth >= 1200.dp -> 6
            screenWidth >= 900.dp -> 5
            screenWidth >= 600.dp -> 4
            else -> 3
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- TOP SEARCH & CONTROLS PANEL ---
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            adminViewModel.onSearchQueryChanged(it)
                        },
                        placeholder = {
                            Text("🔎 Pesquisar filmes ou séries (por Título, TMDB ID ou Ano)...", color = Color.Gray, fontSize = 12.sp)
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                        trailingIcon = {
                            if (isSearching) {
                                RonycineSmileLoader(color = BrandRed, size = 16.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    adminViewModel.onSearchQueryChanged("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar busca", tint = Color.Gray)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // ABAS COM CONTADORES REAIS DE CONTEÚDO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TabBadge(
                                label = "Todos ($allCount)",
                                selected = typeFilter == "ALL",
                                onClick = { adminViewModel.setSearchFilter("all") }
                            )
                            TabBadge(
                                label = "Filmes ($moviesCount)",
                                selected = typeFilter == "movie",
                                onClick = { adminViewModel.setSearchFilter("movie") }
                            )
                            TabBadge(
                                label = "Séries ($seriesCount)",
                                selected = typeFilter == "tv",
                                onClick = { adminViewModel.setSearchFilter("tv") }
                            )
                        }

                        // Toggle Selection Mode Button
                        Button(
                            onClick = {
                                isSelectionModeActive = !isSelectionModeActive
                                if (!isSelectionModeActive) {
                                    selectedItemIds = emptySet()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelectionModeActive) BrandRed else Color.Black.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelectionModeActive) BrandRed else CardBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelectionModeActive) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSelectionModeActive) "Modo Seleção (Ativo)" else "☑ SELECIONAR MAIS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // FILTROS & ORDENAÇÃO
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status Filter
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Status:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FilterPill("Todos", selectedStatusFilter == "ALL") { selectedStatusFilter = "ALL" }
                            FilterPill("Novos", selectedStatusFilter == "NEW") { selectedStatusFilter = "NEW" }
                            FilterPill("No Catálogo", selectedStatusFilter == "CATALOG") { selectedStatusFilter = "CATALOG" }
                            FilterPill("Atualização", selectedStatusFilter == "UPDATE") { selectedStatusFilter = "UPDATE" }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(CardBorder)
                        )

                        // Sort Options
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Ordem:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FilterPill("Relevantes", selectedSortOption == "RELEVANCE") { selectedSortOption = "RELEVANCE" }
                            FilterPill("Recentes", selectedSortOption == "RECENT") { selectedSortOption = "RECENT" }
                            FilterPill("A-Z", selectedSortOption == "AZ") { selectedSortOption = "AZ" }
                            FilterPill("Z-A", selectedSortOption == "ZA") { selectedSortOption = "ZA" }
                            FilterPill("★ Nota", selectedSortOption == "RATING") { selectedSortOption = "RATING" }
                        }
                    }

                    // GENERAL SELECT ALL CONTROL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (selectedItemIds.size == finalDisplayList.size && finalDisplayList.isNotEmpty()) {
                                    selectedItemIds = emptySet()
                                } else {
                                    selectedItemIds = finalDisplayList.map { Pair(it.tmdbId, it.mediaType) }.toSet()
                                }
                            }
                        ) {
                            Checkbox(
                                checked = selectedItemIds.isNotEmpty() && selectedItemIds.size == finalDisplayList.size && finalDisplayList.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedItemIds = finalDisplayList.map { Pair(it.tmdbId, it.mediaType) }.toSet()
                                    } else {
                                        selectedItemIds = emptySet()
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BrandRed,
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Selecionar tudo nesta página",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedItemIds.isNotEmpty()) {
                            TextButton(
                                onClick = { selectedItemIds = emptySet() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Limpar seleção", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Mostrando ${finalDisplayList.size} itens",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // --- RESPONSIVE GRID DE CARDS ---
            if (isLoadingRecent && finalDisplayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RonycineSmileLoader(color = BrandRed, size = 32.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Buscando dados no TMDB...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else if (finalDisplayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum conteúdo encontrado para os filtros selecionados.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(finalDisplayList, key = { "${it.tmdbId}_${it.mediaType}" }) { item ->
                        val itemKey = Pair(item.tmdbId, item.mediaType)
                        val isSelected = selectedItemIds.contains(itemKey)
                        val isImporting = activeImportingIds.contains(itemKey)

                        ModernImportGridCard(
                            item = item,
                            isSelected = isSelected,
                            isImporting = isImporting,
                            isSelectionMode = isSelectionModeActive,
                            onToggleSelect = {
                                selectedItemIds = if (isSelected) selectedItemIds - itemKey else selectedItemIds + itemKey
                            },
                            onImportClick = {
                                if (item.isAlreadyInCatalog) {
                                    if (onNavigateToMedia != null) {
                                        onNavigateToMedia(item.tmdbId, item.mediaType)
                                    } else {
                                        adminViewModel.reimportOrUpdateSeries(item.tmdbId)
                                    }
                                } else {
                                    activeImportingIds = activeImportingIds + itemKey
                                    adminViewModel.importSingleDirect(
                                        tmdbId = item.tmdbId,
                                        mediaType = item.mediaType
                                    ) { success, _ ->
                                        activeImportingIds = activeImportingIds - itemKey
                                    }
                                }
                            },
                            onNavigateToMedia = onNavigateToMedia
                        )
                    }
                }
            }

            // --- BARRA FIXA FLUTUANTE DE IMPORTAÇÃO EM MASSA ---
            AnimatedVisibility(
                visible = selectedItemIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF1E1012),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, BrandRed),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${selectedItemIds.size} selecionados",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = "$selectedMoviesCount filmes • $selectedSeriesCount séries",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { selectedItemIds = emptySet() },
                                border = BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Limpar", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { showMassImportConfirmModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Importar selecionados (${selectedItemIds.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- MODAL INTELIGENTE DE CONFIRMAÇÃO DE IMPORTAÇÃO EM MASSA ---
    if (showMassImportConfirmModal) {
        AlertDialog(
            onDismissRequest = { showMassImportConfirmModal = false },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = BrandRed)
                    Text("CONFIRMAÇÃO DE IMPORTAÇÃO", fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Você selecionou ${selectedItemIds.size} conteúdos.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    HorizontalDivider(color = CardBorder, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Já existem no catálogo:", fontSize = 12.sp, color = Color.Gray)
                        Text("$selectedAlreadyInCatalogCount", fontWeight = FontWeight.Bold, color = Color(0xFFFFB300), fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Disponíveis para importação:", fontSize = 12.sp, color = Color.Gray)
                        Text("$selectedNewToImportCount", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }

                    if (selectedAlreadyInCatalogCount > 0) {
                        Text(
                            text = "ℹ Os $selectedAlreadyInCatalogCount conteúdos que já existem serão mantidos sem duplicação.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val candidatesToImport = selectedList.filter { !it.isAlreadyInCatalog }
                        if (candidatesToImport.isNotEmpty()) {
                            adminViewModel.addMassCandidates(candidatesToImport.map { it.copy(selected = true) })
                            adminViewModel.startMassImportForSelectedCandidates(ImportConfig(concurrentWorkers = 3))
                        }
                        selectedItemIds = emptySet()
                        showMassImportConfirmModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(6.dp),
                    enabled = selectedNewToImportCount > 0
                ) {
                    Text(
                        text = if (selectedNewToImportCount > 0) "Importar $selectedNewToImportCount novos" else "Nada de novo para importar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMassImportConfirmModal = false }) {
                    Text("Cancelar", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }
}

// ============================================================================
// COMPONENTE DO CARD PROFISSIONAL DO GRID (ESTRUTURA COMPACTA & ALTURA IGUAL)
// ============================================================================

@Composable
fun MediaDetailsModal(
    item: AdminViewModel.MassCandidateItem,
    onDismiss: () -> Unit,
    onImportClick: () -> Unit,
    onNavigateToMedia: ((Int, String) -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(135.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        if (!item.posterPath.isNullOrBlank()) {
                            AsyncImage(
                                model = item.posterPath,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("SEM POSTER", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (item.mediaType == "movie") BrandRed else Color(0xFF1976D2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (item.mediaType == "movie") "FILME" else "SÉRIE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (item.year.isNotBlank()) {
                            Text("Ano: ${item.year}", color = Color.White, fontSize = 12.sp)
                        }
                        Text("TMDB ID: #${item.tmdbId}", color = Color.Gray, fontSize = 11.sp)

                        if (item.rating > 0) {
                            Text("Nota: ⭐ ${String.format(Locale.US, "%.1f", item.rating)}", color = Color(0xFFFFA000), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            color = if (item.isAlreadyInCatalog) Color(0xFF1B5E20) else Color(0xFF0D47A1),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (item.isAlreadyInCatalog) "✓ No Catálogo" else "↓ Disponível para Importação",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!item.overview.isNullOrBlank()) {
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Text("SINOPSE:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = item.overview,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            if (item.isAlreadyInCatalog) {
                Button(
                    onClick = {
                        onDismiss()
                        onNavigateToMedia?.invoke(item.tmdbId, item.mediaType)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver no Catálogo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onImportClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Importar Conteúdo", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color.Gray, fontSize = 11.sp)
            }
        }
    )
}

@Composable
fun ModernImportGridCard(
    item: AdminViewModel.MassCandidateItem,
    isSelected: Boolean,
    isImporting: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onImportClick: () -> Unit,
    onNavigateToMedia: ((Int, String) -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDetailsModal by remember { mutableStateOf(false) }
    val isMovie = item.mediaType == "movie"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E1012) else Color(0xFF0F0F12)
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) BrandRed else CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable {
                if (isSelectionMode) {
                    onToggleSelect()
                } else {
                    showDetailsModal = true
                }
            }
            .testTag("import_card_${item.tmdbId}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. POSTER AREA (Compact Vertical 2:3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.Black)
            ) {
                if (!item.posterPath.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.posterPath)
                            .crossfade(true)
                            .size(200, 300) // Smaller cache size for performance in grid
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
                    }
                }

                // Selection Overlay
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isSelected) BrandRed.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandRed,
                                uncheckedColor = Color.White.copy(alpha = 0.7f),
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(2.dp)
                                .scale(0.7f)
                        )
                    }
                }

                // Status Badge Overlay (Top Left)
                if (item.isAlreadyInCatalog) {
                    Surface(
                        color = Color(0xFF1B5E20).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomEnd = 4.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Type Badge (Top Right)
                Surface(
                    color = (if (isMovie) Color(0xFFE53935) else Color(0xFF1E88E5)).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(bottomStart = 4.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (isMovie) "FILME" else "SÉRIE",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // 2. METADATA AREA (Ultra Compact)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.year.isNotBlank()) item.year else "S/A",
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                    if (item.rating > 0) {
                        Text(
                            text = "⭐ ${String.format(Locale.US, "%.1f", item.rating)}",
                            color = Color(0xFFFFA000),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Compact Action Button
                if (isImporting) {
                    Surface(
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), color = BrandRed, strokeWidth = 1.5.dp)
                        }
                    }
                } else {
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isAlreadyInCatalog) Color(0xFF26262E) else BrandRed
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().height(26.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (item.isAlreadyInCatalog) {
                                if (!isMovie) "ATUALIZAR" else "DETALHES"
                            } else "IMPORTAR",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    if (showDetailsModal) {
        MediaDetailsModal(
            item = item,
            onDismiss = { showDetailsModal = false },
            onImportClick = onImportClick,
            onNavigateToMedia = onNavigateToMedia
        )
    }
}

// Sub-componentes visuais auxiliares
@Composable
fun TabBadge(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) BrandRed else CardBorder)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) BrandRed else CardBorder.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = if (selected) BrandRed else Color.Gray,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ============================================================================
// 2. IMPORTAÇÃO EM MASSA (MASS IMPORT)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MassImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String
) {
    var rawInputText by remember { mutableStateOf("") }
    val candidates by adminViewModel.massCandidates.collectAsState()
    val selectedJobId by adminViewModel.selectedJobId.collectAsState()
    val currentJob by adminViewModel.currentImportJob.collectAsState()
    val currentItems by adminViewModel.currentImportItems.collectAsState()
    val isBulkImportRunning by adminViewModel.isBulkImportRunning.collectAsState()

    val selectedCandidatesCount = candidates.count { it.selected }
    val isRunning = isBulkImportRunning || (currentJob?.status == "processing" || currentJob?.status == "queued")
    val processedCount = currentJob?.processed ?: 0
    val totalCount = currentJob?.total ?: selectedCandidatesCount

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toolbar de importação em massa
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📦 GERENCIADOR DE IMPORTAÇÃO EM MASSA",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Input de lista de IDs TMDB
                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Cole IDs TMDB separados por vírgula (ex: 550, 1399, 157336)...", color = Color.Gray, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp, max = 110.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 4
                )

                // Barra de Ações Responsiva
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    Button(
                        onClick = {
                            if (rawInputText.isNotBlank()) {
                                val idPairs = rawInputText.split(",", "\n", ";")
                                    .map { it.trim() }
                                    .mapNotNull { it.toIntOrNull() }
                                    .map { id ->
                                        AdminViewModel.MassCandidateItem(
                                            tmdbId = id,
                                            mediaType = if (typeFilter == "tv") "tv" else "movie",
                                            title = "TMDB #$id",
                                            selected = true
                                        )
                                    }
                                adminViewModel.addMassCandidates(idPairs)
                                rawInputText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Adicionar IDs", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { adminViewModel.selectAllMassCandidates(true) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sel. Todos", color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { adminViewModel.removeSelectedMassCandidates() },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rem. Sel.", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            if (!isRunning && selectedCandidatesCount > 0) {
                                adminViewModel.startMassImportForSelectedCandidates(
                                    config = ImportConfig(concurrentWorkers = 3)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFF1E88E5) else BrandRed
                        ),
                        enabled = selectedCandidatesCount > 0 || isRunning,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isRunning) {
                                "IMPORTANDO ($processedCount/$totalCount)..."
                            } else {
                                "IMPORTAR SELECIONADOS ($selectedCandidatesCount)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Card do Job Ativo de Importação em Massa
        if (selectedJobId != null && currentJob != null) {
            MassJobProgressCard(
                job = currentJob!!,
                items = currentItems,
                onPause = { adminViewModel.pauseJob(it) },
                onResume = { adminViewModel.resumeJob(it) },
                onCancel = { adminViewModel.cancelJob(it) },
                onClose = { adminViewModel.selectJob(null) }
            )
        }

        // Header de Resultados da Fila
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${candidates.size} candidatos no lote",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$selectedCandidatesCount selecionados",
                color = BrandRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Grid de Candidatos do Lote
        if (candidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nenhum candidato na fila de importação em massa.", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val screenWidth = maxWidth
                val columns = when {
                    screenWidth >= 1200.dp -> 6
                    screenWidth >= 900.dp -> 5
                    screenWidth >= 600.dp -> 4
                    else -> 3
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(candidates) { candidate ->
                        MassCandidateCompactCard(
                            candidate = candidate,
                            onToggleSelect = {
                                adminViewModel.toggleMassCandidateSelected(candidate.tmdbId, candidate.mediaType)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Card de Progresso do Job de Importação em Massa
@Composable
fun MassJobProgressCard(
    job: ImportJob,
    items: List<ImportItem>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onClose: () -> Unit
) {
    val progressPercent = if (job.total > 0) ((job.processed.toFloat() / job.total) * 100).toInt() else 0

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BrandRed),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeBox(
                        text = job.status.uppercase(),
                        color = when (job.status) {
                            "processing" -> Color(0xFF1E88E5)
                            "completed" -> Color(0xFF43A047)
                            "paused" -> Color(0xFFFB8C00)
                            else -> Color(0xFFE53935)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LOTE #${job.id.takeLast(6)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    if (job.status == "processing") {
                        IconButton(onClick = { onPause(job.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar", tint = Color.LightGray)
                        }
                    } else if (job.status == "paused") {
                        IconButton(onClick = { onResume(job.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Retomar", tint = Color.Green)
                        }
                    }
                    IconButton(onClick = { onCancel(job.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Cancelar", tint = BrandRed)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                    }
                }
            }

            Text(
                text = "IMPORTANDO ${job.processed} DE ${job.total} ITENS ($progressPercent%)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { (job.processed.toFloat() / job.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BrandRed,
                trackColor = Color.Black
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("✓ Sucesso: ${job.success}", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("ℹ Duplicados: ${job.duplicates}", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("✕ Erros: ${job.failed}", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 3. IMPORTAÇÃO MGEB API
// ============================================================================

@Composable
fun MgebImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String
) {
    val candidates by adminViewModel.mgebCandidates.collectAsState()
    val newCandidates by adminViewModel.mgebNewCandidates.collectAsState()
    val existingCandidates by adminViewModel.mgebExistingCandidates.collectAsState()
    val isMgebLoading by adminViewModel.isMgebLoading.collectAsState()
    val mgebSearchQuery by adminViewModel.mgebSearchQuery.collectAsState()

    val mgebPage by adminViewModel.mgebPage.collectAsState()
    val mgebTotalPages by adminViewModel.mgebTotalPages.collectAsState()
    val mgebTotalCount by adminViewModel.mgebTotalCount.collectAsState()
    val mgebMoviesCount by adminViewModel.mgebMoviesCount.collectAsState()
    val mgebSeriesCount by adminViewModel.mgebSeriesCount.collectAsState()
    val mgebInCatalogCount by adminViewModel.mgebInCatalogCount.collectAsState()
    val mgebNewCount by adminViewModel.mgebNewCount.collectAsState()
    val mgebStatusFilter by adminViewModel.mgebStatusFilter.collectAsState()
    val mgebTypeFilter by adminViewModel.mgebTypeFilter.collectAsState()
    val mgebSelectedIds by adminViewModel.mgebSelectedIds.collectAsState()

    LaunchedEffect(typeFilter) {
        adminViewModel.setMgebTypeFilter(typeFilter)
    }

    LaunchedEffect(Unit) {
        adminViewModel.loadMgebCatalog()
    }

    val visiblePairs = remember(candidates) {
        candidates.map { Pair(it.entity.tmdbId, it.entity.mediaType) }
    }
    val selectedVisibleCount = remember(mgebSelectedIds, visiblePairs) {
        visiblePairs.count { mgebSelectedIds.contains(it) }
    }

    var detailModalItem by remember { mutableStateOf<TmdbSearchResultItem?>(null) }

    detailModalItem?.let { selectedItem ->
        AlertDialog(
            onDismissRequest = { detailModalItem = null },
            containerColor = DarkSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedItem.entity.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { detailModalItem = null },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val poster = selectedItem.entity.posterPath
                        if (!poster.isNullOrBlank()) {
                            AsyncImage(
                                model = if (poster.startsWith("http")) poster else "https://image.tmdb.org/t/p/w342$poster",
                                contentDescription = selectedItem.entity.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TMDB ID: #${selectedItem.entity.tmdbId}",
                                color = BrandRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tipo: ${if (selectedItem.entity.mediaType == "movie") "Filme" else "Série"}",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            if (!selectedItem.entity.releaseYear.isNullOrBlank()) {
                                Text(
                                    text = "Ano: ${selectedItem.entity.releaseYear}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                            if (selectedItem.entity.rating > 0.0) {
                                Text(
                                    text = "Avaliação: ★ ${String.format(Locale.US, "%.1f", selectedItem.entity.rating)}",
                                    color = Color(0xFFFFB300),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (selectedItem.isAlreadyInCatalog) "✓ Já está no catálogo" else "✨ Disponível para importar",
                                color = if (selectedItem.isAlreadyInCatalog) Color(0xFF81C784) else BrandRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!selectedItem.entity.overview.isNullOrBlank()) {
                        Text(
                            text = "Sinopse:",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedItem.entity.overview ?: "",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (!selectedItem.isAlreadyInCatalog) {
                    Button(
                        onClick = {
                            detailModalItem = null
                            adminViewModel.importSingleDirect(selectedItem.entity.tmdbId, selectedItem.entity.mediaType)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Importar Agora", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { detailModalItem = null },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Fechar", fontSize = 11.sp)
                }
            }
        )
    }

    val mainCheckboxState = remember(selectedVisibleCount, visiblePairs.size) {
        when {
            visiblePairs.isEmpty() -> ToggleableState.Off
            selectedVisibleCount == 0 -> ToggleableState.Off
            selectedVisibleCount == visiblePairs.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. CABEÇALHO PROFISSIONAL DA ÁREA MGE API ---
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = BrandRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f)),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "MGE API",
                                    color = BrandRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "IMPORTAR CONTEÚDO",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Importe filmes e séries disponíveis no MGE sem duplicar conteúdos que já existem no catálogo.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    if (isMgebLoading) {
                        RonycineSmileLoader(color = BrandRed, size = 18.dp)
                    } else {
                        IconButton(
                            onClick = { adminViewModel.loadMgebCatalog(forceRefresh = true) },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .border(1.dp, CardBorder, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Recarregar catálogo MGE",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))

                // --- 2. INDICADORES COMPACTOS REAIS E DINÂMICOS ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MgeStatChip(
                        icon = Icons.Default.Language,
                        label = "MGE disponível",
                        value = "$mgebTotalCount",
                        accentColor = Color(0xFF64B5F6)
                    )
                    MgeStatChip(
                        icon = Icons.Default.Movie,
                        label = "Filmes encontrados",
                        value = "$mgebMoviesCount",
                        accentColor = Color(0xFFFFB74D)
                    )
                    MgeStatChip(
                        icon = Icons.Default.Tv,
                        label = "Séries encontradas",
                        value = "$mgebSeriesCount",
                        accentColor = Color(0xFFBA68C8)
                    )
                    MgeStatChip(
                        icon = Icons.Default.CheckCircle,
                        label = "Já no catálogo",
                        value = "$mgebInCatalogCount",
                        accentColor = Color(0xFF81C784)
                    )
                    MgeStatChip(
                        icon = Icons.Default.AutoAwesome,
                        label = "Novos disponíveis",
                        value = "$mgebNewCount",
                        accentColor = BrandRed
                    )
                    if (mgebSelectedIds.isNotEmpty()) {
                        MgeStatChip(
                            icon = Icons.Default.Checklist,
                            label = "Selecionados",
                            value = "${mgebSelectedIds.size}",
                            accentColor = Color(0xFFFFD54F)
                        )
                    }
                }
            }
        }

        // --- 3. ABAS MODERNAS DE TIPO (Todos / Filmes / Séries) ---
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MgeTabButton(
                        icon = Icons.Default.GridView,
                        label = "Todos ($mgebTotalCount)",
                        selected = mgebTypeFilter == "ALL",
                        onClick = { adminViewModel.setMgebTypeFilter("ALL") },
                        modifier = Modifier.weight(1f)
                    )
                    MgeTabButton(
                        icon = Icons.Default.Movie,
                        label = "Filmes ($mgebMoviesCount)",
                        selected = mgebTypeFilter == "movie",
                        onClick = { adminViewModel.setMgebTypeFilter("movie") },
                        modifier = Modifier.weight(1f)
                    )
                    MgeTabButton(
                        icon = Icons.Default.Tv,
                        label = "Séries ($mgebSeriesCount)",
                        selected = mgebTypeFilter == "tv",
                        onClick = { adminViewModel.setMgebTypeFilter("tv") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // --- 4. CAMPO DE PESQUISA FUNCIONAL & FILTROS DE STATUS ---
                OutlinedTextField(
                    value = mgebSearchQuery,
                    onValueChange = { adminViewModel.onMgebSearchQueryChanged(it) },
                    placeholder = { Text("🔎 Pesquisar por título ou TMDB ID...", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.7f),
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (mgebSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { adminViewModel.onMgebSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar busca", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // Sub-filtros: Todos / Apenas Novos / No Catálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = mgebStatusFilter == "ALL",
                        onClick = { adminViewModel.setMgebStatusFilter("ALL") },
                        label = { Text("Todos", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandRed.copy(alpha = 0.2f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            labelColor = Color.Gray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (mgebStatusFilter == "ALL") BrandRed else CardBorder,
                            enabled = true,
                            selected = mgebStatusFilter == "ALL"
                        )
                    )
                    FilterChip(
                        selected = mgebStatusFilter == "NEW_ONLY",
                        onClick = { adminViewModel.setMgebStatusFilter("NEW_ONLY") },
                        label = { Text("✨ Apenas Novos ($mgebNewCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1B5E20).copy(alpha = 0.3f),
                            selectedLabelColor = Color.Green,
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            labelColor = Color.Gray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (mgebStatusFilter == "NEW_ONLY") Color(0xFF4CAF50) else CardBorder,
                            enabled = true,
                            selected = mgebStatusFilter == "NEW_ONLY"
                        )
                    )
                    FilterChip(
                        selected = mgebStatusFilter == "IN_CATALOG_ONLY",
                        onClick = { adminViewModel.setMgebStatusFilter("IN_CATALOG_ONLY") },
                        label = { Text("✓ No Catálogo ($mgebInCatalogCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF37474F).copy(alpha = 0.4f),
                            selectedLabelColor = Color.LightGray,
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            labelColor = Color.Gray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (mgebStatusFilter == "IN_CATALOG_ONLY") Color(0xFF78909C) else CardBorder,
                            enabled = true,
                            selected = mgebStatusFilter == "IN_CATALOG_ONLY"
                        )
                    )
                }

                // --- 5. AÇÕES DE SELEÇÃO RÁPIDA ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { adminViewModel.selectOnlyNewMgeb() },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81C784)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Selecionar Novos", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val selectAll = mainCheckboxState != ToggleableState.On
                                adminViewModel.toggleMgebPageSelection(visiblePairs, selectAll)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (mainCheckboxState == ToggleableState.On) "Desmarcar Página" else "Marcar Página",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (mgebSelectedIds.isNotEmpty()) {
                        TextButton(
                            onClick = { adminViewModel.clearMgebSelection() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Limpar (${mgebSelectedIds.size})", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // --- 6. ACTION BAR FIXA QUANDO HOUVER ITENS SELECIONADOS ---
        AnimatedVisibility(visible = mgebSelectedIds.isNotEmpty()) {
            Surface(
                color = Color(0xFF1B1113),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, BrandRed),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(BrandRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${mgebSelectedIds.size} selecionados",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "Prontos para importação em lote",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { adminViewModel.clearMgebSelection() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Limpar", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                adminViewModel.startMgebSelectedImport {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "IMPORTAR (${mgebSelectedIds.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // --- 7. APRESENTAÇÃO INTELIGENTE DOS RESULTADOS (NOVOS PRIMEIRO, DEPOIS CATÁLOGO) ---
        if (isMgebLoading && candidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RonycineSmileLoader(color = BrandRed, size = 32.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Carregando catálogo da MGE API...", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else if (candidates.isEmpty()) {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (mgebSearchQuery.isNotEmpty()) "Nenhum resultado encontrado para \"$mgebSearchQuery\"." else "Nenhum conteúdo disponível nesta categoria.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // SEÇÃO 1: NOVOS PARA IMPORTAR (PRIORIDADE)
            if (newCandidates.isNotEmpty()) {
                Surface(
                    color = Color(0xFF121A14),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "NOVOS PARA IMPORTAR",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${newCandidates.size} novos",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                MgebCandidateGrid(
                    items = newCandidates,
                    selectedIds = mgebSelectedIds,
                    onToggle = { tmdbId, type ->
                        adminViewModel.toggleMgebSelection(tmdbId, type)
                    },
                    onImportSingle = { item ->
                        adminViewModel.importSingleDirect(item.entity.tmdbId, item.entity.mediaType)
                    },
                    onShowDetail = { item ->
                        detailModalItem = item
                    }
                )
            }

            // SEÇÃO 2: JÁ ESTÃO NO CATÁLOGO (FINAL DA LISTA)
            if (existingCandidates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF16181C),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF90A4AE),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "JÁ ESTÃO NO CATÁLOGO",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = Color(0xFF37474F),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${existingCandidates.size} no catálogo",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                MgebCandidateGrid(
                    items = existingCandidates,
                    selectedIds = mgebSelectedIds,
                    onToggle = { tmdbId, type ->
                        adminViewModel.toggleMgebSelection(tmdbId, type)
                    },
                    onImportSingle = { item ->
                        adminViewModel.importSingleDirect(item.entity.tmdbId, item.entity.mediaType)
                    },
                    onShowDetail = { item ->
                        detailModalItem = item
                    }
                )
            }

            // --- 8. PAGINAÇÃO MODERNA E COMPACTA ---
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { adminViewModel.setMgebPage(mgebPage - 1) },
                        enabled = mgebPage > 1,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Anterior", fontSize = 11.sp)
                    }

                    Text(
                        text = "Página $mgebPage de $mgebTotalPages",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = { adminViewModel.setMgebPage(mgebPage + 1) },
                        enabled = mgebPage < mgebTotalPages,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Próxima", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = "Próxima", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MgeStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
            Column {
                Text(text = label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun MgeTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed else Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) BrandRed else CardBorder),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.Gray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MgebCandidateGrid(
    items: List<TmdbSearchResultItem>,
    selectedIds: Set<Pair<Int, String>>,
    onToggle: (Int, String) -> Unit,
    onImportSingle: (TmdbSearchResultItem) -> Unit,
    onShowDetail: (TmdbSearchResultItem) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val screenWidth = maxWidth
        val columns = when {
            screenWidth >= 1200.dp -> 6
            screenWidth >= 900.dp -> 5
            screenWidth >= 600.dp -> 4
            else -> 3
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp) // Bound height for verticalScroll parent
        ) {
            items(items, key = { "${it.entity.tmdbId}_${it.entity.mediaType}" }) { item ->
                val isSelected = selectedIds.contains(Pair(item.entity.tmdbId, item.entity.mediaType))
                MgebCandidateCard(
                    item = item,
                    isSelected = isSelected,
                    onToggle = { onToggle(item.entity.tmdbId, item.entity.mediaType) },
                    onImportSingle = { onImportSingle(item) },
                    onShowDetail = { onShowDetail(item) }
                )
            }
        }
    }
}

@Composable
fun MgebCandidateCard(
    item: TmdbSearchResultItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onImportSingle: () -> Unit,
    onShowDetail: () -> Unit
) {
    val isMovie = item.entity.mediaType == "movie"
    val isInCatalog = item.isAlreadyInCatalog

    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFF2A1014)
                isInCatalog -> Color(0xFF141518)
                else -> DarkSurface
            }
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = when {
                isSelected -> BrandRed
                isInCatalog -> Color(0xFF2E3440)
                else -> CardBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val poster = item.entity.posterPath
            val releaseYear = item.entity.releaseYear

            Box(modifier = Modifier.fillMaxWidth()) {
                if (!poster.isNullOrBlank()) {
                    AsyncImage(
                        model = if (poster.startsWith("http")) poster else "https://image.tmdb.org/t/p/w342$poster",
                        contentDescription = item.entity.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF181820)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isMovie) Icons.Default.Movie else Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ID #${item.entity.tmdbId}",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Status Badge Overlay (Top Left)
                if (isInCatalog) {
                    Surface(
                        color = Color(0xFF1B5E20).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomEnd = 4.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Type Badge (Top Right)
                Surface(
                    color = (if (isMovie) Color(0xFFE53935) else Color(0xFF1E88E5)).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(bottomStart = 4.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (isMovie) "FILME" else "SÉRIE",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.entity.title,
                color = if (isInCatalog) Color.Gray else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!releaseYear.isNullOrBlank()) releaseYear else "S/A",
                    color = Color.Gray,
                    fontSize = 8.sp
                )
                if (item.entity.rating > 0.0) {
                    Text(
                        text = "⭐ ${String.format(Locale.US, "%.1f", item.entity.rating)}",
                        color = Color(0xFFFFB300),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// 4. IMPORTAÇÃO AUTOMÁTICA
// ============================================================================

@Composable
fun AutoImportSection(
    adminViewModel: AdminViewModel
) {
    val autoSyncConfig by adminViewModel.tmdbAutoSyncConfig.collectAsState()
    val autoSyncProgress by adminViewModel.tmdbAutoSyncProgress.collectAsState()
    val historyLogs by adminViewModel.tmdbAutoSyncHistory.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (autoSyncConfig.enabled) Color(0xFF2E7D32) else CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (autoSyncConfig.enabled) "🟢 AUTOMAÇÃO TMDB: ATIVADA" else "⚪ AUTOMAÇÃO TMDB: DESATIVADA",
                            color = if (autoSyncConfig.enabled) Color.Green else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Importação e verificação automática em segundo plano",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = autoSyncConfig.enabled,
                        onCheckedChange = { adminViewModel.updateTmdbAutoSyncEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2E7D32)
                        )
                    )
                }

                HorizontalDivider(color = CardBorder)

                Button(
                    onClick = { adminViewModel.startTmdbAutoSyncNow() },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    enabled = !autoSyncProgress.isRunning,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (autoSyncProgress.isRunning) {
                        RonycineSmileLoader(color = Color.White, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sincronizando...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EXECUTAR AGORA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Auto Sync History Logs Table
        Text(
            text = "HISTÓRICO DE EXECUÇÕES AUTOMÁTICAS:",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        if (historyLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma execução automática registrada ainda.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(historyLogs) { log ->
                    AutoSyncHistoryRow(log = log)
                }
            }
        }
    }
}

// ============================================================================
// 5. RECENTES PARA IMPORTAR
// ============================================================================

@Composable
fun RecentToImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String,
    onSwitchToMass: () -> Unit
) {
    val recentCandidates by adminViewModel.recentCandidates.collectAsState()
    val isLoading by adminViewModel.isLoadingRecentCandidates.collectAsState()

    val displayList = remember(recentCandidates, typeFilter) {
        if (typeFilter == "ALL") recentCandidates
        else recentCandidates.filter { it.mediaType == typeFilter }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔥 TENDÊNCIAS E RECENTES TMDB",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Button(
                onClick = onSwitchToMass,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ir para Lote", fontSize = 11.sp)
            }
        }

        if (isLoading && displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RonycineSmileLoader(color = BrandRed)
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidth = maxWidth
                val columns = when {
                    screenWidth >= 1200.dp -> 6
                    screenWidth >= 900.dp -> 5
                    screenWidth >= 600.dp -> 4
                    else -> 3
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayList) { candidate ->
                        RecentCandidateGridCard(
                            candidate = candidate,
                            onImportDirect = {
                                adminViewModel.importSingleDirect(candidate.tmdbId, candidate.mediaType)
                            },
                            onSelectForMass = {
                                adminViewModel.addMassCandidates(listOf(candidate.copy(selected = true)))
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. HISTÓRICO DE IMPORTAÇÕES
// ============================================================================

@Composable
fun HistoryImportSection(
    adminViewModel: AdminViewModel
) {
    val historyList by adminViewModel.importHistory.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "📋 HISTÓRICO DE IMPORTAÇÕES REGISTRADAS",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum registro recente de importação.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(historyList) { history ->
                    HistoryItemCard(history = history)
                }
            }
        }
    }
}

// ============================================================================
// SUB-COMPOSABLES SECUNDÁRIOS
// ============================================================================

@Composable
fun FilterBadge(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed else Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) BrandRed else CardBorder)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun ModeTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        border = if (selected) null else BorderStroke(1.dp, CardBorder.copy(alpha = 0.5f)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color.Gray,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun BadgeBox(text: String, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MassCandidateCompactCard(
    candidate: AdminViewModel.MassCandidateItem,
    onToggleSelect: () -> Unit
) {
    Card(
        onClick = onToggleSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.selected) BrandRed.copy(alpha = 0.1f) else DarkSurface
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (candidate.selected) BrandRed else CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = candidate.posterPath,
                    contentDescription = candidate.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (candidate.selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = candidate.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RecentCandidateGridCard(
    candidate: AdminViewModel.MassCandidateItem,
    onImportDirect: () -> Unit,
    onSelectForMass: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = candidate.posterPath,
                contentDescription = candidate.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                candidate.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onImportDirect,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                ) {
                    Text("Imp.", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSelectForMass,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                ) {
                    Text("+ Lote", color = Color.White, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun AutoSyncHistoryRow(log: TmdbAutoSyncHistoryEntity) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = sdf.format(Date(log.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "$dateStr - Auto Sync (${log.type.uppercase()})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Encontrados: ${log.totalFound} | Importados: ${log.importedCount} | Existentes: ${log.existingCount}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            BadgeBox(
                text = log.status.uppercase(),
                color = if (log.status.lowercase().contains("conclu") || log.status.lowercase().contains("sucesso")) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

@Composable
fun HistoryItemCard(history: AdminViewModel.ImportHistoryItem) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = sdf.format(Date(history.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = history.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = "$dateStr • TMDB #${history.tmdbId} • ${history.type.uppercase()}", color = Color.Gray, fontSize = 11.sp)
            }

            BadgeBox(
                text = when (history.status) {
                    "success" -> "IMPORTADO"
                    "existing" -> "JÁ EXISTIA"
                    else -> "FALHA"
                },
                color = when (history.status) {
                    "success" -> Color(0xFF2E7D32)
                    "existing" -> Color(0xFFF57F17)
                    else -> Color(0xFFC62828)
                }
            )
        }
    }
}
