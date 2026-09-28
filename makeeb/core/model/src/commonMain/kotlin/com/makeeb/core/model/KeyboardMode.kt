package com.makeeb.core.model

/** Which key layout is showing. */
enum class KeyboardMode {
    Letters,
    Symbols,
    SymbolsMore,
    Numeric,
    Phone,
}

/** What occupies the key area: the keys themselves or a full panel replacing them. */
enum class KeyboardPanel {
    Keys,
    Emoji,
    Clipboard,
}

enum class ShiftState {
    Off,

    /** Uppercase for the next character only. */
    OneShot,

    /** Caps lock. */
    Locked,
    ;

    val isUppercase: Boolean get() = this != Off
}
