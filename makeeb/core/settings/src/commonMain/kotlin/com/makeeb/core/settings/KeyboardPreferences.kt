package com.makeeb.core.settings

import com.makeeb.core.model.AutocorrectStrength

/**
 * User-facing keyboard settings. The companion app writes them all; the keyboard reads them and
 * writes only the few its quick-settings panel offers ([QuickSetting]).
 */
data class KeyboardPreferences(
    // Typing
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val autoCorrect: Boolean = true,
    val autoCorrectStrength: AutocorrectStrength = AutocorrectStrength.Normal,
    val showSuggestions: Boolean = true,
    /** Offer the emoji a typed word names ("pizza" → 🍕) in the suggestion strip. */
    val emojiSuggestions: Boolean = true,
    /** Holding delete speeds up, then erases whole words. */
    val holdDeleteWords: Boolean = true,
    /** Sliding on the space bar moves the caret a word at a time instead of a character. */
    val cursorSlideByWord: Boolean = false,
    // Feedback
    val keyPressHaptics: Boolean = true,
    val keyPressSound: Boolean = false,
    /** Vibration strength, 0..1. */
    val hapticIntensity: Float = 0.5f,
    /** Click volume, 0..1 (Android; iOS has no volume for key clicks). */
    val soundVolume: Float = 0.5f,
    val keyPopupPreview: Boolean = true,
    // Layout
    val numberRow: Boolean = false,
    /** Height and gap under the keys, each orientation on its own ([size]). */
    val portraitSize: KeyboardSize = KeyboardSize(),
    val landscapeSize: KeyboardSize = KeyboardSize(),
    val letterLayoutId: String = "qwerty",
    /**
     * The languages the user types (BCP 47), primary first. Together they decide the character
     * set: every letter's long-press offers all their accents, whatever the layout. Never empty.
     */
    val languageTags: List<String> = listOf("en"),
    // Appearance
    val theme: ThemeMode = ThemeMode.System,
    /** [ThemeMode.Scheduled]: dark from this minute of the day until [darkUntilMinute] (may wrap past midnight). */
    val darkFromMinute: Int = 21 * 60,
    val darkUntilMinute: Int = 7 * 60,
) {
    /** Whether the keyboard draws its dark palette now; [systemDark] is the OS appearance. */
    fun useDarkTheme(systemDark: Boolean, minuteOfDay: Int): Boolean = when (theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Scheduled ->
            if (darkFromMinute <= darkUntilMinute) minuteOfDay in darkFromMinute until darkUntilMinute
            else minuteOfDay >= darkFromMinute || minuteOfDay < darkUntilMinute
    }
}

enum class ThemeMode { System, Light, Dark, Scheduled }
