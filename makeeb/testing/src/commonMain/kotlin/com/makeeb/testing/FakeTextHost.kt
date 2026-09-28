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

    /** One grapheme, as the real hosts delete: a whole emoji (with modifiers and ZWJ joins), never half. */
    override fun deleteBackward() {
        if (cursor == 0) return
        val length = lastGraphemeLength(buffer.substring(0, cursor))
        buffer.deleteRange(cursor - length, cursor)
        cursor -= length
    }

    private fun lastGraphemeLength(text: String): Int {
        var end = text.length
        while (true) {
            var start = end - if (end >= 2 && text[end - 1].isLowSurrogate() && text[end - 2].isHighSurrogate()) 2 else 1
            // Absorb modifiers that attach to the previous symbol: variation selector, skin tones.
            while (start > 0 && (text[start] == '\uFE0F' || text.isSkinTone(start))) {
                start -= if (start >= 2 && text[start - 1].isLowSurrogate() && text[start - 2].isHighSurrogate()) 2 else 1
            }
            // A zero-width joiner glues the previous symbol on (family, profession emoji).
            if (start >= 2 && text[start - 1] == '\u200D') {
                end = start - 1
                continue
            }
            return text.length - start
        }
    }

    private fun String.isSkinTone(index: Int): Boolean =
        index + 1 < length && this[index] == '\uD83C' && this[index + 1] in '\uDFFB'..'\uDFFF'

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
