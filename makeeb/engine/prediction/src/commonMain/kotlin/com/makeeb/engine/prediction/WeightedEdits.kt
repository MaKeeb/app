package com.makeeb.engine.prediction

import com.makeeb.engine.dictionary.KeyFold
import kotlin.math.sqrt

/**
 * Where each letter sits on the keyboard, in key widths, so a slip onto a neighbouring key costs
 * less than a wild substitution. The input engine builds it from the letters layout in use.
 */
fun interface KeyPositions {
    /** The centre of the key typing [char] (lower case), or null when the layout has none. */
    fun centre(char: Char): Pair<Float, Float>?
}

/**
 * How far a typed word is from a candidate, as the cost of the typing errors that would turn one
 * into the other: a weighted optimal-string-alignment distance with LatinIME's tuned costs
 * (Apache-2.0, `scoring_params.cpp`; docs/research/dictionaries-autocorrect.md §6.3). Slips onto
 * a neighbouring key are cheap, skipped letters cost less than extra ones, doubled letters are
 * forgiven, and the first letter is rarely wrong. Both words are compared folded ([KeyFold]), so
 * accents and apostrophes cost nothing here.
 */
object WeightedEdits {
    const val PROXIMITY = 0.0694f
    const val FIRST_PROXIMITY = 0.0779f
    const val SUBSTITUTION = 0.3806f
    const val OMISSION = 0.467f
    const val DOUBLED_OMISSION = 0.345f
    const val FIRST_OMISSION = 0.5256f
    const val INSERTION = 0.7248f
    const val REPEATED_INSERTION = 0.5508f
    const val PROXIMATE_INSERTION = 0.674f
    const val TRANSPOSITION = 0.5608f

    /**
     * Keys whose centres are at most this far apart (in key widths) are neighbours: side by side
     * (1.0) and diagonal across a half-key row offset (~1.5), not two keys over (2.0).
     */
    private const val NEIGHBOUR_DISTANCE = 1.6f

    /** Per squared key width beyond a neighbour: two keys over adds ~0.1, three ~0.5. */
    private const val FAR_KEY_WEIGHT = 0.3f

    /** Rows are taller than keys are wide; this converts a row step into key widths. */
    private const val ROW_ASPECT = 1.4f

    fun distance(typed: String, candidate: String, keys: KeyPositions?): Float {
        val t = KeyFold.fold(typed)
        val c = KeyFold.fold(candidate)
        // rows[i][j]: cost of turning t[0 until i] into c[0 until j].
        val rows = Array(t.length + 1) { FloatArray(c.length + 1) }
        for (j in 1..c.length) rows[0][j] = rows[0][j - 1] + omission(c, j - 1)
        for (i in 1..t.length) {
            rows[i][0] = rows[i - 1][0] + insertion(t, i - 1, keys)
            for (j in 1..c.length) {
                var best = rows[i - 1][j - 1] + substitution(t[i - 1], c[j - 1], first = j == 1, keys)
                best = minOf(best, rows[i][j - 1] + omission(c, j - 1))
                best = minOf(best, rows[i - 1][j] + insertion(t, i - 1, keys))
                if (i > 1 && j > 1 && t[i - 1] == c[j - 2] && t[i - 2] == c[j - 1] && t[i - 1] != t[i - 2]) {
                    best = minOf(best, rows[i - 2][j - 2] + TRANSPOSITION)
                }
                rows[i][j] = best
            }
        }
        return rows[t.length][c.length]
    }

    /** A key further away is a less likely slip: the cost grows with the squared distance. */
    private fun substitution(typed: Char, wanted: Char, first: Boolean, keys: KeyPositions?): Float {
        if (typed == wanted) return 0f
        val d = keyDistance(typed, wanted, keys) ?: return SUBSTITUTION
        if (d <= NEIGHBOUR_DISTANCE) return PROXIMITY + if (first) FIRST_PROXIMITY else 0f
        val beyond = d - NEIGHBOUR_DISTANCE
        return SUBSTITUTION + FAR_KEY_WEIGHT * beyond * beyond
    }

    /** The user skipped [c]'s letter at [j]. */
    private fun omission(c: String, j: Int): Float = when {
        j == 0 -> FIRST_OMISSION
        c[j] == c[j - 1] -> DOUBLED_OMISSION
        else -> OMISSION
    }

    /** The user typed [t]'s letter at [i] too many. */
    private fun insertion(t: String, i: Int, keys: KeyPositions?): Float = when {
        i > 0 && t[i] == t[i - 1] -> REPEATED_INSERTION
        (i > 0 && neighbours(t[i], t[i - 1], keys)) || (i + 1 < t.length && neighbours(t[i], t[i + 1], keys)) -> PROXIMATE_INSERTION
        else -> INSERTION
    }

    private fun neighbours(a: Char, b: Char, keys: KeyPositions?): Boolean =
        (keyDistance(a, b, keys) ?: Float.MAX_VALUE) <= NEIGHBOUR_DISTANCE

    /** Centre to centre, in key widths; null without positions for both. */
    private fun keyDistance(a: Char, b: Char, keys: KeyPositions?): Float? {
        val pa = keys?.centre(a) ?: return null
        val pb = keys.centre(b) ?: return null
        val dx = pa.first - pb.first
        val dy = (pa.second - pb.second) * ROW_ASPECT
        return sqrt(dx * dx + dy * dy)
    }
}
