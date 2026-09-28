package com.makeeb.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemesTest {
    private fun last(text: String) = text.substring(text.length - Graphemes.lastLength(text))

    @Test
    fun plainTextIsOneCharacter() {
        assertEquals(0, Graphemes.lastLength(""))
        assertEquals("o", last("hello"))
        assertEquals("\n", last("a\n"))
        assertEquals("\r\n", last("a\r\n"))
    }

    @Test
    fun surrogatePairsAndCombiningMarksStayWhole() {
        assertEquals("😀", last("hi 😀"))
        assertEquals("é", last("café")) // decomposed é
        assertEquals("𝒳", last("x𝒳"))
    }

    @Test
    fun emojiSequencesAreOneCharacter() {
        assertEquals("👍🏽", last("ok 👍🏽")) // skin tone
        assertEquals("❤️", last("a❤️")) // variation selector
        assertEquals("👨‍👩‍👧", last("x👨‍👩‍👧")) // ZWJ family
        assertEquals("🧑🏽‍💻", last("🧑🏽‍💻")) // tone inside a ZWJ sequence
        assertEquals("1️⃣", last("a1️⃣")) // keycap
        assertEquals("🏴󠁧󠁢󠁥󠁮󠁧󠁿", last("a🏴󠁧󠁢󠁥󠁮󠁧󠁿")) // England
    }

    @Test
    fun flagsPairRegionalIndicatorsFromTheStartOfTheRun() {
        assertEquals("🇩🇪", last("🇬🇧🇩🇪"))
        assertEquals("🇩", last("🇬🇧🇩")) // an unpaired indicator on its own
    }

    @Test
    fun aLongTextOnlyLooksAtItsTail() {
        assertEquals("👍🏽", last("x".repeat(500) + "👍🏽"))
        // A window starting inside a surrogate pair must not split it.
        assertEquals("b", last("😀".repeat(40) + "b"))
    }
}
