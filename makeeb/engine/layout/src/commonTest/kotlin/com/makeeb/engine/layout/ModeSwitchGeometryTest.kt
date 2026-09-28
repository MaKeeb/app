package com.makeeb.engine.layout

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Switching letters ⇄ symbols ⇄ more symbols moves no key the pages share. The key area has one
 * height per preferences (KeyboardMetrics), so the pages must also agree on their rows, or every
 * row would move when the page changes.
 */
class ModeSwitchGeometryTest {
    private val provider = BuiltInLayoutProvider()
    private val pages = listOf(KeyboardMode.Letters, KeyboardMode.Symbols, KeyboardMode.SymbolsMore)

    /** Every field variant, with and without the number row and the globe key. */
    private val allOptions = LetterVariant.entries.flatMap { variant ->
        listOf(false, true).flatMap { numberRow ->
            listOf(false, true).map { switchKey -> LayoutOptions(numberRow = numberRow, switchKey = switchKey, variant = variant) }
        }
    }

    /** As KeyboardSession lays a page out: the fixed key area split by the page's row weights. */
    private fun geometry(mode: KeyboardMode, options: LayoutOptions): LayoutGeometry {
        val layout = provider.layout(mode, options)
        val areaHeight = ROW_HEIGHT * (4 + if (options.numberRow) NUMBER_ROW_HEIGHT_WEIGHT else 0f)
        return LayoutGeometry(layout, width = 1000f, rowHeight = areaHeight / layout.totalHeightWeight)
    }

    private fun pagesFor(options: LayoutOptions) = pages.map { geometry(it, options) }

    private fun LayoutGeometry.row(index: Int): List<PlacedKey> = keys.filter { it.row == index }

    private fun LayoutGeometry.bottomRow(): List<PlacedKey> = row(layout.rows.lastIndex)

    /** Top and bottom edge of each row. */
    private fun LayoutGeometry.rowEdges(): List<Pair<Float, Float>> =
        layout.rows.indices.map { index -> row(index).first().bounds.let { it.top to it.bottom } }

    private fun LayoutGeometry.backspace(): KeyBounds = keys.single { it.key.action == KeyAction.Backspace }.bounds

    /** Shift on letters, `=\<` on symbols, `?123` on more symbols: the first key of the row with backspace. */
    private fun LayoutGeometry.shiftSlot(): KeyBounds = keys.first { it.row == keys.single { k -> k.key.action == KeyAction.Backspace }.row }.bounds

    private fun LayoutGeometry.name() = "${layout.id}/${layout.mode}"

    @Test
    fun theBottomRowIsTheSameOnEveryPage() {
        allOptions.forEach { options ->
            val (letters, symbols, more) = pagesFor(options)
            for (page in listOf(symbols, more)) {
                val context = "${page.name()} $options"
                assertEquals(letters.bottomRow().map { it.bounds }, page.bottomRow().map { it.bounds }, context)
                // Every key but the mode key is the very same key (action, label, alternates).
                assertEquals(letters.bottomRow().drop(1).map { it.key }, page.bottomRow().drop(1).map { it.key }, context)
                val mode = page.bottomRow().first().key
                assertEquals(KeyAction.SwitchMode(KeyboardMode.Letters), mode.action, context)
                assertEquals(letters.bottomRow().first().key.width, mode.width, context)
            }
        }
    }

    @Test
    fun everyPageHasTheSameRowsAtTheSameHeights() {
        allOptions.forEach { options ->
            val (letters, symbols, more) = pagesFor(options)
            assertEquals(if (options.numberRow) 5 else 4, letters.layout.rows.size, "$options")
            for (page in listOf(symbols, more)) {
                assertEquals(letters.layout.rows.map { it.heightWeight }, page.layout.rows.map { it.heightWeight }, "${page.name()} $options")
                assertEquals(letters.rowEdges(), page.rowEdges(), "${page.name()} $options")
                assertEquals(letters.layout.unitsPerRow, page.layout.unitsPerRow, "${page.name()} $options")
            }
        }
    }

    @Test
    fun backspaceAndTheShiftSlotStayPut() {
        allOptions.forEach { options ->
            val (letters, symbols, more) = pagesFor(options)
            for (page in listOf(symbols, more)) {
                assertEquals(letters.backspace(), page.backspace(), "${page.name()} $options")
                assertEquals(letters.shiftSlot(), page.shiftSlot(), "${page.name()} $options")
            }
        }
    }

