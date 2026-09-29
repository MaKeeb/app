package com.makeeb.core.settings

/**
 * How big the keyboard is in one screen orientation. Portrait and landscape are set and stored
 * separately, because a landscape phone has less than half the height: a size that suits one
 * orientation crowds the app out in the other.
 */
data class KeyboardSize(
    /**
     * Multiplier on the row height that fits the screen, [MIN_HEIGHT_SCALE]..[MAX_HEIGHT_SCALE].
     * The fitted row is already shorter on a landscape phone, so 1 is the right default in both
     * orientations.
     */
    val heightScale: Float = 1f,
    /**
     * Empty space under the keys in dp (Android) or points (iOS), 0..[MAX_BOTTOM_OFFSET]. It raises
     * the keys off the bottom edge, on top of whatever the system keeps there (Android's
     * navigation bar).
     */
    val bottomOffset: Float = 0f,
) {
    /** Within the ranges the settings offer; stored values may come from anywhere. */
    fun coerced(): KeyboardSize = KeyboardSize(
        heightScale = heightScale.coerceIn(MIN_HEIGHT_SCALE, MAX_HEIGHT_SCALE),
        bottomOffset = bottomOffset.coerceIn(0f, MAX_BOTTOM_OFFSET),
    )

    companion object {
        const val MIN_HEIGHT_SCALE = 0.8f
        const val MAX_HEIGHT_SCALE = 1.3f
        const val MAX_BOTTOM_OFFSET = 48f
    }
}

/** Which of the user's [KeyboardSize]s applies. */
enum class ScreenOrientation {
    Portrait,
    Landscape,
    ;

    companion object {
        /**
         * Landscape when the screen is wider than it is tall. Decided from the size rather than the
         * OS's rotation, so both platforms agree; a square screen (some foldables) is portrait.
         */
        fun of(width: Float, height: Float): ScreenOrientation = if (width > height) Landscape else Portrait
    }
}

fun KeyboardPreferences.size(orientation: ScreenOrientation): KeyboardSize = when (orientation) {
    ScreenOrientation.Portrait -> portraitSize
    ScreenOrientation.Landscape -> landscapeSize
}

fun KeyboardPreferences.withSize(orientation: ScreenOrientation, size: KeyboardSize): KeyboardPreferences = when (orientation) {
    ScreenOrientation.Portrait -> copy(portraitSize = size)
    ScreenOrientation.Landscape -> copy(landscapeSize = size)
}

/** Both orientations back to the default height and no gap under the keys. */
fun KeyboardPreferences.withDefaultSizes(): KeyboardPreferences = copy(portraitSize = KeyboardSize(), landscapeSize = KeyboardSize())

val KeyboardPreferences.hasDefaultSizes: Boolean get() = portraitSize == KeyboardSize() && landscapeSize == KeyboardSize()
