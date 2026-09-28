package com.makeeb.engine.touch

import com.makeeb.engine.layout.KeyBounds
import com.makeeb.engine.layout.PlacedKey

/**
 * What the renderers draw on top of the keys. All bounds are in the [com.makeeb.engine.layout.LayoutGeometry]
 * coordinate space; negative `top` values extend into the suggestion strip, never above the
 * keyboard (iOS extensions cannot draw outside their view).
 */
data class TouchState(
    val pressed: Set<PlacedKey> = emptySet(),
    val preview: KeyPreview? = null,
    val popup: AlternatesPopup? = null,
    val cursorSliding: Boolean = false,
)

/** The enlarged key shown above the finger while a character key is held. */
data class KeyPreview(val key: PlacedKey, val label: String, val bounds: KeyBounds)

/** Long-press alternatives, one cell per option, with the option under the finger selected. */
data class AlternatesPopup(
    val key: PlacedKey,
    val options: List<String>,
    val cells: List<KeyBounds>,
    val selected: Int,
)

/** Tuning, in the geometry's units (pixels on Android, points on iOS). */
data class TouchConfig(
    val longPressMillis: Long = 350,
    val repeatStartMillis: Long = 400,
    val repeatIntervalMillis: Long = 60,
    /** Held delete speeds up after this many repeats… */
    val accelerateAfterRepeats: Int = 8,
    val fastRepeatIntervalMillis: Long = 30,
    /** …and, when word deletion is on, erases whole words after this many. */
    val wordDeleteAfterRepeats: Int = 20,
    val wordRepeatIntervalMillis: Long = 180,
    /** Horizontal travel on the space bar before it turns into a cursor slide. */
    val cursorSlideStart: Float = 24f,
    /** Travel per one-character cursor step while sliding. */
    val cursorStep: Float = 14f,
    /** Travel per word when sliding moves by word: words need a deliberate distance. */
    val cursorWordStep: Float = 36f,
    /** How far above the key area popups may go (the suggestion strip height). */
    val overflowAbove: Float = 44f,
) {
    companion object {
        /** Defaults scaled for a display with [density] pixels per dp/pt. */
        fun forDensity(density: Float, overflowAbove: Float = 44f * density) = TouchConfig(
            cursorSlideStart = 24f * density,
            cursorStep = 14f * density,
            cursorWordStep = 36f * density,
            overflowAbove = overflowAbove,
        )
    }
}
