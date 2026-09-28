package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess
import kotlin.time.TimeSource

/**
 * Builds the packs the apps bundle. Run through Gradle (`./gradlew :tools:dictionaries:dictionaryPacks`):
 *
 *     DictionaryBuilder --out <pack directory> --cache <download directory>
 *
 * Each source is fetched once, checked against its pinned SHA-256 and cached. Every pack is
 * read back with the keyboard's own reader and checked before it is written.
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key to value }
    val out = File(options["--out"] ?: usage())
    val cache = File(options["--cache"] ?: usage())
    out.mkdirs()
    BundledPacks.all.forEach { build(it, out, cache) }
}

private fun usage(): Nothing {
    System.err.println("usage: DictionaryBuilder --out <pack directory> --cache <download directory>")
    exitProcess(2)
}

/** The packs that ship inside the apps. Downloadable packs come later (board: APP-37). */
object BundledPacks {
    /** AOSP LatinIME, the commit that last changed the en_US list (2014-10-31, "possibly_offensive flag"). */
    const val AOSP_COMMIT = "8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6"
    private const val AOSP_FILE = "dictionaries/en_US_wordlist.combined.gz"

    val englishUs = PackSpec(
        fileName = "en_US.mkd",
        displayName = "English (US)",
        source = PinnedSource(
            fileName = "aosp-$AOSP_COMMIT-en_US_wordlist.combined.gz",
            sha256 = "0f78dd455b532be169a23f233227b811fabced4b5bd7fc9c40cc05839793bcbd",
            mirrors = listOf(
                PinnedSource.Mirror(
                    "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/$AOSP_COMMIT/$AOSP_FILE?format=TEXT",
                    base64 = true,
                ),
                // LineageOS mirrors AOSP with the same commits; the hash pins the bytes either way.
                PinnedSource.Mirror("https://raw.githubusercontent.com/LineageOS/android_packages_inputmethods_LatinIME/$AOSP_COMMIT/$AOSP_FILE"),
            ),
        ),
        sourceDescription = "AOSP LatinIME $AOSP_FILE at $AOSP_COMMIT",
        licence = "Apache-2.0",
        attribution = "Word list from the Android Open Source Project (LatinIME), Copyright (C) The Android Open Source Project, " +
            "licensed under the Apache License 2.0. Converted to MKD by MaKeeb.",
    )

    val all = listOf(englishUs)
}

class PackSpec(
    val fileName: String,
    val displayName: String,
    val source: PinnedSource,
    val sourceDescription: String,
    val licence: String,
    val attribution: String,
)

private fun build(spec: PackSpec, out: File, cache: File) {
    val started = TimeSource.Monotonic.markNow()
    val gz = spec.source.fetch(cache) { println(it) }
    val list = GZIPInputStream(gz.inputStream()).bufferedReader().useLines { AospWordList.parse(it) }
    val meta = linkedMapOf(
        "language" to list.languageTag,
        "name" to spec.displayName,
        "source" to spec.sourceDescription,
        "sourceSha256" to spec.source.sha256,
        "sourceVersion" to (list.header["version"] ?: "?"),
        "licence" to spec.licence,
        "attribution" to spec.attribution,
    )
    val bytes = MkdWriter.write(list.words, meta)
    verify(spec, bytes, list)
    val target = File(out, spec.fileName)
    val partial = File(out, spec.fileName + ".part")
    partial.writeBytes(bytes)
    check(partial.renameTo(target) || (target.delete() && partial.renameTo(target))) { "could not write $target" }

    val pack = MkdPack.open(ByteArrayRegion(bytes))
    val sections = pack.sectionSizes.entries.joinToString { (id, size) -> "$id ${kib(size)}" }
    println(
        "${spec.fileName}: ${pack.wordCount} words (${list.words.count { it.offensive }} offensive, " +
            "${list.words.count { it.text != it.text.lowercase() }} cased; skipped ${list.skippedNotAWord} not-a-word, " +
            "${list.skippedShortcuts} shortcuts, ${list.skippedBigrams} bigrams), ${kib(bytes.size)} [$sections], " +
            "built in ${started.elapsedNow().inWholeMilliseconds} ms",
    )
}

/** Reads the pack back through the keyboard's reader and fails the build on anything unexpected. */
private fun verify(spec: PackSpec, bytes: ByteArray, list: AospWordList) {
    val pack = MkdPack.open(ByteArrayRegion(bytes))
    val dictionary = MappedDictionary(pack)
    fun expect(condition: Boolean, what: String) = check(condition) { "${spec.fileName}: $what" }
    expect(pack.checksumMatches(), "checksum")
    expect(pack.wordCount == list.words.map { it.text }.toSet().size, "word count")
    for (word in listOf("the", "cat", "kitchen", "London", "don't")) {
        expect(dictionary.lookup(word)?.word == word, "lookup $word")
    }
    expect(dictionary.completions("kitch", 3).any { it.word == "kitchen" }, "kitch completes to kitchen")
    // AOSP's not-a-word "im" is dropped; its folded key finds the real word.
    expect(dictionary.lookup("im")?.word == "I'm", "not-a-word entries are left out; im finds I'm")
    expect(dictionary.lookup("dont")?.word == "don't", "keys fold apostrophes")
    val offensive = list.words.first { it.offensive }
    expect(dictionary.lookup(offensive.text) != null, "offensive words stay known")
    expect(dictionary.completions(offensive.text, 50).none { it.word == offensive.text }, "offensive words are not offered")
}

private fun kib(bytes: Int) = "%.1f KiB".format(bytes / 1024.0)
