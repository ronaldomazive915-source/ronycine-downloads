package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    modifier: Modifier = Modifier.width(115.dp),
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
        media.posterPath?.ifBlank { null } ?: media.backdropPath
    }
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(240, 360)
            .crossfade(200)
            .placeholder(android.R.drawable.progress_horizontal) // Simple native placeholder
            .error(android.R.drawable.ic_menu_report_image)
            .build()
    }

    Column(
        modifier = modifier
            .clip(PosterCardShape)
            .clickable(onClick = onClick)
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
            shadowElevation = 2.dp
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
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Circular Rating Badge (Top Right)
                if (media.rating > 0.0) {
                    CircularRatingBadge(
                        rating = media.rating,
                        size = 28.dp,
                        strokeWidth = 2.dp,
                        textSize = 8,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
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
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
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
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
