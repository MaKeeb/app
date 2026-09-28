package com.makeeb.engine.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiTest {
    private val catalog = BundledEmojiCatalog()

    @Test
    fun everyCategoryHasEmoji() {
        catalog.categories.forEach { assertTrue(catalog.emojis(it).isNotEmpty(), "$it is empty") }
    }

    @Test
    fun theWholeCatalogueLoads() {
        val count = catalog.categories.sumOf { catalog.emojis(it).size }
        assertTrue(count > 1800, "got $count")
        val thumbs = catalog.emojis(EmojiCategory.SmileysAndPeople).first { it.value == "👍" }
        assertEquals("thumbs up", thumbs.name)
        assertTrue(thumbs.hasSkinTones)
        assertTrue("+1" in thumbs.keywords)
        assertEquals(thumbs, catalog.find("👍"))
        assertEquals(null, catalog.find("not an emoji"))
    }

    @Test
    fun searchRanksExactThenPrefixThenKeywordMatches() {
        val heart = catalog.search("heart").map { it.value }
        assertEquals("❤️", heart.first(), "the most used of the exact keyword matches: $heart")
        assertTrue("😍" in heart)
        assertEquals("🍕", catalog.search("pizza").first().value)
        assertEquals("😂", catalog.search("tears of joy").first().value)
        assertEquals("👍", catalog.search("+1").first().value)
        assertTrue(catalog.search("thumbs").take(2).map { it.value }.containsAll(listOf("👍", "👎")))
        assertTrue(catalog.search("RED HEART").map { it.value }.first() == "❤️")
        assertTrue(catalog.search("zzzqqq").isEmpty())
    }

    @Test
    fun aWordNamesItsEmojiExactlyAndTheMostUsedWins() {
        assertEquals("🍕", catalog.forWord("pizza")?.value)
        assertEquals("🍕", catalog.forWord("Pizza")?.value)
        assertEquals("🐕", catalog.forWord("dog")?.value, "an exact name beats a keyword")
        assertEquals("😍", catalog.forWord("love")?.value, "the most used of the keyword matches")
        assertEquals(null, catalog.forWord("pizz"), "no partial words")
        assertEquals(null, catalog.forWord("the"))
    }

    @Test
    fun recentsAreDeduplicatedNewestFirst() {
        val recents = EmojiRecents(capacity = 2)
        val (a, b, c) = catalog.emojis(EmojiCategory.FoodAndDrink).take(3)
        recents.record(a); recents.record(b); recents.record(a); recents.record(c)
        assertEquals(listOf(c, a), recents.recents.value)
    }
}
