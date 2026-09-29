package com.makeeb.engine.dictionary.pack

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Next-word statistics on their way into a pack's NGRM section ([MkdFormat]), keyed by the word
 * ids of the pack they go into: build the lexicon first, then look words up in it. Each entry is
 * the probability of a word after its context (a relative frequency; stupid backoff needs nothing
 * else). Build time only: it holds everything on the heap.
 */
class MkdNgramTable {
    internal val unigrams = HashMap<Int, Double>()
    internal val sentenceStarts = HashMap<Int, Double>()
    internal val bigrams = HashMap<Int, HashMap<Int, Double>>()
    internal val trigrams = HashMap<Long, HashMap<Int, Double>>()

    val bigramCount: Int get() = sentenceStarts.size + bigrams.values.sumOf { it.size }
    val trigramCount: Int get() = trigrams.values.sumOf { it.size }

    /** [word] with no context: the fallback when nothing better is known. */
    fun unigram(word: Int, probability: Double) {
        unigrams[word] = checked(word, probability)
    }

    /** [word] after [context], a word id or [MkdFormat.SENTENCE_START]. */
    fun bigram(context: Int, word: Int, probability: Double) {
        val list = if (context == MkdFormat.SENTENCE_START) sentenceStarts else bigrams.getOrPut(checkedId(context)) { HashMap() }
        list[word] = checked(word, probability)
    }

    /** [word] after [first] (a word id or [MkdFormat.SENTENCE_START]) and [second]. */
    fun trigram(first: Int, second: Int, word: Int, probability: Double) {
        if (first != MkdFormat.SENTENCE_START) checkedId(first)
        trigrams.getOrPut(key(first, checkedId(second))) { HashMap() }[word] = checked(word, probability)
    }

    private fun checked(word: Int, probability: Double): Double {
        checkedId(word)
        require(probability > 0.0 && probability <= 1.0) { "probability $probability out of range" }
        return probability
    }

    private fun checkedId(id: Int): Int {
        require(id in 0 until MkdFormat.SENTENCE_START) { "word id $id out of range" }
        return id
    }

    companion object {
        internal fun key(first: Int, second: Int): Long = (first.toLong() shl 24) or second.toLong()

        /** A probability as the pack stores it: a score byte, tenths of a bit below certainty. */
        fun score(probability: Double): Int =
            (-MkdFormat.NGRAM_SCORE_SCALE * ln(probability) / ln(2.0)).roundToInt().coerceIn(0, 255)
    }
}

/** Encodes an [MkdNgramTable] for a pack of [wordCount] words ([MkdFormat] has the layout). */
internal object MkdNgramEncoder {
    fun encode(table: MkdNgramTable, wordCount: Int): ByteArray {
        fun checkIds(list: Map<Int, Double>) = list.keys.forEach { require(it < wordCount) { "word id $it is not in the pack" } }
        checkIds(table.unigrams)
        checkIds(table.sentenceStarts)
        table.bigrams.forEach { (context, list) -> require(context < wordCount) { "context $context is not in the pack" }; checkIds(list) }
        table.trigrams.forEach { (key, list) ->
            val first = (key shr 24).toInt()
            val second = (key and 0xFFFFFF).toInt()
            require((first == MkdFormat.SENTENCE_START || first < wordCount) && second < wordCount) { "trigram context is not in the pack" }
            checkIds(list)
        }

        val contexts = (table.bigrams.keys.maxOrNull() ?: -1) + 1
        val trigramKeys = table.trigrams.keys.sorted()
        val unigrams = list(table.unigrams, capped = false)
        val sentenceStarts = list(table.sentenceStarts, capped = false)
        val bigramLists = List(contexts) { context -> table.bigrams[context]?.let { list(it, capped = true) } ?: ByteArray(0) }
        val trigramLists = trigramKeys.map { list(table.trigrams.getValue(it), capped = true) }

        val out = ByteWriter()
        repeat(MkdFormat.NGRAM_HEADER_SIZE) { out.u8(0) }
        out.patchU8(0, MkdFormat.NGRAM_LAYOUT)
        out.patchU8(1, MkdFormat.NGRAM_SCORE_SCALE)
        out.patchU8(2, MkdFormat.NGRAM_BACKOFF)
        out.patchU32(4, contexts)
        out.patchU32(8, trigramKeys.size)
        fun block(field: Int, bytes: ByteArray) {
            out.patchU32(field, out.size)
            out.patchU32(field + 4, bytes.size)
            out.bytes(bytes)
        }
        block(12, unigrams)
        block(20, sentenceStarts)
        out.patchU32(28, out.size)
        bigramLists.forEach { out.u8(it.size) }
        offsetTables(out, anchorsField = 32, dataField = 36, bigramLists)
        out.patchU32(40, out.size)
        trigramKeys.forEach { key ->
            out.u24((key shr 24).toInt())
            out.u24((key and 0xFFFFFF).toInt())
        }
        out.patchU32(44, out.size)
        trigramLists.forEach { out.u8(it.size) }
        offsetTables(out, anchorsField = 48, dataField = 52, trigramLists)
        return out.toByteArray()
    }

    /** Anchors for every [MkdFormat.NGRAM_ANCHOR_STRIDE] lists, then the lists back to back. */
    private fun offsetTables(out: ByteWriter, anchorsField: Int, dataField: Int, lists: List<ByteArray>) {
        out.patchU32(anchorsField, out.size)
        var position = 0
        lists.forEachIndexed { i, list ->
            if (i % MkdFormat.NGRAM_ANCHOR_STRIDE == 0) out.u32(position)
            position += list.size
        }
        out.patchU32(dataField, out.size)
        lists.forEach(out::bytes)
    }

    /** Successors in ascending id order: varint gap, then the score byte. */
    private fun list(successors: Map<Int, Double>, capped: Boolean): ByteArray {
        val out = ByteWriter()
        var previous = -1
        for (id in successors.keys.sorted()) {
            var gap = id - previous - 1
            while (gap >= 0x80) {
                out.u8(0x80 or (gap and 0x7F))
                gap = gap ushr 7
            }
            out.u8(gap)
            out.u8(MkdNgramTable.score(successors.getValue(id)))
            previous = id
        }
        require(!capped || out.size <= MkdFormat.MAX_NGRAM_LIST_BYTES) { "a successor list over ${MkdFormat.MAX_NGRAM_LIST_BYTES} bytes: keep fewer successors" }
        return out.toByteArray()
    }
}
