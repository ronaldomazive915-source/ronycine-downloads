package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.remote.UserProfile
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface

/**
 * Single source of truth for rendering user profile avatars across RONYCINE.
 * Prioritizes:
 * 1. Personal photo (photoUrl / custom upload)
 * 2. Selected preset avatar (avatarUrl)
 * 3. Default initials / Cinephile icon fallback
 */
@Composable
fun ProfileAvatar(
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = CardBorder.copy(alpha = 0.5f),
    showActiveCheck: Boolean = false,
    showCameraBadge: Boolean = false,
    testTag: String = "profile_avatar",
    onClick: (() -> Unit)? = null
) {
    val effectiveUrl = remember(profile?.avatarUrl, profile?.photoUrl, profile?.avatarType) {
        when {
            // Priority 1: User's custom photo
            !profile?.photoUrl.isNullOrBlank() && profile?.avatarType == "CUSTOM" -> profile?.photoUrl
            !profile?.photoUrl.isNullOrBlank() && profile?.avatarUrl.isNullOrBlank() -> profile?.photoUrl
            // Priority 2: Selected catalog/preset avatar
            !profile?.avatarUrl.isNullOrBlank() && profile?.avatarUrl != "DEFAULT" -> profile?.avatarUrl
            !profile?.photoUrl.isNullOrBlank() -> profile?.photoUrl
            // Priority 3: Fallback (null)
            else -> null
        }
    }

    ProfileAvatarContent(
        imageUrl = effectiveUrl,
        displayName = profile?.name,
        modifier = modifier,
        size = size,
        borderWidth = borderWidth,
        borderColor = borderColor,
        showActiveCheck = showActiveCheck,
        showCameraBadge = showCameraBadge,
        testTag = testTag,
        onClick = onClick
    )
}

@Composable
fun ProfileAvatar(
    imageUrl: String?,
    displayName: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = CardBorder.copy(alpha = 0.5f),
    showActiveCheck: Boolean = false,
    showCameraBadge: Boolean = false,
    testTag: String = "profile_avatar",
    onClick: (() -> Unit)? = null
) {
    ProfileAvatarContent(
        imageUrl = imageUrl,
        displayName = displayName,
        modifier = modifier,
        size = size,
        borderWidth = borderWidth,
        borderColor = borderColor,
        showActiveCheck = showActiveCheck,
        showCameraBadge = showCameraBadge,
        testTag = testTag,
        onClick = onClick
    )
}

@Composable
private fun ProfileAvatarContent(
    imageUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = CardBorder.copy(alpha = 0.5f),
    showActiveCheck: Boolean = false,
    showCameraBadge: Boolean = false,
    testTag: String = "profile_avatar",
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(clickableModifier)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Main Avatar Circle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(DarkSurface)
                .border(borderWidth, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName ?: "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        AvatarShimmerPlaceholder()
                    },
                    error = {
                        AvatarFallbackBadge(displayName = displayName, size = size)
                    }
                )
            } else {
                AvatarFallbackBadge(displayName = displayName, size = size)
            }
        }

        // Active Check Badge
        if (showActiveCheck) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.36f).coerceIn(16.dp, 28.dp))
                    .clip(CircleShape)
                    .background(BrandRed)
                    .border(1.5.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Ativo",
                    tint = Color.White,
                    modifier = Modifier.size((size * 0.22f).coerceIn(10.dp, 16.dp))
                )
            }
        }

        // Camera Edit Badge
        if (showCameraBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.36f).coerceIn(18.dp, 30.dp))
                    .clip(CircleShape)
                    .background(BrandRed)
                    .border(1.5.dp, DarkBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Alterar Foto",
                    tint = Color.White,
                    modifier = Modifier.size((size * 0.20f).coerceIn(10.dp, 16.dp))
                )
            }
        }
    }
}

@Composable
private fun AvatarShimmerPlaceholder() {
    val transition = rememberInfiniteTransition(label = "avatar_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_shimmer_anim"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            DarkSurface,
            DarkSurface.copy(alpha = 0.6f),
            DarkSurface
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shimmerBrush)
    )
}

@Composable
private fun AvatarFallbackBadge(
    displayName: String?,
    size: Dp
) {
    val initial = remember(displayName) {
        displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        BrandRed.copy(alpha = 0.45f),
                        DarkSurface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!initial.isNullOrBlank() && size >= 32.dp) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.42f).sp,
                letterSpacing = 0.5.sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size((size * 0.55f).coerceIn(14.dp, 36.dp))
            )
        }
    }
}
