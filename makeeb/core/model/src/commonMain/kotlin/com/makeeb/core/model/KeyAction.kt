package com.makeeb.core.model

/**
 * What activating a key asks the input engine to do. Layouts bind keys to actions; only the
 * engine interprets them, so the same layout works on every platform.
 */
sealed interface KeyAction {
    /** Commit [text] (one or more code points) after applying the current shift state. */
    data class Text(val text: String) : KeyAction

    data object Backspace : KeyAction

    /** Delete the word before the caret (and the spaces after it): holding Backspace long. */
    data object DeleteWord : KeyAction

    data object Shift : KeyAction

    data object Space : KeyAction

    /** The field's editor action (Go, Search, Send…) or a newline, depending on the field. */
    data object Enter : KeyAction

    data class SwitchMode(val mode: KeyboardMode) : KeyAction

    data class ShowPanel(val panel: KeyboardPanel) : KeyAction

    /** Move to the next system input method (the globe key). */
    data object NextInputMethod : KeyAction

    /** Show the system input method picker (long-press on the globe key on Android). */
    data object ShowInputMethodPicker : KeyAction

    /** Move the caret by [offset] characters; negative moves left. */
    data class MoveCursor(val offset: Int) : KeyAction

    data object None : KeyAction
}
