package com.makeeb.engine.dictionary.pack

import com.makeeb.platform.storage.ByteRegion

/**
 * An open MKD pack ([MkdFormat]): the header and section table, checked once, and accessors that
 * read the word table in place. Opening reads a few dozen bytes; the lexicon and word table stay
 * in the region until a query touches them.
 */
class MkdPack private constructor(
    val region: ByteRegion,
    val meta: Map<String, String>,
    /** Section id ("LEXI") → length in bytes, for size reports. */
    val sectionSizes: Map<String, Int>,
    /** Absolute offset of the LEXI section (its root node array). */
    internal val lexicon: Int,
    internal val lexiconEnd: Int,
    private val words: Int,
    val wordCount: Int,
) {
    val languageTag: String get() = meta.getValue("language")

    /** How this pack folded its keys; queries fold the same way ([MkdFormat.fold]). */
    val keyFold: String get() = meta.getValue("keyFold")

    fun wordFrequency(id: Int): Int = region.u8(recordAt(id))

    fun wordFlags(id: Int): Int = region.u8(recordAt(id) + 1)

    fun isOffensive(id: Int): Boolean = wordFlags(id) and MkdFormat.WORD_OFFENSIVE != 0

    /** The canonical spelling of word [id]. Allocates: call it for results, not while searching. */
    fun wordText(id: Int): String {
        val at = recordAt(id)
        val length = region.u8(at + 2)
        val bytes = ByteArray(length)
        region.copyInto(at + 3, bytes, 0, length)
        return bytes.decodeToString()
    }

    /** True if word [id]'s spelling is exactly [text], without building a String. */
    internal fun wordEquals(id: Int, text: ByteArray): Boolean {
        val at = recordAt(id)
        if (region.u8(at + 2) != text.size) return false
        for (i in text.indices) if (region.u8(at + 3 + i) != (text[i].toInt() and 0xFF)) return false
        return true
    }

    private fun recordAt(id: Int): Int {
        if (id < 0 || id >= wordCount) throw IndexOutOfBoundsException("word $id of $wordCount")
        return words + region.u24(words + 4 + id * 3)
    }

    /** Recomputes the CRC over the whole pack. Reads every page: do it on install, not on every start. */
    fun checksumMatches(): Boolean =
        Crc32.of(region, MkdFormat.CRC_START, region.size) == region.i32(8)

    companion object {
        /** Reads the header and section table. Throws [MkdFormatException] for anything unusable. */
        fun open(region: ByteRegion): MkdPack {
            fun fail(message: String): Nothing = throw MkdFormatException(message)
            if (region.size < MkdFormat.HEADER_SIZE) fail("too short for a header")
            if (region.i32(0) != MkdFormat.MAGIC) fail("not an MKD pack")
            val major = region.u16(4)
            if (major != MkdFormat.MAJOR_VERSION) fail("unsupported major version $major")
            val sectionCount = region.u16(12)
            if (MkdFormat.HEADER_SIZE + sectionCount * MkdFormat.SECTION_ENTRY_SIZE > region.size) fail("truncated section table")

            val offsets = HashMap<Int, Int>()
            val lengths = HashMap<Int, Int>()
            repeat(sectionCount) { i ->
                val entry = MkdFormat.HEADER_SIZE + i * MkdFormat.SECTION_ENTRY_SIZE
                val offset = region.i32(entry + 4)
                val length = region.i32(entry + 8)
                if (offset < 0 || length < 0 || offset.toLong() + length > region.size) fail("section $i out of bounds")
                offsets[region.i32(entry)] = offset
                lengths[region.i32(entry)] = length
            }
            fun section(id: Int, name: String): Int = offsets[id] ?: fail("no $name section")
            val metaAt = section(MkdFormat.META, "META")
            val lexicon = section(MkdFormat.LEXI, "LEXI")
            val words = section(MkdFormat.WORD, "WORD")

            val metaBytes = ByteArray(lengths.getValue(MkdFormat.META))
            region.copyInto(metaAt, metaBytes, 0, metaBytes.size)
            val meta = metaBytes.decodeToString().lineSequence().filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            if (meta["language"].isNullOrBlank()) fail("META has no language")
            val fold = meta["keyFold"]
            if (fold != MkdFormat.FOLD_LOWERCASE && fold != MkdFormat.FOLD_V2) fail("unsupported key fold $fold")

            val wordsLength = lengths.getValue(MkdFormat.WORD)
            if (wordsLength < 4) fail("WORD section too short")
            val wordCount = region.i32(words)
            if (wordCount < 0 || 4L + wordCount * 3L > wordsLength) fail("WORD index out of bounds")
            val lexiconLength = lengths.getValue(MkdFormat.LEXI)
            if (lexiconLength == 0) fail("empty LEXI section")

            val sizes = lengths.entries.associate { (id, length) ->
                CharArray(4) { (id shr (8 * it) and 0xFF).toChar() }.concatToString() to length
            }
            return MkdPack(region, meta, sizes, lexicon, lexicon + lexiconLength, words, wordCount)
        }
    }
}
