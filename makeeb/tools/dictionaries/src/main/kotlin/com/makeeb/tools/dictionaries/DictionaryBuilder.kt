package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.pack.MkdNgramTable
import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess
import kotlin.time.TimeSource

/**
 * Builds dictionary packs. Run through Gradle (`./gradlew :tools:dictionaries:dictionaryPacks`
 * for the bundled English pack, `packRelease` for every language):
 *
 *     DictionaryBuilder --out <pack directory> --cache <download directory>
 *                       [--packs bundled|downloadable|<stem,…>] [--heldout <directory>] [--ngrams false]
 *                       [--english auto|download|build] [--catalogue-url <url>]
 *
 * Each source is fetched once, checked against its pinned SHA-256 and cached. Every pack is
 * read back with the keyboard's own reader and checked before it is written.
 *
 * English is bundled in the apps, so every app build needs it. Once the packs are published
 * (`--catalogue-url`, the Gradle property `makeeb.packs.catalogueUrl`), `--english auto` takes
 * the published en_US.mkd pinned by [PackSpecs.ENGLISH_US_RELEASE_SHA256], a 6.6 MB download,
 * and builds it from its sources only if that fails; `download` insists on the download, `build`
 * always builds.
 *
 * Next-word statistics come from the pack's corpora: the lexicon is written first, the corpora
 * are tokenised with it (so its ids and spellings are the ones counted), and the counts are
 * pruned into an NGRM section. The counts are cached next to the downloads, keyed by everything
 * they depend on, so rebuilding a pack after a code change doesn't recount. Sentences held out of
 * the counts go to `--heldout` for the typing harness. `--ngrams false` builds packs without
 * next-word data (no corpus download, except for word lists counted from a corpus).
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key to value }
    val out = File(options["--out"] ?: usage())
    val cache = File(options["--cache"] ?: usage())
    val heldOut = options["--heldout"]?.let(::File)
    val ngrams = options["--ngrams"] != "false"
    val packs = when (val which = options["--packs"] ?: "bundled") {
        "bundled" -> PackSpecs.bundled
        "downloadable" -> PackSpecs.downloadable
        // A comma-separated list of stems ("hu,sv"), to rebuild a few while tuning.
        else -> which.split(',').map { stem -> PackSpecs.all.firstOrNull { it.stem == stem } ?: usage() }
    }
    val english = options["--english"] ?: "auto"
    if (english !in setOf("auto", "download", "build")) usage()
    val catalogueUrl = options["--catalogue-url"].orEmpty()
    out.mkdirs()
    for (spec in packs) {
        if (spec === PackSpecs.englishUs && english != "build" && downloadEnglish(catalogueUrl, out, cache, required = english == "download")) continue
        buildPack(spec, out, cache, heldOut, ngrams)
    }
}

/**
 * Copies the published en_US.mkd into [out], downloading it first unless the cache has it.
 * False when there is nothing published to take ([catalogueUrl] unset) or the download fails,
 * unless [required].
 */
private fun downloadEnglish(catalogueUrl: String, out: File, cache: File, required: Boolean): Boolean {
    if (catalogueUrl.isEmpty()) {
        if (required) error("--english download needs --catalogue-url (the makeeb.packs.catalogueUrl property)")
        return false
    }
    val file = try {
        PackSpecs.englishUsRelease(catalogueUrl).fetch(cache) { println(it) }
    } catch (e: IOException) {
        if (required) throw e
        println("the published en_US.mkd isn't available (${e.message?.lineSequence()?.first()}); building it instead")
        return false
    }
    val target = File(out, PackSpecs.englishUs.fileName)
    file.copyTo(File(out, target.name + ".part"), overwrite = true)
    check(File(out, target.name + ".part").renameTo(target)) { "could not write $target" }
    println("${target.name}: the published pack, ${file.length()} bytes, SHA-256 ${PackSpecs.ENGLISH_US_RELEASE_SHA256}")
    return true
}

