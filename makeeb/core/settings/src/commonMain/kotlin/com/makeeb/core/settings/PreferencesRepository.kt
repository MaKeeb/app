package com.makeeb.core.settings

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

/** [PreferencesRepository] over a key-value [Settings] store (SharedPreferences / NSUserDefaults). */
class SettingsPreferencesRepository(private val settings: Settings) : PreferencesRepository {
    private val state = MutableStateFlow(read())
    override val preferences: StateFlow<KeyboardPreferences> = state.asStateFlow()

    override fun update(transform: (KeyboardPreferences) -> KeyboardPreferences) {
        state.update { current -> transform(current).also(::write) }
    }

    override fun reload() {
        state.value = read()
    }

    private fun read(): KeyboardPreferences {
        val defaults = KeyboardPreferences()
        return KeyboardPreferences(
            autoCapitalize = settings.getBoolean(Keys.AUTO_CAPITALIZE, defaults.autoCapitalize),
            doubleSpacePeriod = settings.getBoolean(Keys.DOUBLE_SPACE_PERIOD, defaults.doubleSpacePeriod),
            autoCorrect = settings.getBoolean(Keys.AUTO_CORRECT, defaults.autoCorrect),
            showSuggestions = settings.getBoolean(Keys.SHOW_SUGGESTIONS, defaults.showSuggestions),
            keyPressHaptics = settings.getBoolean(Keys.HAPTICS, defaults.keyPressHaptics),
            keyPressSound = settings.getBoolean(Keys.SOUND, defaults.keyPressSound),
            keyPopupPreview = settings.getBoolean(Keys.POPUP_PREVIEW, defaults.keyPopupPreview),
            numberRow = settings.getBoolean(Keys.NUMBER_ROW, defaults.numberRow),
            heightScale = settings.getFloat(Keys.HEIGHT_SCALE, defaults.heightScale)
                .coerceIn(KeyboardPreferences.MIN_HEIGHT_SCALE, KeyboardPreferences.MAX_HEIGHT_SCALE),
            letterLayoutId = settings.getString(Keys.LETTER_LAYOUT, defaults.letterLayoutId),
            theme = settings.getStringOrNull(Keys.THEME)
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.theme,
        )
    }

    private fun write(preferences: KeyboardPreferences) = with(preferences) {
        settings.putBoolean(Keys.AUTO_CAPITALIZE, autoCapitalize)
        settings.putBoolean(Keys.DOUBLE_SPACE_PERIOD, doubleSpacePeriod)
        settings.putBoolean(Keys.AUTO_CORRECT, autoCorrect)
        settings.putBoolean(Keys.SHOW_SUGGESTIONS, showSuggestions)
        settings.putBoolean(Keys.HAPTICS, keyPressHaptics)
        settings.putBoolean(Keys.SOUND, keyPressSound)
        settings.putBoolean(Keys.POPUP_PREVIEW, keyPopupPreview)
        settings.putBoolean(Keys.NUMBER_ROW, numberRow)
        settings.putFloat(Keys.HEIGHT_SCALE, heightScale)
        settings.putString(Keys.LETTER_LAYOUT, letterLayoutId)
        settings.putString(Keys.THEME, theme.name)
    }

    /** Storage keys are persisted: never rename one without a migration. */
    private object Keys {
        const val AUTO_CAPITALIZE = "typing.auto_capitalize"
        const val DOUBLE_SPACE_PERIOD = "typing.double_space_period"
        const val AUTO_CORRECT = "typing.auto_correct"
        const val SHOW_SUGGESTIONS = "typing.show_suggestions"
        const val HAPTICS = "feedback.haptics"
        const val SOUND = "feedback.sound"
        const val POPUP_PREVIEW = "feedback.popup_preview"
        const val NUMBER_ROW = "layout.number_row"
        const val HEIGHT_SCALE = "layout.height_scale"
        const val LETTER_LAYOUT = "layout.letters"
        const val THEME = "appearance.theme"
    }
}
