package com.makeeb.tools.dictionaries.harness

import com.makeeb.core.model.AutocorrectStrength
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.Suggestion
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.input.InputEngine
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.LayoutOptions
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.engine.prediction.TapPoint
import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
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
 *   Measured twice: tapping completions only, and also tapping a predicted next word before
 *   typing any of it.
 * - **Next-word predictions**: how often the word about to be typed is the strip's best
 *   prediction, or one of its three, wherever the strip predicts (mid-sentence, after a space).
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
    /** Passed to [DictionarySuggestionEngine]; 0 measures completions without the context boost. */
    contextWeight: Double? = null,
    strength: AutocorrectStrength = AutocorrectStrength.Normal,
) {
    private val engine = InputEngine(
        layouts = BuiltInLayoutProvider(),
        suggestionEngine = if (contextWeight == null) DictionarySuggestionEngine(dictionary, user = null)
        else DictionarySuggestionEngine(dictionary, user = null, contextWeight = contextWeight),
        preferences = MutableStateFlow(KeyboardPreferences(autoCorrectStrength = strength)),
        timeSource = TestTimeSource(),
    )
    private val neighbours = keyNeighbours()
    private val centres = letterCentres()

    fun run(sentences: List<String>): HarnessReport {
        val words = sentences.map(::words)
        val clean = cleanPass(words)
        val completion = completionPass(words, tapPredictions = false)
        val predicted = completionPass(words, tapPredictions = true)
        val noisy = noisyPass(words)
        val unknown = UNKNOWN_WORDS.filter { dictionary.lookup(it) == null }
        return HarnessReport(
            sentences = sentences.size,
            words = clean.words,
            knownWords = words.flatten().count { dictionary.lookup(it) != null },
            falseCorrections = clean.changed,
            typedKeys = completion.typedKeys,
            typedKeysWithPredictions = predicted.typedKeys,
            fullKeys = completion.fullKeys,
            predictionPlaces = clean.predictionPlaces,
            predictedBest = clean.predictedBest,
            predictedInStrip = clean.predictedInStrip,
            typos = noisy.typos,
            typosFixed = noisy.fixed,
            typosInStrip = noisy.inStrip,
            falseCorrectionsInNoisyPass = noisy.cleanChanged,
            cleanWordsInNoisyPass = noisy.cleanWords,
            keyMicros = completion.keyMicros.sorted(),
            spaceMicros = completion.spaceMicros.sorted(),
            unknownWords = unknown.size,
            unknownChanged = unknownPass(unknown),
            substitutionRate = substitutionRate,
            seed = seed,
        )
    }

    private class Clean(val words: Int, val changed: Int, val predictionPlaces: Int, val predictedBest: Int, val predictedInStrip: Int)

    /**
     * Every word typed correctly and in full: what does autocorrect change? And before each word,
     * does the strip predict it?
     */
    private fun cleanPass(sentences: List<List<String>>): Clean {
        var total = 0
        var changed = 0
        var places = 0
        var best = 0
        var inStrip = 0
        for (sentence in sentences) {
            val host = startSentence()
            for (word in sentence) {
                val predictions = predictions()
                if (predictions.isNotEmpty()) {
                    places++
                    if (predictions.first().text.equals(word, ignoreCase = true)) best++
                    if (predictions.any { it.text.equals(word, ignoreCase = true) }) inStrip++
                }
                total++
                if (!commit(host, word).equals(word, ignoreCase = true)) changed++
            }
        }
        return Clean(total, changed, places, best, inStrip)
    }

    private class Completion(val typedKeys: Int, val fullKeys: Int, val keyMicros: List<Long>, val spaceMicros: List<Long>)

    /**
     * Every word typed correctly, tapping the strip as soon as it offers the word: as a
     * completion while typing, and with [tapPredictions] also as a prediction before the first
     * letter.
     */
    private fun completionPass(sentences: List<List<String>>, tapPredictions: Boolean): Completion {
        var typed = 0
        var full = 0
        val micros = ArrayList<Long>()
        val spaceMicros = ArrayList<Long>()
        fun timed(action: KeyAction) {
            val mark = TimeSource.Monotonic.markNow()
            engine.onKey(action)
            val elapsed = mark.elapsedNow().inWholeMicroseconds
            micros += elapsed
            if (action == KeyAction.Space) spaceMicros += elapsed
        }
        for (sentence in sentences) {
            startSentence()
            for (word in sentence) {
                full += word.length + 1
                if (tapPredictions) {
                    val prediction = predictions().firstOrNull { it.text.equals(word, ignoreCase = true) }
                    if (prediction != null) {
                        engine.onSuggestionSelected(prediction)
                        typed++
                        continue
                    }
                }
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
        return Completion(typed, full, micros, spaceMicros)
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

    /**
     * Every letter tapped where a finger would land: a Gaussian around its key's centre, [sigma]
     * key widths across and the same physical distance down (rows are [ROW_ASPECT] times taller),
     * typing whichever letter is nearest. Typos then come from the finger, as on a phone. Each
     * word is typed twice, once passing the tap points to the engine (as the keyboard does) and
     * once without, so the difference is what the tap weighting buys.
     */
    fun tapRun(sentences: List<String>, sigma: Double = DEFAULT_TAP_SIGMA): TapReport {
        val random = Random(seed)
        val plan = sentences.map { sentence -> words(sentence).map { word -> word to tapLetters(word, random, sigma) } }
        var typos = 0
        var clean = 0
        val fixed = IntArray(2)
        val changed = IntArray(2)
        listOf(true, false).forEachIndexed { variant, useTaps ->
            for (sentence in plan) {
                val host = startSentence()
                for ((word, letters) in sentence) {
                    val start = host.text.length
                    letters.forEach { (char, tap) -> engine.onKey(KeyAction.Text(char.toString()), tap.takeIf { useTaps }) }
                    engine.onKey(KeyAction.Space)
                    val result = host.text.substring(start).trim()
                    if (letters.joinToString("") { it.first.toString() } == word.lowercase()) {
                        if (variant == 0) clean++
                        if (!result.equals(word, ignoreCase = true)) changed[variant]++
                    } else {
                        if (variant == 0) typos++
                        if (result.equals(word, ignoreCase = true)) fixed[variant]++
                    }
                }
            }
        }
        return TapReport(sigma, plan.sumOf { it.size }, typos, fixed[0], fixed[1], clean, changed[0], changed[1])
    }

    private fun tapLetters(word: String, random: Random, sigma: Double): List<Pair<Char, TapPoint?>> = word.lowercase().map { char ->
        val (cx, cy) = centres[char] ?: return@map char to null
        val x = cx + gaussian(random) * sigma
        val y = cy + gaussian(random) * sigma / ROW_ASPECT
        val nearest = centres.minBy { (_, c) -> (c.first - x).let { it * it } + ((c.second - y) * ROW_ASPECT).let { it * it } }.key
        nearest to TapPoint(x.toFloat(), y.toFloat())
    }

    /** Box–Muller: a standard normal draw from two uniform ones. */
    private fun gaussian(random: Random): Double = sqrt(-2.0 * ln(1.0 - random.nextDouble())) * cos(2.0 * PI * random.nextDouble())

    /** Letter centres in key widths and rows, as the engine measures them for tap points. */
    private fun letterCentres(): Map<Char, Pair<Double, Double>> {
        val layout = BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions())
        val geometry = LayoutGeometry(layout, width = layout.unitsPerRow, rowHeight = 1f)
        return ('a'..'z').associateWith { geometry.keyFor(it)!!.bounds.let { b -> b.centerX.toDouble() to b.centerY.toDouble() } }
    }

    private fun predictions(): List<Suggestion> = engine.state.value.suggestions.filter { it.kind == Suggestion.Kind.NextWord }

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

        /**
         * Tap scatter in key widths. About 0.2 on a phone keyboard (touch-offset studies report
         * 0.15–0.3), which puts roughly one tap in 30 on a neighbouring key.
         */
        const val DEFAULT_TAP_SIGMA = 0.22

        /** Rows are this much taller than keys are wide (WeightedEdits' ROW_ASPECT). */
        const val ROW_ASPECT = 1.4

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

/** [TypingHarness.tapRun]: finger-like taps, corrected with and without the tap points. */
data class TapReport(
    val sigma: Double,
    val words: Int,
    val typos: Int,
    val fixedWithTaps: Int,
    val fixedWithoutTaps: Int,
    val cleanWords: Int,
    val changedWithTaps: Int,
    val changedWithoutTaps: Int,
) {
    fun format(): String {
        fun pct(part: Int, whole: Int) = "%.1f%%".format(100.0 * part / whole.coerceAtLeast(1))
        return "finger taps (σ=$sigma key widths): $typos typo words of $words; fixed ${pct(fixedWithTaps, typos)} with the tap points " +
            "(${pct(fixedWithoutTaps, typos)} without); clean words changed $changedWithTaps of $cleanWords ($changedWithoutTaps without)"
    }
}

data class HarnessReport(
    val sentences: Int,
    val words: Int,
    val knownWords: Int,
    val falseCorrections: Int,
    val typedKeys: Int,
    val typedKeysWithPredictions: Int,
    val fullKeys: Int,
    val predictionPlaces: Int,
    val predictedBest: Int,
    val predictedInStrip: Int,
    val typos: Int,
    val typosFixed: Int,
    val typosInStrip: Int,
    val falseCorrectionsInNoisyPass: Int,
    val cleanWordsInNoisyPass: Int,
    val keyMicros: List<Long>,
    val spaceMicros: List<Long>,
    val unknownWords: Int,
    val unknownChanged: List<Pair<String, String>>,
    val substitutionRate: Double,
    val seed: Long,
) {
    val keystrokeSavings: Double get() = 1.0 - typedKeys.toDouble() / fullKeys
    val keystrokeSavingsWithPredictions: Double get() = 1.0 - typedKeysWithPredictions.toDouble() / fullKeys
    val falseCorrectionRate: Double get() = falseCorrections.toDouble() / words
    val typoFixRate: Double get() = typosFixed.toDouble() / typos
    val typoInStripRate: Double get() = typosInStrip.toDouble() / typos

    fun format(): String {
        fun pct(value: Double) = "%.1f%%".format(value * 100)
        fun percentile(values: List<Long>, p: Int) = values.getOrElse(((values.size - 1) * p) / 100) { 0 }
        val places = predictionPlaces.coerceAtLeast(1).toDouble()
        return """
            |corpus: $sentences sentences, $words words, $knownWords known to the dictionary (${pct(knownWords.toDouble() / words)})
            |keystroke savings (perfect strip use), completions only: ${pct(keystrokeSavings)} ($typedKeys of $fullKeys keys)
            |keystroke savings, completions and next-word predictions: ${pct(keystrokeSavingsWithPredictions)} ($typedKeysWithPredictions of $fullKeys keys)
            |next-word predictions (strip, mid-sentence): shown before $predictionPlaces of $words words; best ${pct(predictedBest / places)} ($predictedBest), top 3 ${pct(predictedInStrip / places)} ($predictedInStrip)
            |false corrections (clean typing): ${pct(falseCorrectionRate)} ($falseCorrections of $words words)
            |typos (${pct(substitutionRate)} neighbour-key substitution per letter, seed $seed): $typos words
            |  fixed by autocorrect: ${pct(typoFixRate)} ($typosFixed)
            |  target in the strip: ${pct(typoInStripRate)} ($typosInStrip)
            |  false corrections on the untouched words: $falseCorrectionsInNoisyPass of $cleanWordsInNoisyPass
            |unknown words (names, slang) changed by autocorrect: ${unknownChanged.size} of $unknownWords ${unknownChanged.joinToString { "${it.first}→${it.second}" }}
            |key cost on the JVM (engine + suggestions): p50 ${percentile(keyMicros, 50)} µs, p95 ${percentile(keyMicros, 95)} µs, max ${keyMicros.lastOrNull() ?: 0} µs
            |space key cost on the JVM (commit + next-word predictions): p50 ${percentile(spaceMicros, 50)} µs, p95 ${percentile(spaceMicros, 95)} µs
        """.trimMargin()
    }
}

/**
 * `./gradlew :tools:dictionaries:typingHarness` runs it on the en_US pack. Arguments:
 * `--pack <file.mkd>` (or `--pack starter` for the 250-word starter list), `--seed <n>`,
 * `--rate <substitution rate per letter>`, `--heldout <sentences>` (the builder's held-out
 * corpus sentences, for [NextWordEvaluation]).
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
    println("dictionary: ${File(packOption).name}, $size words" + ((dictionary as? MappedDictionary)?.let { " " + NextWordEvaluation.describe(it) } ?: ""))
    println(TypingHarness(dictionary, seed, rate).run(corpus).format())
    val withoutContext = TypingHarness(dictionary, seed, rate, contextWeight = 0.0).run(corpus)
    println("without the context boost: keystroke savings, completions only ${"%.1f%%".format(withoutContext.keystrokeSavings * 100)}")
    println()
    for (strength in AutocorrectStrength.entries) {
        val report = TypingHarness(dictionary, seed, rate, strength = strength).run(corpus)
        val taps = TypingHarness(dictionary, seed, rate, strength = strength).tapRun(corpus)
        println(
            "$strength: substitution typos fixed ${"%.1f%%".format(100.0 * report.typosFixed / report.typos)}, false corrections ${report.falseCorrections}, " +
                "unknown words changed ${report.unknownChanged.size} of ${report.unknownWords} ${report.unknownChanged}; ${taps.format()}",
        )
    }

    val heldOut = options["--heldout"]?.let(::File)?.takeIf { it.isFile } ?: return
    val mapped = dictionary as? MappedDictionary ?: return
    val sentences = heldOut.readLines().filter { it.isNotBlank() }
    println()
    println("held-out corpus sentences (${heldOut.name}, never counted): ${sentences.size}")
    if (mapped.nextWords != null) println(NextWordEvaluation(mapped).run(sentences).format())
    // Keystroke savings on news and web text, the register the statistics come from.
    val sample = sentences.filterIndexed { i, _ -> i % 8 == 0 }
    val boosted = TypingHarness(dictionary, seed, rate).run(sample)
    val plain = TypingHarness(dictionary, seed, rate, contextWeight = 0.0).run(sample)
    println(
        "typing ${sample.size} of them: keystroke savings, completions only ${"%.1f%%".format(plain.keystrokeSavings * 100)} without the context boost, " +
            "${"%.1f%%".format(boosted.keystrokeSavings * 100)} with it, ${"%.1f%%".format(boosted.keystrokeSavingsWithPredictions * 100)} with predictions; " +
            "strip predictions best ${"%.1f%%".format(100.0 * boosted.predictedBest / boosted.predictionPlaces)}, top 3 ${"%.1f%%".format(100.0 * boosted.predictedInStrip / boosted.predictionPlaces)}; " +
            "false corrections ${boosted.falseCorrections} of ${boosted.words}",
    )
}
