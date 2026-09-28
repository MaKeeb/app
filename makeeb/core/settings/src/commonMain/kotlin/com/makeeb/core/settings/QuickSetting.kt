package com.makeeb.core.settings

/**
 * The preferences the keyboard's quick-settings panel changes in place, so the common ones don't
 * need a trip to the companion app. Both renderers show them in this order, as one tile each.
 */
enum class QuickSetting(val title: String) {
    NumberRow("Number row"),
    Suggestions("Suggestions"),
    Autocorrect("Autocorrect"),
    AutoCapitalize("Auto-caps"),
    KeyPreview("Key popup"),
    Vibration("Vibration"),
    Sound("Sound"),
    Theme("Theme"),
    ;

    /** Lit tiles are the switches that are on; [Theme] is a choice and never lit. */
    fun isOn(preferences: KeyboardPreferences): Boolean = when (this) {
        NumberRow -> preferences.numberRow
        Suggestions -> preferences.showSuggestions
        Autocorrect -> preferences.autoCorrect
        AutoCapitalize -> preferences.autoCapitalize
        KeyPreview -> preferences.keyPopupPreview
        Vibration -> preferences.keyPressHaptics
        Sound -> preferences.keyPressSound
        Theme -> false
    }

    /** The tile's second line: "On", "Off", or the theme's name. */
    fun valueLabel(preferences: KeyboardPreferences): String = when (this) {
        Theme -> preferences.theme.name
        else -> if (isOn(preferences)) "On" else "Off"
    }

    /** A tap: switches flip, the theme moves to the next mode. */
    fun next(preferences: KeyboardPreferences): KeyboardPreferences = when (this) {
        NumberRow -> preferences.copy(numberRow = !preferences.numberRow)
        Suggestions -> preferences.copy(showSuggestions = !preferences.showSuggestions)
        Autocorrect -> preferences.copy(autoCorrect = !preferences.autoCorrect)
        AutoCapitalize -> preferences.copy(autoCapitalize = !preferences.autoCapitalize)
        KeyPreview -> preferences.copy(keyPopupPreview = !preferences.keyPopupPreview)
        Vibration -> preferences.copy(keyPressHaptics = !preferences.keyPressHaptics)
        Sound -> preferences.copy(keyPressSound = !preferences.keyPressSound)
        Theme -> preferences.copy(theme = ThemeMode.entries[(preferences.theme.ordinal + 1) % ThemeMode.entries.size])
    }
}
