package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdNgramTable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/** A Leipzig Corpora Collection download: a tarball whose `*-sentences.txt` holds `id<TAB>sentence` lines. */
class LeipzigCorpus(val name: String, val source: PinnedSource)

/**
 * How much of the counts goes into a pack. Tuned on held-out text (2026-09-29): these keep the
 * en_US NGRM section at 2.5 MiB. Trigrams are worth their bytes (without them top-3 accuracy falls
 * from 28% to 24%); longer successor lists mostly help the completion boost; trigram contexts
 * whose predictions match their bigrams' are dropped, which saves a fifth at no cost.
 */
data class NgramPruning(
    /** The commonest words, the fallback after any context. */
    val unigrams: Int = 64,
    /** Successors kept per one-word context (and after a sentence start). */
    val bigramsPerContext: Int = 32,
    val minBigramCount: Int = 3,
    /** Successors kept per two-word context. */
    val trigramsPerContext: Int = 8,
    val minTrigramCount: Int = 4,
    /** Two-word contexts seen fewer times than this are left to their last word's bigrams. */
    val minTrigramContext: Int = 16,
    /**
     * Leave out two-word contexts whose three best predictions are the ones their last word's
     * bigrams already give: they would cost a key and a list and change nothing in the strip.
     */
    val dropRedundantTrigrams: Boolean = true,
)

/**
 * Word, bigram and trigram counts from corpora, over one pack's lexicon: every word's count, and
 * the bigrams and trigrams seen at least twice, each with its context's total. Everything else
 * is pruned from these ([prune]), so they are cached and pruning can change without recounting.
 *
 * Ids are the lexicon pack's; [SENTENCE] stands for a sentence start. Words the lexicon lacks
 * (names, numbers, typos) break the chain: no n-gram spans them.
 */
