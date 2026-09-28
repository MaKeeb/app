package com.makeeb.platform.clipboard

import kotlinx.coroutines.flow.Flow

/** A clip as seen by the keyboard. */
data class Clip(
    val text: String,
    /**
     * The source flagged it sensitive: Android 13+ `EXTRA_IS_SENSITIVE`, or on iOS the
     * nspasteboard.org concealed/transient/auto-generated types password managers set. Never keep it.
     */
    val isSensitive: Boolean = false,
)

/**
 * The OS clipboard. On iOS this needs Full Access; without it [read] returns null and
 * [changes] never emits, and the keyboard must keep working.
 */
interface SystemClipboard {
    fun read(): Clip?

    fun write(text: String)

    /** New clips while the keyboard process is alive. Collect only while the keyboard is shown. */
    val changes: Flow<Clip>
}
