package com.makeeb.shared.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.TestTimeSource

class KeyLatencyTest {
    @Test
    fun reportsPercentilesEveryBatchAndNothingAboutTheKeys() {
        val time = TestTimeSource()
        val reports = mutableListOf<String>()
        val latency = KeyLatency(reports::add, every = 4, timeSource = time)
        listOf(100, 200, 300, 4000).forEach { micros -> latency.measure { time += micros.microseconds } }
        assertEquals(listOf("keys=4 p50=0.20ms p95=0.30ms max=4.00ms"), reports)
    }
}
