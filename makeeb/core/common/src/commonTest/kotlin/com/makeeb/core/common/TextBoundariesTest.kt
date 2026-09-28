package com.makeeb.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextBoundariesTest {
    @Test
    fun trailingWordStopsAtWhitespaceAndPunctuation() {
        assertEquals("wor", TextBoundaries.trailingWord("hello wor"))
        assertEquals("", TextBoundaries.trailingWord("hello "))
        assertEquals("don't", TextBoundaries.trailingWord("I don't"))
        assertEquals("x", TextBoundaries.trailingWord("(x"))
    }

    @Test
    fun previousWordsSkipsTheCurrentWord() {
        assertEquals(listOf("see", "you"), TextBoundaries.previousWords("I will see you lat", 2))
    }

    @Test
    fun sentenceStartDetection() {
        assertTrue(TextBoundaries.isSentenceStart(""))
        assertTrue(TextBoundaries.isSentenceStart("Done. "))
        assertTrue(TextBoundaries.isSentenceStart("He said \"Stop.\" "))
        assertTrue(TextBoundaries.isSentenceStart("line\n"))
        assertFalse(TextBoundaries.isSentenceStart("Done."))
        assertFalse(TextBoundaries.isSentenceStart("and then "))
    }
}
