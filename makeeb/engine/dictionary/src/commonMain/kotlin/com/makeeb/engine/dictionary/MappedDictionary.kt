package com.makeeb.engine.dictionary

import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdNgrams
import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.engine.dictionary.pack.codePoints
import com.makeeb.platform.storage.ByteRegion

/**
 * A [Dictionary] read straight from a mapped MKD pack ([MkdFormat]). The trie stays in the
 * region: queries decode the nodes they visit into a few reusable fields and build `String`s only
 * for the words they return, so the heap cost is the same for 250 words or 250,000.
 *
 * - [completions] is a best-first search on the best score each node stores, so it touches about
 *   `limit × depth` nodes whatever the vocabulary size.
 * - [corrections] is [TrieDictionary]'s optimal-string-alignment walk, one DP row per code point
 *   along the radix labels. Its cost grows with the number of keys near the query, not with the
 *   vocabulary; the weighted beam search that replaces it is Stage 3 (dictionaries-autocorrect.md §6.2).
 * - Offensive words ([MkdFormat.WORD_OFFENSIVE]) are found by [lookup], so typing one isn't treated
 *   as a typo, but are never offered by [completions], [corrections] or [entries] unless
 *   [suggestOffensive] is set (AOSP blocks them the same way by default).
 * - Keys fold case, diacritics and apostrophes (the pack's `keyFold`). When several spellings
 *   share a key ("us", "US"; "naive", "naïve"), [lookup] returns the one typed exactly, else the
 *   most frequent.
 * - [nextWords] reads the pack's NGRM section in place when it has one: a prediction decodes at
 *   most three short successor lists and builds strings only for the words it returns.
 */
