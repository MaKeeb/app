package com.makeeb.core.settings

import com.makeeb.core.model.AutocorrectStrength
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface PreferencesRepository {
    val preferences: StateFlow<KeyboardPreferences>

    fun update(transform: (KeyboardPreferences) -> KeyboardPreferences)

    /**
     * Re-read storage. On iOS the companion app and the keyboard extension are separate
     * processes sharing an App Group, so the extension calls this whenever it becomes visible.
     */
    fun reload()
}

/**
 * [PreferencesRepository] over a key-value [Settings] store (SharedPreferences / NSUserDefaults).
 * [defaults] fill in whatever was never stored; the platform factories put the phone's own
 * languages there.
 */
class SettingsPreferencesRepository(
    private val settings: Settings,
    private val defaults: KeyboardPreferences = KeyboardPreferences(),
) : PreferencesRepository {
    private val state = MutableStateFlow(read())
    override val preferences: StateFlow<KeyboardPreferences> = state.asStateFlow()

    override fun update(transform: (KeyboardPreferences) -> KeyboardPreferences) {
        state.update { current -> transform(current).also(::write) }
    }

    override fun reload() {
        state.value = read()
    }

    private fun read(): KeyboardPreferences {
        return KeyboardPreferences(
            autoCapitalize = settings.getBoolean(Keys.AUTO_CAPITALIZE, defaults.autoCapitalize),
            doubleSpacePeriod = settings.getBoolean(Keys.DOUBLE_SPACE_PERIOD, defaults.doubleSpacePeriod),
            autoCorrect = settings.getBoolean(Keys.AUTO_CORRECT, defaults.autoCorrect),
            autoCorrectStrength = settings.getStringOrNull(Keys.AUTO_CORRECT_STRENGTH)
                ?.let { stored -> AutocorrectStrength.entries.firstOrNull { it.name == stored } }
                ?: defaults.autoCorrectStrength,
            showSuggestions = settings.getBoolean(Keys.SHOW_SUGGESTIONS, defaults.showSuggestions),
            emojiSuggestions = settings.getBoolean(Keys.EMOJI_SUGGESTIONS, defaults.emojiSuggestions),
            holdDeleteWords = settings.getBoolean(Keys.HOLD_DELETE_WORDS, defaults.holdDeleteWords),
            cursorSlideByWord = settings.getBoolean(Keys.CURSOR_SLIDE_BY_WORD, defaults.cursorSlideByWord),
            keyPressHaptics = settings.getBoolean(Keys.HAPTICS, defaults.keyPressHaptics),
            keyPressSound = settings.getBoolean(Keys.SOUND, defaults.keyPressSound),
            hapticIntensity = settings.getFloat(Keys.HAPTIC_INTENSITY, defaults.hapticIntensity).coerceIn(0f, 1f),
            soundVolume = settings.getFloat(Keys.SOUND_VOLUME, defaults.soundVolume).coerceIn(0f, 1f),
            keyPopupPreview = settings.getBoolean(Keys.POPUP_PREVIEW, defaults.keyPopupPreview),
            numberRow = settings.getBoolean(Keys.NUMBER_ROW, defaults.numberRow),
            // Before each orientation had its own size there was one height, which portrait keeps.
            // Landscape used the rows that fit the screen whatever the height, which is its default.
            portraitSize = readSize(
                Keys.PORTRAIT_HEIGHT_SCALE,
                Keys.PORTRAIT_BOTTOM_OFFSET,
                defaults.portraitSize,
                settings.getFloatOrNull(Keys.LEGACY_HEIGHT_SCALE),
            ),
            landscapeSize = readSize(Keys.LANDSCAPE_HEIGHT_SCALE, Keys.LANDSCAPE_BOTTOM_OFFSET, defaults.landscapeSize),
            letterLayoutId = settings.getString(Keys.LETTER_LAYOUT, defaults.letterLayoutId),
            // Before several languages there was one, under its own key.
            languageTags = (settings.getStringOrNull(Keys.LANGUAGES) ?: settings.getStringOrNull(Keys.LEGACY_LANGUAGE))
                ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()?.takeIf { it.isNotEmpty() }
                ?: defaults.languageTags,
            theme = settings.getStringOrNull(Keys.THEME)
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.theme,
            darkFromMinute = settings.getInt(Keys.DARK_FROM, defaults.darkFromMinute).coerceIn(0, MINUTES_PER_DAY - 1),
            darkUntilMinute = settings.getInt(Keys.DARK_UNTIL, defaults.darkUntilMinute).coerceIn(0, MINUTES_PER_DAY - 1),
        )
    }

    private fun readSize(heightKey: String, offsetKey: String, default: KeyboardSize, legacyHeightScale: Float? = null) = KeyboardSize(
        heightScale = settings.getFloatOrNull(heightKey) ?: legacyHeightScale ?: default.heightScale,
        bottomOffset = settings.getFloat(offsetKey, default.bottomOffset),
    ).coerced()

    private fun write(preferences: KeyboardPreferences) = with(preferences) {
        settings.putBoolean(Keys.AUTO_CAPITALIZE, autoCapitalize)
        settings.putBoolean(Keys.DOUBLE_SPACE_PERIOD, doubleSpacePeriod)
        settings.putBoolean(Keys.AUTO_CORRECT, autoCorrect)
        settings.putString(Keys.AUTO_CORRECT_STRENGTH, autoCorrectStrength.name)
        settings.putBoolean(Keys.SHOW_SUGGESTIONS, showSuggestions)
        settings.putBoolean(Keys.EMOJI_SUGGESTIONS, emojiSuggestions)
        settings.putBoolean(Keys.HOLD_DELETE_WORDS, holdDeleteWords)
        settings.putBoolean(Keys.CURSOR_SLIDE_BY_WORD, cursorSlideByWord)
        settings.putBoolean(Keys.HAPTICS, keyPressHaptics)
        settings.putBoolean(Keys.SOUND, keyPressSound)
        settings.putFloat(Keys.HAPTIC_INTENSITY, hapticIntensity)
        settings.putFloat(Keys.SOUND_VOLUME, soundVolume)
        settings.putBoolean(Keys.POPUP_PREVIEW, keyPopupPreview)
        settings.putBoolean(Keys.NUMBER_ROW, numberRow)
        settings.putFloat(Keys.PORTRAIT_HEIGHT_SCALE, portraitSize.heightScale)
        settings.putFloat(Keys.PORTRAIT_BOTTOM_OFFSET, portraitSize.bottomOffset)
        settings.putFloat(Keys.LANDSCAPE_HEIGHT_SCALE, landscapeSize.heightScale)
        settings.putFloat(Keys.LANDSCAPE_BOTTOM_OFFSET, landscapeSize.bottomOffset)
        settings.remove(Keys.LEGACY_HEIGHT_SCALE)
        settings.putString(Keys.LETTER_LAYOUT, letterLayoutId)
        settings.putString(Keys.LANGUAGES, languageTags.joinToString(","))
        settings.remove(Keys.LEGACY_LANGUAGE)
        settings.putString(Keys.THEME, theme.name)
        settings.putInt(Keys.DARK_FROM, darkFromMinute)
        settings.putInt(Keys.DARK_UNTIL, darkUntilMinute)
    }

    /** Storage keys are persisted: never rename one without a migration. */
    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }

    private object Keys {
        const val AUTO_CAPITALIZE = "typing.auto_capitalize"
        const val DOUBLE_SPACE_PERIOD = "typing.double_space_period"
        const val AUTO_CORRECT = "typing.auto_correct"
        const val AUTO_CORRECT_STRENGTH = "typing.auto_correct_strength"
        const val SHOW_SUGGESTIONS = "typing.show_suggestions"
        const val EMOJI_SUGGESTIONS = "typing.emoji_suggestions"
        const val HOLD_DELETE_WORDS = "typing.hold_delete_words"
        const val CURSOR_SLIDE_BY_WORD = "typing.cursor_slide_by_word"
        const val HAPTICS = "feedback.haptics"
        const val SOUND = "feedback.sound"
        const val HAPTIC_INTENSITY = "feedback.haptic_intensity"
        const val SOUND_VOLUME = "feedback.sound_volume"
        const val POPUP_PREVIEW = "feedback.popup_preview"
        const val NUMBER_ROW = "layout.number_row"
        const val PORTRAIT_HEIGHT_SCALE = "layout.portrait.height_scale"
        const val PORTRAIT_BOTTOM_OFFSET = "layout.portrait.bottom_offset"
        const val LANDSCAPE_HEIGHT_SCALE = "layout.landscape.height_scale"
        const val LANDSCAPE_BOTTOM_OFFSET = "layout.landscape.bottom_offset"

        /** One height for both orientations, read once into [PORTRAIT_HEIGHT_SCALE]. */
        const val LEGACY_HEIGHT_SCALE = "layout.height_scale"
        const val LETTER_LAYOUT = "layout.letters"
        const val LANGUAGES = "layout.languages"

        /** One language, read once and replaced by [LANGUAGES]. */
        const val LEGACY_LANGUAGE = "layout.language"
        const val THEME = "appearance.theme"
        const val DARK_FROM = "appearance.dark_from_minute"
        const val DARK_UNTIL = "appearance.dark_until_minute"
    }
}
