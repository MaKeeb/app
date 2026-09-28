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
    /** Comes in the five skin tones (for the tone picker). */
    val hasSkinTones: Boolean = false,
)

interface EmojiCatalog {
    val categories: List<EmojiCategory>

    fun emojis(category: EmojiCategory): List<Emoji>

    /** Case-insensitive, by word prefixes of the name and keywords; best matches first. */
    fun search(query: String, limit: Int = 24): List<Emoji>

    /** The emoji with exactly this [value], or null. */
    fun find(value: String): Emoji?

    /**
     * The emoji [word] names: an exact name first, then an exact keyword, the most used on a tie
     * ("pizza" → 🍕, "love" → 😍). Null when nothing matches exactly.
     */
    fun forWord(word: String): Emoji?
}
