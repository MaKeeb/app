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

        repository.update { it.copy(autoCorrect = false, theme = ThemeMode.Dark, portraitSize = KeyboardSize(heightScale = 1.2f)) }

        val reopened = SettingsPreferencesRepository(store)
        assertFalse(reopened.preferences.value.autoCorrect)
        assertEquals(ThemeMode.Dark, reopened.preferences.value.theme)
        assertEquals(1.2f, reopened.preferences.value.portraitSize.heightScale)
    }

    @Test
    fun eachOrientationKeepsItsOwnSize() {
        val store = MapSettings()
        SettingsPreferencesRepository(store).update {
            it.copy(portraitSize = KeyboardSize(heightScale = 1.1f, bottomOffset = 8f), landscapeSize = KeyboardSize(heightScale = 0.9f, bottomOffset = 16f))
        }
        val reopened = SettingsPreferencesRepository(store).preferences.value
        assertEquals(KeyboardSize(heightScale = 1.1f, bottomOffset = 8f), reopened.portraitSize)
        assertEquals(KeyboardSize(heightScale = 0.9f, bottomOffset = 16f), reopened.landscapeSize)
        // The storage keys are persisted: never rename one.
        assertEquals(1.1f, store.getFloat("layout.portrait.height_scale", 0f))
        assertEquals(8f, store.getFloat("layout.portrait.bottom_offset", 0f))
        assertEquals(0.9f, store.getFloat("layout.landscape.height_scale", 0f))
        assertEquals(16f, store.getFloat("layout.landscape.bottom_offset", 0f))
    }

    @Test
    fun theSingleHeightOfEarlierVersionsBecomesThePortraitHeight() {
        val store = MapSettings()
        store.putFloat("layout.height_scale", 1.2f)
        val repository = SettingsPreferencesRepository(store)
        assertEquals(KeyboardSize(heightScale = 1.2f), repository.preferences.value.portraitSize)
        assertEquals(KeyboardSize(), repository.preferences.value.landscapeSize, "landscape starts from its own default")

        repository.update { it.copy(numberRow = true) }
        assertFalse(store.hasKey("layout.height_scale"), "the old key goes once the sizes are written")
        assertEquals(1.2f, store.getFloat("layout.portrait.height_scale", 0f))
        assertEquals(1.2f, SettingsPreferencesRepository(store).preferences.value.portraitSize.heightScale)
    }

    @Test
    fun aStoredPortraitHeightWinsOverTheOldOne() {
        val store = MapSettings()
        store.putFloat("layout.height_scale", 1.2f)
        store.putFloat("layout.portrait.height_scale", 0.9f)
        assertEquals(0.9f, SettingsPreferencesRepository(store).preferences.value.portraitSize.heightScale)
    }

    @Test
    fun languagesDefaultToThePlatformsAndPersistInOrder() {
        val store = MapSettings()
        assertEquals(listOf("en"), SettingsPreferencesRepository(store).preferences.value.languageTags)
        val phone = KeyboardPreferences(languageTags = listOf("hu", "en"))
        assertEquals(listOf("hu", "en"), SettingsPreferencesRepository(store, phone).preferences.value.languageTags)
        SettingsPreferencesRepository(store, phone).update { it.copy(languageTags = listOf("en", "sv", "hu")) }
        assertEquals(listOf("en", "sv", "hu"), SettingsPreferencesRepository(store, phone).preferences.value.languageTags)
        assertEquals("en,sv,hu", store.getString("layout.languages", ""), "the storage key is persisted: never rename it")
    }

    @Test
    fun theSingleLanguageOfEarlierVersionsCarriesOver() {
        val store = MapSettings()
        store.putString("layout.language", "de")
        val repository = SettingsPreferencesRepository(store, KeyboardPreferences(languageTags = listOf("hu")))
        assertEquals(listOf("de"), repository.preferences.value.languageTags, "a stored choice beats the phone's languages")
        repository.update { it.copy(numberRow = true) }
        assertEquals("de", store.getString("layout.languages", ""))
        assertFalse(store.hasKey("layout.language"), "the old key goes once the list is written")
    }

    @Test
    fun outOfRangeSizesAreClamped() {
        val store = MapSettings()
        store.putFloat("layout.height_scale", 9f)
        store.putFloat("layout.portrait.bottom_offset", -5f)
        store.putFloat("layout.landscape.height_scale", 0.1f)
        store.putFloat("layout.landscape.bottom_offset", 500f)
        val preferences = SettingsPreferencesRepository(store).preferences.value
        assertEquals(KeyboardSize(KeyboardSize.MAX_HEIGHT_SCALE, 0f), preferences.portraitSize)
        assertEquals(KeyboardSize(KeyboardSize.MIN_HEIGHT_SCALE, KeyboardSize.MAX_BOTTOM_OFFSET), preferences.landscapeSize)
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
