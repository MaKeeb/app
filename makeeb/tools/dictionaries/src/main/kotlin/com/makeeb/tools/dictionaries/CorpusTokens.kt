package com.makeeb.tools.dictionaries

import com.makeeb.core.common.TextBoundaries
import java.text.Normalizer

/** A word in a corpus sentence: its text, where it starts, and whether it starts a sentence. */
data class CorpusWord(val text: String, val start: Int, val sentenceInitial: Boolean)

/**
 * Splits corpus text into words the way the keyboard reads the text before the caret
 * ([TextBoundaries.wordsBefore]): word characters as [TextBoundaries.isWordChar] has them,
 * trimmed of edge apostrophes and hyphens; punctuation inside a sentence is passed over; a run
 * that [TextBoundaries.endsSentence] starts a new sentence. So the contexts counted here are the
 * contexts the keyboard asks about.
 */
object CorpusTokens {
    /** NFC with typographic apostrophes as typed on the keyboard, as the AOSP list is normalised. */
    fun normalise(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).replace('’', '\'').replace('ʼ', '\'')

    fun words(sentence: String): List<CorpusWord> {
        val out = ArrayList<CorpusWord>()
        var initial = true
        var i = 0
        val n = sentence.length
        while (i < n) {
            val separatorStart = i
            while (i < n && !TextBoundaries.isWordChar(sentence[i])) i++
            if (separatorStart > 0 && i < n && TextBoundaries.endsSentence(sentence.subSequence(separatorStart, i))) initial = true
            if (i == n) break
            val start = i
            while (i < n && TextBoundaries.isWordChar(sentence[i])) i++
            val word = TextBoundaries.trimWord(sentence.subSequence(start, i))
            if (word.isEmpty()) continue
            var lead = start
            while (sentence[lead] != word[0]) lead++
            out += CorpusWord(word, lead, initial)
            initial = false
        }
        return out
    }
}
