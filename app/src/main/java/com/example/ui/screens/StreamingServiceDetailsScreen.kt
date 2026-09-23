package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbMediaDto
import com.example.data.remote.TmdbNetwork
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.*

// Genre model mapping TMDB genre IDs
data class GenreItem(val id: Int?, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingServiceDetailsScreen(
    serviceName: String,
    viewModel: MainViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { TmdbNetwork.apiService }
    val apiKey = remember { com.example.BuildConfig.TMDB_API_KEY.ifEmpty { "d60f5b5a778fe74b5cf4a371f008725b" } }
    val region = "BR" // Always use "BR" watch region to guarantee a consistent and fully populated streaming catalog across all devices globally
    val providerId = remember(serviceName) { getProviderIdForService(serviceName) }

    // Unified genres
    val genresList = remember {
        listOf(
            GenreItem(null, "Todos"),
            GenreItem(28, "Ação"),
            GenreItem(12, "Aventura"),
            GenreItem(16, "Animação"),
            GenreItem(35, "Comédia"),
            GenreItem(80, "Crime"),
            GenreItem(99, "Documentário"),
            GenreItem(18, "Drama"),
            GenreItem(10751, "Família"),
            GenreItem(27, "Terror"),
            GenreItem(10749, "Romance"),
            GenreItem(878, "Ficção Científica"),
            GenreItem(53, "Suspense")
        )
    }

    // User interaction states
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf<GenreItem>(genresList[0]) }
    var selectedType by remember { mutableStateOf("all") } // "all", "movie", "tv"
    var selectedSort by remember { mutableStateOf("popularity.desc") } // "popularity.desc", "release_date.desc", "vote_average.desc"

    // Unified loading and data states
    var isLoadingBrowse by remember { mutableStateOf(true) }
    var isLoadingExplorer by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    // Browse data lists
    var moviesOnPlatform by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }
    var seriesOnPlatform by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }
    var popularMovies by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }
    var popularSeries by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }
    var releaseMedias by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }
    var topRatedMedias by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }

    // Explorer dynamic catalog (filters, search, sorts)
    var explorerResults by remember { mutableStateOf<List<TmdbMediaDto>>(emptyList()) }
    var currentExplorerPage by remember { mutableStateOf(1) }
    var hasMoreExplorerPages by remember { mutableStateOf(true) }

    val brandStyle = remember(serviceName) { getBrandStyleForService(serviceName) }

    // Toggle showing explorer instead of home grid
    val isExplorerMode = remember(searchQuery, selectedGenre, selectedType, selectedSort) {
        searchQuery.isNotBlank() || selectedGenre.id != null || selectedType != "all" || selectedSort != "popularity.desc"
    }

    // Load initial Home/Browse data safely
    LaunchedEffect(serviceName, region) {
        isLoadingBrowse = true
        isError = false
        try {
            // Concurrent loading of all rows with individual safety handlers to prevent cascading failures
            val moviesPlatformJob = coroutineScope.async {
                try {
                    apiService.discoverMovies(apiKey = apiKey, watchRegion = region, withWatchProviders = providerId, page = 1).results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("StreamingServiceDetails", "Erro ao carregar filmes da plataforma: ${e.message}")
                    emptyList()
                }
            }
            val seriesPlatformJob = coroutineScope.async {
                try {
                    apiService.discoverSeries(apiKey = apiKey, watchRegion = region, withWatchProviders = providerId, page = 1).results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("StreamingServiceDetails", "Erro ao carregar séries da plataforma: ${e.message}")
                    emptyList()
                }
            }
            val popularMoviesJob = coroutineScope.async {
                try {
                    apiService.discoverMovies(apiKey = apiKey, watchRegion = region, withWatchProviders = providerId, sortBy = "popularity.desc", page = 1).results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("StreamingServiceDetails", "Erro ao carregar filmes populares: ${e.message}")
                    emptyList()
                }
            }
            val popularSeriesJob = coroutineScope.async {
                try {
                    apiService.discoverSeries(apiKey = apiKey, watchRegion = region, withWatchProviders = providerId, sortBy = "popularity.desc", page = 1).results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("StreamingServiceDetails", "Erro ao carregar séries populares: ${e.message}")
                    emptyList()
                }
            }
            val releaseMoviesJob = coroutineScope.async {
                try {
                    apiService.discoverMovies(apiKey = apiKey, watchRegion = region, withWatchProviders = providerId, sortBy = "release_date.desc", page = 1).results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("StreamingServiceDetails", "Erro ao carregar lançamentos: ${e.message}")
                    emptyList()
                }
            }
            val topRatedMoviesJob = coroutineScope.async {
                try {
                    apiService.discoverMovies(apiKey = apiKey, watchRegion = region, withWatchProviders = providerId, sortBy = "vote_average.desc", page = 1).results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("StreamingServiceDetails", "Erro ao carregar melhor avaliados: ${e.message}")
                    emptyList()
                }
            }

            moviesOnPlatform = moviesPlatformJob.await()
            seriesOnPlatform = seriesPlatformJob.await()
            popularMovies = popularMoviesJob.await()
            popularSeries = popularSeriesJob.await()
            releaseMedias = releaseMoviesJob.await()
            topRatedMedias = topRatedMoviesJob.await()

            // If ALL lists are empty, only then flag it as an error state
            if (moviesOnPlatform.isEmpty() && seriesOnPlatform.isEmpty() && popularMovies.isEmpty() &&
                popularSeries.isEmpty() && releaseMedias.isEmpty() && topRatedMedias.isEmpty()) {
                isError = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isError = true
        } finally {
            isLoadingBrowse = false
        }
    }

    // Load custom dynamic query in explorer mode
    LaunchedEffect(isExplorerMode, searchQuery, selectedGenre, selectedType, selectedSort, region) {
        if (!isExplorerMode) return@LaunchedEffect
        isLoadingExplorer = true
        currentExplorerPage = 1
        hasMoreExplorerPages = true
        try {
            if (searchQuery.isNotBlank()) {
                // Perform live multi-search and filter by provider check asynchronously
                val rawSearch = apiService.searchMulti(apiKey = apiKey, query = searchQuery, page = 1)
                val rawResults = rawSearch.results ?: emptyList()

                // Check provider availability concurrently for maximum performance
                val filtered = rawResults.map { media ->
                    coroutineScope.async {
                        try {
                            val providersRes = if (media.title != null) {
                                apiService.getMovieWatchProviders(media.id, apiKey)
                            } else {
                                apiService.getSeriesWatchProviders(media.id, apiKey)
                            }
                            val regionData = providersRes.results?.get(region)
                            val matchingProvider = regionData?.flatrate?.any {
                                providerId.split("|").contains(it.providerId.toString())
                            } == true || regionData?.buy?.any {
                                providerId.split("|").contains(it.providerId.toString())
                            } == true
                            if (matchingProvider) media else null
                        } catch (ex: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                explorerResults = filtered
                hasMoreExplorerPages = false
            } else {
                // Handle discover with real filters and sort properties
                val genreString = selectedGenre.id?.toString()
                val moviesList = if (selectedType == "all" || selectedType == "movie") {
                    apiService.discoverMovies(
                        apiKey = apiKey,
                        watchRegion = region,
                        withWatchProviders = providerId,
                        withGenres = genreString,
                        sortBy = selectedSort,
                        page = 1
                    ).results ?: emptyList()
                } else emptyList()

                val seriesList = if (selectedType == "all" || selectedType == "tv") {
                    apiService.discoverSeries(
                        apiKey = apiKey,
                        watchRegion = region,
                        withWatchProviders = providerId,
                        withGenres = genreString,
                        sortBy = selectedSort,
                        page = 1
                    ).results ?: emptyList()
                } else emptyList()

                val combined = (moviesList + seriesList).sortedWith(compareByDescending {
                    when (selectedSort) {
                        "vote_average.desc" -> it.voteAverage ?: 0.0
                        "release_date.desc" -> (it.releaseDate ?: it.firstAirDate ?: "")
                        else -> it.voteAverage ?: 0.0
                    }
                })
                explorerResults = combined
            }
        } catch (e: Exception) {
            e.printStackTrace()
            explorerResults = emptyList()
        } finally {
            isLoadingExplorer = false
        }
    }

    // Main layout scaffolding
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = serviceName.uppercase(),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_streaming_details")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("streaming_details_screen_${serviceName.lowercase()}")
        ) {
            // Visual header of the brand style
            StreamingHeaderCard(
                brandStyle = brandStyle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Live Search Bar + Dynamic Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Pesquisar na ${brandStyle.name}...", color = Color.Gray, fontSize = 14.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.White)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF16161D),
                        unfocusedContainerColor = Color(0xFF111116),
                        focusedBorderColor = brandStyle.brandColor,
                        unfocusedBorderColor = CardBorder.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                )
            }

            // Interactive Genres & Filters Toolbar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(genresList) { genre ->
                    val isSelected = selectedGenre == genre
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) brandStyle.brandColor else Color(0xFF1A1A22),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedGenre = genre }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = genre.name,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Filters (Type + Sort)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Media Type Selector
                val types = listOf("all" to "Todos", "movie" to "Filmes", "tv" to "Séries")
                types.forEach { (typeKey, typeLabel) ->
                    val isSelected = selectedType == typeKey
                    Surface(
                        color = if (isSelected) brandStyle.brandColor.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) brandStyle.brandColor else CardBorder.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = typeKey }
                    ) {
                        Text(
                            text = typeLabel,
                            color = if (isSelected) brandStyle.brandColor else Color.Gray,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                // Sort Dropdown Selector
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .background(Color(0xFF1E1E26), RoundedCornerShape(8.dp))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Ordenar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xFF1A1A24))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mais Populares", color = Color.White) },
                            onClick = { selectedSort = "popularity.desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Mais Recentes", color = Color.White) },
                            onClick = { selectedSort = "release_date.desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Melhor Avaliados", color = Color.White) },
                            onClick = { selectedSort = "vote_average.desc"; showSortMenu = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content renderer
            if (isError) {
                ErrorState(onRetry = {
                    isLoadingBrowse = true
                    isError = false
                })
            } else if (isExplorerMode) {
                // --- EXPLORER RESULTS DISPLAY GRID ---
                if (isLoadingExplorer) {
                    SkeletonGrid()
                } else if (explorerResults.isEmpty()) {
                    EmptyExplorerState(serviceName = serviceName)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(explorerResults, key = { it.id }, contentType = { "stream_media_card" }) { item ->
                            StreamMediaCard(
                                media = item,
                                brandStyle = brandStyle,
                                onClick = {
                                    val type = if (item.title != null) "movie" else "tv"
                                    onNavigateToDetail(item.id, type)
                                }
                            )
                        }
                    }
                }
            } else {
                // --- BROWSE MODE CATEGORIES DISPLAY (POINT 4) ---
                if (isLoadingBrowse) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        repeat(3) {
                            SkeletonRow()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. FILMES NA PLATAFORMA
                        if (moviesOnPlatform.isNotEmpty()) {
                            HorizontalMediaRow(
                                title = "FILMES NA ${brandStyle.name.uppercase()}",
                                list = moviesOnPlatform,
                                brandStyle = brandStyle,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }

                        // 2. SÉRIES NA PLATAFORMA
                        if (seriesOnPlatform.isNotEmpty()) {
                            HorizontalMediaRow(
                                title = "SÉRIES NA ${brandStyle.name.uppercase()}",
                                list = seriesOnPlatform,
                                brandStyle = brandStyle,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }

                        // 3. FILMES POPULARES
                        if (popularMovies.isNotEmpty()) {
                            HorizontalMediaRow(
                                title = "FILMES POPULARES",
                                list = popularMovies,
                                brandStyle = brandStyle,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }

                        // 4. SÉRIES POPULARES
                        if (popularSeries.isNotEmpty()) {
                            HorizontalMediaRow(
                                title = "SÉRIES POPULARES",
                                list = popularSeries,
                                brandStyle = brandStyle,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }

                        // 5. LANÇAMENTOS
                        if (releaseMedias.isNotEmpty()) {
                            HorizontalMediaRow(
                                title = "LANÇAMENTOS",
                                list = releaseMedias,
                                brandStyle = brandStyle,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }

                        // 6. MELHOR AVALIADOS
                        if (topRatedMedias.isNotEmpty()) {
                            HorizontalMediaRow(
                                title = "MELHOR AVALIADOS",
                                list = topRatedMedias,
                                brandStyle = brandStyle,
                                onNavigateToDetail = onNavigateToDetail
                            )
                        }

                        // JustWatch and TMDB Attribution
                        Text(
                            text = "Dados de streaming fornecidos por TMDB e JustWatch. Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB.",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalMediaRow(
    title: String,
    list: List<TmdbMediaDto>,
    brandStyle: ServiceBrandStyle,
    onNavigateToDetail: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }, contentType = { "stream_media_card" }) { item ->
                StreamMediaCard(
                    media = item,
                    brandStyle = brandStyle,
                    onClick = {
                        val type = if (item.title != null) "movie" else "tv"
                        onNavigateToDetail(item.id, type)
                    },
                    modifier = Modifier.width(105.dp)
                )
            }
        }
    }
}

@Composable
fun StreamMediaCard(
    media: TmdbMediaDto,
    brandStyle: ServiceBrandStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(media.posterPath) {
        ImageRequest.Builder(context)
            .data("https://image.tmdb.org/t/p/w185${media.posterPath}")
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .crossfade(false)
            .build()
    }

    val displayTitle = remember(media.title, media.name) { media.title ?: media.name ?: "Sem Título" }
    val releaseDate = remember(media.releaseDate, media.firstAirDate) { media.releaseDate ?: media.firstAirDate ?: "" }
    val year = remember(releaseDate) { if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "S/D" }
    val isMovie = remember(media.title) { media.title != null }
    val typeLabel = remember(isMovie) { if (isMovie) "Filme" else "Série" }
    val rating = media.voteAverage ?: 0.0
    val formattedRating = remember(rating) { String.format(java.util.Locale.US, "★ %.1f", rating) }

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121216)),
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .border(0.5.dp, CardBorder.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        ) {
            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    onState = { imageState = it },
                    modifier = Modifier.fillMaxSize().background(Color(0xFF121216))
                )

                if (imageState is AsyncImagePainter.State.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(shimmerBrush()),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = brandStyle.brandColor,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (imageState is AsyncImagePainter.State.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E26)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RONYCINE",
                            color = Color.White.copy(alpha = 0.2f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sleek Brand Logo Badge Overlay
                Surface(
                    color = brandStyle.brandColor.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = brandStyle.name.uppercase(),
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Sleek Rating Overlay Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = formattedRating,
                        color = Color(0xFFFFB300),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = displayTitle,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "$year • $typeLabel",
            color = Color.Gray,
            fontSize = 9.5.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .width(120.dp)
                .height(14.dp)
                .background(shimmerBrush(), RoundedCornerShape(4.dp))
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(5) {
                Column(modifier = Modifier.width(105.dp)) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(2f / 3f)
                            .fillMaxWidth()
                            .background(shimmerBrush(), RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(10.dp)
                            .background(shimmerBrush(), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(12) {
            Column {
                Box(
                    modifier = Modifier
                        .aspectRatio(2f / 3f)
                        .fillMaxWidth()
                        .background(shimmerBrush(), RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(10.dp)
                        .background(shimmerBrush(), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun shimmerBrush(targetValue: Float = 1000f): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnimation"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF13131A),
            Color(0xFF22222E),
            Color(0xFF13131A)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnimation, y = translateAnimation)
    )
}

@Composable
fun EmptyExplorerState(serviceName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📭", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            text = "Nenhum resultado encontrado.",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Tente alterar os termos da busca ou ajustar os filtros de gênero/tipo na plataforma $serviceName.",
            color = TextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📡", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            text = "Não foi possível carregar o catálogo.",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
        ) {
            Text("Tentar novamente", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

data class ServiceBrandStyle(
    val name: String,
    val brandColor: Color,
    val description: String,
    val logoText: String
)

fun getBrandStyleForService(serviceName: String): ServiceBrandStyle {
    val clean = serviceName.lowercase().trim().replace(" ", "").replace("+", "plus").replace("-", "")
    return when (clean) {
        "netflix" -> ServiceBrandStyle("Netflix", Color(0xFFE50914), "Filmes e séries originais da líder em streaming.", "N")
        "disney", "disneyplus" -> ServiceBrandStyle("Disney+", Color(0xFF0063E5), "Histórias mágicas de Disney, Pixar, Marvel e Star Wars.", "D+")
        "globoplay" -> ServiceBrandStyle("Globoplay", Color(0xFFEE2D24), "Novelas, séries nacionais e jornalismo ao vivo.", "G")
        "primevideo", "amazonprimevideo" -> ServiceBrandStyle("Prime Video", Color(0xFF00A8E1), "Séries exclusivas, filmes populares e canais.", "P")
        "max", "hbomax" -> ServiceBrandStyle("Max", Color(0xFF0036C5), "A casa da HBO, Warner Bros, DC e Discovery.", "M")
        "appletv", "appletvplus" -> ServiceBrandStyle("Apple TV+", Color(0xFF7D7D7D), "Produções originais premiadas com qualidade cinematográfica.", "")
        "paramount", "paramountplus" -> ServiceBrandStyle("Paramount+", Color(0xFF0054FF), "Montanha de entretenimento com esportes e séries.", "P+")
        "crunchyroll" -> ServiceBrandStyle("Crunchyroll", Color(0xFFF47521), "O maior acervo de animes, mangás e doramas do mundo.", "C")
        "mubi" -> ServiceBrandStyle("MUBI", Color(0xFF00FFC2), "Cinema de arte, clássicos cult e cinema independente.", "M")
        "plutotv" -> ServiceBrandStyle("Pluto TV", Color(0xFF00A2FF), "TV ao vivo 100% gratuita com canais temáticos.", "P")
        "telecine" -> ServiceBrandStyle("Telecine", Color(0xFFFF003C), "O melhor do cinema mundial na sua tela.", "T")
        "starz", "starzplay", "lionsgate" -> ServiceBrandStyle("Starz", Color(0xFFD4AF37), "Séries de prestígio e filmes premiados.", "S")
        "mgm", "mgmplus", "epix" -> ServiceBrandStyle("MGM+", Color(0xFFFFD700), "Séries épicas e os maiores clássicos de Hollywood.", "M+")
        else -> ServiceBrandStyle(serviceName, Color(0xFFE50914), "Catálogo de filmes e séries da plataforma.", "S")
    }
}

@Composable
fun StreamingHeaderCard(
    brandStyle: ServiceBrandStyle,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = brandStyle.brandColor.copy(alpha = 0.08f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            brandStyle.brandColor.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(brandStyle.brandColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = brandStyle.logoText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Column {
                Text(
                    text = brandStyle.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = brandStyle.description,
                    color = Color.Gray,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

fun getProviderIdForService(serviceName: String): String {
    return when (serviceName.lowercase().trim().replace(" ", "").replace("+", "plus").replace("-", "")) {
        "netflix" -> "8"
        "disney", "disneyplus" -> "337"
        "globoplay" -> "307"
        "primevideo", "amazonprimevideo" -> "119"
        "max", "hbomax" -> "1899|384"
        "appletv", "appletvplus" -> "350"
        "paramount", "paramountplus" -> "531"
        "crunchyroll" -> "283"
        "mubi" -> "11"
        "plutotv" -> "300"
        "telecine" -> "47"
        "starz", "starzplay", "lionsgate" -> "268|1825"
        "mgm", "mgmplus", "epix" -> "584|1960"
        else -> ""
    }
}
