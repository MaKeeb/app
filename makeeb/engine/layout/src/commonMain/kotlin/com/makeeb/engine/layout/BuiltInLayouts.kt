package com.makeeb.engine.layout

import com.makeeb.core.model.KeyboardMode

/**
 * The layouts that ship in the binary. Hand-written for now; a declarative, user-importable
 * layout format is on the roadmap (see the board, `layout-definition-format`).
 */
internal object BuiltInLayouts {
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

    fun letters(id: String, options: LayoutOptions): KeyboardLayout {
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
            if (options.numberRow) row(heightWeight = NUMBER_ROW_HEIGHT_WEIGHT) { chars(topRowDigits) }
            row {
                chars(top, latinAlternates, hints = if (options.numberRow) emptyMap() else digitHints(top))
            }
            row { chars(middle, latinAlternates) }
            row {
                shift()
                chars(bottom, latinAlternates)
                if (id == "azerty") text("'", alternates = listOf("’", "\""))
                backspace()
            }
            bottomRow(KeyboardMode.Symbols, "?123", options, options.variant)
        }
    }

    fun symbols(options: LayoutOptions): KeyboardLayout = keyboardLayout("symbols", KeyboardMode.Symbols) {
        row { chars("1234567890") }
        row { chars("@#\$_&-+()/") }
        row {
            mode(KeyboardMode.SymbolsMore, "=\\<")
            chars("*\"':;!?")
            backspace()
        }
        bottomRow(KeyboardMode.Letters, "ABC", options, numberPadKey = true)
    }

    fun symbolsMore(options: LayoutOptions): KeyboardLayout = keyboardLayout("symbols-more", KeyboardMode.SymbolsMore) {
        row { chars("~`|•√π÷×¶∆") }
        row { chars("£¢€¥^°={}\\") }
        row {
            mode(KeyboardMode.Symbols, "?123")
            chars("%©®™✓[]")
            backspace()
        }
        bottomRow(KeyboardMode.Letters, "ABC", options)
    }

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
     * `?123 | globe-or-emoji | , | space | . | enter`, with field-specific keys for e-mail and URL
     * fields and a number-pad key on the symbols page: always ten units wide.
     */
    private fun LayoutBuilder.bottomRow(
        modeTarget: KeyboardMode,
        modeLabel: String,
        options: LayoutOptions,
        variant: LetterVariant = LetterVariant.Text,
        numberPadKey: Boolean = false,
    ) = row {
        mode(modeTarget, modeLabel)
        if (options.switchKey) globe() else emoji()
        when (variant) {
            LetterVariant.Text -> {
                text(",", alternates = listOf(";", ":"))
                // The symbols page opens the number pad, as on Gboard; the space bar gives up a unit.
                if (numberPadKey) mode(KeyboardMode.Numeric, "1234", width = 1f)
                space(width = if (numberPadKey) 3f else 4f)
                text(".", alternates = listOf("?", "!", "'", "\"", "-", "…"))
            }
            LetterVariant.Email -> {
                text("@")
                space()
                text(".", alternates = listOf("-", "_", ","))
            }
            LetterVariant.Url -> {
                text("/")
                space(width = 3f)
                text(".", alternates = listOf("-", "_", ":"))
                text(".com", alternates = listOf(".net", ".org", ".io", ".co.uk"))
            }
        }
        enter()
    }
}
