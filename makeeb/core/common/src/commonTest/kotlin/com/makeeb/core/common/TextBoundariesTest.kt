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
    fun wordsBeforeSkipTheCurrentWordAndStopAtTheSentence() {
        assertEquals(SentenceWords(listOf("see", "you"), false), TextBoundaries.wordsBefore("I will see you lat", 2))
        assertEquals(SentenceWords(listOf("see", "you"), false), TextBoundaries.wordsBefore("I will see you ", 2))
        assertEquals(SentenceWords(listOf("Will", "you"), true), TextBoundaries.wordsBefore("Will you ", 2))
        assertEquals(SentenceWords(listOf("How"), true), TextBoundaries.wordsBefore("Hi there. How ", 2))
        assertEquals(SentenceWords(emptyList(), true), TextBoundaries.wordsBefore("Hi there. ", 2))
        assertEquals(SentenceWords(emptyList(), true), TextBoundaries.wordsBefore("", 2))
        assertEquals(SentenceWords(listOf("Next"), true), TextBoundaries.wordsBefore("Done\nNext ", 2))
    }

    @Test
    fun wordsBeforeLookThroughPunctuationInsideASentence() {
        assertEquals(SentenceWords(listOf("Hi", "how"), true), TextBoundaries.wordsBefore("Hi, how ", 2))
        assertEquals(SentenceWords(listOf("said", "hello"), false), TextBoundaries.wordsBefore("she said \"hello\" ", 2))
        assertEquals(SentenceWords(listOf("5"), false), TextBoundaries.wordsBefore("costs 3.5 ", 1), "a decimal point is no sentence end")
        assertEquals(SentenceWords(listOf("Then"), true), TextBoundaries.wordsBefore("He said \"Stop.\" Then ", 2))
        assertEquals(SentenceWords(listOf("don't", "know"), false), TextBoundaries.wordsBefore("I don't know ", 2))
    }

    @Test
    fun sentenceEndsNeedWhitespaceAfterTheTerminator() {
        assertTrue(TextBoundaries.endsSentence(". "))
        assertTrue(TextBoundaries.endsSentence("?! "))
        assertTrue(TextBoundaries.endsSentence(".\" "))
        assertTrue(TextBoundaries.endsSentence("\n"))
        assertFalse(TextBoundaries.endsSentence("."))
        assertFalse(TextBoundaries.endsSentence(", "))
        assertFalse(TextBoundaries.endsSentence(" - "))
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

    @Test
    fun wordJumpsSkipSpacesAndPunctuationThenTheWord() {
        assertEquals(7, TextBoundaries.previousWordStart("hello world, "))
        assertEquals(3, TextBoundaries.previousWordStart("it's wor"))
        assertEquals(0, TextBoundaries.previousWordStart(""))
        assertEquals(6, TextBoundaries.nextWordEnd(" hello world"))
        assertEquals(7, TextBoundaries.nextWordEnd(", don't"))
        assertEquals(2, TextBoundaries.nextWordEnd("  "))
    }
}
