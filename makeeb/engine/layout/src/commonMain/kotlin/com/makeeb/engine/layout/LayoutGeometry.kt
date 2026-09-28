package com.makeeb.engine.layout

import kotlin.math.sqrt

data class KeyBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
    val width: Float get() = right - left

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class PlacedKey(val key: Key, val row: Int, val bounds: KeyBounds)

/**
 * Pixel positions of every key for a given keyboard size. The UI uses it for touch handling and
 * the gesture decoder for key proximity, so both agree on what is under a finger.
 */
class LayoutGeometry(
    val layout: KeyboardLayout,
    val width: Float,
    /** Height of a standard row (weight 1); a row is `rowHeight * row.heightWeight` tall. */
    val rowHeight: Float,
    /**
     * Empty space left and right of the rows. Keeps keys off curved display edges and rounded
     * corners; touches in it still resolve to the nearest key.
     */
    val horizontalInset: Float = 0f,
) {
    val height: Float = layout.totalHeightWeight * rowHeight

    /** Top edge of each row, plus the bottom of the last one. */
    private val rowEdges: List<Float> =
        layout.rows.runningFold(0f) { top, row -> top + row.heightWeight * rowHeight }

    val keys: List<PlacedKey> = buildList {
        val usableWidth = (width - 2 * horizontalInset).coerceAtLeast(0f)
        val unitWidth = if (layout.unitsPerRow > 0f) usableWidth / layout.unitsPerRow else 0f
        layout.rows.forEachIndexed { rowIndex, row ->
            var x = horizontalInset + (layout.unitsPerRow - row.units) / 2 * unitWidth
            val top = rowEdges[rowIndex]
            val bottom = rowEdges[rowIndex + 1]
            row.keys.forEach { key ->
                val keyWidth = key.width * unitWidth
                add(PlacedKey(key, rowIndex, KeyBounds(x, top, x + keyWidth, bottom)))
                x += keyWidth
            }
        }
    }

    private val rows: List<List<PlacedKey>> = keys.groupBy { it.row }.values.toList() // keys are built row by row

    private val byCharacter: Map<Char, PlacedKey> =
        layout.characterKeys.entries.mapNotNull { (char, key) -> keys.firstOrNull { it.key == key }?.let { char to it } }.toMap()

    /**
     * The key under ([x], [y]). Touches outside every key (row margins, edges) resolve to the
     * nearest key in the same row, so no touch is lost.
     */
    fun keyAt(x: Float, y: Float): PlacedKey? {
        if (rows.isEmpty()) return null
        val rowIndex = (rowEdges.indexOfLast { it <= y }).coerceIn(0, rows.lastIndex)
        val row = rows[rowIndex]
        return row.firstOrNull { x >= it.bounds.left && x < it.bounds.right }
            ?: row.minByOrNull { kotlin.math.abs(it.bounds.centerX - x) }
    }

    /** Keys ordered by distance from ([x], [y]) to their centre. */
    fun nearestKeys(x: Float, y: Float, limit: Int): List<Pair<PlacedKey, Float>> =
        keys.map { it to distance(it.bounds.centerX, it.bounds.centerY, x, y) }
            .sortedBy { it.second }
            .take(limit)

    fun keyFor(char: Char): PlacedKey? = byCharacter[char.lowercaseChar()]

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
