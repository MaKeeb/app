package com.makeeb.engine.prediction

import com.makeeb.core.model.Suggestion

/** What the engine knows about the caret position when asking for suggestions. */
data class TypingContext(
    /** The partial word being typed; empty right after a space. */
    val composing: String,
    /** Complete words before it, oldest first (for next-word prediction). */
    val previousWords: List<String> = emptyList(),
    /** Where the letters sit on the layout in use, so neighbouring-key slips cost less. */
    val keys: KeyPositions? = null,
    /** The word starts a sentence: a capital there doesn't mean a name. */
    val atSentenceStart: Boolean = true,
    /** Where each letter of [composing] was tapped, when known (same length, else ignored). */
    val taps: List<TapPoint?> = emptyList(),
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
    fun suggest(context: TypingContext, limit: Int = 3): Prediction

    /** Record a word the user committed. Never called for incognito fields. */
    fun learn(word: String)
}
