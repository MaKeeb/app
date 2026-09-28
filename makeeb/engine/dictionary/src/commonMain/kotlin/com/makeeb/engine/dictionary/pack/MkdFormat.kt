package com.makeeb.engine.dictionary.pack

/**
 * MKD v1, MaKeeb's dictionary pack format. [MkdWriter] writes it and [MkdPack] reads it straight
 * from a mapped [com.makeeb.platform.storage.ByteRegion]; nothing is decoded up front, so a pack
 * costs a few objects of heap however many words it holds. docs/dictionaries/mkd-format.md has
 * the same layout with the reasoning; docs/research/dictionaries-autocorrect.md §5 and §10.2 the
 * background.
 *
 * All integers are little-endian unless stated. Offsets are in bytes.
 *
 * ```
 * header        0  u32  magic "MKD" 0x1A
 *               4  u16  major version (1): readers reject any other
 *               6  u16  minor version (0): additive changes only, readers accept higher
 *               8  u32  CRC-32 (IEEE) of bytes 12..end
 *              12  u16  section count n
 *              14  u16  reserved, 0
 *              16  n × { u32 id (four ASCII chars), u32 offset, u32 length }
 *
 * META  UTF-8 "key=value" lines. Required: language (BCP 47), keyFold (how keys are folded; v1:
 *       "lowercase"). Also: name, source, sourceSha256, licence, attribution, words.
 *
 * WORD  word table
 *       u32 count
 *       count × u24 record offset, relative to the section
 *       records: u8 frequency (0..255, log scale), u8 flags, u8 byte length n, n bytes UTF-8
 *       Ids run in descending frequency, so ids 0..k-1 are the k most frequent words.
 *       Flags: 0x01 offensive (never suggested unless the user opts in),
 *              0x02 cased (canonical form isn't all lower case: names, acronyms, "I"),
 *              0x04..0x80 reserved, 0.
 *
 * LEXI  radix trie over folded keys, breadth-first so the hot top levels share a few pages.
 *       Node array: count (1 byte if < 0x80, else 2 bytes: 0x80 | high 7 bits, then low 8),
 *       then its nodes back to back, sorted by best score, highest first.
 *       The root array starts at offset 0 of the section.
 *       Node:
 *         u8  flags   0x80 terminal, 0x40 multi-character label, 0x20 several words,
 *                     0x03 child offset width in bytes (0 = no children)
 *         u8  best    highest frequency of any word at or below this node
 *         label       code points: 0x20..0xFF as one byte; others as three bytes, big-endian,
 *                     whose first byte is < 0x20. A multi-character label ends with 0x1F.
 *         terminal    u8 frequency (highest of its words), then a u24 word id, or with
 *                     "several words" a u8 count and that many u24 ids, most frequent first
 *         children    1–3 byte offset from the node's first byte to its child array
 * ```
 *
 * Sections not listed here are skipped, so later minor versions can add FOLD (a case and
 * diacritic folding table), NGRM (next-word data) or SHRT (shortcuts) without breaking readers.
 * Several words under one key are how a folded key keeps every surface form ("us" and "US"
 * today; "naive" and "naïve" once keys fold diacritics too).
 */
object MkdFormat {
    const val MAGIC = 0x1A444B4D
    const val MAJOR_VERSION = 1
    const val MINOR_VERSION = 0
    const val HEADER_SIZE = 16
    const val SECTION_ENTRY_SIZE = 12
    const val CRC_START = 12

    val META = sectionId("META")
    val WORD = sectionId("WORD")
    val LEXI = sectionId("LEXI")

    const val WORD_OFFENSIVE = 0x01
    const val WORD_CASED = 0x02

    const val NODE_TERMINAL = 0x80
    const val NODE_MULTI_CHAR = 0x40
    const val NODE_VARIANTS = 0x20
    const val NODE_CHILD_WIDTH = 0x03

    const val LABEL_END = 0x1F

    /** The v1 key fold: Unicode lower case, locale-independent. Stage 2 adds diacritic folding (FOLD). */
    const val FOLD_LOWERCASE = "lowercase"

    const val MAX_WORD_BYTES = 255
    const val MAX_U24 = 0xFFFFFF

    fun fold(word: String): String = word.lowercase()

    fun sectionId(name: String): Int {
        require(name.length == 4 && name.all { it.code in 0x20..0x7E }) { "section ids are four ASCII characters" }
        return name[0].code or (name[1].code shl 8) or (name[2].code shl 16) or (name[3].code shl 24)
    }
}

/** A pack that is truncated, corrupt, or from an unsupported major version. */
class MkdFormatException(message: String) : RuntimeException(message)

/** The code points of [text]; surrogate pairs become one code point. */
internal fun codePoints(text: String): IntArray {
    val out = IntArray(text.length)
    var count = 0
    var i = 0
    while (i < text.length) {
        val high = text[i]
        if (high.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            out[count++] = ((high.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000
            i += 2
        } else {
            out[count++] = high.code
            i++
        }
    }
    return if (count == out.size) out else out.copyOf(count)
}
