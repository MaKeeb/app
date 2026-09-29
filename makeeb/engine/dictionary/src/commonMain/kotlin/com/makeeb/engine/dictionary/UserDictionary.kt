package com.makeeb.engine.dictionary

import kotlin.math.pow

/** A word the keyboard learned from the user's typing. */
data class LearnedWord(
    val word: String,
    /** How often it was committed: typed and left, or picked from the strip. */
    val count: Int,
    /** When it was last learned, on the dictionary's [UserDictionary.clock]. */
    val lastUsed: Long,
    /** The user kept it on purpose at least once: picked it as typed, or undid its autocorrection. */
    val kept: Boolean = false,
)

/**
 * Words the user typed that the main dictionary lacks: names, slang, jargon. Learning never
 * happens in incognito fields; that gate lives in the input engine, which is the only caller of
 * [learn]. [LearnedWordsStore] keeps them across restarts.
 *
 * A word is known only on evidence, as in LatinIME's user history: once committed twice, or kept
 * on purpose once ([learn] with `kept`). A typo that slipped through once is not evidence (autocorrect
 * may have been off or paused), so until then the word is offered as a completion but [lookup],
 * [corrections] and [entries] don't see it: it neither blocks autocorrect nor becomes its target.
 *
 * It stays small: at most [capacity] words. When it fills up, the least useful go first, the ones
 * learned least often, discounted by how long ago (half as useful every [HALF_LIFE] words learned
 * since). Time is counted in words learned ([clock]), not read from a clock, so nothing records
 * when the user typed.
 *
 * Case alone doesn't make a new word: a capital often only means the word started a sentence, so
 * "Yeet" and "yeet" are one entry, and once the word is seen in lower case it stays that way.
 * Accents do ("cafe", "café").
 *
 * Not a trie: a node per letter cost about 1.3 KB a word on the JVM heap, against about 150 bytes
 * here (3,000 words, 2026-09-29). The words sit in a map from [KeyFold]ed key to spellings, beside
 * the keys in sorted order. Prefix queries binary-search the sorted keys, and edit-distance
 * queries walk them the way a trie walk would, computing each shared prefix's row once and
 * skipping every key under a prefix that is already too far off; that costs the same as the trie
 * (about 270 µs for two edits over 3,000 words on the JVM).
 *
 * Not thread-safe: the main thread owns it, like the input engine that asks it.
 */
