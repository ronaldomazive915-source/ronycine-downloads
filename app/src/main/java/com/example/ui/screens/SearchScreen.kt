package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.repository.SmartSearchResultItem
import com.example.data.repository.SearchItemStatus
import com.example.ui.components.MediaCard
import com.example.ui.components.MediaSectionRow
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RatingYellow
import com.example.ui.theme.CardBorder
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val smartSearchResults by viewModel.smartSearchResults.collectAsState()
    val processingKeys by viewModel.processingRequestKeys.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val allMedia by viewModel.allMedia.collectAsState()
    val trendingMedia by viewModel.trendingMedia.collectAsState()
    val topRatedMedia by viewModel.topRatedMedia.collectAsState()
    val recentlyAddedMedia by viewModel.recentlyAddedMedia.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()

    var filterType by remember { mutableStateOf("all") } // "all", "movie", "tv"
    var selectedGenre by remember { mutableStateOf("Todos") }

    val filteredCatalogue = remember(allMedia, filterType, selectedGenre) {
        allMedia.filter { media ->
            val typeMatch = when (filterType) {
                "movie" -> media.mediaType == "movie"
                "tv" -> media.mediaType == "tv"
                else -> true
            }
            val genreMatch = if (selectedGenre == "Todos") true else media.genres.contains(selectedGenre)
            typeMatch && genreMatch
        }.distinctBy { it.tmdbId }
    }

    val filteredSmartResults = remember(smartSearchResults, filterType) {
        smartSearchResults.filter { item ->
            val media = item.entity
            when (filterType) {
                "movie" -> media.mediaType == "movie"
                "tv" -> media.mediaType == "tv"
                else -> true
            }
        }.distinctBy { it.entity.tmdbId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("search_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Header & Search
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "🔍 Explorar",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Filmes e séries para assistir",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Pesquisar filmes e séries...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = DarkSurface,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input")
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Filters Row
        item {
            Column {
                // Type Filter
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filterType == "all",
                            onClick = { filterType = "all" },
                            label = { Text(" ✨ Todos") },
                            colors = filterChipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterType == "movie",
                            onClick = { filterType = "movie" },
                            label = { Text("🎬 Filmes") },
                            colors = filterChipColors()
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterType == "tv",
                            onClick = { filterType = "tv" },
                            label = { Text("📺 Séries") },
                            colors = filterChipColors()
                        )
                    }
                }

                // Genre Filter
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedGenre == "Todos",
                            onClick = { selectedGenre = "Todos" },
                            label = { Text("Todos Gêneros") },
                            colors = filterChipColors()
                        )
                    }
                    items(availableGenres) { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = { selectedGenre = genre },
                            label = { Text(genre) },
                            colors = filterChipColors()
                        )
                    }
                }
            }
        }

        // Content
        if (searchQuery.isNotBlank()) {
            // Search Mode
            if (isSearching) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandRed)
                    }
                }
            } else if (filteredSmartResults.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum resultado para \"$searchQuery\"", color = Color.Gray)
                    }
                }
            } else {
                item {
                    Text(
                        text = "Resultados para \"$searchQuery\"",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
                
                items(
                    items = filteredSmartResults,
                    key = { "${it.entity.mediaType}_${it.entity.tmdbId}" }
                ) { result ->
                    SmartSearchResultCard(
                        result = result,
                        isProcessing = processingKeys.contains("${result.entity.mediaType}_${result.entity.tmdbId}"),
                        onClick = { onNavigateToDetail(result.entity.tmdbId, result.entity.mediaType) },
                        onWatch = { onNavigate?.invoke("watch/${result.entity.tmdbId}/${result.entity.mediaType}") },
                        onSendRequest = { viewModel.sendMediaRequest(result.entity) }
                    )
                }
            }
        } else {
            // Catalogue Mode (When not searching)
            if (filterType == "all" && selectedGenre == "Todos") {
                // Featured Sections
                item {
                    MediaSectionRow(
                        title = "🔥 Em Alta",
                        items = trendingMedia,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) },
                        onViewAllClick = { onNavigate?.invoke("trending") }
                    )
                }
                item {
                    MediaSectionRow(
                        title = "⭐ Melhor Avaliados",
                        items = topRatedMedia,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                    )
                }
                item {
                    MediaSectionRow(
                        title = "🆕 Adicionados Recentemente",
                        items = recentlyAddedMedia,
                        onItemClick = { onNavigateToDetail(it.tmdbId, it.mediaType) }
                    )
                }
            }

            // Main Catalogue Grid
            item {
                Text(
                    text = when {
                        filterType == "movie" -> "🎬 Catálogo de Filmes"
                        filterType == "tv" -> "📺 Catálogo de Séries"
                        selectedGenre != "Todos" -> "🎭 $selectedGenre"
                        else -> "🎬 E 📺 Catálogo Completo"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                )
            }

            gridItems(filteredCatalogue, onNavigateToDetail)
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = BrandRed,
    selectedLabelColor = Color.White,
    containerColor = DarkSurface,
    labelColor = Color.LightGray
)

private fun LazyListScope.gridItems(
    items: List<MediaEntity>,
    onNavigateToDetail: (Int, String) -> Unit
) {
    val chunks = items.chunked(3)
    items(chunks) { rowItems ->
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
            // Fill empty space if row has less than 3 items
            repeat(3 - rowItems.size) {
                Box(Modifier.weight(1f))
            }
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("smart_search_card_${media.tmdbId}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.2f))
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
                    .width(85.dp)
                    .height(125.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(media.posterPath ?: media.backdropPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Type Badge (🎬/📺)
                Surface(
                    color = BrandRed.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (media.mediaType == "tv") "📺 TV" else "🎬 FILME",
                        color = Color.White,
                        fontSize = 8.sp,
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (media.releaseYear.isNotBlank()) {
                        Text(
                            text = media.releaseYear,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                // Original Title
                if (media.originalTitle.isNotBlank() && !media.originalTitle.equals(media.title, ignoreCase = true)) {
                    Text(
                        text = media.originalTitle,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Genres & Rating Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rating
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = RatingYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = String.format("%.1f", media.rating),
                            color = RatingYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Divider
                    Text("|", color = Color.DarkGray, fontSize = 12.sp)

                    // Genres
                    Text(
                        text = media.genres.ifBlank { "Sem gênero" },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Description
                Text(
                    text = media.overview.ifBlank { "Nenhuma sinopse disponível." },
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )

                // Status & Custom Button Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status label
                    val statusColor = when (result.status) {
                        SearchItemStatus.IN_CATALOG -> Color(0xFF4CAF50) // Green
                        SearchItemStatus.READY_TO_WATCH -> Color(0xFF4CAF50) // Green
                        SearchItemStatus.REQUEST_PENDING -> Color(0xFFFFEB3B) // Yellow
                        SearchItemStatus.AVAILABLE_FOR_REQUEST -> Color(0xFFF44336) // Red
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusColor, RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = result.statusLabel,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button
                    when (result.status) {
                        SearchItemStatus.IN_CATALOG, SearchItemStatus.READY_TO_WATCH -> {
                            Button(
                                onClick = { onWatch() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("watch_button_${media.tmdbId}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                Box(
                                    modifier = Modifier.height(28.dp).padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = BrandRed,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSendRequest() },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("request_button_${media.tmdbId}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = BrandRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text("PEDIR FILME", fontSize = 10.sp, fontWeight = FontWeight.Black)
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
