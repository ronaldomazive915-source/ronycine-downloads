package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.download.DownloadResolutionResult
import com.example.data.download.DownloadResolver
import com.example.data.download.SourceValidationResult
import com.example.data.download.SourceValidator
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import com.example.ui.viewmodel.MainViewModel
import com.example.util.AppDownloadManager
import com.example.util.DownloadUtils
import com.example.util.ExternalDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesEpisodeDownloadBottomSheet(
    media: MediaEntity,
    viewModel: MainViewModel,
    initialSeason: Int = 1,
    initialEpisodeNumber: Int? = null,
    onDismiss: () -> Unit,
    onNavigateToDownloads: (() -> Unit)? = null,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val firebaseService = remember { com.example.data.remote.FirebaseService.getInstance(context.applicationContext as android.app.Application) }
    val currentUserState by firebaseService.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val downloadManager = remember { AppDownloadManager.getInstance(context) }
    val allDownloads by downloadManager.allDownloads.collectAsState()

    // Real Seasons Count from TV Series
    val totalSeasons = remember(media.seasonsCount) {
        if (media.seasonsCount > 0) media.seasonsCount else 1
    }

    var selectedSeason by remember {
        mutableIntStateOf(initialSeason.coerceIn(1, totalSeasons))
    }

    // Episode state for current season
    var episodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var selectedEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }
    var episodeLoadError by remember { mutableStateOf<String?>(null) }

    // External download managers availability
    val idmInstalled = remember { ExternalDownloadManager.is1DmInstalled(context) }
    val admInstalled = remember { ExternalDownloadManager.isAdmInstalled(context) }

    // Episode source resolution state
    var isResolvingSource by remember { mutableStateOf(false) }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var resolvedFileName by remember { mutableStateOf<String?>(null) }
    var validationResult by remember { mutableStateOf<SourceValidationResult?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var isValidatingSource by remember { mutableStateOf(false) }

    val downloadResolver = remember { DownloadResolver(context) }

    // Unique task id for current selected episode: tv_{tmdbId}_s{season}_e{episode}
    val currentTaskId = remember(media.tmdbId, selectedSeason, selectedEpisode?.episodeNumber) {
        val epNum = selectedEpisode?.episodeNumber
        if (epNum != null) {
            AppDownloadManager.generateTaskId("tv", media.tmdbId.toString(), selectedSeason, epNum)
        } else null
    }

    val existingEpisodeTask = allDownloads.find { it.id == currentTaskId }

    // Load episodes whenever season changes
    LaunchedEffect(selectedSeason) {
        // 1. Reset previous episode selection and resolution
        selectedEpisode = null
        resolvedUrl = null
        resolvedFileName = null
        validationResult = null
        sourceError = null
        isLoadingEpisodes = true
        episodeLoadError = null
        episodes = emptyList()

        val seasonJob = scope.launch {
            // Read from Room database Flow
            viewModel.mediaRepository.getEpisodesForSeason(media.tmdbId, selectedSeason).collect { epList ->
                if (epList.isNotEmpty()) {
                    episodes = epList
                    isLoadingEpisodes = false
                    episodeLoadError = null

                    // Auto-select initial or first episode if none selected yet
                    if (selectedEpisode == null) {
                        val target = if (initialEpisodeNumber != null && selectedSeason == initialSeason) {
                            epList.find { it.episodeNumber == initialEpisodeNumber } ?: epList.firstOrNull()
                        } else {
                            epList.firstOrNull()
                        }
                        selectedEpisode = target
                    }
                }
            }
        }

        // Fetch from API in background if needed
        scope.launch(Dispatchers.IO) {
            try {
                viewModel.mediaRepository.fetchAndStoreEpisodes(media.tmdbId, selectedSeason)
            } catch (_: Exception) {}
        }

        // Safety timeout if database is empty and API is slow
        scope.launch {
            delay(3500)
            if (episodes.isEmpty()) {
                isLoadingEpisodes = false
                episodeLoadError = "Não foi possível carregar os episódios desta temporada."
            }
        }
    }

    // Resolve source whenever selected episode changes
    LaunchedEffect(selectedEpisode?.seasonNumber, selectedEpisode?.episodeNumber, currentUserState) {
        if (currentUserState == null) {
            isResolvingSource = false
            resolvedUrl = null
            sourceError = null
            return@LaunchedEffect
        }
        val ep = selectedEpisode
        if (ep == null) {
            resolvedUrl = null
            resolvedFileName = null
            validationResult = null
            sourceError = null
            isResolvingSource = false
            return@LaunchedEffect
        }

        isResolvingSource = true
        sourceError = null
        resolvedUrl = null
        resolvedFileName = null
        validationResult = null

        val result = withContext(Dispatchers.IO) {
            try {
                downloadResolver.resolve(
                    type = "tv",
                    tmdbId = media.tmdbId,
                    season = ep.seasonNumber,
                    episode = ep.episodeNumber,
                    mediaTitle = "${media.title} S${ep.seasonNumber.toString().padStart(2, '0')}E${ep.episodeNumber.toString().padStart(2, '0')}"
                )
            } catch (e: Exception) {
                DownloadResolutionResult.Error("ERROR", "Erro ao conectar com as fontes do episódio.")
            }
        }

        when (result) {
            is DownloadResolutionResult.Success -> {
                resolvedUrl = result.url
                resolvedFileName = result.fileName
                isResolvingSource = false

                // Validate source with SourceValidator
                isValidatingSource = true
                val validation = withContext(Dispatchers.IO) {
                    SourceValidator.validate(result.url)
                }
                validationResult = validation
                isValidatingSource = false
            }
            is DownloadResolutionResult.Error -> {
                isResolvingSource = false
                sourceError = "Este episódio não possui uma fonte compatível para download no momento."
            }
        }
    }

    val isDirect = (validationResult as? SourceValidationResult.Success)?.isDirectMedia == true
    val detectedSize = (validationResult as? SourceValidationResult.Success)?.contentLength ?: -1L
    val finalDownloadUrl = (validationResult as? SourceValidationResult.Success)?.resolvedUrl ?: resolvedUrl ?: ""
    val finalFileName = resolvedFileName ?: "${media.title} S${selectedSeason}E${selectedEpisode?.episodeNumber ?: 1}.mp4"

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F14),
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF2E2E3A), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Header with Title and Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6).copy(alpha = 0.25f), Color(0xFF6366F1).copy(alpha = 0.15f))
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "BAIXAR EPISÓDIO",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Escolha a temporada e o episódio que deseja baixar.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E28))
                        .testTag("series_download_sheet_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. Selected Media Banner Card (Series Identity)
            Surface(
                color = Color(0xFF161622),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF222232)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!media.posterPath.isNullOrBlank()) {
                        AsyncImage(
                            model = media.posterPath,
                            contentDescription = media.title,
                            modifier = Modifier
                                .width(36.dp)
                                .height(52.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF20202E)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SÉRIE",
                                    color = Color(0xFFC4B5FD),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            if (!media.releaseYear.isNullOrBlank()) {
                                Text(
                                    text = "• ${media.releaseYear}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Text(
                                text = "• $totalSeasons ${if (totalSeasons > 1) "Temporadas" else "Temporada"}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 3. Temporada Selector
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .background(Color(0xFF8B5CF6), RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = "TEMPORADA",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    (1..totalSeasons).forEach { sNum ->
                        val isSelected = selectedSeason == sNum
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (selectedSeason != sNum) {
                                    selectedSeason = sNum
                                }
                            },
                            label = {
                                Text(
                                    text = "Temporada $sNum",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF8B5CF6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF161622),
                                labelColor = Color.LightGray
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF242436)
                            )
                        )
                    }
                }
            }

            // 4. Episódio Selector
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .background(Color(0xFF38BDF8), RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = if (episodes.isNotEmpty()) "EPISÓDIOS (${episodes.size})" else "EPISÓDIOS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (isLoadingEpisodes) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF8B5CF6),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Carregando episódios da Temporada $selectedSeason...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                } else if (episodeLoadError != null && episodes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = episodeLoadError ?: "Não foi possível carregar os episódios.",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                isLoadingEpisodes = true
                                episodeLoadError = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        viewModel.mediaRepository.fetchAndStoreEpisodes(media.tmdbId, selectedSeason)
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Tentar novamente", fontSize = 11.sp, color = Color.White)
                        }
                    }
                } else if (episodes.isNotEmpty()) {
                    // Fast Episode Number Chips [ E1 ] [ E2 ] [ E3 ]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp)
                    ) {
                        episodes.forEach { ep ->
                            val isChosen = selectedEpisode?.episodeNumber == ep.episodeNumber
                            Surface(
                                onClick = { selectedEpisode = ep },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) Color(0xFF8B5CF6) else Color(0xFF161622),
                                border = BorderStroke(1.dp, if (isChosen) Color(0xFFA78BFA) else Color(0xFF242436)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "E${ep.episodeNumber}",
                                        color = if (isChosen) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Selected Episode Summary (CONTEÚDO SELECIONADO)
            if (selectedEpisode != null) {
                val ep = selectedEpisode!!
                Surface(
                    color = Color(0xFF13131D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222232)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(10.dp)
                                    .background(Color(0xFF10B981), RoundedCornerShape(1.dp))
                            )
                            Text(
                                text = "CONTEÚDO SELECIONADO",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!ep.stillPath.isNullOrBlank() || !media.posterPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = ep.stillPath ?: media.posterPath,
                                    contentDescription = ep.title,
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF20202E)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "Temporada $selectedSeason • Episódio ${ep.episodeNumber}",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = ep.title.ifBlank { "Episódio ${ep.episodeNumber}" },
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Episode source status indicator
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isResolvingSource || isValidatingSource) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = if (isResolvingSource) "Buscando fonte do episódio..." else "Verificando integridade da mídia...",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            } else if (sourceError != null) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = sourceError!!,
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp
                                )
                            } else if (resolvedUrl != null) {
                                val sourceLabel = when {
                                    isDirect -> if (detectedSize > 0) "MP4 Direto • ${DownloadUtils.formatFileSize(detectedSize)}" else "Vídeo Direto (MP4)"
                                    (validationResult as? SourceValidationResult.Success)?.isHlsStream == true -> "Transmissão HLS (Compatível com 1DM / ADM)"
                                    else -> "Stream Web"
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isDirect) Color(0xFF10B981) else Color(0xFF8B5CF6), CircleShape)
                                )
                                Text(
                                    text = sourceLabel,
                                    color = if (isDirect) Color(0xFF34D399) else Color(0xFFC4B5FD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 6. Active Download for this Episode (if in progress or completed)
            if (existingEpisodeTask != null &&
                (existingEpisodeTask.status == DownloadStatus.DOWNLOADING ||
                 existingEpisodeTask.status == DownloadStatus.PAUSED ||
                 existingEpisodeTask.status == DownloadStatus.QUEUED ||
                 existingEpisodeTask.status == DownloadStatus.COMPLETED)
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "DOWNLOAD DO EPISÓDIO ATIVO",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ActiveDownloadInlineCard(
                        task = existingEpisodeTask,
                        onPause = { downloadManager.pauseDownload(existingEpisodeTask.id) },
                        onResume = { downloadManager.resumeDownload(existingEpisodeTask.id) },
                        onCancel = { downloadManager.cancelDownload(existingEpisodeTask.id, deleteFile = true) }
                    )
                }
            }

            // 7. Download Options (OPÇÕES DE DOWNLOAD)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .background(Color(0xFF8B5CF6), RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = "OPÇÕES DE DOWNLOAD",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                val hasResolvedSource = !resolvedUrl.isNullOrBlank() && sourceError == null

                // Card 1: 1DM
                CompactDownloadOptionCard(
                    icon = Icons.Default.Download,
                    iconBg = Color(0xFF3B82F6).copy(alpha = 0.15f),
                    iconTint = Color(0xFF60A5FA),
                    title = "1DM",
                    description = "Download Manager",
                    isInstalled = idmInstalled,
                    onClick = {
                        viewModel.requireAuthentication(
                            isAuthenticated = currentUserState != null,
                            onNavigateToLogin = onNavigateToLogin,
                            onAlreadyAuthenticated = {
                                if (!hasResolvedSource) {
                                    Toast.makeText(context, "Aguarde a resolução da fonte do episódio...", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (idmInstalled) {
                                        ExternalDownloadManager.downloadWith1DM(context, finalDownloadUrl, finalFileName)
                                        onDismiss()
                                    } else {
                                        ExternalDownloadManager.openPlayStore(context, ExternalDownloadManager.PKG_1DM)
                                    }
                                }
                            }
                        )
                    }
                )

                // Card 2: ADM
                CompactDownloadOptionCard(
                    icon = Icons.Default.VerticalAlignBottom,
                    iconBg = Color(0xFF10B981).copy(alpha = 0.15f),
                    iconTint = Color(0xFF34D399),
                    title = "ADM",
                    description = "Advanced Download Manager",
                    isInstalled = admInstalled,
                    onClick = {
                        viewModel.requireAuthentication(
                            isAuthenticated = currentUserState != null,
                            onNavigateToLogin = onNavigateToLogin,
                            onAlreadyAuthenticated = {
                                if (!hasResolvedSource) {
                                    Toast.makeText(context, "Aguarde a resolução da fonte do episódio...", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (admInstalled) {
                                        ExternalDownloadManager.downloadWithADM(context, finalDownloadUrl, finalFileName)
                                        onDismiss()
                                    } else {
                                        ExternalDownloadManager.openPlayStore(context, ExternalDownloadManager.PKG_ADM)
                                    }
                                }
                            }
                        )
                    }
                )

                // Card 3: Outro aplicativo
                CompactDownloadOptionCard(
                    icon = Icons.Default.OpenInNew,
                    iconBg = Color(0xFF64748B).copy(alpha = 0.15f),
                    iconTint = Color(0xFF94A3B8),
                    title = "Outro aplicativo",
                    description = "Escolher aplicativo compatível",
                    isInstalled = null,
                    onClick = {
                        viewModel.requireAuthentication(
                            isAuthenticated = currentUserState != null,
                            onNavigateToLogin = onNavigateToLogin,
                            onAlreadyAuthenticated = {
                                if (!hasResolvedSource) {
                                    Toast.makeText(context, "Aguarde a resolução da fonte do episódio...", Toast.LENGTH_SHORT).show()
                                } else {
                                    ExternalDownloadManager.downloadWithSystemChooser(context, finalDownloadUrl, finalFileName)
                                    onDismiss()
                                }
                            }
                        )
                    }
                )

                // Card 4: Baixar no RONYCINE
                CompactDownloadOptionCard(
                    icon = Icons.Default.DownloadForOffline,
                    iconBg = Color(0xFF8B5CF6).copy(alpha = 0.20f),
                    iconTint = Color(0xFFA78BFA),
                    title = "Baixar no RONYCINE",
                    description = "Download interno do episódio para assistir offline",
                    isInstalled = null,
                    isRecommended = true,
                    statusNotice = if (existingEpisodeTask?.status == DownloadStatus.DOWNLOADING) "Baixando agora..."
                                   else if (existingEpisodeTask?.status == DownloadStatus.QUEUED) "Na fila"
                                   else if (existingEpisodeTask?.status == DownloadStatus.COMPLETED) "Concluído"
                                   else null,
                    onClick = {
                        viewModel.requireAuthentication(
                            isAuthenticated = currentUserState != null,
                            onNavigateToLogin = onNavigateToLogin,
                            onAlreadyAuthenticated = {
                                val ep = selectedEpisode
                                if (ep == null) {
                                    Toast.makeText(context, "Selecione uma temporada e um episódio antes de baixar.", Toast.LENGTH_SHORT).show()
                                } else if (!hasResolvedSource || finalDownloadUrl.isBlank()) {
                                    Toast.makeText(context, "Aguarde a resolução da fonte do episódio...", Toast.LENGTH_SHORT).show()
                                } else if (existingEpisodeTask?.status == DownloadStatus.COMPLETED) {
                                    Toast.makeText(context, "Este conteúdo já está disponível offline.", Toast.LENGTH_SHORT).show()
                                    onNavigateToDownloads?.invoke() ?: onDismiss()
                                } else if (existingEpisodeTask?.status == DownloadStatus.DOWNLOADING || existingEpisodeTask?.status == DownloadStatus.QUEUED) {
                                    Toast.makeText(context, "Este conteúdo já está sendo baixado.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val taskId = currentTaskId ?: AppDownloadManager.generateTaskId("tv", media.tmdbId.toString(), selectedSeason, ep.episodeNumber)
                                    val task = DownloadTask(
                                        id = taskId,
                                        title = media.title,
                                        subTitle = "T$selectedSeason • E${ep.episodeNumber} - ${ep.title}",
                                        posterPath = ep.stillPath ?: media.posterPath,
                                        url = finalDownloadUrl,
                                        fileName = finalFileName,
                                        mediaType = "tv",
                                        tmdbId = media.tmdbId.toString(),
                                        seasonNumber = selectedSeason,
                                        episodeNumber = ep.episodeNumber,
                                        status = DownloadStatus.QUEUED,
                                        totalSize = detectedSize.coerceAtLeast(0L),
                                        mimeType = (validationResult as? SourceValidationResult.Success)?.contentType ?: if (finalDownloadUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"
                                    )

                                    downloadManager.startDownload(task)
                                    Toast.makeText(context, "Download iniciado: T$selectedSeason E${ep.episodeNumber}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
            }

            // 8. Footer: Copy Link and Meus Downloads
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (finalDownloadUrl.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("URL do Episódio", finalDownloadUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link do episódio copiado!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Nenhuma URL disponível para copiar.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copiar Link",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (onNavigateToDownloads != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onNavigateToDownloads()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("series_download_sheet_view_all")
                    ) {
                        Text(
                            text = "MEUS DOWNLOADS",
                            color = Color(0xFFA78BFA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
