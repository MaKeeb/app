package com.makeeb.engine.layout

import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.layout.data.LayoutData

data class LayoutInfo(val id: String, val displayName: String)

/** A bundled language: its BCP 47 [tag], English [name] and its own name ([autonym]). */
data class LanguageInfo(val tag: String, val name: String, val autonym: String)

/** Inputs that change which keys a layout contains. */
data class LayoutOptions(
    val letterLayoutId: String = "qwerty",
    val numberRow: Boolean = false,
    /** Draw a globe key: iOS always asks for one, Android only without a system switcher. */
    val switchKey: Boolean = false,
    /** Field-specific keys on the bottom row, which letters and both symbols pages share. */
    val variant: LetterVariant = LetterVariant.Text,
    /**
     * The language whose long-press alternates the letters get, on any layout: a German speaker
     * on QWERTY gets German ones. Unknown tags fall back to the base language, then English.
     */
    val languageTag: String = "en",
)

/** Bottom rows adapted to the field, like the platform keyboards do. */
enum class LetterVariant {
    Text,

    /** `@` in place of the comma. */
    Email,

    /** `/` and `.com` (long-press for other endings) beside a shorter space bar. */
    Url,
}

interface LayoutProvider {
    val letterLayouts: List<LayoutInfo>

    /** The languages whose alternates the letters can offer. */
    val languages: List<LanguageInfo>

    fun layout(mode: KeyboardMode, options: LayoutOptions): KeyboardLayout
}

/**
 * The layouts that ship in the binary: letter rows and languages from the bundled data
 * (engine/layout/data, docs/layouts/schema.md), inside the frame [BuiltInLayouts] builds.
 */
class BuiltInLayoutProvider internal constructor(private val data: LayoutData) : LayoutProvider {
    constructor() : this(LayoutData())

    private val cache = mutableMapOf<Pair<KeyboardMode, LayoutOptions>, KeyboardLayout>()

    override val letterLayouts: List<LayoutInfo> by lazy { data.layouts.map { LayoutInfo(it.id, it.name) } }

    override val languages: List<LanguageInfo> by lazy { data.languages.map { LanguageInfo(it.tag, it.name, it.autonym) } }

    override fun layout(mode: KeyboardMode, options: LayoutOptions): KeyboardLayout =
        cache.getOrPut(mode to options) {
            when (mode) {
                KeyboardMode.Letters ->
                    BuiltInLayouts.letters(data.layout(options.letterLayoutId), data.language(options.languageTag), options)
                KeyboardMode.Symbols -> BuiltInLayouts.symbols(options)
                KeyboardMode.SymbolsMore -> BuiltInLayouts.symbolsMore(options)
                KeyboardMode.Numeric -> BuiltInLayouts.numeric()
                KeyboardMode.Phone -> BuiltInLayouts.phone()
            }
        }
}
