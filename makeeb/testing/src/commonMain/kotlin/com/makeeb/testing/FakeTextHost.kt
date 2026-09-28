package com.makeeb.testing

import com.makeeb.core.model.ImeAction
import com.makeeb.platform.host.TextHost

/**
 * An in-memory text field. `|` in [toString] marks the caret, e.g. `"Hello wor|ld"`.
 * [supportsEditorActions] = false behaves like iOS, where Enter is always a newline.
 */
class FakeTextHost(
    initialText: String = "",
    cursor: Int = initialText.length,
    private val supportsEditorActions: Boolean = true,
) : TextHost {
    private val buffer = StringBuilder(initialText)
    var cursor: Int = cursor
        private set
    val performedActions = mutableListOf<ImeAction>()

    val text: String get() = buffer.toString()

    override fun textBeforeCursor(maxLength: Int): String =
        buffer.substring((cursor - maxLength).coerceAtLeast(0), cursor)

    override fun textAfterCursor(maxLength: Int): String =
        buffer.substring(cursor, (cursor + maxLength).coerceAtMost(buffer.length))

    override fun selectedText(): String = ""

    override fun commitText(text: String) {
        buffer.insert(cursor, text)
        cursor += text.length
    }

    override fun deleteBackward() {
        if (cursor == 0) return
        buffer.deleteAt(cursor - 1)
        cursor--
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

    override fun toString(): String = buffer.substring(0, cursor) + "|" + buffer.substring(cursor)
}
