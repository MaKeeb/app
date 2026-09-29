package com.makeeb.engine.prediction

import com.makeeb.core.model.AutocorrectStrength
import com.makeeb.core.model.Suggestion
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.KeyFold
import com.makeeb.engine.dictionary.MutableDictionary
import com.makeeb.engine.dictionary.NextWord
import com.makeeb.engine.dictionary.NextWordModel
import com.makeeb.engine.dictionary.WordEntry

/**
 * Completions and spelling corrections from word lists. Corrections are costed as typing errors
 * ([WeightedEdits], keyboard-aware) plus how unlikely the word is, the noisy-channel model of
 * docs/research/dictionaries-autocorrect.md §6.5; autocorrect needs the best one to beat the typed
 * word by a margin.
 *
 * With nothing typed, the main dictionary's [NextWordModel] predicts the next word from the one or
 * two before it. While a word is typed, the same predictions raise the completions they contain
 * (§6.6): after "good", "l" puts "luck" ahead of commoner l-words. They are worked out once
 * per context, so typing a word costs no more than before. Neither changes what autocorrect does.
 */
class DictionarySuggestionEngine(
    private val main: Dictionary,
    private val user: MutableDictionary? = null,
    /** How much the context raises a completion it predicts; 0 turns that off (the harness compares both). */
    private val contextWeight: Double = CONTEXT_WEIGHT,
) : SuggestionEngine {

    private val dictionaries: List<Dictionary> get() = listOfNotNull(user, main)

    /** The predictions for the last context asked about. */
    private var following: Following? = null

    private class Following(val model: NextWordModel?, val previous: List<String>, val fromSentenceStart: Boolean, val words: List<NextWord>) {
        val keys: List<String> = words.map { KeyFold.fold(it.word) }
    }

    override fun suggest(context: TypingContext, limit: Int): Prediction {
        val typed = context.composing
        val following = following(context)
        if (typed.isEmpty()) {
            return Prediction(following.words.take(limit).map { Suggestion(it.word, Suggestion.Kind.NextWord, it.score.toDouble()) })
        }

        val candidates = HashMap<String, Suggestion>()
        var best: String? = null
        var bestCost = Float.MAX_VALUE
        var bestFrequency = 0
        fun offer(word: String, kind: Suggestion.Kind, score: Double) {
            val key = word.lowercase()
            val existing = candidates[key]
            if (existing == null || score > existing.score) {
                candidates[key] = Suggestion(matchCase(word, typed), kind, score)
            }
        }

        val exact = dictionaries.firstNotNullOfOrNull { it.lookup(typed) }
        exact?.let { offer(it.word, Suggestion.Kind.Typed, normalise(it.frequency) + EXACT_BONUS) }

        // Completions the context predicts, raised by how likely it makes them.
        val raised = HashSet<String>()
        if (contextWeight > 0.0 && following.words.isNotEmpty()) {
            val key = KeyFold.fold(typed)
            following.words.forEachIndexed { i, next ->
                val nextKey = following.keys[i]
                if (nextKey.length > key.length && nextKey.startsWith(key)) {
                    val boost = contextWeight * (1.0 + next.score / CONTEXT_RANGE).coerceAtLeast(0.0)
                    offer(next.word, Suggestion.Kind.Completion, normalise(next.frequency) - COMPLETION_PENALTY + boost)
                    raised += next.word.lowercase()
                }
            }
        }

        dictionaries.forEach { dictionary ->
            dictionary.completions(typed, limit * 3).forEach { entry ->
                if (!entry.word.equals(typed, ignoreCase = true)) {
                    offer(entry.word, Suggestion.Kind.Completion, normalise(entry.frequency) - COMPLETION_PENALTY)
                }
            }
            val maxEdits = maxEditsFor(typed, context.strength)
            if (maxEdits > 0) {
                // Candidates within plain edits, then costed as typing errors (WeightedEdits).
                dictionary.corrections(typed, maxEdits, CORRECTION_CANDIDATES).forEach { match ->
                    if (match.edits > 0) {
                        val cost = WeightedEdits.distance(typed, match.entry.word, context.keys, context.taps) + LM_WEIGHT * lmCost(match.entry.frequency)
                        if (cost < bestCost && mayCorrectTo(match.entry, typed)) {
                            bestCost = cost
                            best = match.entry.word
                            bestFrequency = match.entry.frequency
                        }
                        offer(match.entry.word, Suggestion.Kind.Correction, -cost.toDouble())
                    }
                }
            }
        }

        // Only known typos are corrected on space (KnownTypos), even where a large word list has
        // the typo as a rare word ("cant", "wont"); then a proper noun's capital ("london" →
        // "London", "i" → "I"), never an acronym's ("nad" is not "NAD"). Other near misses stay
        // suggestions.
        val capitalised = exact?.word?.takeIf { it != typed && typed == typed.lowercase() && it.isTitleCase() }
        // Keys fold accents and apostrophes, so a typed form that isn't a word itself finds the
        // one it lacks them for: "im" → "I'm", "cafe" → "café". "its", "were", "ill" are words.
        val refolded = exact?.word?.takeIf { !it.equals(typed, ignoreCase = true) }?.let { matchCase(it, typed) }
        // A real typo: the best candidate must beat keeping the typed word by a margin (§6.5, §6.7).
        // Only against a full lexicon: with a starter list, "not listed" doesn't mean misspelt.
        val typo = best?.takeIf {
            main.isComprehensive && exact == null && mayCorrect(context) && bestCost + margin(typed, bestFrequency, context.strength) < literalCost()
        }?.let { matchCase(it, typed) }
        // A known learned word (typed twice, or kept once) is the user's, even where it looks like a known typo.
        val userWord = user?.lookup(typed)?.word.equals(typed, ignoreCase = true)
        val correction = KnownTypos.correctionFor(typed)?.takeIf { !userWord }?.let { matchCase(it, typed) } ?: refolded ?: capitalised ?: typo
        correction?.let { offer(it, Suggestion.Kind.Correction, Double.MAX_VALUE) }
        // With a selected language the dictionary can't judge, the correction stays one tap away.
        val autoCorrection = correction?.takeIf { covers(context.languages) }
        val ranked = candidates.values.sortedByDescending { it.score }
        var shown = ranked.take(limit)
        // The context may raise completions past a known word as typed, which space keeps: it
        // keeps a slot all the same.
        val typedWord = exact?.let { candidates[it.word.lowercase()] }
        if (typedWord != null && autoCorrection == null && limit > 1 && typedWord !in shown && shown.any { it.text.lowercase() in raised }) {
            shown = shown.take(limit - 1) + typedWord
        }

        // Offer the literal typed text when it is not a known word, so the user can keep it.
        val typedOption = if (exact == null) listOf(Suggestion(typed, Suggestion.Kind.Typed, Double.NEGATIVE_INFINITY)) else emptyList()
        val suggestions = (shown + typedOption).distinctBy { it.text }.take(limit.coerceAtLeast(1))
        return Prediction(suggestions, autoCorrection)
    }

    /** What the context predicts, from the cache while the context stays the same. */
    private fun following(context: TypingContext): Following {
        val model = main.nextWords
        val previous = context.previousWords.takeLast(CONTEXT_WORDS)
        val fromSentenceStart = if (previous.isEmpty()) context.atSentenceStart else context.previousWordsStartSentence
        following?.let { if (it.model === model && it.fromSentenceStart == fromSentenceStart && it.previous == previous) return it }
        val words = model?.predict(previous, fromSentenceStart, CONTEXT_CANDIDATES).orEmpty()
        return Following(model, previous, fromSentenceStart, words).also { following = it }
    }

    override fun learn(word: String) = learn(word, kept = false)

    override fun keep(word: String) = learn(word, kept = true)

    /**
     * Only words the main dictionary lacks: it knows the rest already, and they would crowd out the
     * new ones. The user dictionary counts a word as known (and stops it being corrected) only
     * once it is committed twice or [kept] once.
     */
    private fun learn(word: String, kept: Boolean) {
        if (word.length < MIN_LEARNED_LENGTH || main.lookup(word) != null) return
        user?.learn(word, kept)
    }

    override fun isLearned(word: String): Boolean = user?.isLearned(word) == true && main.lookup(word) == null

    override fun forget(word: String) {
        if (isLearned(word)) user?.forget(word)
    }

    /**
     * How far the candidate search reaches. Aggressive looks further (two-letter words, a second
     * slip from five letters) rather than lowering the bar: in the typing harness a lower bar
     * mostly changed names and slang, while the typos left unfixed were ones the search never
     * reached (fixed 72.6% → 79.1%, names and slang changed 1 of 27 either way).
     */
    private fun maxEditsFor(word: String, strength: AutocorrectStrength): Int = when (strength) {
        AutocorrectStrength.Aggressive -> when {
            word.length <= 4 -> 1
            else -> 2
        }
        else -> when {
            word.length <= 2 -> 0
            word.length <= 5 -> 1
            else -> 2
        }
    }

    private fun normalise(frequency: Int): Double = frequency / 255.0

    /** Carry the user's casing over: "Teh" → "The", "TEH" → "THE"; canonical case otherwise ("i" → "I"). */
    private fun matchCase(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    /**
     * LatinIME's "never autocorrect" cases that apply to a word the dictionary doesn't know:
     * digits, capitals beyond the first letter ("NASA", "iOS"), and a capitalised word mid-sentence,
     * which is most likely a name. Field and user settings are the input engine's to check.
     */
    private fun mayCorrect(context: TypingContext): Boolean {
        val typed = context.composing
        if (typed.length < 2 || typed.any { it.isDigit() }) return false
        if (typed.drop(1).any { it.isUpperCase() }) return false
        return !(typed.first().isUpperCase() && !context.atSentenceStart)
    }

    /** A lower-case word never becomes a name ("zelle" is not "Belle"); a capitalised one may. */
    private fun mayCorrectTo(target: WordEntry, typed: String): Boolean =
        target.word == target.word.lowercase() || typed.first().isUpperCase()

    /**
     * Short words need a clearer win (a slip in "if" makes many other words), and so do rare
     * targets: people type rare words on purpose more often than they mistype them. Modest raises
     * the whole bar; Aggressive keeps it and searches further instead ([maxEditsFor]).
     */
    private fun margin(typed: String, targetFrequency: Int, strength: AutocorrectStrength): Float =
        MARGIN + (if (typed.length <= 3) SHORT_WORD_MARGIN else 0f) + (if (targetFrequency < RARE_FREQUENCY) RARE_TARGET_MARGIN else 0f) +
            (if (strength == AutocorrectStrength.Modest) MODEST_MARGIN else 0f)

    /** Whether the main dictionary covers every one of [languages]: the primary's, and any pack that vouches for words ([Dictionary.covers]). */
    private fun covers(languages: List<String>): Boolean = languages.all(main::covers)

    /** The cost of a word from its frequency: 0 for the commonest, [LM_RANGE] for the rarest. */
    private fun lmCost(frequency: Int): Float = (MAX_FREQUENCY - frequency.coerceIn(0, MAX_FREQUENCY)) * LM_RANGE / MAX_FREQUENCY

    /** Keeping the typed word as it is: an unknown word, as unlikely as the rarest known one. */
    private fun literalCost(): Float = LM_WEIGHT * LM_RANGE

    /** "London", "I": a capital first letter and nothing else in capitals. */
    private fun String.isTitleCase(): Boolean = first().isUpperCase() && drop(1).none(Char::isUpperCase)

    private companion object {
        const val EXACT_BONUS = 0.5
        const val COMPLETION_PENALTY = 0.05
        const val CORRECTION_CANDIDATES = 24
        const val MAX_FREQUENCY = 255
        const val LM_RANGE = 2.0f
        const val LM_WEIGHT = 1.1214f
        const val RARE_FREQUENCY = 100
        const val RARE_TARGET_MARGIN = 0.5f
        /**
         * How much better than the typed word a correction must be. Tuned on the typing harness
         * (2026-09-29): 0.8 keeps ~73% of neighbour-key typos fixed while leaving 26 of 27 unknown
         * names and slang words alone; 0.4 fixed ~1 point more and changed 6.
         */
        const val MARGIN = 0.8f
        const val SHORT_WORD_MARGIN = 0.3f

        /** Modest's higher bar: in the typing harness it fixes 53.8% of typos (Normal 72.6%) and changes no name or slang. */
        const val MODEST_MARGIN = 0.25f
        const val MIN_LEARNED_LENGTH = 2
        const val CONTEXT_WORDS = 2

        /** Predictions kept per context: enough to cover most completions it would raise. */
        const val CONTEXT_CANDIDATES = 48

        /**
         * A predicted completion gains up to [CONTEXT_WEIGHT], less the less likely the context
         * makes it: nothing at [CONTEXT_RANGE] bits below certainty. Tuned on the typing harness
         * (2026-09-29): completion keystroke savings rise from 34.7% without the boost to 39.8%
         * at 0.3, 41.0% at 0.6 and 41.4% at 1.0 (40.4% at 0.6 on held-out news and web text);
         * typo fixes and false corrections don't change, since autocorrect doesn't look at it.
         */
        const val CONTEXT_WEIGHT = 0.6
        const val CONTEXT_RANGE = 12.0
    }
}
