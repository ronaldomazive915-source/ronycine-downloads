package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import kotlinx.coroutines.delay

@Composable
fun CinematicIntro(
    isFirstTime: Boolean = true,
    onIntroFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by remember { mutableIntStateOf(0) }
    val totalDuration = if (isFirstTime) 800L else 400L

    // Safety timeout fallback
    LaunchedEffect(Unit) {
        // Stage 1: Quick fade in
        stage = 2
        delay(if (isFirstTime) 600L else 300L)

        // Stage 2: Quick fade out
        stage = 4
        delay(if (isFirstTime) 200L else 100L)

        onIntroFinished()
    }

    // Light Glow Animation
    val glowScale by animateFloatAsState(
        targetValue = if (stage >= 2) 1.0f else 0.5f,
        animationSpec = tween(durationMillis = 300),
        label = "glowScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 0.65f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "glowAlpha"
    )

    // Logo scale and opacity
    val logoScale by animateFloatAsState(
        targetValue = if (stage >= 2) 1.0f else 0.8f,
        animationSpec = tween(durationMillis = 300),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "logoAlpha"
    )



    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("cinematic_intro"),
        contentAlignment = Alignment.Center
    ) {
        // Red Cinematic Ambient Glow (radial gradient)
        Box(
            modifier = Modifier
                .size(360.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BrandRed.copy(alpha = 0.5f),
                            BrandRed.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Central Logo & Brand Typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(logoScale)
                .alpha(logoAlpha)
        ) {
            // Emblem Play Icon Badge
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(BrandRed, shape = RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )


                // Light streak highlight on the badge (static)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Brand Wordmark: RONYCINE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "RONY",
                    color = BrandRed,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "CINE",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
