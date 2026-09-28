package com.makeeb.core.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reusable texts the user keeps at hand (an address, a sign-off), offered in the keyboard's
 * clipboard panel. Like the preferences they flow one way: the companion app edits them, the
 * keyboard reads them ([reload] when the iOS extension appears). They are the user's own texts,
 * not clipboard data, so they work on iOS without Full Access.
 */
interface SnippetsRepository {
    val snippets: StateFlow<List<String>>

    fun add(text: String)

    fun remove(index: Int)

    fun reload()
}

/** [SnippetsRepository] in the same key-value store as the preferences. */
class SettingsSnippetsRepository(private val settings: Settings) : SnippetsRepository {
    private val state = MutableStateFlow(read())
    override val snippets: StateFlow<List<String>> = state.asStateFlow()

    override fun add(text: String) {
        val snippet = text.trim().take(MAX_LENGTH)
        if (snippet.isEmpty() || snippet in state.value) return
        write((state.value + snippet).takeLast(MAX_SNIPPETS))
    }

    override fun remove(index: Int) {
        if (index in state.value.indices) write(state.value.filterIndexed { i, _ -> i != index })
    }

    override fun reload() {
        state.value = read()
    }

    private fun read(): List<String> =
        (0 until settings.getInt(COUNT, 0).coerceIn(0, MAX_SNIPPETS)).mapNotNull { settings.getStringOrNull(key(it)) }

    private fun write(snippets: List<String>) {
        (snippets.size until settings.getInt(COUNT, 0)).forEach { settings.remove(key(it)) }
        snippets.forEachIndexed { i, text -> settings.putString(key(i), text) }
        settings.putInt(COUNT, snippets.size)
        state.value = snippets
    }

    private companion object {
        const val COUNT = "clipboard.snippets.count"
        const val MAX_SNIPPETS = 20
        const val MAX_LENGTH = 1_000

        fun key(index: Int) = "clipboard.snippets.$index"
    }
}
