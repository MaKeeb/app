package com.makeeb.engine.dictionary

import com.makeeb.engine.dictionary.pack.Crc32

/** What the learned-words file holds: the words, the dictionary's clock and the last clear request applied. */
class LearnedWordsSnapshot(
    val words: List<LearnedWord>,
    val clock: Long,
    /** See [LearnedWordsStore.applyClearRequest]. */
    val clearRequest: Long,
)

/**
 * The learned-words file, written whole and atomically on every save. It is small (a few bytes a
 * word), so there is no reason to update it in place.
 *
 * ```
 * "MKLW"   magic
 * u8       version (1)
 * varint   clock
 * varint   the last clear request applied
 * varint   word count
 * per word:
 *   varint   UTF-8 length, then the bytes
 *   varint   count
 *   varint   last used
 * u32      CRC-32 of everything before it, little-endian
 * ```
 *
 * Varints are unsigned LEB128. The checksum catches a damaged file, which then reads as
 * [Contents.Damaged] instead of as a wrong word list.
 */
internal object LearnedWordsFile {
    sealed interface Contents {
        class Words(val snapshot: LearnedWordsSnapshot) : Contents

        /** Unreadable: bad magic, checksum or structure. Nothing in it can be trusted. */
        data object Damaged : Contents

        /** Written by a later MaKeeb in a format this one doesn't know; it must be left alone. */
        data object Newer : Contents
    }

    fun encode(snapshot: LearnedWordsSnapshot): ByteArray {
        val out = Output()
        MAGIC.forEach { out.byte(it.code) }
        out.byte(VERSION)
        out.varint(snapshot.clock)
        out.varint(snapshot.clearRequest)
        val words = snapshot.words.map { it to it.word.encodeToByteArray() }.filter { it.second.size in 1..MAX_WORD_BYTES }
        out.varint(words.size.toLong())
        words.forEach { (word, bytes) ->
            out.varint(bytes.size.toLong())
            out.bytes(bytes)
            out.varint(word.count.toLong())
            out.varint(word.lastUsed)
        }
        val crc = Crc32.of(out.buffer, 0, out.size)
        repeat(4) { out.byte((crc ushr (8 * it)) and 0xFF) }
        return out.buffer.copyOf(out.size)
    }

    fun decode(bytes: ByteArray): Contents {
        if (bytes.size < HEADER_SIZE + CRC_SIZE || MAGIC.indices.any { bytes[it].toInt() != MAGIC[it].code }) return Contents.Damaged
        val end = bytes.size - CRC_SIZE
        val storedCrc = (0 until CRC_SIZE).fold(0) { crc, i -> crc or ((bytes[end + i].toInt() and 0xFF) shl (8 * i)) }
        if (storedCrc != Crc32.of(bytes, 0, end)) return Contents.Damaged
        val version = bytes[MAGIC.length].toInt() and 0xFF
        if (version > VERSION) return Contents.Newer
        if (version != VERSION) return Contents.Damaged
        return try {
            val input = Input(bytes, HEADER_SIZE, end)
            val clock = input.varint()
            val clearRequest = input.varint()
            val count = input.varint()
            // Every word takes at least four bytes, so a count beyond that is damage, not a list.
            if (count > (end - HEADER_SIZE) / 4) return Contents.Damaged
            val words = List(count.toInt()) {
                val length = input.varint().toInt()
                val word = input.bytes(length).decodeToString(throwOnInvalidSequence = true)
                LearnedWord(word, input.varint().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), input.varint())
            }
            if (!input.atEnd) return Contents.Damaged
            Contents.Words(LearnedWordsSnapshot(words, clock, clearRequest))
        } catch (_: IllegalArgumentException) {
            Contents.Damaged
        } catch (_: CharacterCodingException) {
            Contents.Damaged
        }
    }

    private class Output {
        var buffer = ByteArray(256)
        var size = 0

        fun byte(value: Int) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = value.toByte()
        }

        fun bytes(value: ByteArray) = value.forEach { byte(it.toInt()) }

        fun varint(value: Long) {
            require(value >= 0)
            var rest = value
            while (rest >= 0x80) {
                byte(((rest and 0x7F) or 0x80).toInt())
                rest = rest ushr 7
            }
            byte(rest.toInt())
        }
    }

    /** Reads [bytes] from [position] to [end]; anything out of bounds or oversized throws IllegalArgumentException. */
    private class Input(private val bytes: ByteArray, private var position: Int, private val end: Int) {
        val atEnd: Boolean get() = position == end

        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (true) {
                require(position < end && shift < 63) { "bad varint" }
                val byte = bytes[position++].toInt() and 0xFF
                value = value or ((byte and 0x7F).toLong() shl shift)
                if (byte < 0x80) return value
                shift += 7
            }
        }

        fun bytes(length: Int): ByteArray {
            require(length in 1..MAX_WORD_BYTES && position + length <= end) { "bad length" }
            return bytes.copyOfRange(position, position + length).also { position += length }
        }
    }

    private const val MAGIC = "MKLW"
    private const val VERSION = 1
    private const val HEADER_SIZE = 5
    private const val CRC_SIZE = 4

    /** Far longer than any word; a longer length is damage. */
    private const val MAX_WORD_BYTES = 256
}
