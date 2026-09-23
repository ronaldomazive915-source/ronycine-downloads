package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.download.SourceValidationResult
import com.example.data.download.SourceValidator
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import com.example.util.AppDownloadManager
import com.example.util.DownloadUtils
import com.example.util.ExternalDownloadManager
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    url: String,
    fileName: String,
    tmdbId: String,
    title: String,
    subTitle: String? = null,
    posterPath: String? = null,
    mediaType: String,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    viewModel: MainViewModel,
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

    // Detect installed external managers in real-time
    val idmInstalled = remember { ExternalDownloadManager.is1DmInstalled(context) }
    val admInstalled = remember { ExternalDownloadManager.isAdmInstalled(context) }

    // Source validation state
    var validationResult by remember { mutableStateOf<SourceValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(true) }

    val taskId = remember(mediaType, tmdbId, seasonNumber, episodeNumber) {
        AppDownloadManager.generateTaskId(mediaType, tmdbId, seasonNumber, episodeNumber)
    }

    // Existing download for this specific media (or episode)
    val existingTask = allDownloads.find { it.id == taskId }

    // Other active downloads (queued or downloading)
    val activeDownloads = allDownloads.filter {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PREPARING
    }

    LaunchedEffect(url) {
        isValidating = true
        validationResult = SourceValidator.validate(url)
        isValidating = false
    }

    val isDirect = (validationResult as? SourceValidationResult.Success)?.isDirectMedia == true
    val detectedSize = (validationResult as? SourceValidationResult.Success)?.contentLength ?: -1L
    val finalDownloadUrl = (validationResult as? SourceValidationResult.Success)?.resolvedUrl ?: url

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
            // 1. Header with Close Button
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
                            text = "BAIXAR CONTEÚDO",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Escolha como deseja realizar o download.",
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
                        .testTag("download_sheet_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. Media Info Banner (Compact & Clean)
            Surface(
                color = Color(0xFF161622),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222232)),
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
                    if (!posterPath.isNullOrBlank()) {
                        AsyncImage(
                            model = posterPath,
                            contentDescription = title,
                            modifier = Modifier
                                .width(34.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF20202E)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!subTitle.isNullOrBlank()) {
                                Text(
                                    text = subTitle,
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            val sourceLabel = when {
                                isValidating -> "Verificando fonte..."
                                isDirect -> if (detectedSize > 0) "MP4 • ${DownloadUtils.formatFileSize(detectedSize)}" else "Vídeo Direto (MP4)"
                                (validationResult as? SourceValidationResult.Success)?.isHlsStream == true -> "Transmissão HLS"
                                else -> "Stream Web"
                            }

                            Text(
                                text = "• $sourceLabel",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFFA78BFA),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // 3. Intelligent Compact Option Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
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
                                if (idmInstalled) {
                                    ExternalDownloadManager.downloadWith1DM(context, finalDownloadUrl, fileName)
                                    onDismiss()
                                } else {
                                    ExternalDownloadManager.openPlayStore(context, ExternalDownloadManager.PKG_1DM)
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
                                if (admInstalled) {
                                    ExternalDownloadManager.downloadWithADM(context, finalDownloadUrl, fileName)
                                    onDismiss()
                                } else {
                                    ExternalDownloadManager.openPlayStore(context, ExternalDownloadManager.PKG_ADM)
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
                                ExternalDownloadManager.downloadWithSystemChooser(context, finalDownloadUrl, fileName)
                                onDismiss()
                            }
                        )
                    }
                )

                // Card 4: Baixar no RONYCINE (Internal Downloader)
                CompactDownloadOptionCard(
                    icon = Icons.Default.DownloadForOffline,
                    iconBg = Color(0xFF8B5CF6).copy(alpha = 0.20f),
                    iconTint = Color(0xFFA78BFA),
                    title = "Baixar no RONYCINE",
                    description = "Download interno para assistir offline",
                    isInstalled = null,
                    isRecommended = true,
                    statusNotice = if (existingTask?.status == DownloadStatus.DOWNLOADING) "Baixando agora..." 
                                   else if (existingTask?.status == DownloadStatus.QUEUED) "Na fila"
                                   else if (existingTask?.status == DownloadStatus.COMPLETED) "Concluído" 
                                   else null,
                    onClick = {
                        viewModel.requireAuthentication(
                            isAuthenticated = currentUserState != null,
                            onNavigateToLogin = onNavigateToLogin,
                            onAlreadyAuthenticated = {
                                if (existingTask?.status == DownloadStatus.COMPLETED) {
                                    Toast.makeText(context, "Este conteúdo já está disponível offline.", Toast.LENGTH_SHORT).show()
                                    onNavigateToDownloads?.invoke() ?: onDismiss()
                                } else if (existingTask?.status == DownloadStatus.DOWNLOADING || existingTask?.status == DownloadStatus.QUEUED) {
                                    Toast.makeText(context, "Este conteúdo já está sendo baixado.", Toast.LENGTH_SHORT).show()
                                } else if (finalDownloadUrl.isBlank()) {
                                    Toast.makeText(context, "Aguarde a validação da fonte do vídeo...", Toast.LENGTH_SHORT).show()
                                } else {
                                    val task = DownloadTask(
                            id = taskId,
                            title = title,
                            subTitle = subTitle,
                            posterPath = posterPath,
                            url = finalDownloadUrl,
                            fileName = fileName,
                            mediaType = mediaType,
                            tmdbId = tmdbId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            totalSize = if (detectedSize > 0) detectedSize else 0L,
                            status = DownloadStatus.QUEUED,
                            mimeType = (validationResult as? SourceValidationResult.Success)?.contentType ?: if (finalDownloadUrl.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"
                        )
                        downloadManager.startDownload(task)
                        Toast.makeText(context, "Download iniciado no RONYCINE", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
            }

            // 4. Section: ARQUIVOS DO DOWNLOAD (if this media is being downloaded or recently completed)
            val relevantTask = existingTask ?: activeDownloads.firstOrNull()
            if (relevantTask != null) {
                HorizontalDivider(color = Color(0xFF222230), modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "ARQUIVOS DO DOWNLOAD",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ActiveDownloadInlineCard(
                    task = relevantTask,
                    onPause = { downloadManager.pauseDownload(relevantTask.id) },
                    onResume = { downloadManager.resumeDownload(relevantTask.id) },
                    onCancel = { downloadManager.cancelDownload(relevantTask.id) }
                )
            }

            // 5. Footer: Copiar Link & Link to Meus Downloads
            HorizontalDivider(color = Color(0xFF1E1E28), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { ExternalDownloadManager.copyToClipboard(context, finalDownloadUrl) },
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
                        modifier = Modifier.testTag("download_sheet_view_all")
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
