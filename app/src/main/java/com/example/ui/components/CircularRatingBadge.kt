package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Utility function to convert 0.0 - 10.0 scale (TMDB) to 0 - 100% integer.
 */
fun convertRatingToPercentage(rating: Double): Int {
    if (rating <= 0.0) return 0
    return (rating * 10.0).roundToInt().coerceIn(0, 100)
}

/**
 * Modern circular percentage rating badge for RONYCINE.
 * Positioned in the top corner of movie and TV show cards.
 *
 * Color Scale:
 * - High (>= 75%): Emerald Green (#10B981)
 * - Medium (60% - 74%): Amber Yellow (#F59E0B)
 * - Low (< 60%): Red (#EF4444)
 */
@Composable
fun CircularRatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 2.dp,
    textSize: Int = 8,
    showPercentSign: Boolean = true
) {
    if (rating <= 0.0) return

    val percentage = remember(rating) { convertRatingToPercentage(rating) }
    if (percentage <= 0) return

    val progress = remember(percentage) { (percentage / 100f).coerceIn(0f, 1f) }

    val ratingColor = remember(percentage) {
        when {
            percentage >= 75 -> Color(0xFF10B981) // Green (Alta)
            percentage >= 60 -> Color(0xFFF59E0B) // Amber/Yellow (Média)
            else -> Color(0xFFEF4444)             // Red (Baixa)
        }
    }

    val trackColor = remember(ratingColor) { ratingColor.copy(alpha = 0.22f) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xE608080C)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 2.dp)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = this.size.minDimension
            val radius = (canvasSize - strokePx) / 2f

            // Background Track Circle
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = strokePx)
            )

            // Progress Arc
            drawArc(
                color = ratingColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Text(
            text = if (showPercentSign) "$percentage%" else "$percentage",
            color = Color.White,
            fontSize = textSize.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
    }
}

/**
 * Compact circular TMDB rating badge for DetailScreen header and media details.
 * Shows circular progress stroke + decimal rating (e.g. 7.9) + "TMDB" label underneath.
 */
@Composable
fun TmdbCircularBadge(
    rating: Double,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    strokeWidth: Dp = 3.dp
) {
    if (rating <= 0.0) return

    val formattedScore = remember(rating) { String.format(java.util.Locale.US, "%.1f", rating) }
    val percentage = remember(rating) { (rating * 10.0).coerceIn(0.0, 100.0) }
    val progress = (percentage / 100f).toFloat()

    val ratingColor = when {
        percentage >= 75 -> Color(0xFF10B981) // Emerald Green
        percentage >= 60 -> Color(0xFFF59E0B) // Amber Gold
        else -> Color(0xFFEF4444)             // Red
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF14141A))
            .border(0.8.dp, Color(0xFF2E2E38), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 2.dp)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = this.size.minDimension
            val radius = (canvasSize - strokePx) / 2f

            // Background Track
            drawCircle(
                color = ratingColor.copy(alpha = 0.2f),
                radius = radius,
                style = Stroke(width = strokePx)
            )

            // Progress Arc
            drawArc(
                color = ratingColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = formattedScore,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 13.sp
            )
            Text(
                text = "TMDB",
                color = Color.LightGray.copy(alpha = 0.85f),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                lineHeight = 8.sp
            )
        }
    }
}
