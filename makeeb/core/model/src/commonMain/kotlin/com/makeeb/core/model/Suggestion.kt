package com.makeeb.core.model

/** One candidate in the suggestion strip. */
data class Suggestion(
    val text: String,
    val kind: Kind,
    /** Higher is better. Only comparable between suggestions from the same engine call. */
    val score: Double = 0.0,
) {
    enum class Kind {
        /** The literal word being typed, offered so the user can keep it. */
        Typed,

        /** The typed prefix, completed to a dictionary word. */
        Completion,

        /** A spelling correction of the typed word. */
        Correction,

        /** A word predicted after the previous one, before anything is typed. */
        NextWord,

        /** A candidate from a swipe (glide) gesture. */
        Gesture,
    }
}

/**
 * Strip order for up to three suggestions, best in the middle where the thumb rests:
 * `[best, second, third]` → `[second, best, third]`. Every renderer uses this.
 */
fun List<Suggestion>.inStripOrder(): List<Suggestion> =
    if (size >= 3) listOf(this[1], this[0], this[2]) + drop(3) else this
