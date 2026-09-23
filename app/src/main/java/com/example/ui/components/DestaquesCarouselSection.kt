package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.TextSecondary
import com.example.util.directionLockedHorizontalScroll

/**
 * Clean & Professional Destaques Horizontal Carousel.
 * Supports smooth horizontal swipe/scroll with snapping fling behavior.
 * Clean aesthetic without side arrows or bottom indicator dots.
 */
@Composable
fun DestaquesCarouselSection(
    items: List<MediaEntity>,
    onItemClick: (MediaEntity) -> Unit,
    onViewAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .testTag("destaques_carousel_section")
    ) {
        // Section Header with Title, Accent and "Ver todos >"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(BrandRed, RoundedCornerShape(2.dp))
                )

                Text(
                    text = "DESTAQUES",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }

            if (onViewAllClick != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onViewAllClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = com.example.util.stringI18n("action.view_all"),
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Ver todos",
                        tint = BrandRed,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Horizontal Carousel with swipe & snapping
        var isScrollEnabled by remember { mutableStateOf(true) }

        LazyRow(
            state = listState,
            userScrollEnabled = isScrollEnabled,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .testTag("destaques_carousel_lazy_row")
                .directionLockedHorizontalScroll { enabled ->
                    if (isScrollEnabled != enabled) {
                        isScrollEnabled = enabled
                    }
                }
        ) {
            itemsIndexed(
                items = items,
                key = { _, it -> "destaque_${it.mediaType}_${it.tmdbId}" },
                contentType = { _, _ -> "destaque_media_card" }
            ) { _, media ->
                MediaCard(
                    media = media,
                    onClick = { onItemClick(media) }
                )
            }
        }
    }
}
