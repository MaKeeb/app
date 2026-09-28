package com.makeeb.platform.host

import com.makeeb.core.model.ImeAction

/**
 * The text field the keyboard is typing into, as far as a keyboard can see it.
 *
 * Adapters: [InputConnectionTextHost] (Android `InputConnection`) and
 * `TextDocumentProxyTextHost` (iOS `UITextDocumentProxy`). Both platforms only expose a window
 * of text around the caret, and the host app may change the text at any time, so callers treat
 * every read as a snapshot.
 */
interface TextHost {
    /**
     * Whether reading the field is cheap. Android's `InputConnection` getters are blocking IPC
     * into the app, so the engine keeps a mirror of the text instead of asking on every key; iOS
     * reads a context snapshot the proxy already holds in-process.
     */
    val readsAreCheap: Boolean get() = false

    /** Up to [maxLength] characters before the caret; empty if unknown. */
    fun textBeforeCursor(maxLength: Int): String

    /** Up to [maxLength] characters after the caret; empty if unknown. */
    fun textAfterCursor(maxLength: Int): String

    /** The current selection; empty when the selection is collapsed or unknown. */
    fun selectedText(): String

    fun commitText(text: String)

    /** Delete the selection, or the user-perceived character (grapheme) before the caret. */
    fun deleteBackward()

    /**
     * Delete [grapheme], which the caller knows sits just before the caret with no selection
     * active. Hosts that delete a grapheme natively (iOS) keep [deleteBackward]; Android deletes
     * it by code points without first reading the text back.
     */
    fun deleteGrapheme(grapheme: String) = deleteBackward()

    /**
     * Replace the [length] characters before the caret with [replacement], e.g. swapping the
     * word being typed for a suggestion. The default deletes one grapheme at a time, which is
     * exact for words of precomposed letters; adapters override it with a single native call.
     */
    fun replaceBeforeCursor(length: Int, replacement: String) = batchEdit {
        repeat(length) { deleteBackward() }
        commitText(replacement)
    }

    /** Move the caret by [offset] characters; negative moves left. */
    fun moveCursor(offset: Int)

    /**
     * Perform the field's editor action. Returns false when the host has no such concept (iOS),
     * in which case the engine inserts a newline, which is how iOS apps receive Return.
     */
    fun performEditorAction(action: ImeAction): Boolean

    /** Group edits so the app sees one change (Android batch edits); a plain call elsewhere. */
    fun batchEdit(block: () -> Unit) = block()
}

/**
 * A selection in the field, in UTF-16 units from the start of the text; [start] == [end] is a
 * caret. Android reports these (`onUpdateSelection`); iOS has no absolute positions.
 */
data class TextSelection(val start: Int, val end: Int = start) {
    val isCollapsed: Boolean get() = start == end
}

/** Actions on the keyboard's own host: the IME service or the input view controller. */
interface KeyboardHost {
    /** Whether the keyboard must draw its own "next keyboard" (globe) key. */
    val needsInputMethodSwitchKey: Boolean

    fun switchToNextInputMethod()

    /** Show the system keyboard picker. No-op where the platform has none (iOS). */
    fun showInputMethodPicker()

    fun hideKeyboard()

    /** Whether the keyboard may open the companion app (iOS extensions may not launch apps). */
    val canOpenSettings: Boolean

    fun openSettings()
}
