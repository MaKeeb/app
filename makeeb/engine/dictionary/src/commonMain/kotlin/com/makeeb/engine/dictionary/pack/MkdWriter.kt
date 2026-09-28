package com.makeeb.engine.dictionary.pack

/** A word going into a pack, in its canonical form ("London", "don't", "naïve"). */
data class MkdWord(val text: String, val frequency: Int, val offensive: Boolean = false)

/**
 * Writes MKD v1 packs ([MkdFormat] has the layout). The build-time tool and the tests share it,
 * so writer and reader are one codec. It holds the whole trie on the heap: build time only, never
 * in the keyboard.
 *
 * The output depends only on the input (no timestamps), so the same word list always builds the
 * same bytes.
 */
object MkdWriter {
    fun write(words: Iterable<MkdWord>, meta: Map<String, String>): ByteArray {
        require(meta["language"]?.isNotBlank() == true) { "meta needs a language" }
        val table = wordTable(words)
        val allMeta = LinkedHashMap(meta).apply {
            put("keyFold", MkdFormat.FOLD_LOWERCASE)
            put("words", table.size.toString())
        }
        val sections = listOf(
            MkdFormat.META to metaSection(allMeta),
            MkdFormat.LEXI to lexiconSection(table),
            MkdFormat.WORD to wordSection(table),
        )
        return assemble(sections)
    }

    /** Merges duplicate spellings and orders by descending frequency (stable), which fixes the ids. */
    private fun wordTable(words: Iterable<MkdWord>): List<MkdWord> {
        val merged = LinkedHashMap<String, MkdWord>()
        for (word in words) {
            require(word.text.isNotEmpty()) { "empty word" }
            require(word.frequency in 0..255) { "frequency of '${word.text}' out of range" }
            require(word.text.encodeToByteArray().size <= MkdFormat.MAX_WORD_BYTES) { "'${word.text}' is too long" }
            require(codePoints(MkdFormat.fold(word.text)).all { it >= 0x20 }) { "control character in '${word.text}'" }
            val existing = merged[word.text]
            merged[word.text] = if (existing == null) word
            else MkdWord(word.text, maxOf(existing.frequency, word.frequency), existing.offensive || word.offensive)
        }
        require(merged.size <= MkdFormat.MAX_U24 + 1) { "too many words" }
        return merged.values.sortedByDescending { it.frequency }
    }

    private fun metaSection(meta: Map<String, String>): ByteArray = buildString {
        meta.forEach { (key, value) ->
            require(key.isNotEmpty() && '=' !in key && '\n' !in key && '\n' !in value) { "bad meta entry '$key'" }
            append(key).append('=').append(value).append('\n')
        }
    }.encodeToByteArray()

    private fun wordSection(table: List<MkdWord>): ByteArray {
        val out = ByteWriter()
        out.u32(table.size)
        val indexAt = out.size
        repeat(table.size) { out.u24(0) }
        table.forEachIndexed { id, word ->
            val offset = out.size
            require(offset <= MkdFormat.MAX_U24) { "word table over 16 MB" }
            out.patchU24(indexAt + id * 3, offset)
            val bytes = word.text.encodeToByteArray()
            var flags = 0
            if (word.offensive) flags = flags or MkdFormat.WORD_OFFENSIVE
            if (MkdFormat.fold(word.text) != word.text) flags = flags or MkdFormat.WORD_CASED
            out.u8(word.frequency)
            out.u8(flags)
            out.u8(bytes.size)
            out.bytes(bytes)
        }
        return out.toByteArray()
    }

    private class CharNode {
        val children = HashMap<Int, CharNode>()
        val words = ArrayList<Int>()
    }

    private class Node(val label: IntArray, val words: IntArray, val frequency: Int, val children: List<Node>) {
        val best: Int = maxOf(if (words.isEmpty()) -1 else frequency, children.maxOfOrNull { it.best } ?: -1)
        var offset = 0
        var childArray = -1
        var childWidth = if (children.isEmpty()) 0 else 3

        val size: Int
            get() {
                var bytes = 2 + label.sumOf { if (it in 0x20..0xFF) 1 else 3 } + (if (label.size > 1) 1 else 0)
                if (words.isNotEmpty()) bytes += if (words.size == 1) 4 else 2 + 3 * words.size
                return bytes + childWidth
            }
    }

