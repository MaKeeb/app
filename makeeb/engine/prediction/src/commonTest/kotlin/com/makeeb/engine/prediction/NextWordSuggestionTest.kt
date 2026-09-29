package com.makeeb.engine.prediction

import com.makeeb.core.model.Suggestion
import com.makeeb.engine.dictionary.DeferredDictionary
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdNgramTable
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NextWordSuggestionTest {
    private val words = listOf(
        MkdWord("the", 230), MkdWord("to", 228), MkdWord("of", 225), MkdWord("good", 200), MkdWord("very", 190),
        MkdWord("later", 185), MkdWord("little", 184), MkdWord("life", 183), MkdWord("look", 182), MkdWord("morning", 170),
        MkdWord("luck", 120), MkdWord("lucky", 110), MkdWord("idea", 150), MkdWord("will", 185), MkdWord("Will", 120),
        MkdWord("you", 215), MkdWord("see", 195), MkdWord("damn", 199, offensive = true),
        MkdWord("then", 200), MkdWord("them", 205), MkdWord("in", 226), MkdWord("into", 205), MkdWord("inside", 180),
        MkdWord("income", 170), MkdWord("information", 175), MkdWord("inn", 60), MkdWord("inner", 150),
        MkdWord("innocent", 145), MkdWord("innings", 140),
    )
    private val meta = mapOf("language" to "en-US")
    private val ids = MappedDictionary(ByteArrayRegion(MkdWriter.write(words, meta))).let { lexicon -> words.associate { it.text to lexicon.wordId(it.text) } }
    private fun id(word: String) = ids.getValue(word)

    private val table = MkdNgramTable().apply {
        unigram(id("the"), 0.05)
        unigram(id("to"), 0.03)
        unigram(id("of"), 0.02)
        bigram(id("good"), id("morning"), 0.3)
        bigram(id("good"), id("luck"), 0.2)
        bigram(id("good"), id("damn"), 0.5)
        trigram(id("very"), id("good"), id("idea"), 0.5)
        bigram(id("will"), id("you"), 0.4)
        bigram(id("Will"), id("see"), 0.4)
        bigram(id("look"), id("into"), 0.3)
        bigram(id("look"), id("inside"), 0.2)
        bigram(id("look"), id("information"), 0.1)
        bigram(id("the"), id("inner"), 0.3)
        bigram(id("the"), id("innocent"), 0.2)
        bigram(id("the"), id("innings"), 0.1)
    }
    private val dictionary = MappedDictionary(ByteArrayRegion(MkdWriter.write(words, meta, table)))
    private val engine = DictionarySuggestionEngine(dictionary)

    private fun suggest(typed: String, vararg previous: String, fromSentenceStart: Boolean = false, engine: SuggestionEngine = this.engine) =
        engine.suggest(TypingContext(typed, previousWords = previous.toList(), atSentenceStart = false, previousWordsStartSentence = fromSentenceStart))

    @Test
    fun nothingTypedPredictsTheNextWordBestFirst() {
        val predicted = suggest("", "very", "good").suggestions
        assertEquals(listOf("idea", "morning", "luck"), predicted.map { it.text }, "trigram, then bigrams")
        assertTrue(predicted.all { it.kind == Suggestion.Kind.NextWord })
        assertEquals(listOf("morning", "luck", "the"), suggest("", "good").suggestions.map { it.text })
        assertEquals(null, suggest("", "good").autoCorrection)
    }

    @Test
    fun offensiveWordsAreNeitherPredictedNorRaised() {
        assertTrue(suggest("", "good").suggestions.none { it.text == "damn" })
        assertTrue(suggest("da", "good").suggestions.none { it.text == "damn" })
    }

    @Test
    fun aCapitalThatStartsTheSentenceIsNotAName() {
        assertEquals("you", suggest("", "Will", fromSentenceStart = true).suggestions.first().text)
        assertEquals("see", suggest("", "Will").suggestions.first().text)
    }

    @Test
    fun theContextRaisesTheCompletionsItPredicts() {
        // After "good", "l" puts "luck" (frequency 120) ahead of commoner l-words.
        assertEquals("luck", suggest("l", "good").suggestions.first().text)
        assertEquals(listOf("later", "little", "life"), suggest("l", "zzz").suggestions.map { it.text }, "no context: by frequency")
        val plain = DictionarySuggestionEngine(dictionary, contextWeight = 0.0)
        assertEquals(listOf("later", "little", "life"), suggest("l", "good", engine = plain).suggestions.map { it.text }, "boost off")
        assertEquals("luck", suggest("luc", "good").suggestions.first().text)
        assertEquals(suggest("luck", "good", engine = plain), suggest("luck", "good"), "\"lucky\" isn't predicted, so nothing changes")
    }

    @Test
    fun aKnownWordAsTypedKeepsASlotWhenTheContextRaisesOthers() {
        // "inn" is a rare word; after "the", the context raises three commoner completions past it.
        assertEquals(listOf("inner", "innocent", "inn"), suggest("inn", "the").suggestions.map { it.text })
        // A common word as typed stays first.
        assertEquals("in", suggest("in", "look").suggestions.first().text)
    }

    @Test
    fun theContextDoesNotChangeAutocorrect() {
        assertEquals(suggest("teh").autoCorrection, suggest("teh", "look").autoCorrection)
        assertEquals(suggest("tge").autoCorrection, suggest("tge", "very", "good").autoCorrection)
    }

    @Test
    fun predictionsArriveWhenThePackDoes() {
        val deferred = DeferredDictionary(StarterDictionaries.english())
        val engine = DictionarySuggestionEngine(deferred)
        assertEquals(emptyList(), suggest("", "good", engine = engine).suggestions, "the starter list has no statistics")
        deferred.install(dictionary)
        assertEquals("morning", suggest("", "good", engine = engine).suggestions.first().text)
    }

    @Test
    fun theSentenceStartListNeedsNoWords() {
        val table = MkdNgramTable().apply {
            bigram(MkdFormat.SENTENCE_START, id("see"), 0.2)
            unigram(id("the"), 0.05)
        }
        val engine = DictionarySuggestionEngine(MappedDictionary(ByteArrayRegion(MkdWriter.write(words, meta, table))))
        assertEquals(listOf("see", "the"), engine.suggest(TypingContext("", atSentenceStart = true)).suggestions.map { it.text })
        assertEquals(listOf("the"), engine.suggest(TypingContext("", atSentenceStart = false)).suggestions.map { it.text })
    }
}
