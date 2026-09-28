package com.makeeb.engine.layout

import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.ShiftState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyIconTest {
    private val keys = BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions(switchKey = true)).rows.flatMap { it.keys }
    private fun key(action: KeyAction) = keys.first { it.action == action }

    @Test
    fun shiftIconFollowsShiftState() {
        val shift = key(KeyAction.Shift)
        assertEquals(KeyIcon.Shift, shift.renderIcon(ShiftState.Off, ImeAction.None))
        assertEquals(KeyIcon.ShiftActive, shift.renderIcon(ShiftState.OneShot, ImeAction.None))
        assertEquals(KeyIcon.CapsLock, shift.renderIcon(ShiftState.Locked, ImeAction.None))
    }

    @Test
    fun enterIconFollowsTheFieldAction() {
        val enter = key(KeyAction.Enter)
        assertEquals(KeyIcon.Search, enter.renderIcon(ShiftState.Off, ImeAction.Search))
        assertEquals(KeyIcon.Return, enter.renderIcon(ShiftState.Off, ImeAction.None))
        assertNull(enter.renderIcon(ShiftState.Off, ImeAction.Join))
        assertEquals("Join", enter.renderLabel(ShiftState.Off, ImeAction.Join))
    }

    @Test
    fun characterKeysHaveNoIcon() {
        assertNull(key(KeyAction.Text("q")).renderIcon(ShiftState.Off, ImeAction.None))
        assertEquals(KeyIcon.Globe, key(KeyAction.NextInputMethod).renderIcon(ShiftState.Off, ImeAction.None))
    }
}
