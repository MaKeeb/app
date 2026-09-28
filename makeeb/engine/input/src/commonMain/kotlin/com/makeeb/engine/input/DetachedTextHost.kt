package com.makeeb.engine.input

import com.makeeb.core.model.ImeAction
import com.makeeb.platform.host.TextHost

/** Stands in between input sessions so the engine never holds a stale connection. */
internal object DetachedTextHost : TextHost {
    override fun textBeforeCursor(maxLength: Int) = ""
    override fun textAfterCursor(maxLength: Int) = ""
    override fun selectedText() = ""
    override fun commitText(text: String) = Unit
    override fun deleteBackward() = Unit
    override fun moveCursor(offset: Int) = Unit
    override fun performEditorAction(action: ImeAction) = false
}
