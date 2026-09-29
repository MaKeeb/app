package com.makeeb.engine.dictionary

import com.makeeb.platform.storage.PackFile
import com.makeeb.testing.FakePackFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstalledPackTest {
    private val sha = "2adf8e4a986909837b7fcfbfb7cba0e73ca3627b81c74c050a40367ab3102b20"

    @Test
    fun theFileNameSaysLanguageAndVersion() {
        val name = InstalledPack.fileName("pt-BR", sha)
        assertEquals("pt-BR-2adf8e4a98690983.mkd", name)
        val pack = InstalledPack.parse(PackFile(name, 42))!!
        assertEquals(InstalledPack("pt-BR", "2adf8e4a98690983", 42), pack)
        assertEquals("pt", pack.baseLanguage)
        assertEquals(true, pack.isVersion(sha))
        assertEquals(false, pack.isVersion("f".repeat(64)))
    }

    @Test
    fun otherFilesAreNotPacks() {
        for (name in listOf("en_US.mkd", "hu.mkd", "hu-2adf8e4a98690983.mkd.part", "hu-2ADF8E4A98690983.mkd", "hu-2adf.mkd", "-2adf8e4a98690983.mkd", ".DS_Store")) {
            assertNull(InstalledPack.parse(PackFile(name, 1)), name)
        }
    }

    @Test
    fun oneVersionPerLanguageAndRegionalPacksServeTheirLanguage() {
        val files = FakePackFiles(
            InstalledPack.fileName("hu", sha) to ByteArray(3),
            InstalledPack.fileName("hu", "0".repeat(64)) to ByteArray(3),
            InstalledPack.fileName("pt-BR", sha) to ByteArray(5),
            "stray.txt" to ByteArray(1),
        )
        val packs = InstalledPack.list(files)
        assertEquals(listOf("hu", "pt-BR"), packs.map { it.language })
        assertEquals("2adf8e4a98690983", packs.first().version, "the same pick every time")
        assertEquals("pt-BR", packs.forLanguage("pt")?.language)
        assertEquals("pt-BR", packs.forLanguage("pt-PT")?.language, "the language's pack when the region has none")
        assertEquals("hu", packs.forLanguage("hu-HU")?.language)
        assertNull(packs.forLanguage("de"))
    }
}
