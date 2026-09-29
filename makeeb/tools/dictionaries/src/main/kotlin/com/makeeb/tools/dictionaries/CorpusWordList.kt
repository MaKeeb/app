package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdWord
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * How a word list is derived from a corpus, for a language AOSP has no list for (Hungarian).
 * Every choice here is part of the cache key, so changing one recounts.
 */
data class CorpusWordRecipe(
    val languageTag: String,
    /**
     * The language's lower-case letters. A word with any other character is left out: digits,
     * foreign letters, and the mis-encoded "õ" and "û" some sources put for "ő" and "ű". Hyphens
     * inside a word are allowed ("EU-s", "magyar-német").
     */
    val alphabet: String,
    /** Seen fewer times than this, a word is more likely a typo or a one-off name than a word. */
    val minCount: Int = 3,
    /** The most frequent this many words are kept. */
    val maxWords: Int = 300_000,
    /**
     * A word is kept capitalised (a name: "Budapest", "NATO") when at least this share of its
     * uses inside a sentence are capitalised; otherwise it is kept in lower case. Sentence starts
     * don't count: a capital there says nothing.
     */
    val nameShare: Double = 0.9,
    val offensive: OffensiveWords,
) {
    fun accepts(word: String): Boolean {
        if (word.isEmpty() || word.length > MAX_LENGTH || "--" in word) return false
        return word.all { it == '-' || it.lowercaseChar() in alphabet } && word.first() != '-' && word.last() != '-'
    }

    private companion object {
        /** Long enough for Hungarian compounds, short enough for MKD's one-byte length. */
        const val MAX_LENGTH = 48
    }
}

/**
 * Words flagged offensive: known to the dictionary (typing one isn't a typo) but never offered,
 * as AOSP's `possibly_offensive` flag does. [exact] words, and words starting with one of
 * [prefixes]. Only lower-case spellings are matched: a name that starts like a vulgar word
 * ("Kurvinen") is still a name.
 */
class OffensiveWords(val exact: Set<String>, val prefixes: List<String>) {
    fun matches(word: String): Boolean =
        word == word.lowercase() && (word in exact || prefixes.any { word.startsWith(it) })

    override fun toString(): String = exact.sorted().joinToString(",") + "|" + prefixes.joinToString(",")
}

/**
 * Hungarian vulgar words and slurs, MaKeeb's own short list. Prefixes only where no ordinary
 * word starts the same way; the others are spelt out: "fasz" would catch "faszerkezet" (fa +
 * szerkezet, a wooden frame), "basz" "baszk" (Basque), "szar" "szarvas" (deer).
 */
val HungarianOffensiveWords = OffensiveWords(
    exact = setOf(
        "fasz", "fasza", "faszt", "faszok", "faszod", "faszos", "szar", "szart", "szaros", "szarok", "szarik", "szarság",
        "szarságot", "szarból", "szarházi", "szarjál", "pina", "pinát", "pinája", "pinák", "pinába", "pinád", "basz", "bassza",
        "basszák", "basszon", "basszátok", "leszarom", "leszarva", "cigányozott", "zsidózott", "csicska",
    ),
    prefixes = listOf(
        "kurv", "geci", "picsa", "picsá", "segg", "buzi", "köcsög", "ribanc", "rohadék", "nigger", "néger", "faszom", "faszság",
        "faszfej", "baszd", "baszi", "baszo", "baszó", "baszt", "baszn", "baszá", "basza", "kibasz", "elbasz", "megbasz",
        "bazdm", "bazm",
    ),
)

/**
 * A word list counted from Leipzig corpora ([count]): each word's canonical spelling (case from
 * its uses inside sentences), its 0–255 frequency ([RankFrequency]) and the offensive flag.
 * Tokenised with [CorpusTokens], the keyboard's own rules, so the words are the ones it reads.
 */
