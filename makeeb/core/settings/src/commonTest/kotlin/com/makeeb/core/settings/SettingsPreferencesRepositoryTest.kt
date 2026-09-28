package com.makeeb.core.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsPreferencesRepositoryTest {
    @Test
    fun updatesPersistAndReload() {
        val store = MapSettings()
        val repository = SettingsPreferencesRepository(store)

        repository.update { it.copy(autoCorrect = false, theme = ThemeMode.Dark, heightScale = 1.2f) }

        val reopened = SettingsPreferencesRepository(store)
        assertFalse(reopened.preferences.value.autoCorrect)
        assertEquals(ThemeMode.Dark, reopened.preferences.value.theme)
        assertEquals(1.2f, reopened.preferences.value.heightScale)
    }

    @Test
    fun outOfRangeHeightIsClamped() {
        val store = MapSettings()
        store.putFloat("layout.height_scale", 9f)
        val repository = SettingsPreferencesRepository(store)
        assertEquals(KeyboardPreferences.MAX_HEIGHT_SCALE, repository.preferences.value.heightScale)
    }
}
