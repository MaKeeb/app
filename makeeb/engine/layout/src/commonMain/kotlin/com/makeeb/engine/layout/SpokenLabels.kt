package com.makeeb.engine.layout

import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.ShiftState

/**
 * What a screen reader says for this key. Function keys get a word instead of their glyph or
 * cryptic label ("?123" is "Symbols"), and a shifted letter says "Capital A", because the glyph
 * alone sounds the same in both cases. Shared so TalkBack and VoiceOver read the same keyboard.
 */
fun Key.spokenLabel(shift: ShiftState, imeAction: ImeAction): String =
    (if (action == KeyAction.Space && label.isNotEmpty()) "Space, $label" else null) ?: spokenLabel(action, shift, imeAction) ?: run {
    val text = displayLabel(shift)
    val letter = text.singleOrNull()
    if (letter != null && letter.isLetter() && letter.isUpperCase() && letter.lowercaseChar() != letter) "Capital $text" else text
}

/** The spoken name of what holding this key does, or null when holding it only repeats or offers alternates. */
fun Key.spokenLongPress(shift: ShiftState, imeAction: ImeAction): String? =
    longPressAction?.let { spokenLabel(it, shift, imeAction) }

private fun spokenLabel(action: KeyAction, shift: ShiftState, imeAction: ImeAction): String? = when (action) {
    is KeyAction.Text, KeyAction.None -> null
    KeyAction.Shift -> when (shift) {
        ShiftState.Off -> "Shift"
        ShiftState.OneShot -> "Shift, on"
        ShiftState.Locked -> "Caps lock"
    }
    KeyAction.Backspace -> "Delete"
    KeyAction.DeleteWord -> "Delete word"
    KeyAction.Space -> "Space"
    is KeyAction.SelectLanguage -> null
    KeyAction.Enter -> imeAction.spoken()
    is KeyAction.SwitchMode -> when (action.mode) {
        KeyboardMode.Letters -> "Letters"
        KeyboardMode.Symbols -> "Symbols"
        KeyboardMode.SymbolsMore -> "More symbols"
        KeyboardMode.Numeric -> "Numbers"
        KeyboardMode.Phone -> "Phone pad"
    }
    is KeyAction.ShowPanel -> when (action.panel) {
        KeyboardPanel.Keys -> "Letters"
        KeyboardPanel.Emoji -> "Emoji"
        KeyboardPanel.Clipboard -> "Clipboard"
        KeyboardPanel.Settings -> "Keyboard settings"
    }
    KeyAction.NextInputMethod -> "Next keyboard"
    KeyAction.ShowInputMethodPicker -> "Choose keyboard"
    is KeyAction.MoveCursor -> if (action.offset < 0) "Cursor left" else "Cursor right"
    is KeyAction.MoveCursorByWord -> if (action.direction < 0) "Previous word" else "Next word"
}

private fun ImeAction.spoken(): String = when (this) {
    ImeAction.None -> "Return"
    ImeAction.Go -> "Go"
    ImeAction.Search -> "Search"
    ImeAction.Send -> "Send"
    ImeAction.Next -> "Next"
    ImeAction.Previous -> "Previous"
    ImeAction.Done -> "Done"
    ImeAction.Join -> "Join"
    ImeAction.Route -> "Route"
    ImeAction.Continue -> "Continue"
    ImeAction.EmergencyCall -> "Emergency call"
}
