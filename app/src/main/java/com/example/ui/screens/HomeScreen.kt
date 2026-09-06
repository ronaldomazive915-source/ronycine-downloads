package com.example.ui.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

    val allMedia by viewModel.allMedia.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val series by viewModel.series.collectAsState()
    val myList by viewModel.myList.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val top10Medias by viewModel.top10Medias.collectAsState()
    val trendingMedia by viewModel.trendingMedia.collectAsState()
    val featuredMedias by viewModel.featuredMedias.collectAsState()
    val activeFeaturedItems by viewModel.activeFeaturedItems.collectAsState()
    val recentlyAddedMedia by viewModel.recentlyAddedMedia.collectAsState()
    val heroIndex by viewModel.heroIndex.collectAsState()

    // Active Category Filter ("todos", "filmes", "series", "animes", "doramas")
    var selectedCategory by remember { mutableStateOf("todos") }

    val fallbackFeaturedList = remember(activeFeaturedItems, featuredMedias) {
        if (activeFeaturedItems.isNotEmpty()) {
            activeFeaturedItems
        } else if (featuredMedias.isNotEmpty()) {
            featuredMedias.map { media ->
                com.example.data.local.FeaturedMediaItem(
                    featured = com.example.data.local.FeaturedMediaEntity(
                        mediaTmdbId = media.tmdbId,
                        mediaType = media.mediaType,
                        trailerUrl = media.trailerKey ?: "",
                        autoPlayTrailer = true
                    ),
                    media = media
                )
            }
        } else {
            emptyList()
        }
    }

    val releases = remember(allMedia) {
        allMedia.sortedByDescending { it.releaseYear }
    }

    val topRated = remember(allMedia) {
        allMedia.filter { it.rating >= 7.5 }
    }

    // Specific filtered lists for category tabs
    val animeList = remember(allMedia) {
        allMedia.filter { it.genres.contains("Animação", ignoreCase = true) || it.genres.contains("Anime", ignoreCase = true) }
    }

    val doramaList = remember(allMedia) {
        allMedia.filter { it.genres.contains("Drama", ignoreCase = true) || it.genres.contains("Coreano", ignoreCase = true) }
    }

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
            // 1. CATEGORIES BAR (Horizontal Compact Navigation)
            item {
                HomeCategoriesBar(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { cat ->
                        selectedCategory = cat
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
                            onMyListToggle = { media -> viewModel.toggleMyList(media.tmdbId, media.mediaType) }
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
                                    text = "TOP 10 HOJE",
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
                                items(top10Medias.size) { index ->
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
                                    text = "CONTINUE ASSISTINDO",
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
                                items(continueWatching, key = { it.id }) { item ->
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

                // 5. FILMES E SÉRIES RECENTES (Recém-adicionados no catálogo)
                if (recentlyAddedMedia.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "Filmes e Séries Recentes",
                            items = recentlyAddedMedia,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                        )
                    }
                }

                // 6. DESTAQUES / EM ALTA (TMDB Trending)
                if (trendingMedia.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "Destaques em Alta",
                            items = trendingMedia,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.TRENDING.route) }
                        )
                    }
                }

                // 7. FILMES POPULARES
                if (movies.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "Filmes Populares",
                            items = movies,
                            onItemClick = { onNavigateToDetail(it.tmdbId, "movie") },
                            onViewAllClick = { onNavigate?.invoke(ScreenRoute.SEARCH.route) }
                        )
                    }
                }

                // 8. SÉRIES POPULARES
                if (series.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "Séries Populares",
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
                            title = "Mais Bem Avaliados",
                            items = topRated,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    }
                }

                // 11. MINHA LISTA (if not empty)
                if (myList.isNotEmpty()) {
                    item {
                        MediaSectionRow(
                            title = "Minha Lista",
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
                // ANIMES VIEW
                item {
                    CategoryHeaderBanner(title = "ANIMES", subtitle = "Animações e animes japoneses")
                }
                item {
                    if (animeList.isNotEmpty()) {
                        MediaSectionRow(
                            title = "Animes Populares",
                            items = animeList,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    } else {
                        MediaSectionRow(
                            title = "Em Destaque",
                            items = allMedia.take(10),
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    }
                }
            } else if (selectedCategory == "doramas") {
                // DORAMAS VIEW
                item {
                    CategoryHeaderBanner(title = "DORAMAS", subtitle = "Dramas asiáticos e séries românticas")
                }
                item {
                    if (doramaList.isNotEmpty()) {
                        MediaSectionRow(
                            title = "Doramas Populares",
                            items = doramaList,
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    } else {
                        MediaSectionRow(
                            title = "Em Destaque",
                            items = series.take(10),
                            onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                        )
                    }
                }
            }

            // Footer / About
            item {
                Spacer(modifier = Modifier.height(20.dp))
                PlayfilmeFooter(onNavigate = onNavigate)
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
    val categories = listOf(
        "todos" to "Todos",
        "filmes" to "Filmes",
        "series" to "Séries",
        "animes" to "Animes",
        "doramas" to "Doramas",
        "tv" to "TV ao Vivo"
    )

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
                            text = "TV ao Vivo",
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "top10_scale"
    )

    val formattedRank = if (rank < 10) "0$rank" else "$rank"

    Box(
        modifier = Modifier
            .width(135.dp)
            .height(180.dp)
            .scale(scale)
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
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.posterPath ?: media.backdropPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Type Badge
                Surface(
                    color = BrandRed,
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (media.mediaType == "tv") "SÉRIE" else "FILME",
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
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.posterPath)
                    .crossfade(true)
                    .build(),
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
