package com.makeeb.engine.dictionary

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDictionaryTest {
    @Test
    fun learnsAndForgetsInAnyCase() {
        val user = UserDictionary("en")
        user.learn("MaKeeb")
        assertEquals("MaKeeb", user.lookup("makeeb")?.word)
        assertTrue(user.isLearned("MAKEEB"))
        user.forget("makeeb")
        assertNull(user.lookup("makeeb"))
        assertEquals(0, user.size)
    }

    @Test
    fun eachUseRaisesTheWord() {
        val user = UserDictionary("en")
        user.learn("zorblax")
        val once = user.lookup("zorblax")!!.frequency
        user.learn("zorblax")
        assertTrue(user.lookup("zorblax")!!.frequency > once)
        assertEquals(1, user.size)
    }

    @Test
    fun aSentenceStartCapitalIsNotANewWordAndLowerCaseWins() {
        val user = UserDictionary("en")
        user.learn("Yeet")
        user.learn("yeet")
        user.learn("YEET")
        assertEquals(listOf(LearnedWord("yeet", 3, 3)), user.words())
    }

    @Test
    fun namesKeepTheirCapitalsAndAccentsMakeAnotherWord() {
        val user = UserDictionary("en")
        user.learn("Zanele")
        user.learn("Zanele")
        user.learn("zelie")
        user.learn("zélie")
        assertEquals("Zanele", user.lookup("zanele")?.word)
        assertEquals(setOf("Zanele", "zelie", "zélie"), user.words().map { it.word }.toSet())
    }

    @Test
    fun ignoresWhatIsNotAWord() {
        val user = UserDictionary("en")
        listOf("", "   ", "two words", "tab\there", "'", "x".repeat(49)).forEach(user::learn)
        assertEquals(0, user.size)
        assertEquals(0L, user.clock)
    }

    @Test
    fun whenFullTheLeastUsefulWordsGo() {
        val user = UserDictionary("en", capacity = 100)
        // Typed often, long ago.
        repeat(20) { user.learn("oldfavourite") }
        // Once each, long ago.
        repeat(100) { user.learn("stale$it") }
        // Once each, recently: they push the dictionary past its capacity.
        repeat(60) { user.learn("fresh$it") }
        assertTrue(user.size <= 100)
        assertTrue(user.isLearned("oldfavourite"), "typed twenty times")
        assertTrue((0 until 60).all { user.isLearned("fresh$it") }, "recent words stay")
        assertFalse(user.isLearned("stale0"), "the oldest one-off words go first")
    }

    @Test
    fun completionsAndCorrectionsMatchATrieOverTheSameWords() {
        val random = Random(42)
        val letters = "abcdeilnorstu"
        val words = List(400) { String(CharArray(3 + random.nextInt(6)) { letters[random.nextInt(letters.length)] }) }.distinct()
        val user = UserDictionary("en")
        words.forEach(user::learn)
        val trie = TrieDictionary("en", user.words().map { WordEntry(it.word, user.lookup(it.word)!!.frequency) })

        fun List<WordMatch>.normalised() = map { it.entry.word to it.edits }.toSet()
        repeat(200) {
            val query = String(CharArray(2 + random.nextInt(7)) { letters[random.nextInt(letters.length)] })
            for (edits in 0..2) {
                assertEquals(trie.corrections(query, edits, 1000).normalised(), user.corrections(query, edits, 1000).normalised(), "$query within $edits")
            }
            val prefix = query.take(2)
            assertEquals(trie.completions(prefix, 1000).map { it.word }.toSet(), user.completions(prefix, 1000).map { it.word }.toSet(), prefix)
        }
    }

    @Test
    fun correctionsCountATranspositionAsOneEdit() {
        val user = UserDictionary("en")
        user.learn("zorblax")
        assertEquals(listOf(WordMatch(user.lookup("zorblax")!!, 1)), user.corrections("zrOblax", maxEdits = 1, limit = 3))
    }

    @Test
    fun restorePutsSavedWordsUnderNewOnes() {
        val user = UserDictionary("en")
        user.learn("fresh")
        user.learn("Shared")
        user.restore(listOf(LearnedWord("saved", 4, 90), LearnedWord("shared", 2, 100)), earlierClock = 100)
        assertEquals(102L, user.clock)
        val words = user.words().associateBy { it.word }
        assertEquals(LearnedWord("saved", 4, 90), words["saved"])
        assertEquals(LearnedWord("fresh", 1, 101), words["fresh"])
        assertEquals(LearnedWord("shared", 3, 102), words["shared"], "counts add up, and lower case wins")
    }
}
