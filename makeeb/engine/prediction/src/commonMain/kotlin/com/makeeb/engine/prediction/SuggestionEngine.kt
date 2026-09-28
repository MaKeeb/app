package com.makeeb.engine.prediction

import com.makeeb.core.model.Suggestion

/** What the engine knows about the caret position when asking for suggestions. */
data class TypingContext(
    /** The partial word being typed; empty right after a space. */
    val composing: String,
    /** Complete words before it, oldest first (for next-word prediction). */
    val previousWords: List<String> = emptyList(),
)

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
