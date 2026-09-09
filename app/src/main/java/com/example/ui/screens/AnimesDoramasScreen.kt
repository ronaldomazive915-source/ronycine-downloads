package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MediaEntity
import com.example.ui.components.MediaCard
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AnimeDoramaTab
import com.example.ui.viewmodel.AnimesDoramasViewModel
import com.example.ui.viewmodel.MediaSortFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimesDoramasScreen(
    viewModel: AnimesDoramasViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val sortFilter by viewModel.sortFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val rawAnimes by viewModel.localAnimes.collectAsState()
    val rawDoramas by viewModel.localDoramas.collectAsState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Individual sort filter state for sections in combined view
    var animeSortFilter by remember { mutableStateOf(MediaSortFilter.POPULARES) }
    var doramaSortFilter by remember { mutableStateOf(MediaSortFilter.POPULARES) }

    val sortedAnimes = remember(rawAnimes, animeSortFilter, sortFilter, selectedTab) {
        val filter = if (selectedTab == AnimeDoramaTab.ALL) animeSortFilter else sortFilter
        sortMediaList(rawAnimes, filter)
    }

    val sortedDoramas = remember(rawDoramas, doramaSortFilter, sortFilter, selectedTab) {
        val filter = if (selectedTab == AnimeDoramaTab.ALL) doramaSortFilter else sortFilter
        sortMediaList(rawDoramas, filter)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("animes_doramas_screen"),
        topBar = {
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Header Bar with Back button, Title and Sync Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DarkBackground)
                                    .testTag("animes_doramas_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "ANIMES & DORAMAS",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "🎌 Animes e 🇰🇷 Doramas 100% Separados",
                                    color = Color.Gray,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshCurrentTab() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkBackground)
                                .testTag("animes_doramas_refresh_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = BrandRed,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Atualizar TMDB / Firestore",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // In-screen Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = {
                            Text(
                                text = "Buscar animes ou doramas por título...",
                                color = Color.Gray,
                                fontSize = 12.5.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = DarkBackground,
                            unfocusedContainerColor = DarkBackground,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("animes_doramas_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Selector Tabs: [ 🎌 & 🇰🇷 TODOS ] [ 🎌 ANIMES (X) ] [ 🇰🇷 DORAMAS (Y) ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ALL TAB
                        val isAllSelected = selectedTab == AnimeDoramaTab.ALL
                        Surface(
                            onClick = { viewModel.selectTab(AnimeDoramaTab.ALL) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAllSelected) BrandRed else DarkBackground,
                            border = BorderStroke(1.dp, if (isAllSelected) BrandRed else CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("tab_all")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "TODOS",
                                    color = Color.White,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // ANIMES TAB
                        val isAnimeSelected = selectedTab == AnimeDoramaTab.ANIMES
                        Surface(
                            onClick = { viewModel.selectTab(AnimeDoramaTab.ANIMES) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAnimeSelected) BrandRed else DarkBackground,
                            border = BorderStroke(1.dp, if (isAnimeSelected) BrandRed else CardBorder),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(38.dp)
                                .testTag("tab_animes")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎌 ANIMES",
                                    color = Color.White,
                                    fontWeight = if (isAnimeSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                if (rawAnimes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(${rawAnimes.size})",
                                        color = if (isAnimeSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // DORAMAS TAB
                        val isDoramaSelected = selectedTab == AnimeDoramaTab.DORAMAS
                        Surface(
                            onClick = { viewModel.selectTab(AnimeDoramaTab.DORAMAS) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDoramaSelected) Color(0xFF7C3AED) else DarkBackground,
                            border = BorderStroke(1.dp, if (isDoramaSelected) Color(0xFF7C3AED) else CardBorder),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(38.dp)
                                .testTag("tab_doramas")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🇰🇷 DORAMAS",
                                    color = Color.White,
                                    fontWeight = if (isDoramaSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                if (rawDoramas.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(${rawDoramas.size})",
                                        color = if (isDoramaSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && rawAnimes.isEmpty() && rawDoramas.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = BrandRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Carregando catálogo TMDB & Firestore...",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }

                rawAnimes.isEmpty() && rawDoramas.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Nenhum anime ou dorama catalogado",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toque abaixo para sincronizar com a base oficial do TMDB e Firestore.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refreshCurrentTab() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizar Conteúdos")
                        }
                    }
                }

                // TAB 1: ALL - Both sections shown on the same page, COMPLETELY SEPARATED!
                selectedTab == AnimeDoramaTab.ALL -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("animes_doramas_combined_list"),
                        contentPadding = PaddingValues(bottom = 40.dp)
                    ) {
                        // ==================== 1. ANIMES SECTION ====================
                        item {
                            SectionHeaderBar(
                                title = "🎌 ANIMES",
                                count = sortedAnimes.size,
                                accentColor = BrandRed,
                                onViewAll = { viewModel.selectTab(AnimeDoramaTab.ANIMES) }
                            )
                        }

                        // Filter chips for Animes
                        item {
                            SortFilterChipsRow(
                                activeFilter = animeSortFilter,
                                onSelectFilter = { animeSortFilter = it },
                                accentColor = BrandRed
                            )
                        }

                        // Animes Horizontal Cards Row
                        item {
                            if (sortedAnimes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Nenhum anime encontrado para o filtro.", color = Color.Gray, fontSize = 13.sp)
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("animes_horizontal_row")
                                ) {
                                    items(sortedAnimes, key = { "anime_${it.tmdbId}" }) { media ->
                                        MediaCard(
                                            media = media,
                                            onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
                                        )
                                    }
                                }
                            }
                        }

                        // Clean Aesthetic Divider
                        item {
                            Spacer(modifier = Modifier.height(18.dp))
                            HorizontalDivider(
                                color = CardBorder.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // ==================== 2. DORAMAS SECTION ====================
                        item {
                            SectionHeaderBar(
                                title = "🇰🇷 DORAMAS",
                                count = sortedDoramas.size,
                                accentColor = Color(0xFF7C3AED),
                                onViewAll = { viewModel.selectTab(AnimeDoramaTab.DORAMAS) }
                            )
                        }

                        // Filter chips for Doramas
                        item {
                            SortFilterChipsRow(
                                activeFilter = doramaSortFilter,
                                onSelectFilter = { doramaSortFilter = it },
                                accentColor = Color(0xFF7C3AED)
                            )
                        }

                        // Doramas Horizontal Cards Row
                        item {
                            if (sortedDoramas.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Nenhum dorama encontrado para o filtro.", color = Color.Gray, fontSize = 13.sp)
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("doramas_horizontal_row")
                                ) {
                                    items(sortedDoramas, key = { "dorama_${it.tmdbId}" }) { media ->
                                        MediaCard(
                                            media = media,
                                            onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: ANIMES ONLY - Full Exclusive Grid
                selectedTab == AnimeDoramaTab.ANIMES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SortFilterChipsRow(
                            activeFilter = sortFilter,
                            onSelectFilter = { viewModel.setSortFilter(it) },
                            accentColor = BrandRed
                        )

                        if (sortedAnimes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Nenhum anime encontrado.", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 105.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .testTag("animes_exclusive_grid"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(sortedAnimes, key = { "anime_${it.tmdbId}" }) { media ->
                                    MediaCard(
                                        media = media,
                                        onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 3: DORAMAS ONLY - Full Exclusive Grid
                selectedTab == AnimeDoramaTab.DORAMAS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SortFilterChipsRow(
                            activeFilter = sortFilter,
                            onSelectFilter = { viewModel.setSortFilter(it) },
                            accentColor = Color(0xFF7C3AED)
                        )

                        if (sortedDoramas.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Nenhum dorama encontrado.", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 105.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .testTag("doramas_exclusive_grid"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(sortedDoramas, key = { "dorama_${it.tmdbId}" }) { media ->
                                    MediaCard(
                                        media = media,
                                        onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
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

/**
 * Modern header for animes/doramas section with count badge and "Ver todos" action.
 */
@Composable
private fun SectionHeaderBar(
    title: String,
    count: Int,
    accentColor: Color,
    onViewAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$count títulos",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        TextButton(
            onClick = onViewAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Ver todos ›",
                color = accentColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Row of sort filters: Mais populares | Mais bem avaliados | Novos | Em alta
 */
@Composable
private fun SortFilterChipsRow(
    activeFilter: MediaSortFilter,
    onSelectFilter: (MediaSortFilter) -> Unit,
    accentColor: Color
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(MediaSortFilter.values().toList()) { filter ->
            val isSelected = activeFilter == filter
            Surface(
                onClick = { onSelectFilter(filter) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) accentColor else DarkSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) accentColor else CardBorder.copy(alpha = 0.7f)
                ),
                modifier = Modifier.height(30.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.label,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Sorts media list according to the selected criteria.
 */
private fun sortMediaList(list: List<MediaEntity>, filter: MediaSortFilter): List<MediaEntity> {
    return when (filter) {
        MediaSortFilter.POPULARES -> list.sortedByDescending { it.rating }
        MediaSortFilter.MELHORES_AVALIADOS -> list.sortedWith(
            compareByDescending<MediaEntity> { it.rating }.thenByDescending { it.releaseYear }
        )
        MediaSortFilter.NOVOS -> list.sortedWith(
            compareByDescending<MediaEntity> { it.releaseYear }.thenByDescending { it.addedAt }
        )
        MediaSortFilter.EM_ALTA -> list.sortedWith(
            compareByDescending<MediaEntity> { it.rating }.thenByDescending { it.addedAt }
        )
    }
}
