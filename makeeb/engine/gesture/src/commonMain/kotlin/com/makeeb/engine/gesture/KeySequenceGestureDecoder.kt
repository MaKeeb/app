package com.makeeb.engine.gesture

import com.makeeb.core.model.Suggestion
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.layout.LayoutGeometry
import kotlin.math.ln

/**
 * Baseline swipe decoder: reduce the path to the sequence of keys it crossed, then keep
 * dictionary words that start on the first key, end on the last, and whose letters appear in
 * order along the path. Ranked by frequency, preferring words that use more of the path.
 *
 * Good enough to exercise the pipeline end to end. A shape/location-channel decoder (SHARK2
 * style) replaces it later (board: `gesture-typing`).
 */
class KeySequenceGestureDecoder(private val dictionary: Dictionary) : GestureDecoder {

    override fun decode(path: List<GesturePoint>, geometry: LayoutGeometry, limit: Int): List<Suggestion> {
        val keys = keySequence(path, geometry)
        if (keys.length < 2) return emptyList()
        val first = keys.first()
        val last = keys.last()

        return dictionary.entries()
            .filter { entry ->
                val word = entry.word.lowercase()
                word.length >= 2 && word.first() == first && word.last() == last && isOrderedSubsequence(word, keys)
            }
            .map { entry ->
                val coverage = entry.word.length.toDouble() / keys.length
                Suggestion(entry.word, Suggestion.Kind.Gesture, ln(entry.frequency.toDouble() + 1) + coverage)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    /** Letters under the path, with consecutive repeats collapsed ("hhheelllo" → "helo"). */
    internal fun keySequence(path: List<GesturePoint>, geometry: LayoutGeometry): String = buildString {
        path.forEach { point ->
            val char = geometry.keyAt(point.x, point.y)?.key?.label?.singleOrNull()?.lowercaseChar()
            if (char != null && char.isLetter() && (isEmpty() || last() != char)) append(char)
        }
    }

    /** Word letters in order within the key sequence; double letters may share one key visit. */
    private fun isOrderedSubsequence(word: String, keys: String): Boolean {
        var k = 0
        word.forEachIndexed { index, char ->
            if (index > 0 && char == word[index - 1]) return@forEachIndexed
            while (k < keys.length && keys[k] != char) k++
            if (k == keys.length) return false
            k++
        }
        return true
    }
}
