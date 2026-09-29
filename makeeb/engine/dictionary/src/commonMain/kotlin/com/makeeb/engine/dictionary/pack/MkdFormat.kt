package com.makeeb.engine.dictionary.pack

import com.makeeb.engine.dictionary.KeyFold

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
 *               6  u16  minor version (1): additive changes only, readers accept higher
 *               8  u32  CRC-32 (IEEE) of bytes 12..end
 *              12  u16  section count n
 *              14  u16  reserved, 0
 *              16  n × { u32 id (four ASCII chars), u32 offset, u32 length }
 *
 * META  UTF-8 "key=value" lines. Required: language (BCP 47), keyFold (how keys are folded:
 *       "fold-v2", or "lowercase" in the first packs). Also: name, source, sourceSha256, licence,
 *       attribution, words; with NGRM: ngramSource, ngramSha256, ngramLicence, ngramAttribution,
 *       bigrams, trigrams.
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
 *
 * NGRM  next-word statistics (optional, minor version 1). Offsets are relative to the section.
 *        0  u8   layout (1)
 *        1  u8   score scale S: a score byte q means a probability of 2^(-q / S)
 *        2  u8   backoff B: each order a prediction backs off costs B more (stupid backoff)
 *        3  u8   reserved, 0
 *        4  u32  C, bigram contexts: word ids 0..C-1 have a slot, later ids have no list
 *        8  u32  T, trigram contexts
 *       12  u32  unigram list offset       16  u32  its length in bytes
 *       20  u32  sentence-start list       24  u32  its length
 *       28  u32  bigram lengths: C × u8, the byte length of each context's list
 *       32  u32  bigram anchors: ⌈C / 32⌉ × u32, where the lists of contexts 32k.. start
 *                (relative to the bigram data)
 *       36  u32  bigram data
 *       40  u32  trigram keys: T × {u24 first, u24 second}, sorted; first 0xFFFFFF is the
 *                sentence start
 *       44  u32  trigram lengths: T × u8
 *       48  u32  trigram anchors: ⌈T / 32⌉ × u32, relative to the trigram data
 *       52  u32  trigram data
 *       A list is its successors in ascending id order, each a LEB128 varint gap (id minus the
 *       previous id minus 1; the first is the id itself) and a u8 score. Ids run by frequency,
 *       so the gaps are small. Context c's list starts at anchor[c / 32] plus the lengths of
 *       contexts 32 × (c / 32) until c, so nothing is decoded until a query needs it.
 * ```
 *
 * Sections not listed here are skipped, so later minor versions can add FOLD (a case and
 * diacritic folding table) or SHRT (shortcuts) without breaking readers; NGRM came that way in
 * minor version 1. Several words under one key are how a folded key keeps every surface form
 * ("us" and "US"; "naive" and "naïve").
 */
object MkdFormat {
    const val MAGIC = 0x1A444B4D
    const val MAJOR_VERSION = 1

    /** 1 since packs may carry NGRM (2026-09-29). Readers of 1.0 skip the section. */
    const val MINOR_VERSION = 1
    const val HEADER_SIZE = 16
    const val SECTION_ENTRY_SIZE = 12
    const val CRC_START = 12

    val META = sectionId("META")
    val WORD = sectionId("WORD")
    val LEXI = sectionId("LEXI")
    val NGRM = sectionId("NGRM")

    const val NGRAM_LAYOUT = 1
    const val NGRAM_HEADER_SIZE = 56

    /** Score bytes are tenths of a bit: q = round(-10 × log2 p), so 0..255 covers p down to 2^-25.5. */
    const val NGRAM_SCORE_SCALE = 10

    /** Stupid backoff's 0.4 (Brants et al. 2007) in score units: 10 × log2(1 / 0.4) ≈ 13. */
    const val NGRAM_BACKOFF = 13

    /** Contexts per anchor in the bigram and trigram offset tables. */
    const val NGRAM_ANCHOR_STRIDE = 32

    /** The context id of a sentence start in trigram keys. */
    const val SENTENCE_START = 0xFFFFFF

    /** A list's length is a u8, so a list holds at most this many bytes. */
    const val MAX_NGRAM_LIST_BYTES = 255

    const val WORD_OFFENSIVE = 0x01
    const val WORD_CASED = 0x02

    const val NODE_TERMINAL = 0x80
    const val NODE_MULTI_CHAR = 0x40
    const val NODE_VARIANTS = 0x20
    const val NODE_CHILD_WIDTH = 0x03

    const val LABEL_END = 0x1F

    /** The first packs' key fold: Unicode lower case, locale-independent. Still readable. */
    const val FOLD_LOWERCASE = "lowercase"

    /** Lower case without diacritics or apostrophes ([KeyFold]); what the writer uses now. */
    const val FOLD_V2 = KeyFold.SCHEME

    const val MAX_WORD_BYTES = 255
    const val MAX_U24 = 0xFFFFFF

    /** Fold [word] the way a pack declaring [scheme] folded its keys. */
    fun fold(word: String, scheme: String = FOLD_V2): String =
        if (scheme == FOLD_LOWERCASE) word.lowercase() else KeyFold.fold(word)

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
