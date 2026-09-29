package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.pack.MkdNgramTable
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
 *                       [--heldout <directory>] [--ngrams false]
 *
 * Each source is fetched once, checked against its pinned SHA-256 and cached. Every pack is
 * read back with the keyboard's own reader and checked before it is written.
 *
 * Next-word statistics come from the pack's corpora: the lexicon is written first, the corpora
 * are tokenised with it (so its ids and spellings are the ones counted), and the counts are
 * pruned into an NGRM section. The counts are cached next to the downloads, keyed by everything
 * they depend on, so rebuilding a pack after a code change doesn't recount. Sentences held out of
 * the counts go to `--heldout` for the typing harness. `--ngrams false` builds packs without
 * next-word data (no corpus download).
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key to value }
    val out = File(options["--out"] ?: usage())
    val cache = File(options["--cache"] ?: usage())
    val heldOut = options["--heldout"]?.let(::File)
    val ngrams = options["--ngrams"] != "false"
    out.mkdirs()
    BundledPacks.all.forEach { build(it, out, cache, heldOut, ngrams) }
}

private fun usage(): Nothing {
    System.err.println("usage: DictionaryBuilder --out <pack directory> --cache <download directory> [--heldout <directory>] [--ngrams false]")
    exitProcess(2)
}

/** The packs that ship inside the apps. Downloadable packs come later (board: APP-37). */
object BundledPacks {
    /** AOSP LatinIME, the commit that last changed the en_US list (2014-10-31, "possibly_offensive flag"). */
    const val AOSP_COMMIT = "8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6"
    private const val AOSP_FILE = "dictionaries/en_US_wordlist.combined.gz"

    private fun leipzig(name: String, sha256: String) = LeipzigCorpus(
        name,
        PinnedSource("leipzig-$name.tar.gz", sha256, listOf(PinnedSource.Mirror("https://downloads.wortschatz-leipzig.de/corpora/$name.tar.gz"))),
    )

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
        // English news (2024) and .com web pages (2018), 1M sentences each: about 42M words.
        ngramCorpora = listOf(
            leipzig("eng_news_2024_1M", "8f1d4d07b9771f8a7fc219ad587d5382eabf5009ef563f1bc4c12a467a8e3a97"),
            leipzig("eng-com_web-public_2018_1M", "de8849fe30c7d5bf3502f620093232f4dceca10a6ac19a9ad49f474b62df0a5f"),
        ),
        ngramLicence = "CC-BY-4.0",
        ngramAttribution = "Next-word statistics counted from the Leipzig Corpora Collection (eng_news_2024_1M, " +
            "eng-com_web-public_2018_1M), Wortschatz Leipzig, Leipzig University, licensed under CC BY 4.0 " +
            "(https://creativecommons.org/licenses/by/4.0/). Counted over the word list, pruned and quantised by MaKeeb. " +
            "Goldhahn, Eckart & Quasthoff (2012): Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: " +
            "From 100 to 200 Languages. LREC 2012.",
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
    val ngramCorpora: List<LeipzigCorpus> = emptyList(),
    val ngramLicence: String = "",
    val ngramAttribution: String = "",
    val pruning: NgramPruning = NgramPruning(),
)

/**
 * Bump when tokenising or counting changes, so cached counts are rebuilt. Pruning isn't part of
 * it: it runs on the cached counts every build.
 */
private const val NGRAM_RECIPE = "leipzig-v1: CorpusTokens + MappedDictionary.wordId; bigrams and trigrams seen twice; 1 in 250 held out"

/** Sentences whose corpus id is a multiple of this are held out of the counts (0.4%). */
private const val HELD_OUT_EVERY = 250L

private fun build(spec: PackSpec, out: File, cache: File, heldOutDirectory: File?, withNgrams: Boolean) {
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
    val lexiconOnly = MkdWriter.write(list.words, meta)
    val ngrams = if (withNgrams && spec.ngramCorpora.isNotEmpty()) ngramTable(spec, lexiconOnly, cache, heldOutDirectory) else null
    if (ngrams != null) {
        meta["licence"] = "${spec.licence} AND ${spec.ngramLicence}"
        meta["ngramSource"] = "Leipzig Corpora Collection " + spec.ngramCorpora.joinToString { it.name } +
            " (1 in $HELD_OUT_EVERY sentences held out)"
        meta["ngramSha256"] = spec.ngramCorpora.joinToString(",") { it.source.sha256 }
        meta["ngramLicence"] = spec.ngramLicence
        meta["ngramAttribution"] = spec.ngramAttribution
    }
    val bytes = MkdWriter.write(list.words, meta, ngrams)
    verify(spec, bytes, list, lexiconOnly, ngrams != null)
    val target = File(out, spec.fileName)
    val partial = File(out, spec.fileName + ".part")
    partial.writeBytes(bytes)
    check(partial.renameTo(target) || (target.delete() && partial.renameTo(target))) { "could not write $target" }

    val pack = MkdPack.open(ByteArrayRegion(bytes))
    val sections = pack.sectionSizes.entries.joinToString { (id, size) -> "$id ${kib(size)}" }
    println(
        "${spec.fileName}: ${pack.wordCount} words (${list.words.count { it.offensive }} offensive, " +
            "${list.words.count { it.text != it.text.lowercase() }} cased; skipped ${list.skippedNotAWord} not-a-word, " +
            "${list.skippedShortcuts} shortcuts, ${list.skippedBigrams} bigrams)" +
            (ngrams?.let { ", ${it.bigramCount} bigrams, ${it.trigramCount} trigrams" } ?: "") +
            ", ${kib(bytes.size)} [$sections], built in ${started.elapsedNow().inWholeMilliseconds} ms",
    )
}

