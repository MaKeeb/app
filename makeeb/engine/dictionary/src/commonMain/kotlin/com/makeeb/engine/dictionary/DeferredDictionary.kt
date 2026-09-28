package com.makeeb.engine.dictionary

import kotlin.concurrent.Volatile

/**
 * Serves [fallback] until [install] hands over the real dictionary. The keyboard can then show,
 * and type, while its pack is still being mapped on a background thread. The switch is a single
 * reference write, so queries on the main thread never wait for the loader.
 */
class DeferredDictionary(private val fallback: Dictionary) : Dictionary {
    @Volatile
    private var installed: Dictionary? = null

    /** The dictionary queries go to now. */
    val current: Dictionary get() = installed ?: fallback

    val isInstalled: Boolean get() = installed != null

    fun install(dictionary: Dictionary) {
        installed = dictionary
    }

    override val languageTag: String get() = current.languageTag

    override fun lookup(word: String): WordEntry? = current.lookup(word)

    override fun completions(prefix: String, limit: Int): List<WordEntry> = current.completions(prefix, limit)

    override fun corrections(word: String, maxEdits: Int, limit: Int): List<WordMatch> = current.corrections(word, maxEdits, limit)

    override fun entries(): Sequence<WordEntry> = current.entries()
}
