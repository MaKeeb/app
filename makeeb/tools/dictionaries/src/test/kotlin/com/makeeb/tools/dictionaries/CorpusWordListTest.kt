package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.pack.MkdWord
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorpusWordListTest {
    private val recipe = CorpusWordRecipe(
        languageTag = "hu",
        alphabet = "aábcdeéfghiíjklmnoóöőpqrstuúüűvwxyz",
        minCount = 2,
        offensive = OffensiveWords(exact = setOf("szar"), prefixes = listOf("kurv")),
    )

    @Test
    fun keepsTheLanguagesWordsWithTheirCaseAndCounts() {
        val corpus = corpusFile(
            listOf(
                "1\tA ház Budapesten áll, a ház szép.",
                "2\tBudapesten a ház 2024-ben épült, és szép.",
                "3\tA Ház szép, mondta Kurvinen a Budapesten.",
                "4\tEz szar, és a kurva ház az õ háza.",
                "5\tEz szar, és a kurva ház nem épült.",
                // Held out: never counted, like the n-grams' held-out sentences.
                "250\tzebra zebra zebra zebra",
            ),
        )
        val list = CorpusWordList.count(listOf(corpus), recipe, heldOut = { it % 250 == 0L })
        val words = list.words.associateBy { it.text.lowercase() }

        assertEquals(5L, list.sentences)
        assertTrue("zebra" !in words, "held-out sentences aren't counted")
        // "Ház" once inside a sentence against "ház" five times: an ordinary word.
        assertEquals("ház", words.getValue("ház").text)
        // Capitalised every time inside a sentence: a name.
        assertEquals("Budapesten", words.getValue("budapesten").text)
        // Digits and a mis-encoded ő ("õ") aren't Hungarian words; seen once is too rare.
        assertTrue(words.keys.none { it.any(Char::isDigit) || 'õ' in it })
        assertTrue("mondta" !in words && "kurvinen" !in words, "seen once")
        // Sentence-initial capitals don't make a name: "Ez" is "ez".
        assertEquals("ez", words.getValue("ez").text)
        assertTrue(words.getValue("szar").offensive)
        assertTrue(words.getValue("kurva").offensive)
        assertFalse(words.getValue("ház").offensive)

        // The commonest word comes first and gets the top frequency; equal counts, equal frequencies.
        assertEquals("a", list.words.first().text)
        assertEquals(RankFrequency.of(1), list.words.first().frequency)
        assertEquals(words.getValue("szar").frequency, words.getValue("épült").frequency, "both seen twice")
        assertTrue(list.words.zipWithNext().all { (a, b) -> a.frequency >= b.frequency })
    }

    @Test
    fun keepsAtMostTheCap() {
        val corpus = corpusFile(listOf("1\ta a a b b c", "2\ta b c d d e e"))
        val list = CorpusWordList.count(listOf(corpus), recipe.copy(minCount = 1, maxWords = 3), heldOut = { false })
        assertEquals(listOf("a", "b", "c"), list.words.map { it.text })
    }

    @Test
    fun theCacheRoundTrips() {
        val list = CorpusWordList(listOf(MkdWord("ház", 200), MkdWord("Budapest", 150), MkdWord("szar", 90, offensive = true)), 10, 100, 90, 12)
        val file = File.createTempFile("words", ".tsv").apply { deleteOnExit() }
        list.write(file)
        val read = CorpusWordList.read(file)!!
        assertEquals(list.words, read.words)
        assertEquals(listOf(10L, 100L, 90L), listOf(read.sentences, read.tokens, read.accepted))
        assertEquals(12, read.distinct)
    }

    @Test
    fun offensivePrefixesSpareNamesAndSpeltOutWords() {
        val words = HungarianOffensiveWords
        assertTrue(words.matches("kurva") && words.matches("baszd") && words.matches("szar"))
        assertFalse(words.matches("Kurvinen"), "a name")
        assertFalse(words.matches("baszk"), "Basque")
        assertFalse(words.matches("szarvas"), "deer")
        assertFalse(words.matches("faszerkezet"), "a wooden frame")
        assertFalse(words.matches("basszus"), "bass")
    }

    @Test
    fun frequenciesFollowTheAospCurve() {
        assertEquals(221, RankFrequency.of(1))
        assertEquals(138, RankFrequency.of(1_000))
        assertEquals(106, RankFrequency.of(10_000))
        assertEquals(1, RankFrequency.of(5_000_000))
        assertTrue((1..300_000 step 997).map(RankFrequency::of).zipWithNext().all { (a, b) -> a >= b })
    }
}