private fun usage(): Nothing {
    System.err.println(
        "usage: DictionaryBuilder --out <pack directory> --cache <download directory> " +
            "[--packs bundled|downloadable|<stem,…>] [--heldout <directory>] [--ngrams false] " +
            "[--english auto|download|build] [--catalogue-url <url>]",
    )
    exitProcess(2)
}

/**
 * Bump when tokenising or counting changes, so cached counts are rebuilt. Pruning isn't part of
 * it: it runs on the cached counts every build.
 */
private const val NGRAM_RECIPE = "leipzig-v1: CorpusTokens + MappedDictionary.wordId; bigrams and trigrams seen twice; 1 in 250 held out"

/** Sentences whose corpus id is a multiple of this are held out of the counts (0.4%). */
const val HELD_OUT_EVERY = 250L

/** A pack's words, whichever source they came from. */
private class WordList(val languageTag: String, val version: String, val words: List<MkdWord>, val summary: String)

private fun wordList(spec: PackSpec, cache: File): WordList = when (val lexicon = spec.lexicon) {
    is AospLexicon -> {
        val gz = lexicon.source.fetch(cache) { println(it) }
        val list = GZIPInputStream(gz.inputStream()).bufferedReader().useLines { AospWordList.parse(it) }
        WordList(
            list.languageTag,
            list.header["version"] ?: "?",
            list.words,
            "skipped ${list.skippedNotAWord} not-a-word, ${list.skippedShortcuts} shortcuts, ${list.skippedBigrams} bigrams",
        )
    }
    is CorpusLexicon -> {
        val recipe = lexicon.recipe
        val key = PinnedSource.sha256Of("$recipe\n${lexicon.corpus.source.sha256}\n$HELD_OUT_EVERY".encodeToByteArray()).take(16)
        val cached = File(cache, "corpus-words-${spec.stem}-$key.tsv")
        val list = (if (cached.isFile) CorpusWordList.read(cached) else null) ?: run {
            val started = TimeSource.Monotonic.markNow()
            val corpus = lexicon.corpus.source.fetch(cache) { println(it) }
            CorpusWordList.count(listOf(corpus), recipe, heldOut = { it % HELD_OUT_EVERY == 0L }).also {
                it.write(cached)
                println("counted words in ${lexicon.corpus.name} in ${started.elapsedNow().inWholeMilliseconds} ms")
            }
        }
        WordList(
            recipe.languageTag,
            lexicon.corpus.name,
            list.words,
            "from ${list.sentences} sentences, ${list.tokens} words, ${list.accepted} in the alphabet, " +
                "${list.distinct} distinct",
        )
    }
}

fun buildPack(spec: PackSpec, out: File, cache: File, heldOutDirectory: File?, withNgrams: Boolean): File {
    val started = TimeSource.Monotonic.markNow()
    val list = wordList(spec, cache)
    val meta = linkedMapOf(
        "language" to list.languageTag,
        "name" to spec.displayName,
        "source" to spec.lexicon.description,
        "sourceSha256" to spec.lexicon.source.sha256,
        "sourceVersion" to list.version,
        "licence" to spec.licence,
        "attribution" to spec.attribution,
    )
    val lexiconOnly = MkdWriter.write(list.words, meta)
    val ngrams = if (withNgrams && spec.ngramCorpora.isNotEmpty()) ngramTable(spec, lexiconOnly, cache, heldOutDirectory) else null
    if (ngrams != null) {
        // A pack whose words and statistics share one licence names it once.
        meta["licence"] = if (spec.ngramLicence == spec.licence) spec.licence else "${spec.licence} AND ${spec.ngramLicence}"
        meta["ngramSource"] = "Leipzig Corpora Collection " + spec.ngramCorpora.joinToString { it.name } +
            " (1 in $HELD_OUT_EVERY sentences held out)"
        meta["ngramSha256"] = spec.ngramCorpora.joinToString(",") { it.source.sha256 }
        meta["ngramLicence"] = spec.ngramLicence
        meta["ngramAttribution"] = spec.ngramAttribution
    }
    val bytes = MkdWriter.write(list.words, meta, ngrams)
    verify(spec, bytes, list.words, lexiconOnly, ngrams != null)
    val target = File(out, spec.fileName)
    val partial = File(out, spec.fileName + ".part")
    partial.writeBytes(bytes)
    check(partial.renameTo(target) || (target.delete() && partial.renameTo(target))) { "could not write $target" }

    val pack = MkdPack.open(ByteArrayRegion(bytes))
    val sections = pack.sectionSizes.entries.joinToString { (id, size) -> "$id ${kib(size)}" }
    println(
        "${spec.fileName}: ${pack.wordCount} words (${list.words.count { it.offensive }} offensive, " +
            "${list.words.count { it.text != it.text.lowercase() }} cased; ${list.summary})" +
            (ngrams?.let { ", ${it.bigramCount} bigrams, ${it.trigramCount} trigrams" } ?: "") +
            ", ${bytes.size} bytes (${kib(bytes.size)}) [$sections], built in ${started.elapsedNow().inWholeMilliseconds} ms",
    )
    return target
}

