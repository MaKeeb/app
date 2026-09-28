package com.makeeb.shared.keyboard

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * How long each key costs on the main thread: the engine's edit, suggestions and the calls into
 * the host, from the touch reaching the keyboard to the text going out. Debug builds report a
 * summary every [every] keys, to compare against the one-frame budget. It records durations only,
 * never which key.
 */
class KeyLatency(
    private val report: (String) -> Unit,
    private val every: Int = 50,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val samples = ArrayList<Duration>(every)

    fun <T> measure(block: () -> T): T {
        val mark = timeSource.markNow()
        try {
            return block()
        } finally {
            record(mark.elapsedNow())
        }
    }

    private fun record(duration: Duration) {
        samples += duration
        if (samples.size < every) return
        samples.sort()
        fun percentile(p: Int) = samples[((samples.size - 1) * p) / 100]
        report("keys=${samples.size} p50=${percentile(50).ms()} p95=${percentile(95).ms()} max=${samples.last().ms()}")
        samples.clear()
    }

    private fun Duration.ms(): String {
        val hundredths = inWholeMicroseconds / 10
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}ms"
    }
}
