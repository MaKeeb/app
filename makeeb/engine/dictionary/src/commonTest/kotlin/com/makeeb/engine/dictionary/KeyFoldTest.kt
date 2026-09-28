package com.makeeb.engine.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyFoldTest {
    @Test
    fun foldsCaseDiacriticsLettersAndApostrophes() {
        assertEquals("naive", KeyFold.fold("Naïve"))
        assertEquals("cafe", KeyFold.fold("café"))
        assertEquals("strasse", KeyFold.fold("Straße"))
        assertEquals("aeroskobing", KeyFold.fold("Ærøskøbing"))
        assertEquals("lodz", KeyFold.fold("Łódź"))
        assertEquals("dont", KeyFold.fold("don't"))
        assertEquals("dont", KeyFold.fold("don’t"))
        assertEquals("tieng viet", KeyFold.fold("Tiếng Việt"))
        assertEquals("москва", KeyFold.fold("Москва"), "other scripts only lower-case")
    }

    @Test
    fun theTrieFindsAccentedAndApostrophedSpellings() {
        val words = TrieDictionary("en", listOf(WordEntry("naïve", 90), WordEntry("café", 120), WordEntry("don't", 200), WordEntry("us", 200), WordEntry("US", 150)))
        assertEquals("naïve", words.lookup("naive")?.word)
        assertEquals("café", words.completions("caf", 3).single().word)
        assertEquals("don't", words.lookup("dont")?.word)
        assertEquals("US", words.lookup("US")?.word, "the exact spelling when it exists")
        assertEquals("us", words.lookup("Us")?.word, "else the most frequent")
        assertTrue(words.corrections("cafs", 1, 3).any { it.entry.word == "café" })
    }
}