class MappedDictionary(
    val pack: MkdPack,
    private val suggestOffensive: Boolean = false,
) : Dictionary {
    constructor(region: ByteRegion, suggestOffensive: Boolean = false) : this(MkdPack.open(region), suggestOffensive)

    override val languageTag: String = pack.languageTag

    override val isComprehensive: Boolean get() = true

    val wordCount: Int get() = pack.wordCount

    private val region = pack.region
    private val keyFold = pack.keyFold

    /** Nodes the last [completions] call decoded; tests check it stays independent of subtree size. */
    internal var lastCompletionNodes = 0
        private set

    /** DP rows the last [corrections] call computed: its cost, one row per code point walked. */
    internal var lastCorrectionRows = 0
        private set

    override val nextWords: NextWordModel? = MkdNgrams.open(pack)?.let(::PackNextWords)

    override fun lookup(word: String): WordEntry? = wordId(word).takeIf { it >= 0 }?.let(::entry)

    /**
     * The id [lookup] would answer with, or -1. With [sentenceInitial], a capital that only marks
     * a sentence start ("Will you", not "US") prefers the lower-case spelling where both exist.
     * The pack builder tokenises its corpora with this too.
     */
    fun wordId(word: String, sentenceInitial: Boolean = false): Int {
        val key = codePoints(MkdFormat.fold(word, keyFold))
        if (key.isEmpty()) return -1
        val node = Node(region)
        val matched = descend(key, node)
        if (matched != node.labelLength || !node.isTerminal) return -1
        if (node.wordCount > 1) {
            if (sentenceInitial && word.first().isUpperCase() && word.drop(1).none(Char::isUpperCase)) {
                spelling(node, word.lowercase())?.let { return it }
            }
            spelling(node, word)?.let { return it }
        }
        return node.wordId(0)
    }

    /** The id of the word at [node] spelt exactly [text], if there is one. */
    private fun spelling(node: Node, text: String): Int? {
        val bytes = text.encodeToByteArray()
        for (i in 0 until node.wordCount) {
            val id = node.wordId(i)
            if (pack.wordEquals(id, bytes)) return id
        }
        return null
    }

    override fun completions(prefix: String, limit: Int): List<WordEntry> {
        if (limit <= 0) return emptyList()
        val key = codePoints(MkdFormat.fold(prefix, keyFold))
        val node = Node(region)
        val queue = BestFirstQueue()
        if (key.isEmpty()) {
            queue.pushArray(pack.lexicon)
        } else {
            if (descend(key, node) < 0) return emptyList()
            queue.push(node.best, isWord = false, first = node.at, second = 0)
        }
        val out = ArrayList<WordEntry>(limit)
        var visited = 0
        while (out.size < limit && queue.pop()) {
            if (queue.isWord) {
                val id = queue.first
                if (suggestOffensive || !pack.isOffensive(id)) out += entry(id)
                continue
            }
            node.read(queue.first)
            visited++
            val siblingsLeft = queue.second
            // Siblings are sorted by best score, so the next one can wait until this one is popped.
            if (siblingsLeft > 0) queue.push(region.u8(node.end + 1), isWord = false, first = node.end, second = siblingsLeft - 1)
            for (i in 0 until node.wordCount) {
                val id = node.wordId(i)
                val frequency = if (node.wordCount == 1) node.frequency else pack.wordFrequency(id)
                queue.push(frequency, isWord = true, first = id, second = 0)
            }
            if (node.childArray >= 0) queue.pushArray(node.childArray)
        }
        lastCompletionNodes = visited
        return out
    }

    override fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch> {
        if (limit <= 0 || maxEdits < 0) return emptyList()
        val walk = CorrectionWalk(codePoints(MkdFormat.fold(word, keyFold)), maxEdits, limit)
        walk.visitArray(pack.lexicon, depth = 0)
        lastCorrectionRows = walk.rowsComputed
        return walk.results()
    }

    override fun entries(): Sequence<WordEntry> = sequence {
        for (id in 0 until pack.wordCount) {
            if (suggestOffensive || !pack.isOffensive(id)) yield(entry(id))
        }
    }

    /**
     * Reads one byte per 4 KB of the first [bytes] of the trie and of the word index, so the
     * first keystrokes don't stall on page faults. The breadth-first layout puts the levels every
     * query starts from at the front. Call it off the main thread; the pages it touches are clean
     * and the OS may drop them again.
     */
    fun warmUp(bytes: Int = 256 * 1024): Int {
        var sum = 0
        var offset = pack.lexicon
        val end = minOf(pack.lexiconEnd, pack.lexicon + bytes)
        while (offset < end) {
            sum += region.u8(offset)
            offset += PAGE_STRIDE
        }
        val indexEnd = minOf(pack.wordCount, bytes / 3)
        var id = 0
        while (id < indexEnd) {
            sum += pack.wordFrequency(id)
            id += PAGE_STRIDE / 3
        }
        return sum
    }

    private fun entry(id: Int) = WordEntry(pack.wordText(id), pack.wordFrequency(id))

    /**
     * Follows [key] from the root and leaves [node] on the node where it ends. Returns how many
     * code points of that node's label the key covered (the label length when it ended exactly
     * on the node), or -1 if no key in the trie starts with it.
     */
    private fun descend(key: IntArray, node: Node): Int {
        var array = pack.lexicon
        var i = 0
        while (true) {
            if (!findChild(array, key[i], node)) return -1
            var position = node.labelAt
            var matched = 0
            while (matched < node.labelLength) {
                if (i == key.size) return matched
                if (codePointAt(region, position) != key[i]) return -1
                position += codePointSize(region, position)
                i++
                matched++
            }
            if (i == key.size) return matched
            if (node.childArray < 0) return -1
            array = node.childArray
        }
    }

    private fun findChild(array: Int, codePoint: Int, node: Node): Boolean {
        var position = firstNode(region, array)
        repeat(nodeCount(region, array)) {
            node.read(position)
            if (codePointAt(region, node.labelAt) == codePoint) return true
            position = node.end
        }
        return false
    }

    /** A max-queue of trie nodes (bounded by their best score) and words (scored by frequency). */
    private inner class BestFirstQueue {
        private var heap = LongArray(64)
        private var size = 0
        private var firsts = IntArray(64)
        private var seconds = IntArray(64)
        private var count = 0

        var isWord = false
            private set
        var first = 0
            private set
        var second = 0
            private set

        /** Queues the first node of the array at [array]; the rest follow one by one. */
        fun pushArray(array: Int) {
            val start = firstNode(region, array)
            push(region.u8(start + 1), isWord = false, first = start, second = nodeCount(region, array) - 1)
        }

        fun push(score: Int, isWord: Boolean, first: Int, second: Int) {
            if (count == firsts.size) {
                firsts = firsts.copyOf(count * 2)
                seconds = seconds.copyOf(count * 2)
            }
            firsts[count] = first
            seconds[count] = second
            // Higher score first; at equal scores words before nodes, then first in, first out.
            val key = (score.toLong() shl 40) or (if (isWord) 1L shl 39 else 0L) or (SEQUENCE_MASK - count)
            count++
            if (size == heap.size) heap = heap.copyOf(size * 2)
            var i = size++
            while (i > 0) {
                val parent = (i - 1) / 2
                if (heap[parent] >= key) break
                heap[i] = heap[parent]
                i = parent
            }
            heap[i] = key
        }

        /** Moves the best item into [isWord], [first] and [second]; false when empty. */
        fun pop(): Boolean {
            if (size == 0) return false
            val top = heap[0]
            val last = heap[--size]
            var i = 0
            while (true) {
                var child = 2 * i + 1
                if (child >= size) break
                if (child + 1 < size && heap[child + 1] > heap[child]) child++
                if (heap[child] <= last) break
                heap[i] = heap[child]
                i = child
            }
            if (size > 0) heap[i] = last
            val index = (SEQUENCE_MASK - (top and SEQUENCE_MASK)).toInt()
            isWord = top and (1L shl 39) != 0L
            first = firsts[index]
            second = seconds[index]
            return true
        }
    }

    /**
     * The optimal-string-alignment walk of [TrieDictionary.corrections] over the mapped trie: one
     * DP row per code point, pruned once no row entry is within [maxEdits]. Keeps the best [limit]
     * matches (fewest edits, then most frequent) as ids and builds strings only for those.
     *
     * Only the band of cells within [maxEdits] of the diagonal is computed (Ukkonen's cut-off):
     * a cell further off can't be within [maxEdits], so every value is capped at `maxEdits + 1`
     * and the cells outside the band stay at that cap. Distances up to [maxEdits], the only ones
     * returned, are exact; a row costs `2 × maxEdits + 1` cells instead of the word's length.
     */
    private inner class CorrectionWalk(private val target: IntArray, private val maxEdits: Int, private val limit: Int) {
        private val cap = maxEdits + 1
        private val node = Node(region)
        private val rows = ArrayList<IntArray>().apply { add(IntArray(target.size + 1) { minOf(it, cap) }) }
        private var mins = IntArray(32)
        private var chars = IntArray(32)

        private val ids = IntArray(limit)
        private val edits = IntArray(limit)
        private val frequencies = IntArray(limit)
        private var found = 0
        var rowsComputed = 0
            private set

        fun visitArray(array: Int, depth: Int) {
            var position = firstNode(region, array)
            repeat(nodeCount(region, array)) {
                node.read(position)
                position = node.end
                visitNode(depth)
            }
        }

        /** Walks the node [node] holds; its fields are copied first, since children reuse it. */
        private fun visitNode(depth: Int) {
            val labelLength = node.labelLength
            val childArray = node.childArray
            val wordCount = node.wordCount
            val wordsAt = node.wordsAt
            val frequency = node.frequency
            var position = node.labelAt
            var d = depth
            repeat(labelLength) {
                val c = codePointAt(region, position)
                position += codePointSize(region, position)
                nextRow(d, c)
                d++
                if (mins[d] > maxEdits) return
            }
            val distance = rows[d][target.size]
            if (distance <= maxEdits) {
                for (i in 0 until wordCount) {
                    val id = region.u24(wordsAt + 3 * i)
                    if (!suggestOffensive && pack.isOffensive(id)) continue
                    offer(id, distance, if (wordCount == 1) frequency else pack.wordFrequency(id))
                }
            }
            if (childArray >= 0) visitArray(childArray, d)
        }

        /** Computes the row after consuming [c] at depth [d] (so it becomes row d + 1). */
        private fun nextRow(d: Int, c: Int) {
            rowsComputed++
            if (d + 1 >= chars.size) {
                chars = chars.copyOf(chars.size * 2)
                mins = mins.copyOf(mins.size * 2)
            }
            chars[d] = c
            // Each row array always holds the same depth, so cells outside its band keep the cap.
            if (rows.size <= d + 1) rows.add(IntArray(target.size + 1) { cap })
            val previous = rows[d]
            val row = rows[d + 1]
            val beforePrevious = if (d >= 1) rows[d - 1] else null
            row[0] = minOf(d + 1, cap)
            var min = row[0]
            val from = maxOf(1, d + 1 - maxEdits)
            val to = minOf(target.size, d + 1 + maxEdits)
            for (i in from..to) {
                val substitution = previous[i - 1] + if (target[i - 1] == c) 0 else 1
                var best = minOf(row[i - 1] + 1, previous[i] + 1, substitution)
                if (beforePrevious != null && i > 1 && target[i - 1] == chars[d - 1] && target[i - 2] == c) {
                    best = minOf(best, beforePrevious[i - 2] + 1)
                }
                if (best > cap) best = cap
                row[i] = best
                if (best < min) min = best
            }
            mins[d + 1] = min
        }

        private fun offer(id: Int, distance: Int, frequency: Int) {
            /** Whether the new match ranks above the one in [slot]. */
            fun beats(slot: Int): Boolean = when {
                distance != edits[slot] -> distance < edits[slot]
                frequency != frequencies[slot] -> frequency > frequencies[slot]
                else -> id < ids[slot]
            }
            if (found == limit && !beats(found - 1)) return
            var i = if (found < limit) found++ else found - 1
            while (i > 0 && beats(i - 1)) {
                ids[i] = ids[i - 1]
                edits[i] = edits[i - 1]
                frequencies[i] = frequencies[i - 1]
                i--
            }
            ids[i] = id
            edits[i] = distance
            frequencies[i] = frequency
        }

        fun results(): List<WordMatch> = List(found) { WordMatch(entry(ids[it]), edits[it]) }
    }

    /**
     * Stupid backoff over the pack's lists: the trigram list of the last two words scores as it
     * is, the last word's bigram list one [MkdNgrams.backoff] lower, the unigrams two lower. A word
     * keeps the score of the highest order that lists it. Scores are score bytes (lower is
     * better) until the words are built.
     */
    private inner class PackNextWords(private val ngrams: MkdNgrams) : NextWordModel {
        override fun predict(previous: List<String>, fromSentenceStart: Boolean, limit: Int): List<NextWord> {
            if (limit <= 0) return emptyList()
            val count = previous.size
            val last = if (count >= 1) wordId(previous[count - 1], sentenceInitial = fromSentenceStart && count == 1) else -1
            val before = when {
                count >= 2 -> wordId(previous[count - 2], sentenceInitial = fromSentenceStart && count == 2)
                count == 1 && fromSentenceStart -> MkdFormat.SENTENCE_START
                else -> -1
            }
            val candidates = Candidates()
            if (last >= 0) {
                if (before >= 0) candidates.addAll(ngrams.trigrams(before, last), penalty = 0)
                candidates.addAll(ngrams.bigrams(last), penalty = ngrams.backoff)
            } else if (count == 0 && fromSentenceStart) {
                candidates.addAll(ngrams.sentenceStarts(), penalty = ngrams.backoff)
            }
            candidates.addAll(ngrams.unigrams(), penalty = 2 * ngrams.backoff)
            return candidates.best(limit)
        }

        private inner class Candidates {
            private var ids = IntArray(64)
            private var scores = IntArray(64)
            private var size = 0

            fun addAll(list: Long, penalty: Int) {
                ngrams.forEachSuccessor(list) { id, score ->
                    if (!suggestOffensive && pack.isOffensive(id)) return@forEachSuccessor
                    for (i in 0 until size) if (ids[i] == id) return@forEachSuccessor
                    if (size == ids.size) {
                        ids = ids.copyOf(size * 2)
                        scores = scores.copyOf(size * 2)
                    }
                    ids[size] = id
                    scores[size] = score + penalty
                    size++
                }
            }

            /** The [limit] lowest scores, ties to the more frequent (lower) id. */
            fun best(limit: Int): List<NextWord> {
                val out = ArrayList<NextWord>(minOf(limit, size))
                val scale = ngrams.scoreScale.toFloat()
                while (out.size < limit) {
                    var pick = -1
                    for (i in 0 until size) {
                        if (ids[i] < 0) continue
                        if (pick < 0 || scores[i] < scores[pick] || (scores[i] == scores[pick] && ids[i] < ids[pick])) pick = i
                    }
                    if (pick < 0) break
                    val id = ids[pick]
                    out += NextWord(pack.wordText(id), pack.wordFrequency(id), -scores[pick] / scale)
                    ids[pick] = -1
                }
                return out
            }
        }
    }

    /** One decoded trie node. Reused across a query: read() overwrites every field. */
    private class Node(private val region: ByteRegion) {
        var at = 0
        var flags = 0
        var best = 0
        var labelAt = 0
        var labelLength = 0
        var frequency = 0
        var wordCount = 0
        var wordsAt = 0
        var childArray = -1
        var end = 0

        val isTerminal: Boolean get() = flags and MkdFormat.NODE_TERMINAL != 0

        fun wordId(i: Int): Int = region.u24(wordsAt + 3 * i)

        fun read(offset: Int) {
            at = offset
            flags = region.u8(offset)
            best = region.u8(offset + 1)
            var p = offset + 2
            labelAt = p
            if (flags and MkdFormat.NODE_MULTI_CHAR != 0) {
                var length = 0
                while (true) {
                    val byte = region.u8(p)
                    if (byte == MkdFormat.LABEL_END) break
                    p += if (byte < 0x20) 3 else 1
                    length++
                }
                p++
                labelLength = length
            } else {
                p += codePointSize(region, p)
                labelLength = 1
            }
            if (flags and MkdFormat.NODE_TERMINAL != 0) {
                frequency = region.u8(p++)
                wordCount = if (flags and MkdFormat.NODE_VARIANTS != 0) region.u8(p++) else 1
                wordsAt = p
                p += 3 * wordCount
            } else {
                frequency = 0
                wordCount = 0
            }
            val width = flags and MkdFormat.NODE_CHILD_WIDTH
            childArray = when (width) {
                0 -> -1
                1 -> offset + region.u8(p)
                2 -> offset + region.u16(p)
                else -> offset + region.u24(p)
            }
            end = p + width
        }
    }

    private companion object {
        const val PAGE_STRIDE = 4096
        const val SEQUENCE_MASK = (1L shl 39) - 1

        fun nodeCount(region: ByteRegion, array: Int): Int {
            val first = region.u8(array)
            return if (first < 0x80) first else ((first and 0x7F) shl 8) or region.u8(array + 1)
        }

        fun firstNode(region: ByteRegion, array: Int): Int = array + if (region.u8(array) < 0x80) 1 else 2

        fun codePointAt(region: ByteRegion, at: Int): Int {
            val first = region.u8(at)
            return if (first >= 0x20) first else (first shl 16) or (region.u8(at + 1) shl 8) or region.u8(at + 2)
        }

        fun codePointSize(region: ByteRegion, at: Int): Int = if (region.u8(at) >= 0x20) 1 else 3
    }
}
