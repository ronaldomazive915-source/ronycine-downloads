package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.MediaReleaseInfo
import com.example.ui.theme.BrandRed
import com.example.ui.theme.TextSecondary

@Composable
fun MediaReleaseBadge(
    releaseInfo: MediaReleaseInfo?,
    modifier: Modifier = Modifier
) {
    if (releaseInfo == null) return

    val headerLabel = when {
        releaseInfo.isNextEpisode -> "PRÓXIMO EPISÓDIO"
        releaseInfo.isMovie -> "LANÇAMENTO"
        else -> "PRÓXIMO LANÇAMENTO"
    }

    val primaryDetailText = when {
        releaseInfo.isNextEpisode && !releaseInfo.formattedEpisodeLabel.isNullOrBlank() -> {
            "${releaseInfo.formattedEpisodeLabel} • ${releaseInfo.formattedShortDate}"
        }
        else -> releaseInfo.formattedReleaseDate
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("media_release_badge"),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF14141A),
        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.35f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BrandRed.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandRed.copy(alpha = 0.2f))
                    .border(1.dp, BrandRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = headerLabel,
                        color = BrandRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                    if (releaseInfo.isUnreleasedContent && releaseInfo.isMovie) {
                        Surface(
                            color = BrandRed.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "EM BREVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = primaryDetailText,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (releaseInfo.isNextEpisode && !releaseInfo.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = releaseInfo.episodeTitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
