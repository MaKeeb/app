package com.makeeb.engine.dictionary

import com.makeeb.engine.dictionary.pack.Crc32
import com.makeeb.testing.FakePrivateFiles
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LearnedWordsStoreTest {
    private val files = FakePrivateFiles()

    /** A store as a fresh keyboard process makes it: reading the file off the main thread. */
    private fun TestScope.store(): LearnedWordsStore =
        LearnedWordsStore(UserDictionary("en"), files, backgroundScope, StandardTestDispatcher(testScheduler, "io"))

    private fun TestScope.loadedStore(): LearnedWordsStore = store().also {
        it.load()
        runCurrent()
    }

    /** What a later process finds on disk. */
    private fun TestScope.savedWords(): Set<String> = loadedStore().words().map { it.word }.toSet()

    private fun TestScope.save(vararg words: String) {
        val store = loadedStore()
        words.forEach(store::learn)
        store.flush()
        runCurrent()
    }

    @Test
    fun learnedWordsSurviveANewProcess() = runTest {
        save("zorblax", "Zanele")
        assertEquals(setOf("zorblax", "Zanele"), savedWords())
    }

    @Test
    fun changesAreSavedInOneBatch() = runTest {
        val store = loadedStore()
        listOf("zorblax", "quibbit", "flarn").forEach(store::learn)
        runCurrent()
        assertEquals(0, files.writes, "nothing is written while typing")
        advanceTimeBy(LearnedWordsStore.SAVE_DELAY - 1.milliseconds)
        assertEquals(0, files.writes)
        store.learn("snorp")
        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(1, files.writes, "one write for the whole batch")
        assertEquals(setOf("zorblax", "quibbit", "flarn", "snorp"), savedWords())
    }

    @Test
    fun hidingTheKeyboardSavesAtOnce() = runTest {
        val store = loadedStore()
        store.learn("zorblax")
        store.flush()
        runCurrent()
        assertEquals(1, files.writes)
        store.flush()
        runCurrent()
        assertEquals(1, files.writes, "nothing new, nothing written")
    }

    @Test
    fun beforeTheFirstUnlockLearningStaysInMemoryAndMergesLater() = runTest {
        save("zorblax")
        val saved = files.files.values.single().copyOf()
        files.locked = true
        val store = loadedStore()
        assertFalse(store.isLoaded)
        store.learn("lockscreen")
        assertTrue(store.isLearned("lockscreen"), "usable before the unlock")
        advanceTimeBy(LearnedWordsStore.SAVE_DELAY * 2)
        store.flush()
        runCurrent()
        assertContentEquals(saved, files.files.values.single(), "the saved words are never overwritten while locked")

        files.locked = false
        store.flush()
        runCurrent()
        assertTrue(store.isLoaded)
        assertTrue(store.isLearned("zorblax") && store.isLearned("lockscreen"))
        assertEquals(setOf("zorblax", "lockscreen"), savedWords())
    }

    @Test
    fun forgettingAndClearingBeforeTheLoadStick() = runTest {
        save("zorblax", "quibbit", "flarn")
        files.locked = true
        val forgetting = loadedStore()
        forgetting.forget("Zorblax")
        files.locked = false
        forgetting.flush()
        runCurrent()
        assertEquals(setOf("quibbit", "flarn"), savedWords())

        files.locked = true
        val clearing = loadedStore()
        clearing.clear()
        clearing.learn("snorp")
        files.locked = false
        clearing.load()
        runCurrent()
        assertEquals(setOf("snorp"), clearing.words().map { it.word }.toSet())
        assertEquals(setOf("snorp"), savedWords())
    }

    @Test
    fun aFailedWriteIsRetried() = runTest {
        val store = loadedStore()
        files.failWrites = true
        store.learn("zorblax")
        store.flush()
        runCurrent()
        assertEquals(0, files.writes)
        files.failWrites = false
        store.flush()
        runCurrent()
        assertEquals(setOf("zorblax"), savedWords())
    }

    @Test
    fun aDamagedFileIsReplaced() = runTest {
        files.files["learned-words-en.mklw"] = "garbage".encodeToByteArray()
        val store = loadedStore()
        assertTrue(store.isLoaded)
        store.learn("zorblax")
        store.flush()
        runCurrent()
        assertEquals(setOf("zorblax"), savedWords())
    }

    @Test
    fun aFileFromALaterVersionIsLeftAlone() = runTest {
        save("zorblax")
        val name = files.files.keys.single()
        val later = files.files.getValue(name).also { it[4] = 9 }
        // Re-seal it as that version would have.
        val crc = Crc32.of(later, 0, later.size - 4)
        repeat(4) { later[later.size - 4 + it] = (crc ushr (8 * it)).toByte() }
        files.files[name] = later.copyOf()

        val store = loadedStore()
        store.learn("quibbit")
        assertTrue(store.isLearned("quibbit"), "learning still works, in memory")
        store.flush()
        advanceTimeBy(LearnedWordsStore.SAVE_DELAY * 2)
        runCurrent()
        assertContentEquals(later, files.files.getValue(name))
    }

    @Test
    fun forgettingAWordSavesTheListWithoutIt() = runTest {
        save("zorblax", "quibbit")
        val store = loadedStore()
        store.forget("zorblax")
        store.forget("neverlearned")
        store.flush()
        runCurrent()
        assertEquals(setOf("quibbit"), savedWords())
    }

    @Test
    fun aClearRequestAppliesOnce() = runTest {
        save("zorblax")
        val store = loadedStore()
        store.applyClearRequest(1)
        assertTrue(store.words().isEmpty())
        store.learn("quibbit")
        store.applyClearRequest(1)
        assertTrue(store.isLearned("quibbit"), "an old request doesn't clear again")
        store.flush()
        runCurrent()

        // A new process sees request 1 again before its words are read: it was applied already.
        val next = store()
        next.applyClearRequest(1)
        next.load()
        runCurrent()
        assertEquals(setOf("quibbit"), next.words().map { it.word }.toSet())

        // A newer request, seen before the words are read, drops them.
        val cleared = store()
        cleared.applyClearRequest(2)
        cleared.load()
        runCurrent()
        assertTrue(cleared.words().isEmpty())
        assertEquals(emptySet(), savedWords())
    }
}
