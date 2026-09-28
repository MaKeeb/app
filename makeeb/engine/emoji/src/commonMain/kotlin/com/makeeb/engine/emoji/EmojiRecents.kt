package com.makeeb.engine.emoji

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Most-recently-used emoji, newest first. In memory for now (board: `emoji-recents`). */
class EmojiRecents(private val capacity: Int = 32) {
    private val state = MutableStateFlow<List<Emoji>>(emptyList())
    val recents: StateFlow<List<Emoji>> = state.asStateFlow()

    fun record(emoji: Emoji) {
        state.update { current -> (listOf(emoji) + current.filterNot { it.value == emoji.value }).take(capacity) }
    }
}
