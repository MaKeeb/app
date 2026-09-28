package com.makeeb.engine.gesture

import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.LayoutOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class KeySequenceGestureDecoderTest {
    private val geometry = LayoutGeometry(
        BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions()),
        width = 1000f,
        rowHeight = 100f,
    )
    private val decoder = KeySequenceGestureDecoder(StarterDictionaries.english())

    /** A straight-line swipe through the centres of [letters], sampled every few pixels. */
    private fun swipe(letters: String): List<GesturePoint> {
        val centres = letters.map { geometry.keyFor(it)!!.bounds.let { b -> b.centerX to b.centerY } }
        return centres.zipWithNext().flatMap { (from, to) ->
            (0 until 20).map { step ->
                val t = step / 20f
                GesturePoint(from.first + (to.first - from.first) * t, from.second + (to.second - from.second) * t, 0)
            }
        } + GesturePoint(centres.last().first, centres.last().second, 0)
    }

    @Test
    fun decodesAStraightSwipe() {
        val words = decoder.decode(swipe("hello"), geometry).map { it.text }
        assertEquals("hello", words.first())
    }

    @Test
    fun shortPathsDecodeToNothing() {
        assertEquals(emptyList(), decoder.decode(swipe("h"), geometry))
    }
}
