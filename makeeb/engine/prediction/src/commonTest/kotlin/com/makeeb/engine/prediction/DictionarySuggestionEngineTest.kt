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

    @Test
    fun aTypedFormMissingOnlyAccentsOrAnApostropheIsCorrected() {
        val big = TrieDictionary("en", listOf(WordEntry("I'm", 220), WordEntry("café", 120), WordEntry("its", 200), WordEntry("it's", 210), WordEntry("ill", 100), WordEntry("I'll", 180)))
        val engine = DictionarySuggestionEngine(big)
        assertEquals("I'm", engine.suggest(TypingContext("im")).autoCorrection)
        assertEquals("café", engine.suggest(TypingContext("cafe")).autoCorrection)
        assertEquals("Café", engine.suggest(TypingContext("Cafe")).autoCorrection)
        assertNull(engine.suggest(TypingContext("its")).autoCorrection, "a word in its own right")
        assertNull(engine.suggest(TypingContext("ill")).autoCorrection)
    }

    /** A full lexicon stand-in: typo correction only runs against one. */
    private fun lexicon(vararg words: Pair<String, Int>) = object : TrieDictionary("en", words.map { WordEntry(it.first, it.second) }) {
        override val isComprehensive = true
    }

    private val qwerty = KeyPositions { char ->
        val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
        rows.withIndex().firstNotNullOfOrNull { (row, keys) -> keys.first.indexOf(char).takeIf { it >= 0 }?.let { (keys.second + it + 0.5f) to (row + 0.5f) } }
    }

    @Test
    fun aNeighbouringKeySlipInACommonWordIsCorrected() {
        val engine = DictionarySuggestionEngine(lexicon("hello" to 200, "help" to 190, "world" to 180, "jello" to 60))
        assertEquals("hello", engine.suggest(TypingContext("hwllo", keys = qwerty)).autoCorrection, "w is next to e")
        assertEquals("world", engine.suggest(TypingContext("wirld", keys = qwerty)).autoCorrection, "i is next to o")
    }

    @Test
    fun unknownWordsThatAreNotNearMissesStay() {
        val engine = DictionarySuggestionEngine(lexicon("hello" to 200, "kitchen" to 150, "karaoke" to 60))
        assertNull(engine.suggest(TypingContext("kiraly", keys = qwerty)).autoCorrection, "a name, not a slip")
        assertNull(engine.suggest(TypingContext("Kitcheb", keys = qwerty, atSentenceStart = false)).autoCorrection, "capitalised mid-sentence: a name")
        assertNull(engine.suggest(TypingContext("HWLLO", keys = qwerty)).autoCorrection, "capitals")
        assertNull(engine.suggest(TypingContext("hwll0", keys = qwerty)).autoCorrection, "digits")
    }

    @Test
    fun aDistantSubstitutionInARareWordIsOnlyASuggestion() {
        val engine = DictionarySuggestionEngine(lexicon("karaoke" to 40))
        val prediction = engine.suggest(TypingContext("karaoxe", keys = qwerty))
        assertNull(prediction.autoCorrection)
        assertTrue(prediction.suggestions.any { it.text == "karaoke" }, "still one tap away")
    }
}
