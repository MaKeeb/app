package com.makeeb.engine.layout

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.layout.data.LetterLayoutSpec

/**
 * The frame of every page, in Kotlin: the letters page around a data layout's character rows,
 * the symbols, more-symbols, number and phone pages, the number row and the field's bottom row.
 * Letter rows and alternates are data (engine/layout/data, docs/layouts/schema.md), which has no
 * way to express a frame key, so the pages' shared keys stay put whatever the data says.
 */
internal object BuiltInLayouts {
    private val topRowDigits = "1234567890"

    /**
     * The letters page from data: the [layout]'s three character rows, with the selected
     * languages' merged [accents] as long-press keys, inside the frame only the engine builds (the
     * number row, shift, backspace, the digit hints and the field's bottom row), so no data file
     * can move a key the pages share.
     */
    fun letters(layout: LetterLayoutSpec, accents: Map<String, List<String>>, options: LayoutOptions): KeyboardLayout {
        val (top, middle, bottom) = layout.rows
        // Standard ten units; Dvorak's nine-letter bottom row is compressed to fit.
        return keyboardLayout(layout.id, KeyboardMode.Letters, widthUnits = 10f) {
            if (options.numberRow) numberRow()
            row { letterKeys(top, layout, accents, hints = if (options.numberRow) emptyList() else topRowDigits.map(Char::toString)) }
            row { letterKeys(middle, layout, accents) }
            row {
                shift()
                letterKeys(bottom, layout, accents)
                backspace()
            }
            bottomRow(KeyboardMode.Symbols, "?123", options)
        }
    }

    /**
     * Each key's long-press list: its digit hint (top row, no number row), the layout's own
     * alternates (punctuation on a layout's extra keys, never letters), then the languages'.
     */
    private fun RowBuilder.letterKeys(keys: List<String>, layout: LetterLayoutSpec, accents: Map<String, List<String>>, hints: List<String> = emptyList()) {
        keys.forEachIndexed { index, key ->
            val hint = hints.getOrNull(index)
            val alternates = listOfNotNull(hint) + layout.alternates[key].orEmpty() + accents[key].orEmpty()
            text(key, width = layout.widths[key] ?: 1f, alternates = alternates.distinct(), hint = hint)
        }
    }

    /**
     * Both symbols pages follow the letters page's row structure, so switching pages moves no key
     * they share: the same rows at the same heights (with the number row, its digits stay on top at
     * the same 0.8 weight), shift's slot and backspace at the same size and place, and the field's
     * bottom row. Only characters and the mode keys' labels change.
     */
    fun symbols(options: LayoutOptions): KeyboardLayout = keyboardLayout("symbols", KeyboardMode.Symbols, widthUnits = 10f) {
        if (options.numberRow) {
            numberRow()
            // The digits keep their short row, which frees a row for the most used "more" symbols
            // (and < >). `~` and `|` sit where they are on the more-symbols page.
            row { chars("~=|%<>[]{}") }
        } else {
            row { chars(topRowDigits) }
        }
        // Brackets of every shape behind the parentheses, as on Gboard: < and > have no key of their
        // own without the number row.
        row { chars("@#\$_&-+()/", alternates = mapOf('(' to "<[{", ')' to ">]}")) }
        row {
            mode(KeyboardMode.SymbolsMore, "=\\<")
            chars("*\"':;!?")
            backspace()
        }
        symbolsBottomRow(options)
    }

    fun symbolsMore(options: LayoutOptions): KeyboardLayout = keyboardLayout("symbols-more", KeyboardMode.SymbolsMore, widthUnits = 10f) {
        if (options.numberRow) numberRow()
        row { chars("~`|•√π÷×¶∆") }
        row { chars("£¢€¥^°={}\\") }
        row {
            mode(KeyboardMode.Symbols, "?123")
            chars("%©®™✓[]")
            backspace()
        }
        symbolsBottomRow(options)
    }

    /** The optional digit row, shorter than the others; identical on every page that shows it. */
    private fun LayoutBuilder.numberRow() = row(heightWeight = NUMBER_ROW_HEIGHT_WEIGHT) { chars(topRowDigits) }

    /**
     * ABC back to letters. Holding it opens the number pad, as the symbols page's 1234 key did
     * (Gboard's), without a key of its own that would shift the bottom row.
     */
    private fun LayoutBuilder.symbolsBottomRow(options: LayoutOptions) =
        bottomRow(KeyboardMode.Letters, "ABC", options, modeLongPress = KeyAction.SwitchMode(KeyboardMode.Numeric))

    fun numeric(): KeyboardLayout = keyboardLayout("numeric", KeyboardMode.Numeric) {
        row { chars("123"); text("-") }
        row { chars("456"); text(",") }
        row { chars("789"); backspace(width = 1f) }
        row { mode(KeyboardMode.Letters, "ABC", width = 1f); text("0"); text("."); enter(width = 1f) }
    }

    /** Letters on phone-pad digits, as printed on phone keypads. */
    private val keypadLetters = mapOf(
        '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL",
        '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    )

    fun phone(): KeyboardLayout = keyboardLayout("phone", KeyboardMode.Phone) {
        row { keypad("123"); text("+") }
        row { keypad("456"); space(width = 1f) }
        row { keypad("789"); backspace(width = 1f) }
        row {
            text("*", alternates = listOf("#"))
            text("0", alternates = listOf("+"), caption = "+")
            text("#")
            enter(width = 1f)
        }
    }

    private fun RowBuilder.keypad(digits: String) =
        digits.forEach { digit -> text(digit.toString(), caption = keypadLetters[digit]) }

    /**
     * `mode | globe-or-emoji | , | space | . | enter`, with field-specific keys for e-mail and URL
     * fields: always ten units wide. Letters and both symbols pages share it, so only the mode
     * key's label and target change between them.
     */
    private fun LayoutBuilder.bottomRow(
        modeTarget: KeyboardMode,
        modeLabel: String,
        options: LayoutOptions,
        modeLongPress: KeyAction? = null,
    ) = row {
        mode(modeTarget, modeLabel, longPress = modeLongPress)
        if (options.switchKey) globe() else emoji()
        when (options.variant) {
            LetterVariant.Text -> {
                text(",", alternates = listOf(";", ":"))
                space()
                text(".", alternates = listOf("?", "!", "'", "\"", "-", "…"))
            }
            // The symbols pages have no comma or ellipsis of their own: in these fields they are
            // alternates of the full stop.
            LetterVariant.Email -> {
                text("@")
                space()
                text(".", alternates = listOf("-", "_", ",", "…"))
            }
            LetterVariant.Url -> {
                text("/")
                space(width = 3f)
                text(".", alternates = listOf("-", "_", ":", ",", "…"))
                text(".com", alternates = listOf(".net", ".org", ".io", ".co.uk"))
            }
        }
        enter()
    }
}
