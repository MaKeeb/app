package com.makeeb.engine.packs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PackCatalogueTest {
    private fun entry(language: String, sha: Char = 'a', version: String = "1.1") = PackEntry(
        language = language,
        name = "Name \"$language\" — ő",
        file = "${language.replace('-', '_')}.mkd",
        url = "${language.replace('-', '_')}.mkd",
        size = 6_404_145,
        sha256 = sha.toString().repeat(64),
        mkdVersion = version,
        words = 170_043,
        nextWords = true,
        licence = "Apache-2.0 AND CC-BY-4.0",
        attribution = "Word list from AOSP.\nCounts from Leipzig.",
    )

    @Test
    fun roundTripsThroughJson() {
        val catalogue = PackCatalogue(listOf(entry("de"), entry("pt-BR", 'b'), entry("hu", 'c')))
        val json = catalogue.toJson()
        assertEquals(catalogue.packs, PackCatalogue.parse(json).packs)
        assertEquals(json, PackCatalogue.parse(json).toJson())
    }

    @Test
    fun findsAPackByTagOrLanguage() {
        val catalogue = PackCatalogue(listOf(entry("pt-BR"), entry("pt-PT"), entry("de")))
        assertEquals("pt-PT", catalogue.forLanguage("pt-PT")?.language)
        assertEquals("pt-BR", catalogue.forLanguage("pt")?.language, "the first listed for the language")
        assertEquals("de", catalogue.forLanguage("de-AT")?.language)
        assertNull(catalogue.forLanguage("hu"))
    }

    @Test
    fun readsWhatANewerCatalogueAdds() {
        val json = """
            {"format": 1, "published": "2027-01-01", "packs": [
              {"language": "sv", "name": "Svenska", "file": "sv.mkd", "url": "https://example.org/sv.mkd", "size": 12,
               "sha256": "${"A".repeat(64)}", "mkdVersion": "1.4", "words": 3, "licence": "CC-BY-4.0", "attribution": "x",
               "script": "Latn"},
              {"language": "fi", "name": "Suomi", "file": "fi.mkd", "url": "fi.mkd", "size": 12, "sha256": "${"b".repeat(64)}",
               "mkdVersion": "2.0", "words": 3, "licence": "CC-BY-4.0", "attribution": "x"}
            ]}
        """.trimIndent()
        val packs = PackCatalogue.parse(json).packs
        assertEquals(listOf("sv"), packs.map { it.language }, "a pack of an unknown MKD major version is left out")
        assertEquals("a".repeat(64), packs.single().sha256, "hashes compare in lower case")
        assertEquals(false, packs.single().nextWords)
    }

    @Test
    fun rejectsWhatCouldHarmTheDevice() {
        fun catalogue(language: String = "de", sha: String = "a".repeat(64), size: Long = 10) =
            """{"format":1,"packs":[{"language":"$language","name":"n","file":"f","url":"u","size":$size,"sha256":"$sha","mkdVersion":"1.1","words":1,"licence":"l","attribution":"a"}]}"""
        PackCatalogue.parse(catalogue())
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse(catalogue(language = "../../x")) }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse(catalogue(language = "de/evil")) }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse(catalogue(sha = "xyz")) }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse(catalogue(size = 0)) }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse(catalogue(size = 1L shl 40)) }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse("""{"format":2,"packs":[]}""") }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse("<html>Not Found</html>") }
        assertFailsWith<CatalogueFormatException> { PackCatalogue.parse("""{"format":1,"packs":[}""") }
    }

    @Test
    fun resolvesPackUrlsAgainstTheCatalogue() {
        val release = "https://github.com/owner/repo/releases/download/dictionaries-v1/catalogue.json"
        assertEquals("https://github.com/owner/repo/releases/download/dictionaries-v1/hu.mkd", resolveUrl(release, "hu.mkd"))
        assertEquals("https://github.com/elsewhere/hu.mkd", resolveUrl(release, "/elsewhere/hu.mkd"))
        assertEquals("https://cdn.example.org/hu.mkd", resolveUrl(release, "https://cdn.example.org/hu.mkd"))
        assertEquals("http://localhost:8000/hu.mkd", resolveUrl("http://localhost:8000/catalogue.json", "hu.mkd"))
        assertEquals("http://localhost:8000/hu.mkd", resolveUrl("http://localhost:8000", "hu.mkd"))
        assertEquals("http://10.0.2.2:8000/packs/hu.mkd", resolveUrl("http://10.0.2.2:8000/packs/catalogue.json?v=2", "hu.mkd"))
    }
}
