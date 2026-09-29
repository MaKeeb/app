package com.makeeb.engine.prediction

import com.makeeb.core.model.AutocorrectStrength
import com.makeeb.core.model.Suggestion

/** What the engine knows about the caret position when asking for suggestions. */
data class TypingContext(
    /** The partial word being typed; empty right after a space, when the next word is predicted. */
    val composing: String,
    /**
     * Complete words before it in the same sentence, oldest first: the context next-word
     * statistics condition on (`TextBoundaries.wordsBefore`).
     */
    val previousWords: List<String> = emptyList(),
    /** Where the letters sit on the layout in use, so neighbouring-key slips cost less. */
    val keys: KeyPositions? = null,
    /** The word starts a sentence: a capital there doesn't mean a name. */
    val atSentenceStart: Boolean = true,
    /** Where each letter of [composing] was tapped, when known (same length, else ignored). */
    val taps: List<TapPoint?> = emptyList(),
    /** Nothing but a sentence start comes before [previousWords]. */
    val previousWordsStartSentence: Boolean = false,
    /** How sure a correction must be before it replaces the typed word. */
    val strength: AutocorrectStrength = AutocorrectStrength.Normal,
    /**
     * The languages the user types (BCP 47). Words are only replaced automatically when the
     * dictionary covers all of them: without a Hungarian lexicon, a Hungarian word looks like an
     * English typo. Empty means the dictionary's own language.
     */
    val languages: List<String> = emptyList(),
)

/** A tap on the letters layout, in key widths across and rows down (the [KeyPositions] units). */
data class TapPoint(val x: Float, val y: Float)

data class Prediction(
    /** Best first. The UI decides how to lay them out. */
    val suggestions: List<Suggestion>,
    /** The word to replace [TypingContext.composing] with on space/punctuation, if confident. */
    val autoCorrection: String? = null,
) {
    companion object {
        val Empty = Prediction(emptyList())
    }
}

interface SuggestionEngine {
    /**
     * Suggestions for the word being typed, or, when [TypingContext.composing] is empty, the words
     * likely to come next ([Suggestion.Kind.NextWord]; none when the engine has no statistics).
     */
    fun suggest(context: TypingContext, limit: Int = 3): Prediction

    /**
     * Record a word the user committed. Never called for incognito fields. Once is not enough to
     * stop correcting it: it may be a typo that slipped through with autocorrect off or paused.
     */
    fun learn(word: String)

    /**
     * Record a word the user kept on purpose: picked as typed from the strip, or restored by undoing
     * its autocorrection. Once is enough to stop correcting it.
     */
    fun keep(word: String) = learn(word)

    /** Whether [word] is one the engine learned from the user rather than a dictionary word; only those can be forgotten. */
    fun isLearned(word: String): Boolean = false

    /** Stops suggesting a learned word and drops it from what was learned. Dictionary words stay. */
    fun forget(word: String) = Unit
}
