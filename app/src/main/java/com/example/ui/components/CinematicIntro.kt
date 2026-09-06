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
    val totalDuration = if (isFirstTime) 1900L else 750L

    // Safety timeout fallback (strictly maximum 2.8 seconds)
    LaunchedEffect(Unit) {
        // Stage 1: Initial black canvas with expanding center red point of light
        stage = 1
        delay(if (isFirstTime) 350L else 150L)

        // Stage 2: Logo and title emergence + glow bloom
        stage = 2
        delay(if (isFirstTime) 650L else 250L)

        // Stage 3: Light flare sweep through the logo
        stage = 3
        delay(if (isFirstTime) 550L else 200L)

        // Stage 4: Smooth fade-out to Home
        stage = 4
        delay(if (isFirstTime) 350L else 150L)

        onIntroFinished()
    }

    // Light Glow Animation
    val glowScale by animateFloatAsState(
        targetValue = when (stage) {
            0 -> 0.1f
            1 -> 0.7f
            2 -> 1.0f
            3 -> 1.15f
            else -> 0.8f
        },
        animationSpec = tween(
            durationMillis = if (isFirstTime) 600 else 250,
            easing = FastOutSlowInEasing
        ),
        label = "glowScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = when (stage) {
            0 -> 0f
            1 -> 0.45f
            2 -> 0.65f
            3 -> 0.8f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (isFirstTime) 500 else 200,
            easing = LinearEasing
        ),
        label = "glowAlpha"
    )

    // Logo scale and opacity
    val logoScale by animateFloatAsState(
        targetValue = when (stage) {
            0 -> 0.75f
            1 -> 0.85f
            2, 3 -> 1.0f
            else -> 0.95f
        },
        animationSpec = tween(
            durationMillis = if (isFirstTime) 700 else 250,
            easing = FastOutSlowInEasing
        ),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = when (stage) {
            0, 1 -> 0f
            2, 3 -> 1f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (isFirstTime) 500 else 200,
            easing = FastOutSlowInEasing
        ),
        label = "logoAlpha"
    )

    // Shimmer flare transition
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
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

                // Light streak highlight on the badge
                if (stage >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.3f),
                                        Color.Transparent
                                    ),
                                    start = androidx.compose.ui.geometry.Offset(shimmerTranslate, 0f),
                                    end = androidx.compose.ui.geometry.Offset(shimmerTranslate + 60f, 100f)
                                ),
                                shape = RoundedCornerShape(18.dp)
                            )
                    )
                }
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
