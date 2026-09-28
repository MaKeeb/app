package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.pack.MkdWord
import java.text.Normalizer

/**
 * An AOSP LatinIME `.combined` word list: a header line, then one ` word=…,f=…,…` line per word,
 * optionally followed by indented `shortcut=` or `bigram=` lines.
 *
 * Kept: the spelling with its case (proper nouns and acronyms stay capitalised), the 0–255
 * frequency `f`, and `possibly_offensive`. Skipped: `not_a_word` entries, which exist only to
 * carry a shortcut ("im" → "I'm"), and the shortcut and bigram lines (Stage 3 and 5 data).
 */
class AospWordList(
    val header: Map<String, String>,
    val words: List<MkdWord>,
    val skippedNotAWord: Int,
    val skippedShortcuts: Int,
    val skippedBigrams: Int,
) {
    /** "en_US" → "en-US". */
    val languageTag: String get() = header.getValue("locale").replace('_', '-')

    companion object {
        fun parse(lines: Sequence<String>): AospWordList {
            var header: Map<String, String>? = null
            val words = ArrayList<MkdWord>()
            var notAWord = 0
            var shortcuts = 0
            var bigrams = 0
            for (line in lines) {
                if (line.isBlank()) continue
                val trimmed = line.trimStart()
                when {
                    header == null -> header = attributes(line)
                    trimmed.startsWith("word=") -> {
                        // The spelling runs up to ",f=", so it may itself contain commas.
                        val frequencyAt = trimmed.indexOf(",f=")
                        require(frequencyAt > 5) { "no frequency: $line" }
                        val text = normalise(trimmed.substring(5, frequencyAt))
                        val attributes = attributes(trimmed.substring(frequencyAt + 1))
                        if (attributes["not_a_word"] == "true") {
                            notAWord++
                            continue
                        }
                        val frequency = attributes.getValue("f").toInt()
                        words += MkdWord(text, frequency, offensive = attributes["possibly_offensive"] == "true")
                    }
                    trimmed.startsWith("shortcut=") -> shortcuts++
                    trimmed.startsWith("bigram=") -> bigrams++
                    else -> error("unexpected line: $line")
                }
            }
            return AospWordList(requireNotNull(header) { "empty word list" }, words, notAWord, shortcuts, bigrams)
        }

        private fun attributes(text: String): Map<String, String> =
            text.split(',').filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=') }

        /** NFC, and typographic apostrophes folded to ASCII ones as typed on the keyboard. */
        private fun normalise(word: String): String = Normalizer.normalize(word, Normalizer.Form.NFC).replace('’', '\'')
    }
}
