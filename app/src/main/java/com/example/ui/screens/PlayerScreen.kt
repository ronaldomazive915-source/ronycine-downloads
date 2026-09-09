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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * Elegant circular TMDB rating bubble for PlayerScreen.
 * Displays score with a circular arc colored by score value:
 * - Green (>= 7.5)
 * - Amber (6.0 - 7.4)
 * - Red (< 6.0)
 */
@Composable
fun PlayerCircularRatingBubble(
    rating: Double,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 30.dp
) {
    if (rating <= 0.0) return

    val formattedScore = remember(rating) { String.format(java.util.Locale.US, "%.1f", rating) }
    val percentage = remember(rating) { (rating * 10.0).coerceIn(0.0, 100.0) }
    val progress = (percentage / 100f).toFloat()

    val ratingColor = when {
        percentage >= 75 -> Color(0xFF10B981) // Emerald Green
        percentage >= 60 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444)             // Red
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF14141A))
            .border(0.8.dp, Color(0xFF282834), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 2.dp)) {
            val strokePx = 2.dp.toPx()
            val canvasSize = this.size.minDimension
            val radius = (canvasSize - strokePx) / 2f

            // Background Track
            drawCircle(
                color = ratingColor.copy(alpha = 0.22f),
                radius = radius,
                style = Stroke(width = strokePx)
            )

            // Progress Arc
            drawArc(
                color = ratingColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Text(
            text = formattedScore,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )
    }
}

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
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()

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
    
    // Player Load ID for race condition prevention
    var playerLoadId by remember { mutableIntStateOf(0) }

    val currentAppLanguage by viewModel.appLanguage.collectAsState()
    val currentPreferredPlayerLanguage by viewModel.preferredPlayerLanguage.collectAsState()

    val defaultPlaybackMode = remember(currentAppLanguage, currentPreferredPlayerLanguage) {
        com.example.util.LanguageManager.resolvePreferredPlaybackMode(currentAppLanguage, currentPreferredPlayerLanguage)
    }

    // Audio Source Selection: DUBLADO (mgeb.top - default) / LEGENDADO (vidsrc)
    var selectedAudioSource by remember(defaultPlaybackMode) { mutableStateOf(defaultPlaybackMode) }
    
    // Fallback source index
    var fallbackSourceIndex by remember { mutableIntStateOf(0) }

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
    var ageGateAccepted by remember { mutableStateOf(false) }

    val playerSources by viewModel.playerSources.collectAsState()
    val playerConfig by viewModel.playerConfig.collectAsState()

    val originalEmbedUrl = remember(mediaType, tmdbId, currentSeasonNum, currentEpisodeNum, selectedAudioSource, playerSources, playerConfig, media, fallbackSourceIndex) {
        val currentMedia = media
        if (currentMedia != null) {
            if (selectedAudioSource == EmbedAudioSource.LEGENDADO) {
                // LEGENDADO uses VidSrc exclusively
                com.example.util.PlayerUtils.buildPlayerUrl(
                    provider = "vidsrc",
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                    episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                    audio = "Legendado",
                    dsLang = playerConfig.subtitledPlayer.defaultLanguage.ifBlank { "pt" }
                )
            } else {
                val audioLabel = "Dublado"
                
                // 1. Find the master default player from config
                val masterDefault = playerSources.find { it.id == playerConfig.defaultPlayerId && it.enabled }
                
                // 2. Filter compatible players by language and priority
                val compatibleSources = playerSources
                    .filter { it.enabled && it.language.equals(audioLabel, ignoreCase = true) }
                    .sortedBy { it.priority }
                
                // 3. Final Selection Logic
                val source = if (fallbackSourceIndex > 0) {
                    // If user is cycling through fallbacks, use the priority list
                    compatibleSources.getOrNull(fallbackSourceIndex)
                } else {
                    // Initial load: 
                    // A) Check if the master default player is compatible with current language
                    if (masterDefault != null && masterDefault.language.equals(audioLabel, ignoreCase = true)) {
                        masterDefault
                    } else {
                        // B) Otherwise use the first compatible player by priority
                        compatibleSources.firstOrNull()
                    }
                }
                
                if (source != null) {
                    android.util.Log.d("RONYCINE_PLAYER", "PLAY_SOURCE: Usando player '${source.name}' (ID: ${source.id}) [DefaultId: ${playerConfig.defaultPlayerId}, MegaEmbedPlayer: ${playerConfig.megaEmbed.player}]")
                    com.example.util.PlayerUtils.buildPlayerUrl(
                        source = source,
                        media = currentMedia,
                        season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                        episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                        megaEmbedConfig = playerConfig.megaEmbed
                    )
                } else {
                    // Fallback to legacy builder if absolutely no dynamic sources found
                    android.util.Log.w("RONYCINE_PLAYER", "PLAY_SOURCE: Nenhuma fonte dinâmica encontrada para $audioLabel. Usando fallback legando.")
                    EmbedUrlBuilder.buildUrl(
                        mediaType = mediaType,
                        tmdbId = tmdbId,
                        season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                        episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                        audioSource = EmbedAudioSource.DUBLADO,
                        player = playerConfig.megaEmbed.player,
                        color = playerConfig.megaEmbed.color
                    )
                }
            }
        } else {
            ""
        }
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
                if (isFullscreen) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
    val screenHeightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val playerHeight = remember(screenHeightDp, screenWidthDp, isLandscape) {
        if (isLandscape) {
            (screenHeightDp * 0.70f).coerceIn(200f, 320f).dp
        } else if (screenWidthDp >= 600) {
            (screenHeightDp * 0.50f).coerceIn(380f, 560f).dp
        } else {
            // Mobile: significantly increased height downward, occupying a large, cinematic portion of the screen (~46%)
            (screenHeightDp * 0.46f).coerceIn(330f, 440f).dp
        }
    }
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
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (isLocalDownloadAvailable) Color(0xFF065F46) else BrandRed,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            if (isLocalDownloadAvailable) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = if (isLocalDownloadAvailable) "OFFLINE" else "RONYCINE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = media?.title ?: "Reproduzindo",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("top_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Player",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2. UNIFIED EMBED PLAYER CONTAINER - RESPONSIVE AND PROPORTIONAL
            val currentEpisode = episodes.find { it.seasonNumber == currentSeasonNum && it.episodeNumber == currentEpisodeNum }
            val isRestricted = (media?.restricted18 == true) || (currentEpisode?.restricted18 == true)
            val showAgeGate = isRestricted && !ageGateAccepted

            Box(
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .testTag("fullscreen_embed_container")
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(if (showAgeGate) playerHeight.coerceAtLeast(300.dp) else playerHeight)
                        .padding(horizontal = 0.dp)
                },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(0.dp),
                    border = null,
                    modifier = Modifier.fillMaxSize(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    if (showAgeGate) {
                        // Age Gate Overlay - Compact & Centered
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .padding(10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "CONTEÚDO RESTRITO",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Este conteúdo é classificado como +18 e pode conter cenas impróprias para menores.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { ageGateAccepted = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text("SIM, TENHO 18 ANOS", fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
                                }

                                TextButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("VOLTAR", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        EmbedPlayer(
                            mediaType = mediaType,
                            tmdbId = tmdbId,
                            season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                            episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                            audioSource = selectedAudioSource,
                            customUrl = originalEmbedUrl, // Use the dynamically built URL
                            title = media?.title ?: "RONYCINE",
                            isFullscreen = isFullscreen,
                            autoplayEnabled = autoplayEnabled,
                            onToggleFullscreen = {
                                isFullscreen = !isFullscreen
                                showFullscreenControls = true
                                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_FULLSCREEN: changed isFullscreen=$isFullscreen")
                            },
                            onAudioSourceChange = { 
                                selectedAudioSource = it 
                                fallbackSourceIndex = 0 // Reset fallback index on manual language change
                                playerLoadId++
                            },
                            onPlaybackProgress = { currentTime, duration, event ->
                                if (isValidId && duration > 0) {
                                    val progress = (currentTime / duration).toFloat().coerceIn(0f, 1f)
                                    viewModel.saveWatchProgress(
                                        tmdbId = tmdbId,
                                        mediaType = mediaType,
                                        title = media?.title ?: "Conteúdo RONYCINE",
                                        posterPath = media?.posterPath,
                                        seasonNumber = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                                        episodeNumber = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                                        progressPercent = progress,
                                        positionMs = (currentTime * 1000).toLong(),
                                        totalDurationMs = (duration * 1000).toLong()
                                    )
                                }
                            },
                            onTryAgain = {
                                // Logic for fallback to next source
                                val audioLabel = if (selectedAudioSource == EmbedAudioSource.DUBLADO) "Dublado" else "Legendado"
                                val compatibleSourcesCount = playerSources.count { it.enabled && it.language.equals(audioLabel, ignoreCase = true) }
                                
                                if (fallbackSourceIndex + 1 < compatibleSourcesCount) {
                                    fallbackSourceIndex++
                                    android.util.Log.i("RONYCINE_PLAYER", "PLAYER_FALLBACK: Trying next source index=$fallbackSourceIndex")
                                } else {
                                    // Loop back or just retry current
                                    fallbackSourceIndex = 0
                                }
                                playerLoadId++
                            },
                            onWebViewCreated = { webView ->
                                activeWebView = webView
                            },
                            onUrlChanged = { url ->
                                currentWebViewUrl = url
                            },
                            playerLoadId = playerLoadId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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

                // SUB-PLAYER STREAMING UTILITY STRIP (Audio Source Dublado/Legendado + Quick Fullscreen & Download)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Audio selector (Dublado / Legendado)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ÁUDIO:",
                                color = Color(0xFF888892),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            val isDub = selectedAudioSource == EmbedAudioSource.DUBLADO
                            Surface(
                                color = if (isDub) BrandRed else Color(0xFF14141A),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (isDub) BrandRed else Color(0xFF282834)),
                                modifier = Modifier
                                    .clickable { selectedAudioSource = EmbedAudioSource.DUBLADO }
                                    .testTag("audio_dublado_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    if (isDub) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = "DUBLADO",
                                        color = if (isDub) Color.White else Color(0xFFCCCCCC),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val isLeg = selectedAudioSource == EmbedAudioSource.LEGENDADO
                            Surface(
                                color = if (isLeg) BrandRed else Color(0xFF14141A),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (isLeg) BrandRed else Color(0xFF282834)),
                                modifier = Modifier
                                    .clickable { selectedAudioSource = EmbedAudioSource.LEGENDADO }
                                    .testTag("audio_legendado_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    if (isLeg) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = "LEGENDADO",
                                        color = if (isLeg) Color.White else Color(0xFFCCCCCC),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Right utilities: Download & Fullscreen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (media != null) {
                                        if (currentDownload?.status == DownloadEntity.STATUS_DOWNLOADING) {
                                            viewModel.pauseDownload(currentDownload!!.id)
                                        } else if (currentDownload?.status == DownloadEntity.STATUS_PAUSED) {
                                            viewModel.resumeDownload(currentDownload!!.id)
                                        } else if (currentDownload?.status == DownloadEntity.STATUS_COMPLETED) {
                                            // Already downloaded
                                        } else {
                                            showDownloadOptionsSheet = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("player_control_download")
                            ) {
                                when (currentDownload?.status) {
                                    DownloadEntity.STATUS_DOWNLOADING -> {
                                        CircularProgressIndicator(
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DownloadEntity.STATUS_COMPLETED -> {
                                        Icon(Icons.Default.DownloadDone, contentDescription = "Offline", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                    }
                                    else -> {
                                        Icon(Icons.Default.Download, contentDescription = "Baixar", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            IconButton(
                                onClick = { isFullscreen = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("player_control_fullscreen")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tela Cheia",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 4. MAIN DETAILS SECTION: Título, Informações, Gêneros, Ações, Sinopse
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        // TÍTULO
                        Text(
                            text = media?.title ?: "Carregando...",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // INFORMAÇÕES DO TÍTULO: [8.1] 2022 4 Temporadas 16+ 4K
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            val ratingVal = media?.rating ?: 0.0
                            if (ratingVal > 0.0) {
                                PlayerCircularRatingBubble(rating = ratingVal)
                            }

                            Text(
                                text = media?.releaseYear?.takeIf { it.isNotBlank() } ?: "2025",
                                color = Color(0xFFB0B0B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            val durationOrSeasons = if (mediaType == "tv" || mediaType == "serie") {
                                val sCount = media?.seasonsCount ?: 1
                                "$sCount ${if (sCount > 1) "Temporadas" else "Temporada"}"
                            } else {
                                "${media?.durationMinutes ?: 120} min"
                            }
                            Text(
                                text = durationOrSeasons,
                                color = Color(0xFFB0B0B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Surface(
                                color = Color(0xFF202028),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF323240))
                            ) {
                                Text(
                                    text = if (media?.restricted18 == true) "18+" else "16+",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                color = BrandRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f))
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

                        // GÊNEROS (Chips compactos)
                        val parsedGenres = remember(media?.genres) {
                            val g = media?.genres
                            if (!g.isNullOrBlank()) {
                                g.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
                            } else {
                                listOf("AÇÃO", "AVENTURA", "FICÇÃO CIENTÍFICA", "DRAMA")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 14.dp)
                        ) {
                            parsedGenres.forEach { genre ->
                                Surface(
                                    color = Color(0xFF16161E),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Color(0xFF282834))
                                ) {
                                    Text(
                                        text = genre,
                                        color = Color(0xFFD1D5DB),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                    )
                                }
                            }
                        }

                        // AÇÕES PRINCIPAIS (Minha Lista, Gostei, Não gostei, Partilhar)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            // Minha Lista
                            OutlinedButton(
                                onClick = { viewModel.toggleMyList(tmdbId, mediaType) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isInMyList) BrandRed.copy(alpha = 0.12f) else Color(0xFF14141A),
                                    contentColor = if (isInMyList) BrandRed else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isInMyList) BrandRed else Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_my_list_btn")
                            ) {
                                Icon(
                                    imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isInMyList) BrandRed else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isInMyList) "Na Lista" else "Minha Lista",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Gostei
                            OutlinedButton(
                                onClick = {
                                    isLiked = !isLiked
                                    if (isLiked) isDisliked = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isLiked) BrandRed.copy(alpha = 0.12f) else Color(0xFF14141A),
                                    contentColor = if (isLiked) BrandRed else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isLiked) BrandRed else Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_like_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    tint = if (isLiked) BrandRed else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Gostei", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }

                            // Não gostei
                            OutlinedButton(
                                onClick = {
                                    isDisliked = !isDisliked
                                    if (isDisliked) isLiked = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isDisliked) BrandRed.copy(alpha = 0.12f) else Color(0xFF14141A),
                                    contentColor = if (isDisliked) BrandRed else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isDisliked) BrandRed else Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_dislike_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbDown,
                                    contentDescription = null,
                                    tint = if (isDisliked) BrandRed else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Não gostei", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }

                            // Partilhar
                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Assista ${media?.title ?: "este conteúdo"} no RONYCINE!")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    try {
                                        context.startActivity(shareIntent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF14141A),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_share_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Partilhar", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // SINOPSE
                        Text(
                            text = "SINOPSE",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val overviewText = media?.overview.takeIf { !it.isNullOrBlank() } ?: "Nenhuma sinopse disponível."
                        Text(
                            text = overviewText,
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (overviewText.length > 120) {
                            TextButton(
                                onClick = { isSynopsisExpanded = !isSynopsisExpanded },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.padding(top = 2.dp).testTag("toggle_synopsis_btn")
                            ) {
                                Text(
                                    text = if (isSynopsisExpanded) "Mostrar menos ↑" else "Mostrar mais ↓",
                                    color = BrandRed,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 5. EPISODES SECTION (Series Only)
                if (mediaType == "tv" || mediaType == "serie") {
                    item {
                        HorizontalDivider(
                            color = Color(0xFF1E1E26),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "EPISÓDIOS",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (hasPreviousEpisode || hasNextEpisode) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { navigateToPreviousEpisode() },
                                            enabled = hasPreviousEpisode,
                                            modifier = Modifier.size(28.dp).testTag("prev_episode_btn")
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious,
                                                contentDescription = "Episódio Anterior",
                                                tint = if (hasPreviousEpisode) Color.White else Color(0xFF555555),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Surface(
                                            color = BrandRed.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "T${currentSeasonNum}:E${currentEpisodeNum}",
                                                color = BrandRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { navigateToNextEpisode() },
                                            enabled = hasNextEpisode,
                                            modifier = Modifier.size(28.dp).testTag("next_episode_btn")
                                        ) {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = "Próximo Episódio",
                                                tint = if (hasNextEpisode) Color.White else Color(0xFF555555),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Season selector chips compactos
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 10.dp)
                            ) {
                                (1..totalSeasons).forEach { seasonNum ->
                                    val isSel = selectedSeason == seasonNum
                                    Surface(
                                        color = if (isSel) BrandRed else Color(0xFF14141A),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSel) BrandRed else Color(0xFF2E2E38)),
                                        modifier = Modifier
                                            .clickable { viewModel.loadSeasonEpisodes(tmdbId, seasonNum) }
                                            .testTag("season_chip_$seasonNum")
                                    ) {
                                        Text(
                                            text = "Temporada $seasonNum",
                                            color = if (isSel) Color.White else Color(0xFFCCCCCC),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
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
