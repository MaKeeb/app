package com.makeeb.tools.dictionaries.harness

import com.makeeb.core.common.TextBoundaries
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.pack.MkdNgrams
import com.makeeb.tools.dictionaries.CorpusTokens
import java.lang.management.ManagementFactory

/**
 * The next-word model on its own, over corpus sentences held out of its counts: before every
 * word, what would the model predict from the text before it, read the way the keyboard reads it
 * ([TextBoundaries.wordsBefore])? Counts top-1 and top-3 hits at sentence starts and mid-sentence
 * (the keyboard shows predictions only mid-sentence), and times each prediction.
 */
class NextWordEvaluation(private val dictionary: MappedDictionary) {
    private val model = requireNotNull(dictionary.nextWords) { "the pack has no next-word statistics" }

    fun run(sentences: List<String>): NextWordReport {
        val starts = Tally()
        val middles = Tally()
        var unpredictable = 0
        var contextMismatches = 0
        val nanos = ArrayList<Long>()
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        var allocated = 0L
        for (sentence in sentences) {
            for (word in CorpusTokens.words(sentence)) {
                val target = dictionary.wordId(word.text, word.sentenceInitial)
                if (target < 0 || dictionary.pack.isOffensive(target)) {
                    unpredictable++
                    continue
                }
                val context = TextBoundaries.wordsBefore(sentence.substring(0, word.start), 2)
                if (word.sentenceInitial != (context.words.isEmpty() && context.fromSentenceStart)) contextMismatches++
                val bytesBefore = threads.currentThreadAllocatedBytes
                val started = System.nanoTime()
                val predicted = model.predict(context.words, context.fromSentenceStart, 3)
                nanos += System.nanoTime() - started
                allocated += threads.currentThreadAllocatedBytes - bytesBefore
                val text = dictionary.pack.wordText(target)
                val tally = if (word.sentenceInitial) starts else middles
                tally.places++
                if (predicted.firstOrNull()?.word == text) tally.best++
                if (predicted.any { it.word == text }) tally.top3++
            }
        }
        nanos.sort()
        return NextWordReport(starts, middles, unpredictable, contextMismatches, nanos, allocated / nanos.size.coerceAtLeast(1))
    }

    class Tally {
        var places = 0
        var best = 0
        var top3 = 0
    }

    companion object {
        /** The NGRM section's size and where its bytes go. */
        fun describe(dictionary: MappedDictionary): String {
            val pack = dictionary.pack
            val ngrams = MkdNgrams.open(pack) ?: return "(no next-word statistics)"
            val size = pack.sectionSizes.getValue("NGRM")
            val c = ngrams.bigramContexts
            val t = ngrams.trigramContexts
            val bigramIndex = c + 4 * ((c + 31) / 32)
            val trigramIndex = 7 * t + 4 * ((t + 31) / 32)
            return "(NGRM ${"%.1f".format(size / 1024.0)} KiB: ${pack.meta["bigrams"]} bigrams in $c contexts, " +
                "${pack.meta["trigrams"]} trigrams in $t contexts; indexes ${"%.0f".format(bigramIndex / 1024.0)} + " +
                "${"%.0f".format(trigramIndex / 1024.0)} KiB, lists ${"%.0f".format((size - bigramIndex - trigramIndex) / 1024.0)} KiB)"
        }
    }
}

data class NextWordReport(
    val starts: NextWordEvaluation.Tally,
    val middles: NextWordEvaluation.Tally,
    val unpredictable: Int,
    val contextMismatches: Int,
    val nanos: List<Long>,
    val bytesPerPrediction: Long,
) {
    fun format(): String {
        fun pct(part: Int, whole: Int) = "%.1f%%".format(100.0 * part / whole.coerceAtLeast(1))
        fun micros(p: Int) = "%.1f".format(nanos.getOrElse(((nanos.size - 1) * p) / 100) { 0 } / 1000.0)
        val all = starts.places + middles.places
        return """
            |next-word model on held-out text: ${all + unpredictable} words, $unpredictable not in the lexicon or offensive (can't be predicted)
            |  mid-sentence (where the strip predicts): ${middles.places} words, best ${pct(middles.best, middles.places)}, top 3 ${pct(middles.top3, middles.places)}
            |  sentence starts: ${starts.places} words, best ${pct(starts.best, starts.places)}, top 3 ${pct(starts.top3, starts.places)}
            |  all words: best ${pct(starts.best + middles.best, all + unpredictable)}, top 3 ${pct(starts.top3 + middles.top3, all + unpredictable)}
            |  cost per prediction on the JVM: p50 ${micros(50)} µs, p95 ${micros(95)} µs, max ${micros(100)} µs; $bytesPerPrediction bytes allocated
            |  contexts where the corpus tokeniser and the keyboard disagree: $contextMismatches
        """.trimMargin()
    }
}
