package com.makeeb.engine.dictionary

/** A word predicted to come next. */
data class NextWord(
    /** The canonical spelling ("the", "London", "I"). */
    val word: String,
    /** Its lexicon frequency, as in [WordEntry]. */
    val frequency: Int,
    /**
     * log2 of its stupid-backoff score: 0 would be certain, and every order the prediction had to
     * back off to costs more. Comparable only within one [NextWordModel.predict] call.
     */
    val score: Float,
)

/**
 * Which words follow the one or two before the caret: trigrams, then bigrams, then the commonest
 * words, ranked by stupid backoff (Brants et al. 2007; docs/research/dictionaries-autocorrect.md
 * §4.2). Statistics only: counts from a corpus, no learning here.
 */
interface NextWordModel {
    /**
     * Up to [limit] words likely to come after [previous], best first. [previous] holds the words
     * before the caret within its sentence, oldest first; only the last two count.
     * [fromSentenceStart] says nothing but a sentence start comes before them (then the first of
     * them may be capitalised only because it starts the sentence, and with no words at all the
     * next word starts one). Offensive words are never offered. Words come in their canonical
     * spelling: capitalising a sentence's first word is the caller's job, as with typed letters.
     */
    fun predict(previous: List<String>, fromSentenceStart: Boolean, limit: Int): List<NextWord>
}
