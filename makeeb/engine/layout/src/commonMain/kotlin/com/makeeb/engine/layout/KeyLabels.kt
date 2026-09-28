package com.makeeb.engine.layout

import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.ShiftState

/**
 * The icon to draw for this key in the current state, or null to draw [renderLabel]. Shared so
 * both renderers show the same key the same way.
 */
fun Key.renderIcon(shift: ShiftState, imeAction: ImeAction): KeyIcon? = when (action) {
    KeyAction.Shift -> when (shift) {
        ShiftState.Off -> KeyIcon.Shift
        ShiftState.OneShot -> KeyIcon.ShiftActive
        ShiftState.Locked -> KeyIcon.CapsLock
    }
    KeyAction.Backspace -> KeyIcon.Backspace
    // A narrow space key isn't recognisable by its shape, so it gets a glyph; the bar doesn't.
    KeyAction.Space -> if (width < 2f) KeyIcon.Space else null
    KeyAction.NextInputMethod -> KeyIcon.Globe
    is KeyAction.ShowPanel -> if (action.panel == KeyboardPanel.Emoji) KeyIcon.Emoji else null
    KeyAction.Enter -> when (imeAction) {
        ImeAction.None -> KeyIcon.Return
        ImeAction.Search -> KeyIcon.Search
        ImeAction.Send -> KeyIcon.Send
        ImeAction.Go -> KeyIcon.Go
        ImeAction.Next -> KeyIcon.Next
        ImeAction.Previous -> KeyIcon.Previous
        ImeAction.Done -> KeyIcon.Done
        // iOS-only return styles read better as words.
        ImeAction.Join, ImeAction.Route, ImeAction.Continue, ImeAction.EmergencyCall -> null
    }
    else -> null
}

/** The text drawn on a key when [renderIcon] is null. */
fun Key.renderLabel(shift: ShiftState, imeAction: ImeAction, spaceLabel: String = ""): String = when (action) {
    KeyAction.Enter -> imeAction.label()
    KeyAction.Space -> spaceLabel
    else -> displayLabel(shift)
}

private fun ImeAction.label(): String = when (this) {
    ImeAction.None -> "Return"
    ImeAction.Go -> "Go"
    ImeAction.Search -> "Search"
    ImeAction.Send -> "Send"
    ImeAction.Next -> "Next"
    ImeAction.Previous -> "Prev"
    ImeAction.Done -> "Done"
    ImeAction.Join -> "Join"
    ImeAction.Route -> "Route"
    ImeAction.Continue -> "Continue"
    ImeAction.EmergencyCall -> "SOS"
}
