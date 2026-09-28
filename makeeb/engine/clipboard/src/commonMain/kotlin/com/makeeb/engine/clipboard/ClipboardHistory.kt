package com.makeeb.engine.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class ClipboardEntry(
    val id: Long,
    val text: String,
    val createdAt: Instant,
    val pinned: Boolean = false,
)

/**
 * Recent clips, newest first. Pinned entries survive [clearUnpinned] and expiry. Sensitive clips
 * are filtered by the caller before [add]; this class never sees them.
 */
@OptIn(ExperimentalTime::class)
class ClipboardHistory(
    private val capacity: Int = 30,
    private val retention: Duration = 1.hours,
    private val clock: Clock = Clock.System,
) {
    private val state = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = state.asStateFlow()
    private var nextId = 1L

    fun add(text: String) {
        if (text.isBlank()) return
        state.update { current ->
            val existing = current.firstOrNull { it.text == text }
            val entry = existing?.copy(createdAt = clock.now()) ?: ClipboardEntry(nextId++, text, clock.now())
            val others = current.filterNot { it.text == text }
            val (pinned, unpinned) = others.partition { it.pinned }
            (listOf(entry) + unpinned).take((capacity - pinned.size).coerceAtLeast(1)) + pinned
        }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        state.update { current -> current.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    fun remove(id: Long) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    fun clearUnpinned() {
        state.update { current -> current.filter { it.pinned } }
    }

    /** Drop unpinned clips older than the retention window. Call when the keyboard is shown. */
    fun expire() {
        val cutoff = clock.now() - retention
        state.update { current -> current.filter { it.pinned || it.createdAt >= cutoff } }
    }
}
