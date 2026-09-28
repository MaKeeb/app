package com.makeeb.core.model

/**
 * Keyboard colours as 0xAARRGGBB, so the Compose renderer (Android) and the native renderer
 * (iOS extension) share one palette. User themes will produce these (board: `theming`).
 */
data class KeyboardPalette(
    /** Android only. iOS draws no background: keys sit directly on the system keyboard glass. */
    val background: Long,
    val key: Long,
    val keyPressed: Long,
    val modifierKey: Long,
    val accentKey: Long,
    val onKey: Long,
    val onAccentKey: Long,
    val hint: Long,
    val popup: Long,
    val onPopup: Long,
    val popupSelected: Long,
    val onPopupSelected: Long,
    val divider: Long,
) {
    companion object {
        val Light = KeyboardPalette(
            background = 0xFFE6E9EF,
            key = 0xFFFFFFFF,
            keyPressed = 0xFFCDD3DE,
            modifierKey = 0xFFC5CCD8,
            accentKey = 0xFF3F5EFB,
            onKey = 0xFF15171C,
            onAccentKey = 0xFFFFFFFF,
            hint = 0xFF6B7384,
            popup = 0xFFFFFFFF,
            onPopup = 0xFF15171C,
            popupSelected = 0xFF3F5EFB,
            onPopupSelected = 0xFFFFFFFF,
            divider = 0x22000000,
        )

        // In dark themes function keys sit a step lighter than letter keys; darker ones read as
        // holes in the keyboard.
        val Dark = KeyboardPalette(
            background = 0xFF14161B,
            key = 0xFF2B2F38,
            keyPressed = 0xFF4F5767,
            modifierKey = 0xFF414856,
            accentKey = 0xFF7B90FF,
            onKey = 0xFFE9ECF2,
            onAccentKey = 0xFF0B0E22,
            hint = 0xFF9199A8,
            popup = 0xFF363B46,
            onPopup = 0xFFE9ECF2,
            popupSelected = 0xFF7B90FF,
            onPopupSelected = 0xFF0B0E22,
            divider = 0x33FFFFFF,
        )
    }
}
