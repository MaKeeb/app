package com.makeeb.engine.layout

import com.makeeb.core.model.KeyboardMode

data class LayoutInfo(val id: String, val displayName: String)

/** Inputs that change which keys a layout contains. */
data class LayoutOptions(
    val letterLayoutId: String = "qwerty",
    val numberRow: Boolean = false,
    /** Draw a globe key: iOS always asks for one, Android only without a system switcher. */
    val switchKey: Boolean = false,
    /** Field-specific keys on the letters layout's bottom row. */
    val variant: LetterVariant = LetterVariant.Text,
)

/** Letters layouts adapted to the field, like the platform keyboards do. */
enum class LetterVariant {
    Text,

    /** `@` in place of the comma. */
    Email,

    /** `/` and `.com` (long-press for other endings) beside a shorter space bar. */
    Url,
}

interface LayoutProvider {
    val letterLayouts: List<LayoutInfo>

    fun layout(mode: KeyboardMode, options: LayoutOptions): KeyboardLayout
}

class BuiltInLayoutProvider : LayoutProvider {
    private val cache = mutableMapOf<Pair<KeyboardMode, LayoutOptions>, KeyboardLayout>()

    override val letterLayouts: List<LayoutInfo> = BuiltInLayouts.letterLayouts

    override fun layout(mode: KeyboardMode, options: LayoutOptions): KeyboardLayout =
        cache.getOrPut(mode to options) {
            when (mode) {
                KeyboardMode.Letters -> BuiltInLayouts.letters(options.letterLayoutId, options)
                KeyboardMode.Symbols -> BuiltInLayouts.symbols(options)
                KeyboardMode.SymbolsMore -> BuiltInLayouts.symbolsMore(options)
                KeyboardMode.Numeric -> BuiltInLayouts.numeric()
                KeyboardMode.Phone -> BuiltInLayouts.phone()
            }
        }
}
