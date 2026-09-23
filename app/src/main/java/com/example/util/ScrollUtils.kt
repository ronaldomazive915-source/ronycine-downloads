package com.example.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Custom modifier to implement a "direction lock" for horizontal carousels.
 * Prevents horizontal scroll from triggering when the user is clearly scrolling vertically.
 */
fun Modifier.directionLockedHorizontalScroll(
    onScrollEnabled: (Boolean) -> Unit
): Modifier = composed {
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    var directionLocked by remember { mutableStateOf(false) }
    
    // Threshold in pixels (approx 10dp)
    val threshold = 10f

    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            startX = down.position.x
            startY = down.position.y
            directionLocked = false
            onScrollEnabled(true)

            do {
                val event = awaitPointerEvent()
                val changes = event.changes
                
                if (changes.any { it.pressed } && !directionLocked) {
                    val firstChange = changes.first()
                    val currentX = firstChange.position.x
                    val currentY = firstChange.position.y
                    
                    val deltaX = abs(currentX - startX)
                    val deltaY = abs(currentY - startY)
                    
                    if (deltaX > threshold || deltaY > threshold) {
                        directionLocked = true
                        val horizontalDominant = deltaX >= deltaY
                        onScrollEnabled(horizontalDominant)
                    }
                }
            } while (changes.any { it.pressed })
            
            directionLocked = false
            onScrollEnabled(true)
        }
    }
}
