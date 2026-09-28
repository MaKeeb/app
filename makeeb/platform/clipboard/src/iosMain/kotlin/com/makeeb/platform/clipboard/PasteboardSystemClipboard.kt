package com.makeeb.platform.clipboard

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.UIKit.UIPasteboard

/**
 * The general pasteboard. iOS has no change callback a keyboard extension can use, so [changes]
 * polls `changeCount` while collected. Reading requires Full Access; [hasAccess] gates it.
 */
class PasteboardSystemClipboard(
    private val hasAccess: () -> Boolean,
    private val pollIntervalMillis: Long = 1_000,
) : SystemClipboard {
    private val pasteboard get() = UIPasteboard.generalPasteboard

    override fun read(): Clip? {
        if (!hasAccess() || !pasteboard.hasStrings) return null
        return pasteboard.string?.takeIf { it.isNotEmpty() }?.let { Clip(it) }
    }

    override fun write(text: String) {
        if (hasAccess()) pasteboard.string = text
    }

    override val changes: Flow<Clip> = flow {
        var lastChangeCount = pasteboard.changeCount
        while (true) {
            delay(pollIntervalMillis)
            val changeCount = pasteboard.changeCount
            if (changeCount != lastChangeCount) {
                lastChangeCount = changeCount
                read()?.let { emit(it) }
            }
        }
    }
}
