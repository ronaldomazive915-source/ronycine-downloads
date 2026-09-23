package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.CalendarItem
import com.example.data.remote.CalendarMediaType
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarMediaCard(
    item: CalendarItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    val todayStr = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.format(Calendar.getInstance().time)
    }
    val tomorrowStr = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        sdf.format(cal.time)
    }

    val computedStatus = remember(item.airDate, todayStr, tomorrowStr) {
        item.getComputedStatus(todayStr, tomorrowStr)
    }

    val typeColor = when (item.mediaType) {
        CalendarMediaType.MOVIE -> BrandRed
        CalendarMediaType.SERIES -> Color(0xFF0284C7) // Sky Blue
        CalendarMediaType.ANIME -> Color(0xFFEA580C)  // Vivid Orange
        CalendarMediaType.DORAMA -> Color(0xFF9333EA) // Purple
    }

    val isToday = item.airDate == todayStr
    val isTomorrow = item.airDate == tomorrowStr

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = typeColor.copy(alpha = 0.3f)),
                onClick = onClick
            )
            .padding(2.dp)
            .testTag("calendar_card_${item.id}"),
        horizontalAlignment = Alignment.Start
    ) {
        // Poster Container (2:3 aspect ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
                .border(
                    BorderStroke(
                        if (isToday) 1.2.dp else 0.8.dp,
                        if (isToday) BrandRed.copy(alpha = 0.8f) else CardBorder.copy(alpha = 0.4f)
                    ),
                    RoundedCornerShape(8.dp)
                )
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(DarkSurface, DarkBackground)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaType == CalendarMediaType.MOVIE) Icons.Default.Movie else Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Subtle gradient scrim at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Category Badge (Top-Left)
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(typeColor)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
                    .testTag("calendar_card_type_tag")
            ) {
                Text(
                    text = item.mediaType.label,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
            }

            // Episode Badge (Top-Right)
            if (item.formattedEpisode != null) {
                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.formattedEpisode!!,
                        color = Color(0xFFFFD54F), // Amber
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Badge (Bottom-Right overlay)
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isToday -> BrandRed.copy(alpha = 0.9f)
                            isTomorrow -> Color(0xFF0284C7).copy(alpha = 0.9f)
                            else -> Color(0xCC111116)
                        }
                    )
                    .padding(horizontal = 4.5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = computedStatus,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp)
                .testTag("calendar_card_title")
        )

        // Episode title or category info
        val subtitle = when {
            !item.episodeTitle.isNullOrBlank() && !item.episodeTitle.equals("Episódio ${item.episodeNumber}", ignoreCase = true) -> item.episodeTitle
            item.formattedEpisode != null -> item.formattedEpisode
            item.mediaType == CalendarMediaType.MOVIE -> "Filme Completo"
            else -> item.mediaType.label
        }

        if (subtitle != null) {
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp)
            )
        }
    }
}
