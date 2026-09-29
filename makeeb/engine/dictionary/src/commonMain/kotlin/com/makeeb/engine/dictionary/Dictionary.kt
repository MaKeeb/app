package com.makeeb.engine.dictionary

/** A word and its relative frequency (1 = rare … 255 = most common). */
data class WordEntry(val word: String, val frequency: Int)

/** A dictionary match with its Levenshtein distance from the query. */
data class WordMatch(val entry: WordEntry, val edits: Int)

/**
 * Read-only word lookup for one language. Matching is case-insensitive; entries keep their
 * canonical casing ("London", "I").
 */
interface Dictionary {
    val languageTag: String

    /**
     * A full lexicon for its language, so a word it lacks is most likely misspelt. False for
     * starter lists and personal dictionaries: autocorrecting against those turns real words into
     * listed ones.
     */
    val isComprehensive: Boolean get() = false

    fun lookup(word: String): WordEntry?

    /** Words starting with [prefix], most frequent first. */
    fun completions(prefix: String, limit: Int): List<WordEntry>

    /** Words within [maxEdits] edits of [word], closest then most frequent first. */
    fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch>

    /** Every entry, for decoders that scan the vocabulary (e.g. gesture typing). */
    fun entries(): Sequence<WordEntry>

    /** Next-word statistics, when the dictionary has them (a pack with an NGRM section). */
    val nextWords: NextWordModel? get() = null
}

/** A [Dictionary] the user adds to: learned words, shortcuts, the personal dictionary. */
interface MutableDictionary : Dictionary {
    fun learn(word: String)

    fun forget(word: String)

    /** Whether [word], in any case, was added (unlike [lookup], which also finds other accents). */
    fun isLearned(word: String): Boolean
}