    private fun lexiconSection(table: List<MkdWord>): ByteArray {
        val root = CharNode()
        table.forEachIndexed { id, word ->
            var node = root
            codePoints(MkdFormat.fold(word.text)).forEach { node = node.children.getOrPut(it) { CharNode() } }
            node.words += id // ids ascend, so each node's words run most frequent first
        }
        fun radix(first: Int, start: CharNode): Node {
            val label = mutableListOf(first)
            var node = start
            while (node.words.isEmpty() && node.children.size == 1) {
                val (next, child) = node.children.entries.single()
                label += next
                node = child
            }
            require(node.words.size <= 255) { "more than 255 spellings of one key" }
            val children = node.children.map { (next, child) -> radix(next, child) }
                .sortedWith(compareByDescending<Node> { it.best }.thenBy { it.label[0] })
            val frequency = node.words.firstOrNull()?.let { table[it].frequency } ?: 0
            return Node(label.toIntArray(), node.words.toIntArray(), frequency, children)
        }
        val rootArray = root.children.map { (first, child) -> radix(first, child) }
            .sortedWith(compareByDescending<Node> { it.best }.thenBy { it.label[0] })

        // Breadth-first: arrays in the order they are discovered level by level.
        val arrays = mutableListOf(rootArray)
        var index = 0
        while (index < arrays.size) {
            arrays[index].forEach { node ->
                if (node.children.isNotEmpty()) {
                    node.childArray = arrays.size
                    arrays += node.children
                }
            }
            index++
        }
        arrays.forEach { require(it.size <= 0x7FFF) { "node array too large" } }

        // Child offsets start 3 bytes wide and shrink until the layout stops moving. Children
        // always follow their parent, so shrinking any node never lengthens an offset.
        val arrayOffsets = IntArray(arrays.size)
        while (true) {
            var position = 0
            arrays.forEachIndexed { i, array ->
                arrayOffsets[i] = position
                position += if (array.size < 0x80) 1 else 2
                array.forEach { node ->
                    node.offset = position
                    position += node.size
                }
            }
            var changed = false
            arrays.forEach { array ->
                array.forEach { node ->
                    if (node.childArray >= 0) {
                        val width = widthOf(arrayOffsets[node.childArray] - node.offset)
                        if (width != node.childWidth) {
                            node.childWidth = width
                            changed = true
                        }
                    }
                }
            }
            if (!changed) break
        }

        val out = ByteWriter()
        arrays.forEachIndexed { i, array ->
            check(out.size == arrayOffsets[i])
            if (array.size < 0x80) out.u8(array.size) else { out.u8(0x80 or (array.size shr 8)); out.u8(array.size and 0xFF) }
            array.forEach { node ->
                check(out.size == node.offset)
                var flags = node.childWidth
                if (node.words.isNotEmpty()) flags = flags or MkdFormat.NODE_TERMINAL
                if (node.label.size > 1) flags = flags or MkdFormat.NODE_MULTI_CHAR
                if (node.words.size > 1) flags = flags or MkdFormat.NODE_VARIANTS
                out.u8(flags)
                out.u8(node.best)
                node.label.forEach { cp ->
                    if (cp in 0x20..0xFF) out.u8(cp)
                    else { out.u8(cp shr 16); out.u8((cp shr 8) and 0xFF); out.u8(cp and 0xFF) }
                }
                if (node.label.size > 1) out.u8(MkdFormat.LABEL_END)
                if (node.words.isNotEmpty()) {
                    out.u8(node.frequency)
                    if (node.words.size > 1) out.u8(node.words.size)
                    node.words.forEach(out::u24)
                }
                if (node.childArray >= 0) {
                    val distance = arrayOffsets[node.childArray] - node.offset
                    repeat(node.childWidth) { byte -> out.u8((distance shr (8 * byte)) and 0xFF) }
                }
            }
        }
        return out.toByteArray()
    }

    private fun widthOf(distance: Int): Int = when {
        distance < 0x100 -> 1
        distance < 0x10000 -> 2
        distance <= MkdFormat.MAX_U24 -> 3
        else -> error("trie over 16 MB")
    }

    private fun assemble(sections: List<Pair<Int, ByteArray>>): ByteArray {
        val out = ByteWriter()
        out.u32(MkdFormat.MAGIC)
        out.u16(MkdFormat.MAJOR_VERSION)
        out.u16(MkdFormat.MINOR_VERSION)
        out.u32(0) // CRC, patched below
        out.u16(sections.size)
        out.u16(0)
        val tableAt = out.size
        repeat(sections.size) { repeat(MkdFormat.SECTION_ENTRY_SIZE) { out.u8(0) } }
        sections.forEachIndexed { i, (id, bytes) ->
            while (out.size % 4 != 0) out.u8(0)
            val entry = tableAt + i * MkdFormat.SECTION_ENTRY_SIZE
            out.patchU32(entry, id)
            out.patchU32(entry + 4, out.size)
            out.patchU32(entry + 8, bytes.size)
            out.bytes(bytes)
        }
        val result = out.toByteArray()
        val crc = Crc32.of(result, MkdFormat.CRC_START, result.size)
        for (byte in 0 until 4) result[8 + byte] = (crc shr (8 * byte)).toByte()
        return result
    }
}

/** A growable little-endian byte buffer. */
private class ByteWriter {
    private var buffer = ByteArray(1024)
    var size = 0
        private set

    fun u8(value: Int) {
        if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[size++] = value.toByte()
    }

    fun u16(value: Int) = repeat(2) { u8(value shr (8 * it)) }

    fun u24(value: Int) = repeat(3) { u8(value shr (8 * it)) }

    fun u32(value: Int) = repeat(4) { u8(value shr (8 * it)) }

    fun bytes(bytes: ByteArray) = bytes.forEach { u8(it.toInt()) }

    fun patchU24(at: Int, value: Int) = repeat(3) { buffer[at + it] = (value shr (8 * it)).toByte() }

    fun patchU32(at: Int, value: Int) = repeat(4) { buffer[at + it] = (value shr (8 * it)).toByte() }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
