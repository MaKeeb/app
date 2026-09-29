package com.makeeb.engine.dictionary

import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdNgramTable
import com.makeeb.engine.dictionary.pack.MkdNgrams
import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextWordModelTest {
    private val words = listOf(
        MkdWord("the", 230), MkdWord("of", 225), MkdWord("and", 224), MkdWord("I", 220), MkdWord("good", 200),
        MkdWord("very", 190), MkdWord("morning", 170), MkdWord("luck", 160), MkdWord("idea", 150), MkdWord("am", 180),
        MkdWord("think", 175), MkdWord("will", 185), MkdWord("Will", 120), MkdWord("damn", 150, offensive = true),
        MkdWord("you", 215), MkdWord("see", 195),
    )
    private val meta = mapOf("language" to "en-US")
    private val plain = MkdWriter.write(words, meta)
    private val ids = MappedDictionary(ByteArrayRegion(plain)).let { lexicon -> words.associate { it.text to lexicon.wordId(it.text) } }

    private fun id(word: String) = ids.getValue(word)

    private val table = MkdNgramTable().apply {
        unigram(id("the"), 0.05)
        unigram(id("of"), 0.03)
        unigram(id("and"), 0.02)
        unigram(id("damn"), 0.9)
        bigram(id("good"), id("morning"), 0.3)
        bigram(id("good"), id("luck"), 0.2)
        bigram(id("good"), id("the"), 0.01)
        bigram(id("good"), id("damn"), 0.5)
        trigram(id("very"), id("good"), id("idea"), 0.5)
        trigram(id("very"), id("good"), id("luck"), 0.01)
        bigram(MkdFormat.SENTENCE_START, id("I"), 0.2)
        bigram(MkdFormat.SENTENCE_START, id("the"), 0.1)
        trigram(MkdFormat.SENTENCE_START, id("I"), id("think"), 0.3)
        trigram(MkdFormat.SENTENCE_START, id("I"), id("am"), 0.2)
        bigram(id("will"), id("you"), 0.4)
        bigram(id("Will"), id("see"), 0.4)
    }
    private val bytes = MkdWriter.write(words, meta, table)
    private val dictionary = MappedDictionary(ByteArrayRegion(bytes))
    private val model = assertNotNull(dictionary.nextWords)

    private fun sectionOffset(pack: ByteArray, name: String): Int {
        val region = ByteArrayRegion(pack)
        repeat(region.u16(12)) { i ->
            val entry = MkdFormat.HEADER_SIZE + i * MkdFormat.SECTION_ENTRY_SIZE
            if (region.i32(entry) == MkdFormat.sectionId(name)) return region.i32(entry + 4)
        }
        error("no $name section")
    }

    private fun predict(vararg previous: String, fromSentenceStart: Boolean = false, limit: Int = 10) =
        model.predict(previous.toList(), fromSentenceStart, limit).map { it.word }

    @Test
    fun theCodecRoundTripsEveryListAcrossAnchors() {
        // 100 contexts cross three anchor groups; odd ones have no list.
        val many = List(300) { MkdWord("w$it", 255 - it % 256) }
        val lexicon = MappedDictionary(ByteArrayRegion(MkdWriter.write(many, meta)))
        val wordIds = many.map { lexicon.wordId(it.text) }
        val expected = HashMap<Long, Map<Int, Double>>()
        val input = MkdNgramTable()
        for (context in 0 until 100 step 2) {
            val successors = (1..(context % 7 + 1)).associate { wordIds[(context * 13 + it * 31) % 300] to 1.0 / (it + 1) }
            successors.forEach { (word, p) -> input.bigram(wordIds[context], word, p) }
            expected[wordIds[context].toLong()] = successors
        }
        input.trigram(wordIds[3], wordIds[4], wordIds[299], 0.5)
        input.trigram(MkdFormat.SENTENCE_START, wordIds[4], wordIds[0], 0.25)
        input.unigram(wordIds[0], 0.1)
        input.bigram(MkdFormat.SENTENCE_START, wordIds[7], 1.0)

        val pack = MkdPack.open(ByteArrayRegion(MkdWriter.write(many, meta, input)))
        assertTrue(pack.checksumMatches())
        assertEquals(input.bigramCount.toString(), pack.meta["bigrams"])
        val ngrams = assertNotNull(MkdNgrams.open(pack))
        fun decode(list: Long): Map<Int, Int> = buildMap { ngrams.forEachSuccessor(list) { id, score -> put(id, score) } }
        fun scores(successors: Map<Int, Double>) = successors.mapValues { (-10 * log2(it.value)).roundToInt() }
        for (context in 0 until 300) {
            val wanted = expected[wordIds[context].toLong()]
            assertEquals(wanted?.let(::scores) ?: emptyMap(), decode(ngrams.bigrams(wordIds[context])), "context $context")
        }
        assertEquals(mapOf(wordIds[299] to 10), decode(ngrams.trigrams(wordIds[3], wordIds[4])))
        assertEquals(mapOf(wordIds[0] to 20), decode(ngrams.trigrams(MkdFormat.SENTENCE_START, wordIds[4])))
        assertEquals(emptyMap(), decode(ngrams.trigrams(wordIds[4], wordIds[3])))
        assertEquals(mapOf(wordIds[0] to 33), decode(ngrams.unigrams()))
        assertEquals(mapOf(wordIds[7] to 0), decode(ngrams.sentenceStarts()))
    }

    @Test
    fun theLexiconSectionsDoNotChangeSoOlderReadersStillWork() {
        val withNgrams = MkdPack.open(ByteArrayRegion(bytes))
        val without = MkdPack.open(ByteArrayRegion(plain))
        for (name in listOf("LEXI", "WORD")) {
            val size = without.sectionSizes.getValue(name)
            assertEquals(size, withNgrams.sectionSizes.getValue(name))
            assertContentEquals(
                plain.copyOfRange(sectionOffset(plain, name), sectionOffset(plain, name) + size),
                bytes.copyOfRange(sectionOffset(bytes, name), sectionOffset(bytes, name) + size),
                name,
            )
        }
        assertTrue("NGRM" in withNgrams.sectionSizes)
        words.forEach { assertEquals(MappedDictionary(without).lookup(it.text), dictionary.lookup(it.text)) }
        assertNull(MappedDictionary(without).nextWords, "a pack without NGRM predicts nothing")
    }

    @Test
    fun anUnknownNgramLayoutIsSkippedLikeAnUnknownSection() {
        val changed = bytes.copyOf()
        changed[sectionOffset(changed, "NGRM")] = 2
        val reopened = MappedDictionary(ByteArrayRegion(changed))
        assertNull(reopened.nextWords)
        assertEquals("good", reopened.lookup("good")?.word)
    }

    @Test
    fun trigramsThenBigramsThenUnigrams() {
        // Score bytes, lower is better. idea: trigram 0.5 (10); morning: bigram 0.3 (17 + 13);
        // luck: the trigram's 0.01 (66) although its bigram says 0.2, as stupid backoff keeps the
        // highest order that lists a word; so "the" keeps its bigram 0.01 (66 + 13) behind the
        // unigram "of" (51 + 26); "and" is a unigram (56 + 26).
        assertEquals(listOf("idea", "morning", "luck", "of", "the", "and"), predict("very", "good"))
        // One word of context: its bigrams, then the unigrams one order down.
        assertEquals(listOf("morning", "luck", "of", "the", "and"), predict("good"))
        assertEquals(predict("good"), predict("zzz", "good"), "an unknown word adds nothing")
        assertEquals(listOf("the", "of", "and"), predict("zzz"), "nothing known: the commonest words")
        assertEquals(listOf("idea", "morning"), predict("very", "good", limit = 2))
    }

    @Test
    fun sentenceStartsHaveTheirOwnContext() {
        assertEquals(listOf("I", "the", "of", "and"), predict(fromSentenceStart = true))
        assertEquals(listOf("the", "of", "and"), predict(fromSentenceStart = false), "mid-sentence with no words: unigrams")
        assertEquals(listOf("think", "am"), predict("I", fromSentenceStart = true, limit = 2))
        assertEquals(listOf("the", "of"), predict("I", fromSentenceStart = false, limit = 2), "no sentence start, no trigram")
    }

    @Test
    fun aSentenceStartCapitalIsNotAName() {
        assertEquals("you", predict("Will", fromSentenceStart = true).first(), "\"Will you\" starts a question")
        assertEquals("see", predict("Will", fromSentenceStart = false).first(), "mid-sentence, Will is a name")
        assertEquals(id("will"), dictionary.wordId("Will", sentenceInitial = true))
        assertEquals(id("Will"), dictionary.wordId("Will"))
        assertEquals(id("I"), dictionary.wordId("I", sentenceInitial = true))
    }

    @Test
    fun offensiveWordsAreNeverPredictedUnlessAskedFor() {
        assertTrue("damn" !in predict("good"))
        assertTrue("damn" !in predict("zzz"))
        val optedIn = assertNotNull(MappedDictionary(ByteArrayRegion(bytes), suggestOffensive = true).nextWords)
        assertEquals("damn", optedIn.predict(listOf("good"), false, 3).first().word)
    }

    @Test
    fun scoresAreLog2OfTheBackoffScore() {
        val best = model.predict(listOf("very", "good"), false, 1).single()
        assertEquals(NextWord("idea", 150, -1.0f), best)
        assertContentEquals(listOf(-1.0f, -3.0f), model.predict(listOf("very", "good"), false, 2).map { it.score })
    }
}
