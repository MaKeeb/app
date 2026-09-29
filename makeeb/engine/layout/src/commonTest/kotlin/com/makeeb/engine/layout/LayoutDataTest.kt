package com.makeeb.engine.layout

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.layout.data.BundledFile
import com.makeeb.engine.layout.data.LayoutData
import com.makeeb.engine.layout.data.LayoutDataParser
import com.makeeb.engine.layout.data.bundledLanguageFiles
import com.makeeb.engine.layout.data.bundledLayoutFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled layout and language files, and the checks every file goes through (docs/layouts/schema.md). */
class LayoutDataTest {
    private val layoutIds = bundledLayoutFiles.map { it.name }.toSet()

    @Test
    fun everyBundledFileIsValid() {
        bundledLayoutFiles.forEach { file ->
            val parsed = LayoutDataParser.layout(file)
            assertEquals(emptyList(), parsed.problems.map { it.toString() }, file.name)
            assertNotNull(parsed.value)
        }
        bundledLanguageFiles.forEach { file ->
            val parsed = LayoutDataParser.language(file, layoutIds)
            assertEquals(emptyList(), parsed.problems.map { it.toString() }, file.name)
            assertNotNull(parsed.value)
        }
    }

    @Test
    fun theBundleHasTheSixLayoutsAndTenLanguages() {
        assertEquals(listOf("qwerty", "qwertz", "azerty", "dvorak", "colemak", "workman"), bundledLayoutFiles.map { it.name })
        assertEquals(listOf("en", "de", "fr", "es", "it", "pt", "hu", "pl", "nl", "sv"), bundledLanguageFiles.map { it.name })
        assertEquals(listOf("QWERTY", "QWERTZ", "AZERTY", "Dvorak", "Colemak", "Workman"), LayoutData().layouts.map { it.name })
        assertEquals("Deutsch", LayoutData().languages.single { it.tag == "de" }.autonym)
    }

    /** Every letter a language needs (its CLDR main exemplars) can be typed on every layout. */
    @Test
    fun everyLanguageCanTypeAllItsLettersOnEveryLayout() {
        val provider = BuiltInLayoutProvider()
        assertEquals(bundledLanguageFiles.map { it.name }.toSet(), cldrMainExemplars.keys)
        assertEquals(bundledLanguageFiles.map { it.name }, provider.languages.map { it.tag })
        cldrMainExemplars.forEach { (tag, letters) ->
            provider.letterLayouts.forEach { layout ->
                val page = provider.layout(KeyboardMode.Letters, LayoutOptions(letterLayoutId = layout.id, languageTags = listOf(tag)))
                val reachable = page.rows.flatMap { it.keys }
                    .flatMap { key -> listOfNotNull((key.action as? KeyAction.Text)?.text) + key.alternates }
                    .toSet()
                val missing = letters.split(' ').filter { it !in reachable }
                assertTrue(missing.isEmpty(), "$tag on ${layout.id} cannot type $missing")
            }
        }
    }

    @Test
    fun unknownLayoutsFallBackToQwertyAndLanguagesToTheirBaseThenEnglish() {
        val data = LayoutData()
        assertEquals("qwerty", data.layout("nope").id)
        assertEquals("de", data.language("de-CH")?.tag)
        assertEquals("en", data.language("xx")?.tag)
        val broken = LayoutData(
            layoutFiles = listOf(BundledFile("qwerty", "{}")),
            languageFiles = listOf(BundledFile("en", "{}")),
        )
        assertEquals(LayoutData.FALLBACK_LAYOUT, broken.layout("qwerty"))
        assertNull(broken.language("en"))
        assertEquals(emptyList(), broken.layouts)
    }

    @Test
    fun severalLanguagesSkipUnknownTagsAndFallBackToEnglishOnlyWhenNoneIsKnown() {
        val data = LayoutData()
        assertEquals(listOf("de", "sv"), data.languages(listOf("xx", "de-CH", "sv", "de")).map { it.tag })
        assertEquals(listOf("en"), data.languages(listOf("xx", "ja")).map { it.tag })
        assertEquals(listOf("en"), data.languages(emptyList()).map { it.tag })
    }

    @Test
    fun accentsMergeInLanguageOrderWithoutRepeats() {
        val data = LayoutData()
        val english = data.accents(listOf("en")).getValue("o")
        val merged = data.accents(listOf("en", "hu")).getValue("o")
        assertEquals(english, merged.take(english.size), "the primary language's accents come first")
        assertTrue("ő" in merged && "ő" !in english, "Hungarian adds ő")
        assertEquals(merged.distinct(), merged)
        assertEquals("ő", data.accents(listOf("hu", "en")).getValue("o").first { it !in listOf("ó", "ö") }, "Hungarian first puts ő early")
    }

    @Test
    fun theFallbackLayoutMatchesTheBundledQwertyRows() {
        assertEquals(LayoutData().layout("qwerty").rows, LayoutData.FALLBACK_LAYOUT.rows)
    }

    // region Validation: every rule has a failing example

    private fun layoutProblems(json: String, name: String = "test"): List<String> =
        LayoutDataParser.layout(BundledFile(name, json)).problems.map { "${it.path}: ${it.message}" }

    private fun languageProblems(json: String, name: String = "xx"): List<String> =
        LayoutDataParser.language(BundledFile(name, json), setOf("qwerty")).problems.map { "${it.path}: ${it.message}" }

    private fun layout(
        rows: String = """"q w e", "a s d", "z x c"""",
        extra: String = "",
        schema: Int = 1,
        id: String = "test",
    ) = """{"schema": $schema, "id": "$id", "name": "Test", "rows": [$rows], $extra "sources": ["test"]}"""

