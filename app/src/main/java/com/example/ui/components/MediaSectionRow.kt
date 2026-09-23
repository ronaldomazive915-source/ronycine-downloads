package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.TextSecondary
import com.example.util.directionLockedHorizontalScroll

private val AccentShape = RoundedCornerShape(2.dp)
private val ViewAllShape = RoundedCornerShape(4.dp)

@Composable
fun MediaSectionRow(
    title: String,
    items: List<MediaEntity>,
    onItemClick: (MediaEntity) -> Unit,
    onViewAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Section Header
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
                // Red vertical accent indicator
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(BrandRed, AccentShape)
                )

                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }

            if (onViewAllClick != null) {
                Row(
                    modifier = Modifier
                        .clip(ViewAllShape)
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

        // Horizontal Row of Media Cards (Native optimized scroll)
        var isScrollEnabled by remember { mutableStateOf(true) }

        LazyRow(
            userScrollEnabled = isScrollEnabled,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(top = 2.dp, bottom = 4.dp)
                .directionLockedHorizontalScroll { enabled ->
                    if (isScrollEnabled != enabled) {
                        isScrollEnabled = enabled
                    }
                }
        ) {
            items(
                items = items,
                key = { "${it.mediaType}:${it.tmdbId}" },
                contentType = { "media_card" }
            ) { media ->
                MediaCard(
                    media = media,
                    onClick = { onItemClick(media) }
                )
            }
        }
    }
}