/**
 * The pack's next-word table: counts from the cache, or from the corpora (fetched first), then
 * pruned. Writes the held-out sentences to [heldOutDirectory] as `<stem>-heldout.txt`.
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
        heldOutFile.copyTo(File(heldOutDirectory, spec.stem + "-heldout.txt"), overwrite = true)
    }
    return counts.prune(spec.pruning) { lexicon.pack.isOffensive(it) }
}

/** Reads the pack back through the keyboard's reader and fails the build on anything unexpected. */
private fun verify(spec: PackSpec, bytes: ByteArray, words: List<MkdWord>, lexiconOnly: ByteArray, withNgrams: Boolean) {
    val pack = MkdPack.open(ByteArrayRegion(bytes))
    val dictionary = MappedDictionary(pack)
    val checks = spec.checks
    fun expect(condition: Boolean, what: String) = check(condition) { "${spec.fileName}: $what" }
    expect(pack.checksumMatches(), "checksum")
    expect(pack.wordCount == words.map { it.text }.toSet().size, "word count")
    for (word in checks.words) {
        expect(dictionary.lookup(word)?.word == word, "lookup $word")
    }
    val (prefix, completion) = checks.completion
    expect(dictionary.completions(prefix, 3).any { it.word == completion }, "$prefix completes to $completion")
    for ((typed, found) in checks.folds) {
        expect(dictionary.lookup(typed)?.word == found, "$typed finds $found")
    }
    words.firstOrNull { it.offensive }?.let { offensive ->
        expect(dictionary.lookup(offensive.text) != null, "offensive words stay known")
        expect(dictionary.completions(offensive.text, 50).none { it.word == offensive.text }, "offensive words are not offered")
    }
    expect(bytes.size <= spec.maxBytes, "pack over ${spec.maxBytes / 1_000_000} MB")
    if (!withNgrams) return

    // The n-grams were counted with the lexicon-only pack's ids: they must be the same words.
    val lexicon = MkdPack.open(ByteArrayRegion(lexiconOnly))
    expect((0 until pack.wordCount).all { pack.wordText(it) == lexicon.wordText(it) }, "word ids match the counted lexicon")
    val model = dictionary.nextWords
    expect(model != null, "next-word statistics present")
    for ((context, word) in checks.predictions) {
        expect(model!!.predict(context, false, 3).any { it.word == word }, "${context.joinToString(" ")} → $word")
    }
    // The commonest word predicts something, and nothing offensive is ever predicted.
    val top = pack.wordText(0)
    expect(model!!.predict(listOf(top), false, 3).isNotEmpty(), "$top predicts next words")
    val offensiveIds = (0 until pack.wordCount).filter { pack.isOffensive(it) }.toSet()
    val contexts = listOf(listOf(top), listOf(pack.wordText(1)), listOf(pack.wordText(1), top), emptyList()) +
        checks.predictions.map { it.first }
    expect(contexts.all { context -> model.predict(context, false, 48).none { dictionary.wordId(it.word) in offensiveIds } }, "offensive words are never predicted")
}

private fun kib(bytes: Int) = "%.1f KiB".format(bytes / 1024.0)
