package com.makeeb.engine.dictionary

import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdFormatException
import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.platform.storage.ByteRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MappedDictionaryTest {
    private fun pack(words: List<MkdWord>, language: String = "en-US"): ByteArray =
        MkdWriter.write(words, mapOf("language" to language, "name" to "Test"))

    private fun dictionary(words: List<MkdWord>, suggestOffensive: Boolean = false) =
        MappedDictionary(ByteArrayRegion(pack(words)), suggestOffensive)

    @Test
    fun roundTripsEveryWordFrequencyAndMetaEntry() {
        val words = listOf(
            MkdWord("the", 222), MkdWord("then", 180), MkdWord("there", 190), MkdWord("London", 120),
            MkdWord("naïve", 90), MkdWord("Ωmega", 40), MkdWord("smile😀", 10), MkdWord("don't", 185),
            MkdWord("a", 208), MkdWord("zyzzyva", 0),
        )
        val bytes = pack(words)
        val pack = MkdPack.open(ByteArrayRegion(bytes))
        assertTrue(pack.checksumMatches())
        assertEquals("en-US", pack.languageTag)
        assertEquals("Test", pack.meta["name"])
        assertEquals(MkdFormat.FOLD_V2, pack.meta["keyFold"])
        assertEquals(words.size.toString(), pack.meta["words"])

        val dictionary = MappedDictionary(pack)
        assertEquals(words.size, dictionary.wordCount)
        words.forEach { word ->
            assertEquals(WordEntry(word.text, word.frequency), dictionary.lookup(word.text), word.text)
            assertEquals(word.text, dictionary.lookup(word.text.uppercase())?.word, "case-insensitive: ${word.text}")
        }
        assertEquals(words.sortedByDescending { it.frequency }.map { it.text }, dictionary.entries().map { it.word }.toList())
        assertNull(dictionary.lookup("th"), "a prefix is not a word")
        assertNull(dictionary.lookup("thereby"))
        assertEquals(listOf("Ωmega"), dictionary.completions("ω", 5).map { it.word })
        assertEquals(listOf("smile😀"), dictionary.completions("smile", 5).map { it.word })
    }

    @Test
    fun theSameWordsAlwaysBuildTheSameBytes() {
        val words = List(500) { MkdWord("w" + it.toString(36), it % 256) }
        assertTrue(pack(words).contentEquals(pack(words.toList())))
    }

    @Test
    fun completionsAreBestFirstAndVisitFewNodesInAHugeSubtree() {
        // 20,000 words under "a", with frequencies scattered over 0..255.
        val words = List(20_000) { i -> MkdWord("a" + base26(i), (i * 7919) % 256) }
        val dictionary = dictionary(words)
        val expected = words.map { it.frequency }.sortedDescending().take(5)

        val top = dictionary.completions("a", 5)
        assertEquals(expected, top.map { it.frequency })
        assertEquals(5, top.map { it.word }.toSet().size)
        assertTrue(top.all { it.word.startsWith("a") })
        assertTrue(dictionary.lastCompletionNodes <= 40, "visited ${dictionary.lastCompletionNodes} nodes for 20,000 words")

        // Deeper prefixes cost the same handful of nodes.
        val prefix = "a" + base26(12_345).take(2)
        val below = words.filter { it.text.startsWith(prefix) }
        assertEquals(below.map { it.frequency }.sortedDescending().take(3), dictionary.completions(prefix, 3).map { it.frequency })
        assertTrue(dictionary.lastCompletionNodes <= 30, "visited ${dictionary.lastCompletionNodes} nodes")
    }

    @Test
    fun correctionsCostGrowsWithNearbyKeysNotWithTheVocabulary() {
        val near = listOf("kitchen", "kitten", "kitchens", "chicken", "thicken", "kitsch").map { MkdWord(it, 100) }
        val far = List(20_000) { i -> MkdWord("zq" + base26(i), 50) }
        val small = dictionary(near)
        val large = dictionary(near + far)
        assertEquals(small.corrections("kitchn", 2, 9), large.corrections("kitchn", 2, 9))
        val smallRows = small.lastCorrectionRows
        assertTrue(large.lastCorrectionRows - smallRows < 50, "20,000 distant words cost ${large.lastCorrectionRows - smallRows} more rows")
    }

    @Test
    fun openingAndQueryingReadOnlyWhatTheyNeed() {
        val words = List(20_000) { i -> MkdWord("a" + base26(i), (i * 7919) % 256) }
        val bytes = pack(words)
        var reads = 0
        val counting = object : ByteRegion {
            override val size = bytes.size
            override fun u8(offset: Int): Int {
                reads++
                return bytes[offset].toInt() and 0xFF
            }
        }
        val dictionary = MappedDictionary(counting)
        assertTrue(reads < 1_000, "opening read $reads of ${bytes.size} bytes")
        reads = 0
        dictionary.completions("a", 5)
        assertTrue(reads < 2_000, "completions read $reads bytes")
    }

    @Test
    fun offensiveWordsAreKnownButNeverOffered() {
        val words = listOf(MkdWord("damn", 200, offensive = true), MkdWord("dance", 100), MkdWord("dam", 50), MkdWord("dame", 40))
        val dictionary = dictionary(words)
        assertEquals("damn", dictionary.lookup("damn")?.word, "typing it exactly is not a typo")
        assertEquals(listOf("dance", "dam", "dame"), dictionary.completions("da", 5).map { it.word })
        assertTrue(dictionary.corrections("damm", maxEdits = 1, limit = 5).none { it.entry.word == "damn" })
        assertTrue(dictionary.entries().none { it.word == "damn" })

        val optedIn = dictionary(words, suggestOffensive = true)
        assertEquals("damn", optedIn.completions("da", 1).single().word)
    }

    @Test
    fun caseVariantsShareAKeyAndLookupPrefersTheTypedSpelling() {
        val dictionary = dictionary(listOf(MkdWord("us", 160), MkdWord("US", 90), MkdWord("Polish", 80), MkdWord("polish", 70)))
        assertEquals("us", dictionary.lookup("us")?.word)
        assertEquals("US", dictionary.lookup("US")?.word)
        assertEquals("us", dictionary.lookup("Us")?.word, "no exact spelling: the most frequent")
        assertEquals("polish", dictionary.lookup("polish")?.word)
        assertEquals("Polish", dictionary.lookup("POLISH")?.word)
        assertEquals(listOf("us", "US"), dictionary.completions("u", 5).map { it.word })
        assertEquals(setOf("Polish", "polish"), dictionary.corrections("polsh", 1, 5).map { it.entry.word }.toSet())
    }

    @Test
    fun behavesLikeTrieDictionaryOnTheSameWords() {
        val entries = StarterDictionaries.english().entries().toList()
        val trie = TrieDictionary("en", entries)
        val mapped = dictionary(entries.map { MkdWord(it.word, it.frequency) })

        val queries = entries.flatMap { entry ->
            val word = entry.word.lowercase()
            (1..word.length).map { word.take(it) } + typos(word)
        }.toSet() + listOf("", "zzqx", "teh", "thete", "cat")

        for (query in queries) {
            assertEquals(trie.lookup(query), mapped.lookup(query), "lookup '$query'")
            assertEquals(ranked(trie.completions(query, 1_000)), ranked(mapped.completions(query, 1_000)), "completions '$query'")
            assertEquals(
                trie.completions(query, 3).map { it.frequency },
                mapped.completions(query, 3).map { it.frequency },
                "top completions '$query'",
            )
            for (maxEdits in 0..2) {
                assertEquals(
                    trie.corrections(query, maxEdits, 1_000).map { it.entry.word to it.edits }.toSet(),
                    mapped.corrections(query, maxEdits, 1_000).map { it.entry.word to it.edits }.toSet(),
                    "corrections '$query' within $maxEdits",
                )
                assertEquals(
                    trie.corrections(query, maxEdits, 3).map { it.edits to it.entry.frequency },
                    mapped.corrections(query, maxEdits, 3).map { it.edits to it.entry.frequency },
                    "top corrections '$query' within $maxEdits",
                )
            }
        }
    }

    @Test
    fun rejectsPacksItCannotRead() {
        val good = pack(listOf(MkdWord("word", 10)))
        assertFailsWith<MkdFormatException> { MkdPack.open(ByteArrayRegion(good.copyOf(8))) }
        assertFailsWith<MkdFormatException> { MkdPack.open(ByteArrayRegion(good.copyOf().also { it[0] = 'X'.code.toByte() })) }
        assertFailsWith<MkdFormatException> { MkdPack.open(ByteArrayRegion(good.copyOf().also { it[4] = 2 })) }

        val corrupted = good.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertFalse(MkdPack.open(ByteArrayRegion(corrupted)).checksumMatches())
    }

    @Test
    fun deferredDictionaryServesTheFallbackUntilThePackIsInstalled() {
        val deferred = DeferredDictionary(TrieDictionary("en", listOf(WordEntry("hello", 200))))
        assertEquals("hello", deferred.lookup("hello")?.word)
        assertFalse(deferred.isInstalled)
        deferred.install(dictionary(listOf(MkdWord("kitchen", 116))))
        assertTrue(deferred.isInstalled)
        assertNull(deferred.lookup("hello"))
        assertEquals("kitchen", deferred.completions("kitch", 3).single().word)
        assertEquals("en-US", deferred.languageTag)
    }

    /** Sorted by frequency, ties by spelling, so orderings that differ only among ties compare equal. */
    private fun ranked(entries: List<WordEntry>) = entries.sortedWith(compareByDescending<WordEntry> { it.frequency }.thenBy { it.word })

    private fun typos(word: String): List<String> = buildList {
        for (i in word.indices) {
            add(word.removeRange(i, i + 1))
            add(word.substring(0, i) + 'x' + word.substring(i + 1))
            if (i + 1 < word.length) add(word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2))
        }
    }

    private fun base26(n: Int): String {
        var value = n
        return buildString {
            repeat(4) {
                append('a' + value % 26)
                value /= 26
            }
        }.reversed()
    }

    @Test
    fun keysFoldDiacriticsAndApostrophesButSuggestionsKeepTheSpelling() {
        val words = dictionary(listOf(MkdWord("naïve", 90), MkdWord("naive", 60), MkdWord("café", 150), MkdWord("cafeteria", 110), MkdWord("don't", 200), MkdWord("Straße", 100)))
        assertEquals("naive", words.lookup("naive")?.word, "the spelling typed, when it exists")
        assertEquals("naïve", words.lookup("naïve")?.word)
        assertEquals(listOf("café", "cafeteria"), words.completions("cafe", 5).map { it.word })
        assertEquals("don't", words.lookup("dont")?.word)
        assertEquals("don't", words.completions("don", 1).single().word)
        assertEquals("Straße", words.lookup("strasse")?.word)
        assertTrue(words.corrections("cafw", 1, 5).any { it.entry.word == "café" })
    }
}
