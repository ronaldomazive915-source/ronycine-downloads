package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary

/**
 * Modern compact Media Card for movies and TV series.
 * Features:
 * - 2:3 aspect ratio poster.
 * - Circular percentage rating badge positioned in the top-right corner.
 * - Discreet type badge (FILME / SÉRIE) in the top-left corner.
 * - Uniform 2-line title layout with ellipsis.
 * - Year and media type / genre subtitle (e.g. "2026 • Filme").
 * - Fluid touch scale feedback.
 */
@Composable
fun MediaCard(
    media: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(115.dp),
    posterHeight: Dp? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "media_card_press_scale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("media_card_${media.tmdbId}")
    ) {
        // Poster Card with 2:3 aspect ratio
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (posterHeight != null) Modifier.height(posterHeight) else Modifier.aspectRatio(2f / 3f))
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageUrl = media.posterPath?.ifBlank { null } ?: media.backdropPath

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = BrandRed,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF18181B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (media.mediaType == "tv") Icons.Default.Tv else Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                )

                // Dark Bottom Gradient for Poster Depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Type Badge (Top Left)
                Surface(
                    color = BrandRed.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (media.mediaType == "tv") "SÉRIE" else "FILME",
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
        val mediaTypeLabel = if (media.mediaType == "tv") "Série" else "Filme"
        val subtitle = when {
            media.releaseYear.isNotBlank() -> "${media.releaseYear} • $mediaTypeLabel"
            else -> mediaTypeLabel
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
