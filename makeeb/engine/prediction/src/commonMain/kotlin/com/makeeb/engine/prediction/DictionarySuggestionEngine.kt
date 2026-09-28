package com.makeeb.engine.prediction

import com.makeeb.core.model.Suggestion
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.MutableDictionary

/**
 * Completions and spelling corrections from word lists, ranked by frequency minus an edit
 * penalty. This is the baseline; key-proximity weighting and n-gram next-word prediction build
 * on it (board: `proximity-correction`, APP-38).
 */
class DictionarySuggestionEngine(
    private val main: Dictionary,
    private val user: MutableDictionary? = null,
) : SuggestionEngine {

    private val dictionaries: List<Dictionary> get() = listOfNotNull(user, main)

    override fun suggest(context: TypingContext, limit: Int): Prediction {
        val typed = context.composing
        if (typed.isEmpty()) return Prediction.Empty

        val candidates = HashMap<String, Suggestion>()
        fun offer(word: String, kind: Suggestion.Kind, score: Double) {
            val key = word.lowercase()
            val existing = candidates[key]
            if (existing == null || score > existing.score) {
                candidates[key] = Suggestion(matchCase(word, typed), kind, score)
            }
        }

        val exact = dictionaries.firstNotNullOfOrNull { it.lookup(typed) }
        exact?.let { offer(it.word, Suggestion.Kind.Typed, normalise(it.frequency) + EXACT_BONUS) }

        dictionaries.forEach { dictionary ->
            dictionary.completions(typed, limit * 3).forEach { entry ->
                if (!entry.word.equals(typed, ignoreCase = true)) {
                    offer(entry.word, Suggestion.Kind.Completion, normalise(entry.frequency) - COMPLETION_PENALTY)
                }
            }
            val maxEdits = maxEditsFor(typed)
            if (maxEdits > 0) {
                dictionary.corrections(typed, maxEdits, limit * 3).forEach { match ->
                    if (match.edits > 0) {
                        offer(match.entry.word, Suggestion.Kind.Correction, normalise(match.entry.frequency) - EDIT_PENALTY * match.edits)
                    }
                }
            }
        }

        // Only known typos are corrected on space (KnownTypos), even where a large word list has
        // the typo as a rare word ("cant", "wont"); then a proper noun's capital ("london" →
        // "London", "i" → "I"), never an acronym's ("nad" is not "NAD"). Other near misses stay
        // suggestions.
        val capitalised = exact?.word?.takeIf { it != typed && typed == typed.lowercase() && it.isTitleCase() }
        val autoCorrection = KnownTypos.correctionFor(typed)?.let { matchCase(it, typed) } ?: capitalised
        autoCorrection?.let { offer(it, Suggestion.Kind.Correction, Double.MAX_VALUE) }
        val ranked = candidates.values.sortedByDescending { it.score }

        // Offer the literal typed text when it is not a known word, so the user can keep it.
        val typedOption = if (exact == null) listOf(Suggestion(typed, Suggestion.Kind.Typed, Double.NEGATIVE_INFINITY)) else emptyList()
        val suggestions = (ranked.take(limit) + typedOption).distinctBy { it.text }.take(limit.coerceAtLeast(1))
        return Prediction(suggestions, autoCorrection)
    }

    override fun learn(word: String) {
        if (word.length < MIN_LEARNED_LENGTH) return
        user?.learn(word)
    }

    private fun maxEditsFor(word: String): Int = when {
        word.length <= 2 -> 0
        word.length <= 5 -> 1
        else -> 2
    }

    private fun normalise(frequency: Int): Double = frequency / 255.0

    /** Carry the user's casing over: "Teh" → "The", "TEH" → "THE"; canonical case otherwise ("i" → "I"). */
    private fun matchCase(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    /** "London", "I": a capital first letter and nothing else in capitals. */
    private fun String.isTitleCase(): Boolean = first().isUpperCase() && drop(1).none(Char::isUpperCase)

    private companion object {
        const val EXACT_BONUS = 0.5
        const val COMPLETION_PENALTY = 0.05
        const val EDIT_PENALTY = 0.5
        const val MIN_LEARNED_LENGTH = 2
    }
}
