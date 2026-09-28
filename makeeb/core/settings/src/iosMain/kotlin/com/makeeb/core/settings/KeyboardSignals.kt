package com.makeeb.core.settings

import platform.Foundation.NSUserDefaults

/**
 * What the keyboard extension tells the companion app's setup screen, through the App Group.
 * The extension can only write there with Full Access, so an absent signal means "unknown",
 * never "no".
 */
object KeyboardSignals {
    private const val SHOWN = "keyboard.shown"
    private const val FULL_ACCESS = "keyboard.full_access"

    private val defaults get() = NSUserDefaults(suiteName = APP_GROUP_ID)

    /** Called by the extension each time it appears. */
    fun recordShown(hasFullAccess: Boolean) {
        if (!hasFullAccess) return // the App Group is read-only without Full Access
        defaults.setBool(true, forKey = SHOWN)
        defaults.setBool(true, forKey = FULL_ACCESS)
    }

    /** True once the keyboard has been shown with Full Access; null when it can't be known. */
    val shownWithFullAccess: Boolean?
        get() = if (defaults.boolForKey(SHOWN) && defaults.boolForKey(FULL_ACCESS)) true else null
}