    private fun language(
        alternates: String = """{"a": "ä à"}""",
        extra: String = "",
        layouts: String = """["qwerty"]""",
        tag: String = "xx",
    ) = """{"schema": 1, "language": "$tag", "name": "X", "autonym": "X", "layouts": $layouts, "alternates": $alternates, $extra "sources": ["test"]}"""

    private fun assertProblem(problems: List<String>, fragment: String) =
        assertTrue(problems.any { fragment in it }, "expected a problem with '$fragment', got $problems")

    @Test
    fun aValidMinimalFilePasses() {
        assertEquals(emptyList(), layoutProblems(layout()))
        assertEquals(emptyList(), languageProblems(language()))
    }

    @Test
    fun structureProblemsAreReported() {
        assertProblem(layoutProblems(layout(extra = """"frame": "shift",""")), "frame")
        assertProblem(layoutProblems("not json"), "$")
        assertProblem(layoutProblems(layout(schema = 2)), "needs a newer MaKeeb")
        assertProblem(layoutProblems(layout(id = "Other")), "$.id")
        assertProblem(layoutProblems(layout(), name = "other"), "not the file's name")
        assertProblem(layoutProblems(layout().replace(""""sources": ["test"]""", """"sources": []""")), "$.sources")
        assertProblem(layoutProblems(" ".repeat(LayoutDataParser.MAX_FILE_CHARS + 1)), "at most")
        assertProblem(languageProblems(language(extra = """"bottomRow": [],""")), "bottomRow")
        assertProblem(languageProblems(language(tag = "German"), name = "German"), "language tag")
        assertProblem(layoutProblems(layout(extra = """"keys": {"a": {"alternates": "ä ."}},""")), "accents come from the languages")
    }

    @Test
    fun rowProblemsAreReported() {
        assertProblem(layoutProblems(layout(rows = """"q w e", "a s d"""")), "exactly 3 rows")
        assertProblem(layoutProblems(layout(rows = """"q w e", "a s q", "z x c"""")), "'q' is on the layout twice")
        assertProblem(layoutProblems(layout(rows = """"q w  e", "a s d", "z x c"""")), "double space")
        assertProblem(layoutProblems(layout(rows = """"q w e r t y u i o p å ö ä", "a s d", "z x c"""")), "needs 1–12 keys")
        assertProblem(layoutProblems(layout(rows = """"q w e", "a s d", "z x c v b n m , . ; ?"""")), "units wide, at most 10")
        assertProblem(
            layoutProblems(layout(rows = """"q w e r t y u i o p", "a s d", "z x c"""", extra = """"keys": {"q": {"width": 2}, "w": {"width": 2}, "e": {"width": 2}},""")),
            "units wide, at most 12",
        )
    }

    @Test
    fun keyProblemsAreReported() {
        assertProblem(layoutProblems(layout(rows = """"q w ${'$'}shift", "a s d", "z x c"""")), "reserved")
        assertProblem(layoutProblems(layout(rows = """"q w e\t", "a s d", "z x c"""")), "whitespace")
        assertProblem(layoutProblems(layout(rows = """"q w abcdefghi", "a s d", "z x c"""")), "1–8 characters")
        assertProblem(layoutProblems(layout(extra = """"keys": {"k": {"width": 1.5}},""")), "not a key in the rows")
        assertProblem(layoutProblems(layout(extra = """"keys": {"q": {"width": 3}},""")), "must be 0.5–2")
        assertProblem(layoutProblems(layout(extra = """"keys": {"q": {"alternates": "1 1"}},""")), "twice")
        assertProblem(layoutProblems(layout(extra = """"keys": {"q": {"alternates": "q"}},""")), "the key itself")
        assertProblem(layoutProblems(layout(extra = """"keys": {"q": {"alternates": "${"x ".repeat(17).trim()}"}},""")), "needs 1–16 alternates")
    }

    @Test
    fun idsAndLanguageTagsAreChecked() {
        listOf("qwerty", "bepo", "east_slavic", "hindi-compact", "3l").forEach { id ->
            assertEquals(emptyList(), layoutProblems(layout(id = id), name = id), id)
        }
        listOf("Qwerty", "-dash", "has space", "x".repeat(33)).forEach { id ->
            assertProblem(layoutProblems(layout(id = id), name = id), "$.id")
        }
        listOf("de", "pt-BR", "sr-Latn", "sr-Latn-RS", "es-419", "fil").forEach { tag ->
            assertEquals(emptyList(), languageProblems(language(tag = tag), name = tag), tag)
        }
        listOf("DE", "de_DE", "de-", "de-de", "deutsch", "sr-latn", "es-41", "de-CH-x").forEach { tag ->
            assertProblem(languageProblems(language(tag = tag), name = tag), "language tag")
        }
    }

    @Test
    fun languageProblemsAreReported() {
        assertProblem(languageProblems(language(layouts = """["nope"]""")), "not a bundled layout")
        assertProblem(languageProblems(language(layouts = "[]")), "at least one layout")
        assertProblem(languageProblems(language(layouts = """["qwerty", "qwerty"]""")), "twice")
        assertProblem(languageProblems(language(alternates = """{"a": "ä ä"}""")), "twice")
        assertProblem(languageProblems(language(alternates = """{"a": ""}""")), "needs 1–16 alternates")
        assertProblem(languageProblems(language(extra = """"shifted": {"ß": "ẞ"},""")), "neither a base key nor an alternate")
    }

    // endregion
}
