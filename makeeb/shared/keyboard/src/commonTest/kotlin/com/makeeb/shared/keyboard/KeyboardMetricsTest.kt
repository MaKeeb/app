package com.makeeb.shared.keyboard

import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.KeyboardSize
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardMetricsTest {
    private val defaults = KeyboardPreferences()
    private val portraitPhone = ScreenSize(width = 402f, height = 874f)
    private val landscapePhone = ScreenSize(width = 874f, height = 400f)
    private val landscapeTablet = ScreenSize(width = 1366f, height = 1024f)

    @Test
    fun portraitScreensKeepThePreferredRowHeight() {
        assertEquals(54f, KeyboardMetrics.rowHeight(defaults, portraitPhone))
        assertEquals(KeyboardMetrics.keysAreaHeight(defaults), KeyboardMetrics.keysAreaHeight(defaults, portraitPhone))
    }

    @Test
    fun landscapePhonesGetShorterRows() {
        assertEquals(36f, KeyboardMetrics.rowHeight(defaults, landscapePhone), absoluteTolerance = 0.01f)
        assertEquals(144f, KeyboardMetrics.keysAreaHeight(defaults, landscapePhone), absoluteTolerance = 0.01f)
    }

    @Test
    fun tabletsKeepFullRowsInLandscape() {
        assertEquals(54f, KeyboardMetrics.rowHeight(defaults, landscapeTablet))
    }

    @Test
    fun eachOrientationUsesItsOwnHeight() {
        val preferences = KeyboardPreferences(portraitSize = KeyboardSize(heightScale = 1.2f), landscapeSize = KeyboardSize(heightScale = 0.8f))
        assertEquals(54f * 1.2f, KeyboardMetrics.rowHeight(preferences, portraitPhone), absoluteTolerance = 0.01f)
        // The landscape height scales the row that fits the screen, so it still has an effect there.
        assertEquals(36f * 0.8f, KeyboardMetrics.rowHeight(preferences, landscapePhone), absoluteTolerance = 0.01f)
        assertEquals(54f * 1.2f, KeyboardMetrics.rowHeight(preferences), absoluteTolerance = 0.01f, "an unknown screen is portrait")
    }

    @Test
    fun theHeightStillScalesWithTheNumberRow() {
        val preferences = KeyboardPreferences(numberRow = true, landscapeSize = KeyboardSize(heightScale = 1.25f))
        assertEquals(36f * 1.25f * 4.8f, KeyboardMetrics.keysAreaHeight(preferences, landscapePhone), absoluteTolerance = 0.01f)
    }

    @Test
    fun theBottomOffsetAddsToTheTotalInItsOwnOrientation() {
        val preferences = KeyboardPreferences(portraitSize = KeyboardSize(bottomOffset = 12f), landscapeSize = KeyboardSize(bottomOffset = 4f))
        assertEquals(12f, KeyboardMetrics.bottomOffset(preferences, portraitPhone))
        assertEquals(4f, KeyboardMetrics.bottomOffset(preferences, landscapePhone))

        val portraitTotal = KeyboardMetrics.STRIP_HEIGHT + 216f + KeyboardMetrics.BOTTOM_PADDING
        assertEquals(portraitTotal, KeyboardMetrics.totalHeight(defaults, portraitPhone), absoluteTolerance = 0.01f)
        assertEquals(portraitTotal + 12f, KeyboardMetrics.totalHeight(preferences, portraitPhone), absoluteTolerance = 0.01f)
        assertEquals(
            KeyboardMetrics.keysAreaHeight(defaults, portraitPhone),
            KeyboardMetrics.keysAreaHeight(preferences, portraitPhone),
            "the gap raises the keys without shrinking them",
        )
    }

    @Test
    fun keysStopAtTheirWidestAndCentreOnWideScreens() {
        assertEquals(KeyboardMetrics.SIDE_INSET, KeyboardMetrics.horizontalInset(412f), "a portrait phone keeps its side inset")
        assertEquals(0f, KeyboardMetrics.horizontalInset(402f, sideInset = 0f), "iOS keys run to the edge")
        assertEquals(KeyboardMetrics.SIDE_INSET, KeyboardMetrics.horizontalInset(KeyboardMetrics.MAX_KEYS_WIDTH + 20f))
        assertEquals(263f, KeyboardMetrics.horizontalInset(1366f, sideInset = 0f), "an iPad in landscape")
        assertEquals(25.5f, KeyboardMetrics.horizontalInset(891f), absoluteTolerance = 0.01f, message = "a landscape phone")
    }
}
