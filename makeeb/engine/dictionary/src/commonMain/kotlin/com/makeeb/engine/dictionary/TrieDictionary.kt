package com.makeeb.engine.dictionary

/**
 * An in-memory trie supporting prefix completion and bounded edit-distance search: a trie walk
 * computing one row of the optimal-string-alignment (Damerau) distance per node, so an adjacent
 * transposition ("teh" → "the") costs one edit. Branches are pruned once they cannot get back
 * under the limit.
 *
 * Fine for the bundled starter lists and tests. Full language dictionaries are memory-mapped
 * ([MappedDictionary]), and learned words live in the smaller [UserDictionary], to fit the iOS
 * extension's memory budget.
 */
open class TrieDictionary(
    override val languageTag: String,
    entries: Iterable<WordEntry> = emptyList(),
) : Dictionary {
    /** Keys are [KeyFold]ed, so one node can hold several spellings ("naive", "naïve"). */
    private class Node {
        val children = HashMap<Char, Node>()
        val entries = ArrayList<WordEntry>(1)
    }

    private val root = Node()

    init {
        entries.forEach(::insert)
    }

    /** Adds [entry], or updates the frequency of the same spelling. */
    private fun insert(entry: WordEntry) {
        var node = root
        KeyFold.fold(entry.word).forEach { char -> node = node.children.getOrPut(char) { Node() } }
        val same = node.entries.indexOfFirst { it.word == entry.word }
        if (same >= 0) node.entries[same] = entry else node.entries += entry
        node.entries.sortByDescending { it.frequency }
    }

    /** The spelling typed exactly when it exists, otherwise the most frequent one under the key. */
    override fun lookup(word: String): WordEntry? {
        val entries = find(KeyFold.fold(word))?.entries ?: return null
        return entries.firstOrNull { it.word == word } ?: entries.firstOrNull()
    }

    override fun completions(prefix: String, limit: Int): List<WordEntry> {
        val start = find(KeyFold.fold(prefix)) ?: return emptyList()
        val found = mutableListOf<WordEntry>()
        collect(start, found)
        return found.sortedByDescending { it.frequency }.take(limit)
    }

    override fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch> {
        val target = KeyFold.fold(word)
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
            yieldAll(node.entries)
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
        val row = nextDistanceRow(target, previousRow, rowBeforePrevious, char, previousChar)
        val distance = row[target.length]
        if (distance <= maxEdits) node.entries.forEach { out += WordMatch(it, distance) }
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
        out += node.entries
        node.children.values.forEach { collect(it, out) }
    }
}

/**
 * One step of the optimal-string-alignment distance from [target]: the row for a key one letter
 * ([char]) longer than the key [previousRow] is for. [rowBeforePrevious] and [previousChar] (null
 * for the first letter) let an adjacent transposition cost one edit. Walks that share prefixes
 * (a trie's, [UserDictionary]'s sorted keys) compute each prefix's row once.
 */
internal fun nextDistanceRow(target: String, previousRow: IntArray, rowBeforePrevious: IntArray?, char: Char, previousChar: Char?): IntArray {
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
    return row
}
