package com.makeeb.engine.dictionary.pack

import com.makeeb.platform.storage.ByteRegion

/**
 * A pack's NGRM section ([MkdFormat]), read in place. Opening checks the header and table
 * bounds; lists are found and decoded only when asked for, straight from the region.
 *
 * A list is handed around as a Long: its absolute offset in the high half and its byte length in
 * the low half ([EMPTY] when there is none), so looking one up allocates nothing.
 */
class MkdNgrams private constructor(
    @PublishedApi internal val region: ByteRegion,
    val scoreScale: Int,
    /** What each order a prediction backs off costs, in score units. */
    val backoff: Int,
    /** Words with ids below this can have a bigram list. */
    val bigramContexts: Int,
    val trigramContexts: Int,
    private val unigrams: Long,
    private val sentenceStarts: Long,
    private val bigramLengths: Int,
    private val bigramAnchors: Int,
    private val bigramData: Int,
    private val trigramKeys: Int,
    private val trigramLengths: Int,
    private val trigramAnchors: Int,
    private val trigramData: Int,
) {
    /** The commonest words, whatever came before. */
    fun unigrams(): Long = unigrams

    /** The words that start sentences. */
    fun sentenceStarts(): Long = sentenceStarts

    /** The words that follow word [context]. */
    fun bigrams(context: Int): Long {
        if (context < 0 || context >= bigramContexts) return EMPTY
        return list(bigramLengths, bigramAnchors, bigramData, context)
    }

    /** The words that follow [first] (a word id or [MkdFormat.SENTENCE_START]) and then [second]. */
    fun trigrams(first: Int, second: Int): Long {
        if (first < 0 || second < 0 || trigramContexts == 0) return EMPTY
        val wanted = (first.toLong() shl 24) or second.toLong()
        var low = 0
        var high = trigramContexts - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val at = trigramKeys + middle * TRIGRAM_KEY_SIZE
            val key = (region.u24(at).toLong() shl 24) or region.u24(at + 3).toLong()
            when {
                key < wanted -> low = middle + 1
                key > wanted -> high = middle - 1
                else -> return list(trigramLengths, trigramAnchors, trigramData, middle)
            }
        }
        return EMPTY
    }

    /** Calls [action] with each successor's word id and score byte, in ascending id order. */
    inline fun forEachSuccessor(list: Long, action: (id: Int, score: Int) -> Unit) {
        var at = (list ushr 32).toInt()
        val end = at + (list and 0xFFFFFFFFL).toInt()
        var previous = -1
        while (at < end) {
            var gap = 0
            var shift = 0
            while (true) {
                val byte = region.u8(at++)
                gap = gap or ((byte and 0x7F) shl shift)
                if (byte < 0x80) break
                shift += 7
            }
            val id = previous + 1 + gap
            action(id, region.u8(at++))
            previous = id
        }
    }

    /** The list of context [index]: its anchor, plus the lengths of the lists before it since. */
    private fun list(lengths: Int, anchors: Int, data: Int, index: Int): Long {
        val length = region.u8(lengths + index)
        if (length == 0) return EMPTY
        val group = index / MkdFormat.NGRAM_ANCHOR_STRIDE
        var offset = data + region.i32(anchors + group * 4)
        for (i in group * MkdFormat.NGRAM_ANCHOR_STRIDE until index) offset += region.u8(lengths + i)
        return handle(offset, length)
    }

    companion object {
        const val EMPTY = 0L
        private const val TRIGRAM_KEY_SIZE = 6

        fun handle(offset: Int, length: Int): Long = (offset.toLong() shl 32) or length.toLong()

        /**
         * The pack's NGRM section, or null when it has none or uses a layout this reader doesn't
         * know (an unknown layout is skipped like an unknown section). Throws
         * [MkdFormatException] when the section is truncated.
         */
        fun open(pack: MkdPack): MkdNgrams? {
            if (pack.ngrams < 0) return null
            val region = pack.region
            val base = pack.ngrams
            val size = pack.ngramsLength
            fun fail(message: String): Nothing = throw MkdFormatException("NGRM: $message")
            if (size < MkdFormat.NGRAM_HEADER_SIZE) fail("too short for a header")
            if (region.u8(base) != MkdFormat.NGRAM_LAYOUT) return null
            fun field(at: Int): Int = region.i32(base + at)
            fun within(offset: Int, length: Long, what: String): Int {
                if (offset < 0 || length < 0 || offset + length > size) fail("$what out of bounds")
                return base + offset
            }
            val contexts = field(4)
            val trigramContexts = field(8)
            if (contexts < 0 || trigramContexts < 0) fail("negative counts")
            val unigrams = handle(within(field(12), field(16).toLong(), "unigrams"), field(16))
            val sentenceStarts = handle(within(field(20), field(24).toLong(), "sentence starts"), field(24))
            val bigramLengths = within(field(28), contexts.toLong(), "bigram lengths")
            val bigramAnchors = within(field(32), anchors(contexts) * 4L, "bigram anchors")
            val bigramData = within(field(36), 0, "bigram data")
            val trigramKeys = within(field(40), trigramContexts * TRIGRAM_KEY_SIZE.toLong(), "trigram keys")
            val trigramLengths = within(field(44), trigramContexts.toLong(), "trigram lengths")
            val trigramAnchors = within(field(48), anchors(trigramContexts) * 4L, "trigram anchors")
            val trigramData = within(field(52), 0, "trigram data")
            return MkdNgrams(
                region, scoreScale = region.u8(base + 1), backoff = region.u8(base + 2),
                bigramContexts = contexts, trigramContexts = trigramContexts,
                unigrams = if (field(16) == 0) EMPTY else unigrams,
                sentenceStarts = if (field(24) == 0) EMPTY else sentenceStarts,
                bigramLengths, bigramAnchors, bigramData, trigramKeys, trigramLengths, trigramAnchors, trigramData,
            )
        }

        private fun anchors(contexts: Int): Int = (contexts + MkdFormat.NGRAM_ANCHOR_STRIDE - 1) / MkdFormat.NGRAM_ANCHOR_STRIDE
    }
}
