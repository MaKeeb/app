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
    fun searchPrefersPrefixMatches() {
        val results = catalog.search("heart").map { it.value }
        assertEquals("😍", results.first()) // "heart_eyes" name → "heart eyes"
        assertTrue("❤️" in results)
    }

    @Test
    fun recentsAreDeduplicatedNewestFirst() {
        val recents = EmojiRecents(capacity = 2)
        val (a, b, c) = catalog.emojis(EmojiCategory.FoodAndDrink).take(3)
        recents.record(a); recents.record(b); recents.record(a); recents.record(c)
        assertEquals(listOf(c, a), recents.recents.value)
    }
}
