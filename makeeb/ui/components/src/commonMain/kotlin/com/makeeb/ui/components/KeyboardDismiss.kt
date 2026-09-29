package com.makeeb.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.math.abs

/**
 * Hides the on-screen keyboard when the user drags this scrolling content, like iOS lists with
 * `keyboardDismissMode = .onDrag` and Android search screens. Compose scrollers aren't
 * UIScrollViews, so iOS doesn't do it for them. Clearing focus hides the keyboard on both
 * platforms; tapping a field brings it back.
 *
 * It watches the finger rather than the scroll: scrolling a newly focused field into view above
 * the keyboard is a scroll too, and must not close the keyboard it made room for. Events are only
 * observed, never consumed, so taps and scrolling behave as before. Put it before the scroll
 * modifier.
 */
@Composable
fun Modifier.dismissKeyboardOnDrag(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                    focusManager.clearFocus()
                    break
                }
            }
        }
    }
}
