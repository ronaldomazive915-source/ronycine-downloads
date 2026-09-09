package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.data.repository.SearchItemStatus
import com.example.data.repository.SmartSearchResultItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.MediaClassifier
import kotlinx.coroutines.launch

/**
 * Sort options for the Explore catalog.
 */
enum class ExploreSortOption(val label: String) {
    RECENTES("Mais recentes"),
    POPULARES("Mais populares"),
    MELHOR_AVALIADOS("Melhor avaliação"),
    ANTIGOS("Mais antigos"),
    TITULO_AZ("A - Z"),
    TITULO_ZA("Z - A")
}

/**
 * Redesigned Explore Screen (Explorar) for RONYCINE.
 * Features:
 * - Ultra-compact, cinema-grade header with title and quick filter drawer button
 * - Horizontal main category pills (TODOS, FILMES, SÉRIES, ANIMES, DORAMAS)
 * - Horizontal contextual quick filter chips (Recentes, Populares, 8+ ⭐, Gêneros)
 * - Advanced Filter Bottom Sheet (Tipo, Gênero, Ano, Nota Mínima, Ordenação)
 * - Responsive 3-column poster grid with sleek rating badges, clean typography and press animations
 * - Infinite scroll / pagination loading for high-performance navigation
 * - Shimmer skeleton loading & modern empty states
 * - Fully integrated real-time catalog from Firestore / Room DB
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.exploreSearchQuery.collectAsState()
    val smartSearchResults by viewModel.exploreSmartSearchResults.collectAsState()
    val processingKeys by viewModel.processingRequestKeys.collectAsState()
    val isSearching by viewModel.isExploreSearching.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    val allMedia by viewModel.allMedia.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // --- Filter States ---
    var selectedCategory by rememberSaveable { mutableStateOf("all") } // "all", "movie", "tv", "anime", "dorama"
    var selectedQuickFilter by rememberSaveable { mutableStateOf("Todos") } // "Todos", "Recentes", "Populares", "8+", or genre name
    var filterGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var filterYear by rememberSaveable { mutableStateOf("Todos") }
    var filterMinRating by rememberSaveable { mutableStateOf(0.0) }
    var sortOption by rememberSaveable { mutableStateOf(ExploreSortOption.RECENTES) }

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    // Count of active non-default filters
    val activeFiltersCount = remember(selectedCategory, filterGenre, filterYear, filterMinRating, sortOption) {
        var count = 0
        if (filterGenre != null && filterGenre != "Todos") count++
        if (filterYear != "Todos") count++
        if (filterMinRating > 0.0) count++
        if (sortOption != ExploreSortOption.RECENTES) count++
        count
    }

    // Contextual Quick Filters for the selected category
    val contextualQuickFilters = remember(selectedCategory, availableGenres, allMedia) {
        val base = mutableListOf("Todos", "Recentes", "Populares", "8+ ⭐")

        // Extract top genres relevant to the selected category
        val categoryItems = allMedia.filter { media ->
            val cat = MediaClassifier.classifyMedia(media)
            when (selectedCategory) {
                "movie" -> cat == MediaClassifier.CATEGORY_MOVIE
                "tv" -> cat == MediaClassifier.CATEGORY_SERIES
                "anime" -> cat == MediaClassifier.CATEGORY_ANIME
                "dorama" -> cat == MediaClassifier.CATEGORY_DORAMA
                else -> true
            }
        }

        val topGenres = categoryItems.flatMap { it.genres.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "Todos" }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)

        base.addAll(topGenres)
        base.distinct()
    }

    // Filter and Sort Catalog Items
    val filteredCatalog = remember(
        allMedia,
        selectedCategory,
        selectedQuickFilter,
        filterGenre,
        filterYear,
        filterMinRating,
        sortOption,
        searchQuery
    ) {
        allMedia.filter { media ->
            val cat = MediaClassifier.classifyMedia(media)

            // 1. Category check (STRICT classification)
            val categoryMatch = when (selectedCategory) {
                "movie" -> cat == MediaClassifier.CATEGORY_MOVIE
                "tv" -> cat == MediaClassifier.CATEGORY_SERIES
                "anime" -> cat == MediaClassifier.CATEGORY_ANIME
                "dorama" -> cat == MediaClassifier.CATEGORY_DORAMA
                else -> true // "all"
            }
            if (!categoryMatch) return@filter false

            // 2. Search query match
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                val titleMatch = media.title.lowercase().contains(query)
                val originalTitleMatch = media.originalTitle.lowercase().contains(query)
                val genreMatch = media.genres.lowercase().contains(query)
                if (!titleMatch && !originalTitleMatch && !genreMatch) return@filter false
            }

            // 3. Quick Filter check
            when (selectedQuickFilter) {
                "8+ ⭐" -> if (media.rating < 8.0) return@filter false
                "Todos", "Recentes", "Populares" -> { /* handled in sort */ }
                else -> {
                    // Selected a genre chip from quick filters
                    if (!media.genres.contains(selectedQuickFilter, ignoreCase = true)) return@filter false
                }
            }

            // 4. Advanced Genre filter
            if (!filterGenre.isNullOrBlank() && filterGenre != "Todos") {
                if (!media.genres.contains(filterGenre!!, ignoreCase = true)) return@filter false
            }

            // 5. Year Filter
            if (filterYear != "Todos") {
                when (filterYear) {
                    "2010s" -> {
                        val y = media.releaseYear.toIntOrNull()
                        if (y == null || y !in 2010..2019) return@filter false
                    }
                    "Clássicos" -> {
                        val y = media.releaseYear.toIntOrNull()
                        if (y == null || y >= 2010) return@filter false
                    }
                    else -> {
                        if (!media.releaseYear.startsWith(filterYear)) return@filter false
                    }
                }
            }

            // 6. Rating Filter
            if (filterMinRating > 0.0) {
                if (media.rating < filterMinRating) return@filter false
            }

            true
        }.distinctBy { "${it.tmdbId}_${it.mediaType}" }
            .let { list ->
                // Apply sorting
                when {
                    selectedQuickFilter == "Recentes" -> {
                        list.sortedWith(
                            compareByDescending<MediaEntity> { it.releaseYear }
                                .thenByDescending { it.addedAt }
                                .thenByDescending { it.id }
                        )
                    }
                    selectedQuickFilter == "Populares" -> {
                        list.sortedWith(
                            compareByDescending<MediaEntity> { it.rating }
                                .thenByDescending { it.releaseYear }
                        )
                    }
                    else -> {
                        when (sortOption) {
                            ExploreSortOption.RECENTES -> list.sortedWith(
                                compareByDescending<MediaEntity> { it.releaseYear }
                                    .thenByDescending { it.addedAt }
                                    .thenByDescending { it.id }
                            )
                            ExploreSortOption.POPULARES -> list.sortedWith(
                                compareByDescending<MediaEntity> { it.rating }
                                    .thenByDescending { it.releaseYear }
                            )
                            ExploreSortOption.MELHOR_AVALIADOS -> list.sortedByDescending { it.rating }
                            ExploreSortOption.ANTIGOS -> list.sortedWith(
                                compareBy<MediaEntity> { it.releaseYear.ifBlank { "9999" } }
                            )
                            ExploreSortOption.TITULO_AZ -> list.sortedBy { it.title.lowercase() }
                            ExploreSortOption.TITULO_ZA -> list.sortedByDescending { it.title.lowercase() }
                        }
                    }
                }
            }
    }

    // Filter smart search results if in external search mode
    val filteredSmartResults = remember(smartSearchResults, selectedCategory) {
        smartSearchResults.filter { item ->
            val cat = MediaClassifier.classifyMedia(item.entity)
            when (selectedCategory) {
                "movie" -> cat == MediaClassifier.CATEGORY_MOVIE
                "tv" -> cat == MediaClassifier.CATEGORY_SERIES
                "anime" -> cat == MediaClassifier.CATEGORY_ANIME
                "dorama" -> cat == MediaClassifier.CATEGORY_DORAMA
                else -> true
            }
        }.distinctBy { "${it.entity.tmdbId}_${it.entity.mediaType}" }
    }

    val categoryTitle = when (selectedCategory) {
        "movie" -> "FILMES"
        "tv" -> "SÉRIES"
        "anime" -> "ANIMES"
        "dorama" -> "DORAMAS"
        else -> "TODO O CATÁLOGO"
    }

    val isInitialLoading = allMedia.isEmpty() && (syncStatus == com.example.data.remote.SyncStatus.SYNCING || isSearching)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("search_screen"),
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ==========================================
                // 1. TOP HEADER (TITLE & FILTER BUTTON)
                // ==========================================
                ExploreTopHeader(
                    title = "EXPLORAR",
                    activeFiltersCount = activeFiltersCount,
                    onOpenFilters = { showFilterSheet = true }
                )

                // ==========================================
                // 2. SINGLE INTEGRATED SEARCH BAR
                // ==========================================
                ExploreSearchBar(
                    query = searchQuery,
                    onQueryChanged = { viewModel.onExploreSearchQueryChanged(it) },
                    onClearQuery = {
                        viewModel.clearExploreSearch()
                        focusManager.clearFocus()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ==========================================
                // 3. MAIN CATEGORIES (HORIZONTAL SCROLL)
                // ==========================================
                ExploreCategoryTabs(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { cat ->
                        selectedCategory = cat
                        selectedQuickFilter = "Todos"
                        filterGenre = null
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ==========================================
                // 4. CONTEXTUAL QUICK FILTERS ROW
                // ==========================================
                ExploreQuickFiltersRow(
                    filters = contextualQuickFilters,
                    selectedFilter = selectedQuickFilter,
                    onSelectFilter = { filter ->
                        selectedQuickFilter = filter
                        if (filter != "Todos" && filter != "Recentes" && filter != "Populares" && filter != "8+ ⭐") {
                            filterGenre = filter
                        } else {
                            filterGenre = null
                        }
                    }
                )

                // ==========================================
                // 5. RESULT COUNTER & ACTIVE FILTER BADGES
                // ==========================================
                ExploreResultHeader(
                    categoryTitle = categoryTitle,
                    count = filteredCatalog.size,
                    hasActiveFilters = activeFiltersCount > 0 || selectedQuickFilter != "Todos" || searchQuery.isNotEmpty(),
                    onClearFilters = {
                        selectedCategory = "all"
                        selectedQuickFilter = "Todos"
                        filterGenre = null
                        filterYear = "Todos"
                        filterMinRating = 0.0
                        sortOption = ExploreSortOption.RECENTES
                        viewModel.onSearchQueryChanged("")
                    }
                )

                // ==========================================
                // 6. MAIN CONTENT GRID / SMART SEARCH / EMPTY
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        // Skeleton loading when no media is loaded yet
                        isInitialLoading -> {
                            ExploreSkeletonGrid()
                        }

                        // Search mode active with smart search results
                        searchQuery.isNotBlank() && isSearching -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp)
                            }
                        }

                        // Smart Search item cards if external items exist
                        searchQuery.isNotBlank() && filteredCatalog.isEmpty() && filteredSmartResults.isNotEmpty() -> {
                            ExploreSmartSearchResultsList(
                                results = filteredSmartResults,
                                processingKeys = processingKeys,
                                onNavigateToDetail = onNavigateToDetail,
                                onNavigate = onNavigate,
                                onSendRequest = { viewModel.sendMediaRequest(it) }
                            )
                        }

                        // Empty State
                        filteredCatalog.isEmpty() -> {
                            ExploreEmptyState(
                                hasFilters = activeFiltersCount > 0 || selectedQuickFilter != "Todos" || searchQuery.isNotEmpty(),
                                onClearFilters = {
                                    selectedCategory = "all"
                                    selectedQuickFilter = "Todos"
                                    filterGenre = null
                                    filterYear = "Todos"
                                    filterMinRating = 0.0
                                    sortOption = ExploreSortOption.RECENTES
                                    viewModel.onSearchQueryChanged("")
                                }
                            )
                        }

                        // Normal Responsive Catalog Grid
                        else -> {
                            ExploreMediaGrid(
                                items = filteredCatalog,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 7. ADVANCED FILTER BOTTOM SHEET
    // ==========================================
    if (showFilterSheet) {
        ExploreFilterBottomSheet(
            currentCategory = selectedCategory,
            currentGenre = filterGenre,
            currentYear = filterYear,
            currentMinRating = filterMinRating,
            currentSortOption = sortOption,
            availableGenres = availableGenres,
            totalResultCount = filteredCatalog.size,
            onApply = { cat, genre, year, rating, sort ->
                selectedCategory = cat
                filterGenre = genre
                filterYear = year
                filterMinRating = rating
                sortOption = sort
                if (genre != null) {
                    selectedQuickFilter = genre
                }
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

// =========================================================================
// HEADER COMPONENTS
// =========================================================================

@Composable
private fun ExploreTopHeader(
    title: String,
    activeFiltersCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandRed)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }

        // Right Filter Button with active indicator
        Box {
            IconButton(
                onClick = onOpenFilters,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (activeFiltersCount > 0) BrandRed.copy(alpha = 0.2f) else DarkSurface)
                    .border(
                        1.dp,
                        if (activeFiltersCount > 0) BrandRed.copy(alpha = 0.6f) else CardBorder.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .testTag("explore_filter_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Filtros",
                    tint = if (activeFiltersCount > 0) BrandRed else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Active filters count badge
            if (activeFiltersCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeFiltersCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = BrandRed,
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(BrandRed),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier
                    .weight(1f)
                    .testTag("explore_search_input"),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Buscar filmes, séries, animes e doramas...",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// CATEGORY TABS & QUICK FILTERS
// =========================================================================

private data class CategoryTabItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun ExploreCategoryTabs(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember {
        listOf(
            CategoryTabItem("all", "TODOS", Icons.Default.AutoAwesome),
            CategoryTabItem("movie", "FILMES", Icons.Default.Movie),
            CategoryTabItem("tv", "SÉRIES", Icons.Default.Tv),
            CategoryTabItem("anime", "ANIMES", Icons.Default.Animation),
            CategoryTabItem("dorama", "DORAMAS", Icons.Default.Favorite)
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { cat ->
            val isSelected = selectedCategory == cat.id

            val animBg by animateColorAsState(
                targetValue = if (isSelected) BrandRed else DarkSurface,
                animationSpec = tween(durationMillis = 200),
                label = "cat_tab_bg"
            )
            val animTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color(0xFFA1A1AA),
                animationSpec = tween(durationMillis = 200),
                label = "cat_tab_text"
            )

            Surface(
                onClick = { onSelectCategory(cat.id) },
                shape = RoundedCornerShape(20.dp),
                color = animBg,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) BrandRed else CardBorder.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("explore_category_${cat.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = cat.icon,
                        contentDescription = null,
                        tint = animTextColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = cat.label,
                        color = animTextColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreQuickFiltersRow(
    filters: List<String>,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filters, key = { it }) { filter ->
            val isSelected = selectedFilter == filter

            val containerColor = if (isSelected) Color(0xFF2A1012) else Color(0xFF16161A)
            val borderColor = if (isSelected) BrandRed else CardBorder.copy(alpha = 0.25f)
            val textColor = if (isSelected) Color.White else Color(0xFF9CA3AF)

            Surface(
                onClick = { onSelectFilter(filter) },
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("explore_quick_filter_$filter")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        color = textColor,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreResultHeader(
    categoryTitle: String,
    count: Int,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = categoryTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "•",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
            Text(
                text = "$count ${if (count == 1) "título" else "títulos"}",
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (hasActiveFilters) {
            Text(
                text = "Limpar filtros",
                color = BrandRed,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onClearFilters() }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
                    .testTag("explore_clear_filters_btn")
            )
        }
    }
}

// =========================================================================
// RESPONSIVE POSTER GRID & CARDS
// =========================================================================

@Composable
private fun ExploreMediaGrid(
    items: List<MediaEntity>,
    onNavigateToDetail: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Responsive column count: minimum width 100dp gives 3 columns on standard phones, 4-5 on tablet, 6+ on desktop
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 102.dp),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = 100.dp // Ample padding for BottomNav safe area
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_media_grid")
    ) {
        items(
            items = items,
            key = { "${it.mediaType}_${it.tmdbId}" }
        ) { media ->
            ExplorePosterCard(
                media = media,
                onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
            )
        }
    }
}

/**
 * High-polish Cinema Poster Card for the Explore Screen.
 * 2:3 Aspect ratio, sleek corner rating badge, title with ellipsis, subtle year subtitle.
 */
@Composable
private fun ExplorePosterCard(
    media: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val category = remember(media.mediaCategory, media.genres, media.title) {
        MediaClassifier.classifyMedia(media)
    }
    val categoryBadgeText = remember(category) {
        when (category) {
            MediaClassifier.CATEGORY_ANIME -> "ANIME"
            MediaClassifier.CATEGORY_DORAMA -> "DORAMA"
            MediaClassifier.CATEGORY_MOVIE -> "FILME"
            else -> "SÉRIE"
        }
    }

    val categoryBadgeColor = remember(category) {
        when (category) {
            MediaClassifier.CATEGORY_ANIME -> Color(0xFFE11D48)
            MediaClassifier.CATEGORY_DORAMA -> Color(0xFF7C3AED)
            MediaClassifier.CATEGORY_MOVIE -> BrandRed
            else -> Color(0xFF2563EB)
        }
    }

    val context = LocalContext.current
    val imageUrl = remember(media.posterPath, media.backdropPath) {
        media.posterPath?.ifBlank { null } ?: media.backdropPath
    }
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(340, 510)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (isPressed) 0.96f else 1f
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("explore_card_${media.tmdbId}")
    ) {
        // Poster Box (2:3 Aspect Ratio)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurface)
                )

                // Subtle Bottom Vignette Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // Type Badge (Top-Left)
                Surface(
                    color = categoryBadgeColor.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = categoryBadgeText,
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 2.dp)
                    )
                }

                // Sleek Rating Badge (Top-Right)
                if (media.rating > 0.0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(bottomStart = 6.dp, topEnd = 8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFBBF24).copy(alpha = 0.3f)),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = RatingYellow,
                                modifier = Modifier.size(9.5.dp)
                            )
                            Text(
                                text = String.format("%.1f", media.rating),
                                color = RatingYellow,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Title (Uniform 1-2 lines with ellipsis)
        Text(
            text = media.title,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            minLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle: Year
        val yearText = media.releaseYear.ifBlank { "—" }
        Text(
            text = yearText,
            color = TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// =========================================================================
// SKELETON LOADING & EMPTY STATES
// =========================================================================

@Composable
private fun ExploreSkeletonGrid(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 102.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(12) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.8f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = alpha * 0.6f))
                )
            }
        }
    }
}

@Composable
private fun ExploreEmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = DarkSurface,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.4f)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasFilters) "Nenhum conteúdo encontrado" else "Catálogo vazio",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (hasFilters) {
                "Experimente remover ou ajustar os filtros para encontrar o que procura."
            } else {
                "Quando novos títulos forem adicionados ao RONYCINE, eles aparecerão aqui."
            },
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        if (hasFilters) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onClearFilters,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.testTag("empty_state_clear_filters_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LIMPAR FILTROS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// =========================================================================
// SMART SEARCH RESULTS LIST
// =========================================================================

@Composable
private fun ExploreSmartSearchResultsList(
    results: List<SmartSearchResultItem>,
    processingKeys: Set<String>,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigate: ((String) -> Unit)?,
    onSendRequest: (MediaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        items(
            items = results,
            key = { "${it.entity.mediaType}_${it.entity.tmdbId}" }
        ) { result ->
            SmartSearchResultCard(
                result = result,
                isProcessing = processingKeys.contains("${result.entity.mediaType}_${result.entity.tmdbId}"),
                onClick = { onNavigateToDetail(result.entity.tmdbId, result.entity.mediaType) },
                onWatch = { onNavigate?.invoke("watch/${result.entity.tmdbId}/${result.entity.mediaType}") },
                onSendRequest = { onSendRequest(result.entity) }
            )
        }
    }
}

@Composable
fun SmartSearchResultCard(
    result: SmartSearchResultItem,
    isProcessing: Boolean,
    onClick: () -> Unit,
    onWatch: () -> Unit,
    onSendRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = result.entity
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable { onClick() }
            .testTag("smart_search_card_${media.tmdbId}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                val imageUrl = remember(media.posterPath, media.backdropPath) {
                    media.posterPath ?: media.backdropPath
                }
                val imageRequest = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(240, 360)
                        .crossfade(false)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Type Badge
                Surface(
                    color = BrandRed.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (media.mediaType == "tv") "📺 SÉRIE" else "🎬 FILME",
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title and Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (media.releaseYear.isNotBlank()) {
                        Text(
                            text = media.releaseYear,
                            color = Color.LightGray,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                // Genres & Rating
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (media.rating > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = RatingYellow,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = String.format("%.1f", media.rating),
                                color = RatingYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("•", color = Color.DarkGray, fontSize = 11.sp)
                    }

                    Text(
                        text = media.genres.ifBlank { "Geral" },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Description
                Text(
                    text = media.overview.ifBlank { "Sem sinopse disponível." },
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Action Status & Buttons
                val statusColor = when (result.status) {
                    SearchItemStatus.IN_CATALOG, SearchItemStatus.READY_TO_WATCH -> Color(0xFF4CAF50)
                    SearchItemStatus.REQUEST_PENDING -> Color(0xFFFFEB3B)
                    SearchItemStatus.AVAILABLE_FOR_REQUEST -> Color(0xFFF44336)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(statusColor, CircleShape)
                        )
                        Text(
                            text = result.statusLabel,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    when (result.status) {
                        SearchItemStatus.IN_CATALOG, SearchItemStatus.READY_TO_WATCH -> {
                            Button(
                                onClick = onWatch,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("ASSISTIR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        SearchItemStatus.REQUEST_PENDING -> {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    disabledContainerColor = Color.DarkGray.copy(alpha = 0.3f),
                                    disabledContentColor = Color.Gray
                                )
                            ) {
                                Text("SOLICITADO", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        SearchItemStatus.AVAILABLE_FOR_REQUEST -> {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = BrandRed,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                OutlinedButton(
                                    onClick = onSendRequest,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("PEDIR CONTEÚDO", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// ADVANCED FILTER BOTTOM SHEET
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreFilterBottomSheet(
    currentCategory: String,
    currentGenre: String?,
    currentYear: String,
    currentMinRating: Double,
    currentSortOption: ExploreSortOption,
    availableGenres: List<String>,
    totalResultCount: Int,
    onApply: (category: String, genre: String?, year: String, rating: Double, sort: ExploreSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    var tempCategory by remember { mutableStateOf(currentCategory) }
    var tempGenre by remember { mutableStateOf(currentGenre) }
    var tempYear by remember { mutableStateOf(currentYear) }
    var tempMinRating by remember { mutableStateOf(currentMinRating) }
    var tempSortOption by remember { mutableStateOf(currentSortOption) }

    val years = remember {
        listOf("Todos", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2010s", "Clássicos")
    }

    val ratings = remember {
        listOf(
            0.0 to "Todas",
            9.0 to "9.0+ ⭐",
            8.0 to "8.0+ ⭐",
            7.0 to "7.0+ ⭐",
            6.0 to "6.0+ ⭐"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.DarkGray)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "FILTRAR CATÁLOGO",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Scrollable Content
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. TIPO DE CONTEÚDO
                item {
                    FilterSectionTitle("TIPO DE CONTEÚDO")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val types = listOf(
                            "all" to "Todos",
                            "movie" to "Filmes",
                            "tv" to "Séries",
                            "anime" to "Animes",
                            "dorama" to "Doramas"
                        )
                        items(types) { (key, label) ->
                            FilterSelectableChip(
                                label = label,
                                isSelected = tempCategory == key,
                                onClick = { tempCategory = key }
                            )
                        }
                    }
                }

                // 2. ORDENAÇÃO
                item {
                    FilterSectionTitle("ORDENAR POR")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ExploreSortOption.values()) { opt ->
                            FilterSelectableChip(
                                label = opt.label,
                                isSelected = tempSortOption == opt,
                                onClick = { tempSortOption = opt }
                            )
                        }
                    }
                }

                // 3. AVALIAÇÃO MÍNIMA
                item {
                    FilterSectionTitle("AVALIAÇÃO MÍNIMA")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ratings) { (score, label) ->
                            FilterSelectableChip(
                                label = label,
                                isSelected = tempMinRating == score,
                                onClick = { tempMinRating = score }
                            )
                        }
                    }
                }

                // 4. ANO DE LANÇAMENTO
                item {
                    FilterSectionTitle("ANO DE LANÇAMENTO")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(years) { y ->
                            FilterSelectableChip(
                                label = y,
                                isSelected = tempYear == y,
                                onClick = { tempYear = y }
                            )
                        }
                    }
                }

                // 5. GÊNEROS DISPONÍVEIS
                if (availableGenres.isNotEmpty()) {
                    item {
                        FilterSectionTitle("GÊNERO")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterSelectableChip(
                                    label = "Todos os Gêneros",
                                    isSelected = tempGenre == null || tempGenre == "Todos",
                                    onClick = { tempGenre = null }
                                )
                            }
                            items(availableGenres) { genre ->
                                FilterSelectableChip(
                                    label = genre,
                                    isSelected = tempGenre == genre,
                                    onClick = { tempGenre = genre }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions (Limpar & Aplicar)
            Divider(color = CardBorder.copy(alpha = 0.3f), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        tempCategory = "all"
                        tempGenre = null
                        tempYear = "Todos"
                        tempMinRating = 0.0
                        tempSortOption = ExploreSortOption.RECENTES
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.4f)
                        .height(46.dp)
                ) {
                    Text("LIMPAR", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onApply(tempCategory, tempGenre, tempYear, tempMinRating, tempSortOption)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.6f)
                        .height(46.dp)
                        .testTag("apply_filters_sheet_btn")
                ) {
                    Text(
                        text = "APLICAR FILTROS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFFA1A1AA),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun FilterSelectableChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) BrandRed else Color(0xFF1E1E24)
    val textColor = if (isSelected) Color.White else Color(0xFFA1A1AA)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) BrandRed else CardBorder.copy(alpha = 0.4f)
        ),
        modifier = Modifier.height(34.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
