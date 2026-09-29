package com.makeeb.engine.layout

import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.layout.data.BundledFile
import com.makeeb.engine.layout.data.LayoutData
import com.makeeb.engine.layout.data.bundledLayoutFiles
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The data-built layouts equal the hand-written Kotlin ones they replaced
 * ([LegacyBuiltInLayouts], stages A and B of docs/research/layout-formats.md §9.7), key for key:
 * actions, labels, widths, styles, alternates, hints, captions and long-press actions, on every
 * page, for every layout, field variant, number row and globe option.
 */
class LayoutParityTest {
    private val layoutIds = listOf("qwerty", "qwertz", "azerty", "dvorak", "colemak", "workman")

    private val allOptions = layoutIds.flatMap { id ->
        LetterVariant.entries.flatMap { variant ->
            listOf(false, true).flatMap { numberRow ->
                listOf(false, true).map { switchKey ->
                    LayoutOptions(letterLayoutId = id, numberRow = numberRow, switchKey = switchKey, variant = variant)
                }
            }
        }
    }

    /**
     * Before languages, alternates came with the layout: German on QWERTZ, French on AZERTY, a
     * Latin set elsewhere. As language files, through the real parser, they rebuild exactly that.
     */
    @Test
    fun theDataRebuildsTheKotlinLayoutsWithTheirOldAlternates() {
        val legacyLanguages = mapOf("qwerty" to "en", "qwertz" to "de", "azerty" to "fr")
        val provider = BuiltInLayoutProvider(
            LayoutData(
                layoutFiles = bundledLayoutFiles,
                languageFiles = legacyLanguages.map { (layout, tag) -> languageFile(tag, LegacyBuiltInLayouts.alternatesFor(layout)) },
            ),
        )
        assertEquals(LegacyBuiltInLayouts.letterLayouts, provider.letterLayouts)
        KeyboardMode.entries.forEach { mode ->
            allOptions.forEach { options ->
                val tag = legacyLanguages[options.letterLayoutId] ?: "en"
                assertEquals(
                    LegacyBuiltInLayouts.layout(mode, options),
                    provider.layout(mode, options.copy(languageTag = tag)),
                    "$mode $options",
                )
            }
        }
    }

    /**
     * Under every bundled language, every page of every layout is the Kotlin one with that
     * language's alternates: the language changes long-press keys and nothing else.
     */
    @Test
    fun everyLanguageChangesOnlyTheAlternates() {
        val provider = BuiltInLayoutProvider()
        val data = LayoutData()
        data.languages.forEach { language ->
            val alternates = language.alternates.map { (base, keys) ->
                require(base.length == 1 && keys.all { it.length == 1 }) { "${language.tag}: the Kotlin layouts took single characters" }
                base.single() to keys.joinToString("")
            }.toMap()
            KeyboardMode.entries.forEach { mode ->
                allOptions.forEach { options ->
                    assertEquals(
                        LegacyBuiltInLayouts.layout(mode, options, alternates),
                        provider.layout(mode, options.copy(languageTag = language.tag)),
                        "${language.tag} $mode $options",
                    )
                }
            }
        }
    }

    private fun languageFile(tag: String, alternates: Map<Char, String>): BundledFile {
        val entries = alternates.entries.joinToString(",") { (base, keys) -> "\"$base\": \"${keys.toList().joinToString(" ")}\"" }
        return BundledFile(
            tag,
            """{"schema": 1, "language": "$tag", "name": "Legacy", "autonym": "Legacy", "layouts": ["qwerty"],
               "alternates": {$entries}, "sources": ["MaKeeb's Kotlin layouts before stage B"]}""",
        )
    }
}
