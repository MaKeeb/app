package com.makeeb.engine.input

import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.ShiftState
import com.makeeb.core.model.Suggestion
import com.makeeb.engine.layout.KeyboardLayout

/** Everything the keyboard UI renders. Produced only by [InputEngine]. */
data class KeyboardState(
    val layout: KeyboardLayout,
    val mode: KeyboardMode = KeyboardMode.Letters,
    val panel: KeyboardPanel = KeyboardPanel.Keys,
    val shift: ShiftState = ShiftState.Off,
    val editor: EditorAttributes = EditorAttributes.Default,
    /** The partial word before the caret that suggestions are for. */
    val composing: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    /** False between fields, when there is nothing to type into. */
    val active: Boolean = false,
    /** Incognito turned on by the user; it stays on across fields until turned off. */
    val manualIncognito: Boolean = false,
) {
    /** No learning, clipboard history or emoji recents: the field asked for it, or the user did. */
    val incognito: Boolean get() = editor.incognito || manualIncognito
}
