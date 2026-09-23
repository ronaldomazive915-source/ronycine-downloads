package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.data.local.FeaturedMediaItem
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import kotlinx.coroutines.delay

/**
 * Modern, cinematic & responsive Hero Destaques swipable horizontal carousel.
 * - Smooth native HorizontalPager with automatic snapping.
 * - Displays peeking edges of neighboring cards on the left/right.
 * - No dot pagination indicators (clean, premium streaming aesthetic).
 * - Smooth autoplay that automatically pauses during user gesture and resumes smoothly.
 * - Full real data integration (backdrop, title, genres, rating, overview, watch, my list).
 */
@Composable
fun HeroBanner(
    featuredList: List<FeaturedMediaItem>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    isInMyList: Boolean,
    onWatchClick: (MediaEntity) -> Unit,
    onMyListToggle: (MediaEntity) -> Unit,
    modifier: Modifier = Modifier,
    isItemInMyList: ((MediaEntity) -> Boolean)? = null,
    onCardClick: ((MediaEntity) -> Unit)? = null,
    isVisible: Boolean = true
) {
    if (featuredList.isEmpty()) return

    val validInitialIndex = currentIndex.coerceIn(0, (featuredList.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = validInitialIndex,
        pageCount = { featuredList.size }
    )

    // Sync state changes with external index if needed
    LaunchedEffect(pagerState.currentPage) {
        onSelectIndex(pagerState.currentPage)
    }

    // React if external index changes programmatically
    LaunchedEffect(currentIndex) {
        if (currentIndex in featuredList.indices && currentIndex != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    // Gentle Auto-advance carousel every 7 seconds when idle and visible
    LaunchedEffect(pagerState, featuredList.size, isVisible) {
        if (isVisible && featuredList.size > 1) {
            while (true) {
                delay(7000)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % featuredList.size
                    pagerState.animateScrollToPage(
                        page = next,
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }
    }

    // Horizontal Pager with side padding to show preview of next and previous cards
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        pageSpacing = 12.dp,
        beyondViewportPageCount = 1,
        key = { page -> "hero_${featuredList[page].media.mediaType}_${featuredList[page].media.tmdbId}" },
        modifier = modifier
            .fillMaxWidth()
            .height(375.dp)
            .testTag("hero_destaques_pager")
    ) { page ->
        val item = featuredList[page]
        val media = item.media
        val itemInMyList = isItemInMyList?.invoke(media) ?: (isInMyList && page == pagerState.currentPage)

        HeroCardItem(
            media = media,
            isInMyList = itemInMyList,
            onWatchClick = { onWatchClick(media) },
            onMyListToggle = { onMyListToggle(media) },
            onCardClick = { onCardClick?.invoke(media) ?: onWatchClick(media) }
        )
    }
}

@Composable
private fun HeroCardItem(
    media: MediaEntity,
    isInMyList: Boolean,
    onWatchClick: () -> Unit,
    onMyListToggle: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
            .testTag("hero_card_${media.tmdbId}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop Image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.backdropPath ?: media.posterPath)
                    .crossfade(true)
                    .build(),
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top Vignette Gradient for clean contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.60f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bottom Cinematic Deep Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                DarkBackground.copy(alpha = 0.50f),
                                DarkBackground.copy(alpha = 0.90f),
                                DarkBackground
                            ),
                            startY = 140f
                        )
                    )
            )

            // Left Subtle Shadow for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkBackground.copy(alpha = 0.70f),
                                Color.Transparent
                            ),
                            endX = 400f
                        )
                    )
            )

            // Hero Info and Action Buttons (at Bottom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Badges Row: "DESTAQUES" + TYPE + YEAR + CIRCULAR RATING
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
                            text = com.example.util.stringI18n("admin.highlights").uppercase(),
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (media.mediaType == "tv") {
                                com.example.util.stringI18n("home.series").uppercase()
                            } else {
                                com.example.util.stringI18n("home.movies").uppercase()
                            },
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
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
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

                // Action Buttons Row (Assistir & Minha Lista)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Button with Text
                    Button(
                        onClick = onWatchClick,
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
                                text = com.example.util.stringI18n("action.watch"),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // My List Toggle Button
                    OutlinedButton(
                        onClick = onMyListToggle,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(
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
            }
        }
    }
}


