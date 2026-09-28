package com.makeeb.tools.dictionaries

import com.makeeb.engine.dictionary.pack.MkdWord
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AospWordListTest {
    private val sample = """
        dictionary=main:en_us,locale=en_US,description=English (US),date=1414726260,version=54
         word=the,f=222,flags=,originalFreq=222
         word=London,f=150,flags=,originalFreq=150
         word=rape,f=115,flags=offensive,originalFreq=115,possibly_offensive=true
         word=cafe${'́'},f=103,flags=,originalFreq=103
         word=don${'’'}t,f=185,flags=,originalFreq=130
         word=cant,f=70,flags=,originalFreq=73
          shortcut=can't,f=whitelist
         word=im,f=0,not_a_word=true
          shortcut=I'm,f=whitelist
         word=hello,f=120,flags=
          bigram=there,f=3
    """.trimIndent()

    @Test
    fun keepsCaseFrequencyAndOffensiveFlagsAndSkipsNotAWordEntries() {
        val list = AospWordList.parse(sample.lineSequence())
        assertEquals("en-US", list.languageTag)
        assertEquals("54", list.header["version"])
        assertEquals(
            listOf(
                MkdWord("the", 222),
                MkdWord("London", 150),
                MkdWord("rape", 115, offensive = true),
                MkdWord("café", 103), // NFC
                MkdWord("don't", 185), // typographic apostrophe folded
                MkdWord("cant", 70),
                MkdWord("hello", 120),
            ),
            list.words,
        )
        assertEquals(1, list.skippedNotAWord)
        assertEquals(2, list.skippedShortcuts)
        assertEquals(1, list.skippedBigrams)
    }

    @Test
    fun aCachedFileWithTheRightHashNeedsNoNetwork() {
        val cache = Files.createTempDirectory("mkd-cache").toFile()
        try {
            val bytes = "word list".toByteArray()
            File(cache, "list.gz").writeBytes(bytes)
            val source = PinnedSource("list.gz", PinnedSource.sha256Of(bytes), mirrors = emptyList())
            assertEquals(File(cache, "list.gz"), source.fetch(cache) {})

            val tampered = PinnedSource("list.gz", "0".repeat(64), mirrors = emptyList())
            assertFailsWith<IOException> { tampered.fetch(cache) {} }
        } finally {
            cache.deleteRecursively()
        }
    }
}
