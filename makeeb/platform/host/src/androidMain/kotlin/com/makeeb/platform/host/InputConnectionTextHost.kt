package com.makeeb.platform.host

import android.icu.text.BreakIterator
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.makeeb.core.model.ImeAction

/**
 * [TextHost] over the current [InputConnection]. The connection changes with every focused
 * field, so it is looked up on each call rather than captured.
 */
class InputConnectionTextHost(
    private val connection: () -> InputConnection?,
) : TextHost {

    override fun textBeforeCursor(maxLength: Int): String =
        connection()?.getTextBeforeCursor(maxLength, 0)?.toString().orEmpty()

    override fun textAfterCursor(maxLength: Int): String =
        connection()?.getTextAfterCursor(maxLength, 0)?.toString().orEmpty()

    override fun selectedText(): String =
        connection()?.getSelectedText(0)?.toString().orEmpty()

    override fun commitText(text: String) {
        connection()?.commitText(text, 1)
    }

    override fun deleteBackward() {
        val ic = connection() ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(GRAPHEME_WINDOW, 0)?.toString().orEmpty()
        if (before.isEmpty()) {
            // Nothing visible before the caret (or the app hides it): let the app handle a real
            // DEL key, which also works in terminals and apps that ignore deleteSurroundingText.
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return
        }
        ic.deleteSurroundingText(lastGraphemeLength(before), 0)
    }

    /** By code points (API 24), so even a mistaken count never splits a surrogate pair. */
    override fun deleteGrapheme(grapheme: String) {
        connection()?.deleteSurroundingTextInCodePoints(grapheme.codePointCount(0, grapheme.length), 0)
    }

    override fun replaceBeforeCursor(length: Int, replacement: String) {
        val ic = connection() ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(length, 0)
        ic.commitText(replacement, 1)
        ic.endBatchEdit()
    }

    override fun moveCursor(offset: Int) {
        val ic = connection() ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val end = extracted.startOffset + extracted.text.length
            val target = (extracted.startOffset + extracted.selectionEnd + offset).coerceIn(0, end)
            ic.setSelection(target, target)
        } else {
            val keyCode = if (offset < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(offset)) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }
    }

    override fun performEditorAction(action: ImeAction): Boolean {
        val code = action.toEditorInfoAction() ?: return false
        return connection()?.performEditorAction(code) ?: false
    }

    override fun batchEdit(block: () -> Unit) {
        val ic = connection()
        ic?.beginBatchEdit()
        try {
            block()
        } finally {
            ic?.endBatchEdit()
        }
    }

    private fun lastGraphemeLength(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val end = iterator.last()
        val start = iterator.previous()
        return if (start == BreakIterator.DONE) 1 else end - start
    }

    private companion object {
        /** Long enough for any emoji ZWJ sequence or flag. */
        const val GRAPHEME_WINDOW = 32
    }
}

private fun ImeAction.toEditorInfoAction(): Int? = when (this) {
    ImeAction.Go -> EditorInfo.IME_ACTION_GO
    ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
    ImeAction.Send -> EditorInfo.IME_ACTION_SEND
    ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
    ImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
    ImeAction.Done -> EditorInfo.IME_ACTION_DONE
    // iOS-only return key styles have no Android action; Enter inserts a newline.
    ImeAction.None, ImeAction.Join, ImeAction.Route, ImeAction.Continue, ImeAction.EmergencyCall -> null
}
