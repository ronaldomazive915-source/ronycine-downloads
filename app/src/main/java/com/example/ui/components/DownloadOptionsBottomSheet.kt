package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.download.*
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    media: MediaEntity,
    episode: EpisodeEntity? = null,
    customUrl: String? = null,
    onDismiss: () -> Unit,
    onStartInternalDownload: (MediaEntity, EpisodeEntity?, String?) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Resolution State
    var isResolving by remember { mutableStateOf(true) }
    var resolutionResult by remember { mutableStateOf<StreamResolutionResult?>(null) }
    var resolvedStream by remember { mutableStateOf<ResolvedStream?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // App Missing Dialog State
    var missingAppInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var noAppFoundMessage by remember { mutableStateOf<String?>(null) }

    // Check installed apps
    val installedApps = remember {
        ExternalDownloadHelper.checkInstalledDownloadApps(context)
    }

    val isTv = media.mediaType == "tv" || media.mediaType == "serie" || episode != null
    val targetFileName = remember(media, episode) {
        if (isTv) {
            ExternalDownloadHelper.formatEpisodeFileName(
                seriesTitle = media.title,
                seasonNumber = episode?.seasonNumber ?: 1,
                episodeNumber = episode?.episodeNumber ?: 1,
                episodeTitle = episode?.title
            )
        } else {
            ExternalDownloadHelper.formatMovieFileName(
                title = media.title,
                releaseYear = media.releaseYear
            )
        }
    }

    // Resolve URL in coroutine
    LaunchedEffect(media, episode, customUrl, retryTrigger) {
        isResolving = true
        resolutionResult = null
        resolvedStream = null

        val result = ExternalDownloadHelper.resolveStreamInfo(
            media = media,
            episode = episode,
            customUrl = customUrl
        )

        resolutionResult = result
        if (result is StreamResolutionResult.Success) {
            resolvedStream = result.stream
        }
        isResolving = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Gray.copy(alpha = 0.5f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTv) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTv && episode != null) {
                            "Baixar Episódio ${episode.episodeNumber}"
                        } else {
                            "Baixar Filme"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isTv && episode != null) {
                            "${media.title} • T${episode.seasonNumber}E${episode.episodeNumber}${if (episode.title.isNotBlank()) " - ${episode.title}" else ""}"
                        } else {
                            "${media.title} (${media.releaseYear.take(4)})"
                        },
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // File Name Box
            Surface(
                color = DarkBackground,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = resolvedStream?.fileName ?: targetFileName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stream Resolution State / Loader
            AnimatedContent(
                targetState = isResolving to resolutionResult,
                label = "resolutionState"
            ) { (loading, result) ->
                when {
                    loading -> {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = BrandRed,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Identificando URL real do vídeo...",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Verificando fonte de transmissão",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    result is StreamResolutionResult.Success -> {
                        val stream = result.stream
                        Surface(
                            color = when (stream.streamType) {
                                StreamType.DIRECT_MP4 -> Color(0xFF065F46).copy(alpha = 0.35f)
                                StreamType.HLS -> Color(0xFF1E3A8A).copy(alpha = 0.35f)
                                else -> Color(0xFF374151).copy(alpha = 0.35f)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                when (stream.streamType) {
                                    StreamType.DIRECT_MP4 -> Color(0xFF10B981).copy(alpha = 0.5f)
                                    StreamType.HLS -> Color(0xFF3B82F6).copy(alpha = 0.5f)
                                    else -> Color.Gray.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (stream.streamType) {
                                        StreamType.DIRECT_MP4 -> Icons.Default.CheckCircle
                                        StreamType.HLS -> Icons.Default.LiveTv
                                        else -> Icons.Default.VideoFile
                                    },
                                    contentDescription = null,
                                    tint = when (stream.streamType) {
                                        StreamType.DIRECT_MP4 -> Color(0xFF34D399)
                                        StreamType.HLS -> Color(0xFF60A5FA)
                                        else -> Color.White
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stream.streamType.label,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (stream.streamType == StreamType.HLS) {
                                            "Fluxo HLS (.m3u8) pronto para download"
                                        } else {
                                            "Vídeo direto pronto para download"
                                        },
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    result is StreamResolutionResult.NotSupported -> {
                        Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Download externo não compatível",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = result.reason,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    result is StreamResolutionResult.Error -> {
                        Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Fonte não encontrada",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = result.errorMessage,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { retryTrigger++ },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tentar Novamente", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ESCOLHA O APLICATIVO",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Options List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. 1DM
                val idmApp = installedApps.firstOrNull { it.type == ExternalDownloaderType.IDM }
                DownloadOptionItem(
                    title = "1DM",
                    appName = "Internet Download Manager",
                    description = if (idmApp?.isInstalled == true) "Instalado • Aceleração máxima" else "Recomendado • Aceleração máxima",
                    icon = Icons.Default.CloudDownload,
                    accentColor = Color(0xFF3B82F6),
                    enabled = !isResolving && resolvedStream != null,
                    testTag = "download_option_1dm",
                    onClick = {
                        val stream = resolvedStream ?: return@DownloadOptionItem
                        val launchRes = ExternalDownloadHelper.launchExternalDownloader(
                            context = context,
                            appType = ExternalDownloaderType.IDM,
                            stream = stream
                        )
                        when (launchRes) {
                            is DownloadLaunchResult.Success -> {
                                Toast.makeText(context, "Enviado para o 1DM com sucesso!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                            is DownloadLaunchResult.AppNotInstalled -> {
                                missingAppInfo = "1DM" to launchRes.playStorePackage
                            }
                            is DownloadLaunchResult.NoAppFound -> {
                                noAppFoundMessage = launchRes.message
                            }
                            is DownloadLaunchResult.Error -> {
                                Toast.makeText(context, launchRes.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )

                // 2. ADM
                val admApp = installedApps.firstOrNull { it.type == ExternalDownloaderType.ADM }
                DownloadOptionItem(
                    title = "ADM",
                    appName = "Advanced Download Manager",
                    description = if (admApp?.isInstalled == true) "Instalado • Multi-conexões" else "Gerenciador multi-conexões",
                    icon = Icons.Default.FileDownload,
                    accentColor = Color(0xFF10B981),
                    enabled = !isResolving && resolvedStream != null,
                    testTag = "download_option_adm",
                    onClick = {
                        val stream = resolvedStream ?: return@DownloadOptionItem
                        val launchRes = ExternalDownloadHelper.launchExternalDownloader(
                            context = context,
                            appType = ExternalDownloaderType.ADM,
                            stream = stream
                        )
                        when (launchRes) {
                            is DownloadLaunchResult.Success -> {
                                Toast.makeText(context, "Enviado para o ADM com sucesso!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                            is DownloadLaunchResult.AppNotInstalled -> {
                                missingAppInfo = "ADM" to launchRes.playStorePackage
                            }
                            is DownloadLaunchResult.NoAppFound -> {
                                noAppFoundMessage = launchRes.message
                            }
                            is DownloadLaunchResult.Error -> {
                                Toast.makeText(context, launchRes.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )

                // 3. Outro aplicativo
                DownloadOptionItem(
                    title = "Outro aplicativo",
                    appName = null,
                    description = "Escolher outro aplicativo instalado no Android",
                    icon = Icons.Default.Share,
                    accentColor = Color(0xFFA855F7),
                    enabled = !isResolving && resolvedStream != null,
                    testTag = "download_option_chooser",
                    onClick = {
                        val stream = resolvedStream ?: return@DownloadOptionItem
                        val launchRes = ExternalDownloadHelper.launchExternalDownloader(
                            context = context,
                            appType = ExternalDownloaderType.CHOOSER,
                            stream = stream
                        )
                        when (launchRes) {
                            is DownloadLaunchResult.Success -> {
                                onDismiss()
                            }
                            is DownloadLaunchResult.NoAppFound -> {
                                noAppFoundMessage = launchRes.message
                            }
                            is DownloadLaunchResult.Error -> {
                                Toast.makeText(context, launchRes.message, Toast.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }
                )

                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // 4. Baixar no RONYCINE
                DownloadOptionItem(
                    title = "Baixar no RONYCINE",
                    appName = null,
                    description = "Salvar no armazenamento do aparelho",
                    icon = Icons.Default.PhoneAndroid,
                    accentColor = BrandRed,
                    enabled = true,
                    testTag = "download_option_internal",
                    onClick = {
                        onStartInternalDownload(media, episode, resolvedStream?.url ?: customUrl)
                        onDismiss()
                    }
                )
            }
        }
    }

    // Modal when requested app is not installed (e.g. 1DM or ADM)
    if (missingAppInfo != null) {
        val (appName, playPackage) = missingAppInfo!!
        AlertDialog(
            onDismissRequest = { missingAppInfo = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "$appName não instalado",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "O aplicativo $appName não foi encontrado no seu aparelho. O que deseja fazer?",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Você pode usar outro aplicativo instalado no celular ou usar o download próprio do RONYCINE.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        missingAppInfo = null
                        val stream = resolvedStream
                        if (stream != null) {
                            ExternalDownloadHelper.launchExternalDownloader(
                                context = context,
                                appType = ExternalDownloaderType.CHOOSER,
                                stream = stream
                            )
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Outro Aplicativo")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            missingAppInfo = null
                            ExternalDownloadHelper.openPlayStore(context, playPackage)
                        }
                    ) {
                        Text("Instalar $appName", color = Color(0xFF38BDF8))
                    }
                    TextButton(
                        onClick = {
                            missingAppInfo = null
                            onStartInternalDownload(media, episode, resolvedStream?.url ?: customUrl)
                            onDismiss()
                        }
                    ) {
                        Text("Usar RONYCINE", color = Color(0xFF10B981))
                    }
                }
            }
        )
    }

    // Fallback Alert when NO external apps are found
    if (noAppFoundMessage != null) {
        AlertDialog(
            onDismissRequest = { noAppFoundMessage = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Nenhum aplicativo compatível",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = noAppFoundMessage ?: "Nenhum aplicativo de download compatível foi encontrado.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        noAppFoundMessage = null
                        onStartInternalDownload(media, episode, resolvedStream?.url ?: customUrl)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Usar download do RONYCINE")
                }
            },
            dismissButton = {
                TextButton(onClick = { noAppFoundMessage = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun DownloadOptionItem(
    title: String,
    appName: String? = null,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) DarkBackground else DarkBackground.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (enabled) CardBorder else CardBorder.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (enabled) accentColor.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) accentColor else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = if (enabled) Color.White else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!appName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = appName,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = description,
                    color = if (enabled) Color.LightGray else Color.DarkGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) Color.Gray else Color.DarkGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

