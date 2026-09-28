package com.makeeb.tools.dictionaries.harness

import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.input.InputEngine
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.LayoutOptions
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.random.Random
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/**
 * Simulated typing (docs/research/dictionaries-autocorrect.md §6.8). Types held-out sentences
 * through the real [InputEngine] and [DictionarySuggestionEngine] over [dictionary], word by word
 * with a space after each, and measures what a user would see:
 *
 * - **Keystroke savings**: a user who taps the target word as soon as the strip shows it, versus
 *   typing every letter and the space. An upper bound: real users look at the strip less often.
 * - **False corrections**: correctly typed words that autocorrect changed.
 * - **Typo fixes**: words given a typo that autocorrect turned back into the target, and how often
 *   the target was at least in the strip (one tap away).
 *
 * Typos substitute a letter with a neighbouring key on the QWERTY layout ([substitutionRate] per
 * letter, from a [Random] seeded with [seed]), so every run is reproducible. Words compare
 * case-insensitively: the user types lower case and relies on auto-capitalisation. No user
 * dictionary, so nothing learnt in one sentence changes the next. Timings are the JVM's, for
 * comparing runs on one machine, not device budgets.
 */
class TypingHarness(
    private val dictionary: Dictionary,
    private val seed: Long = DEFAULT_SEED,
    private val substitutionRate: Double = DEFAULT_SUBSTITUTION_RATE,
) {
    private val engine = InputEngine(
        layouts = BuiltInLayoutProvider(),
        suggestionEngine = DictionarySuggestionEngine(dictionary, user = null),
        preferences = MutableStateFlow(KeyboardPreferences()),
        timeSource = TestTimeSource(),
    )
    private val neighbours = keyNeighbours()

    fun run(sentences: List<String>): HarnessReport {
        val words = sentences.map(::words)
        val clean = cleanPass(words)
        val completion = completionPass(words)
        val noisy = noisyPass(words)
        val unknown = UNKNOWN_WORDS.filter { dictionary.lookup(it) == null }
        return HarnessReport(
            sentences = sentences.size,
            words = clean.words,
            knownWords = words.flatten().count { dictionary.lookup(it) != null },
            falseCorrections = clean.changed,
            typedKeys = completion.typedKeys,
            fullKeys = completion.fullKeys,
            typos = noisy.typos,
            typosFixed = noisy.fixed,
            typosInStrip = noisy.inStrip,
            falseCorrectionsInNoisyPass = noisy.cleanChanged,
            cleanWordsInNoisyPass = noisy.cleanWords,
            keyMicros = completion.keyMicros.sorted(),
            unknownWords = unknown.size,
            unknownChanged = unknownPass(unknown),
            substitutionRate = substitutionRate,
            seed = seed,
        )
    }

    private class Clean(val words: Int, val changed: Int)

    /** Every word typed correctly and in full: what does autocorrect change? */
    private fun cleanPass(sentences: List<List<String>>): Clean {
        var total = 0
        var changed = 0
        for (sentence in sentences) {
            val host = startSentence()
            for (word in sentence) {
                total++
                if (!commit(host, word).equals(word, ignoreCase = true)) changed++
            }
        }
        return Clean(total, changed)
    }

    private class Completion(val typedKeys: Int, val fullKeys: Int, val keyMicros: List<Long>)

    /** Every word typed correctly, tapping the strip as soon as it offers the word. */
    private fun completionPass(sentences: List<List<String>>): Completion {
        var typed = 0
        var full = 0
        val micros = ArrayList<Long>()
        fun timed(action: KeyAction) {
            val mark = TimeSource.Monotonic.markNow()
            engine.onKey(action)
            micros += mark.elapsedNow().inWholeMicroseconds
        }
        for (sentence in sentences) {
            startSentence()
            for (word in sentence) {
                full += word.length + 1
                var picked = false
                for (i in word.indices) {
                    timed(KeyAction.Text(word[i].lowercase()))
                    typed++
                    if (i == word.lastIndex) break
                    val offer = engine.state.value.suggestions.firstOrNull { it.text.equals(word, ignoreCase = true) }
                    if (offer != null) {
                        engine.onSuggestionSelected(offer)
                        typed++
                        picked = true
                        break
                    }
                }
                if (!picked) {
                    timed(KeyAction.Space)
                    typed++
                }
            }
        }
        return Completion(typed, full, micros)
    }

    private class Noisy(val typos: Int, val fixed: Int, val inStrip: Int, val cleanWords: Int, val cleanChanged: Int)

    /** Words typed with neighbour-key substitutions, then a space: does autocorrect repair them? */
    private fun noisyPass(sentences: List<List<String>>): Noisy {
        val random = Random(seed)
        var typos = 0
        var fixed = 0
        var inStrip = 0
        var cleanWords = 0
        var cleanChanged = 0
        for (sentence in sentences) {
            val host = startSentence()
            for (word in sentence) {
                val noisy = word.lowercase().map { char ->
                    val options = neighbours[char]
                    if (options != null && random.nextDouble() < substitutionRate) options[random.nextInt(options.size)] else char
                }.joinToString("")
                if (noisy == word.lowercase()) {
                    cleanWords++
                    if (!commit(host, word).equals(word, ignoreCase = true)) cleanChanged++
                    continue
                }
                typos++
                val start = host.text.length
                noisy.forEach { engine.onKey(KeyAction.Text(it.toString())) }
                if (engine.state.value.suggestions.any { it.text.equals(word, ignoreCase = true) }) inStrip++
                engine.onKey(KeyAction.Space)
                if (host.text.substring(start).trim().equals(word, ignoreCase = true)) fixed++
            }
        }
        return Noisy(typos, fixed, inStrip, cleanWords, cleanChanged)
    }

    /**
     * Names, brands and slang the lexicon lacks, typed in lower case mid-sentence so no capital
     * protects them: how many does autocorrect change? The "autocorrupt" count.
     */
    private fun unknownPass(words: List<String>): List<Pair<String, String>> = words.mapNotNull { word ->
        val host = startSentence()
        commit(host, "with")
        val result = commit(host, word)
        (word to result).takeUnless { result.equals(word, ignoreCase = true) }
    }

    private fun startSentence(): FakeTextHost =
        FakeTextHost().also { engine.startInput(it, FakeKeyboardHost(), EditorAttributes()) }

    /** Types [word] in lower case plus a space; returns the word the field ended up with. */
    private fun commit(host: FakeTextHost, word: String): String {
        val start = host.text.length
        word.lowercase().forEach { engine.onKey(KeyAction.Text(it.toString())) }
        engine.onKey(KeyAction.Space)
        return host.text.substring(start).trim()
    }

    /** Letter keys whose centres are within ~1.25 key sizes: the row neighbours and the diagonals. */
    private fun keyNeighbours(): Map<Char, List<Char>> {
        val layout = BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions())
        val geometry = LayoutGeometry(layout, width = 1000f, rowHeight = 100f)
        val centres = ('a'..'z').associateWith { geometry.keyFor(it)!!.bounds }
        val keyWidth = centres.getValue('q').width
        return centres.mapValues { (letter, bounds) ->
            centres.filter { (other, b) ->
                val dx = (b.centerX - bounds.centerX) / keyWidth
                val dy = (b.centerY - bounds.centerY) / 100f
                other != letter && dx * dx + dy * dy <= 1.25f * 1.25f
            }.keys.sorted()
        }
    }

    companion object {
        const val DEFAULT_SEED = 20260928L
        const val DEFAULT_SUBSTITUTION_RATE = 0.05

        private val WORD = Regex("[A-Za-z]+(?:'[A-Za-z]+)*")

        /** Words people type that a lexicon lacks; the ones the pack knows are skipped. */
        val UNKNOWN_WORDS = listOf(
            "kiraly", "makeeb", "zoltan", "gyula", "szia", "kotlin", "tiktok", "okhttp", "xcode", "figma",
            "brb", "hmu", "yeet", "doggo", "bestie", "vibing", "ngl", "tbh", "omw", "lmao",
            "anyaa", "nokia", "shein", "grindr", "venmo", "zelle", "lyft", "insta", "selfies", "wifi",
        )

        fun words(sentence: String): List<String> = WORD.findAll(sentence).map { it.value }.toList()

        fun corpus(): List<String> {
            val stream = TypingHarness::class.java.getResourceAsStream("/harness/everyday-en.txt")
                ?: error("harness corpus missing")
            return stream.bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        }
    }
}

