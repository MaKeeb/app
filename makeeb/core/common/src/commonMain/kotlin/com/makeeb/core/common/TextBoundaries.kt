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

    /**
     * The words a next-word model conditions on: up to [count] complete words before the current
     * one, oldest first, within the current sentence. Punctuation inside a sentence (commas,
     * quotes, brackets) is skipped, so "Hi, how are" gives `[how, are]`; a sentence end
     * ([endsSentence]) or the start of the text stops the search and sets
     * [SentenceWords.fromSentenceStart]. The dictionary builder tokenises its corpora by the same
     * rules, so the contexts it counts are the ones the keyboard asks about.
     */
    fun wordsBefore(textBeforeCursor: CharSequence, count: Int): SentenceWords {
        var end = textBeforeCursor.length
        while (end > 0 && isWordChar(textBeforeCursor[end - 1])) end--
        val words = ArrayList<String>(count)
        while (true) {
            var start = end
            while (start > 0 && !isWordChar(textBeforeCursor[start - 1])) start--
            if (start == 0 || endsSentence(textBeforeCursor.subSequence(start, end))) {
                return SentenceWords(words.asReversed().toList(), fromSentenceStart = true)
            }
            if (words.size == count) return SentenceWords(words.asReversed().toList(), fromSentenceStart = false)
            end = start
            while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
            val word = trimWord(textBeforeCursor.subSequence(start, end))
            if (word.isNotEmpty()) words += word
            end = start
        }
    }

    /**
     * Whether the non-word characters between two words end a sentence: a line break, or a
     * sentence terminator with whitespace after it (closing quotes and brackets may come between:
     * `Stop." Then`). "3.5" and "e.g" don't; "Hi. Then" does, as [isSentenceStart] would say.
     */
    fun endsSentence(separators: CharSequence): Boolean {
        var terminator = false
        for (char in separators) {
            when {
                char == '\n' -> return true
                char in sentenceTerminators -> terminator = true
                char.isWhitespace() && terminator -> return true
            }
        }
        return false
    }

    /** A run of [isWordChar]s without the apostrophes and hyphens at its ends: "'hello'" → "hello". */
    fun trimWord(run: CharSequence): String = run.trim('\'', '’', '-').toString()

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

/** The words before the caret in its sentence ([TextBoundaries.wordsBefore]). */
data class SentenceWords(
    /** Oldest first. */
    val words: List<String>,
    /** Nothing but the start of the sentence (or of the text) comes before [words]. */
    val fromSentenceStart: Boolean,
)
