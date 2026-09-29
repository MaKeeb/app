package com.makeeb.engine.dictionary

/**
 * The dictionary for the languages the user selected. The [primary] language's dictionary
 * answers everything: completions, corrections, next words. The [others] only vouch for words:
 * [lookup] finds a word any of them spells exactly (ignoring case), so a word valid in another
 * selected language is never mistaken for a typo, and [covers] their languages, so autocorrect
 * runs once every selected language has a lexicon. Offering the other languages' words as
 * suggestions, and choosing between languages as the user types, is multilingual typing (board
 * card APP-18), not this.
 */
class SelectedDictionaries(private val primary: Dictionary, private val others: List<Dictionary>) : Dictionary {
    override val languageTag: String get() = primary.languageTag

    override val isComprehensive: Boolean get() = primary.isComprehensive

    override fun covers(languageTag: String): Boolean = primary.covers(languageTag) || others.any { it.covers(languageTag) }

    /**
     * A spelling of [word] itself (ignoring case) wins, the primary's first: typed "hello" is an
     * English word, not Hungarian "helló" missing its accent. Only then the primary's refolded
     * spelling ("cafe" → "café"). Never another language's refolded one: that would turn a word
     * into another language's ("im" into English "I'm").
     */
    override fun lookup(word: String): WordEntry? {
        val own = primary.lookup(word)
        if (own != null && own.word.equals(word, ignoreCase = true)) return own
        return others.firstNotNullOfOrNull { other -> other.lookup(word)?.takeIf { it.word.equals(word, ignoreCase = true) } } ?: own
    }

    override fun completions(prefix: String, limit: Int): List<WordEntry> = primary.completions(prefix, limit)

    override fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch> = primary.corrections(word, maxEdits, limit)

    override fun entries(): Sequence<WordEntry> = primary.entries()

    override val nextWords: NextWordModel? get() = primary.nextWords
}
