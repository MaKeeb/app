package com.makeeb.testing

import com.makeeb.core.model.ImeAction
import com.makeeb.core.common.Graphemes
import com.makeeb.platform.host.TextHost
import com.makeeb.platform.host.TextSelection

/**
 * An in-memory text field. `|` in [toString] marks the caret, e.g. `"Hello wor|ld"`.
 * [supportsEditorActions] = false behaves like iOS, where Enter is always a newline.
 * By default reads count as expensive, as on Android, so the engine serves them from its text
 * mirror; [reads] counts the ones that reached the field. [inputFilter] models a field that
 * silently refuses some input without moving its selection.
 */
class FakeTextHost(
    initialText: String = "",
    cursor: Int = initialText.length,
    private val supportsEditorActions: Boolean = true,
    override val readsAreCheap: Boolean = false,
    /** What the field accepts of committed text, like an Android input filter. */
    private val inputFilter: (String) -> String = { it },
) : TextHost {
    private val buffer = StringBuilder(initialText)
    var cursor: Int = cursor
        private set
    val performedActions = mutableListOf<ImeAction>()

    /** Reads of the field's text: the IPC round trips a real Android field would cost. */
    var reads: Int = 0
        private set

    val text: String get() = buffer.toString()

    /** Where the field's selection is, as Android's `onUpdateSelection` reports it. */
    val selection: TextSelection get() = TextSelection(cursor)

    override fun textBeforeCursor(maxLength: Int): String {
        reads++
        return buffer.substring((cursor - maxLength).coerceAtLeast(0), cursor)
    }

    override fun textAfterCursor(maxLength: Int): String {
        reads++
        return buffer.substring(cursor, (cursor + maxLength).coerceAtMost(buffer.length))
    }

    override fun selectedText(): String {
        reads++
        return ""
    }

    override fun commitText(text: String) {
        val accepted = inputFilter(text)
        buffer.insert(cursor, accepted)
        cursor += accepted.length
    }

    /** One grapheme, as the real hosts delete: a whole emoji (with modifiers and ZWJ joins), never half. */
    override fun deleteBackward() {
        if (cursor == 0) return
        val length = Graphemes.lastLength(buffer.substring(0, cursor))
        buffer.deleteRange(cursor - length, cursor)
        cursor -= length
    }

    override fun deleteGrapheme(grapheme: String) {
        val count = grapheme.length.coerceAtMost(cursor)
        buffer.deleteRange(cursor - count, cursor)
        cursor -= count
    }

    override fun moveCursor(offset: Int) {
        cursor = (cursor + offset).coerceIn(0, buffer.length)
    }

    override fun performEditorAction(action: ImeAction): Boolean {
        if (!supportsEditorActions || action == ImeAction.None) return false
        performedActions += action
        return true
    }

    /** Simulate the user tapping elsewhere in the field. */
    fun placeCursor(position: Int) {
        cursor = position.coerceIn(0, buffer.length)
    }

    /** Simulate the app changing its own text (an input filter, a chat app clearing after send). */
    fun replaceAll(text: String, cursor: Int = text.length) {
        buffer.clear()
        buffer.append(text)
        this.cursor = cursor.coerceIn(0, buffer.length)
    }

    override fun toString(): String = buffer.substring(0, cursor) + "|" + buffer.substring(cursor)
}
