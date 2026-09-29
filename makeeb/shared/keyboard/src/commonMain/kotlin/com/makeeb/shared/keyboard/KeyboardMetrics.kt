package com.makeeb.shared.keyboard

import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.KeyboardSize
import com.makeeb.core.settings.ScreenOrientation
import com.makeeb.core.settings.size
import com.makeeb.engine.layout.NUMBER_ROW_HEIGHT_WEIGHT
import kotlin.math.max
import kotlin.math.min

/** The whole screen in dp (Android) / points (iOS); its shape picks the user's portrait or landscape size. */
data class ScreenSize(val width: Float, val height: Float) {
    val orientation: ScreenOrientation get() = ScreenOrientation.of(width, height)
}

/**
 * Keyboard sizes in dp (Android) / points (iOS), shared by both renderers so the keyboard is the
 * same size everywhere. The key area height is fixed per preferences and screen, not per layout:
 * every mode (letters, symbols, emoji…) fills the same area, so the keyboard never jumps.
 */
object KeyboardMetrics {
    const val STRIP_HEIGHT = 44f
    const val BOTTOM_PADDING = 4f

    /** Space beside the outermost keys on Android; clears curved display edges. iOS uses none. */
    const val SIDE_INSET = 10f

    /**
     * The widest the keys get. A phone layout stretched across a tablet or a landscape phone turns
     * keys into slabs, so past this (where Material's expanded window class starts) the keys keep
     * this width, centred. Touches beside them still reach the nearest key.
     */
    const val MAX_KEYS_WIDTH = 840f
    private const val BASE_ROW_HEIGHT = 54f
    private const val BASE_ROWS = 4f

    /**
     * A row fits the screen when it takes at most this share of the screen's height. On a landscape
     * phone (about 400dp tall) that is ~36dp, like the system keyboards, so the app keeps room;
     * portrait phones and tablets keep the full [BASE_ROW_HEIGHT].
     */
    private const val MAX_ROW_SHARE_OF_SCREEN = 0.09f

    /**
     * The row that fits [screen], times the user's height for its orientation. Scaling after
     * fitting keeps the slider effective in landscape too. With no [screen] (unknown), the
     * portrait size at full height.
     */
    fun rowHeight(preferences: KeyboardPreferences, screen: ScreenSize? = null): Float {
        val fitted = if (screen == null) BASE_ROW_HEIGHT else min(BASE_ROW_HEIGHT, screen.height * MAX_ROW_SHARE_OF_SCREEN)
        return fitted * size(preferences, screen).heightScale
    }

    fun keysAreaHeight(preferences: KeyboardPreferences, screen: ScreenSize? = null): Float {
        val rows = BASE_ROWS + if (preferences.numberRow) NUMBER_ROW_HEIGHT_WEIGHT else 0f
        return rowHeight(preferences, screen) * rows
    }

    /** The user's gap under the keys; it adds to any inset the system keeps there (Android's navigation bar). */
    fun bottomOffset(preferences: KeyboardPreferences, screen: ScreenSize? = null): Float = size(preferences, screen).bottomOffset

    /** Everything the keyboard draws or leaves empty: strip, keys, padding and the user's gap. */
    fun totalHeight(preferences: KeyboardPreferences, screen: ScreenSize? = null): Float =
        STRIP_HEIGHT + keysAreaHeight(preferences, screen) + BOTTOM_PADDING + bottomOffset(preferences, screen)

    /**
     * Space left and right of the keys in a key area [width] wide: at least [sideInset], and more
     * once the keys would pass [MAX_KEYS_WIDTH]. It depends on the width alone, never the layout,
     * so every page gets the same inset and no key moves between modes.
     */
    fun horizontalInset(width: Float, sideInset: Float = SIDE_INSET): Float = max(sideInset, (width - MAX_KEYS_WIDTH) / 2)

    private fun size(preferences: KeyboardPreferences, screen: ScreenSize?): KeyboardSize =
        preferences.size(screen?.orientation ?: ScreenOrientation.Portrait)
}
