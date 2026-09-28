package com.makeeb.engine.prediction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeightedEditsTest {
    private val qwerty = KeyPositions { char ->
        val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
        rows.withIndex().firstNotNullOfOrNull { (row, keys) -> keys.first.indexOf(char).takeIf { it >= 0 }?.let { (keys.second + it + 0.5f) to (row + 0.5f) } }
    }

    @Test
    fun neighbourSlipsAreCheapFarKeysAreNot() {
        val neighbour = WeightedEdits.distance("hwllo", "hello", qwerty)
        val far = WeightedEdits.distance("hmllo", "hello", qwerty)
        assertTrue(neighbour < 0.1f, "w is next to e: $neighbour")
        assertTrue(far > 1f, "m is far from e: $far")
        assertTrue(WeightedEdits.distance("helo", "hello", qwerty) < WeightedEdits.distance("hwlo", "hello", qwerty), "a skipped doubled letter is forgiven")
    }

    @Test
    fun aTapOnTheSharedEdgeIsACheaperSlipThanOneOnTheKeyCentre() {
        // "w" typed; e's centre is at x 2.5 of the top row, w's at 1.5.
        val onEdge = listOf(null, TapPoint(1.95f, 0.5f), null, null, null)
        val onCentre = listOf(null, TapPoint(1.5f, 0.5f), null, null, null)
        val edge = WeightedEdits.distance("hwllo", "hello", qwerty, onEdge)
        val centre = WeightedEdits.distance("hwllo", "hello", qwerty, onCentre)
        assertTrue(edge < centre / 2, "edge $edge vs centre $centre")
        assertEquals(WeightedEdits.distance("hwllo", "hello", qwerty), centre, absoluteTolerance = 0.001f, message = "a centre tap is a plain neighbour slip")
    }
}
