package com.makeeb.engine.layout

import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.layout.data.LanguageSpec
import com.makeeb.engine.layout.data.LayoutData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage A of the declarative layouts (docs/research/layout-formats.md §9.7): the letters pages
 * built from the JSON data equal the hand-written Kotlin ones, key for key (actions, labels,
 * widths, styles, alternates, hints, long-press actions), for every layout, option and field
 * variant, given today's layout-to-alternates choice.
 */
class LayoutParityTest {
    private val data = LayoutData()
    private val legacy = BuiltInLayoutProvider()
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

    @Test
    fun dataBuiltLettersEqualTheKotlinOnes() {
        assertEquals(layoutIds, data.layouts.map { it.id })
        allOptions.forEach { options ->
            val fromData = BuiltInLayouts.letters(data.layout(options.letterLayoutId), LegacyAlternates.languageFor(options.letterLayoutId), options)
            assertEquals(legacy.layout(KeyboardMode.Letters, options), fromData, "$options")
        }
    }
}

/** The alternates the Kotlin layouts had, as languages: English, German on QWERTZ, French on AZERTY. */
internal object LegacyAlternates {
    private val latin: Map<Char, String> = mapOf(
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

    private val german: Map<Char, String> = latin + mapOf(
        'a' to "äàáâæãåā",
        'o' to "öóòôõøœō",
        'u' to "üúùûū",
        's' to "ßśš",
    )

    private val french: Map<Char, String> = latin + mapOf(
        'a' to "àâæáäãåā",
        'c' to "çćč",
        'e' to "éèêëēėę",
        'i' to "îïíìīį",
        'o' to "ôœöóòõøō",
        'u' to "ùûüúū",
        'y' to "ÿý",
    )

    fun mapFor(layoutId: String): Map<Char, String> = when (layoutId) {
        "qwertz" -> german
        "azerty" -> french
        else -> latin
    }

    fun languageFor(layoutId: String): LanguageSpec = LanguageSpec(
        tag = "legacy-$layoutId",
        name = "Legacy",
        autonym = "Legacy",
        layouts = listOf(layoutId),
        alternates = mapFor(layoutId).map { (base, alternates) -> base.toString() to alternates.map(Char::toString) }.toMap(),
    )
}
