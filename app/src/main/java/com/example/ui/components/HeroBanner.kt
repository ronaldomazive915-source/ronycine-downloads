package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.FeaturedMediaItem
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import kotlinx.coroutines.delay

@Composable
fun HeroBanner(
    featuredList: List<FeaturedMediaItem>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onTrailerEnded: () -> Unit = {},
    isInMyList: Boolean,
    onWatchClick: (MediaEntity) -> Unit,
    onMyListToggle: (MediaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (featuredList.isEmpty()) return

    val validIndex = currentIndex.coerceIn(0, featuredList.size - 1)
    val currentItem = featuredList[validIndex]
    val media = currentItem.media
    val featured = currentItem.featured

    // Auto-advance carousel every 7 seconds if more than 1 item and no active video interaction
    var isUserInteracting by remember { mutableStateOf(false) }
    LaunchedEffect(validIndex, featuredList.size, isUserInteracting) {
        if (featuredList.size > 1 && !isUserInteracting) {
            delay(7000)
            val next = (validIndex + 1) % featuredList.size
            onSelectIndex(next)
        }
    }

    val isRunningOnEmulator = remember {
        val brand = android.os.Build.BRAND ?: ""
        val device = android.os.Build.DEVICE ?: ""
        val fingerprint = android.os.Build.FINGERPRINT ?: ""
        val hardware = android.os.Build.HARDWARE ?: ""
        val model = android.os.Build.MODEL ?: ""
        val manufacturer = android.os.Build.MANUFACTURER ?: ""
        val product = android.os.Build.PRODUCT ?: ""
        (brand.startsWith("generic") && device.startsWith("generic"))
                || fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || manufacturer.contains("Genymotion")
                || product.contains("sdk_google")
                || product.contains("google_sdk")
                || product.contains("sdk")
                || product.contains("sdk_x86")
                || product.contains("vbox86p")
                || product.contains("emulator")
                || product.contains("simulator")
    }

    val youtubeId = remember(featured.trailerUrl, media.trailerKey) {
        val raw = featured.trailerUrl.ifBlank { media.trailerKey }
        extractYoutubeVideoId(raw)
    }

    var isMuted by remember(validIndex) { mutableStateOf(true) }
    var showTrailerByDelay by remember(validIndex) { mutableStateOf(false) }
    var isTrailerReady by remember(validIndex) { mutableStateOf(false) }
    var isTrailerError by remember(validIndex) { mutableStateOf(false) }
    var forcePlayTrailer by remember(validIndex) { mutableStateOf(false) }

    LaunchedEffect(validIndex) {
        showTrailerByDelay = false
        isTrailerReady = false
        isTrailerError = false
        forcePlayTrailer = false
        if (featured.autoPlayTrailer) {
            delay(2000)
            showTrailerByDelay = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(370.dp)
            .testTag("hero_banner_${media.tmdbId}")
    ) {
        // Background Backdrop Image with smooth crossfade
        AnimatedContent(
            targetState = media.backdropPath ?: media.posterPath,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
            label = "hero_backdrop"
        ) { targetImage ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(targetImage)
                    .crossfade(true)
                    .build(),
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Embedded Trailer Video Layer (auto-play disabled on emulators to prevent crashes)
        val shouldPlayTrailer = if (forcePlayTrailer) true else (featured.autoPlayTrailer && !isRunningOnEmulator)
        if (youtubeId != null && shouldPlayTrailer && !isTrailerError && showTrailerByDelay) {
            val playerAlpha by animateFloatAsState(
                targetValue = if (isTrailerReady) 1f else 0f,
                animationSpec = tween(700),
                label = "hero_trailer_alpha"
            )
            androidx.compose.runtime.key(validIndex, youtubeId) {
                HeroTrailerPlayer(
                    youtubeId = youtubeId,
                    autoPlay = true,
                    isMuted = isMuted,
                    onPlayerReady = { isTrailerReady = true },
                    onPlaying = { isTrailerReady = true },
                    onPlayerError = { isTrailerError = true },
                    onPlayerEnded = { onTrailerEnded() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = playerAlpha }
                )
            }
        }

        // Dark Gradient Overlays for Contrast & Readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkBackground.copy(alpha = 0.55f),
                            Color.Transparent,
                            DarkBackground.copy(alpha = 0.7f),
                            DarkBackground.copy(alpha = 0.95f),
                            DarkBackground
                        ),
                        startY = 0f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            DarkBackground.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Audio Control / Trailer Trigger Button (Top Right)
        if (youtubeId != null) {
            val isTrailerActive = showTrailerByDelay && isTrailerReady && !isTrailerError && shouldPlayTrailer
            IconButton(
                onClick = {
                    isUserInteracting = true
                    if (isTrailerActive) {
                        isMuted = !isMuted
                    } else {
                        forcePlayTrailer = true
                        showTrailerByDelay = true
                        isTrailerError = false
                        isMuted = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .testTag("hero_audio_toggle")
            ) {
                Icon(
                    imageVector = if (isTrailerActive) {
                        if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (isTrailerActive) {
                        if (isMuted) "Sem Som" else "Com Som"
                    } else {
                        "Assistir Trailer"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hero Info and Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Badges Row: "DESTAQUE" + TYPE + YEAR + CIRCULAR RATING
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Surface(
                    color = BrandRed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "DESTAQUE",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (media.mediaType == "tv") "SÉRIE" else "FILME",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (media.releaseYear.isNotBlank()) {
                    Text(
                        text = media.releaseYear,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (media.rating > 0.0) {
                    CircularRatingBadge(
                        rating = media.rating,
                        size = 28.dp,
                        strokeWidth = 2.dp,
                        textSize = 8
                    )
                }
            }

            // Title (Clean & Dominant)
            Text(
                text = media.title,
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            // Genres
            if (media.genres.isNotBlank()) {
                Text(
                    text = media.genres.replace(",", " • "),
                    color = Color(0xFFD4D4D8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Synopsis (Compact, max 2 lines)
            if (media.overview.isNotBlank()) {
                Text(
                    text = media.overview,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action Buttons Row & Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Button with Text
                    Button(
                        onClick = { onWatchClick(media) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("hero_watch_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Assistir",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // My List Toggle Button
                    OutlinedButton(
                        onClick = { onMyListToggle(media) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isInMyList) BrandRed else Color.White.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("hero_my_list_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isInMyList) BrandRed else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isInMyList) "Na Lista" else "Minha Lista",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Carousel Dot / Pill Indicators
                if (featuredList.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        featuredList.indices.forEach { index ->
                            val isSelected = index == validIndex
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 18.dp else 6.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSelected) BrandRed else Color.White.copy(alpha = 0.35f))
                                    .clickable {
                                        isUserInteracting = true
                                        onSelectIndex(index)
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SingleMediaHeroBanner(
    media: MediaEntity,
    isInMyList: Boolean,
    onWatchClick: () -> Unit,
    onMyListToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val featuredItem = remember(media) {
        FeaturedMediaItem(
            featured = com.example.data.local.FeaturedMediaEntity(
                mediaTmdbId = media.tmdbId,
                mediaType = media.mediaType,
                trailerUrl = media.trailerKey ?: "",
                autoPlayTrailer = true
            ),
            media = media
        )
    }

    HeroBanner(
        featuredList = listOf(featuredItem),
        currentIndex = 0,
        onSelectIndex = {},
        isInMyList = isInMyList,
        onWatchClick = { onWatchClick() },
        onMyListToggle = { onMyListToggle() },
        modifier = modifier
    )
}
