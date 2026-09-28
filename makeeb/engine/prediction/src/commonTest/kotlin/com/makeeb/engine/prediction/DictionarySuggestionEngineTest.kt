package com.makeeb.engine.prediction

import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.dictionary.WordEntry
import com.makeeb.engine.dictionary.TrieDictionary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DictionarySuggestionEngineTest {
    private val engine = DictionarySuggestionEngine(StarterDictionaries.english(), UserDictionary("en"))

    @Test
    fun correctsCommonTypos() {
        assertEquals("the", engine.suggest(TypingContext("teh")).autoCorrection)
        assertEquals("The", engine.suggest(TypingContext("Teh")).autoCorrection)
    }

    @Test
    fun knownWordsAreNeverAutocorrected() {
        assertNull(engine.suggest(TypingContext("then")).autoCorrection)
    }

    @Test
    fun wordsMissingFromTheStarterListAreLeftAlone() {
        // "cat" isn't in the starter list and sits one edit from "at": it used to become "at".
        val cat = engine.suggest(TypingContext("cat"))
        assertNull(cat.autoCorrection)
        assertTrue(cat.suggestions.any { it.text == "cat" }, "the typed word stays one tap away")
    }

    @Test
    fun knownTyposAndMissingApostrophesAreFixed() {
        assertEquals("don't", engine.suggest(TypingContext("dont")).autoCorrection)
        assertEquals("I'm", engine.suggest(TypingContext("im")).autoCorrection)
        assertEquals("I", engine.suggest(TypingContext("i")).autoCorrection)
        assertEquals("the", engine.suggest(TypingContext("teh")).suggestions.first().text, "the correction leads the strip")
    }

    @Test
    fun completesPrefixes() {
        val words = engine.suggest(TypingContext("hel"), limit = 5).suggestions.map { it.text }
        assertTrue("hello" in words && "help" in words, "got $words")
    }

    @Test
    fun unknownWordsAreOfferedVerbatim() {
        val words = engine.suggest(TypingContext("zzqx")).suggestions.map { it.text }
        assertEquals(listOf("zzqx"), words)
    }

    @Test
    fun learnedWordsBecomeSuggestions() {
        engine.learn("MaKeeb")
        assertTrue(engine.suggest(TypingContext("MaK")).suggestions.any { it.text == "MaKeeb" })
    }

    @Test
    fun knownTyposWinOverRareWordsAndOnlyProperNounsGetCapitals() {
        // Large word lists hold "cant" and "wont" as words and "NAD" as an acronym.
        val big = TrieDictionary("en", listOf(WordEntry("cant", 40), WordEntry("wont", 30), WordEntry("NAD", 20), WordEntry("London", 120), WordEntry("and", 250)))
        val engine = DictionarySuggestionEngine(big)
        assertEquals("can't", engine.suggest(TypingContext("cant")).autoCorrection)
        assertEquals("won't", engine.suggest(TypingContext("wont")).autoCorrection)
        assertEquals("and", engine.suggest(TypingContext("nad")).autoCorrection)
        assertEquals("London", engine.suggest(TypingContext("london")).autoCorrection)
    }
}
