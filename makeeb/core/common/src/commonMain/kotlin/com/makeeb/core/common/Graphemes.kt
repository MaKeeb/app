package com.makeeb.core.common

/**
 * User-perceived characters (grapheme clusters) at the end of a text, which is what one
 * Backspace removes. A practical subset of UAX #29 covering what people type on a keyboard:
 * surrogate pairs, combining marks, variation selectors, emoji skin tones and ZWJ sequences,
 * flags (regional-indicator pairs and tag sequences), keycaps and CR LF. It lets the keyboard
 * predict a deletion without asking the app, so it errs towards the platforms' own rules.
 */
object Graphemes {
    /** Length in UTF-16 units of the last grapheme of [text]; 0 when [text] is empty. */
    fun lastLength(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        // Clusters are short: look at a bounded tail, never starting inside a surrogate pair.
        var from = (text.length - WINDOW).coerceAtLeast(0)
        if (from > 0 && text[from].isLowSurrogate()) from++
        val points = codePoints(text, from)
        var start = points.size - 1
        while (start > 0) {
            val current = points[start].value
            val previous = points[start - 1].value
            start = when {
                previous == CR && current == LF -> start - 1
                isExtend(current) -> start - 1
                previous == ZWJ -> start - 1
                isRegionalIndicator(current) && isRegionalIndicator(previous) &&
                    regionalIndicatorsEndingAt(points, start) % 2 == 0 -> start - 1
                else -> break
            }
        }
        return text.length - points[start].offset
    }

    private class CodePoint(val value: Int, val offset: Int)

    private fun codePoints(text: CharSequence, from: Int): List<CodePoint> {
        val points = ArrayList<CodePoint>(text.length - from)
        var i = from
        while (i < text.length) {
            val high = text[i]
            if (high.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                points += CodePoint(0x10000 + ((high.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00), i)
                i += 2
            } else {
                points += CodePoint(high.code, i)
                i++
            }
        }
        return points
    }

    /** Whether the code point attaches to the one before it. */
    private fun isExtend(cp: Int): Boolean = when {
        cp == ZWJ || cp == KEYCAP -> true
        cp in 0xFE00..0xFE0F -> true // variation selectors
        cp in 0x1F3FB..0x1F3FF -> true // skin tones
        cp in 0xE0020..0xE007F -> true // tags (subdivision flags)
        cp in 0xE0100..0xE01EF -> true // variation selectors supplement
        cp < 0x10000 -> cp.toChar().category.let {
            it == CharCategory.NON_SPACING_MARK || it == CharCategory.ENCLOSING_MARK || it == CharCategory.COMBINING_SPACING_MARK
        }
        else -> false
    }

    private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /** How many regional indicators run back from [index] inclusive: flags pair them from the start. */
    private fun regionalIndicatorsEndingAt(points: List<CodePoint>, index: Int): Int {
        var count = 0
        var i = index
        while (i >= 0 && isRegionalIndicator(points[i].value)) {
            count++
            i--
        }
        return count
    }

    private const val WINDOW = 64
    private const val ZWJ = 0x200D
    private const val KEYCAP = 0x20E3
    private const val CR = 0x0D
    private const val LF = 0x0A
}
