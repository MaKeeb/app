package com.makeeb.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Ends scrolling content so its last item clears whatever covers the bottom of the screen. On iOS
 * content scrolls beneath the glass tab bar and home indicator; on Android the Scaffold has
 * already padded for (and consumed) those insets, so this is zero there.
 */
@Composable
fun ScrollEndSpacer() {
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
}