data class HarnessReport(
    val sentences: Int,
    val words: Int,
    val knownWords: Int,
    val falseCorrections: Int,
    val typedKeys: Int,
    val fullKeys: Int,
    val typos: Int,
    val typosFixed: Int,
    val typosInStrip: Int,
    val falseCorrectionsInNoisyPass: Int,
    val cleanWordsInNoisyPass: Int,
    val keyMicros: List<Long>,
    val unknownWords: Int,
    val unknownChanged: List<Pair<String, String>>,
    val substitutionRate: Double,
    val seed: Long,
) {
    val keystrokeSavings: Double get() = 1.0 - typedKeys.toDouble() / fullKeys
    val falseCorrectionRate: Double get() = falseCorrections.toDouble() / words
    val typoFixRate: Double get() = typosFixed.toDouble() / typos
    val typoInStripRate: Double get() = typosInStrip.toDouble() / typos

    fun format(): String {
        fun pct(value: Double) = "%.1f%%".format(value * 100)
        fun percentile(p: Int) = keyMicros.getOrElse(((keyMicros.size - 1) * p) / 100) { 0 }
        return """
            |corpus: $sentences sentences, $words words, $knownWords known to the dictionary (${pct(knownWords.toDouble() / words)})
            |keystroke savings (perfect strip use): ${pct(keystrokeSavings)} ($typedKeys of $fullKeys keys)
            |false corrections (clean typing): ${pct(falseCorrectionRate)} ($falseCorrections of $words words)
            |typos (${pct(substitutionRate)} neighbour-key substitution per letter, seed $seed): $typos words
            |  fixed by autocorrect: ${pct(typoFixRate)} ($typosFixed)
            |  target in the strip: ${pct(typoInStripRate)} ($typosInStrip)
            |  false corrections on the untouched words: $falseCorrectionsInNoisyPass of $cleanWordsInNoisyPass
            |unknown words (names, slang) changed by autocorrect: ${unknownChanged.size} of $unknownWords ${unknownChanged.joinToString { "${it.first}→${it.second}" }}
            |key cost on the JVM (engine + suggestions): p50 ${percentile(50)} µs, p95 ${percentile(95)} µs, max ${keyMicros.lastOrNull() ?: 0} µs
        """.trimMargin()
    }
}

/**
 * `./gradlew :tools:dictionaries:typingHarness` runs it on the en_US pack. Arguments:
 * `--pack <file.mkd>` (or `--pack starter` for the 250-word starter list), `--seed <n>`,
 * `--rate <substitution rate per letter>`.
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key to value }
    val packOption = options["--pack"] ?: error("--pack <file.mkd | starter> required")
    val dictionary: Dictionary = if (packOption == "starter") StarterDictionaries.english()
    else MappedDictionary(ByteArrayRegion(File(packOption).readBytes()))
    val seed = options["--seed"]?.toLong() ?: TypingHarness.DEFAULT_SEED
    val rate = options["--rate"]?.toDouble() ?: TypingHarness.DEFAULT_SUBSTITUTION_RATE
    val corpus = TypingHarness.corpus()
    TypingHarness(dictionary, seed, rate).run(corpus.take(20)) // JIT warm-up for the timings
    val size = (dictionary as? MappedDictionary)?.wordCount ?: dictionary.entries().count()
    println("dictionary: ${File(packOption).name}, $size words")
    println(TypingHarness(dictionary, seed, rate).run(corpus).format())
}
