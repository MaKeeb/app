package com.makeeb.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardSizeTest {
    @Test
    fun aScreenWiderThanTallIsLandscape() {
        assertEquals(ScreenOrientation.Portrait, ScreenOrientation.of(width = 412f, height = 915f))
        assertEquals(ScreenOrientation.Landscape, ScreenOrientation.of(width = 915f, height = 412f))
        assertEquals(ScreenOrientation.Landscape, ScreenOrientation.of(width = 1366f, height = 1024f), "a tablet in landscape")
        assertEquals(ScreenOrientation.Portrait, ScreenOrientation.of(width = 800f, height = 800f), "a square screen")
    }

    @Test
    fun eachOrientationChangesOnlyItsOwnSize() {
        val raised = KeyboardSize(heightScale = 1.2f, bottomOffset = 12f)
        val preferences = KeyboardPreferences().withSize(ScreenOrientation.Landscape, raised)
        assertEquals(raised, preferences.size(ScreenOrientation.Landscape))
        assertEquals(KeyboardSize(), preferences.size(ScreenOrientation.Portrait))
        assertFalse(preferences.hasDefaultSizes)

        val reset = preferences.withSize(ScreenOrientation.Portrait, raised).withDefaultSizes()
        assertTrue(reset.hasDefaultSizes)
        assertEquals(KeyboardPreferences(), reset)
    }

    @Test
    fun coercingKeepsBothValuesInTheirRanges() {
        assertEquals(KeyboardSize(1.1f, 20f), KeyboardSize(1.1f, 20f).coerced())
        assertEquals(KeyboardSize(KeyboardSize.MIN_HEIGHT_SCALE, 0f), KeyboardSize(0f, -1f).coerced())
        assertEquals(KeyboardSize(KeyboardSize.MAX_HEIGHT_SCALE, KeyboardSize.MAX_BOTTOM_OFFSET), KeyboardSize(5f, 99f).coerced())
    }
}
