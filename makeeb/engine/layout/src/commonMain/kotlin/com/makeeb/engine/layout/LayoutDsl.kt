package com.makeeb.engine.layout

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.KeyboardPanel

@DslMarker
annotation class LayoutDsl

fun keyboardLayout(id: String, mode: KeyboardMode, widthUnits: Float? = null, block: LayoutBuilder.() -> Unit): KeyboardLayout =
    KeyboardLayout(id, mode, LayoutBuilder().apply(block).rows, widthUnits)

@LayoutDsl
class LayoutBuilder {
    internal val rows = mutableListOf<KeyRow>()

    fun row(heightWeight: Float = 1f, block: RowBuilder.() -> Unit) {
        rows += KeyRow(RowBuilder().apply(block).keys, heightWeight)
    }
}

@LayoutDsl
class RowBuilder {
    internal val keys = mutableListOf<Key>()

    /** One character key per char, with long-press alternates and hints from the maps. */
    fun chars(
        chars: String,
        alternates: Map<Char, String> = emptyMap(),
        hints: Map<Char, String> = emptyMap(),
    ) {
        chars.forEach { char ->
            val hint = hints[char]
            val alts = listOfNotNull(hint) + alternates[char].orEmpty().map(Char::toString)
            text(char.toString(), alternates = alts, hint = hint)
        }
    }

    fun text(
        text: String,
        label: String = text,
        width: Float = 1f,
        alternates: List<String> = emptyList(),
        hint: String? = null,
        caption: String? = null,
    ) {
        keys += Key(KeyAction.Text(text), label, width, KeyStyle.Character, alternates, hint, caption)
    }

    fun shift(width: Float = 1.5f) {
        keys += Key(KeyAction.Shift, "⇧", width, KeyStyle.Modifier)
    }

    fun backspace(width: Float = 1.5f) {
        keys += Key(KeyAction.Backspace, "⌫", width, KeyStyle.Modifier)
    }

    fun mode(mode: KeyboardMode, label: String, width: Float = 1.5f, longPress: KeyAction? = null) {
        keys += Key(KeyAction.SwitchMode(mode), label, width, KeyStyle.Modifier, longPressAction = longPress)
    }

    /** Holding it lists the input methods, like the system switcher. */
    fun globe(width: Float = 1f) {
        keys += Key(KeyAction.NextInputMethod, "🌐", width, KeyStyle.Modifier, longPressAction = KeyAction.ShowInputMethodPicker)
    }

    fun emoji(width: Float = 1f) {
        keys += Key(KeyAction.ShowPanel(KeyboardPanel.Emoji), "☺", width, KeyStyle.Modifier)
    }

    /**
     * The space bar. With two or more [languages] (autonym to tag, primary first) it names the
     * primary one, and holding it offers the others.
     */
    fun space(width: Float = 4f, languages: List<Pair<String, String>> = emptyList()) {
        keys += if (languages.size < 2) {
            Key(KeyAction.Space, "", width, KeyStyle.Space)
        } else {
            Key(
                KeyAction.Space,
                languages.first().first,
                width,
                KeyStyle.Space,
                alternates = languages.map { it.first },
                alternateActions = languages.map { KeyAction.SelectLanguage(it.second) },
            )
        }
    }

    fun enter(width: Float = 1.5f) {
        keys += Key(KeyAction.Enter, "⏎", width, KeyStyle.Enter)
    }
}
