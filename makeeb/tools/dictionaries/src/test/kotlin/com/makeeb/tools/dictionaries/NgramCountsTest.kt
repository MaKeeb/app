package com.makeeb.tools.dictionaries

import com.makeeb.core.common.TextBoundaries
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.pack.MkdNgrams
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NgramCountsTest {
    @Test
    fun theCorpusTokeniserSeesTheContextsTheKeyboardSees() {
        val sentences = listOf(
            "Hi, how are you? I'm fine. \"Stop.\" Then we left.",
            "It costs 3.5 dollars — or 'so' they said: (maybe) not.",
            "Mr. Smith went to Washington... and stayed!",
            "A well-known author's work, e.g. this one; don't worry",
        )
        for (sentence in sentences) {
            val words = CorpusTokens.words(sentence)
            words.forEachIndexed { i, word ->
                val context = TextBoundaries.wordsBefore(sentence.substring(0, word.start), 2)
                val sentenceStart = words.subList(0, i + 1).indexOfLast { it.sentenceInitial }
                val expected = words.subList(maxOf(sentenceStart, i - 2), i).map { it.text }
                assertEquals(expected, context.words, "before '${word.text}' in: $sentence")
                assertEquals(i - sentenceStart <= 2, context.fromSentenceStart, "sentence start before '${word.text}' in: $sentence")
            }
        }
        assertEquals(
            listOf("Hi", "how", "are", "you", "I'm", "fine", "Stop", "Then", "we", "left"),
            CorpusTokens.words(sentences[0]).map { it.text },
        )
        assertEquals(listOf(true, false, false, false, true, false, true, true, false, false), CorpusTokens.words(sentences[0]).map { it.sentenceInitial })
    }

    @Test
    fun readsTarEntriesAndSkipsWhatIsNotRead() {
        val tar = tar(listOf("corpus/a.txt" to "first".encodeToByteArray(), "corpus/b-sentences.txt" to "1\tHello there.\n".encodeToByteArray()))
        val seen = mutableListOf<String>()
        forEachTarEntry(ByteArrayInputStream(tar)) { path, content ->
            seen += path
            if (path.endsWith("-sentences.txt")) assertEquals("1\tHello there.\n", content.readBytes().decodeToString())
        }
        assertEquals(listOf("corpus/a.txt", "corpus/b-sentences.txt"), seen)
    }

    @Test
    fun countsRespectSentencesUnknownWordsAndTheHeldOutSentences() {
        val words = listOf("the", "cat", "sat", "on", "mat", "a", "dog", "damn").mapIndexed { i, w -> MkdWord(w, 200 - i, offensive = w == "damn") }
        val lexicon = MappedDictionary(ByteArrayRegion(MkdWriter.write(words, mapOf("language" to "en-US"))))
        fun id(word: String) = lexicon.wordId(word)
        val corpus = File.createTempFile("corpus", ".tar.gz").apply { deleteOnExit() }
        val lines = listOf(
            "1\tThe cat sat on the mat.",
            "2\tThe cat sat. The dog sat on a mat.",
            "3\tThe cat sat on Zorro the mat.",
            "4\tthe cat damn the cat damn the cat damn",
            "5\tThe cat sat on the mat.",
        )
        corpus.writeBytes(gzip(tar(listOf("x/x-sentences.txt" to lines.joinToString("\n", postfix = "\n").encodeToByteArray()))))
        val heldOut = mutableListOf<String>()
        val counts = NgramCounts.count(listOf(corpus), lexicon, heldOut = { it == 5L }) { heldOut += it }
        assertEquals(listOf("The cat sat on the mat."), heldOut)
        assertEquals(4L, counts.sentences)
        assertEquals(1L, counts.tokens - counts.knownTokens, "Zorro")

        val table = counts.prune(NgramPruning(minBigramCount = 2, minTrigramCount = 2, minTrigramContext = 2, dropRedundantTrigrams = false)) { it == id("damn") }
        val dictionary = MappedDictionary(ByteArrayRegion(MkdWriter.write(words, mapOf("language" to "en-US"), table)))
        val model = dictionary.nextWords!!
        assertEquals("the", model.predict(emptyList(), true, 1).single().word, "sentences start with The, counted as the")
        assertEquals("sat", model.predict(listOf("the", "cat"), false, 1).single().word)
        assertTrue(model.predict(listOf("the", "cat"), false, 10).none { it.word == "damn" }, "offensive successors are left out")
        // "on Zorro the": no n-gram spans the unknown word, so "on the" is seen once, below the cut.
        val ngrams = MkdNgrams.open(dictionary.pack)!!
        assertEquals(MkdNgrams.EMPTY, ngrams.bigrams(id("on")))
        assertTrue(ngrams.bigrams(id("the")) != MkdNgrams.EMPTY)
    }
}
