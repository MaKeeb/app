package com.makeeb.engine.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ClipboardHistoryTest {
    private class TestClock(var now: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun newestFirstWithoutDuplicates() {
        val history = ClipboardHistory(clock = TestClock())
        history.add("one"); history.add("two"); history.add("one")
        assertEquals(listOf("one", "two"), history.entries.value.map { it.text })
    }

    @Test
    fun capacityNeverEvictsPinnedEntries() {
        val history = ClipboardHistory(capacity = 2, clock = TestClock())
        history.add("keep")
        history.setPinned(history.entries.value.single().id, true)
        history.add("a"); history.add("b")
        assertEquals(listOf("b", "keep"), history.entries.value.map { it.text })
    }

    @Test
    fun expiryKeepsPinnedEntries() {
        val clock = TestClock()
        val history = ClipboardHistory(retention = 10.minutes, clock = clock)
        history.add("old"); history.add("pinned")
        history.setPinned(history.entries.value.first { it.text == "pinned" }.id, true)
        clock.now = Instant.fromEpochMilliseconds(11.minutes.inWholeMilliseconds)
        history.expire()
        assertEquals(listOf("pinned"), history.entries.value.map { it.text })
    }
}
