package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.local.DetailedCastMember
import com.example.data.local.parseCastList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import com.example.data.local.WatchHistoryEntity
import com.example.ui.components.CircularRatingBadge
import com.example.ui.components.DownloadOptionsBottomSheet
import com.example.ui.viewmodel.DownloadState
import com.example.ui.components.HeroTrailerPlayer
import com.example.ui.components.TmdbCircularBadge
import com.example.ui.components.openYouTubeExternal
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.components.CompactDownloadButton
import com.example.ui.components.ContentShareBottomSheet
import com.example.ui.components.SeriesEpisodeDownloadBottomSheet
import com.example.ui.components.MediaReleaseBadge
import com.example.data.remote.MediaReleaseInfo
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.PendingDownloadIntent



/**
 * Refined, professional DetailScreen for Movies and TV Shows on RONYCINE.
 * 
 * Strict Audit & Refinement Rules:
 * 1. Single TMDB Rating: Displayed ONLY ONCE via a compact circular badge (`TmdbCircularBadge`).
 * 2. RONYCINE Community Rating: Kept distinct with interactive 5-star rating picker.
 * 3. Single Trailer Action: Exactly ONE Trailer button in the primary actions row, no duplicate trailers below.
 * 4. Real TMDB Cast: Shows actor photos, real names, and character roles in compact cards.
 * 5. Full Cast Modal: "Ver elenco completo" option for long cast lists.
 * 6. "Você também pode gostar": Compact recommendation cards with poster and small circular ratings.
 * 7. Mobile First: Clean vertical hierarchy, zero horizontal overflows on 360px-430px screens.
 */
