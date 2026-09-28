package com.makeeb.shared.keyboard

import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.layout.NUMBER_ROW_HEIGHT_WEIGHT
import kotlin.math.min

/**
 * Keyboard heights in dp (Android) / points (iOS), shared by both renderers so the keyboard is
 * the same size everywhere. The key area height is fixed per preferences, not per layout: every
 * mode (letters, symbols, emoji…) fills the same area, so the keyboard never jumps.
 */
object KeyboardMetrics {
    const val STRIP_HEIGHT = 44f
    const val BOTTOM_PADDING = 4f

    /** Space beside the outermost keys on Android; clears curved display edges. iOS uses none. */
    const val SIDE_INSET = 10f
    private const val BASE_ROW_HEIGHT = 54f
    private const val BASE_ROWS = 4f

    /**
     * A row never takes more than this share of the screen height. On a landscape phone (about
     * 400dp tall) that is ~36dp, like the system keyboards; portrait screens keep the full height.
     */
    private const val MAX_ROW_SHARE_OF_SCREEN = 0.09f

    /** [screenHeight] in the same units; null when unknown (keeps the preferred height). */
    fun rowHeight(preferences: KeyboardPreferences, screenHeight: Float? = null): Float {
        val preferred = BASE_ROW_HEIGHT * preferences.heightScale
        return if (screenHeight == null) preferred else min(preferred, screenHeight * MAX_ROW_SHARE_OF_SCREEN)
    }

    fun keysAreaHeight(preferences: KeyboardPreferences, screenHeight: Float? = null): Float {
        val rows = BASE_ROWS + if (preferences.numberRow) NUMBER_ROW_HEIGHT_WEIGHT else 0f
        return rowHeight(preferences, screenHeight) * rows
    }

    fun totalHeight(preferences: KeyboardPreferences, screenHeight: Float? = null): Float =
        STRIP_HEIGHT + keysAreaHeight(preferences, screenHeight) + BOTTOM_PADDING
}
