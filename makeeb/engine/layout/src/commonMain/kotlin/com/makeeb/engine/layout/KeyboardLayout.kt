package com.makeeb.engine.layout

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.ShiftState

/** A complete key arrangement for one [mode]. Widths are in key units (a letter key is 1). */
data class KeyboardLayout(
    val id: String,
    val mode: KeyboardMode,
    val rows: List<KeyRow>,
) {
    /** The widest row. Narrower rows are centred, e.g. the `asdf` row on QWERTY. */
    val unitsPerRow: Float = rows.maxOfOrNull { it.units } ?: 0f

    /** Sum of row height weights: a layout with a number row is 4.8 rows tall, not 5. */
    val totalHeightWeight: Float = rows.sumOf { it.heightWeight.toDouble() }.toFloat()

    /** Every key bound to a single character, keyed by that character (for swipe decoding). */
    val characterKeys: Map<Char, Key> by lazy {
        rows.flatMap { it.keys }
            .mapNotNull { key -> (key.action as? KeyAction.Text)?.text?.singleOrNull()?.let { it.lowercaseChar() to key } }
            .toMap()
    }
}

data class KeyRow(
    val keys: List<Key>,
    /** Height relative to a standard row; the number row is shorter ([NUMBER_ROW_HEIGHT_WEIGHT]). */
    val heightWeight: Float = 1f,
) {
    val units: Float = keys.sumOf { it.width.toDouble() }.toFloat()
}

data class Key(
    val action: KeyAction,
    val label: String,
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.Character,
    /** Long-press alternatives, most likely first. */
    val alternates: List<String> = emptyList(),
    /** A small secondary label, e.g. the digit on a top-row letter. */
    val hint: String? = null,
    /** A small line under the label, e.g. the letters on a phone-pad digit. */
    val caption: String? = null,
) {
    /** The label to draw for the current shift state. Only letter keys change case. */
    fun displayLabel(shift: ShiftState): String =
        if (shift.isUppercase && style == KeyStyle.Character && action is KeyAction.Text) label.uppercase() else label

    fun displayAlternates(shift: ShiftState): List<String> =
        if (shift.isUppercase && style == KeyStyle.Character) alternates.map { it.uppercase() } else alternates
}

/** Relative height of the optional number row, as on the platform keyboards. */
const val NUMBER_ROW_HEIGHT_WEIGHT = 0.8f

enum class KeyStyle {
    /** Letters, digits and symbols. */
    Character,

    /** Shift, backspace, mode switches, globe, emoji. */
    Modifier,

    Space,

    /** Enter/action key, drawn with the accent colour. */
    Enter,
}
