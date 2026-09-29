package com.makeeb.engine.layout

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode

/**
 * The hand-written Kotlin layouts as they were before the letter rows moved into data (stage B of
 * docs/research/layout-formats.md §9.7), kept as the reference [LayoutParityTest] holds the data
 * against. Only the letters page takes its alternates as a parameter, so it can be compared under
 * every bundled language. A deliberate change to the frame changes this copy too.
 */
internal object LegacyBuiltInLayouts {
    private val latinAlternates: Map<Char, String> = mapOf(
        'a' to "àáâäæãåā",
        'c' to "çćč",
        'e' to "éèêëēėę",
        'i' to "íìîïīį",
        'l' to "ł",
        'n' to "ñń",
        'o' to "óòôöõøœō",
        's' to "ßśš",
        'u' to "úùûüū",
        'y' to "ÿý",
        'z' to "žźż",
    )

    /** German letters first on QWERTZ, as a German keyboard offers them. */
    private val germanAlternates: Map<Char, String> = latinAlternates + mapOf(
        'a' to "äàáâæãåā",
        'o' to "öóòôõøœō",
        'u' to "üúùûū",
        's' to "ßśš",
    )

    /** French letters first on AZERTY. */
    private val frenchAlternates: Map<Char, String> = latinAlternates + mapOf(
        'a' to "àâæáäãåā",
        'c' to "çćč",
        'e' to "éèêëēėę",
        'i' to "îïíìīį",
        'o' to "ôœöóòõøō",
        'u' to "ùûüúū",
        'y' to "ÿý",
    )

    /** The choice before languages: German on QWERTZ, French on AZERTY, the Latin set elsewhere. */
    fun alternatesFor(layoutId: String): Map<Char, String> = when (layoutId) {
        "qwertz" -> germanAlternates
        "azerty" -> frenchAlternates
        else -> latinAlternates
    }

    private val topRowDigits = "1234567890"

    private fun digitHints(topRow: String): Map<Char, String> =
        topRow.zip(topRowDigits).associate { (char, digit) -> char to digit.toString() }

    val letterLayouts: List<LayoutInfo> = listOf(
        LayoutInfo("qwerty", "QWERTY"),
        LayoutInfo("qwertz", "QWERTZ"),
        LayoutInfo("azerty", "AZERTY"),
        LayoutInfo("dvorak", "Dvorak"),
        LayoutInfo("colemak", "Colemak"),
        LayoutInfo("workman", "Workman"),
    )

    fun layout(
        mode: KeyboardMode,
        options: LayoutOptions,
        alternates: Map<Char, String> = alternatesFor(options.letterLayoutId),
    ): KeyboardLayout = when (mode) {
        KeyboardMode.Letters -> letters(options.letterLayoutId, options, alternates)
        KeyboardMode.Symbols -> symbols(options)
        KeyboardMode.SymbolsMore -> symbolsMore(options)
        KeyboardMode.Numeric -> numeric()
        KeyboardMode.Phone -> phone()
    }

    fun letters(id: String, options: LayoutOptions, alternates: Map<Char, String> = alternatesFor(id)): KeyboardLayout {
        val (top, middle, bottom) = when (id) {
            "qwertz" -> Triple("qwertzuiop", "asdfghjkl", "yxcvbnm")
            "azerty" -> Triple("azertyuiop", "qsdfghjklm", "wxcvbn")
            // Dvorak keeps its punctuation on the top row; ';' lives on the symbols page.
            "dvorak" -> Triple("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz")
            "colemak" -> Triple("qwfpgjluy", "arstdhneio", "zxcvbkm")
            "workman" -> Triple("qdrwbjfup", "ashtgyneoi", "zxmcvkl")
            else -> Triple("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        // Standard ten units; Dvorak's nine-letter bottom row is compressed to fit.
        return keyboardLayout(id, KeyboardMode.Letters, widthUnits = 10f) {
            if (options.numberRow) numberRow()
            row {
                chars(top, alternates, hints = if (options.numberRow) emptyMap() else digitHints(top))
            }
            row { chars(middle, alternates) }
            row {
                shift()
                chars(bottom, alternates)
                if (id == "azerty") text("'", alternates = listOf("’", "\""))
                backspace()
            }
            bottomRow(KeyboardMode.Symbols, "?123", options)
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
