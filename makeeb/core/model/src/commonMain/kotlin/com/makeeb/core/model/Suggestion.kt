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

        /** Punctuation offered right after a word and a space; it takes the space's place. */
        Punctuation,
    }
}

/**
 * The strip's cells, left to right; every renderer uses this. Words fill three fixed slots with
 * the best in the middle, where the thumb rests, and missing ones stay empty (null) so the best
 * never moves: `[best, second, third]` → `[second, best, third]`. Punctuation shows in order.
 */
fun List<Suggestion>.stripSlots(): List<Suggestion?> = when {
    isEmpty() -> emptyList()
    all { it.kind == Suggestion.Kind.Punctuation } -> this
    else -> listOf(getOrNull(1), this[0], getOrNull(2))
}

/** Which of [stripSlots] is the best suggestion, drawn in bold; -1 for punctuation. */
fun List<Suggestion>.bestStripSlot(): Int =
    if (isEmpty() || all { it.kind == Suggestion.Kind.Punctuation }) -1 else 1
