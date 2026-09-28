package com.makeeb.shared.keyboard

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyboardPalette
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.inStripOrder
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.ThemeMode
import com.makeeb.engine.input.KeyboardState
import com.makeeb.engine.layout.KeyBounds
import com.makeeb.engine.layout.KeyStyle
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.renderIcon
import com.makeeb.engine.layout.renderLabel
import com.makeeb.engine.touch.TouchState

/**
 * A flat, draw-ready snapshot for native renderers (the iOS extension draws this with UIKit).
 * Frames are in key-area coordinates; negative `y` reaches into the suggestion strip.
 */
data class KeyboardRender(
    /** Empty while a panel replaces the keys. */
    val keys: List<RenderKey>,
    /** At most three, already in strip order (best in the middle). */
    val suggestions: List<String>,
    val preview: RenderPreview?,
    val popup: RenderPopup?,
    val theme: ThemeMode,
    /** What fills the key area; panels other than [KeyboardPanel.Keys] are drawn natively. */
    val panel: KeyboardPanel = KeyboardPanel.Keys,
    /** The strip's buttons when [suggestions] is empty; [StripAction.Settings] sits apart, at the end. */
    val stripActions: List<StripAction> = emptyList(),
) {
    fun palette(systemDark: Boolean): KeyboardPalette = when (theme) {
        ThemeMode.System -> if (systemDark) KeyboardPalette.Dark else KeyboardPalette.Light
        ThemeMode.Light -> KeyboardPalette.Light
        ThemeMode.Dark -> KeyboardPalette.Dark
    }
}

data class RenderRect(val x: Double, val y: Double, val width: Double, val height: Double)

data class RenderKey(
    /** Draw this glyph when set (SF Symbol on iOS); otherwise draw [label]. */
    val icon: KeyIcon?,
    val label: String,
    val hint: String?,
    /** Small line under the label (phone-pad letters). */
    val caption: String?,
    val frame: RenderRect,
    val isModifier: Boolean,
    val isAccent: Boolean,
    /** Shift key while shift or caps lock is on: drawn like a character key. */
    val isActive: Boolean,
    val pressed: Boolean,
)

data class RenderPreview(val label: String, val frame: RenderRect)

data class RenderPopup(val options: List<String>, val cells: List<RenderRect>, val selected: Int)

/** Buttons in the suggestion strip while there is nothing to suggest. */
enum class StripAction { Emoji, Clipboard, Settings }

object KeyboardRenderer {
    fun render(
        state: KeyboardState,
        touch: TouchState,
        geometry: LayoutGeometry?,
        preferences: KeyboardPreferences,
        canOpenSettings: Boolean = false,
    ): KeyboardRender {
        val showingKeys = state.panel == KeyboardPanel.Keys
        val keys = geometry?.keys.orEmpty().takeIf { showingKeys }.orEmpty().map { placed ->
            val key = placed.key
            RenderKey(
                icon = key.renderIcon(state.shift, state.editor.imeAction),
                label = key.renderLabel(state.shift, state.editor.imeAction),
                hint = key.hint,
                caption = key.caption,
                frame = placed.bounds.toRect(),
                isModifier = key.style == KeyStyle.Modifier,
                isAccent = key.style == KeyStyle.Enter,
                isActive = key.action == KeyAction.Shift && state.shift.isUppercase,
                pressed = placed in touch.pressed,
            )
        }
        return KeyboardRender(
            keys = keys,
            suggestions = if (showingKeys) state.suggestions.take(3).inStripOrder().map { it.text } else emptyList(),
            preview = touch.preview?.takeIf { showingKeys }?.let { RenderPreview(it.label, it.bounds.toRect()) },
            popup = touch.popup?.takeIf { showingKeys }?.let { popup -> RenderPopup(popup.options, popup.cells.map { it.toRect() }, popup.selected) },
            theme = preferences.theme,
            panel = state.panel,
            stripActions = stripActions(state, canOpenSettings),
        )
    }

    /**
     * The strip's toolbar. Emoji only when the bottom row has no emoji key (it carries a globe key
     * instead); settings only where the platform lets the keyboard open the companion app.
     */
    fun stripActions(state: KeyboardState, canOpenSettings: Boolean): List<StripAction> = buildList {
        val emojiKey = KeyAction.ShowPanel(KeyboardPanel.Emoji)
        if (state.layout.rows.none { row -> row.keys.any { it.action == emojiKey } }) add(StripAction.Emoji)
        add(StripAction.Clipboard)
        if (canOpenSettings) add(StripAction.Settings)
    }

    private fun KeyBounds.toRect() =
        RenderRect(left.toDouble(), top.toDouble(), (right - left).toDouble(), (bottom - top).toDouble())
}