class CorpusWordList(
    val words: List<MkdWord>,
    val sentences: Long,
    val tokens: Long,
    /** Tokens made only of the language's letters. */
    val accepted: Long,
    /** Distinct accepted words, before the count and size cuts. */
    val distinct: Int,
) {
    fun write(file: File) {
        val partial = File(file.path + ".part")
        partial.bufferedWriter().use { out ->
            out.write("$CACHE_VERSION\t$sentences\t$tokens\t$accepted\t$distinct\n")
            for (word in words) out.write("${word.text}\t${word.frequency}\t${if (word.offensive) 1 else 0}\n")
        }
        check(partial.renameTo(file) || (file.delete() && partial.renameTo(file))) { "could not write $file" }
    }

    /** Case and filtering evidence for one lower-case form. */
    private class Forms {
        var total = 0
        var midLower = 0
        var midCapitalised = 0

        /** Capitalised spellings seen inside sentences ("Budapest", "NATO"), with their counts. */
        var capitalised: HashMap<String, Int>? = null
    }

    companion object {
        private const val CACHE_VERSION = "corpus-words-v1"

        fun read(file: File): CorpusWordList? {
            val lines = file.readLines()
            val header = lines.firstOrNull()?.split('\t') ?: return null
            if (header.first() != CACHE_VERSION) return null
            val words = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
                val (text, frequency, offensive) = line.split('\t')
                MkdWord(text, frequency.toInt(), offensive == "1")
            }
            return CorpusWordList(words, header[1].toLong(), header[2].toLong(), header[3].toLong(), header[4].toInt())
        }

        /**
         * Counts the `*-sentences.txt` of each of [corpora] with [recipe], skipping the sentences
         * [heldOut] picks by corpus id (the n-gram counts hold out the same ones).
         */
        fun count(corpora: List<File>, recipe: CorpusWordRecipe, heldOut: (Long) -> Boolean): CorpusWordList {
            val forms = HashMap<String, Forms>(1 shl 21)
            var sentences = 0L
            var tokens = 0L
            var accepted = 0L
            for (corpus in corpora) {
                GZIPInputStream(BufferedInputStream(corpus.inputStream(), 1 shl 20), 1 shl 16).use { gz ->
                    forEachTarEntry(gz) { path, content ->
                        if (!path.endsWith("-sentences.txt")) return@forEachTarEntry
                        content.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                            val tab = line.indexOf('\t')
                            if (tab < 0 || heldOut(line.substring(0, tab).toLongOrNull() ?: 0L)) return@forEachLine
                            sentences++
                            for (word in CorpusTokens.words(CorpusTokens.normalise(line.substring(tab + 1)))) {
                                tokens++
                                if (!recipe.accepts(word.text)) continue
                                accepted++
                                val lower = word.text.lowercase()
                                val entry = forms.getOrPut(lower) { Forms() }
                                entry.total++
                                if (word.sentenceInitial) continue
                                if (word.text == lower) {
                                    entry.midLower++
                                } else {
                                    entry.midCapitalised++
                                    val spellings = entry.capitalised ?: HashMap<String, Int>(2).also { entry.capitalised = it }
                                    spellings[word.text] = (spellings[word.text] ?: 0) + 1
                                }
                            }
                        }
                    }
                }
            }
            val kept = forms.entries.asSequence()
                .filter { it.value.total >= recipe.minCount }
                .sortedWith(compareByDescending<Map.Entry<String, Forms>> { it.value.total }.thenBy { it.key })
                .take(recipe.maxWords)
                .toList()
            val words = ArrayList<MkdWord>(kept.size)
            var rank = 0
            var previousCount = -1
            var frequency = 0
            for ((index, entry) in kept.withIndex()) {
                val (lower, stats) = entry
                // Equal counts share the rank of the first of them, so they get equal frequencies.
                if (stats.total != previousCount) {
                    rank = index + 1
                    previousCount = stats.total
                    frequency = RankFrequency.of(rank)
                }
                val inside = stats.midLower + stats.midCapitalised
                val spelling = if (stats.midCapitalised > 0 && stats.midCapitalised >= recipe.nameShare * inside) {
                    stats.capitalised!!.entries.maxWith(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key }).key
                } else {
                    lower
                }
                if (spelling.encodeToByteArray().size > MkdFormat.MAX_WORD_BYTES) continue
                words += MkdWord(spelling, frequency, offensive = recipe.offensive.matches(spelling))
            }
            return CorpusWordList(words, sentences, tokens, accepted, forms.size)
        }
    }
}

/**
 * A word's 0–255 frequency from its rank, on the curve the AOSP word lists share. Their
 * frequencies by rank agree across languages to within a few steps (the medians of the de, es,
 * fr, it, nl, pl, pt_BR, sv and en_US lists at pinned commit 8dd31a28, below), and the suggestion
 * engine's costs were tuned on them, so a corpus-derived list means the same thing to it.
 */
object RankFrequency {
    /** (rank, frequency), interpolated in log rank; past the last anchor the frequency stays 1. */
    private val anchors = listOf(
        1 to 221, 10 to 198, 100 to 164, 1_000 to 138, 10_000 to 106,
        50_000 to 73, 100_000 to 55, 150_000 to 38, 400_000 to 10, 1_000_000 to 1,
    )

    fun of(rank: Int): Int {
        require(rank >= 1)
        val x = log10(rank.toDouble())
        for (i in 1 until anchors.size) {
            val (r1, f1) = anchors[i]
            if (rank <= r1) {
                val (r0, f0) = anchors[i - 1]
                val t = (x - log10(r0.toDouble())) / (log10(r1.toDouble()) - log10(r0.toDouble()))
                return (f0 + t * (f1 - f0)).roundToInt().coerceIn(1, 255)
            }
        }
        return 1
    }
}
