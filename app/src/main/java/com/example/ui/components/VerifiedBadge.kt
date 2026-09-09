package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom Verified Badge Component for RONYCINE.
 * Displays a vibrant blue verification badge with a white checkmark.
 */
@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    showToastOnClick: Boolean = true
) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val badgeBluePrimary = Color(0xFF0095F6)
    val badgeBlueGradientBottom = Color(0xFF1877F2)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.75f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = "Perfil verificado" }
                .testTag("verified_badge")
                .shadow(elevation = 2.dp, shape = CircleShape, ambientColor = badgeBluePrimary, spotColor = badgeBluePrimary)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(badgeBluePrimary, badgeBlueGradientBottom)
                    )
                )
                .border(0.6.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = showToastOnClick
                ) {
                    Toast.makeText(context, "Perfil verificado", Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(size * 0.65f)
            )
        }
    }
}

/**
 * Helper composable to render a profile name with an optional Verified Badge inline.
 */
@Composable
fun ProfileNameWithBadge(
    name: String,
    isVerified: Boolean,
    modifier: Modifier = Modifier,
    nameColor: Color = Color.White,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    badgeSize: Dp = 15.dp,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            color = nameColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (isVerified) {
            VerifiedBadge(size = badgeSize)
        }
    }
}
