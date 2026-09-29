package com.makeeb.feature.settings

import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.KeyboardSize
import com.makeeb.core.settings.ScreenOrientation
import com.makeeb.core.settings.hasDefaultSizes
import com.makeeb.core.settings.size
import com.makeeb.core.settings.withDefaultSizes
import com.makeeb.core.settings.withSize
import com.makeeb.ui.components.ActionRow
import com.makeeb.ui.components.SliderRow
import kotlin.math.roundToInt

/**
 * The keyboard's height and bottom gap, one pair of sliders per orientation, and one reset for
 * all of them. Each title starts with its orientation, because the companion itself may be held
 * either way: the screen's current shape says nothing about which slider the user means.
 */
internal fun keyboardSizeRows(
    preferences: KeyboardPreferences,
    onUpdate: ((KeyboardPreferences) -> KeyboardPreferences) -> Unit,
): List<SettingRow> = ScreenOrientation.entries.flatMap { orientation ->
    val size = preferences.size(orientation)
    fun change(transform: (KeyboardSize) -> KeyboardSize) = onUpdate { it.withSize(orientation, transform(it.size(orientation))) }
    val heightTitle = "${orientation.name} height"
    val heightSubtitle = if (orientation == ScreenOrientation.Landscape) "At 100% the rows fit a phone's short landscape screen" else null
    val gapTitle = "${orientation.name} bottom gap"
    val gapSubtitle = "Raises the keys off the bottom edge"
    listOf(
        SettingRow(heightTitle, heightSubtitle, "keyboard size tall short bigger smaller rows orientation") {
            SliderRow(
                title = heightTitle,
                subtitle = heightSubtitle,
                value = size.heightScale,
                onValueChange = { v -> change { it.copy(heightScale = v) } },
                valueRange = KeyboardSize.MIN_HEIGHT_SCALE..KeyboardSize.MAX_HEIGHT_SCALE,
                valueLabel = "${(size.heightScale * 100).roundToInt()}%",
                steps = steps(KeyboardSize.MAX_HEIGHT_SCALE - KeyboardSize.MIN_HEIGHT_SCALE, HEIGHT_STEP),
            )
        },
        SettingRow(gapTitle, gapSubtitle, "keyboard size offset space below lift raise padding margin orientation") {
            SliderRow(
                title = gapTitle,
                subtitle = gapSubtitle,
                value = size.bottomOffset,
                onValueChange = { v -> change { it.copy(bottomOffset = v) } },
                valueRange = 0f..KeyboardSize.MAX_BOTTOM_OFFSET,
                valueLabel = "${size.bottomOffset.roundToInt()} $lengthUnit",
                steps = steps(KeyboardSize.MAX_BOTTOM_OFFSET, BOTTOM_OFFSET_STEP),
            )
        },
    )
} + SettingRow("Keyboard size", RESET_SUBTITLE, "reset default height bottom gap portrait landscape") {
    ActionRow(
        title = "Keyboard size",
        subtitle = RESET_SUBTITLE,
        action = "Reset",
        onClick = { onUpdate { it.withDefaultSizes() } },
        enabled = !preferences.hasDefaultSizes,
    )
}

/** A slider's positions between its ends, for a [range] walked in [step]s. */
private fun steps(range: Float, step: Float) = (range / step).roundToInt() - 1

private const val HEIGHT_STEP = 0.05f
private const val BOTTOM_OFFSET_STEP = 4f
private const val RESET_SUBTITLE = "Back to 100% and no gap, in both orientations"
