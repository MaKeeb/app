package com.makeeb.engine.prediction

import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.UserDictionary
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
}
