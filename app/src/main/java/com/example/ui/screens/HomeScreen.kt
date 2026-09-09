package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.data.local.WatchHistoryEntity
import com.example.ui.components.CircularRatingBadge
import com.example.ui.components.HeroBanner
import com.example.ui.components.MediaCard
import com.example.ui.components.MediaSectionRow
import com.example.util.MediaClassifier
import com.example.ui.components.PlayfilmeFooter
import com.example.ui.components.ScreenRoute
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToLiveChannel: (String) -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isHeroVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
    }

    val allMedia by viewModel.allMedia.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val series by viewModel.series.collectAsState()
    val animes by viewModel.animes.collectAsState()
    val doramas by viewModel.doramas.collectAsState()
    val recentAnimes by viewModel.recentAnimes.collectAsState()
    val recentDoramas by viewModel.recentDoramas.collectAsState()
    val myList by viewModel.myList.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val top10Medias by viewModel.top10Medias.collectAsState()
    val trendingMedia by viewModel.trendingMedia.collectAsState()
    val featuredMedias by viewModel.featuredMedias.collectAsState()
    val activeFeaturedItems by viewModel.activeFeaturedItems.collectAsState()
    val recentlyAddedMedia by viewModel.recentlyAddedMedia.collectAsState()
    val heroIndex by viewModel.heroIndex.collectAsState()
    val releases by viewModel.releases.collectAsState()
    val topRated by viewModel.topRated.collectAsState()
    val fallbackFeaturedList by viewModel.fallbackFeaturedList.collectAsState()

    // Home Isolated Search State
    val homeSearchQuery by viewModel.homeSearchQuery.collectAsState()
    val homeSearchResults by viewModel.homeSearchResults.collectAsState()
    val isHomeSearching by viewModel.isHomeSearching.collectAsState()

    // BackHandler: if searching on Home, back button cleans search instead of quitting
    BackHandler(enabled = homeSearchQuery.isNotBlank()) {
        viewModel.clearHomeSearch()
    }

    // Active Category Filter ("todos", "filmes", "series", "animes", "doramas")
    var selectedCategory by remember { mutableStateOf("todos") }

    // Specific isolated lists for categories
    val animeList = animes
    val doramaList = doramas

    var itemToRemoveFromContinue by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen"),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (homeSearchQuery.isNotBlank()) {
                // =========================================================================
                // 1. HOME ISOLATED SEARCH RESULTS VIEW
                // =========================================================================
                item {
                    HomeSearchResultsHeader(
                        query = homeSearchQuery,
                        resultCount = homeSearchResults.size,
                        isSearching = isHomeSearching,
                        onClearSearch = { viewModel.clearHomeSearch() }
                    )
                }

                if (isHomeSearching && homeSearchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = BrandRed,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Buscando no catálogo RONYCINE...",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (!isHomeSearching && homeSearchResults.isEmpty()) {
                    item {
                        HomeSearchEmptyState(
                            query = homeSearchQuery,
                            onClear = { viewModel.clearHomeSearch() }
                        )
                    }
                } else {
                    // Display results in 3-column rows using existing MediaCard
                    items(homeSearchResults.chunked(3)) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { media ->
                                MediaCard(
                                    media = media,
                                    onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill remaining columns in the row with empty space
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // 2. STANDARD HOME SECTIONS
                // =========================================================================
                // 1. CATEGORIES BAR (Horizontal Compact Navigation)
                item {
                    HomeCategoriesBar(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { cat ->
                            if (cat == "animes_doramas" || cat == "animes" || cat == "doramas") {
                                onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route)
                            } else {
                                selectedCategory = cat
                            }
                        },
                        onNavigateToLiveTv = onNavigateToLiveTv
                    )
                }

                // If "todos" or standard view, show full structured streaming Home
            if (selectedCategory == "todos") {
                // 2. HERO / DESTAQUE PRINCIPAL (Carousel)
                item {
                    if (fallbackFeaturedList.isNotEmpty()) {
                        val currentItem = fallbackFeaturedList[heroIndex % fallbackFeaturedList.size]
                        val inList = myList.any { it.tmdbId == currentItem.media.tmdbId }

                        HeroBanner(
                            featuredList = fallbackFeaturedList,
                            currentIndex = heroIndex % fallbackFeaturedList.size,
                            onSelectIndex = { index -> viewModel.selectHeroIndex(index) },
                            onTrailerEnded = { viewModel.nextHeroIndex() },
                            isInMyList = inList,
                            onWatchClick = { media -> onNavigateToWatch(media.tmdbId, media.mediaType, null, null) },
                            onMyListToggle = { media -> viewModel.toggleMyList(media.tmdbId, media.mediaType) },
                            isVisible = isHeroVisible
                        )
                    } else if (allMedia.isEmpty()) {
                        // Skeleton Shimmer for Hero
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandRed, strokeWidth = 2.dp)
                        }
                    }
                }

                // 3. TOP 10 HOJE (Numbered 01 to 10)
                if (top10Medias.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(14.dp)
                                        .background(BrandRed, RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = com.example.util.stringI18n("admin.top10").uppercase() + " HOJE",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.3.sp
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                items(
                                    count = top10Medias.size,
                                    key = { index -> "${top10Medias[index].mediaType}:${top10Medias[index].tmdbId}" },
                                    contentType = { "top10_card" }
                                ) { index ->
                                    val media = top10Medias[index]
                                    Top10MediaCard(
                                        media = media,
                                        rank = index + 1,
                                        onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. CONTINUE ASSISTINDO (If user has progress)
                if (continueWatching.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(14.dp)
                                        .background(BrandRed, RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = com.example.util.stringI18n("home.continue_watching").uppercase(),
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.3.sp
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                items(
                                    items = continueWatching,
                                    key = { it.id },
                                    contentType = { "continue_watching_card" }
                                ) { item ->
                                    ContinueWatchingCard(
                                        item = item,
                                        onClick = { onNavigateToWatch(item.tmdbId, item.mediaType, item.seasonNumber, item.episodeNumber) },
                                        onRequestRemove = { itemToRemoveFromContinue = item }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. FILMES E SÉRIES RECENTES (Recém-adicionados no catálogo - apenas filmes e séries)
                if (recentlyAddedMedia.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = com.example.util.stringI18n("home.recently_added"),
                            items = recentlyAddedMedia,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                        )
                    }
                }

                // 6. ANIMES (Exclusivo Animes)
                val displayAnimes = if (recentAnimes.isNotEmpty()) recentAnimes else animes
                if (displayAnimes.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "🎌 " + com.example.util.stringI18n("home.animes"),
                            items = displayAnimes,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route) }
                        )
                    }
                }

                // 7. DORAMAS (Exclusivo Doramas)
                val displayDoramas = if (recentDoramas.isNotEmpty()) recentDoramas else doramas
                if (displayDoramas.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "🇰🇷 " + com.example.util.stringI18n("home.doramas"),
                            items = displayDoramas,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route) }
                        )
                    }
                }

                // 8. DESTAQUES / EM ALTA (TMDB Trending)
                if (trendingMedia.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = com.example.util.stringI18n("nav.trending"),
                            items = trendingMedia,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.TRENDING.route) }
                        )
                    }
                }

                // 9. FILMES POPULARES
                if (movies.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = com.example.util.stringI18n("home.featured"),
                            items = movies,
                            onItemClick = { onNavigateToDetail(it.tmdbId, "movie") },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                        )
                    }
                }

                // 10. SÉRIES POPULARES
                if (series.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = com.example.util.stringI18n("home.popular_series"),
                            items = series,
                            onItemClick = { onNavigateToDetail(it.tmdbId, "tv") },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                        )
                    }
                }

                // 9. LANÇAMENTOS
                if (releases.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "Lançamentos",
                            items = releases,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    }
                }

                // 10. MAIS BEM AVALIADOS
                if (topRated.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = com.example.util.stringI18n("home.top_rated"),
                            items = topRated,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    }
                }

                // 11. MINHA LISTA (if not empty)
                if (myList.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = com.example.util.stringI18n("nav.myList"),
                            items = myList,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.MY_LIST.route) }
                        )
                    }
                }

            } else if (selectedCategory == "filmes") {
                // FILMES VIEW
                item {
                    CategoryHeaderBanner(title = "FILMES", subtitle = "Explore todos os filmes disponíveis")
                }
                item {
                    MediaSectionRow(
                        title = "Filmes em Destaque",
                        items = movies.take(15),
                        onItemClick = { onNavigateToDetail(it.tmdbId, "movie") }
                    )
                }
                item {
                    MediaSectionRow(
                        title = "Lançamentos em Filmes",
                        items = movies.sortedByDescending { it.releaseYear },
                        onItemClick = { onNavigateToDetail(it.tmdbId, "movie") }
                    )
                }
                item {
                    MediaSectionRow(
                        title = "Mais Bem Avaliados",
                        items = movies.filter { it.rating >= 7.0 },
                        onItemClick = { onNavigateToDetail(it.tmdbId, "movie") }
                    )
                }
            } else if (selectedCategory == "series") {
                // SÉRIES VIEW
                item {
                    CategoryHeaderBanner(title = "SÉRIES", subtitle = "Explore todas as séries e temporadas")
                }
                item {
                    MediaSectionRow(
                        title = "Séries em Destaque",
                        items = series.take(15),
                        onItemClick = { onNavigateToDetail(it.tmdbId, "tv") }
                    )
                }
                item {
                    MediaSectionRow(
                        title = "Lançamentos em Séries",
                        items = series.sortedByDescending { it.releaseYear },
                        onItemClick = { onNavigateToDetail(it.tmdbId, "tv") }
                    )
                }
            } else if (selectedCategory == "animes") {
                // ANIMES VIEW (EXCLUSIVO)
                item {
                    CategoryHeaderBanner(title = "🎌 ANIMES", subtitle = "Animes japoneses exclusivos e organizados")
                }
                item {
                    if (animeList.isNotEmpty()) {
                        MediaSectionRow(
                            title = "🎌 Animes em Destaque",
                            items = animeList,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nenhum anime catalogado no momento", color = Color.White, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                                ) {
                                    Text("🎌 Abrir Catálogo TMDB de Animes")
                                }
                            }
                        }
                    }
                }
            } else if (selectedCategory == "doramas") {
                // DORAMAS VIEW (EXCLUSIVO)
                item {
                    CategoryHeaderBanner(title = "🇰🇷 DORAMAS", subtitle = "Dramas e séries asiáticas autênticas")
                }
                item {
                    if (doramaList.isNotEmpty()) {
                        MediaSectionRow(
                            title = "🇰🇷 Doramas em Destaque",
                            items = doramaList,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nenhum dorama catalogado no momento", color = Color.White, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                                ) {
                                    Text("🇰🇷 Abrir Catálogo TMDB de Doramas")
                                }
                            }
                        }
                    }
                }
            }

            // Footer / About
            item {
                Spacer(modifier = Modifier.height(20.dp))
                PlayfilmeFooter(onNavigate = onNavigate)
            }
            } // Close else (Standard Home Sections)
        }
    }

    // Confirmation Dialog for Removing from Continue Watching
    itemToRemoveFromContinue?.let { targetItem ->
        AlertDialog(
            onDismissRequest = { itemToRemoveFromContinue = null },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remover de \"Continuar Assistindo\"?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = targetItem.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "O histórico de reprodução deste conteúdo será removido.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val itemToDelete = targetItem
                        itemToRemoveFromContinue = null
                        viewModel.removeFromContinueWatching(itemToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_remove_continue_button")
                ) {
                    Text("REMOVER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToRemoveFromContinue = null },
                    modifier = Modifier.testTag("cancel_remove_continue_button")
                ) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        )
    }
}

