package com.makeeb.tools.dictionaries.harness

import com.makeeb.engine.dictionary.StarterDictionaries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keeps the harness itself honest; the numbers that matter come from `typingHarness` on the real pack. */
class TypingHarnessTest {
    private val sentences = TypingHarness.corpus().take(15)

    @Test
    fun theSameSeedGivesTheSameNumbers() {
        val first = TypingHarness(StarterDictionaries.english()).run(sentences)
        val second = TypingHarness(StarterDictionaries.english()).run(sentences)
        assertEquals(first.copy(keyMicros = emptyList(), spaceMicros = emptyList()), second.copy(keyMicros = emptyList(), spaceMicros = emptyList()))
        assertTrue(first.typos > 0, "the noise produced typos")
        assertTrue(first.typedKeys in 1..first.fullKeys)
        assertEquals(first.words, sentences.sumOf { TypingHarness.words(it).size })
    }

    @Test
    fun anotherSeedGivesOtherTypos() {
        val a = TypingHarness(StarterDictionaries.english(), seed = 1).run(sentences)
        val b = TypingHarness(StarterDictionaries.english(), seed = 2).run(sentences)
        assertTrue(a.typos != b.typos || a.typosFixed != b.typosFixed || a.typosInStrip != b.typosInStrip)
    }

    @Test
    fun corpusWordsIncludeContractions() {
        assertEquals(listOf("I", "don't", "know"), TypingHarness.words("I don't know."))
    }
}
