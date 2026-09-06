package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import coil.request.ImageRequest
import com.example.data.local.DownloadEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit,
    onNavigateToExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allDownloads by viewModel.allDownloads.collectAsState()
    var selectedFilter by remember { mutableStateOf("all") } // "all", "movie", "tv", "active"
    var itemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val filteredDownloads = remember(allDownloads, selectedFilter) {
        when (selectedFilter) {
            "movie" -> allDownloads.filter { it.mediaType == "movie" }
            "tv" -> allDownloads.filter { it.mediaType == "tv" || it.mediaType == "serie" }
            "active" -> allDownloads.filter {
                it.status == DownloadEntity.STATUS_DOWNLOADING ||
                it.status == DownloadEntity.STATUS_PREPARING ||
                it.status == DownloadEntity.STATUS_PAUSED ||
                it.status == DownloadEntity.STATUS_PENDING
            }
            else -> allDownloads
        }
    }

    val usedBytes = remember(allDownloads) { viewModel.getUsedStorageBytes() }
    val freeBytes = remember(allDownloads) { viewModel.getAvailableStorageBytes() }
    val totalBytes = remember(allDownloads) { viewModel.getTotalStorageBytes() }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    // Delete single item confirmation dialog
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text("Excluir Download", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja excluir o download de \"${target.title}\"${if (target.episodeTitle != null) " - ${target.episodeTitle}" else ""}? O arquivo de vídeo local será removido.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownload(target.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            }
        )
    }

    // Clear all downloads confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Excluir Todos os Downloads", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza que deseja apagar todos os vídeos baixados no RONYCINE? Esta ação liberará ${formatBytes(usedBytes)} de espaço.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Excluir Tudo", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("downloads_screen")
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("downloads_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📥 Meus Downloads",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            if (allDownloads.isNotEmpty()) {
                IconButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier.testTag("downloads_clear_all_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Limpar todos os downloads",
                        tint = Color.LightGray
                    )
                }
            }
        }

        // --- Storage Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Armazenamento",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Usado: ${formatBytes(usedBytes)}",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Storage bar
                val usedRatio = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { usedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BrandRed,
                    trackColor = Color(0xFF334155),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RONYCINE Offline: ${formatBytes(usedBytes)}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Livre no Aparelho: ${formatBytes(freeBytes)}",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Filter Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "all",
                onClick = { selectedFilter = "all" },
                label = { Text("Todos (${allDownloads.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandRed,
                    selectedLabelColor = Color.White,
                    containerColor = DarkSurface,
                    labelColor = Color.LightGray
                )
            )

            val movieCount = allDownloads.count { it.mediaType == "movie" }
            if (movieCount > 0) {
                FilterChip(
                    selected = selectedFilter == "movie",
                    onClick = { selectedFilter = "movie" },
                    label = { Text("Filmes ($movieCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandRed,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurface,
                        labelColor = Color.LightGray
                    )
                )
            }

            val seriesCount = allDownloads.count { it.mediaType == "tv" || it.mediaType == "serie" }
            if (seriesCount > 0) {
                FilterChip(
                    selected = selectedFilter == "tv",
                    onClick = { selectedFilter = "tv" },
                    label = { Text("Séries ($seriesCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandRed,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurface,
                        labelColor = Color.LightGray
                    )
                )
            }

            val activeCount = allDownloads.count {
                it.status == DownloadEntity.STATUS_DOWNLOADING ||
                it.status == DownloadEntity.STATUS_PREPARING ||
                it.status == DownloadEntity.STATUS_PAUSED ||
                it.status == DownloadEntity.STATUS_PENDING
            }
            if (activeCount > 0) {
                FilterChip(
                    selected = selectedFilter == "active",
                    onClick = { selectedFilter = "active" },
                    label = { Text("Baixando ($activeCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF38BDF8),
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurface,
                        labelColor = Color.LightGray
                    )
                )
            }
        }

        // --- Downloads List ---
        if (filteredDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, CardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (selectedFilter == "all") "Nenhum download encontrado" else "Nenhum item nesta categoria",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Baixe filmes e séries para assistir quando e onde quiser, mesmo sem internet.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onNavigateToExplore,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("downloads_explore_btn")
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explorar Catálogo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    DownloadCardItem(
                        item = item,
                        onPlayClick = {
                            onNavigateToWatch(item.tmdbId, item.mediaType, item.seasonNumber, item.episodeNumber)
                        },
                        onPauseClick = { viewModel.pauseDownload(item.id) },
                        onResumeClick = { viewModel.resumeDownload(item.id) },
                        onDeleteClick = { itemToDelete = item }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadCardItem(
    item: DownloadEntity,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(95.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .clickable(enabled = item.status == DownloadEntity.STATUS_COMPLETED) {
                        onPlayClick()
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.posterPath ?: item.backdropPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (item.status == DownloadEntity.STATUS_COMPLETED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BrandRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Assistir Offline",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details and progress
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.mediaType == "tv" || item.mediaType == "serie") {
                    val epLabel = "T${item.seasonNumber ?: 1}:E${item.episodeNumber ?: 1}${if (!item.episodeTitle.isNullOrBlank()) " • ${item.episodeTitle}" else ""}"
                    Text(
                        text = epLabel,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Status & Progress section
                fun formatItemBytes(bytes: Long): String {
                    if (bytes <= 0) return "0 MB"
                    val mb = bytes / (1024.0 * 1024.0)
                    return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
                }

                when (item.status) {
                    DownloadEntity.STATUS_COMPLETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFF065F46),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Baixado", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            val sizeBytes = if (item.downloadedBytes > 0) item.downloadedBytes else item.totalBytes
                            val sizeText = if (sizeBytes > 0) formatItemBytes(sizeBytes) else "Vídeo Offline"
                            Text(sizeText, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    DownloadEntity.STATUS_DOWNLOADING -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val downloadedStr = formatItemBytes(item.downloadedBytes)
                            val totalStr = if (item.totalBytes > 0) formatItemBytes(item.totalBytes) else "Tamanho desconhecido"
                            val subDetail = if (item.totalBytes > 0) "$downloadedStr / $totalStr" else "$downloadedStr baixados"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Baixando ${item.progress}% • $subDetail",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.downloadSpeed ?: "",
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF334155),
                            )
                        }
                    }

                    DownloadEntity.STATUS_PAUSED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFF78350F),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Pausado (${item.progress}%)",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(formatItemBytes(item.downloadedBytes), color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    DownloadEntity.STATUS_PREPARING, DownloadEntity.STATUS_PENDING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                color = BrandRed,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("Preparando download...", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }

                    DownloadEntity.STATUS_NOT_SUPPORTED -> {
                        Text(
                            text = item.errorMessage ?: "Transmissão online apenas.",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> { // ERROR
                        Text(
                            text = item.errorMessage ?: "Erro ao baixar arquivo.",
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                when (item.status) {
                    DownloadEntity.STATUS_DOWNLOADING -> {
                        IconButton(
                            onClick = onPauseClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Outlined.Pause, contentDescription = "Pausar", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    DownloadEntity.STATUS_PAUSED, DownloadEntity.STATUS_ERROR -> {
                        IconButton(
                            onClick = onResumeClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Retomar", tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }
                    }
                    DownloadEntity.STATUS_COMPLETED -> {
                        IconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Reproduzir Offline", tint = BrandRed, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Excluir",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
