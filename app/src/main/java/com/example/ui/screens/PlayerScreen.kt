package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.webkit.WebView
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.DownloadEntity
import com.example.ui.components.DownloadOptionsBottomSheet
import com.example.ui.components.EmbedAudioSource
import com.example.ui.components.EmbedPlayer
import com.example.ui.components.EmbedUrlBuilder
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RatingYellow
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    tmdbId: Int,
    mediaType: String,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((Int, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Load main media details & episodes
    LaunchedEffect(tmdbId, mediaType) {
        viewModel.loadMediaDetails(tmdbId, mediaType)
    }

    val media by viewModel.selectedMedia.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val allMediaList by viewModel.allMedia.collectAsState()
    val myList by viewModel.myList.collectAsState()

    val isInMyList = myList.any { it.tmdbId == tmdbId }

    // Active Episode Tracking
    var currentSeasonNum by remember(seasonNumber) { mutableIntStateOf(seasonNumber ?: 1) }
    var currentEpisodeNum by remember(episodeNumber) { mutableIntStateOf(episodeNumber ?: 1) }

    // Observe local download for current content
    val currentDownload by viewModel.observeDownloadForEpisode(
        tmdbId = tmdbId,
        seasonNumber = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
        episodeNumber = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null
    ).collectAsState(initial = null)

    val isLocalDownloadAvailable = currentDownload?.status == DownloadEntity.STATUS_COMPLETED &&
            !currentDownload?.localFilePath.isNullOrBlank() &&
            java.io.File(currentDownload?.localFilePath ?: "").exists()

    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }

    // Download options modal state
    var showDownloadOptionsSheet by remember { mutableStateOf(false) }

    // Synopsis expand state
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    // User reaction states
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }

    // Audio Source Selection: DUBLADO (mgeb.top - default) / LEGENDADO (nhdapi.com)
    var selectedAudioSource by remember { mutableStateOf(EmbedAudioSource.DUBLADO) }

    // Validate TMDB ID
    val isValidId = EmbedUrlBuilder.isValidTmdbId(tmdbId)

    // Automatically save watch history when playback starts or episode changes
    LaunchedEffect(tmdbId, mediaType, currentSeasonNum, currentEpisodeNum, media) {
        if (isValidId) {
            viewModel.saveWatchProgress(
                tmdbId = tmdbId,
                mediaType = mediaType,
                title = media?.title ?: "Conteúdo RONYCINE",
                posterPath = media?.posterPath,
                seasonNumber = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                episodeNumber = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                progressPercent = 0.5f,
                positionMs = 300000L,
                totalDurationMs = 600000L
            )
        }
    }

    // WebView reference and URL tracking to safely handle ad redirects
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var currentWebViewUrl by remember { mutableStateOf("") }

    val originalEmbedUrl = remember(mediaType, tmdbId, currentSeasonNum, currentEpisodeNum, selectedAudioSource) {
        EmbedUrlBuilder.buildUrl(
            mediaType = mediaType,
            tmdbId = tmdbId,
            season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
            episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
            audioSource = selectedAudioSource
        )
    }

    val handleBackNavigation = {
        val webView = activeWebView
        if (webView != null) {
            val canGoBack = webView.canGoBack()
            // Check if URL has drifted from the expected original video embed domain
            val isRedirected = currentWebViewUrl.isNotBlank() && 
                    currentWebViewUrl != "about:blank" && 
                    !currentWebViewUrl.startsWith(originalEmbedUrl.substringBefore("?")) &&
                    !currentWebViewUrl.contains("superflix") && 
                    !currentWebViewUrl.contains("embed")

            if (canGoBack) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Navigating back within WebView history")
                webView.goBack()
            } else if (isRedirected) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_RESTORE: Ad redirect detected, restoring original url=$originalEmbedUrl")
                webView.loadUrl(originalEmbedUrl)
            } else if (isFullscreen) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Exiting fullscreen mode")
                isFullscreen = false
            } else {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Exiting Player screen")
                onNavigateBack()
            }
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            onNavigateBack()
        }
    }

    // Handles the physical or gesture Back button on Android devices
    BackHandler(enabled = true) {
        handleBackNavigation()
    }

    var showFullscreenControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 4 seconds of inactivity in fullscreen mode
    LaunchedEffect(showFullscreenControls, isFullscreen) {
        if (isFullscreen && showFullscreenControls) {
            delay(4000L)
            showFullscreenControls = false
        }
    }

    // System Bars and Landscape Orientation Controller for Fullscreen Mode
    DisposableEffect(Unit) {
        onDispose {
            if (activity != null) {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val window = activity.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        if (activity != null) {
            try {
                activity.requestedOrientation = if (isFullscreen) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            } catch (_: Exception) {}
            try {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (isFullscreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (_: Exception) {}
        }
    }

    // Helper functions for Episode Navigation
    val seasonEpisodes = episodes.filter { it.seasonNumber == currentSeasonNum }
    val maxEpisodeInSeason = if (seasonEpisodes.isNotEmpty()) seasonEpisodes.maxOf { it.episodeNumber } else 10
    val totalSeasons = media?.seasonsCount ?: 1

    val hasPreviousEpisode = (mediaType == "tv" || mediaType == "serie") && (currentEpisodeNum > 1 || currentSeasonNum > 1)
    val isLastEpisodeOfSeason = currentEpisodeNum >= maxEpisodeInSeason
    val hasNextSeason = currentSeasonNum < totalSeasons
    val hasNextEpisode = (mediaType == "tv" || mediaType == "serie") && (!isLastEpisodeOfSeason || hasNextSeason)

    fun navigateToPreviousEpisode() {
        if (currentEpisodeNum > 1) {
            currentEpisodeNum -= 1
        } else if (currentSeasonNum > 1) {
            currentSeasonNum -= 1
            val prevSeasonEps = episodes.filter { it.seasonNumber == currentSeasonNum }
            currentEpisodeNum = if (prevSeasonEps.isNotEmpty()) prevSeasonEps.maxOf { it.episodeNumber } else 1
            viewModel.loadSeasonEpisodes(tmdbId, currentSeasonNum)
        }
    }

    fun navigateToNextEpisode() {
        if (!isLastEpisodeOfSeason) {
            currentEpisodeNum += 1
        } else if (hasNextSeason) {
            currentSeasonNum += 1
            currentEpisodeNum = 1
            viewModel.loadSeasonEpisodes(tmdbId, currentSeasonNum)
        }
    }

    // Recommendations (similar media)
    val recommendations = remember(allMediaList, tmdbId) {
        allMediaList.filter { it.tmdbId != tmdbId }.take(10)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val episodeColumns = remember(screenWidthDp) {
        when {
            screenWidthDp >= 1000 -> 4
            screenWidthDp >= 680 -> 3
            else -> 2
        }
    }

    // --- PROFESSIONAL STREAMING PLAYER SCREEN LAYOUT ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("clean_playmoz_player_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. PROFESSIONAL PLAYER HEADER BAR (Only visible in Portrait mode)
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (isLocalDownloadAvailable) Color(0xFF065F46) else BrandRed,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (isLocalDownloadAvailable) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isLocalDownloadAvailable) "OFFLINE" else "RONYCINE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = media?.title ?: "Reproduzindo",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("top_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Player",
                            tint = Color.White
                        )
                    }
                }
            }

            // 2. UNIFIED EMBED PLAYER CONTAINER - NEVER UNMOUNTED ACROSS FULLSCREEN TRANSITIONS
            Box(
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .testTag("fullscreen_embed_container")
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(horizontal = 4.dp)
                },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                    border = if (isFullscreen) null else androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxSize()
                ) {
                    EmbedPlayer(
                        mediaType = mediaType,
                        tmdbId = tmdbId,
                        season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                        episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                        audioSource = selectedAudioSource,
                        title = media?.title ?: "RONYCINE",
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = {
                            isFullscreen = !isFullscreen
                            showFullscreenControls = true
                            android.util.Log.i("RONYCINE_PLAYER", "PLAYER_FULLSCREEN: changed isFullscreen=$isFullscreen")
                        },
                        onAudioSourceChange = { selectedAudioSource = it },
                        onWebViewCreated = { webView ->
                            activeWebView = webView
                        },
                        onUrlChanged = { url ->
                            currentWebViewUrl = url
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 3. Scrollable Content below the Player in Portrait Mode
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(DarkBackground),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {

                // 2.5 DUBLADO / LEGENDADO AUDIO SOURCE SELECTOR
                item {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ÁUDIO:",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedAudioSource == EmbedAudioSource.DUBLADO,
                                    onClick = { selectedAudioSource = EmbedAudioSource.DUBLADO },
                                    label = {
                                        Text(
                                            "DUBLADO",
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedAudioSource == EmbedAudioSource.DUBLADO) FontWeight.Black else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (selectedAudioSource == EmbedAudioSource.DUBLADO) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandRed,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = Color.Black.copy(alpha = 0.5f),
                                        labelColor = Color.LightGray
                                    ),
                                    modifier = Modifier.testTag("audio_dublado_chip")
                                )

                                FilterChip(
                                    selected = selectedAudioSource == EmbedAudioSource.LEGENDADO,
                                    onClick = { selectedAudioSource = EmbedAudioSource.LEGENDADO },
                                    label = {
                                        Text(
                                            "LEGENDADO",
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedAudioSource == EmbedAudioSource.LEGENDADO) FontWeight.Black else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (selectedAudioSource == EmbedAudioSource.LEGENDADO) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandRed,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = Color.Black.copy(alpha = 0.5f),
                                        labelColor = Color.LightGray
                                    ),
                                    modifier = Modifier.testTag("audio_legendado_chip")
                                )
                            }
                        }
                    }
                }

                // 3. COMPACT CONTROLS BAR DIRECTLY BELOW PLAYER (← 📥 ⚙ ⛶)
                item {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Voltar
                            IconButton(
                                onClick = { handleBackNavigation() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_control_back")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = BrandRed
                                )
                            }

                            // Download quick status / action button
                            IconButton(
                                onClick = {
                                    if (media != null) {
                                        if (currentDownload?.status == DownloadEntity.STATUS_DOWNLOADING) {
                                            viewModel.pauseDownload(currentDownload!!.id)
                                        } else if (currentDownload?.status == DownloadEntity.STATUS_PAUSED) {
                                            viewModel.resumeDownload(currentDownload!!.id)
                                        } else if (currentDownload?.status == DownloadEntity.STATUS_COMPLETED) {
                                            // Already completed
                                        } else {
                                            showDownloadOptionsSheet = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_control_download")
                            ) {
                                when (currentDownload?.status) {
                                    DownloadEntity.STATUS_DOWNLOADING -> {
                                        CircularProgressIndicator(
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DownloadEntity.STATUS_PREPARING -> {
                                        CircularProgressIndicator(
                                            color = BrandRed,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DownloadEntity.STATUS_PAUSED -> {
                                        Icon(Icons.Default.Pause, contentDescription = "Pausado", tint = Color(0xFFFBBF24))
                                    }
                                    DownloadEntity.STATUS_COMPLETED -> {
                                        Icon(Icons.Default.DownloadDone, contentDescription = "Disponível Offline", tint = Color(0xFF34D399))
                                    }
                                    DownloadEntity.STATUS_ERROR -> {
                                        Icon(Icons.Default.Refresh, contentDescription = "Tentar Novamente", tint = Color(0xFFF87171))
                                    }
                                    DownloadEntity.STATUS_NOT_SUPPORTED -> {
                                        Icon(Icons.Default.Block, contentDescription = "Download Indisponível", tint = Color.Gray)
                                    }
                                    else -> {
                                        Icon(Icons.Default.Download, contentDescription = "Baixar", tint = Color.LightGray)
                                    }
                                }
                            }

                            // Center: Configurações / Opções
                            IconButton(
                                onClick = { /* Settings / Audio / Subtitles options */ },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_control_settings")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Configurações",
                                    tint = Color.LightGray
                                )
                            }

                            // Right: Tela Cheia
                            IconButton(
                                onClick = { isFullscreen = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_control_fullscreen")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tela Cheia",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // 4. EPISODE NAVIGATION BAR (Series Only)
                if (mediaType == "tv" || mediaType == "serie") {
                    item {
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = { navigateToPreviousEpisode() },
                                    enabled = hasPreviousEpisode,
                                    modifier = Modifier.testTag("prev_episode_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = null,
                                        tint = if (hasPreviousEpisode) Color.White else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Anterior", color = if (hasPreviousEpisode) Color.White else Color.Gray, fontSize = 12.sp)
                                }

                                Surface(
                                    color = BrandRed,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "T$currentSeasonNum:E$currentEpisodeNum",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                TextButton(
                                    onClick = { navigateToNextEpisode() },
                                    enabled = hasNextEpisode,
                                    modifier = Modifier.testTag("next_episode_btn")
                                ) {
                                    Text("Próximo", color = if (hasNextEpisode) Color.White else Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = null,
                                        tint = if (hasNextEpisode) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. CONTENT INFORMATION SECTION (Title, Rating, Year, Duration, Parental, Resolution)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = media?.title ?: "Carregando...",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            // Rating
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = RatingYellow,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = String.format("%.1f", media?.rating ?: 7.5),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Release Year
                            Text(
                                text = media?.releaseYear ?: "2025",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )

                            // Duration / Seasons
                            Text(
                                text = if (mediaType == "tv" || mediaType == "serie") "${media?.seasonsCount ?: 1} Temporadas" else "${media?.durationMinutes ?: 120} min",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )

                            // Parental Rating Badge
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = "16+",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Resolution Badge
                            Surface(
                                color = BrandRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "4K",
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Genres Badges
                        val sampleGenres = listOf("AÇÃO", "AVENTURA", "FICÇÃO CIENTÍFICA", "DRAMA")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp)
                        ) {
                            sampleGenres.forEach { genre ->
                                Surface(
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                                ) {
                                    Text(
                                        text = genre,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // 6. ACTION BUTTONS (Minha Lista, Gostei, Não gostei, Compartilhar)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            // Minha Lista
                            OutlinedButton(
                                onClick = { viewModel.toggleMyList(tmdbId, mediaType) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isInMyList) BrandRed else CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("player_my_list_btn")
                            ) {
                                Icon(
                                    imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isInMyList) BrandRed else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isInMyList) "Na Lista" else "Minha Lista", fontSize = 12.sp, maxLines = 1)
                            }

                            // Gostei
                            OutlinedButton(
                                onClick = {
                                    isLiked = !isLiked
                                    if (isLiked) isDisliked = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isLiked) BrandRed else Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isLiked) BrandRed else CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("player_like_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    tint = if (isLiked) BrandRed else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gostei", fontSize = 12.sp, maxLines = 1)
                            }

                            // Não gostei
                            OutlinedButton(
                                onClick = {
                                    isDisliked = !isDisliked
                                    if (isDisliked) isLiked = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isDisliked) BrandRed else Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDisliked) BrandRed else CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("player_dislike_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbDown,
                                    contentDescription = null,
                                    tint = if (isDisliked) BrandRed else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Não gostei", fontSize = 12.sp, maxLines = 1)
                            }

                            // Compartilhar
                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Assista ${media?.title ?: "este filme"} no RONYCINE!")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    try {
                                        context.startActivity(shareIntent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("player_share_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Partilhar", fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        // 7. SYNOPSIS SECTION WITH EXPAND/COLLAPSE
                        Text(
                            text = "SINOPSE",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val overviewText = media?.overview.takeIf { !it.isNullOrBlank() } ?: "Nenhuma sinopse disponível."
                        Text(
                            text = overviewText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { isSynopsisExpanded = !isSynopsisExpanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.testTag("toggle_synopsis_btn")
                        ) {
                            Text(
                                text = if (isSynopsisExpanded) "Mostrar menos ↑" else "Mostrar mais ↓",
                                color = BrandRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 8. EPISODES SECTION (Series Only)
                if (mediaType == "tv" || mediaType == "serie") {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "EPISÓDIOS",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Season selector chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 12.dp)
                            ) {
                                (1..totalSeasons).forEach { seasonNum ->
                                    FilterChip(
                                        selected = selectedSeason == seasonNum,
                                        onClick = { viewModel.loadSeasonEpisodes(tmdbId, seasonNum) },
                                        label = { Text("Temporada $seasonNum") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandRed,
                                            selectedLabelColor = Color.White,
                                            containerColor = DarkSurface,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }

                            // Horizontal Episodes Cards -> Replaced with Vertical Compact Grid
                            val currentSeasonEpisodes = episodes.filter { it.seasonNumber == selectedSeason }
                            if (currentSeasonEpisodes.isNotEmpty()) {
                                val chunkedEpisodes = remember(currentSeasonEpisodes, episodeColumns) {
                                    currentSeasonEpisodes.chunked(episodeColumns)
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chunkedEpisodes.forEach { rowEpisodes ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowEpisodes.forEach { ep ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    val isCurrentEp = (selectedSeason == currentSeasonNum && ep.episodeNumber == currentEpisodeNum)
                                                    val epNumberFormatted = String.format("%02d", ep.episodeNumber)
                                                    Card(
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isCurrentEp) BrandRed.copy(alpha = 0.15f) else DarkSurface
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            1.dp,
                                                            if (isCurrentEp) BrandRed else CardBorder
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                currentSeasonNum = selectedSeason
                                                                currentEpisodeNum = ep.episodeNumber
                                                            }
                                                            .testTag("episode_card_${ep.episodeNumber}")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // Left block: compact number
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(if (isCurrentEp) BrandRed else Color(0xFF222222))
                                                                    .border(1.dp, if (isCurrentEp) BrandRed else CardBorder, RoundedCornerShape(4.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = epNumberFormatted,
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 11.5.sp
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(
                                                                    text = ep.title.ifBlank { "Episódio ${ep.episodeNumber}" },
                                                                    color = Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                
                                                                val durationStr = if (ep.duration.isNotBlank()) ep.duration else "45 min"
                                                                Text(
                                                                    text = "S${String.format("%02d", ep.seasonNumber)} · $durationStr",
                                                                    color = if (isCurrentEp) BrandRed else Color.Gray,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            val emptySlots = episodeColumns - rowEpisodes.size
                                            repeat(emptySlots) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Carregando episódios...",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // 9. RECOMMENDATIONS SECTION (VOCÊ TAMBÉM PODE GOSTAR)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "VOCÊ TAMBÉM PODE GOSTAR",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(recommendations) { rec ->
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable {
                                            if (onNavigateToDetail != null) {
                                                onNavigateToDetail(rec.tmdbId, rec.mediaType)
                                            }
                                        }
                                        .testTag("recommendation_item_${rec.tmdbId}")
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(rec.posterPath)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = rec.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = rec.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = RatingYellow,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = String.format("%.1f", rec.rating),
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "• ${rec.releaseYear}",
                                            color = Color.LightGray,
                                            fontSize = 11.sp
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

    // Modal de opções de download (Interno ou Externo com 1DM, ADM, Chooser)
    if (showDownloadOptionsSheet && media != null) {
        val currentEp = if (mediaType == "tv" || mediaType == "serie") {
            episodes.firstOrNull { it.seasonNumber == currentSeasonNum && it.episodeNumber == currentEpisodeNum }
        } else null

        DownloadOptionsBottomSheet(
            media = media!!,
            episode = currentEp,
            customUrl = currentEp?.videoUrl,
            onDismiss = { showDownloadOptionsSheet = false },
            onStartInternalDownload = { m, ep, url ->
                if (ep != null) {
                    viewModel.startEpisodeDownload(m, ep, url)
                } else {
                    viewModel.startMovieDownload(m, url)
                }
            }
        )
    }
}
