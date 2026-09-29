package com.makeeb.tools.dictionaries.harness

import com.makeeb.core.model.Suggestion
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.SelectedDictionaries
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.platform.storage.ByteRegion
import java.io.File
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * What mapping two or three packs costs the keyboard, measured on the JVM
 * (`./gradlew :tools:dictionaries:packMemory`): the heap each mapped pack keeps (the iOS
 * extension's budget counts heap, not the clean pages of a mapping), and the cost of a keystroke
 * when the other selected languages vouch for words. Arguments: the pack files, primary first.
 *
 * Heap is measured as used heap after repeated full GCs, so it is approximate to a few KB. Heap
 * on Kotlin/Native differs in detail (object headers, strings), not in what is kept.
 */
fun main(args: Array<String>) {
    val files = args.map(::File)
    require(files.isNotEmpty() && files.all { it.isFile }) { "usage: PackMemory <pack.mkd>…, primary first" }
    val regions = ArrayList<ByteRegion>()
    val dictionaries = ArrayList<MappedDictionary>()
    val baseline = usedHeap()
    for (file in files) {
        val before = usedHeap()
        val region = map(file)
        val dictionary = MappedDictionary(region)
        dictionary.warmUp()
        regions += region
        dictionaries += dictionary
        println(
            "%-10s %,11d bytes mapped, %,8d words: %,7d bytes of heap once opened and warmed".format(
                file.name, region.size, dictionary.wordCount, usedHeap() - before,
            ),
        )
    }
    val primary = dictionaries.first()
    val selected: Dictionary = if (dictionaries.size == 1) primary else SelectedDictionaries(primary, dictionaries.drop(1))
    val words = (0 until 2_000).map { primary.pack.wordText((it * 37) % minOf(20_000, primary.wordCount)) }
    val keys = words.sumOf { it.length }
    // Every prefix of 2,000 common words, then the predictions after each. The configurations take
    // turns over several rounds and each keeps its best, so JIT and GC timing don't favour one.
    val configurations = listOf("${files.first().name} alone" to primary, "with the others vouching" to selected)
    val engines = configurations.map { (_, dictionary) -> DictionarySuggestionEngine(dictionary, UserDictionary(dictionary.languageTag)) }
    val best = LongArray(engines.size) { Long.MAX_VALUE }
    repeat(ROUNDS) {
        engines.forEachIndexed { i, engine -> best[i] = minOf(best[i], typeAll(engine, words)) }
    }
    configurations.forEachIndexed { i, (label, _) ->
        println("%-26s %.1f µs per key over %,d keys (suggestions with corrections, next words after each word)".format(label, best[i] / 1000.0 / keys, keys))
    }
    println("all ${files.size} mapped: %,d bytes of heap over the baseline after typing".format(usedHeap() - baseline))
    // Keep everything reachable until measured.
    check(regions.size == dictionaries.size && selected.lookup(words.first()) != null)
}

private const val ROUNDS = 6

private fun typeAll(engine: DictionarySuggestionEngine, words: List<String>): Long {
    var sink = 0
    val start = System.nanoTime()
    for (word in words) {
        for (end in 1..word.length) {
            val prediction = engine.suggest(TypingContext(word.substring(0, end), languages = emptyList()))
            sink += prediction.suggestions.size
        }
        sink += engine.suggest(TypingContext("", previousWords = listOf(word))).suggestions.count { it.kind == Suggestion.Kind.NextWord }
    }
    check(sink >= 0)
    return System.nanoTime() - start
}

private fun usedHeap(): Long {
    val memory = ManagementFactory.getMemoryMXBean()
    repeat(4) {
        System.gc()
        Thread.sleep(50)
    }
    return memory.heapMemoryUsage.used
}

/** A read-only mapping, as the keyboard makes on either platform (here with java.nio, as Android does). */
private fun map(file: File): ByteRegion = RandomAccessFile(file, "r").use { raf ->
    MappedRegion(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()))
}

private class MappedRegion(buffer: MappedByteBuffer) : ByteRegion {
    private val buffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    override val size: Int = this.buffer.limit()

    override fun u8(offset: Int): Int = buffer.get(offset).toInt() and 0xFF

    override fun u16(offset: Int): Int = buffer.getShort(offset).toInt() and 0xFFFF

    override fun i32(offset: Int): Int = buffer.getInt(offset)
}
