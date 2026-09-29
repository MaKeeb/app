package com.makeeb.engine.prediction

import com.makeeb.core.model.AutocorrectStrength
import com.makeeb.engine.dictionary.SelectedDictionaries
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.dictionary.WordEntry
import com.makeeb.engine.dictionary.TrieDictionary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun onlyWordsTheDictionaryLacksAreLearnedAndForgettable() {
        val user = UserDictionary("en")
        val engine = DictionarySuggestionEngine(StarterDictionaries.english(), user)
        engine.learn("hello")
        engine.learn("zorblax")
        assertEquals(listOf("zorblax"), user.words().map { it.word }, "the dictionary knows hello already")
        assertTrue(engine.isLearned("Zorblax"))
        assertFalse(engine.isLearned("hello"))

        engine.forget("hello")
        assertTrue(engine.suggest(TypingContext("hell")).suggestions.any { it.text == "hello" }, "dictionary words can't be forgotten")
        engine.forget("ZORBLAX")
        assertFalse(engine.isLearned("zorblax"))
        assertTrue(engine.suggest(TypingContext("zorb")).suggestions.none { it.text == "zorblax" })
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
    fun aTypoCommittedOnceIsStillCorrectedButTwiceOrKeptItIsTheUsers() {
        fun engine(user: UserDictionary) = DictionarySuggestionEngine(lexicon("hello" to 200, "help" to 190, "world" to 180), user)
        val once = engine(UserDictionary("en"))
        // Slipped through with autocorrect off or paused.
        once.learn("hwllo")
        assertEquals("hello", once.suggest(TypingContext("hwllo", keys = qwerty)).autoCorrection, "once is no evidence")
        assertTrue(once.suggest(TypingContext("hwl", keys = qwerty)).suggestions.any { it.text == "hwllo" }, "though it is offered")

        once.learn("hwllo")
        assertNull(once.suggest(TypingContext("hwllo", keys = qwerty)).autoCorrection, "typed twice: meant")

        val kept = engine(UserDictionary("en"))
        kept.keep("wirld")
        assertNull(kept.suggest(TypingContext("wirld", keys = qwerty)).autoCorrection, "kept once: meant")
    }

    @Test
    fun aKeptWordIsNotAKnownTypoAnyMore() {
        val once = DictionarySuggestionEngine(StarterDictionaries.english(), UserDictionary("en"))
        once.learn("teh")
        assertEquals("the", once.suggest(TypingContext("teh")).autoCorrection)
        val kept = DictionarySuggestionEngine(StarterDictionaries.english(), UserDictionary("en"))
        kept.keep("teh")
        assertNull(kept.suggest(TypingContext("teh")).autoCorrection)
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

    @Test
    fun modestWantsAClearerWin() {
        fun correct(strength: AutocorrectStrength) =
            DictionarySuggestionEngine(lexicon("hello" to 115)).suggest(TypingContext("hwllo", keys = qwerty, strength = strength)).autoCorrection
        // A neighbour slip into a fairly common word: Normal fixes it, Modest leaves it in the strip.
        assertEquals("hello", correct(AutocorrectStrength.Normal))
        assertNull(correct(AutocorrectStrength.Modest))
    }

    @Test
    fun aggressiveReachesSlipsTheNormalSearchDoesNot() {
        val engine = DictionarySuggestionEngine(lexicon("of" to 250, "hello" to 200))
        fun correct(typed: String, strength: AutocorrectStrength) =
            engine.suggest(TypingContext(typed, keys = qwerty, strength = strength)).autoCorrection
        assertNull(correct("og", AutocorrectStrength.Normal), "two letters: no search")
        assertEquals("of", correct("og", AutocorrectStrength.Aggressive))
        assertNull(correct("hwlko", AutocorrectStrength.Normal), "two slips in five letters")
        assertEquals("hello", correct("hwlko", AutocorrectStrength.Aggressive))
    }

    @Test
    fun aSelectedLanguageTheDictionaryDoesNotCoverOnlySuggests() {
        val engine = DictionarySuggestionEngine(lexicon("hello" to 200, "don't" to 200))
        val mixed = engine.suggest(TypingContext("hwllo", keys = qwerty, languages = listOf("en", "hu")))
        assertNull(mixed.autoCorrection, "without a Hungarian lexicon, a Hungarian word looks like an English typo")
        assertEquals("hello", mixed.suggestions.first().text, "the correction stays first in the strip")
        assertNull(engine.suggest(TypingContext("dont", languages = listOf("hu", "en"))).autoCorrection, "known typos too: Hungarian has dont")
        assertEquals("hello", engine.suggest(TypingContext("hwllo", keys = qwerty, languages = listOf("en-GB"))).autoCorrection, "another English is covered")
        assertEquals("hello", engine.suggest(TypingContext("hwllo", keys = qwerty)).autoCorrection, "no languages: the dictionary's own")
    }

    @Test
    fun onceEverySelectedLanguageHasALexiconAutocorrectRunsAndLeavesTheirWordsAlone() {
        val hungarian = object : TrieDictionary("hu", listOf(WordEntry("szia", 200), WordEntry("helló", 190), WordEntry("kérdés", 180))) {
            override val isComprehensive = true
        }
        val engine = DictionarySuggestionEngine(SelectedDictionaries(hungarian, listOf(lexicon("hello" to 200, "the" to 220))), UserDictionary("hu"))
        val both = listOf("hu", "en")
        assertEquals("szia", engine.suggest(TypingContext("szis", keys = qwerty, languages = both)).autoCorrection, "the pause lifts")
        assertEquals("kérdés", engine.suggest(TypingContext("kerdes", languages = both)).autoCorrection, "the primary's accents")
        assertNull(engine.suggest(TypingContext("hello", languages = both)).autoCorrection, "an English word, not helló without its accent")
        assertNull(engine.suggest(TypingContext("the", keys = qwerty, languages = both)).autoCorrection, "valid in English")
        assertNull(engine.suggest(TypingContext("szis", keys = qwerty, languages = listOf("hu", "en", "sv"))).autoCorrection, "Swedish has no lexicon yet")
        assertTrue(engine.suggest(TypingContext("hel", languages = both)).suggestions.none { it.text == "hello" }, "suggestions stay the primary's")
    }

    @Test
    fun anotherLanguagesExactWordIsNotTakenForTheprimarysName() {
        // Hungarian news capitalises "Hello" (Hello Kitty): as the primary's only spelling it would
        // capitalise English "hello" mid-sentence.
        val hungarian = object : TrieDictionary("hu", listOf(WordEntry("Hello", 150), WordEntry("London", 160), WordEntry("szia", 200))) {
            override val isComprehensive = true
        }
        val engine = DictionarySuggestionEngine(SelectedDictionaries(hungarian, listOf(lexicon("hello" to 200))), UserDictionary("hu"))
        val both = listOf("hu", "en")
        assertNull(engine.suggest(TypingContext("hello", languages = both, atSentenceStart = false)).autoCorrection, "English hello stays lower case")
        assertEquals("London", engine.suggest(TypingContext("london", languages = both, atSentenceStart = false)).autoCorrection, "a name only a name still gets its capital")
    }
}
