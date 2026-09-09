package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.TmdbAutoSyncHistoryEntity
import com.example.data.remote.ImportConfig
import com.example.data.remote.ImportItem
import com.example.data.remote.ImportJob
import com.example.data.repository.TmdbSearchResultItem
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

    // Auto load config and recent on start
    LaunchedEffect(Unit) {
        adminViewModel.loadTmdbAutoSyncConfig()
        adminViewModel.loadRecentCandidatesFromTmdb()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // --- HEADER PRINCIPAL DE IMPORTAÇÃO ---
        Surface(
            color = DarkSurface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "IMPORTAR CONTEÚDO",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Módulo oficial de gestão e integração de filmes e séries TMDB",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Top Type Filter Badges (Filme / Série / Todos)
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

                Spacer(modifier = Modifier.height(8.dp))

                // --- NAVEGAÇÃO DE MODOS DE IMPORTAÇÃO (SCROLLABLE BAR - NO TEXT WRAPPING) ---
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
                        label = "Inteligente",
                        selected = selectedMode == ImportMode.INDIVIDUAL,
                        onClick = { selectedMode = ImportMode.INDIVIDUAL }
                    )
                    ModeTabButton(
                        icon = Icons.Default.DynamicFeed,
                        label = "Em Massa",
                        selected = selectedMode == ImportMode.MASS,
                        onClick = { selectedMode = ImportMode.MASS }
                    )
                    ModeTabButton(
                        icon = Icons.Default.Language,
                        label = "Mgeb API",
                        selected = selectedMode == ImportMode.MGEB,
                        onClick = { selectedMode = ImportMode.MGEB }
                    )
                    ModeTabButton(
                        icon = Icons.Default.AutoMode,
                        label = "Automática",
                        selected = selectedMode == ImportMode.AUTOMATIC,
                        onClick = { selectedMode = ImportMode.AUTOMATIC }
                    )
                    ModeTabButton(
                        icon = Icons.Default.NewReleases,
                        label = "Recentes",
                        selected = selectedMode == ImportMode.RECENT,
                        onClick = { selectedMode = ImportMode.RECENT }
                    )
                    ModeTabButton(
                        icon = Icons.Default.History,
                        label = "Histórico",
                        selected = selectedMode == ImportMode.HISTORY,
                        onClick = { selectedMode = ImportMode.HISTORY }
                    )
                }
            }
        }

        // Global message banner if present
        importMessage?.let { msg ->
            Surface(
                color = if (msg.contains("sucesso") || msg.contains("✓") || msg.contains("🚀")) Color(0xFF1B5E20)
                else if (msg.contains("já existe") || msg.contains("⚠️")) Color(0xFFE65100)
                else Color(0xFFB71C1C),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
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

        // --- CONTEÚDO PRINCIPAL DE ACORDO COM O MODO SELECIONADO ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
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
// 1. IMPORTAÇÃO INTELIGENTE (SMART IMPORT)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String,
    onNavigateToMedia: ((Int, String) -> Unit)?
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMediaType by remember { mutableStateOf(if (typeFilter == "tv") "tv" else "movie") }

    val isPreviewLoading by adminViewModel.isPreviewLoading.collectAsState()
    val previewMedia by adminViewModel.selectedPreviewMedia.collectAsState()
    val previewExists by adminViewModel.selectedPreviewExists.collectAsState()
    val isImportingSingle by adminViewModel.isImportingSingle.collectAsState()
    val isSeriesUpdateRunning by adminViewModel.isSeriesUpdateRunning.collectAsState()
    val isRunning = isImportingSingle || isSeriesUpdateRunning
    val importStepMessage by adminViewModel.importStepMessage.collectAsState()
    val searchResults by adminViewModel.searchResults.collectAsState()
    val isSearching by adminViewModel.isSearching.collectAsState()

    val recentCandidates by adminViewModel.recentCandidates.collectAsState()
    val isLoadingRecent by adminViewModel.isLoadingRecentCandidates.collectAsState()
    var recentFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        adminViewModel.loadRecentCandidatesFromTmdb()
    }

    val filteredRecent = recentCandidates.filter {
        val matchesType = if (typeFilter == "ALL") {
            if (recentFilter == "ALL") true else it.mediaType == recentFilter
        } else {
            it.mediaType == typeFilter
        }
        matchesType
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Search Bar Card
        item {
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "IMPORTAÇÃO INTELIGENTE",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Pesquise um filme ou série e encontre rapidamente conteúdos que ainda não estão no catálogo.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            adminViewModel.onSearchQueryChanged(it)
                        },
                        placeholder = { Text("🔎 Pesquisar filme ou série...", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandRed, strokeWidth = 2.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    adminViewModel.onSearchQueryChanged("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
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
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // Preview Card if media selected
        if (previewMedia != null) {
            item {
                val media = previewMedia!!
                val isMovie = media.mediaType.lowercase() == "movie"
                var showFullOverview by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (previewExists) Color(0xFFFFA000) else BrandRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MÍDIA SELECIONADA",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { adminViewModel.clearPreview() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Voltar para a Busca", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(media.posterPath)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(105.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = media.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (media.originalTitle.isNotBlank() && media.originalTitle != media.title) {
                                    Text(
                                        text = media.originalTitle,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BadgeBox(
                                        text = if (isMovie) "FILME" else "SÉRIE",
                                        color = if (isMovie) BrandRed else Color(0xFF1976D2)
                                    )
                                    BadgeBox(text = "★ ${String.format(Locale.US, "%.1f", media.rating)}", color = Color(0xFFF57C00))
                                    if (media.releaseYear.isNotBlank()) {
                                        BadgeBox(text = media.releaseYear, color = Color.DarkGray)
                                    }
                                }

                                if (media.genres.isNotBlank()) {
                                    Text(
                                        text = media.genres,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = media.overview.ifBlank { "Sem sinopse cadastrada no TMDB." },
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = if (showFullOverview) 10 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { showFullOverview = !showFullOverview }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (previewExists) {
                            Surface(
                                color = Color(0xFF3E2723),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF8F00)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✓ JÁ ESTÁ NO CATÁLOGO",
                                        color = Color(0xFFFFE082),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        importStepMessage?.let { stepMsg ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = BrandRed, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stepMsg, color = Color.LightGray, fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (previewExists && onNavigateToMedia != null) {
                                OutlinedButton(
                                    onClick = { onNavigateToMedia(media.tmdbId, media.mediaType) },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Ver no Catálogo", color = Color.White, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = {
                                    if (!isRunning) {
                                        if (previewExists) {
                                            adminViewModel.reimportOrUpdateSeries(media.tmdbId)
                                        } else {
                                            adminViewModel.confirmImportSelectedMedia()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (previewExists) Color(0xFFD84315) else BrandRed
                                ),
                                enabled = !isRunning,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                if (isRunning) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = importStepMessage ?: "Importando...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (previewExists) "Atualizar no Catálogo" else "Importar Conteúdo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (searchResults.isNotEmpty()) {
            item {
                Text(
                    text = "RESULTADOS DA PESQUISA (${searchResults.size}):",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(searchResults) { result ->
                SearchResultCompactCard(
                    result = result,
                    onSelect = {
                        adminViewModel.selectTmdbIdForPreview(result.entity.tmdbId.toString(), result.entity.mediaType)
                    }
                )
            }
        }

        // --- SECTION: ✨ NOVIDADES PARA IMPORTAR ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "✨ NOVIDADES PARA IMPORTAR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lançamentos recentes do TMDB que ainda não estão no catálogo",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                // Filter pills
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterBadge(label = "Todos", selected = recentFilter == "ALL") { recentFilter = "ALL" }
                    FilterBadge(label = "Filmes", selected = recentFilter == "movie") { recentFilter = "movie" }
                    FilterBadge(label = "Séries", selected = recentFilter == "tv") { recentFilter = "tv" }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoadingRecent) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandRed)
                }
            }
        } else if (filteredRecent.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma novidade pendente encontrada.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            items(filteredRecent) { candidate ->
                RecentCandidateCompactCard(
                    candidate = candidate,
                    onImportDirect = {
                        adminViewModel.selectTmdbIdForPreview(candidate.tmdbId.toString(), candidate.mediaType)
                    },
                    onSelectForMass = {
                        adminViewModel.selectTmdbIdForPreview(candidate.tmdbId.toString(), candidate.mediaType)
                    }
                )
            }
        }
    }
}

// ============================================================================
// 2. IMPORTAÇÃO EM MASSA
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
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📦 GERENCIADOR DE IMPORTAÇÃO EM MASSA",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Input para adicionar lista de IDs ou Nomes
                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Cole IDs TMDB separados por vírgula (ex: 550, 1399)...", color = Color.Gray, fontSize = 12.sp) },
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
                        .heightIn(min = 80.dp, max = 120.dp), // Height limited
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 4
                )

                // Action Bar Responsiva
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

                    // Submit Mass Import Button
                    Button(
                        onClick = {
                            if (!isRunning && selectedCandidatesCount > 0) {
                                adminViewModel.startMassImportForSelectedCandidates(
                                    config = ImportConfig(concurrentWorkers = 2)
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

        // Active Mass Job Progress Viewer if selected or running
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

        // Candidate Items Grid/List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${candidates.size} resultados",
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

        // Candidate Items Grid
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
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

// Card do Job Ativo de Importação em Massa
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
                        text = "PROGRESSO DO LOTE #${job.id.takeLast(6)}",
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

            // Real Progress Bar
            Text(
                text = "IMPORTANDO ${job.processed} DE ${job.total} ITENS ($progressPercent%)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { job.processed.toFloat() / job.total.coerceAtLeast(1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BrandRed,
                trackColor = Color.Black
            )

            // Item status breakdown summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("✓ Sucesso: ${job.success}", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("ℹ Duplicados: ${job.duplicates}", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("✕ Erros: ${job.failed}", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Realtime items status list
            if (items.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    items(items.take(15)) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${item.mediaType.uppercase()} TMDB #${item.tmdbId} - ${item.title ?: "Processando..."}",
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            BadgeBox(
                                text = item.status.uppercase(),
                                color = when (item.status) {
                                    "success" -> Color(0xFF2E7D32)
                                    "duplicate" -> Color(0xFFF57F17)
                                    "processing" -> Color(0xFF1565C0)
                                    "failed" -> Color(0xFFC62828)
                                    else -> Color.DarkGray
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. MGEB API IMPORT
// ============================================================================

@Composable
fun MgebImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String
) {
    val candidates by adminViewModel.mgebCandidates.collectAsState()
    val isMgebLoading by adminViewModel.isMgebLoading.collectAsState()
    val mgebSearchQuery by adminViewModel.mgebSearchQuery.collectAsState()

    val mgebPage by adminViewModel.mgebPage.collectAsState()
    val mgebTotalPages by adminViewModel.mgebTotalPages.collectAsState()
    val mgebTotalCount by adminViewModel.mgebTotalCount.collectAsState()
    val mgebSelectedIds by adminViewModel.mgebSelectedIds.collectAsState()

    // Sync parental typeFilter to ViewModel dynamically
    LaunchedEffect(typeFilter) {
        adminViewModel.setMgebTypeFilter(typeFilter)
    }

    LaunchedEffect(Unit) {
        adminViewModel.loadMgebCatalog()
    }

    // Identify visible items for selection calculations
    val visiblePairs = remember(candidates) {
        candidates.map { Pair(it.entity.tmdbId, it.entity.mediaType) }
    }
    val selectedVisibleCount = remember(mgebSelectedIds, visiblePairs) {
        visiblePairs.count { mgebSelectedIds.contains(it) }
    }

    val mainCheckboxState = remember(selectedVisibleCount, visiblePairs.size) {
        when {
            visiblePairs.isEmpty() -> ToggleableState.Off
            selectedVisibleCount == 0 -> ToggleableState.Off
            selectedVisibleCount == visiblePairs.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    }

    val onMainCheckboxClick = {
        val selectAll = mainCheckboxState != ToggleableState.On
        adminViewModel.toggleMgebPageSelection(visiblePairs, selectAll)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- FILTERS & SEARCH ROW ---
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌐 EXPLORAR CATÁLOGO MGEB API",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    
                    if (isMgebLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandRed, strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { adminViewModel.loadMgebCatalog(forceRefresh = true) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Text(
                    text = "Busca direta no catálogo remoto Mgeb (Dublado). Selecione múltiplos itens e clique no botão de importação em lote.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = mgebSearchQuery,
                    onValueChange = { adminViewModel.onMgebSearchQueryChanged(it) },
                    placeholder = { Text("Pesquisar por ID ou título...", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
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
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        // --- STICKY / COMPACT ACTION BAR (Appears dynamically if any selected) ---
        AnimatedVisibility(
            visible = mgebSelectedIds.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${mgebSelectedIds.size} selecionados",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { adminViewModel.clearMgebSelection() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Limpar", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        var showConfirmModal by remember { mutableStateOf(false) }
                        var selectionSummary by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) } // total, novos, existentes

                        Button(
                            onClick = {
                                adminViewModel.getMgebSelectionSummary { total, novos, existentes ->
                                    selectionSummary = Triple(total, novos, existentes)
                                    showConfirmModal = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "IMPORTAR ${mgebSelectedIds.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Confirmation Modal
                        if (showConfirmModal && selectionSummary != null) {
                            val (total, novos, existentes) = selectionSummary!!
                            AlertDialog(
                                onDismissRequest = { showConfirmModal = false },
                                containerColor = DarkSurface,
                                titleContentColor = Color.White,
                                textContentColor = Color.LightGray,
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = BrandRed)
                                        Text("CONFIRMAR IMPORTAÇÃO", fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Origem dos dados: MGEB API", fontSize = 11.sp, color = Color.Gray)
                                        HorizontalDivider(color = CardBorder, thickness = 1.dp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total selecionados:", fontSize = 12.sp)
                                            Text("$total", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Novos conteúdos:", fontSize = 12.sp)
                                            Text("$novos", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Já no catálogo (serão ignorados):", fontSize = 12.sp)
                                            Text("$existentes", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            adminViewModel.startMgebSelectedImport {
                                                showConfirmModal = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        shape = RoundedCornerShape(4.dp),
                                        enabled = novos > 0
                                    ) {
                                        Text("IMPORTAR $novos", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmModal = false }) {
                                        Text("CANCELAR", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- TOP SELECT ALL CONTROL HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TriStateCheckbox(
                    state = mainCheckboxState,
                    onClick = onMainCheckboxClick,
                    colors = CheckboxDefaults.colors(
                        checkedColor = BrandRed,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.White
                    )
                )
                Text(
                    text = "SELECIONAR TUDO",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onMainCheckboxClick() }
                )
                
                Text(
                    text = "• ${visiblePairs.size} itens nesta página",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            
            Text(
                text = "$mgebTotalCount resultados",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // --- GLOBAL SELECT ALL BANNER ---
        if (mainCheckboxState == ToggleableState.On && mgebSelectedIds.size < mgebTotalCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Todos os ${visiblePairs.size} itens desta página estão selecionados.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                Text(
                    text = "Selecionar todos os $mgebTotalCount resultados",
                    color = BrandRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clickable { adminViewModel.selectAllMgebResults() }
                        .padding(vertical = 2.dp)
                )
            }
        }

        // --- DATA VISUALIZATION GRID ---
        if (isMgebLoading && candidates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandRed)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Carregando catálogo da API...", color = Color.Gray)
                }
            }
        } else {
            if (candidates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum conteúdo encontrado na Mgeb para este filtro.", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(candidates, key = { "${it.entity.tmdbId}_${it.entity.mediaType}" }) { item ->
                        val isSelected = mgebSelectedIds.contains(Pair(item.entity.tmdbId, item.entity.mediaType))
                        
                        MgebCandidateCard(
                            item = item,
                            isSelected = isSelected,
                            onToggle = {
                                adminViewModel.toggleMgebSelection(item.entity.tmdbId, item.entity.mediaType)
                            }
                        )
                    }
                }

                // --- PAGINATION FOOTER ROW ---
                if (mgebTotalPages > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { adminViewModel.setMgebPage(mgebPage - 1) },
                            enabled = mgebPage > 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Página Anterior",
                                tint = if (mgebPage > 1) Color.White else Color.DarkGray
                            )
                        }

                        Text(
                            text = "Página $mgebPage de $mgebTotalPages",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        IconButton(
                            onClick = { adminViewModel.setMgebPage(mgebPage + 1) },
                            enabled = mgebPage < mgebTotalPages
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Próxima Página",
                                tint = if (mgebPage < mgebTotalPages) Color.White else Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MgebCandidateCard(
    item: TmdbSearchResultItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E1012) else DarkSurface
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) BrandRed else if (item.isAlreadyInCatalog) Color.Gray.copy(alpha = 0.3f) else CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = if (item.entity.posterPath?.isNotBlank() == true) item.entity.posterPath else null,
                    contentDescription = item.entity.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black)
                )
                
                if (item.entity.posterPath.isNullOrBlank()) {
                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = Color.DarkGray)
                            Text(text = "ID #${item.entity.tmdbId}", color = Color.Gray, fontSize = 9.sp)
                        }
                    }
                }

                // Checkbox inside card, top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BrandRed,
                            uncheckedColor = Color.LightGray.copy(alpha = 0.8f),
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.scale(0.7f)
                    )
                }
                
                if (item.isAlreadyInCatalog) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("JÁ EXISTE", color = Color(0xFF4CAF50), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.entity.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.entity.mediaType.uppercase(),
                    color = if (item.entity.mediaType == "movie") BrandRed else Color(0xFF1976D2),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
                if (item.entity.releaseYear.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "• ${item.entity.releaseYear}", color = Color.Gray, fontSize = 9.sp)
                }
            }
        }
    }
}

// ============================================================================
// 4. IMPORTAÇÃO AUTOMÁTICA TMDB
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
        // Card Principal de Status e Configuração Automática
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (autoSyncConfig.enabled) "🟢 AUTOMAÇÃO TMDB: ATIVADA" else "⚪ AUTOMAÇÃO TMDB: DESATIVADA",
                                color = if (autoSyncConfig.enabled) Color.Green else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "Importação e verificação em segundo plano (Sincronização persistente)",
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

                Divider(color = CardBorder)

                // Frequência de Execução
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Frequência da Automação:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Desativado", 1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h").forEach { (hours, label) ->
                            FilterBadge(
                                label = label,
                                selected = autoSyncConfig.frequencyHours == hours,
                                onClick = { adminViewModel.updateTmdbAutoSyncFrequency(hours) }
                            )
                        }
                    }
                }

                // Limite por Execução
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Limite de Conteúdos por Sessão:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 20, 50).forEach { limit ->
                            FilterBadge(
                                label = "$limit itens",
                                selected = autoSyncConfig.maxItemsPerSync == limit,
                                onClick = { adminViewModel.updateTmdbAutoSyncMaxItems(limit) }
                            )
                        }
                    }
                }

                // Critério de Seleção
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Critério de Seleção no TMDB:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "trending" to "Tendências",
                            "releases" to "Lançamentos",
                            "popular" to "Populares",
                            "today" to "Hoje",
                            "this_week" to "Esta Semana"
                        ).forEach { (crit, label) ->
                            FilterBadge(
                                label = label,
                                selected = autoSyncConfig.criterion == crit,
                                onClick = { adminViewModel.updateTmdbAutoSyncCriterion(crit) }
                            )
                        }
                    }
                }

                // Tipo de Conteúdo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tipo:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterBadge(
                            label = "Filmes e Séries",
                            selected = autoSyncConfig.contentType == "both" || autoSyncConfig.contentType == "all",
                            onClick = { adminViewModel.updateTmdbAutoSyncContentType("both") }
                        )
                        FilterBadge(
                            label = "Filmes",
                            selected = autoSyncConfig.contentType == "movie",
                            onClick = { adminViewModel.updateTmdbAutoSyncContentType("movie") }
                        )
                        FilterBadge(
                            label = "Séries",
                            selected = autoSyncConfig.contentType == "tv",
                            onClick = { adminViewModel.updateTmdbAutoSyncContentType("tv") }
                        )
                    }
                }

                // Disparo Manual e Cancelamento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (autoSyncProgress.isRunning) {
                        OutlinedButton(
                            onClick = { adminViewModel.cancelTmdbAutoSync() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = BorderStroke(1.dp, Color.Red),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CANCELAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = { adminViewModel.startTmdbAutoSyncNow() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        enabled = !autoSyncProgress.isRunning,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (autoSyncProgress.isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sincronizando...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXECUTAR IMPORTAÇÃO AUTOMÁTICA AGORA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Live Dashboard / Progress Card during sync
        if (autoSyncProgress.isRunning || autoSyncProgress.totalFound > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (autoSyncProgress.isRunning) BrandRed else CardBorder),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (autoSyncProgress.isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandRed, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PROCESSANDO...", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ÚLTIMA EXECUÇÃO FINALIZADA", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (autoSyncProgress.totalFound > 0) {
                            val pct = ((autoSyncProgress.processedCount.toFloat() / autoSyncProgress.totalFound.toFloat()) * 100).toInt().coerceIn(0, 100)
                            Text("$pct%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    if (autoSyncProgress.totalFound > 0) {
                        val progressFraction = (autoSyncProgress.processedCount.toFloat() / autoSyncProgress.totalFound.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = progressFraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BrandRed,
                            trackColor = Color.DarkGray
                        )
                    }

                    // Status Message
                    Text(
                        text = autoSyncProgress.stepMessage,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Metrics Dashboard Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricBadge("Encontrados", autoSyncProgress.totalFound.toString(), Color.White)
                        MetricBadge("Importados", autoSyncProgress.importedCount.toString(), Color.Green)
                        MetricBadge("Já Existe", autoSyncProgress.existingCount.toString(), Color.Cyan)
                        MetricBadge("Ignorados", autoSyncProgress.ignoredCount.toString(), Color.Gray)
                        MetricBadge("Erros", autoSyncProgress.errorCount.toString(), if (autoSyncProgress.errorCount > 0) Color.Red else Color.Gray)
                    }
                }
            }
        }

        // Auto Sync History Logs Table
        Text(
            text = "HISTÓRICO DE EXECUÇÕES AUTOMÁTICAS TMDB:",
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

@Composable
fun MetricBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.Gray, fontSize = 10.sp)
    }
}

// ============================================================================
// 4. RECENTES PARA IMPORTAR
// ============================================================================

@Composable
fun RecentToImportSection(
    adminViewModel: AdminViewModel,
    typeFilter: String,
    onSwitchToMass: () -> Unit
) {
    val recentCandidates by adminViewModel.recentCandidates.collectAsState()
    val isLoading by adminViewModel.isLoadingRecentCandidates.collectAsState()

    val filteredCandidates = recentCandidates.filter {
        if (typeFilter == "ALL") true
        else it.mediaType == typeFilter
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🔥 TMDB RECENTES & EM ALTA (FORA DO CATÁLOGO)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Conteúdos novos que ainda não estão no banco de dados do RONYCINE",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { adminViewModel.loadRecentCandidatesFromTmdb() },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Atualizar Lista", fontSize = 12.sp)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandRed)
            }
        } else if (filteredCandidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum lançamento pendente no TMDB no momento.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredCandidates) { candidate ->
                    RecentCandidateCompactCard(
                        candidate = candidate,
                        onImportDirect = {
                            adminViewModel.selectTmdbIdForPreview(candidate.tmdbId.toString(), candidate.mediaType)
                        },
                        onSelectForMass = {
                            adminViewModel.addMassCandidate(candidate.copy(selected = true))
                            onSwitchToMass()
                        }
                    )
                }
            }
        }
    }
}

// ============================================================================
// 5. HISTÓRICO DE IMPORTAÇÕES
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
            text = "📋 HISTÓRICO GERAL DE IMPORTAÇÕES REGISTRADAS",
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
                Text("Nenhum registro de importação recente.", color = Color.Gray, fontSize = 13.sp)
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
// SUB-COMPOSABLES & UTILS
// ============================================================================

@Composable
fun FilterBadge(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
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
                softWrap = false,
                overflow = TextOverflow.Clip
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
fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SearchResultCompactCard(
    result: TmdbSearchResultItem,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = result.entity.posterPath,
                contentDescription = result.entity.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(34.dp)
                    .height(51.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(result.entity.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BadgeBox(text = result.entity.mediaType.uppercase(), color = if (result.entity.mediaType == "movie") BrandRed else Color(0xFF1976D2))
                    Text("★ ${String.format(Locale.US, "%.1f", result.entity.rating)}", color = Color(0xFFFFA000), fontSize = 11.sp)
                    Text(result.entity.releaseYear, color = Color.Gray, fontSize = 11.sp)
                }
            }

            if (result.isAlreadyInCatalog) {
                BadgeBox(text = "NO CATÁLOGO", color = Color(0xFFE65100))
            } else {
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Selecionar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
            modifier = Modifier.padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f/3f)
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
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(32.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = candidate.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (candidate.mediaType == "movie") "FILME" else "SÉRIE",
                    color = if (candidate.mediaType == "movie") BrandRed else Color(0xFF1976D2),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                if (candidate.year.isNotBlank()) Text(candidate.year, color = Color.Gray, fontSize = 8.sp)
                if (candidate.rating > 0) {
                    Text("★${String.format(Locale.US, "%.1f", candidate.rating)}", color = Color(0xFFFFA000), fontSize = 8.sp)
                }
            }
            if (candidate.isAlreadyInCatalog) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("JÁ EXISTE", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RecentCandidateCompactCard(
    candidate: AdminViewModel.MassCandidateItem,
    onImportDirect: () -> Unit,
    onSelectForMass: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = candidate.posterPath,
                contentDescription = candidate.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(38.dp)
                    .height(57.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BadgeBox(text = candidate.mediaType.uppercase(), color = if (candidate.mediaType == "movie") BrandRed else Color(0xFF1976D2))
                    Text("★ ${String.format(Locale.US, "%.1f", candidate.rating)}", color = Color(0xFFFFA000), fontSize = 11.sp)
                    if (candidate.year.isNotBlank()) Text(candidate.year, color = Color.Gray, fontSize = 11.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onImportDirect,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Importar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSelectForMass,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("+ Lote", color = Color.White, fontSize = 10.sp)
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
                    text = "Encontrados: ${log.totalFound} | Importados: ${log.importedCount} | Existentes: ${log.existingCount} | Erros: ${log.errorCount}",
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
