package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.example.ui.components.DownloadOptionsBottomSheet
import com.example.ui.components.ContentShareBottomSheet
import com.example.ui.viewmodel.DownloadState
import com.example.ui.components.EmbedAudioSource
import com.example.ui.components.EmbedPlayer
import com.example.ui.components.EmbedUrlBuilder
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.RatingYellow
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.PendingDownloadIntent
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

data class NextEpisodeTarget(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null
)

@Composable
fun PlayerScreen(
    tmdbId: Int,
    mediaType: String,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    viewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((Int, String) -> Unit)? = null,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Active Episode Tracking
    var currentSeasonNum by remember(seasonNumber) { mutableIntStateOf(seasonNumber ?: 1) }
    var currentEpisodeNum by remember(episodeNumber) { mutableIntStateOf(episodeNumber ?: 1) }

    // Load main media details & episodes
    LaunchedEffect(tmdbId, mediaType, currentSeasonNum) {
        android.util.Log.i("RONYCINE_DIAG", "[PLAYER_REQUEST_STARTED] PlayerScreen carregando detalhes. tmdbId=$tmdbId, mediaType=$mediaType")
        viewModel.loadMediaDetails(tmdbId, mediaType)
        if (mediaType == "tv" || mediaType == "serie") {
            viewModel.loadSeasonEpisodes(tmdbId, currentSeasonNum)
        }
    }

    val media by viewModel.selectedMedia.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val allMediaList by viewModel.allMedia.collectAsState()
    val myList by viewModel.myList.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val isAccessRestricted by viewModel.isAccessRestricted.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val pendingDownloadIntent by viewModel.pendingDownloadIntent.collectAsState()

    LaunchedEffect(pendingDownloadIntent, media, currentUser) {
        val intent = pendingDownloadIntent
        if (intent != null && media != null && intent.tmdbId == tmdbId && intent.isPlayer) {
            if (currentUser != null) {
                viewModel.resolveDownload(
                    type = mediaType,
                    tmdbId = tmdbId,
                    season = intent.season,
                    episode = intent.episode,
                    mediaTitle = intent.mediaTitle
                )
                viewModel.clearPendingDownloadIntent()
            }
        }
    }

    val isInMyList = myList.any { it.tmdbId == tmdbId }

    // Player Load ID & Audio Source Selection
    var playerLoadId by remember { mutableIntStateOf(0) }

    val currentAppLanguage by viewModel.appLanguage.collectAsState()
    val currentPreferredPlayerLanguage by viewModel.preferredPlayerLanguage.collectAsState()

    val defaultPlaybackMode = remember(currentAppLanguage, currentPreferredPlayerLanguage) {
        com.example.util.LanguageManager.resolvePreferredPlaybackMode(currentAppLanguage, currentPreferredPlayerLanguage)
    }

    var selectedAudioSource by remember(defaultPlaybackMode) { mutableStateOf(defaultPlaybackMode) }
    var selectedPlayerSourceId by remember { mutableStateOf<String?>(null) }
    var fallbackSourceIndex by remember { mutableIntStateOf(0) }

    // Next Episode Button State
    var showNextEpisodeButton by remember { mutableStateOf(false) }  // Shown during last 120s and on ended
    var nextEpisodeTarget by remember { mutableStateOf<NextEpisodeTarget?>(null) }
    var remainingSecondsUntilEnd by remember { mutableIntStateOf(0) }
    var currentVideoDuration by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(currentSeasonNum, currentEpisodeNum, playerLoadId, selectedAudioSource) {
        showNextEpisodeButton = false
        nextEpisodeTarget = null
        remainingSecondsUntilEnd = 0
    }

    val downloadState by viewModel.downloadState.collectAsState()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var downloadTargetUrl by remember { mutableStateOf("") }
    var downloadTargetFileName by remember { mutableStateOf("") }
    var showShareSheet by remember { mutableStateOf(false) }

    val activeEpisode = remember(episodes, currentSeasonNum, currentEpisodeNum) {
        episodes.firstOrNull { it.seasonNumber == currentSeasonNum && it.episodeNumber == currentEpisodeNum }
    }

    if (showShareSheet && media != null) {
        ContentShareBottomSheet(
            media = media!!,
            seasonNumber = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
            episodeNumber = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
            episodeTitle = activeEpisode?.title,
            onDismiss = { showShareSheet = false }
        )
    }

    if (showDownloadSheet) {
        DownloadOptionsBottomSheet(
            url = downloadTargetUrl,
            fileName = downloadTargetFileName,
            tmdbId = tmdbId.toString(),
            title = media?.title ?: "Conteúdo RONYCINE",
            subTitle = if (mediaType == "tv" || mediaType == "serie") "S${currentSeasonNum}E${currentEpisodeNum}" else null,
            posterPath = media?.posterPath,
            mediaType = mediaType,
            seasonNumber = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
            episodeNumber = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
            viewModel = viewModel,
            onDismiss = { 
                showDownloadSheet = false
                viewModel.resetDownloadState()
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
            val errMsg = (downloadState as DownloadState.Error).message
            android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.resetDownloadState()
        }
    }

    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }

    // Synopsis expand state
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    // User reaction states
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }

    // Validate TMDB ID
    val isValidId = EmbedUrlBuilder.isValidTmdbId(tmdbId)

    var initialResumePosition by remember { mutableDoubleStateOf(0.0) }
    var lastSavedTimeSeconds by remember { mutableDoubleStateOf(0.0) }
    var lastSavedTimestamp by remember { mutableLongStateOf(0L) }
    var currentVideoPosition by remember { mutableDoubleStateOf(0.0) }

    // Load real saved progress on entry or when episode changes
    LaunchedEffect(tmdbId, mediaType, currentSeasonNum, currentEpisodeNum) {
        if (isValidId) {
            val isTv = mediaType == "tv" || mediaType == "serie"
            val saved = viewModel.getWatchHistoryItemSync(
                tmdbId = tmdbId,
                mediaType = mediaType,
                seasonNumber = if (isTv) currentSeasonNum else null,
                episodeNumber = if (isTv) currentEpisodeNum else null
            )
            if (saved != null && saved.progressPercent < 95.0f) {
                val sec = if (saved.currentTimeSeconds > 0.0) {
                    saved.currentTimeSeconds
                } else if (saved.lastWatchedPositionMs > 0) {
                    saved.lastWatchedPositionMs / 1000.0
                } else 0.0

                if (sec > 2.0) {
                    initialResumePosition = sec
                    android.util.Log.i("CONTINUE WATCHING", "[CONTINUE WATCHING] Retomando em: $sec segundos (tmdbId=$tmdbId)")
                }
            }
        }
    }

    val persistProgress: (currentTime: Double, duration: Double, force: Boolean) -> Unit = { ct, dur, force ->
        if (isValidId && dur > 0.0 && !dur.isNaN() && !dur.isInfinite() && !ct.isNaN() && !ct.isInfinite()) {
            val now = System.currentTimeMillis()
            if (force || (now - lastSavedTimestamp >= 3000L) || Math.abs(ct - lastSavedTimeSeconds) >= 5.0) {
                lastSavedTimestamp = now
                lastSavedTimeSeconds = ct
                val rawPercentage = ((ct / dur) * 100.0).toFloat().coerceIn(0f, 100f)
                val isTv = mediaType == "tv" || mediaType == "serie"
                val contentId = if (isTv) "tv_${tmdbId}_s${currentSeasonNum}_e${currentEpisodeNum}" else "movie_${tmdbId}"
                val type = if (isTv) "episode" else "movie"

                android.util.Log.i(
                    "CONTINUE WATCHING",
                    """
                    [CONTINUE WATCHING]
                    contentId: $contentId
                    type: $type
                    tmdbId: $tmdbId
                    season: ${if (isTv) currentSeasonNum else "N/A"}
                    episode: ${if (isTv) currentEpisodeNum else "N/A"}
                    currentTime: $ct
                    duration: $dur
                    percentage: $rawPercentage%
                    updatedAt: $now
                    saveResult: SUCCESS
                    """.trimIndent()
                )

                viewModel.saveWatchProgress(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = media?.title ?: "Conteúdo RONYCINE",
                    posterPath = media?.posterPath,
                    seasonNumber = if (isTv) currentSeasonNum else null,
                    episodeNumber = if (isTv) currentEpisodeNum else null,
                    progressPercent = rawPercentage,
                    positionMs = (ct * 1000).toLong(),
                    totalDurationMs = (dur * 1000).toLong(),
                    currentTimeSeconds = ct,
                    durationSeconds = dur
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (currentVideoDuration > 0.0 && currentVideoPosition > 0.0) {
                persistProgress(currentVideoPosition, currentVideoDuration, true)
            }
        }
    }

    // WebView reference and URL tracking to safely handle ad redirects
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var currentWebViewUrl by remember { mutableStateOf("") }
    var ageGateAccepted by remember { mutableStateOf(false) }

    val playerSources by viewModel.playerSources.collectAsState()
    val playerConfig by viewModel.playerConfig.collectAsState()

    val accentColor = remember(playerConfig.megaEmbed.color) {
        try {
            if (playerConfig.megaEmbed.color.startsWith("#")) {
                Color(android.graphics.Color.parseColor(playerConfig.megaEmbed.color))
            } else if (playerConfig.megaEmbed.color.isNotBlank() && playerConfig.megaEmbed.color != "Padrão") {
                // Handle named colors if any, or just hex
                Color(android.graphics.Color.parseColor(playerConfig.megaEmbed.color))
            } else {
                Color(0xFFfb542b) // Ronycine orange default
            }
        } catch (e: Exception) {
            Color(0xFFfb542b)
        }
    }

    var attemptedPlayerIds by remember(tmdbId, mediaType, currentSeasonNum, currentEpisodeNum, selectedAudioSource) {
        mutableStateOf(setOf<String>())
    }

    val originalEmbedUrl = remember(mediaType, tmdbId, currentSeasonNum, currentEpisodeNum, selectedAudioSource, selectedPlayerSourceId, playerSources, playerConfig, fallbackSourceIndex, attemptedPlayerIds) {
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
            
            // 1. Find the master default player from config (excluding R2)
            val masterDefault = playerSources.find { it.id == playerConfig.defaultPlayerId && it.enabled && !it.id.equals("r2", ignoreCase = true) }
            
            // 2. Filter compatible players by language and priority (excluding R2)
            val compatibleSources = playerSources
                .filter { it.enabled && it.language.equals(audioLabel, ignoreCase = true) && !it.id.equals("r2", ignoreCase = true) }
                .sortedBy { it.priority }
            
            // 3. Selection Logic considering attemptedPlayerIds
            val unattempted = compatibleSources.filter { !attemptedPlayerIds.contains(it.id) }
            
            val source = if (selectedPlayerSourceId != null && !attemptedPlayerIds.contains(selectedPlayerSourceId)) {
                compatibleSources.find { it.id == selectedPlayerSourceId }
            } else if (unattempted.isNotEmpty()) {
                if (masterDefault != null && unattempted.any { it.id == masterDefault.id }) {
                    masterDefault
                } else {
                    unattempted.first()
                }
            } else if (compatibleSources.isNotEmpty()) {
                compatibleSources.first()
            } else {
                null
            }
            
            if (source != null) {
                android.util.Log.i("RONYCINE_DIAG", "[PLAYER_URL_CREATED] URL final decidida para o player '${source.name}' (ID: ${source.id})")
                android.util.Log.d("RONYCINE_PLAYER", "PLAY_SOURCE: Usando player '${source.name}' (ID: ${source.id}) [DefaultId: ${playerConfig.defaultPlayerId}, MegaEmbedPlayer: ${playerConfig.megaEmbed.player}]")
                com.example.util.PlayerUtils.buildPlayerUrl(
                    source = source,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                    episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                    megaEmbedConfig = playerConfig.megaEmbed
                )
            } else {
                // Fallback to legacy builder if absolutely no dynamic sources found
                android.util.Log.w("RONYCINE_PLAYER", "PLAY_SOURCE: Nenhuma fonte dinâmica encontrada para $audioLabel. Usando fallback padrão.")
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
    }

    // Requirement 2: Audit log before opening player
    LaunchedEffect(originalEmbedUrl, selectedPlayerSourceId, selectedAudioSource) {
        val audioLabel = if (selectedAudioSource == EmbedAudioSource.DUBLADO) "Dublado" else "Legendado"
        val masterDefault = playerSources.find { it.id == playerConfig.defaultPlayerId && it.enabled }
        val activeSource = playerSources.find { it.id == selectedPlayerSourceId }
            ?: playerSources.firstOrNull { it.enabled && it.language.equals(audioLabel, ignoreCase = true) }

        android.util.Log.i(
            "RONYCINE_PLAYER",
            "PLAYER_AUDIT: provider=${activeSource?.name ?: "MegaEmbed"}, defaultPlayer=${masterDefault?.id ?: playerConfig.defaultPlayerId}, enabled=${activeSource?.enabled ?: true}, tmdbId=$tmdbId, contentType=$mediaType, season=${if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else "N/A"}, episode=${if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else "N/A"}, generatedUrl=$originalEmbedUrl"
        )
    }

    val handleBackNavigation = {
        if (isFullscreen) {
            android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Exiting fullscreen mode without navigating away")
            isFullscreen = false
        } else {
            val webView = activeWebView
            val isRedirected = webView != null && currentWebViewUrl.isNotBlank() && 
                    currentWebViewUrl != "about:blank" && 
                    !currentWebViewUrl.startsWith(originalEmbedUrl.substringBefore("?")) &&
                    !currentWebViewUrl.contains("superflix") && 
                    !currentWebViewUrl.contains("embed")

            if (isRedirected && webView != null) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_RESTORE: Ad redirect detected, restoring original url=$originalEmbedUrl")
                webView.loadUrl(originalEmbedUrl)
            } else {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Exiting Player screen")
                onNavigateBack()
            }
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
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    fun computeNextEpisodeTarget(): NextEpisodeTarget? {
        if (mediaType != "tv" && mediaType != "serie") return null

        val currentSeasonEps = episodes.filter { it.seasonNumber == currentSeasonNum }
        
        if (currentSeasonEps.isNotEmpty()) {
            val maxEpInCurrentSeason = currentSeasonEps.maxOf { it.episodeNumber }
            if (currentEpisodeNum < maxEpInCurrentSeason) {
                val nextEpEntity = currentSeasonEps.find { it.episodeNumber == currentEpisodeNum + 1 }
                return NextEpisodeTarget(
                    seasonNumber = currentSeasonNum,
                    episodeNumber = currentEpisodeNum + 1,
                    title = nextEpEntity?.title
                )
            }
            
            // Check if there is a next season
            val totalSeasons = media?.seasonsCount ?: currentSeasonNum
            if (currentSeasonNum < totalSeasons) {
                return NextEpisodeTarget(
                    seasonNumber = currentSeasonNum + 1,
                    episodeNumber = 1,
                    title = "T${currentSeasonNum + 1} E1"
                )
            }
            
            // Last episode of last season -> No next episode exists
            return null
        } else {
            // Fallback if episode list isn't populated in memory yet
            val totalSeasons = media?.seasonsCount ?: 1
            val nextEpNumber = currentEpisodeNum + 1
            
            if (currentSeasonNum < totalSeasons) {
                return NextEpisodeTarget(
                    seasonNumber = currentSeasonNum,
                    episodeNumber = nextEpNumber
                )
            } else if (currentSeasonNum == totalSeasons) {
                return NextEpisodeTarget(
                    seasonNumber = currentSeasonNum,
                    episodeNumber = nextEpNumber
                )
            }
            return null
        }
    }

    // Recommendations (similar media)
    val recommendations = remember(allMediaList, tmdbId) {
        allMediaList.filter { it.tmdbId != tmdbId }.take(10)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val playerHeight = remember(screenHeightDp, screenWidthDp) {
        if (screenWidthDp >= 600) {
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

    // --- PROTECTION: RESTRICTED ACCESS FOR KIDS PROFILE ---
    if (isAccessRestricted || (activeProfile != null && activeProfile!!.isKidsProfile && media != null && !com.example.util.ContentAccessManager.canProfileAccessContent(activeProfile, media!!))) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .testTag("kids_restricted_player_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandRed.copy(alpha = 0.12f),
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueado",
                            tint = BrandRed,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "CONTEÚDO NÃO DISPONÍVEL",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Este filme ou série não está disponível no perfil infantil.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Voltar ao catálogo",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        return
    }

    // --- PROFESSIONAL STREAMING PLAYER SCREEN LAYOUT ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("clean_player_screen")
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
                        color = accentColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "RONYCINE",
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
                            initialPositionSeconds = initialResumePosition,
                            onToggleFullscreen = {
                                isFullscreen = !isFullscreen
                                showFullscreenControls = true
                                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_FULLSCREEN: changed isFullscreen=$isFullscreen")
                            },
                            onAudioSourceChange = { 
                                val mode = it.name.lowercase()
                                android.util.Log.i("RONYCINE_DIAG", "[AUDIO_TRACK_SELECTED] Usuário alterou áudio para: $mode")
                                android.util.Log.i("RONYCINE_DIAG", "[LANGUAGE_CHANGED] Nova preferência salva: $mode")
                                
                                viewModel.setPreferredPlayerLanguage(mode)
                                selectedAudioSource = it 
                                fallbackSourceIndex = 0 // Reset fallback index on manual language change
                                playerLoadId++
                            },
                            onPlaybackProgress = { currentTime, duration, event ->
                                if (isValidId && duration > 0 && !duration.isNaN() && !duration.isInfinite()) {
                                    if (currentVideoDuration == 0.0) {
                                        android.util.Log.i("RONYCINE_DIAG", "[VIDEO_METADATA_LOADED] Duração do vídeo: $duration")
                                        android.util.Log.i("RONYCINE_DIAG", "[VIDEO_CAN_PLAY] Player pronto para reprodução.")
                                    }
                                    if (currentVideoPosition == 0.0 && currentTime > 0.1) {
                                        android.util.Log.i("RONYCINE_DIAG", "[PLAYBACK_STARTED] Reprodução iniciada em: $currentTime")
                                    }

                                    currentVideoDuration = duration
                                    currentVideoPosition = currentTime
                                    
                                    if (event == "ended" || event == "completed") {
                                        persistProgress(duration, duration, true)
                                    } else if (event == "pause") {
                                        persistProgress(currentTime, duration, true)
                                    } else {
                                        persistProgress(currentTime, duration, false)
                                    }

                                    // REGRA PRINCIPAL: Botão Próximo Episódio nos últimos 2 minutos (120s)
                                    if (mediaType == "tv" || mediaType == "serie") {
                                        val timeRemaining = duration - currentTime
                                         
                                         if (timeRemaining <= 120.0 && timeRemaining >= 0.0) {
                                             val target = nextEpisodeTarget ?: computeNextEpisodeTarget()
                                             if (target != null) {
                                                 nextEpisodeTarget = target
                                                 showNextEpisodeButton = true
                                                 remainingSecondsUntilEnd = timeRemaining.toInt()
                                                 android.util.Log.d("RONYCINE_PLAYER", "SHOW_NEXT_BUTTON: remainingTime=${timeRemaining}s <= 120s, target=S${target.seasonNumber}E${target.episodeNumber}")
                                             } else {
                                                 showNextEpisodeButton = false
                                             }
                                         } else if (timeRemaining > 125.0) {
                                             showNextEpisodeButton = false
                                         }
                                     }
                                 }

                                 if (event == "ended" || event == "completed") {
                                     android.util.Log.i("RONYCINE_PLAYER", "ON_PLAYER_ENDED: mediaType=$mediaType, S$currentSeasonNum E$currentEpisodeNum")
                                     if (mediaType == "tv" || mediaType == "serie") {
                                         val target = nextEpisodeTarget ?: computeNextEpisodeTarget()
                                         if (target != null) {
                                             nextEpisodeTarget = target
                                             showNextEpisodeButton = true // Mantém botão visível ao encerrar
                                         }
                                     }
                                 }
                             },
                            onTryAgain = {
                                playerLoadId++
                            },
                            onTryAnotherPlayer = {
                                val audioLabel = if (selectedAudioSource == EmbedAudioSource.DUBLADO) "Dublado" else "Legendado"
                                val compatibleSources = playerSources.filter { 
                                    it.enabled && 
                                    it.language.equals(audioLabel, ignoreCase = true) && 
                                    !it.id.equals("r2", ignoreCase = true) 
                                }.sortedBy { it.priority }
                                
                                val currentSourceId = selectedPlayerSourceId ?: compatibleSources.firstOrNull()?.id
                                if (currentSourceId != null) {
                                    attemptedPlayerIds = attemptedPlayerIds + currentSourceId
                                }
                                
                                val nextUnattempted = compatibleSources.firstOrNull { !attemptedPlayerIds.contains(it.id) }
                                if (nextUnattempted != null) {
                                    selectedPlayerSourceId = nextUnattempted.id
                                    android.util.Log.i("RONYCINE_DIAG", "PLAYER_SWITCH: Mudando para provedor ${nextUnattempted.name} (${nextUnattempted.id})")
                                    android.widget.Toast.makeText(context, "Alternando para ${nextUnattempted.name}...", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    // NO AUTOMATIC SWITCH TO LEGENDADO
                                    // If we are here, all sources for the SELECTED language failed.
                                    android.util.Log.e("RONYCINE_DIAG", "PLAYER_FAILURE: Todas as fontes para $audioLabel foram esgotadas.")
                                    android.widget.Toast.makeText(context, "Não foi possível carregar fontes ${audioLabel.lowercase()}s. Tente novamente ou escolha legendado manualmente.", android.widget.Toast.LENGTH_LONG).show()
                                    
                                    // Optional: We can reset attemptedPlayerIds to allow the user to try again with the same sources
                                    attemptedPlayerIds = emptySet()
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

                // NEXT EPISODE BUTTON - Pequeno e Flutuante (Canto inferior direito)
                val currentTarget = nextEpisodeTarget
                if (!showAgeGate && showNextEpisodeButton && currentTarget != null && (mediaType == "tv" || mediaType == "serie")) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("next_episode_button_container"),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            onClick = {
                                showNextEpisodeButton = false
                                val target = nextEpisodeTarget ?: computeNextEpisodeTarget()
                                if (target != null) {
                                    val nextS = target.seasonNumber
                                    val nextE = target.episodeNumber
                                    if (nextS != currentSeasonNum) {
                                        currentSeasonNum = nextS
                                        viewModel.loadSeasonEpisodes(tmdbId, nextS)
                                    }
                                    currentEpisodeNum = nextE
                                    nextEpisodeTarget = null
                                    playerLoadId++
                                    android.util.Log.i("RONYCINE_PLAYER", "PLAY_NEXT_EPISODE_CLICK: Navigating to S${nextS} E${nextE}")
                                }
                            },
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.85f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .padding(
                                    end = 20.dp,
                                    bottom = if (isFullscreen) 32.dp else 24.dp
                                )
                                .testTag("next_episode_mini_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "PRÓXIMO EP.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.NavigateNext,
                                    contentDescription = "Próximo Episódio",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                                color = if (isDub) accentColor else Color(0xFF14141A),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (isDub) accentColor else Color(0xFF282834)),
                                modifier = Modifier
                                    .clickable { 
                                        if (selectedAudioSource != EmbedAudioSource.DUBLADO) {
                                            selectedAudioSource = EmbedAudioSource.DUBLADO
                                            fallbackSourceIndex = 0
                                            playerLoadId++
                                        }
                                    }
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
                                color = if (isLeg) accentColor else Color(0xFF14141A),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (isLeg) accentColor else Color(0xFF282834)),
                                modifier = Modifier
                                    .clickable { 
                                        if (selectedAudioSource != EmbedAudioSource.LEGENDADO) {
                                            selectedAudioSource = EmbedAudioSource.LEGENDADO
                                            fallbackSourceIndex = 0
                                            playerLoadId++
                                        }
                                    }
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

                            // Server/Source selector when multiple Dublado sources are available
                            val dubSources = remember(playerSources) {
                                playerSources.filter { it.enabled && it.language.equals("Dublado", ignoreCase = true) }.sortedBy { it.priority }
                            }
                            if (isDub && dubSources.size > 1) {
                                var showServerMenu by remember { mutableStateOf(false) }
                                val effectiveCurrentSourceId = selectedPlayerSourceId 
                                    ?: (if (dubSources.any { it.id == playerConfig.defaultPlayerId }) playerConfig.defaultPlayerId else dubSources.firstOrNull()?.id)
                                val activeSourceName = dubSources.find { it.id == effectiveCurrentSourceId }?.name 
                                    ?: "Servidor"
                                
                                Box {
                                    Surface(
                                        color = Color(0xFF1C1C26),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFF323246)),
                                        modifier = Modifier
                                            .clickable { showServerMenu = true }
                                            .testTag("player_source_selector_chip")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = activeSourceName,
                                                color = Color(0xFFE0E0E8),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Trocar servidor",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showServerMenu,
                                        onDismissRequest = { showServerMenu = false },
                                        modifier = Modifier.background(Color(0xFF181822))
                                    ) {
                                        dubSources.forEach { src ->
                                            val isCurrent = (src.id == effectiveCurrentSourceId)
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = src.name,
                                                            color = if (isCurrent) accentColor else Color.White,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 12.sp
                                                        )
                                                        if (isCurrent) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = accentColor,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedPlayerSourceId = src.id
                                                    fallbackSourceIndex = 0
                                                    playerLoadId++
                                                    showServerMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right utilities: Fullscreen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // [ ↓ Baixar ] (NOVO)
                            IconButton(
                                onClick = {
                                    val calculatedTitle = if (mediaType == "tv" || mediaType == "serie") {
                                        "${media?.title} S${currentSeasonNum}E${currentEpisodeNum}"
                                    } else {
                                        media?.title
                                    }
                                    viewModel.requireAuthenticationForDownload(
                                        isAuthenticated = currentUser != null,
                                        pendingIntent = PendingDownloadIntent(
                                            type = mediaType,
                                            tmdbId = tmdbId,
                                            season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                                            episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                                            mediaTitle = calculatedTitle,
                                            isPlayer = true
                                        ),
                                        onNavigateToLogin = onNavigateToLogin,
                                        onAlreadyAuthenticated = {
                                            if (media != null) {
                                                viewModel.resolveDownload(
                                                    type = mediaType,
                                                    tmdbId = tmdbId,
                                                    season = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                                                    episode = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                                                    mediaTitle = calculatedTitle
                                                )
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("player_control_download")
                            ) {
                                val isResolvingThis = downloadState is DownloadState.Resolving && 
                                    (downloadState as DownloadState.Resolving).tmdbId == tmdbId && 
                                    (downloadState as DownloadState.Resolving).season == (if (mediaType == "tv") currentSeasonNum else null) &&
                                    (downloadState as DownloadState.Resolving).episode == (if (mediaType == "tv") currentEpisodeNum else null)
                                
                                if (isResolvingThis) {
                                    RonycineSmileLoader(color = Color(0xFF38BDF8), size = 20.dp)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = "Baixar", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
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
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
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
                                onClick = { 
                                    viewModel.requireAuthentication(
                                        isAuthenticated = currentUser != null,
                                        onNavigateToLogin = onNavigateToLogin,
                                        pendingAction = com.example.ui.viewmodel.PendingAction.ToggleMyList(tmdbId, mediaType),
                                        onAlreadyAuthenticated = {
                                            viewModel.toggleMyList(tmdbId, mediaType)
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isInMyList) accentColor.copy(alpha = 0.12f) else Color(0xFF14141A),
                                    contentColor = if (isInMyList) accentColor else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isInMyList) accentColor else Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_my_list_btn")
                            ) {
                                Icon(
                                    imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isInMyList) accentColor else Color.White,
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
                                    viewModel.requireAuthentication(
                                        isAuthenticated = currentUser != null,
                                        onNavigateToLogin = onNavigateToLogin,
                                        pendingAction = com.example.ui.viewmodel.PendingAction.Reaction(tmdbId, mediaType, isLike = true),
                                        onAlreadyAuthenticated = {
                                            isLiked = !isLiked
                                            if (isLiked) isDisliked = false
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isLiked) accentColor.copy(alpha = 0.12f) else Color(0xFF14141A),
                                    contentColor = if (isLiked) accentColor else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isLiked) accentColor else Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_like_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    tint = if (isLiked) accentColor else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Gostei", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }

                            // Não gostei
                            OutlinedButton(
                                onClick = {
                                    viewModel.requireAuthentication(
                                        isAuthenticated = currentUser != null,
                                        onNavigateToLogin = onNavigateToLogin,
                                        pendingAction = com.example.ui.viewmodel.PendingAction.Reaction(tmdbId, mediaType, isLike = false),
                                        onAlreadyAuthenticated = {
                                            isDisliked = !isDisliked
                                            if (isDisliked) isLiked = false
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isDisliked) accentColor.copy(alpha = 0.12f) else Color(0xFF14141A),
                                    contentColor = if (isDisliked) accentColor else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isDisliked) accentColor else Color(0xFF2A2A36)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("player_dislike_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbDown,
                                    contentDescription = null,
                                    tint = if (isDisliked) accentColor else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Não gostei", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }

                            // Partilhar
                            OutlinedButton(
                                onClick = { showShareSheet = true },
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
                                    color = accentColor,
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
                                            color = accentColor.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "T${currentSeasonNum}:E${currentEpisodeNum}",
                                                color = accentColor,
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
                                        color = if (isSel) accentColor else Color(0xFF14141A),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSel) accentColor else Color(0xFF2E2E38)),
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
                                                            containerColor = if (isCurrentEp) accentColor.copy(alpha = 0.15f) else DarkSurface
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            1.dp,
                                                            if (isCurrentEp) accentColor else CardBorder
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
                                                                    .background(if (isCurrentEp) accentColor else Color(0xFF222222))
                                                                    .border(1.dp, if (isCurrentEp) accentColor else CardBorder, RoundedCornerShape(4.dp)),
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
                                                                    color = if (isCurrentEp) accentColor else Color.Gray,
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

}
