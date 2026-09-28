package com.makeeb.engine.emoji

enum class EmojiCategory(val icon: String) {
    SmileysAndPeople("😀"),
    AnimalsAndNature("🐻"),
    FoodAndDrink("🍔"),
    Activities("⚽"),
    TravelAndPlaces("🚗"),
    Objects("💡"),
    Symbols("❤️"),
    Flags("🏳️"),
}

data class Emoji(
    val value: String,
    val name: String,
    val category: EmojiCategory,
    val keywords: List<String> = emptyList(),
)

interface EmojiCatalog {
    val categories: List<EmojiCategory>

    fun emojis(category: EmojiCategory): List<Emoji>

    /** Case-insensitive match on name and keywords, prefix matches first. */
    fun search(query: String, limit: Int = 24): List<Emoji>
}
