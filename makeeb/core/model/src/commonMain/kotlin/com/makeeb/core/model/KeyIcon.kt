package com.makeeb.core.model

/**
 * Icons for function keys. Renderers map each to a native glyph (Material Symbols in Compose,
 * SF Symbols on iOS) so function keys never depend on whatever font happens to cover a Unicode
 * arrow or emoji.
 */
enum class KeyIcon {
    Shift,
    ShiftActive,
    CapsLock,
    Backspace,
    Return,
    Search,
    Send,
    Go,
    Next,
    Previous,
    Done,
    Globe,
    Emoji,
    Space,
}
