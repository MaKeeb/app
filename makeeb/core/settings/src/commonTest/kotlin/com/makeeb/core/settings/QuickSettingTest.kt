package com.makeeb.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickSettingTest {
    @Test
    fun eachSwitchFlipsOnlyItsOwnPreference() {
        val start = KeyboardPreferences()
        QuickSetting.entries.filter { it != QuickSetting.Theme }.forEach { setting ->
            val flipped = setting.next(start)
            assertEquals(!setting.isOn(start), setting.isOn(flipped), "$setting flips")
            QuickSetting.entries.filter { it != setting }.forEach { other ->
                assertEquals(other.isOn(start), other.isOn(flipped), "$setting leaves $other alone")
            }
            assertEquals(start, setting.next(flipped), "a second tap restores $setting")
        }
    }

    @Test
    fun themeCyclesThroughEveryModeAndIsNeverLit() {
        var prefs = KeyboardPreferences(theme = ThemeMode.System)
        val seen = List(ThemeMode.entries.size) {
            prefs = QuickSetting.Theme.next(prefs)
            assertFalse(QuickSetting.Theme.isOn(prefs))
            prefs.theme
        }
        assertEquals(listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Scheduled, ThemeMode.System), seen)
        assertEquals("Light", QuickSetting.Theme.valueLabel(KeyboardPreferences(theme = ThemeMode.Light)))
    }

    @Test
    fun switchesLabelTheirState() {
        assertEquals("Off", QuickSetting.NumberRow.valueLabel(KeyboardPreferences(numberRow = false)))
        assertEquals("On", QuickSetting.NumberRow.valueLabel(KeyboardPreferences(numberRow = true)))
    }

    @Test
    fun searchNeedsEveryWordInAnyFieldIgnoringCaseAndOrder() {
        assertTrue(matchesSettingsSearch("", "Theme"))
        assertTrue(matchesSettingsSearch("  ", "Theme"))
        assertTrue(matchesSettingsSearch("THEME", "Theme"))
        assertTrue(matchesSettingsSearch("dark theme", "Theme", null, "Appearance", "dark light night"))
        assertTrue(matchesSettingsSearch("theme dark", "Theme", null, "Appearance", "dark light night"))
        assertTrue(matchesSettingsSearch("vibr", "Vibrate on keypress"))
        assertFalse(matchesSettingsSearch("dark sound", "Theme", null, "Appearance", "dark light night"))
        assertFalse(matchesSettingsSearch("xyz", "Theme"))
    }
}