@Composable
fun DetailScreen(
    tmdbId: Int,
    mediaType: String,
    viewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit,
    onNavigateToDetail: ((Int, String) -> Unit)? = null,
    onNavigateToDownloads: (() -> Unit)? = null,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(tmdbId, mediaType) {
        viewModel.loadMediaDetails(tmdbId, mediaType)
    }

    val media by viewModel.selectedMedia.collectAsState()
    val releaseInfo by viewModel.selectedMediaReleaseInfo.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val myList by viewModel.myList.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val allCatalogMedia by viewModel.allMedia.collectAsState()
    val mediaCastMap by viewModel.mediaCastMap.collectAsState()
    val mediaCastLoadingMap by viewModel.mediaCastLoadingMap.collectAsState()

    var showTrailerDialog by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var showFullCastModal by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // RONYCINE Community Interactive Rating State
    var userRating by remember(tmdbId) { mutableStateOf(0) }
    var userRatingSaved by remember(tmdbId) { mutableStateOf(false) }

    // BackHandler for trailer modal
    BackHandler(enabled = showTrailerDialog || showFullCastModal) {
        if (showTrailerDialog) showTrailerDialog = false
        else if (showFullCastModal) showFullCastModal = false
    }

    val isAccessRestricted by viewModel.isAccessRestricted.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    if (isAccessRestricted || (activeProfile != null && activeProfile!!.isKidsProfile && media != null && !media?.title.isNullOrBlank() && !com.example.util.ContentAccessManager.canProfileAccessContent(activeProfile, media!!))) {
        RestrictedAccessScreen(onNavigateBack = onNavigateBack)
        return
    }

    if (media == null || media?.title.isNullOrBlank()) {
        DetailScreenSkeleton(
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
        return
    }

    val downloadState by viewModel.downloadState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val pendingDownloadIntent by viewModel.pendingDownloadIntent.collectAsState()

    var showDownloadSheet by remember { mutableStateOf(false) }
    var showSeriesDownloadSheet by remember { mutableStateOf(false) }
    var downloadTargetUrl by remember { mutableStateOf("") }
    var downloadTargetFileName by remember { mutableStateOf("") }

    // Download Metadata States
    var currentDownloadMediaType by remember { mutableStateOf("movie") }
    var currentDownloadTmdbId by remember { mutableStateOf("") }
    var currentDownloadTitle by remember { mutableStateOf("") }
    var currentDownloadSubtitle by remember { mutableStateOf<String?>(null) }
    var currentDownloadPoster by remember { mutableStateOf<String?>(null) }
    var currentDownloadSeason by remember { mutableStateOf<Int?>(null) }
    var currentDownloadEpisode by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pendingDownloadIntent, media, currentUser) {
        val intent = pendingDownloadIntent
        if (intent != null && media != null && intent.tmdbId == media?.tmdbId && !intent.isPlayer) {
            if (currentUser != null) {
                // Real auth verified: proceed with download resolution
                if (intent.isSeriesDownloadSheet) {
                    showSeriesDownloadSheet = true
                } else if (intent.season != null && intent.episode != null) {
                    currentDownloadMediaType = "tv"
                    currentDownloadTmdbId = media!!.tmdbId.toString()
                    currentDownloadTitle = media!!.title
                    currentDownloadSubtitle = "S${intent.season}E${intent.episode}"
                    currentDownloadPoster = media!!.posterPath
                    currentDownloadSeason = intent.season
                    currentDownloadEpisode = intent.episode

                    viewModel.resolveDownload(
                        type = "tv",
                        tmdbId = media!!.tmdbId,
                        season = intent.season,
                        episode = intent.episode,
                        mediaTitle = "${media!!.title} S${intent.season}E${intent.episode}"
                    )
                } else {
                    currentDownloadMediaType = "movie"
                    currentDownloadTmdbId = media!!.tmdbId.toString()
                    currentDownloadTitle = media!!.title
                    currentDownloadSubtitle = media!!.releaseYear
                    currentDownloadPoster = media!!.posterPath
                    currentDownloadSeason = null
                    currentDownloadEpisode = null

                    viewModel.resolveDownload("movie", media!!.tmdbId, mediaTitle = media!!.title)
                }
                viewModel.clearPendingDownloadIntent()
            }
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            showSeriesDownloadSheet = false
            showDownloadSheet = false
            viewModel.resetDownloadState()
        }
    }
    var showShareSheet by remember { mutableStateOf(false) }
    var shareEpisodeTarget by remember { mutableStateOf<EpisodeEntity?>(null) }

    if (showShareSheet && media != null) {
        ContentShareBottomSheet(
            media = media!!,
            onDismiss = { showShareSheet = false }
        )
    }

    if (shareEpisodeTarget != null && media != null) {
        ContentShareBottomSheet(
            media = media!!,
            seasonNumber = shareEpisodeTarget!!.seasonNumber,
            episodeNumber = shareEpisodeTarget!!.episodeNumber,
            episodeTitle = shareEpisodeTarget!!.title,
            onDismiss = { shareEpisodeTarget = null }
        )
    }

    if (showSeriesDownloadSheet && media != null) {
        SeriesEpisodeDownloadBottomSheet(
            media = media!!,
            viewModel = viewModel,
            initialSeason = selectedSeason,
            onDismiss = { showSeriesDownloadSheet = false },
            onNavigateToDownloads = {
                showSeriesDownloadSheet = false
                onNavigateToDownloads?.invoke()
            },
            onNavigateToLogin = onNavigateToLogin
        )
    }

    if (showDownloadSheet) {
        DownloadOptionsBottomSheet(
            url = downloadTargetUrl,
            fileName = downloadTargetFileName,
            tmdbId = currentDownloadTmdbId,
            title = currentDownloadTitle,
            subTitle = currentDownloadSubtitle,
            posterPath = currentDownloadPoster,
            mediaType = currentDownloadMediaType,
            seasonNumber = currentDownloadSeason,
            episodeNumber = currentDownloadEpisode,
            viewModel = viewModel,
            onDismiss = { 
                showDownloadSheet = false
                viewModel.resetDownloadState()
            },
            onNavigateToDownloads = {
                showDownloadSheet = false
                viewModel.resetDownloadState()
                onNavigateToDownloads?.invoke()
            },
            onNavigateToLogin = onNavigateToLogin
        )
    }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Ready) {
            downloadTargetUrl = (downloadState as DownloadState.Ready).url
            downloadTargetFileName = (downloadState as DownloadState.Ready).fileName
            showDownloadSheet = true
        } else if (downloadState is DownloadState.Error) {
            infoMessage = (downloadState as DownloadState.Error).message
            viewModel.resetDownloadState()
        }
    }

    val item = media!!
    val isInMyList = myList.any { it.tmdbId == item.tmdbId }

    val watchHistoryMap = remember(watchHistory, item.tmdbId) {
        watchHistory.filter { it.tmdbId == item.tmdbId && it.mediaType == "tv" }
            .associateBy { "${it.seasonNumber}_${it.episodeNumber}" }
    }

    val movieHistory = remember(watchHistory, item.tmdbId) {
        watchHistory.firstOrNull { it.tmdbId == item.tmdbId && (it.mediaType == "movie" || it.mediaType.isBlank()) }
    }

    val fetchedCast = mediaCastMap[item.tmdbId]
    val isCastLoading = mediaCastLoadingMap[item.tmdbId] == true

    val castList = remember(fetchedCast, item.cast) {
        if (!fetchedCast.isNullOrEmpty()) {
            fetchedCast
        } else {
            parseCastList(item.cast)
        }
    }

    LaunchedEffect(item.tmdbId, item.mediaType) {
        viewModel.fetchCastForMedia(item.tmdbId, item.mediaType, item.cast)
    }

    val relatedItems = remember(allCatalogMedia, item.tmdbId, item.genres) {
        val mainGenre = item.genres.split(",").firstOrNull()?.trim() ?: ""
        allCatalogMedia.filter { cat ->
            cat.tmdbId != item.tmdbId && (mainGenre.isBlank() || cat.genres.contains(mainGenre, ignoreCase = true) || cat.mediaType == item.mediaType)
        }.take(10)
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isTabletOrDesktop = screenWidthDp >= 680

    // Info alert dialog
    if (infoMessage != null) {
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            containerColor = DarkSurface,
            title = { Text("Aviso", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(infoMessage ?: "", color = Color.LightGray, fontSize = 13.5.sp) },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) {
                    Text("Entendi", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal de Elenco Completo
    if (showFullCastModal && castList.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showFullCastModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .systemBarsPadding()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Elenco Completo • ${item.title}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showFullCastModal = false },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222228))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(castList) { actor ->
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    if (!actor.profileUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(actor.profileUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = actor.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF222228))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF222228)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = actor.name.firstOrNull()?.uppercase() ?: "A",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = actor.name,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (actor.character.isNotBlank()) {
                                            Text(
                                                text = actor.character,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
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

    // Modal de Trailer Único
    if (showTrailerDialog && !item.trailerKey.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showTrailerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .systemBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(color = BrandRed, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "TRAILER",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { showTrailerDialog = false },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222228))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    val trailerUrl = "https://www.youtube.com/watch?v=${item.trailerKey}"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        HeroTrailerPlayer(
                            youtubeId = item.trailerKey ?: "",
                            autoPlay = true,
                            isMuted = false,
                            showControls = true,
                            onPlayerReady = {},
                            onPlayerError = {},
                            onPlayerEnded = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { openYouTubeExternal(context, trailerUrl) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir no App do YouTube", color = BrandRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. BACKDROP HEADER WITH GRADIENT OVERLAY
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTabletOrDesktop) 320.dp else 230.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.backdropPath?.ifBlank { null } ?: item.posterPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent,
                                    DarkBackground.copy(alpha = 0.5f),
                                    DarkBackground.copy(alpha = 0.92f),
                                    DarkBackground
                                )
                            )
                        )
                )

                // Discretely Floating Back Button (←)
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, CardBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. POSTER + TITLE & METADATA HEADER BLOCK WITH SINGLE CIRCULAR TMDB BADGE
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Poster Card
                    val posterUrl = if (!item.posterPath.isNullOrBlank()) {
                        if (item.posterPath!!.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w342${item.posterPath}"
                    } else null

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.8f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .width(if (isTabletOrDesktop) 135.dp else 115.dp)
                            .height(if (isTabletOrDesktop) 195.dp else 165.dp)
                    ) {
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // Title & Side Information Block
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = if (isTabletOrDesktop) 24.sp else 19.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = if (isTabletOrDesktop) 28.sp else 23.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(color = BrandRed, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = if (item.mediaType == "tv") "SÉRIE" else "FILME",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            if (item.releaseYear.isNotBlank()) {
                                Text(
                                    text = item.releaseYear,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (item.restricted18) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, null, tint = Color(0xFFEF4444), modifier = Modifier.size(10.dp))
                                        Text(
                                            text = "18+",
                                            color = Color(0xFFEF4444),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }

                        val durationOrSeasons = if (item.mediaType == "tv") {
                            val count = if (item.seasonsCount > 0) item.seasonsCount else 1
                            if (count == 1) "1 temporada" else "$count temporadas"
                        } else {
                            val mins = if (item.durationMinutes > 0) item.durationMinutes else 118
                            val hours = mins / 60
                            val remMins = mins % 60
                            if (hours > 0) "${hours}h ${remMins}min" else "${mins}min"
                        }
                        Text(
                            text = durationOrSeasons,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        val cleanGenres = remember(item.genres) {
                            item.genres.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .take(3)
                                .joinToString(" • ")
                        }
                        if (cleanGenres.isNotBlank()) {
                            Text(
                                text = cleanGenres,
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // SINGLE OFFICIAL TMDB CIRCULAR BADGE (Requirement 1 & 2)
                        if (item.rating > 0.0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            TmdbCircularBadge(
                                rating = item.rating,
                                size = 44.dp,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // COMPACT PROFESSIONAL RELEASE INFO BADGE (Calendário)
                if (releaseInfo != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    MediaReleaseBadge(
                        releaseInfo = releaseInfo,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. ACTION BUTTONS (Assistir, Minha Lista, Trailer - Audited Single Set)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val watchProgress = movieHistory?.progressPercent ?: 0f
                    val hasPartialProgress = item.mediaType == "movie" && watchProgress > 5f && watchProgress < 90f
                    val isUnreleasedMovie = releaseInfo?.isMovie == true && releaseInfo?.isUnreleasedContent == true

                    val playButtonText = when {
                        hasPartialProgress -> "Continuar (${watchProgress.toInt()}%)"
                        isUnreleasedMovie -> "Em Breve"
                        else -> "Assistir"
                    }

                    // Primary Action Row: [ ▶ Assistir ] [ ↓ Baixar ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary Play Button
                        Button(
                            onClick = {
                                android.util.Log.i("RONYCINE_DIAG", "[PLAYER_CLICK] Usuário clicou em Assistir no Detalhe. tmdbId=${item.tmdbId}")
                                onNavigateToWatch(
                                    item.tmdbId,
                                    item.mediaType,
                                    if (item.mediaType == "tv") selectedSeason else null,
                                    if (item.mediaType == "tv") 1 else null
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("detail_play_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = playButtonText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // [ ↓ Baixar ] (Filmes e Séries)
                        if (item.mediaType == "movie") {
                            val isResolvingThis = downloadState is DownloadState.Resolving && 
                                    (downloadState as DownloadState.Resolving).tmdbId == item.tmdbId && 
                                    (downloadState as DownloadState.Resolving).season == null

                            CompactDownloadButton(
                                isResolving = isResolvingThis,
                                onClick = { 
                                    viewModel.requireAuthenticationForDownload(
                                        isAuthenticated = currentUser != null,
                                        pendingIntent = PendingDownloadIntent(
                                            type = "movie",
                                            tmdbId = item.tmdbId,
                                            mediaTitle = item.title
                                        ),
                                        onNavigateToLogin = onNavigateToLogin,
                                        onAlreadyAuthenticated = {
                                            currentDownloadMediaType = "movie"
                                            currentDownloadTmdbId = item.tmdbId.toString()
                                            currentDownloadTitle = item.title
                                            currentDownloadSubtitle = (item as? MediaEntity)?.releaseYear
                                            currentDownloadPoster = item.posterPath
                                            currentDownloadSeason = null
                                            currentDownloadEpisode = null

                                            viewModel.resolveDownload("movie", item.tmdbId, mediaTitle = item.title) 
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("detail_download_button")
                            )
                        } else {
                            CompactDownloadButton(
                                isResolving = false,
                                onClick = { 
                                    viewModel.requireAuthenticationForDownload(
                                        isAuthenticated = currentUser != null,
                                        pendingIntent = PendingDownloadIntent(
                                            type = "tv",
                                            tmdbId = item.tmdbId,
                                            isSeriesDownloadSheet = true
                                        ),
                                        onNavigateToLogin = onNavigateToLogin,
                                        onAlreadyAuthenticated = {
                                            showSeriesDownloadSheet = true
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("detail_series_download_button")
                            )
                        }
                    }

                    // Secondary Action Buttons Row: [ ＋ Minha Lista ] [ ▶ Trailer ] [ 🔗 Partilhar ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // [ ＋ Minha Lista ]
                        OutlinedButton(
                            onClick = { 
                                viewModel.requireAuthentication(
                                    isAuthenticated = currentUser != null,
                                    onNavigateToLogin = onNavigateToLogin,
                                    pendingAction = com.example.ui.viewmodel.PendingAction.ToggleMyList(item.tmdbId, item.mediaType),
                                    onAlreadyAuthenticated = {
                                        viewModel.toggleMyList(item.tmdbId, item.mediaType)
                                    }
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isInMyList) BrandRed.copy(alpha = 0.6f) else CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isInMyList) BrandRed.copy(alpha = 0.12f) else DarkSurface,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("detail_my_list_toggle")
                        ) {
                            Icon(
                                imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isInMyList) BrandRed else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isInMyList) "Na Lista" else "Minha Lista",
                                color = if (isInMyList) BrandRed else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        // [ ▶ Trailer ] (AUDITED SINGLE TRAILER ACTION - Requirement 4)
                        if (!item.trailerKey.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { showTrailerDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("detail_trailer_button")
                            ) {
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Trailer", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }

                        // [ 🔗 Partilhar ]
                        OutlinedButton(
                            onClick = { showShareSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("detail_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Partilhar", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Partilhar", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }

                // 4. SINOPSE SECTION
                val hasLongOverview = item.overview.length > 180
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .animateContentSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .background(BrandRed, RoundedCornerShape(1.dp))
                        )
                        Text(
                            text = "SINOPSE",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = item.overview.ifBlank { "Sinopse não disponível para este título." },
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.5.sp,
                        lineHeight = 19.5.sp,
                        maxLines = if (isSynopsisExpanded || !hasLongOverview) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasLongOverview) {
                        Text(
                            text = if (isSynopsisExpanded) "Ver menos" else "Ver mais...",
                            color = BrandRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                .padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                // 5. REAL TMDB CAST SECTION WITH COMPACT CAROUSEL (Requirement 5, 6, 7, 8 & 9)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(12.dp)
                                    .background(BrandRed, RoundedCornerShape(1.dp))
                            )
                            Text(
                                text = "ELENCO",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            if (castList.isNotEmpty()) {
                                Text(
                                    text = "(${castList.size})",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (castList.size > 6) {
                            Text(
                                text = "Ver elenco completo",
                                color = BrandRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showFullCastModal = true }
                            )
                        }
                    }

                    if (isCastLoading && castList.isEmpty()) {
                        // Skeleton Loading State
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            items(6) {
                                Surface(
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, CardBorder),
                                    modifier = Modifier.width(92.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF282830))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(60.dp)
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF282830))
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(40.dp)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF222228))
                                        )
                                    }
                                }
                            }
                        }
                    } else if (castList.isNotEmpty()) {
                        // Real TMDB Cast Items
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            items(castList.take(8)) { actor ->
                                Surface(
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, CardBorder),
                                    modifier = Modifier.width(92.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, CardBorder.copy(alpha = 0.5f), CircleShape)
                                                .background(Color(0xFF222228)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!actor.profileUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(actor.profileUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = actor.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = actor.name,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = actor.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 12.5.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        if (actor.character.isNotBlank()) {
                                            Text(
                                                text = actor.character,
                                                color = TextSecondary,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty / Fallback
                        Surface(
                            color = DarkSurface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.6.dp, CardBorder.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Elenco indisponível no momento.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Director / Showrunners
                val isRealDirector = item.director.isNotBlank() &&
                        !item.director.contains("Direção Aclamada", ignoreCase = true) &&
                        !item.director.contains("Desconhecido", ignoreCase = true)

                if (isRealDirector) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = if (item.mediaType == "tv") "Criadores:" else "Direção:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = item.director,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 6. RONYCINE COMMUNITY INTERACTIVE RATING SECTION (Requirement 3)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(0.8.dp, CardBorder.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AVALIAÇÕES RONYCINE",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )

                            if (userRatingSaved) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Avaliado!",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                val effectiveRating = if (userRating > 0) userRating.toDouble() else 5.0
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", effectiveRating),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    (1..5).forEach { starIndex ->
                                        val isFilled = starIndex <= (if (userRating > 0) userRating else 5)
                                        Icon(
                                            imageVector = if (isFilled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = null,
                                            tint = if (isFilled) Color(0xFFFBBF24) else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (userRatingSaved) "Sua avaliação registrada no RONYCINE" else "1 avaliação de usuário",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Sua avaliação",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (1..5).forEach { starIndex ->
                                        val isSelected = starIndex <= userRating
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = "Avaliar $starIndex estrelas",
                                            tint = if (isSelected) Color(0xFFFBBF24) else Color.Gray,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable {
                                                    userRating = starIndex
                                                    userRatingSaved = true
                                                    infoMessage = "Sua avaliação de $starIndex estrela(s) foi gravada com sucesso no RONYCINE!"
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. TV SERIES: SEASONS & EPISODES SECTION
        if (item.mediaType == "tv") {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .background(BrandRed, RoundedCornerShape(1.dp))
                        )
                        Text(
                            text = "TEMPORADAS & EPISÓDIOS",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    val totalSeasons = if (item.seasonsCount > 0) item.seasonsCount else 1
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                    ) {
                        (1..totalSeasons).forEach { seasonNum ->
                            FilterChip(
                                selected = selectedSeason == seasonNum,
                                onClick = { viewModel.loadSeasonEpisodes(item.tmdbId, seasonNum) },
                                label = {
                                    Text(
                                        text = "Temporada $seasonNum",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedSeason == seasonNum) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkSurface,
                                    labelColor = Color.LightGray
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selectedSeason == seasonNum) BrandRed else CardBorder
                                )
                            )
                        }
                    }
                }
            }

            if (episodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RonycineSmileLoader(color = BrandRed, size = 32.dp)
                    }
                }
            } else {
                items(episodes, key = { "${it.seasonNumber}_${it.episodeNumber}" }) { ep ->
                    val epKey = "${ep.seasonNumber}_${ep.episodeNumber}"
                    val epHistory = watchHistoryMap[epKey]
                    
                    val isResolvingThis = downloadState is DownloadState.Resolving && 
                        (downloadState as DownloadState.Resolving).tmdbId == item.tmdbId && 
                        (downloadState as DownloadState.Resolving).season == ep.seasonNumber && 
                        (downloadState as DownloadState.Resolving).episode == ep.episodeNumber

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        ModernEpisodeCard(
                            media = item,
                            episode = ep,
                            viewModel = viewModel,
                            epHistory = epHistory,
                            isResolvingDownload = isResolvingThis,
                            isAuthenticated = currentUser != null,
                            onPlayClick = {
                                onNavigateToWatch(item.tmdbId, "tv", ep.seasonNumber, ep.episodeNumber)
                            },
                            onShowInfo = { msg -> infoMessage = msg },
                            onShareClick = { shareEpisodeTarget = ep },
                            onSetDownloadMetadata = { mType, tId, title, sub, poster, sNum, eNum ->
                                currentDownloadMediaType = mType
                                currentDownloadTmdbId = tId
                                currentDownloadTitle = title
                                currentDownloadSubtitle = sub
                                currentDownloadPoster = poster
                                currentDownloadSeason = sNum
                                currentDownloadEpisode = eNum
                            },
                            onNavigateToLogin = onNavigateToLogin
                        )
                    }
                }
            }
        }

        // 8. RELATED CONTENT ("VOCÊ TAMBÉM PODE GOSTAR") (Requirement 10 & 11)
        if (relatedItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .background(BrandRed, RoundedCornerShape(1.dp))
                        )
                        Text(
                            text = "VOCÊ TAMBÉM PODE GOSTAR",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(relatedItems, key = { "rel_${it.tmdbId}_${it.mediaType}" }) { rel ->
                            val relPosterUrl = if (!rel.posterPath.isNullOrBlank()) {
                                if (rel.posterPath!!.startsWith("http")) rel.posterPath else "https://image.tmdb.org/t/p/w185${rel.posterPath}"
                            } else null

                            Surface(
                                onClick = {
                                    onNavigateToDetail?.invoke(rel.tmdbId, rel.mediaType)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(0.8.dp, CardBorder),
                                modifier = Modifier
                                    .width(105.dp)
                                    .height(155.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (!relPosterUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(relPosterUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = rel.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(DarkSurface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = rel.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }

                                    // Small Circular Rating Badge on Poster Corner (Requirement 11)
                                    if (rel.rating > 0.0) {
                                        CircularRatingBadge(
                                            rating = rel.rating,
                                            size = 26.dp,
                                            strokeWidth = 2.dp,
                                            textSize = 8,
                                            showPercentSign = false,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
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

/**
 * Compact, modern episode card for series.
 */
@Composable
fun ModernEpisodeCard(
    media: MediaEntity,
    episode: EpisodeEntity,
    viewModel: MainViewModel,
    epHistory: WatchHistoryEntity? = null,
    isResolvingDownload: Boolean = false,
    isAuthenticated: Boolean,
    onPlayClick: () -> Unit,
    onShowInfo: (String) -> Unit,
    onShareClick: (EpisodeEntity) -> Unit = {},
    onSetDownloadMetadata: (String, String, String, String, String?, Int?, Int?) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val watchProgress = epHistory?.progressPercent ?: 0f
    val isWatched = watchProgress >= 90f
    val epNumberFormatted = remember(episode.episodeNumber) {
        String.format(java.util.Locale.US, "%02d", episode.episodeNumber)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "ep_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    android.util.Log.i("RONYCINE_DIAG", "[PLAYER_CLICK] Usuário clicou em Assistir no Episódio ${episode.episodeNumber}. tmdbId=${media.tmdbId}")
                    onPlayClick()
                }
            )
            .testTag("episode_item_${episode.episodeNumber}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isWatched) Color(0xFF131317) else DarkSurface
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (isWatched) BrandRed.copy(alpha = 0.25f) else CardBorder.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail or EP Badge
                if (!episode.stillPath.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://image.tmdb.org/t/p/w185${episode.stillPath}")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Episódio $epNumberFormatted",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = BrandRed,
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                        ) {
                            Text(
                                text = "EP $epNumberFormatted",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E1E24))
                            .border(1.dp, CardBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "EP\n$epNumberFormatted",
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Subtitle Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = episode.title.ifBlank { "Episódio ${episode.episodeNumber}" },
                            color = if (isWatched) Color(0xFFC0C0C0) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isWatched) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Assistido",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val durationText = if (episode.duration.isNotBlank()) " • ${episode.duration}" else ""
                    val progressLabel = if (watchProgress > 5f && !isWatched) " • ${watchProgress.toInt()}% assistido" else ""
                    Text(
                        text = "Temp. ${episode.seasonNumber} • Ep. ${episode.episodeNumber}$durationText$progressLabel",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                
                // Download Button (Mini)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { 
                            viewModel.requireAuthenticationForDownload(
                                isAuthenticated = isAuthenticated,
                                pendingIntent = PendingDownloadIntent(
                                    type = "tv",
                                    tmdbId = media.tmdbId,
                                    season = episode.seasonNumber,
                                    episode = episode.episodeNumber,
                                    mediaTitle = "${media.title} S${episode.seasonNumber}E${episode.episodeNumber}"
                                ),
                                onNavigateToLogin = onNavigateToLogin,
                                onAlreadyAuthenticated = {
                                    onSetDownloadMetadata(
                                        "tv",
                                        media.tmdbId.toString(),
                                        media.title,
                                        "S${episode.seasonNumber}E${episode.episodeNumber}",
                                        media.posterPath,
                                        episode.seasonNumber,
                                        episode.episodeNumber
                                    )

                                    viewModel.resolveDownload(
                                        type = "tv",
                                        tmdbId = media.tmdbId,
                                        season = episode.seasonNumber,
                                        episode = episode.episodeNumber,
                                        mediaTitle = "${media.title} S${episode.seasonNumber}E${episode.episodeNumber}"
                                    )
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isResolvingDownload) {
                        RonycineSmileLoader(color = Color(0xFF38BDF8), size = 14.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Baixar",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Share Episode Button (Mini)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onShareClick(episode) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Partilhar Episódio",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Actions: Mini Play Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isWatched) Color(0xFF222226) else BrandRed.copy(alpha = 0.18f))
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Assistir",
                        tint = if (isWatched) Color.LightGray else BrandRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Progress bar at bottom of card
            if (watchProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF222222))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (watchProgress / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(if (isWatched) Color(0xFF34D399) else BrandRed)
                    )
                }
            }
        }
    }
}

@Composable
private fun RestrictedAccessScreen(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = BrandRed.copy(alpha = 0.1f),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "CONTEÚDO RESTRITO",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Este conteúdo não é permitido para o Perfil Infantil ativado.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = "VOLTAR",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DetailScreenSkeleton(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Backdrop Skeleton Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color(0xFF1E1E24).copy(alpha = alpha))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    DarkBackground
                                )
                            )
                        )
                )

                // Functional Back Arrow Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, CardBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("detail_skeleton_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Poster & Meta Skeleton Block
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Poster Box Skeleton
                    Box(
                        modifier = Modifier
                            .width(115.dp)
                            .height(165.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF22222A).copy(alpha = alpha))
                    )

                    // Title + Badges Skeleton
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF282832).copy(alpha = alpha))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF22222A).copy(alpha = alpha))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp, 20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF22222A).copy(alpha = alpha))
                            )
                            Box(
                                modifier = Modifier
                                    .size(50.dp, 20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF22222A).copy(alpha = alpha))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons Skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandRed.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF22222A).copy(alpha = alpha))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Synopsis Lines Skeleton
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF22222A).copy(alpha = alpha))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF22222A).copy(alpha = alpha))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF22222A).copy(alpha = alpha))
                    )
                }
            }
        }
    }
}
