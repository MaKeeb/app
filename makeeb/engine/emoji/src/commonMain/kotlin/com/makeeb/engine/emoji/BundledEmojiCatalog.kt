package com.makeeb.engine.emoji

/**
 * A small hand-picked set so the panel has content. The real catalogue is generated from
 * Unicode `emoji-test.txt` + CLDR annotations, filtered by what the OS font can render
 * (board: `emoji-data-pipeline`).
 */
class BundledEmojiCatalog : EmojiCatalog {
    private val all: List<Emoji> = buildList {
        fun put(category: EmojiCategory, vararg entries: Pair<String, String>) =
            entries.forEach { (value, words) ->
                val tokens = words.split(' ')
                add(Emoji(value, tokens.first().replace('_', ' '), category, tokens.drop(1)))
            }

        put(
            EmojiCategory.SmileysAndPeople,
            "😀" to "grinning_face happy smile", "😂" to "tears_of_joy laugh lol", "🥲" to "smiling_with_tear",
            "😊" to "smiling_face blush happy", "😍" to "heart_eyes love", "🤔" to "thinking hmm",
            "😎" to "sunglasses cool", "😭" to "crying sad", "😴" to "sleeping tired", "🙃" to "upside_down",
            "👍" to "thumbs_up yes ok like", "👎" to "thumbs_down no dislike", "👋" to "waving_hand hello bye",
            "🙏" to "folded_hands please thanks", "👏" to "clapping applause", "💪" to "flexed_biceps strong",
        )
        put(
            EmojiCategory.AnimalsAndNature,
            "🐶" to "dog puppy", "🐱" to "cat kitten", "🦊" to "fox", "🐻" to "bear", "🐼" to "panda",
            "🌸" to "cherry_blossom flower", "🌳" to "tree", "🌈" to "rainbow", "☀️" to "sun sunny", "🌧️" to "rain",
        )
        put(
            EmojiCategory.FoodAndDrink,
            "🍕" to "pizza", "🍔" to "hamburger burger", "🍣" to "sushi", "🍎" to "apple", "☕" to "coffee",
            "🍺" to "beer", "🍷" to "wine", "🍰" to "cake birthday", "🌮" to "taco", "🥐" to "croissant",
        )
        put(
            EmojiCategory.Activities,
            "⚽" to "soccer football", "🏀" to "basketball", "🎮" to "video_game gaming", "🎸" to "guitar music",
            "🎉" to "party_popper celebrate tada", "🏆" to "trophy win", "🎨" to "art paint",
        )
        put(
            EmojiCategory.TravelAndPlaces,
            "🚗" to "car", "✈️" to "airplane flight travel", "🚆" to "train", "🏠" to "house home",
            "🗺️" to "map", "🏖️" to "beach holiday", "🚲" to "bicycle bike",
        )
        put(
            EmojiCategory.Objects,
            "💡" to "light_bulb idea", "📱" to "phone mobile", "💻" to "laptop computer", "⌨️" to "keyboard",
            "📷" to "camera photo", "🔑" to "key", "📚" to "books read", "⏰" to "alarm_clock time",
        )
        put(
            EmojiCategory.Symbols,
            "❤️" to "red_heart love", "💔" to "broken_heart", "✅" to "check_mark done yes", "❌" to "cross_mark no",
            "⭐" to "star", "🔥" to "fire lit", "💯" to "hundred_points perfect", "⚠️" to "warning",
        )
        put(
            EmojiCategory.Flags,
            "🏳️" to "white_flag", "🏳️‍🌈" to "rainbow_flag pride", "🇬🇧" to "united_kingdom uk",
            "🇭🇺" to "hungary", "🇺🇸" to "united_states usa", "🇪🇺" to "european_union eu",
        )
    }

    private val byCategory = all.groupBy { it.category }

    override val categories: List<EmojiCategory> = EmojiCategory.entries

    override fun emojis(category: EmojiCategory): List<Emoji> = byCategory[category].orEmpty()

    override fun search(query: String, limit: Int): List<Emoji> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val terms = { emoji: Emoji -> listOf(emoji.name) + emoji.keywords }
        val prefix = all.filter { emoji -> terms(emoji).any { it.startsWith(needle) } }
        val contains = all.filter { emoji -> emoji !in prefix && terms(emoji).any { needle in it } }
        return (prefix + contains).take(limit)
    }
}
