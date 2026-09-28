package com.makeeb.platform.host

import com.makeeb.core.model.ImeAction
import platform.UIKit.UITextDocumentProxyProtocol

/**
 * [TextHost] over the extension's `UITextDocumentProxy`. iOS exposes only a sentence-or-so of
 * context around the caret, and `deleteBackward` already removes a whole grapheme.
 */
class TextDocumentProxyTextHost(
    private val proxy: () -> UITextDocumentProxyProtocol,
) : TextHost {

    override fun textBeforeCursor(maxLength: Int): String =
        proxy().documentContextBeforeInput.orEmpty().takeLast(maxLength)

    override fun textAfterCursor(maxLength: Int): String =
        proxy().documentContextAfterInput.orEmpty().take(maxLength)

    override fun selectedText(): String = proxy().selectedText.orEmpty()

    override fun commitText(text: String) {
        proxy().insertText(text)
    }

    override fun deleteBackward() {
        proxy().deleteBackward()
    }

    override fun moveCursor(offset: Int) {
        proxy().adjustTextPositionByCharacterOffset(offset.toLong())
    }

    /** iOS apps receive Return as an inserted newline; there is no separate action channel. */
    override fun performEditorAction(action: ImeAction): Boolean = false
}
