package com.makeeb.core.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun scheduledThemeIsDarkBetweenItsHoursAcrossMidnight() {
        val night = KeyboardPreferences(theme = ThemeMode.Scheduled, darkFromMinute = 21 * 60, darkUntilMinute = 7 * 60)
        assertTrue(night.useDarkTheme(systemDark = false, minuteOfDay = 23 * 60))
        assertTrue(night.useDarkTheme(systemDark = false, minuteOfDay = 6 * 60 + 59))
        assertFalse(night.useDarkTheme(systemDark = true, minuteOfDay = 12 * 60), "the schedule wins over the system")
        val day = night.copy(darkFromMinute = 9 * 60, darkUntilMinute = 17 * 60)
        assertTrue(day.useDarkTheme(systemDark = false, minuteOfDay = 12 * 60))
        assertFalse(day.useDarkTheme(systemDark = false, minuteOfDay = 17 * 60))
        assertTrue(KeyboardPreferences(theme = ThemeMode.System).useDarkTheme(systemDark = true, minuteOfDay = 0))
    }

    @Test
    fun scheduleHoursPersist() {
        val store = MapSettings()
        SettingsPreferencesRepository(store).update { it.copy(theme = ThemeMode.Scheduled, darkFromMinute = 20 * 60, darkUntilMinute = 6 * 60) }
        val reopened = SettingsPreferencesRepository(store).preferences.value
        assertEquals(ThemeMode.Scheduled, reopened.theme)
        assertEquals(20 * 60, reopened.darkFromMinute)
        assertEquals(6 * 60, reopened.darkUntilMinute)
    }
}
