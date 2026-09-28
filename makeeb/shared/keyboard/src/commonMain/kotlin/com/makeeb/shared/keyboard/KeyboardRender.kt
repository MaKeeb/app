package com.makeeb.shared.keyboard

import com.makeeb.core.common.currentMinuteOfDay
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyboardPalette
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.bestStripSlot
import com.makeeb.core.model.stripSlots
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
    /** The strip's cells left to right ([stripSlots]); an empty string is an empty slot. */
    val suggestions: List<String>,
    val preview: RenderPreview?,
    val popup: RenderPopup?,
    val theme: ThemeMode,
    /** For [ThemeMode.Scheduled]; see [KeyboardPreferences.useDarkTheme]. */
    val darkFromMinute: Int = 21 * 60,
    val darkUntilMinute: Int = 7 * 60,
    /** What fills the key area; panels other than [KeyboardPanel.Keys] are drawn natively. */
    val panel: KeyboardPanel = KeyboardPanel.Keys,
    /** The strip's buttons when [suggestions] is empty; [StripAction.Settings] sits apart, at the end. */
    val stripActions: List<StripAction> = emptyList(),
    /** The cell of [suggestions] drawn in bold (the best word), or -1. */
    val bestSuggestion: Int = -1,
    /** Incognito is on (the strip's incognito button is lit). */
    val incognito: Boolean = false,
) {
    /** Resolved when drawn, so a scheduled theme switches at its time. */
    fun palette(systemDark: Boolean): KeyboardPalette {
        val prefs = KeyboardPreferences(theme = theme, darkFromMinute = darkFromMinute, darkUntilMinute = darkUntilMinute)
        return if (prefs.useDarkTheme(systemDark, currentMinuteOfDay())) KeyboardPalette.Dark else KeyboardPalette.Light
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
enum class StripAction { Emoji, Clipboard, Incognito, Settings }

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
            suggestions = if (showingKeys) state.suggestions.stripSlots().map { it?.text.orEmpty() } else emptyList(),
            preview = touch.preview?.takeIf { showingKeys }?.let { RenderPreview(it.label, it.bounds.toRect()) },
            popup = touch.popup?.takeIf { showingKeys }?.let { popup -> RenderPopup(popup.options, popup.cells.map { it.toRect() }, popup.selected) },
            theme = preferences.theme,
            darkFromMinute = preferences.darkFromMinute,
            darkUntilMinute = preferences.darkUntilMinute,
            panel = state.panel,
            stripActions = stripActions(state, canOpenSettings),
            bestSuggestion = if (showingKeys) state.suggestions.bestStripSlot() else -1,
            incognito = state.incognito,
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
        add(StripAction.Incognito)
        if (canOpenSettings) add(StripAction.Settings)
    }

    private fun KeyBounds.toRect() =
        RenderRect(left.toDouble(), top.toDouble(), (right - left).toDouble(), (bottom - top).toDouble())
}
