package com.makeeb.core.model

/**
 * Size tokens both renderers read, next to [KeyboardPalette], so Compose and the iOS renderer
 * cannot drift apart. Units are dp on Android and points on iOS.
 */
object KeyboardTokens {
    /** Box a function-key icon is drawn in (Compose). Glyphs fill ~75–90% of it. */
    const val KEY_ICON_SIZE = 28f

    /**
     * SF Symbol point size for the same icons on iOS. An SF Symbol at 22pt is about as wide as
     * our 28dp Compose glyphs, and matches the system keyboard's shift and delete keys.
     */
    const val KEY_SYMBOL_POINT_SIZE = 22f

}
