package com.makeeb.shared.keyboard

import com.makeeb.core.settings.KeyboardPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardMetricsTest {
    private val defaults = KeyboardPreferences()

    @Test
    fun portraitScreensKeepThePreferredRowHeight() {
        assertEquals(54f, KeyboardMetrics.rowHeight(defaults, screenHeight = 874f))
        assertEquals(KeyboardMetrics.keysAreaHeight(defaults), KeyboardMetrics.keysAreaHeight(defaults, screenHeight = 874f))
    }

    @Test
    fun landscapePhonesGetShorterRows() {
        assertEquals(36f, KeyboardMetrics.rowHeight(defaults, screenHeight = 400f), absoluteTolerance = 0.01f)
        assertEquals(144f, KeyboardMetrics.keysAreaHeight(defaults, screenHeight = 400f), absoluteTolerance = 0.01f)
    }
}
