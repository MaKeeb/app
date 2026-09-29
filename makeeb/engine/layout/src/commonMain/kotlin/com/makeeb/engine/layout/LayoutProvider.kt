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
     * The languages the user types, primary first. Every letter's long-press offers all their
     * accents, the primary's first: the languages decide the character set, the layout only
     * places the letters. A tag without data falls back to its base language or is skipped;
     * with none left, English.
     */
    val languageTags: List<String> = listOf("en"),
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

    /**
     * Each letter's long-press accents for [languageTags], merged as the letters page merges
     * them and in key order: what Settings shows the user they will be able to type.
     */
    fun accents(languageTags: List<String>): Map<String, List<String>>

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

    override fun accents(languageTags: List<String>): Map<String, List<String>> =
        data.accents(languageTags).toList().sortedBy { it.first }.toMap()

    override fun layout(mode: KeyboardMode, options: LayoutOptions): KeyboardLayout =
        cache.getOrPut(mode to options) {
            // The space bar names the primary language and switches between them; the same on
            // every page with a space bar, so nothing moves between modes.
            val spaceLanguages = data.languages(options.languageTags).map { it.autonym to it.tag }
            when (mode) {
                KeyboardMode.Letters ->
                    BuiltInLayouts.letters(data.layout(options.letterLayoutId), data.accents(options.languageTags), options, spaceLanguages)
                KeyboardMode.Symbols -> BuiltInLayouts.symbols(options, spaceLanguages)
                KeyboardMode.SymbolsMore -> BuiltInLayouts.symbolsMore(options, spaceLanguages)
                KeyboardMode.Numeric -> BuiltInLayouts.numeric()
                KeyboardMode.Phone -> BuiltInLayouts.phone()
            }
        }
}
