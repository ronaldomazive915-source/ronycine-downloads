package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.data.local.WatchHistoryEntity
import com.example.ui.components.CircularRatingBadge
import com.example.ui.components.DestaquesCarouselSection
import com.example.ui.components.HeroBanner
import com.example.ui.components.MediaCard
import com.example.ui.components.MediaSectionRow
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.components.StreamingServicesSection
import com.example.util.MediaClassifier
import com.example.ui.components.PlayfilmeFooter
import com.example.ui.components.ScreenRoute
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import com.example.util.directionLockedHorizontalScroll

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

    val myList by viewModel.myList.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val top10Medias by viewModel.top10Medias.collectAsStateWithLifecycle()
    val fallbackFeaturedList by viewModel.fallbackFeaturedList.collectAsStateWithLifecycle()
    val heroIndex by viewModel.heroIndex.collectAsStateWithLifecycle()

    // Optimized Media Sections from ViewModel
    val topMovies by viewModel.topMovies.collectAsStateWithLifecycle()
    val releaseMovies by viewModel.releaseMovies.collectAsStateWithLifecycle()
    val topRatedMovies by viewModel.topRatedMovies.collectAsStateWithLifecycle()
    val topSeries by viewModel.topSeries.collectAsStateWithLifecycle()
    val releaseSeries by viewModel.releaseSeries.collectAsStateWithLifecycle()
    val filteredRecentlyAdded by viewModel.filteredRecentlyAdded.collectAsStateWithLifecycle()
    val filteredTrending by viewModel.filteredTrending.collectAsStateWithLifecycle()
    val filteredReleases by viewModel.filteredReleases.collectAsStateWithLifecycle()
    val filteredTopRated by viewModel.filteredTopRated.collectAsStateWithLifecycle()
    val filteredPopularMovies by viewModel.filteredPopularMovies.collectAsStateWithLifecycle()
    val filteredPopularSeries by viewModel.filteredPopularSeries.collectAsStateWithLifecycle()
    val displayAnimes by viewModel.displayAnimes.collectAsStateWithLifecycle()
    val displayDoramas by viewModel.displayDoramas.collectAsStateWithLifecycle()
    val destaqueItems by viewModel.destaqueItems.collectAsStateWithLifecycle()

    // Home Isolated Search State
    val homeSearchQuery by viewModel.homeSearchQuery.collectAsStateWithLifecycle()
    val homeSearchResults by viewModel.homeSearchResults.collectAsStateWithLifecycle()
    val isHomeSearching by viewModel.isHomeSearching.collectAsStateWithLifecycle()

    // BackHandler: if searching on Home, back button cleans search instead of quitting
    BackHandler(enabled = homeSearchQuery.isNotBlank()) {
        viewModel.clearHomeSearch()
    }

    // Active Category Filter ("todos", "filmes", "series", "animes", "doramas")
    var selectedCategory by remember { mutableStateOf("todos") }

    // Progressive Rendering: Load sections in batches to avoid freezing UI on startup
    var sectionsBatch by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        // First batch: Hero + Continue Watching + Top 10 + Destaques
        delay(100)
        sectionsBatch = 2 // Next sections: Recently Added + Trending
        delay(300)
        sectionsBatch = 3 // Popular Movies + Series + Releases
        delay(500)
        sectionsBatch = 4 // All remaining sections (Animes, Doramas, Top Rated, etc)
    }

    var itemToRemoveFromContinue by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    // Pull-to-Refresh State (Distance reduced to ~36dp for immediate responsiveness)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val refreshThresholdPx = with(density) { 36.dp.toPx() }
    val maxPullPx = with(density) { 58.dp.toPx() }

    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshResultState by remember { mutableStateOf<Boolean?>(null) }

    val animatedPullOffset by animateFloatAsState(
        targetValue = pullOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pullOffset"
    )

    val nestedScrollConnection = remember(isRefreshing, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isRefreshing && pullOffsetPx > 0f && available.y < 0f) {
                    val newOffset = (pullOffsetPx + available.y).coerceAtLeast(0f)
                    val consumedY = newOffset - pullOffsetPx
                    pullOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Strictly allow pull-to-refresh only when at the top (scrollTop <= 0)
                if (!isRefreshing && available.y > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    val newOffset = (pullOffsetPx + available.y * 0.70f).coerceIn(0f, maxPullPx)
                    val consumedY = newOffset - pullOffsetPx
                    pullOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshing && pullOffsetPx > 0f) {
                    if (pullOffsetPx >= refreshThresholdPx) {
                        pullOffsetPx = with(density) { 36.dp.toPx() }
                        coroutineScope.launch {
                            isRefreshing = true
                            refreshResultState = null
                            val success = try {
                                viewModel.refreshHomeData()
                            } catch (e: Exception) {
                                false
                            }
                            refreshResultState = success

                            if (success) {
                                delay(600L)
                            } else {
                                delay(900L)
                            }

                            pullOffsetPx = 0f
                            delay(200L)
                            isRefreshing = false
                            refreshResultState = null
                        }
                    } else {
                        pullOffsetPx = 0f
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .nestedScroll(nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen"),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (homeSearchQuery.isNotBlank()) {
                homeSearchLayer(
                    query = homeSearchQuery,
                    results = homeSearchResults,
                    isSearching = isHomeSearching,
                    onDetail = onNavigateToDetail,
                    onClear = { viewModel.clearHomeSearch() }
                )
            } else {
                homeStandardLayer(
                    viewModel = viewModel,
                    sectionsBatch = sectionsBatch,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { cat ->
                        if (cat == "animes_doramas" || cat == "animes" || cat == "doramas") {
                            onNavigate?.invoke(ScreenRoute.ANIMES_DORAMAS.route)
                        } else {
                            selectedCategory = cat
                        }
                    },
                    onNavigateToLiveTv = onNavigateToLiveTv,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToWatch = onNavigateToWatch,
                    onNavigate = onNavigate,
                    isHeroVisible = isHeroVisible,
                    fallbackFeaturedList = fallbackFeaturedList,
                    heroIndex = heroIndex,
                    myList = myList,
                    continueWatching = continueWatching,
                    top10Medias = top10Medias,
                    destaqueItems = destaqueItems,
                    filteredRecentlyAdded = filteredRecentlyAdded,
                    filteredTrending = filteredTrending,
                    filteredPopularMovies = filteredPopularMovies,
                    filteredPopularSeries = filteredPopularSeries,
                    filteredReleases = filteredReleases,
                    filteredTopRated = filteredTopRated,
                    onRequestRemoveContinue = { itemToRemoveFromContinue = it }
                )
            }
        }

        // PULL TO REFRESH OVERLAY INDICATOR (Ultra-light, responsive, smile-centered)
        AnimatedVisibility(
            visible = animatedPullOffset > 1.5f || isRefreshing,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(120)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = with(density) { (animatedPullOffset * 0.55f).toDp().coerceIn(6.dp, 40.dp) })
                .testTag("pull_to_refresh_indicator")
        ) {
            val progress = (animatedPullOffset / refreshThresholdPx).coerceIn(0f, 1f)
            val isReadyToRelease = !isRefreshing && animatedPullOffset >= refreshThresholdPx

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = DarkSurface.copy(alpha = 0.95f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isReadyToRelease || isRefreshing) BrandRed.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.2f)
                    ),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRefreshing && refreshResultState == null) {
                            RonycineSmileLoader(
                                modifier = Modifier.size(24.dp),
                                color = BrandRed
                            )
                        } else if (refreshResultState == true) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Atualizado",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (refreshResultState == false) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Erro ao atualizar",
                                tint = BrandRed,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            RonycineSmileLoader(
                                progress = progress,
                                color = if (isReadyToRelease) BrandRed else Color.White.copy(alpha = 0.85f),
                                size = 24.dp
                            )
                        }
                    }
                }

                // Minimal micro-label: smile remains the primary hero
                val microLabel = when {
                    refreshResultState == true -> "atualizado"
                    refreshResultState == false -> "erro ao atualizar"
                    isRefreshing -> "processando..."
                    isReadyToRelease -> "solte para atualizar"
                    else -> null
                }

                if (microLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.80f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Text(
                            text = microLabel,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
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
 * Highly optimized for smooth 60/120fps scrolling.
 */
@Composable
fun Top10MediaCard(
    media: MediaEntity,
    rank: Int,
    onClick: () -> Unit
) {
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
    val context = LocalContext.current
    val imageRequest = remember(item.posterPath) {
        ImageRequest.Builder(context)
            .data(item.posterPath)
            .size(280, 420)
            .crossfade(false)
            .build()
    }

    val progressFraction = (item.progressPercent / 100f).coerceIn(0.01f, 1f)

    Card(
        modifier = Modifier
            .width(125.dp)
            .clickable { onClick() }
            .testTag("continue_card_${item.tmdbId}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. POSTER with Center Play and Top-Right Close/Remove Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Subtle dark overlay to make elements pop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )

                // Play Icon in Center
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Continuar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Remove [×] Button (Top Right)
                IconButton(
                    onClick = onRequestRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .testTag("continue_remove_button_${item.tmdbId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // 2. REAL PROGRESS BAR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(BrandRed)
                )
            }

            // 3. DETAILS & ACTIONS (Compact vertical metadata)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Percentage watched
                Text(
                    text = "${item.progressPercent.toInt()}% assistido",
                    color = BrandRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                // Title
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Episode info for series
                if (item.seasonNumber != null && item.episodeNumber != null) {
                    Text(
                        text = "T${item.seasonNumber} • E${item.episodeNumber}",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Compact action row: [▶ Continuar] [×]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandRed)
                            .clickable { onClick() }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Continuar",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { onRequestRemove() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remover",
                            tint = Color.LightGray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
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

// --- Modularized Sub-composables for HomeScreen ---

private fun androidx.compose.foundation.lazy.LazyListScope.homeSearchLayer(
    query: String,
    results: List<MediaEntity>,
    isSearching: Boolean,
    onDetail: (Int, String) -> Unit,
    onClear: () -> Unit
) {
    item(key = "search_header") {
        HomeSearchResultsHeader(
            query = query,
            resultCount = results.size,
            isSearching = isSearching,
            onClearSearch = onClear
        )
    }

    if (isSearching && results.isEmpty()) {
        item(key = "search_loading") {
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
                    RonycineSmileLoader(color = BrandRed, size = 36.dp)
                    Text(
                        text = "Buscando no catálogo RONYCINE...",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    } else if (!isSearching && results.isEmpty()) {
        item(key = "search_empty") {
            HomeSearchEmptyState(query = query, onClear = onClear)
        }
    } else {
        items(
            items = results.chunked(3),
            key = { row -> row.joinToString("-") { "${it.mediaType}:${it.tmdbId}" } }
        ) { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { media ->
                    MediaCard(
                        media = media,
                        onClick = { onDetail(media.tmdbId, media.mediaType) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeStandardLayer(
    viewModel: MainViewModel,
    sectionsBatch: Int,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit,
    onNavigate: ((String) -> Unit)?,
    isHeroVisible: Boolean,
    fallbackFeaturedList: List<com.example.data.local.FeaturedMediaItem>,
    heroIndex: Int,
    myList: List<MediaEntity>,
    continueWatching: List<WatchHistoryEntity>,
    top10Medias: List<MediaEntity>,
    destaqueItems: List<MediaEntity>,
    filteredRecentlyAdded: List<MediaEntity>,
    filteredTrending: List<MediaEntity>,
    filteredPopularMovies: List<MediaEntity>,
    filteredPopularSeries: List<MediaEntity>,
    filteredReleases: List<MediaEntity>,
    filteredTopRated: List<MediaEntity>,
    onRequestRemoveContinue: (WatchHistoryEntity) -> Unit
) {
    item(key = "categories_bar") {
        HomeCategoriesBar(
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory,
            onNavigateToLiveTv = onNavigateToLiveTv
        )
    }

    if (selectedCategory == "todos") {
        item(key = "hero_banner") {
            if (fallbackFeaturedList.isNotEmpty()) {
                HeroBanner(
                    featuredList = fallbackFeaturedList,
                    currentIndex = heroIndex % fallbackFeaturedList.size,
                    onSelectIndex = { viewModel.selectHeroIndex(it) },
                    isInMyList = myList.any { it.tmdbId == fallbackFeaturedList[heroIndex % fallbackFeaturedList.size].media.tmdbId },
                    isItemInMyList = { media -> myList.any { it.tmdbId == media.tmdbId } },
                    onWatchClick = { m -> onNavigateToWatch(m.tmdbId, m.mediaType, null, null) },
                    onMyListToggle = { m -> viewModel.toggleMyList(m.tmdbId, m.mediaType) },
                    onCardClick = { m -> onNavigateToDetail(m.tmdbId, m.mediaType) },
                    isVisible = isHeroVisible
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(320.dp).background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) { RonycineSmileLoader(color = BrandRed, size = 32.dp) }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue_watching_section") {
                HomeContinueWatchingSection(
                    items = continueWatching,
                    onWatch = onNavigateToWatch,
                    onRemove = onRequestRemoveContinue
                )
            }
        }

        item(key = "streaming_services_section") {
            StreamingServicesSection(onServiceClick = { onNavigate?.invoke("streaming/$it") })
        }

        if (top10Medias.isNotEmpty()) {
            item(key = "top10_section") {
                HomeTop10Section(
                    items = top10Medias,
                    onDetail = onNavigateToDetail,
                    onViewAll = { onNavigate?.invoke(ScreenRoute.TRENDING.route) }
                )
            }
        }

        if (destaqueItems.isNotEmpty()) {
            item(key = "destaques_carousel_section") {
                DestaquesCarouselSection(
                    items = destaqueItems,
                    onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                    onViewAllClick = { onNavigate?.invoke(ScreenRoute.TRENDING.route) }
                )
            }
        }

        // --- Progressive Sections ---
        if (sectionsBatch >= 2) {
            if (filteredRecentlyAdded.isNotEmpty()) {
                item(key = "recently_added_section") {
                    MediaSectionRow(
                        title = com.example.util.stringI18n("home.recently_added"),
                        items = filteredRecentlyAdded,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                        onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                    )
                }
            }
            if (filteredTrending.isNotEmpty()) {
                item(key = "trending_section") {
                    MediaSectionRow(
                        title = com.example.util.stringI18n("nav.trending"),
                        items = filteredTrending,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                        onViewAllClick = { onNavigate?.invoke(ScreenRoute.TRENDING.route) }
                    )
                }
            }
        }

        if (sectionsBatch >= 3) {
            if (filteredPopularMovies.isNotEmpty()) {
                item(key = "popular_movies_section") {
                    MediaSectionRow(
                        title = com.example.util.stringI18n("home.featured"),
                        items = filteredPopularMovies,
                        onItemClick = { onNavigateToDetail(it.tmdbId, "movie") },
                        onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                    )
                }
            }
            if (filteredPopularSeries.isNotEmpty()) {
                item(key = "popular_series_section") {
                    MediaSectionRow(
                        title = com.example.util.stringI18n("home.popular_series"),
                        items = filteredPopularSeries,
                        onItemClick = { onNavigateToDetail(it.tmdbId, "tv") },
                        onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                    )
                }
            }
            if (filteredReleases.isNotEmpty()) {
                item(key = "releases_section") {
                    MediaSectionRow(
                        title = "Lançamentos",
                        items = filteredReleases,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                    )
                }
            }
        }

        if (sectionsBatch >= 4) {
            if (filteredTopRated.isNotEmpty()) {
                item(key = "top_rated_section") {
                    MediaSectionRow(
                        title = com.example.util.stringI18n("home.top_rated"),
                        items = filteredTopRated,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                    )
                }
            }
            if (myList.isNotEmpty()) {
                item(key = "my_list_section") {
                    MediaSectionRow(
                        title = com.example.util.stringI18n("nav.myList"),
                        items = myList,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                        onViewAllClick = { onNavigate?.invoke(ScreenRoute.MY_LIST.route) }
                    )
                }
            }
        }
    } else if (selectedCategory == "filmes") {
        item(key = "filmes_header") {
            CategoryHeaderBanner(title = "FILMES", subtitle = "Explore todos os filmes disponíveis")
        }
        if (filteredPopularMovies.isNotEmpty()) {
            item(key = "filmes_populares") {
                MediaSectionRow(
                    title = "Populares",
                    items = filteredPopularMovies,
                    onItemClick = { onNavigateToDetail(it.tmdbId, "movie") }
                )
            }
        }
        if (filteredReleases.isNotEmpty()) {
            item(key = "filmes_lancamentos") {
                MediaSectionRow(
                    title = "Lançamentos",
                    items = filteredReleases.filter { it.mediaType == "movie" },
                    onItemClick = { onNavigateToDetail(it.tmdbId, "movie") }
                )
            }
        }
    } else if (selectedCategory == "series") {
        item(key = "series_header") {
            CategoryHeaderBanner(title = "SÉRIES", subtitle = "Explore todas as séries e temporadas")
        }
        if (filteredPopularSeries.isNotEmpty()) {
            item(key = "series_populares") {
                MediaSectionRow(
                    title = "Populares",
                    items = filteredPopularSeries,
                    onItemClick = { onNavigateToDetail(it.tmdbId, "tv") }
                )
            }
        }
        if (filteredReleases.isNotEmpty()) {
            item(key = "series_lancamentos") {
                MediaSectionRow(
                    title = "Lançamentos",
                    items = filteredReleases.filter { it.mediaType == "tv" },
                    onItemClick = { onNavigateToDetail(it.tmdbId, "tv") }
                )
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(20.dp))
        PlayfilmeFooter(onNavigate = onNavigate)
    }
}

@Composable
private fun HomeContinueWatchingSection(
    items: List<WatchHistoryEntity>,
    onWatch: (Int, String, Int?, Int?) -> Unit,
    onRemove: (WatchHistoryEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
        HomeSectionHeader(title = com.example.util.stringI18n("home.continue_watching"))
        
        var isScrollEnabled by remember { mutableStateOf(true) }
        LazyRow(
            userScrollEnabled = isScrollEnabled,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 2.dp).directionLockedHorizontalScroll { 
                if (isScrollEnabled != it) isScrollEnabled = it 
            }
        ) {
            items(
                items = items,
                key = { "${it.id}_${it.tmdbId}_${it.mediaType}_${it.seasonNumber}_${it.episodeNumber}" },
                contentType = { "continue_watching_card" }
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onWatch(item.tmdbId, item.mediaType, item.seasonNumber, item.episodeNumber) },
                    onRequestRemove = { onRemove(item) }
                )
            }
        }
    }
}

@Composable
private fun HomeTop10Section(
    items: List<MediaEntity>,
    onDetail: (Int, String) -> Unit,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        HomeSectionHeader(
            title = com.example.util.stringI18n("admin.top10") + " HOJE",
            onViewAll = onViewAll
        )
        
        var isScrollEnabled by remember { mutableStateOf(true) }
        LazyRow(
            userScrollEnabled = isScrollEnabled,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 2.dp).directionLockedHorizontalScroll { 
                if (isScrollEnabled != it) isScrollEnabled = it 
            }
        ) {
            items(
                count = items.size,
                key = { index -> "${items[index].mediaType}:${items[index].tmdbId}" },
                contentType = { "top10_card" }
            ) { index ->
                Top10MediaCard(
                    media = items[index],
                    rank = index + 1,
                    onClick = { onDetail(items[index].tmdbId, items[index].mediaType) }
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.width(3.dp).height(14.dp).background(BrandRed, RoundedCornerShape(2.dp)))
            Text(
                text = title.uppercase(),
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }
        if (onViewAll != null) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onViewAll() }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(text = com.example.util.stringI18n("action.view_all"), color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = BrandRed, modifier = Modifier.size(10.dp))
            }
        }
    }
}
