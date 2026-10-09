package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.util.MediaClassifier

// Stable pre-allocated constants to avoid allocations during scrolling
private val PosterCardShape = RoundedCornerShape(8.dp)
private val BadgeShape = RoundedCornerShape(bottomEnd = 6.dp)
private val LockBadgeShape = RoundedCornerShape(topEnd = 6.dp)
private val PosterBorder = BorderStroke(1.dp, CardBorder.copy(alpha = 0.4f))
private val PosterGradient = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
)
private val ColorAnime = Color(0xFFE11D48)
private val ColorDorama = Color(0xFF7C3AED)
private val ColorSeries = Color(0xFF2563EB)
private val ColorRestricted = Color(0xFFEF4444).copy(alpha = 0.9f)

/**
 * Ultra-optimized, lightweight Media Card for movies, series, animes, and doramas.
 * - Zero extra state allocations during scroll.
 * - Fixed memory-safe downsampled image dimensions (size: 240x360).
 * - Pre-compiled static shapes, brushes, and colors.
 * - Hardware accelerated layout with zero layout shifts.
 */
@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(105.dp),
    posterHeight: Dp? = null
) {
    val category = remember(media.mediaCategory, media.genres, media.title) {
        MediaClassifier.classifyMedia(media)
    }
    val badgeText = remember(category) {
        when (category) {
            MediaClassifier.CATEGORY_ANIME -> "ANIME"
            MediaClassifier.CATEGORY_DORAMA -> "DORAMA"
            MediaClassifier.CATEGORY_MOVIE -> "FILME"
            else -> "SÉRIE"
        }
    }
    val badgeColor = remember(category) {
        when (category) {
            MediaClassifier.CATEGORY_ANIME -> ColorAnime
            MediaClassifier.CATEGORY_DORAMA -> ColorDorama
            MediaClassifier.CATEGORY_MOVIE -> BrandRed
            else -> ColorSeries
        }
    }

    val context = LocalContext.current
    val imageUrl = remember(media.posterPath, media.backdropPath) {
        val rawUrl = media.posterPath?.ifBlank { null } ?: media.backdropPath
        // FORCE LOW RESOLUTION for list cards to save memory and bandwidth
        rawUrl?.replace("/w500/", "/w185/")
              ?.replace("/w1280/", "/w780/")
              ?.replace("/original/", "/w185/")
    }
    
    val imageRequest = remember(imageUrl, context) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(180, 270) // STRICT DOWNSAMPLING
            .crossfade(false) // REMOVE ANIMATION OVERHEAD
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    Column(
        modifier = modifier
            .clip(PosterCardShape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null // REMOVE RIPPLE ANIMATION FOR PERFORMANCE
            )
            .graphicsLayer {
                // HARDWARE ACCELERATION
                clip = true
            }
            .testTag("media_card_${media.tmdbId}")
    ) {
        // Poster Card with strictly defined 2:3 aspect ratio (preventing layout shifts)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (posterHeight != null) Modifier.height(posterHeight) else Modifier.aspectRatio(2f / 3f)),
            shape = PosterCardShape,
            color = DarkSurface,
            border = PosterBorder,
            shadowElevation = 0.dp // REMOVE SHADOWS FOR GPU EFFICIENCY
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurface)
                )

                // Dark Bottom Gradient for Poster Depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(PosterGradient)
                )

                // Type Badge (Top Left)
                Surface(
                    color = badgeColor.copy(alpha = 0.92f),
                    shape = BadgeShape,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    )
                }

                // Circular Rating Badge (Top Right)
                if (media.rating > 0.0) {
                    CircularRatingBadge(
                        rating = media.rating,
                        size = 24.dp,
                        strokeWidth = 1.8.dp,
                        textSize = 7,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                    )
                }

                // Restricted +18 Badge (Bottom Left)
                if (media.restricted18) {
                    Surface(
                        color = ColorRestricted,
                        shape = LockBadgeShape,
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = "+18",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Title (Uniform 2 lines)
        Text(
            text = media.title,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle: Year & Media Type / Genre
        val categoryLabel = remember(category) {
            when (category) {
                MediaClassifier.CATEGORY_ANIME -> "Anime"
                MediaClassifier.CATEGORY_DORAMA -> "Dorama"
                MediaClassifier.CATEGORY_MOVIE -> "Filme"
                else -> "Série"
            }
        }
        val subtitle = remember(media.releaseYear, categoryLabel) {
            if (media.releaseYear.isNotBlank()) "${media.releaseYear} • $categoryLabel" else categoryLabel
        }

        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 0.dp)
        )
    }
}