/**
 * Modern compact horizontal category navigation bar.
 */
@Composable
private fun HomeCategoriesBar(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onNavigateToLiveTv: () -> Unit
) {
    val labelAll = com.example.util.stringI18n("home.all")
    val labelMovies = com.example.util.stringI18n("home.movies")
    val labelSeries = com.example.util.stringI18n("home.series")
    val labelLiveTv = com.example.util.stringI18n("nav.liveTV")

    val categories = remember(labelAll, labelMovies, labelSeries, labelLiveTv) {
        listOf(
            "todos" to labelAll,
            "filmes" to labelMovies,
            "series" to labelSeries,
            "animes_doramas" to "ANIMES & DORAMAS",
            "tv" to labelLiveTv
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .testTag("home_categories_bar")
    ) {
        items(categories) { (id, label) ->
            val isSelected = selectedCategory == id

            if (id == "tv") {
                // TV ao Vivo shortcut
                Surface(
                    onClick = onNavigateToLiveTv,
                    shape = RoundedCornerShape(16.dp),
                    color = BrandRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandRed)
                        )
                        Text(
                            text = labelLiveTv,
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    onClick = { onSelectCategory(id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) BrandRed else DarkSurface,
                    border = if (!isSelected) BorderStroke(0.8.dp, CardBorder.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top 10 Card with integrated typographic numbers (01 to 10).
 */
@Composable
fun Top10MediaCard(
    media: MediaEntity,
    rank: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val formattedRank = remember(rank) { if (rank < 10) "0$rank" else "$rank" }

    val category = remember(media.mediaCategory, media.genres, media.title) {
        MediaClassifier.classifyMedia(media)
    }
    val badgeText = remember(category) {
        when (category) {
            MediaClassifier.CATEGORY_ANIME -> "ANIME"
            MediaClassifier.CATEGORY_DORAMA -> "DORAMA"
            MediaClassifier.CATEGORY_MOVIE -> "FILME"
            else -> "SÉRIE"
        }
    }
    val badgeColor = remember(category) {
        when (category) {
            MediaClassifier.CATEGORY_ANIME -> Color(0xFFE11D48)
            MediaClassifier.CATEGORY_DORAMA -> Color(0xFF7C3AED)
            MediaClassifier.CATEGORY_MOVIE -> BrandRed
            else -> Color(0xFF2563EB)
        }
    }

    val context = LocalContext.current
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

    Box(
        modifier = Modifier
            .width(135.dp)
            .height(180.dp)
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
            .testTag("top10_card_${media.tmdbId}")
    ) {
        // Large Stylized Rank Number (Backdrop shadow)
        Text(
            text = formattedRank,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.15f),
            letterSpacing = (-4).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = 14.dp)
        )

        // Poster Card
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxHeight()
                .width(96.dp)
                .align(Alignment.CenterEnd)
                .border(1.dp, CardBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Type Badge
                Surface(
                    color = badgeColor.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Circular Rating Badge (Top Right)
                if (media.rating > 0.0) {
                    CircularRatingBadge(
                        rating = media.rating,
                        size = 24.dp,
                        strokeWidth = 2.dp,
                        textSize = 7,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }
        }

        // High contrast front rank number
        Text(
            text = formattedRank,
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            color = BrandRed,
            letterSpacing = (-2).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = 4.dp)
        )
    }
}

/**
 * Continue Watching Card with progress bar and 3-dots menu.
 */
@Composable
fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onClick: () -> Unit,
    onRequestRemove: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imageRequest = remember(item.posterPath) {
        ImageRequest.Builder(context)
            .data(item.posterPath)
            .size(360, 230)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .width(180.dp)
            .height(115.dp)
            .clickable { onClick() }
            .testTag("continue_card_${item.tmdbId}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Play Icon in Center
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Continuar",
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandRed.copy(alpha = 0.9f))
                        .padding(5.dp)
                )
            }

            // 3-dots Menu Button (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .testTag("continue_more_button_${item.tmdbId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Mais opções",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                    modifier = Modifier
                        .background(DarkSurface)
                        .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remover da lista", color = Color.White, fontSize = 12.sp)
                            }
                        },
                        onClick = {
                            isMenuExpanded = false
                            onRequestRemove()
                        }
                    )
                }
            }

            // Title & Episode Info (Bottom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.seasonNumber != null && item.episodeNumber != null) {
                    Text(
                        text = "T${item.seasonNumber} • E${item.episodeNumber}",
                        color = Color.LightGray,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Red Progress Bar at the very bottom
            val progress = if (item.progressPercent > 0f) {
                (item.progressPercent / 100f).coerceIn(0.05f, 1f)
            } else if (item.totalDurationMs > 0) {
                (item.lastWatchedPositionMs.toFloat() / item.totalDurationMs.toFloat()).coerceIn(0.05f, 1f)
            } else 0.35f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(BrandRed)
                )
            }
        }
    }
}

@Composable
private fun CategoryHeaderBanner(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

/**
 * Clean, organized header for Home search results.
 */
@Composable
private fun HomeSearchResultsHeader(
    query: String,
    resultCount: Int,
    isSearching: Boolean,
    onClearSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(BrandRed, RoundedCornerShape(2.dp))
            )
            Column {
                Text(
                    text = "RESULTADOS PARA \"${query.trim().uppercase()}\"",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSearching) "Buscando no catálogo..." else "$resultCount ${if (resultCount == 1) "conteúdo encontrado" else "conteúdos encontrados"}",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        TextButton(
            onClick = onClearSearch,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.testTag("home_clear_search_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Limpar busca",
                tint = BrandRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Limpar",
                color = BrandRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Professional empty state for Home search.
 */
@Composable
private fun HomeSearchEmptyState(
    query: String,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(DarkSurface, CircleShape)
                    .border(1.dp, CardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "NENHUM RESULTADO ENCONTRADO",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Não encontramos filmes, séries, animes ou doramas para \"$query\".",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BrandRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("home_empty_return_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "VOLTAR AO CATÁLOGO",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
