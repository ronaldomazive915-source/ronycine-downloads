package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import com.example.ui.components.OfflineVideoPlayerModal
import com.example.ui.viewmodel.DownloadsViewModel
import com.example.util.DownloadUtils
import java.io.File

enum class DownloadCategoryFilter(val label: String) {
    TODOS("Todos"),
    FILMES("Filmes"),
    SERIES("Séries"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDOS("Concluídos")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onNavigateToExplore: (() -> Unit)? = null,
    viewModel: DownloadsViewModel = viewModel()
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState()

    var selectedFilter by remember { mutableStateOf(DownloadCategoryFilter.TODOS) }
    var playingTask by remember { mutableStateOf<DownloadTask?>(null) }
    var taskToDelete by remember { mutableStateOf<DownloadTask?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Real device storage calculations using StatFs
    val (totalInternalBytes, freeInternalBytes) = remember(downloads) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir ?: Environment.getDataDirectory()
            val stat = StatFs(dir.path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Pair(total, free)
        } catch (e: Exception) {
            try {
                val stat = StatFs(Environment.getDataDirectory().path)
                Pair(stat.totalBytes, stat.availableBytes)
            } catch (ex: Exception) {
                Pair(0L, 0L)
            }
        }
    }

    val usedInternalBytes = remember(totalInternalBytes, freeInternalBytes) {
        (totalInternalBytes - freeInternalBytes).coerceAtLeast(0L)
    }

    val totalDownloadedBytes = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }.sumOf { task ->
            if (task.totalSize > 0) task.totalSize else task.downloadedSize
        }
    }

    val filteredDownloads = remember(downloads, selectedFilter) {
        when (selectedFilter) {
            DownloadCategoryFilter.TODOS -> downloads
            DownloadCategoryFilter.FILMES -> downloads.filter { it.mediaType == "movie" }
            DownloadCategoryFilter.SERIES -> downloads.filter { it.mediaType == "tv" || it.mediaType == "serie" }
            DownloadCategoryFilter.EM_ANDAMENTO -> downloads.filter {
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PAUSED ||
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.PREPARING
            }
            DownloadCategoryFilter.CONCLUIDOS -> downloads.filter { it.status == DownloadStatus.COMPLETED }
        }
    }

    // Modal player for local files
    if (playingTask != null) {
        OfflineVideoPlayerModal(
            task = playingTask!!,
            onDismiss = { playingTask = null }
        )
    }

    // Confirm Single Delete Dialog
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            containerColor = Color(0xFF181824),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("Excluir download?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "O arquivo baixado de \"${taskToDelete?.title}\" será removido do seu aparelho e o espaço será liberado.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = taskToDelete
                        taskToDelete = null
                        if (t != null) viewModel.deleteDownload(t)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // Confirm Clear All Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = Color(0xFF181824),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("Excluir todos os downloads?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Todos os ${downloads.size} downloads serão apagados permanentemente do armazenamento do dispositivo.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        downloads.forEach { viewModel.deleteDownload(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Excluir Todos", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Meus Downloads",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${downloads.size} item(ns) salvos offline",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("downloads_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.testTag("downloads_clear_all_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Limpar todos",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C0C10),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0C0C10)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Real Device Storage Card
            StorageOverviewCard(
                totalBytes = totalInternalBytes,
                usedBytes = usedInternalBytes,
                freeBytes = freeInternalBytes,
                ronycineBytes = totalDownloadedBytes
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DownloadCategoryFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        DownloadCategoryFilter.TODOS -> downloads.size
                        DownloadCategoryFilter.FILMES -> downloads.count { it.mediaType == "movie" }
                        DownloadCategoryFilter.SERIES -> downloads.count { it.mediaType == "tv" || it.mediaType == "serie" }
                        DownloadCategoryFilter.EM_ANDAMENTO -> downloads.count {
                            it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.PAUSED ||
                            it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.PREPARING
                        }
                        DownloadCategoryFilter.CONCLUIDOS -> downloads.count { it.status == DownloadStatus.COMPLETED }
                    }

                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = "${filter.label} ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF161622),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = null
                    )
                }
            }

            // Content List or Empty State
            if (filteredDownloads.isEmpty()) {
                DownloadsEmptyState(
                    selectedFilter = selectedFilter,
                    onExploreClick = onNavigateToExplore ?: onBack
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDownloads, key = { it.id }) { task ->
                        DownloadItemCard(
                            task = task,
                            onPlay = {
                                val file = task.localPath?.let { File(it) }
                                if (file != null && file.exists() && file.length() > 0L) {
                                    playingTask = task
                                } else {
                                    // Fallback to openExternal or alert
                                    openDownloadedFile(context, task)
                                }
                            },
                            onOpenExternal = {
                                openDownloadedFile(context, task)
                            },
                            onPause = { viewModel.pauseDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task.id) },
                            onRetry = { viewModel.retryDownload(task.id) },
                            onCancel = { viewModel.cancelDownload(task.id) },
                            onDelete = { taskToDelete = task }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    totalBytes: Long,
    usedBytes: Long,
    freeBytes: Long,
    ronycineBytes: Long
) {
    Surface(
        color = Color(0xFF13131E),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222234))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Armazenamento do Aparelho",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val totalFormatted = if (totalBytes > 0) DownloadUtils.formatFileSize(totalBytes) else "Dispositivo"
                        val freeFormatted = if (freeBytes > 0) DownloadUtils.formatFileSize(freeBytes) else "Calculando..."
                        Text(
                            text = "Livre: $freeFormatted de $totalFormatted",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Ronycine Downloads indicator
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "RONYCINE",
                        color = Color(0xFFA78BFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = DownloadUtils.formatFileSize(ronycineBytes),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-segment storage bar
            val totalFloat = if (totalBytes > 0) totalBytes.toFloat() else 1f
            val ronycineFraction = (ronycineBytes.toFloat() / totalFloat).coerceIn(0f, 1f)
            val otherUsedFraction = ((usedBytes - ronycineBytes).coerceAtLeast(0L).toFloat() / totalFloat).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF222234))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // System / other apps used space
                    if (otherUsedFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(otherUsedFraction.coerceAtLeast(0.01f))
                                .background(Color(0xFF4B5563))
                        )
                    }
                    // Ronycine downloaded files space
                    if (ronycineFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(ronycineFraction.coerceAtLeast(0.01f))
                                .background(Color(0xFF8B5CF6))
                        )
                    }
                    // Free space
                    val freeFraction = (freeBytes.toFloat() / totalFloat).coerceIn(0.01f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(freeFraction)
                            .background(Color(0xFF1E1E2C))
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF8B5CF6), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Downloads RONYCINE", color = Color(0xFFCBD5E1), fontSize = 10.5.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4B5563), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Outros apps/sistema", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF1E1E2C), CircleShape).border(1.dp, Color(0xFF4B5563), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Espaço Livre", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    task: DownloadTask,
    onPlay: () -> Unit,
    onOpenExternal: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Color(0xFF14141E),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF20202F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster
                if (!task.posterPath.isNullOrBlank()) {
                    AsyncImage(
                        model = task.posterPath,
                        contentDescription = task.title,
                        modifier = Modifier
                            .width(44.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E2C)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Info & Status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val typeSubtitle = if (!task.subTitle.isNullOrBlank()) {
                        task.subTitle
                    } else if (task.mediaType == "movie") {
                        "Filme • MP4"
                    } else {
                        "Série • MP4"
                    }

                    Text(
                        text = typeSubtitle,
                        color = Color(0xFFA78BFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val progressFloat = if (task.totalSize > 0) (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f) else 0f
                    val percent = (progressFloat * 100).toInt()

                    val statusText = when (task.status) {
                        DownloadStatus.QUEUED -> "Aguardando na fila..."
                        DownloadStatus.PREPARING -> "Iniciando conexão..."
                        DownloadStatus.DOWNLOADING -> {
                            val speedStr = if (task.speed > 0) " • ${DownloadUtils.formatDownloadSpeed(task.speed)}" else ""
                            "$percent% • ${DownloadUtils.formatFileSize(task.downloadedSize)} / ${DownloadUtils.formatFileSize(task.totalSize)}$speedStr"
                        }
                        DownloadStatus.PAUSED -> "Pausado em $percent% (${DownloadUtils.formatFileSize(task.downloadedSize)})"
                        DownloadStatus.COMPLETED -> "Concluído • ${DownloadUtils.formatFileSize(task.totalSize)}"
                        DownloadStatus.ERROR -> task.errorMessage ?: "Erro no download"
                        DownloadStatus.CANCELED -> "Cancelado"
                        DownloadStatus.IDLE -> "Parado"
                    }

                    Text(
                        text = statusText,
                        color = when (task.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF10B981)
                            DownloadStatus.ERROR -> Color(0xFFEF4444)
                            DownloadStatus.DOWNLOADING -> Color(0xFFA78BFA)
                            DownloadStatus.PAUSED -> Color(0xFFF59E0B)
                            else -> Color(0xFF94A3B8)
                        },
                        fontSize = 11.sp,
                        fontWeight = if (task.status == DownloadStatus.COMPLETED) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (task.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Pause, contentDescription = "Pausar", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Continuar", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.QUEUED, DownloadStatus.PREPARING -> {
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            Button(
                                onClick = onPlay,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Assistir", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.ERROR, DownloadStatus.CANCELED -> {
                            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tentar novamente", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.IDLE -> {
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Progress Bar if in progress
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                val progressFloat = if (task.totalSize > 0) (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progressFloat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (task.status == DownloadStatus.PAUSED) Color(0xFFF59E0B) else Color(0xFF8B5CF6),
                    trackColor = Color(0xFF222232)
                )
            }
        }
    }
}

@Composable
private fun DownloadsEmptyState(
    selectedFilter: DownloadCategoryFilter,
    onExploreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF161622), CircleShape)
                    .border(1.dp, Color(0xFF262638), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (selectedFilter) {
                    DownloadCategoryFilter.TODOS -> "Nenhum download encontrado"
                    DownloadCategoryFilter.FILMES -> "Nenhum filme baixado"
                    DownloadCategoryFilter.SERIES -> "Nenhuma série baixada"
                    DownloadCategoryFilter.EM_ANDAMENTO -> "Nenhum download em andamento"
                    DownloadCategoryFilter.CONCLUIDOS -> "Nenhum conteúdo baixado ainda"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Baixe filmes e episódios para assistir offline sem precisar de internet onde quer que você esteja.",
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onExploreClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Explorar Catálogo", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    }
}

private fun openDownloadedFile(context: Context, task: DownloadTask) {
    try {
        val path = task.localPath
        if (path.isNullOrBlank()) {
            Toast.makeText(context, "Caminho do arquivo não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Arquivo baixado não encontrado no armazenamento local", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "Assistir ${task.title}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o reprodutor de vídeo: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
