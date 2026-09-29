package com.makeeb.engine.dictionary

import com.makeeb.engine.dictionary.pack.Crc32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class LearnedWordsFileTest {
    private val snapshot = LearnedWordsSnapshot(
        words = listOf(LearnedWord("zorblax", 3, 17), LearnedWord("Zanele", 1, 1), LearnedWord("naïveté", 200, 5_000_000_000)),
        clock = 5_000_000_000,
        clearRequest = 2,
    )

    @Test
    fun roundTrips() {
        val decoded = assertIs<LearnedWordsFile.Contents.Words>(LearnedWordsFile.decode(LearnedWordsFile.encode(snapshot))).snapshot
        assertEquals(snapshot.words, decoded.words)
        assertEquals(snapshot.clock, decoded.clock)
        assertEquals(snapshot.clearRequest, decoded.clearRequest)
    }

    @Test
    fun anEmptyListRoundTrips() {
        val empty = LearnedWordsSnapshot(emptyList(), 0, 0)
        val decoded = assertIs<LearnedWordsFile.Contents.Words>(LearnedWordsFile.decode(LearnedWordsFile.encode(empty))).snapshot
        assertEquals(emptyList(), decoded.words)
    }

    @Test
    fun damageIsDetected() {
        val bytes = LearnedWordsFile.encode(snapshot)
        assertSame(LearnedWordsFile.Contents.Damaged, LearnedWordsFile.decode(bytes.copyOf(bytes.size - 1)), "truncated")
        assertSame(LearnedWordsFile.Contents.Damaged, LearnedWordsFile.decode(bytes.copyOf().also { it[12] = (it[12] + 1).toByte() }), "flipped byte")
        assertSame(LearnedWordsFile.Contents.Damaged, LearnedWordsFile.decode(ByteArray(0)), "empty")
        assertSame(LearnedWordsFile.Contents.Damaged, LearnedWordsFile.decode("not a word list".encodeToByteArray()), "not ours")
    }

    @Test
    fun aLaterFormatIsRecognisedAsSuch() {
        val bytes = LearnedWordsFile.encode(snapshot)
        bytes[4] = 2
        // Re-seal it, as a later version would have.
        val crc = Crc32.of(bytes, 0, bytes.size - 4)
        repeat(4) { bytes[bytes.size - 4 + it] = (crc ushr (8 * it)).toByte() }
        assertSame(LearnedWordsFile.Contents.Newer, LearnedWordsFile.decode(bytes))
    }
}
