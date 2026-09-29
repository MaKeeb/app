package com.makeeb.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrieDictionaryTest {
    private val dictionary = TrieDictionary(
        "en",
        listOf(WordEntry("the", 255), WordEntry("then", 200), WordEntry("there", 210), WordEntry("London", 100)),
    )

    @Test
    fun lookupIsCaseInsensitiveAndKeepsCanonicalCase() {
        assertEquals("London", dictionary.lookup("london")?.word)
        assertNull(dictionary.lookup("paris"))
    }

    @Test
    fun completionsAreOrderedByFrequency() {
        assertEquals(listOf("the", "there", "then"), dictionary.completions("th", 5).map { it.word })
    }

    @Test
    fun correctionsFindTranspositionsAndTypos() {
        val matches = dictionary.corrections("teh", maxEdits = 1, limit = 3)
        assertEquals("the", matches.first().entry.word)
        assertEquals(1, matches.first().edits) // a transposition is one edit
        assertTrue(dictionary.corrections("thete", maxEdits = 1, limit = 3).any { it.entry.word == "there" })
    }
}
