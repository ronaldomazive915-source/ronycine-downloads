package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * RonycineSmileLoader - O indicador de processamento padrão, leve e elegante do RONYCINE.
 * Design minimalista e premium:
 *  •   •
 *   \_/
 *
 * Características:
 * - Movimento suave de "respiração" (scale 0.94f - 1.05f)
 * - Curvatura amigável e dinâmica
 * - Zero rotação, zero tremores, consumo mínimo de CPU/GPU
 * - Suporte a prefers-reduced-motion
 * - Transição suave de entrada e saída
 */
@Composable
fun RonycineSmileLoader(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = Color.White,
    isVisible: Boolean = true,
    delayMillis: Int = 0,
    progress: Float? = null
) {
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    var showIndicator by remember { mutableStateOf(isVisible || progress != null) }

    LaunchedEffect(isVisible, progress, delayMillis) {
        if (isVisible || progress != null) {
            if (progress == null && delayMillis > 0) {
                delay(delayMillis.toLong())
            }
            showIndicator = true
        } else {
            showIndicator = false
        }
    }

    if (showIndicator) {
        val infiniteTransition = rememberInfiniteTransition(label = "ronycine_smile_transition")

        // Sutil movimento de respiração (escala suave entre 0.94f e 1.05f)
        val breathScale by if (progress == null && !isReducedMotion) {
            infiniteTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ronycine_breath_scale"
            )
        } else if (progress != null) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            remember(clampedProgress) { mutableFloatStateOf(0.68f + 0.32f * clampedProgress) }
        } else {
            remember { mutableFloatStateOf(1f) }
        }

        // Variação suave da curvatura do sorriso
        val smileCurvature by if (progress == null && !isReducedMotion) {
            infiniteTransition.animateFloat(
                initialValue = 0.60f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ronycine_smile_curvature"
            )
        } else if (progress != null) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            remember(clampedProgress) { mutableFloatStateOf(0.20f + 0.75f * clampedProgress) }
        } else {
            remember { mutableFloatStateOf(0.75f) }
        }

        // Opacidade dos olhos e traço acompanhando progresso ou constante
        val baseAlpha = if (progress != null) {
            (progress.coerceIn(0f, 1f) * 1.35f).coerceIn(0.25f, 1f)
        } else {
            1f
        }

        Canvas(
            modifier = modifier
                .size(size)
                .graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                    alpha = baseAlpha
                }
        ) {
            val canvasSize = this.size
            val strokeWidth = (canvasSize.width * 0.085f).coerceAtLeast(1.5f)

            // Olhos (• •) perfeitamente proporcionais e centrados
            val eyeRadius = canvasSize.width * 0.075f
            val eyeY = canvasSize.height * 0.36f
            val leftEyeX = canvasSize.width * 0.34f
            val rightEyeX = canvasSize.width * 0.66f

            drawCircle(
                color = color,
                radius = eyeRadius,
                center = Offset(leftEyeX, eyeY)
            )

            drawCircle(
                color = color,
                radius = eyeRadius,
                center = Offset(rightEyeX, eyeY)
            )

            // Arco do sorriso (\_/) elegante e com pontas arredondadas
            val smileWidth = canvasSize.width * 0.46f
            val startX = (canvasSize.width - smileWidth) / 2f
            val endX = startX + smileWidth
            val smileY = canvasSize.height * 0.54f
            val smileHeight = canvasSize.height * 0.24f

            val path = Path().apply {
                moveTo(startX, smileY)
                quadraticTo(
                    startX + (smileWidth / 2f),
                    smileY + (smileHeight * smileCurvature * 1.8f),
                    endX,
                    smileY
                )
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Overlay de carregamento centralizado com o sorriso e mensagem discreta.
 */
@Composable
fun RonycineLoadingOverlay(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    message: String? = null,
    size: Dp = 44.dp,
    color: Color = Color.White
) {
    if (isVisible) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RonycineSmileLoader(size = size, color = color)
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = message.lowercase(),
                        color = color.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}
