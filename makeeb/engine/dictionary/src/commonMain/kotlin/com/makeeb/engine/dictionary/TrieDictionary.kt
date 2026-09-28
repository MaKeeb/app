package com.makeeb.engine.dictionary

/**
 * An in-memory trie supporting prefix completion and bounded edit-distance search: a trie walk
 * computing one row of the optimal-string-alignment (Damerau) distance per node, so an adjacent
 * transposition ("teh" → "the") costs one edit. Branches are pruned once they cannot get back
 * under the limit.
 *
 * Fine for bundled starter lists and the user dictionary. Full language dictionaries will need a
 * compact memory-mapped format to fit the iOS extension memory budget (board:
 * APP-37).
 */
open class TrieDictionary(
    override val languageTag: String,
    entries: Iterable<WordEntry> = emptyList(),
) : Dictionary {
    private class Node {
        val children = HashMap<Char, Node>()
        var entry: WordEntry? = null
    }

    private val root = Node()

    init {
        entries.forEach(::insert)
    }

    protected fun insert(entry: WordEntry) {
        var node = root
        entry.word.lowercase().forEach { char -> node = node.children.getOrPut(char) { Node() } }
        val existing = node.entry
        node.entry = if (existing == null || entry.frequency >= existing.frequency) entry else existing
    }

    protected fun remove(word: String) {
        find(word.lowercase())?.entry = null
    }

    override fun lookup(word: String): WordEntry? = find(word.lowercase())?.entry

    override fun completions(prefix: String, limit: Int): List<WordEntry> {
        val start = find(prefix.lowercase()) ?: return emptyList()
        val found = mutableListOf<WordEntry>()
        collect(start, found)
        return found.sortedByDescending { it.frequency }.take(limit)
    }

    override fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch> {
        val target = word.lowercase()
        val firstRow = IntArray(target.length + 1) { it }
        val matches = mutableListOf<WordMatch>()
        root.children.forEach { (char, child) -> walk(child, char, null, target, firstRow, null, maxEdits, matches) }
        return matches
            .sortedWith(compareBy<WordMatch> { it.edits }.thenByDescending { it.entry.frequency })
            .take(limit)
    }

    override fun entries(): Sequence<WordEntry> = sequence {
        val stack = ArrayDeque(listOf(root))
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.entry?.let { yield(it) }
            stack.addAll(node.children.values)
        }
    }

    private fun walk(
        node: Node,
        char: Char,
        previousChar: Char?,
        target: String,
        previousRow: IntArray,
        rowBeforePrevious: IntArray?,
        maxEdits: Int,
        out: MutableList<WordMatch>,
    ) {
        val row = IntArray(target.length + 1)
        row[0] = previousRow[0] + 1
        for (i in 1..target.length) {
            val substitution = previousRow[i - 1] + if (target[i - 1] == char) 0 else 1
            var best = minOf(row[i - 1] + 1, previousRow[i] + 1, substitution)
            if (rowBeforePrevious != null && i > 1 && target[i - 1] == previousChar && target[i - 2] == char) {
                best = minOf(best, rowBeforePrevious[i - 2] + 1)
            }
            row[i] = best
        }
        val distance = row[target.length]
        node.entry?.let { if (distance <= maxEdits) out += WordMatch(it, distance) }
        if (row.min() <= maxEdits) {
            node.children.forEach { (next, child) -> walk(child, next, char, target, row, previousRow, maxEdits, out) }
        }
    }

    private fun find(key: String): Node? {
        var node = root
        key.forEach { char -> node = node.children[char] ?: return null }
        return node
    }

    private fun collect(node: Node, out: MutableList<WordEntry>) {
        node.entry?.let(out::add)
        node.children.values.forEach { collect(it, out) }
    }
}

/**
 * Words the user typed. Learning never happens in incognito fields; that gate lives in the
 * input engine, which is the only caller of [learn].
 */
class UserDictionary(languageTag: String) : TrieDictionary(languageTag), MutableDictionary {
    override fun learn(word: String) {
        if (word.isBlank()) return
        val current = lookup(word)?.frequency ?: LEARNED_BASE_FREQUENCY
        insert(WordEntry(word, (current + 1).coerceAtMost(MAX_FREQUENCY)))
    }

    override fun forget(word: String) = remove(word)

    private companion object {
        const val LEARNED_BASE_FREQUENCY = 120
        const val MAX_FREQUENCY = 255
    }
}
