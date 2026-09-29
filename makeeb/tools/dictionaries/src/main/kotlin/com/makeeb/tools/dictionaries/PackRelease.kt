package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.engine.packs.PackCatalogue
import com.makeeb.engine.packs.PackEntry
import com.makeeb.platform.storage.ByteArrayRegion
import java.io.File
import kotlin.system.exitProcess

/**
 * Writes the folder to upload as one GitHub release's assets (`./gradlew :tools:dictionaries:packRelease`):
 *
 *     PackRelease --packs <language pack directory> --bundled <bundled pack directory> --out <folder>
 *
 * - every downloadable pack, and `catalogue.json` listing them with their size, SHA-256, licence
 *   and attribution (read back from each pack's own META), with URLs relative to the catalogue, so
 *   the same folder works from GitHub or from a local server;
 * - `en_US.mkd`, which the apps bundle: builds download it instead of counting 517 MB of corpora
 *   (`PackSpecs.englishUsRelease`); it isn't in the catalogue;
 * - `SHA256SUMS`, to check an upload by hand.
 */
object PackRelease {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = args.toList().chunked(2).associate { (key, value) -> key to value }
        val packs = File(options["--packs"] ?: releaseUsage())
        val bundled = File(options["--bundled"] ?: releaseUsage())
        val out = File(options["--out"] ?: releaseUsage())
        out.deleteRecursively()
        out.mkdirs()

        val entries = PackSpecs.downloadable.map { spec -> releasePack(File(packs, spec.fileName), out) }.sortedBy { it.language }
        val english = File(bundled, PackSpecs.englishUs.fileName)
        check(english.isFile) { "no $english: run dictionaryPacks first" }
        val englishEntry = releasePack(english, out)
        if (englishEntry.sha256 != PackSpecs.ENGLISH_US_RELEASE_SHA256) {
            println(
                "note: en_US.mkd is ${englishEntry.sha256}, not the pinned ${PackSpecs.ENGLISH_US_RELEASE_SHA256}: " +
                    "after publishing this folder, pin the new hash in PackSpecs.ENGLISH_US_RELEASE_SHA256",
            )
        }

        File(out, CATALOGUE).writeText(PackCatalogue(entries).toJson())
        val sums = (entries + englishEntry).joinToString("") { "${it.sha256}  ${it.file}\n" } +
            "${PinnedSource.sha256Of(File(out, CATALOGUE))}  $CATALOGUE\n"
        File(out, "SHA256SUMS").writeText(sums)
        for (entry in entries + englishEntry) {
            println(
                "%-10s %-6s %,11d bytes  %,8d words  %s  %s".format(
                    entry.file, entry.language, entry.size, entry.words, if (entry.nextWords) "next words" else "no next words", entry.sha256,
                ),
            )
        }
        println("${entries.size} packs and $CATALOGUE in $out, ${(entries + englishEntry).sumOf { it.size } / 1_000_000} MB")
    }
}

private const val CATALOGUE = "catalogue.json"

/** Copies [file] into [out] and describes it from its own header and META. */
private fun releasePack(file: File, out: File): PackEntry {
    check(file.isFile) { "no $file: run languagePacks first" }
    val bytes = file.readBytes()
    val region = ByteArrayRegion(bytes)
    val pack = MkdPack.open(region)
    check(pack.checksumMatches()) { "$file: checksum" }
    val attribution = listOfNotNull(pack.meta["attribution"], pack.meta["ngramAttribution"]).distinct().joinToString(" ")
    val hasNextWords = "NGRM" in pack.sectionSizes
    if (!hasNextWords) println("warning: ${file.name} has no next-word statistics (built with -Pmakeeb.ngrams=false?)")
    file.copyTo(File(out, file.name), overwrite = true)
    return PackEntry(
        language = pack.languageTag,
        name = pack.meta["name"] ?: pack.languageTag,
        file = file.name,
        url = file.name,
        size = bytes.size.toLong(),
        sha256 = PinnedSource.sha256Of(bytes),
        mkdVersion = "${region.u16(4)}.${region.u16(6)}",
        words = pack.wordCount,
        nextWords = hasNextWords,
        licence = pack.meta["licence"] ?: error("$file: no licence"),
        attribution = attribution,
    )
}

private fun releaseUsage(): Nothing {
    System.err.println("usage: PackRelease --packs <language pack directory> --bundled <bundled pack directory> --out <folder>")
    exitProcess(2)
}
