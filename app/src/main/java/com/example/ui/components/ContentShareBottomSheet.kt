package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.util.ContentShareHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern, professional Content Sharing Bottom Sheet for Movies, Series, and Episodes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentShareBottomSheet(
    media: MediaEntity,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val shareData = remember(media, seasonNumber, episodeNumber, episodeTitle) {
        ContentShareHelper.buildShareData(
            media = media,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle
        )
    }

    var contentCopied by remember { mutableStateOf(false) }
    var appCopied by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F16),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3B3B4F))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.testTag("content_share_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Compartilhar",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2A))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Media Info Card with Real Poster & TMDB Metadata
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF181824),
                border = BorderStroke(1.dp, Color(0xFF2B2B3E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Real Poster Card
                    val posterUrl = if (!shareData.posterPath.isNullOrBlank()) {
                        if (shareData.posterPath.startsWith("http")) shareData.posterPath else "https://image.tmdb.org/t/p/w342${shareData.posterPath}"
                    } else null

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F0F16),
                        border = BorderStroke(1.dp, Color(0xFF333348)),
                        modifier = Modifier
                            .width(68.dp)
                            .height(98.dp)
                    ) {
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = shareData.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (shareData.mediaType == "tv") Icons.Default.Tv else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Content Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = shareData.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Episode or Seasons/Year row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = BrandRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (shareData.mediaType == "tv") "SÉRIE" else "FILME",
                                    color = BrandRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            if (shareData.releaseYear.isNotBlank()) {
                                Text(
                                    text = shareData.releaseYear,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (shareData.restricted18) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "18+",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Duration or Episode details
                        Text(
                            text = shareData.durationOrSeasons,
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal
                        )

                        if (!shareData.episodeTitle.isNullOrBlank()) {
                            Text(
                                text = "“${shareData.episodeTitle}”",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (shareData.genres.isNotBlank()) {
                            Text(
                                text = shareData.genres,
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section 1: LINK DO CONTEÚDO
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LINK DO CONTEÚDO",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF14141E),
                    border = BorderStroke(1.dp, if (contentCopied) BrandRed.copy(alpha = 0.8f) else Color(0xFF262638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ContentShareHelper.copyContentLink(context, shareData.contentUrl)
                                contentCopied = true
                                coroutineScope.launch {
                                    delay(2000)
                                    contentCopied = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = shareData.contentUrl,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (contentCopied) BrandRed else Color(0xFF222230),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (contentCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copiar link",
                                    tint = if (contentCopied) Color.White else Color(0xFFA1A1AA),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: BAIXAR O RONYCINE
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📱 BAIXAR O RONYCINE",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "APK Oficial",
                        color = Color(0xFF10B981),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF14141E),
                    border = BorderStroke(1.dp, if (appCopied) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0xFF262638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ContentShareHelper.copyAppLink(context, shareData.appDownloadUrl)
                                appCopied = true
                                coroutineScope.launch {
                                    delay(2000)
                                    appCopied = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = shareData.appDownloadUrl,
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (appCopied) Color(0xFF10B981) else Color(0xFF222230),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (appCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copiar link do app",
                                    tint = if (appCopied) Color.White else Color(0xFFA1A1AA),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Primary Big Action Button: COMPARTILHAR
            Button(
                onClick = {
                    ContentShareHelper.shareViaNativeChooser(context, shareData)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_sheet_primary_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "COMPARTILHAR",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // Quick Actions Row: [ Copiar conteúdo ] [ Copiar app ] [ WhatsApp ] [ Telegram ] [ Mais ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickShareActionItem(
                    icon = Icons.Default.ContentCopy,
                    label = "Copiar link",
                    tint = Color(0xFFA78BFA),
                    onClick = {
                        ContentShareHelper.copyContentLink(context, shareData.contentUrl)
                        contentCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            contentCopied = false
                        }
                    }
                )

                QuickShareActionItem(
                    icon = Icons.Default.Download,
                    label = "Copiar app",
                    tint = Color(0xFF34D399),
                    onClick = {
                        ContentShareHelper.copyAppLink(context, shareData.appDownloadUrl)
                        appCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            appCopied = false
                        }
                    }
                )

                QuickShareActionItem(
                    icon = Icons.Default.Chat,
                    label = "WhatsApp",
                    tint = Color(0xFF22C55E),
                    onClick = {
                        ContentShareHelper.shareViaWhatsApp(context, shareData)
                    }
                )

                QuickShareActionItem(
                    icon = Icons.Default.Send,
                    label = "Telegram",
                    tint = Color(0xFF38BDF8),
                    onClick = {
                        ContentShareHelper.shareViaTelegram(context, shareData)
                    }
                )

                QuickShareActionItem(
                    icon = Icons.Default.MoreHoriz,
                    label = "Mais",
                    tint = Color(0xFFE2E8F0),
                    onClick = {
                        ContentShareHelper.shareViaNativeChooser(context, shareData)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickShareActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