    @Test
    fun theDigitsLineUpWithTheNumberRowOrTheTopLetterRow() {
        listOf(false, true).forEach { numberRow ->
            val (letters, symbols, more) = pagesFor(LayoutOptions(numberRow = numberRow))
            val digits = "1234567890".map { symbols.keyFor(it)!!.bounds }
            if (numberRow) {
                assertEquals("1234567890".map { letters.keyFor(it)!!.bounds }, digits)
                assertEquals(letters.row(0).map { it.key }, symbols.row(0).map { it.key }, "the same short row of digits")
                assertEquals(letters.row(0).map { it.key }, more.row(0).map { it.key })
            } else {
                assertEquals("qwertyuiop".map { letters.keyFor(it)!!.bounds }, digits)
            }
            // On QWERTY every row the pages build from the same key widths lines up key for key.
            for (page in listOf(symbols, more)) {
                val sameWidths = letters.layout.rows.indices.filter { index ->
                    letters.row(index).map { it.key.width } == page.row(index).map { it.key.width }
                }
                assertEquals(if (numberRow) listOf(0, 1, 3, 4) else listOf(0, 2, 3), sameWidths, page.name())
                sameWidths.forEach { index ->
                    assertEquals(letters.row(index).map { it.bounds }, page.row(index).map { it.bounds }, "${page.name()} row $index")
                }
            }
        }
    }

    @Test
    fun otherLetterLayoutsKeepShiftBackspaceAndTheBottomRowInPlace() {
        provider.letterLayouts.forEach { info ->
            listOf(false, true).forEach { numberRow ->
                val options = LayoutOptions(letterLayoutId = info.id, numberRow = numberRow)
                val (letters, symbols, more) = pagesFor(options)
                for (page in listOf(symbols, more)) {
                    val context = "${info.id} ${page.name()} numberRow=$numberRow"
                    assertEquals(letters.rowEdges(), page.rowEdges(), context)
                    assertSameBounds(letters.shiftSlot(), page.shiftSlot(), context)
                    assertSameBounds(letters.backspace(), page.backspace(), context)
                    letters.bottomRow().zip(page.bottomRow()).forEach { (a, b) -> assertSameBounds(a.bounds, b.bounds, context) }
                }
            }
        }
    }

    @Test
    fun dvoraksLongBottomLetterRowNarrowsOnlyItsLetters() {
        val geometry = geometry(KeyboardMode.Letters, LayoutOptions(letterLayoutId = "dvorak"))
        val row = geometry.row(2)
        assertEquals(150f, row.first().bounds.width, absoluteTolerance = 0.01f) // shift
        assertEquals(150f, row.last().bounds.width, absoluteTolerance = 0.01f) // backspace
        row.drop(1).dropLast(1).forEach { assertEquals(700f / 9, it.bounds.width, absoluteTolerance = 0.01f) }
        assertEquals(1000f, row.last().bounds.right, absoluteTolerance = 0.01f)
    }

    @Test
    fun noSymbolIsLostFromTheSymbolsPages() {
        // Everything the symbols and more-symbols pages offered before they shared the field's
        // bottom row and the number row (keys and long-press alternates).
        val before = "1234567890" + "@#\$_&-+()/" + "*\"':;!?" + ",;:.?!'\"-…" +
            "~`|•√π÷×¶∆" + "£¢€¥^°={}\\" + "%©®™✓[]"
        allOptions.forEach { options ->
            val reachable = listOf(KeyboardMode.Symbols, KeyboardMode.SymbolsMore)
                .flatMap { provider.layout(it, options).rows.flatMap { row -> row.keys } }
                .flatMap { key -> listOfNotNull((key.action as? KeyAction.Text)?.text) + key.alternates }
                .toSet()
            val missing = before.map { it.toString() }.filter { it !in reachable }
            assertTrue(missing.isEmpty(), "$options lost $missing")
        }
    }

    private fun assertSameBounds(expected: KeyBounds, actual: KeyBounds, message: String) {
        val tolerance = 0.01f
        assertEquals(expected.left, actual.left, tolerance, message)
        assertEquals(expected.top, actual.top, tolerance, message)
        assertEquals(expected.right, actual.right, tolerance, message)
        assertEquals(expected.bottom, actual.bottom, tolerance, message)
    }

    private companion object {
        const val ROW_HEIGHT = 100f
    }
}