/**
 * The pack's next-word table: counts from the cache, or from the corpora (fetched first), then
 * pruned. Writes the held-out sentences to [heldOutDirectory] as `<language>-heldout.txt`.
 */
private fun ngramTable(spec: PackSpec, lexiconOnly: ByteArray, cache: File, heldOutDirectory: File?): MkdNgramTable {
    val lexicon = MappedDictionary(ByteArrayRegion(lexiconOnly))
    val key = PinnedSource.sha256Of(
        (NGRAM_RECIPE + "\n" + spec.ngramCorpora.joinToString("\n") { it.source.sha256 } + "\n" + PinnedSource.sha256Of(lexiconOnly)).encodeToByteArray(),
    ).take(16)
    val countsFile = File(cache, "ngram-counts-$key.bin")
    val heldOutFile = File(cache, "ngram-heldout-$key.txt")
    val started = TimeSource.Monotonic.markNow()
    var counts = if (countsFile.isFile && heldOutFile.isFile) NgramCounts.read(countsFile) else null
    if (counts == null) {
        val corpora = spec.ngramCorpora.map { it.source.fetch(cache) { message -> println(message) } }
        val heldOut = StringBuilder()
        counts = NgramCounts.count(corpora, lexicon, heldOut = { it % HELD_OUT_EVERY == 0L }) { heldOut.append(it).append('\n') }
        counts.write(countsFile)
        heldOutFile.writeText(heldOut.toString())
        println(
            "counted ${spec.ngramCorpora.joinToString { it.name }}: ${counts.sentences} sentences, ${counts.tokens} words, " +
                "%.1f%% known; ${counts.distinctBigrams} bigrams and ${counts.distinctTrigrams} trigrams seen twice or more, in %d ms"
                    .format(100.0 * counts.knownTokens / counts.tokens, started.elapsedNow().inWholeMilliseconds),
        )
    }
    if (heldOutDirectory != null) {
        heldOutDirectory.mkdirs()
        heldOutFile.copyTo(File(heldOutDirectory, spec.fileName.substringBefore('.') + "-heldout.txt"), overwrite = true)
    }
    return counts.prune(spec.pruning) { lexicon.pack.isOffensive(it) }
}

/** Reads the pack back through the keyboard's reader and fails the build on anything unexpected. */
private fun verify(spec: PackSpec, bytes: ByteArray, list: AospWordList, lexiconOnly: ByteArray, withNgrams: Boolean) {
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
    if (!withNgrams) return

    // The n-grams were counted with the lexicon-only pack's ids: they must be the same words.
    val lexicon = MkdPack.open(ByteArrayRegion(lexiconOnly))
    expect((0 until pack.wordCount).all { pack.wordText(it) == lexicon.wordText(it) }, "word ids match the counted lexicon")
    val model = dictionary.nextWords
    expect(model != null, "next-word statistics present")
    fun predicts(previous: List<String>, word: String, fromSentenceStart: Boolean = false) =
        model!!.predict(previous, fromSentenceStart, 3).any { it.word == word }
    expect(predicts(listOf("thank"), "you"), "thank → you")
    expect(predicts(listOf("one", "of"), "the"), "one of → the")
    expect(predicts(listOf("I"), "have", fromSentenceStart = true) || predicts(listOf("I"), "am", fromSentenceStart = true), "I → have/am")
    val offensiveIds = (0 until pack.wordCount).filter { pack.isOffensive(it) }.toSet()
    expect(listOf(listOf("you"), listOf("what", "the"), listOf("son", "of"), emptyList()).all { context ->
        model!!.predict(context, false, 48).none { dictionary.wordId(it.word) in offensiveIds }
    }, "offensive words are never predicted")
    expect(bytes.size <= MAX_PACK_BYTES, "pack over ${MAX_PACK_BYTES / 1_000_000} MB")
}

/** The bundled English pack's budget (docs/research/dictionaries-autocorrect.md §10.5). */
private const val MAX_PACK_BYTES = 10_000_000

private fun kib(bytes: Int) = "%.1f KiB".format(bytes / 1024.0)