class NgramCounts(
    val sentences: Long,
    val tokens: Long,
    val knownTokens: Long,
    val unigrams: IntArray,
    private val bigramKeys: LongArray,
    private val bigramCounts: IntArray,
    private val bigramTotals: IntArray,
    private val trigramKeys: LongArray,
    private val trigramCounts: IntArray,
    private val trigramTotals: IntArray,
) {
    val distinctBigrams: Int get() = bigramKeys.size
    val distinctTrigrams: Int get() = trigramKeys.size

    /** The pack's next-word table: the lists [pruning] keeps, as relative frequencies. */
    fun prune(pruning: NgramPruning, offensive: (Int) -> Boolean): MkdNgramTable {
        val table = MkdNgramTable()
        val knownTotal = unigrams.sumOf { it.toLong() }.toDouble()
        val unigramList = unigrams.indices.filter { unigrams[it] > 0 && !offensive(it) }
            .sortedWith(compareByDescending<Int> { unigrams[it] }.thenBy { it })
            .take(pruning.unigrams)
            .map { it to unigrams[it] / knownTotal }
        unigramList.forEach { (word, p) -> table.unigram(word, p) }

        val bigramLists = HashMap<Int, List<Pair<Int, Double>>>()
        forEachContext(bigramKeys, contextShift = BITS) { from, to ->
            val context = (bigramKeys[from] ushr BITS).toInt()
            val list = top(from, to, bigramKeys, bigramCounts, pruning.minBigramCount, pruning.bigramsPerContext, offensive)
                .map { (word, count) -> word to count.toDouble() / bigramTotals[from] }
            list.forEach { (word, p) -> table.bigram(if (context == SENTENCE) MkdFormat.SENTENCE_START else context, word, p) }
            bigramLists[context] = list
        }
        forEachContext(trigramKeys, contextShift = BITS) { from, to ->
            if (trigramTotals[from] < pruning.minTrigramContext) return@forEachContext
            val first = (trigramKeys[from] ushr (2 * BITS)).toInt()
            val second = ((trigramKeys[from] ushr BITS) and MASK).toInt()
            val list = top(from, to, trigramKeys, trigramCounts, pruning.minTrigramCount, pruning.trigramsPerContext, offensive)
                .map { (word, count) -> word to count.toDouble() / trigramTotals[from] }
            if (list.isEmpty()) return@forEachContext
            val backoff = bigramLists[second].orEmpty()
            if (pruning.dropRedundantTrigrams && best(list, backoff, unigramList) == best(emptyList(), backoff, unigramList)) return@forEachContext
            list.forEach { (word, p) -> table.trigram(if (first == SENTENCE) MkdFormat.SENTENCE_START else first, second, word, p) }
        }
        return table
    }

    /**
     * The three words stupid backoff ranks first, as the keyboard ranks them
     * (`MappedDictionary.nextWords`): each word scored by the highest order that lists it.
     */
    private fun best(trigrams: List<Pair<Int, Double>>, bigrams: List<Pair<Int, Double>>, unigrams: List<Pair<Int, Double>>): List<Int> {
        val scores = LinkedHashMap<Int, Int>()
        for ((penalty, list) in listOf(0 to trigrams, MkdFormat.NGRAM_BACKOFF to bigrams, 2 * MkdFormat.NGRAM_BACKOFF to unigrams)) {
            for ((word, p) in list) scores.getOrPut(word) { MkdNgramTable.score(p) + penalty }
        }
        return scores.entries.sortedWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).take(3).map { it.key }
    }

    /** Calls [action] with each run of entries that share a context (all bits above [contextShift]). */
    private inline fun forEachContext(keys: LongArray, contextShift: Int, action: (from: Int, to: Int) -> Unit) {
        var from = 0
        while (from < keys.size) {
            val context = keys[from] ushr contextShift
            var to = from + 1
            while (to < keys.size && keys[to] ushr contextShift == context) to++
            action(from, to)
            from = to
        }
    }

    /** The [limit] most frequent successors in entries [from] until [to] with at least [minCount], as (word, count). */
    private fun top(from: Int, to: Int, keys: LongArray, counts: IntArray, minCount: Int, limit: Int, offensive: (Int) -> Boolean): List<Pair<Int, Int>> =
        (from until to).filter { counts[it] >= minCount && !offensive((keys[it] and MASK).toInt()) }
            .sortedWith(compareByDescending<Int> { counts[it] }.thenBy { keys[it] and MASK })
            .take(limit)
            .map { (keys[it] and MASK).toInt() to counts[it] }

    fun write(file: File) {
        val partial = File(file.path + ".part")
        DataOutputStream(BufferedOutputStream(partial.outputStream(), 1 shl 20)).use { out ->
            out.writeInt(CACHE_VERSION)
            out.writeLong(sentences)
            out.writeLong(tokens)
            out.writeLong(knownTokens)
            out.writeInt(unigrams.size)
            unigrams.forEach(out::writeInt)
            for ((keys, counts, totals) in listOf(Triple(bigramKeys, bigramCounts, bigramTotals), Triple(trigramKeys, trigramCounts, trigramTotals))) {
                out.writeInt(keys.size)
                keys.forEach(out::writeLong)
                counts.forEach(out::writeInt)
                totals.forEach(out::writeInt)
            }
        }
        check(partial.renameTo(file) || (file.delete() && partial.renameTo(file))) { "could not write $file" }
    }

    companion object {
        /** Bits per word id in a key; ids and [SENTENCE] fit below 2^21. */
        const val BITS = 21
        const val MASK = (1L shl BITS) - 1
        const val SENTENCE = (1 shl BITS) - 1
        private const val UNKNOWN = -1
        private const val CACHE_VERSION = 1

        fun read(file: File): NgramCounts? {
            DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 20)).use { input ->
                if (input.readInt() != CACHE_VERSION) return null
                val sentences = input.readLong()
                val tokens = input.readLong()
                val known = input.readLong()
                val unigrams = IntArray(input.readInt()) { input.readInt() }
                fun arrays(): Triple<LongArray, IntArray, IntArray> {
                    val size = input.readInt()
                    return Triple(LongArray(size) { input.readLong() }, IntArray(size) { input.readInt() }, IntArray(size) { input.readInt() })
                }
                val (bk, bc, bt) = arrays()
                val (tk, tc, tt) = arrays()
                return NgramCounts(sentences, tokens, known, unigrams, bk, bc, bt, tk, tc, tt)
            }
        }

        /**
         * Tokenises [corpora] with [lexicon] ([CorpusTokens], [MappedDictionary.wordId]) and counts.
         * Sentences [heldOut] picks by their corpus id go to [heldOutSink] instead of the counts, so
         * the typing harness can measure predictions on text the model never saw.
         */
        fun count(
            corpora: List<File>,
            lexicon: MappedDictionary,
            heldOut: (Long) -> Boolean,
            heldOutSink: (String) -> Unit,
        ): NgramCounts {
            val stream = IntList(1 shl 24)
            val ids = HashMap<String, Int>(1 shl 21)
            val initialIds = HashMap<String, Int>(1 shl 18)
            var sentences = 0L
            var tokens = 0L
            var known = 0L
            val unigrams = IntArray(lexicon.wordCount)
            for (corpus in corpora) {
                GZIPInputStream(BufferedInputStream(corpus.inputStream(), 1 shl 20), 1 shl 16).use { gz ->
                    forEachTarEntry(gz) { path, content ->
                        if (!path.endsWith("-sentences.txt")) return@forEachTarEntry
                        content.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                            val tab = line.indexOf('\t')
                            if (tab < 0) return@forEachLine
                            val sentence = CorpusTokens.normalise(line.substring(tab + 1))
                            if (heldOut(line.substring(0, tab).toLongOrNull() ?: 0L)) {
                                heldOutSink(sentence)
                                return@forEachLine
                            }
                            sentences++
                            stream.add(SENTENCE)
                            CorpusTokens.words(sentence).forEachIndexed { index, word ->
                                if (word.sentenceInitial && index > 0) stream.add(SENTENCE)
                                val cache = if (word.sentenceInitial) initialIds else ids
                                val id = cache.getOrPut(word.text) { lexicon.wordId(word.text, word.sentenceInitial) }
                                tokens++
                                if (id >= 0) {
                                    known++
                                    unigrams[id]++
                                    stream.add(id)
                                } else {
                                    stream.add(UNKNOWN)
                                }
                            }
                        }
                    }
                }
            }
            ids.clear()
            initialIds.clear()
            val (bigramKeys, bigramCounts, bigramTotals) = ngrams(stream, order = 2)
            val (trigramKeys, trigramCounts, trigramTotals) = ngrams(stream, order = 3)
            return NgramCounts(sentences, tokens, known, unigrams, bigramKeys, bigramCounts, bigramTotals, trigramKeys, trigramCounts, trigramTotals)
        }

        /**
         * Every n-gram of [order] in [stream], sorted, counted, and cut to those seen twice or
         * more, each with its context's total (of all n-grams with that context, kept or not).
         */
        private fun ngrams(stream: IntList, order: Int): Triple<LongArray, IntArray, IntArray> {
            val keys = LongArray(stream.size)
            var size = 0
            var first = UNKNOWN
            var second = UNKNOWN
            for (index in 0 until stream.size) {
                val token = stream[index]
                when (token) {
                    UNKNOWN -> { first = UNKNOWN; second = UNKNOWN }
                    SENTENCE -> { first = UNKNOWN; second = SENTENCE }
                    else -> {
                        if (order == 2 && second != UNKNOWN) keys[size++] = (second.toLong() shl BITS) or token.toLong()
                        if (order == 3 && first != UNKNOWN && second != UNKNOWN) {
                            keys[size++] = (first.toLong() shl (2 * BITS)) or (second.toLong() shl BITS) or token.toLong()
                        }
                        first = second
                        second = token
                    }
                }
            }
            keys.sort(0, size)
            // Runs of equal keys are one n-gram; runs of equal contexts (key >>> BITS) one context.
            val outKeys = LongArrayList()
            val outCounts = IntList(1 shl 16)
            val outTotals = IntList(1 shl 16)
            var from = 0
            while (from < size) {
                val context = keys[from] ushr BITS
                var to = from
                while (to < size && keys[to] ushr BITS == context) to++
                val total = to - from
                var i = from
                while (i < to) {
                    var j = i + 1
                    while (j < to && keys[j] == keys[i]) j++
                    if (j - i >= 2) {
                        outKeys.add(keys[i])
                        outCounts.add(j - i)
                        outTotals.add(total)
                    }
                    i = j
                }
                from = to
            }
            return Triple(outKeys.toArray(), outCounts.toArray(), outTotals.toArray())
        }
    }
}

/** A growable IntArray. */
internal class IntList(capacity: Int) {
    private var values = IntArray(capacity)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(values.size + values.size / 2)
        values[size++] = value
    }

    operator fun get(index: Int): Int = values[index]

    fun toArray(): IntArray = values.copyOf(size)
}

/** A growable LongArray. */
internal class LongArrayList {
    private var values = LongArray(1 shl 16)
    private var size = 0

    fun add(value: Long) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }

    fun toArray(): LongArray = values.copyOf(size)
}
