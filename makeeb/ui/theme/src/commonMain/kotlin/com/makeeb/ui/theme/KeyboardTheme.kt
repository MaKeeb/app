package com.makeeb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.core.model.KeyboardPalette
import com.makeeb.core.model.KeyboardTokens

/** Compose view of a [KeyboardPalette]. */
@Immutable
data class KeyboardColors(
    val background: Color,
    val key: Color,
    val keyPressed: Color,
    val modifierKey: Color,
    val accentKey: Color,
    val onKey: Color,
    val onAccentKey: Color,
    val hint: Color,
    val popup: Color,
    val onPopup: Color,
    val popupSelected: Color,
    val onPopupSelected: Color,
    val divider: Color,
)

fun KeyboardPalette.toColors() = KeyboardColors(
    background = Color(background),
    key = Color(key),
    keyPressed = Color(keyPressed),
    modifierKey = Color(modifierKey),
    accentKey = Color(accentKey),
    onKey = Color(onKey),
    onAccentKey = Color(onAccentKey),
    hint = Color(hint),
    popup = Color(popup),
    onPopup = Color(onPopup),
    popupSelected = Color(popupSelected),
    onPopupSelected = Color(onPopupSelected),
    divider = Color(divider),
)

/** Spacing and type inside the key area. Overall heights come from `KeyboardMetrics`. */
@Immutable
data class KeyboardDimensions(
    val keyGap: Dp = 6.dp,
    val rowGap: Dp = 8.dp,
    val keyCornerRadius: Dp = 8.dp,
    val keyTextSize: TextUnit = 22.sp,
    val modifierTextSize: TextUnit = 15.sp,
    val hintTextSize: TextUnit = 10.sp,
    val iconSize: Dp = KeyboardTokens.KEY_ICON_SIZE.dp,
)

val LocalKeyboardColors = staticCompositionLocalOf { KeyboardPalette.Light.toColors() }
val LocalKeyboardDimensions = staticCompositionLocalOf { KeyboardDimensions() }

object KeyboardTheme {
    val colors: KeyboardColors
        @Composable @ReadOnlyComposable get() = LocalKeyboardColors.current

    val dimensions: KeyboardDimensions
        @Composable @ReadOnlyComposable get() = LocalKeyboardDimensions.current
}

@Composable
fun MaKeebKeyboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) KeyboardPalette.Dark else KeyboardPalette.Light
    CompositionLocalProvider(
        LocalKeyboardColors provides palette.toColors(),
        LocalKeyboardDimensions provides KeyboardDimensions(),
        content = content,
    )
}
