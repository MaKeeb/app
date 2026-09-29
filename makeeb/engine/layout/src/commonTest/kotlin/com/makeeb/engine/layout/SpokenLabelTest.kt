package com.makeeb.engine.layout

import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.ShiftState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpokenLabelTest {
    private val provider = BuiltInLayoutProvider()
    private val letters = provider.layout(KeyboardMode.Letters, LayoutOptions(switchKey = true)).rows.flatMap { it.keys }
    private val symbols = provider.layout(KeyboardMode.Symbols, LayoutOptions(switchKey = true)).rows.flatMap { it.keys }
    private fun key(action: KeyAction, keys: List<Key> = letters) = keys.first { it.action == action }

    @Test
    fun shiftedLettersSayCapital() {
        val a = key(KeyAction.Text("a"))
        assertEquals("a", a.spokenLabel(ShiftState.Off, ImeAction.None))
        assertEquals("Capital A", a.spokenLabel(ShiftState.OneShot, ImeAction.None))
        assertEquals("Capital A", a.spokenLabel(ShiftState.Locked, ImeAction.None))
    }

    @Test
    fun functionKeysSayWhatTheyDo() {
        assertEquals("Shift", key(KeyAction.Shift).spokenLabel(ShiftState.Off, ImeAction.None))
        assertEquals("Caps lock", key(KeyAction.Shift).spokenLabel(ShiftState.Locked, ImeAction.None))
        assertEquals("Delete", key(KeyAction.Backspace).spokenLabel(ShiftState.Off, ImeAction.None))
        assertEquals("Search", key(KeyAction.Enter).spokenLabel(ShiftState.Off, ImeAction.Search))
        assertEquals("Previous", key(KeyAction.Enter).spokenLabel(ShiftState.Off, ImeAction.Previous))
        assertEquals("Space", key(KeyAction.Space).spokenLabel(ShiftState.OneShot, ImeAction.None))
        assertEquals("Next keyboard", key(KeyAction.NextInputMethod).spokenLabel(ShiftState.Off, ImeAction.None))
        assertEquals("Symbols", key(KeyAction.SwitchMode(KeyboardMode.Symbols)).spokenLabel(ShiftState.Off, ImeAction.None))
        assertEquals("Letters", key(KeyAction.SwitchMode(KeyboardMode.Letters), symbols).spokenLabel(ShiftState.Off, ImeAction.None))
    }

    @Test
    fun symbolsAreLeftToTheScreenReader() {
        // TalkBack and VoiceOver already name single punctuation marks in the user's language.
        assertEquals("@", key(KeyAction.Text("@"), symbols).spokenLabel(ShiftState.OneShot, ImeAction.None))
        assertEquals("1", key(KeyAction.Text("1"), symbols).spokenLabel(ShiftState.Off, ImeAction.None))
    }

    @Test
    fun longPressIsNamedOnlyWhenItDoesSomethingElse() {
        assertEquals("Choose keyboard", key(KeyAction.NextInputMethod).spokenLongPress(ShiftState.Off, ImeAction.None))
        assertNull(key(KeyAction.Text("e")).spokenLongPress(ShiftState.Off, ImeAction.None))
    }
}
