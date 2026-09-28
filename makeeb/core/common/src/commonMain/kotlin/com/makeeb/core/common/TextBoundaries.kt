package com.makeeb.core.common

/**
 * Word and sentence boundary helpers shared by the engine modules. They work on the small
 * windows of text the platforms expose around the caret, never on whole documents.
 */
object TextBoundaries {
    private val sentenceTerminators = setOf('.', '!', '?', '…', '。', '！', '？')

    /** Characters that continue a word: letters, digits, and in-word apostrophes and hyphens. */
    fun isWordChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '\'' || char == '’' || char == '-'

    /** The word the caret is at the end of, e.g. `"hello wor"` → `"wor"`. Empty after whitespace. */
    fun trailingWord(textBeforeCursor: CharSequence): String {
        var start = textBeforeCursor.length
        while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
        return textBeforeCursor.substring(start).trimStart('\'', '’', '-')
    }

    /** Up to [count] complete words before the current one, oldest first. */
    fun previousWords(textBeforeCursor: CharSequence, count: Int): List<String> {
        val current = trailingWord(textBeforeCursor)
        val before = textBeforeCursor.substring(0, textBeforeCursor.length - current.length)
        return before
            .split(' ', '\n', '\t')
            .map { word -> word.filter(::isWordChar) }
            .filter { it.isNotEmpty() }
            .takeLast(count)
    }

    /**
     * True when the caret sits where a new sentence starts: at the start of the field, after a
     * newline, or after sentence-ending punctuation followed by whitespace.
     */
    fun isSentenceStart(textBeforeCursor: CharSequence): Boolean {
        if (textBeforeCursor.isEmpty()) return true
        val last = textBeforeCursor.last()
        if (last == '\n') return true
        if (!last.isWhitespace()) return false
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return true
        // Skip closing quotes/brackets: `He said "Stop." ` still ends a sentence.
        val significant = trimmed.trimEnd('"', '\'', '”', '’', ')', ']')
        return significant.isNotEmpty() && significant.last() in sentenceTerminators
    }

    /** Characters to move back to the start of the previous word, skipping spaces and punctuation first. */
    fun previousWordStart(textBeforeCursor: CharSequence): Int {
        var i = textBeforeCursor.length
        while (i > 0 && !isWordChar(textBeforeCursor[i - 1])) i--
        while (i > 0 && isWordChar(textBeforeCursor[i - 1])) i--
        return textBeforeCursor.length - i
    }

    /** Characters to move forward to the end of the next word, skipping spaces and punctuation first. */
    fun nextWordEnd(textAfterCursor: CharSequence): Int {
        var i = 0
        while (i < textAfterCursor.length && !isWordChar(textAfterCursor[i])) i++
        while (i < textAfterCursor.length && isWordChar(textAfterCursor[i])) i++
        return i
    }

    /** True when the caret is at the start of a word (field start or after whitespace). */
    fun isWordStart(textBeforeCursor: CharSequence): Boolean =
        textBeforeCursor.isEmpty() || textBeforeCursor.last().isWhitespace()
}