class UserDictionary(
    override val languageTag: String,
    /** The most words kept. */
    val capacity: Int = DEFAULT_CAPACITY,
) : MutableDictionary {
    private class Entry(var word: String, var count: Int, var lastUsed: Long, var kept: Boolean) {
        val known: Boolean get() = kept || count >= KNOWN_AFTER
    }

    /** Folded key → its spellings, most often learned first. */
    private val byKey = HashMap<String, MutableList<Entry>>()

    /** The keys of [byKey], sorted. */
    private val keys = ArrayList<String>()

    /** Words learned so far, including forgotten ones: the dictionary's measure of time. */
    var clock: Long = 0
        private set

    /** Words held now. */
    var size: Int = 0
        private set

    override fun learn(word: String, kept: Boolean) {
        val key = KeyFold.fold(word)
        if (key.isEmpty() || word.length > MAX_WORD_LENGTH || word.any { it.isWhitespace() || it.isISOControl() }) return
        clock++
        add(key, word, count = 1, lastUsed = clock, kept = kept)
        evictIfFull()
    }

    /** Forgets every spelling of [word] that differs from it only in case. */
    override fun forget(word: String) {
        val key = KeyFold.fold(word)
        val spellings = byKey[key] ?: return
        val before = spellings.size
        spellings.removeAll { it.word.equals(word, ignoreCase = true) }
        size -= before - spellings.size
        if (spellings.isEmpty()) removeKey(key)
    }

    /** Forgets everything. The [clock] keeps going. */
    fun clear() {
        byKey.clear()
        keys.clear()
        size = 0
    }

    /** Every learned word, known or not yet: all of them can be forgotten. */
    override fun isLearned(word: String): Boolean = byKey[KeyFold.fold(word)]?.any { it.word.equals(word, ignoreCase = true) } == true

    fun words(): List<LearnedWord> = byKey.values.flatMap { spellings -> spellings.map { LearnedWord(it.word, it.count, it.lastUsed, it.kept) } }

    /**
     * Puts [earlier] words under the ones held now, as if they had been learned first: they came
     * from disk ([LearnedWordsStore]) and predate everything learned since this dictionary was
     * created. Times learned since move forward by [earlierClock], and counts of the same word
     * add up.
     */
    fun restore(earlier: List<LearnedWord>, earlierClock: Long) {
        val since = byKey.values.flatten()
        val sinceClock = clock
        clear()
        earlier.forEach { word ->
            val key = KeyFold.fold(word.word)
            if (key.isNotEmpty() && word.count > 0) add(key, word.word, word.count, word.lastUsed, word.kept)
        }
        since.forEach { add(KeyFold.fold(it.word), it.word, it.count, it.lastUsed + earlierClock, it.kept) }
        clock = earlierClock + sinceClock
        evictIfFull()
    }

    /** A known word: the spelling typed exactly when it exists, otherwise the most learned one under the key. */
    override fun lookup(word: String): WordEntry? {
        val spellings = byKey[KeyFold.fold(word)] ?: return null
        val entry = spellings.firstOrNull { it.known && it.word == word } ?: spellings.firstOrNull { it.known } ?: return null
        return entry.toWordEntry()
    }

    override fun completions(prefix: String, limit: Int): List<WordEntry> {
        val key = KeyFold.fold(prefix)
        val found = ArrayList<WordEntry>()
        var index = lowerBound(key)
        while (index < keys.size && keys[index].startsWith(key)) {
            byKey.getValue(keys[index]).forEach { found += it.toWordEntry() }
            index++
        }
        return found.sortedByDescending { it.frequency }.take(limit)
    }

    override fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch> {
        val target = KeyFold.fold(word)
        // rows[d] is the distance row for the first d letters of `path`, the last key walked.
        val rows = ArrayList<IntArray>().apply { add(IntArray(target.length + 1) { it }) }
        var path = ""
        val matches = mutableListOf<WordMatch>()
        var index = 0
        while (index < keys.size) {
            val key = keys[index]
            var depth = commonPrefixLength(path, rows.size - 1, key)
            while (rows.size > depth + 1) rows.removeAt(rows.lastIndex)
            path = key
            var tooFar = false
            while (depth < key.length) {
                val row = nextDistanceRow(target, rows[depth], rows.getOrNull(depth - 1), key[depth], key.getOrNull(depth - 1))
                rows += row
                depth++
                if (row.min() > maxEdits) {
                    tooFar = true
                    break
                }
            }
            index++
            if (tooFar) {
                // Nothing under this prefix can get back within reach, as a trie walk would prune.
                while (index < keys.size && sharesPrefix(keys[index], key, depth)) index++
            } else {
                val distance = rows[depth][target.length]
                if (distance <= maxEdits) byKey.getValue(key).forEach { if (it.known) matches += WordMatch(it.toWordEntry(), distance) }
            }
        }
        return matches
            .sortedWith(compareBy<WordMatch> { it.edits }.thenByDescending { it.entry.frequency })
            .take(limit)
    }

    /** Known words only: decoders scanning the vocabulary shouldn't land on a one-off typo. */
    override fun entries(): Sequence<WordEntry> =
        byKey.values.flatMap { spellings -> spellings.filter { it.known }.map { it.toWordEntry() } }.asSequence()

    private fun add(key: String, word: String, count: Int, lastUsed: Long, kept: Boolean) {
        val spellings = byKey[key] ?: ArrayList<Entry>(1).also {
            byKey[key] = it
            keys.add(lowerBound(key), key)
        }
        val same = spellings.firstOrNull { it.word.equals(word, ignoreCase = true) }
        if (same == null) {
            spellings += Entry(word, count.coerceAtMost(MAX_COUNT), lastUsed, kept)
            size++
        } else {
            same.count = (same.count + count).coerceAtMost(MAX_COUNT)
            same.lastUsed = maxOf(same.lastUsed, lastUsed)
            same.kept = same.kept || kept
            if (word == word.lowercase()) same.word = word
        }
        spellings.sortByDescending { it.count }
    }

    private fun removeKey(key: String) {
        byKey.remove(key)
        val index = keys.binarySearch(key)
        if (index >= 0) keys.removeAt(index)
    }

    /**
     * Drops the least useful words, a little past [capacity], so a full dictionary doesn't
     * rank everything again on every new word.
     */
    private fun evictIfFull() {
        if (size <= capacity) return
        val keep = capacity - capacity / EVICTION_HEADROOM
        val ranked = byKey.entries.flatMap { (key, spellings) -> spellings.map { key to it } }
            .sortedWith(compareBy<Pair<String, Entry>> { usefulness(it.second) }.thenBy { it.second.lastUsed })
        ranked.take(size - keep).forEach { (key, entry) ->
            val spellings = byKey.getValue(key)
            spellings.remove(entry)
            size--
            if (spellings.isEmpty()) removeKey(key)
        }
    }

    /** Keeping a word on purpose counts as a second use, so one-off typos go before kept words. */
    private fun usefulness(entry: Entry): Double =
        (entry.count + if (entry.kept) 1 else 0) * 2.0.pow(-(clock - entry.lastUsed) / HALF_LIFE)

    private fun lowerBound(key: String): Int = keys.binarySearch(key).let { if (it >= 0) it else -(it + 1) }

    private fun Entry.toWordEntry() = WordEntry(word, (LEARNED_BASE_FREQUENCY + count).coerceAtMost(MAX_FREQUENCY))

    companion object {
        /**
         * Plenty for the names and slang one person types, since learning skips words the main
         * dictionary knows. Full, it takes about 450 KB of heap and a 35 KB file.
         */
        const val DEFAULT_CAPACITY = 3_000

        /** Words learned since, after which a word counts half as much when making room. */
        const val HALF_LIFE = 1_000.0

        /** Commits that make a word known without the user keeping it on purpose. */
        const val KNOWN_AFTER = 2

        /** A new word ranks like a mid-frequency dictionary word; each use raises it. */
        private const val LEARNED_BASE_FREQUENCY = 120
        private const val MAX_FREQUENCY = 255
        private const val MAX_COUNT = 1 shl 20

        /** Longer "words" are pasted links or keyboard mashing, not vocabulary. */
        private const val MAX_WORD_LENGTH = 48

        /** Eviction makes room for a twentieth of [capacity] at once. */
        private const val EVICTION_HEADROOM = 20

        private fun commonPrefixLength(a: String, aLength: Int, b: String): Int {
            val limit = minOf(aLength, a.length, b.length)
            var i = 0
            while (i < limit && a[i] == b[i]) i++
            return i
        }

        private fun sharesPrefix(a: String, b: String, length: Int): Boolean =
            a.length >= length && commonPrefixLength(a, length, b) == length
    }
}
