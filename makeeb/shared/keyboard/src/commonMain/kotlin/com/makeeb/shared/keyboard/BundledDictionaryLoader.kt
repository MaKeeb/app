package com.makeeb.shared.keyboard

import com.makeeb.engine.dictionary.BundledPacks
import com.makeeb.engine.dictionary.DeferredDictionary
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.platform.storage.BundledFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Maps the bundled English pack ([PACK]) on [scope], a background dispatcher, and swaps it into
 * [dictionary]. Until then, or for good if the pack is missing or unreadable, the starter list
 * answers, so showing the keyboard never waits for the pack.
 *
 * Mapping reads only the pack's header; [MappedDictionary.warmUp] then touches the trie's top
 * levels so the first keystrokes don't stall on page faults. Nothing is copied onto the heap.
 */
class BundledDictionaryLoader(
    private val files: BundledFiles?,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val deferred = DeferredDictionary(StarterDictionaries.english())

    /** The dictionary to hand the engines: the starter list now, the pack once it is mapped. */
    val dictionary: Dictionary get() = deferred

    private val mutableStatus = MutableStateFlow<Status>(Status.Idle)

    /** Where loading stands, for debug logs. Durations and counts only, never text. */
    val status: StateFlow<Status> = mutableStatus.asStateFlow()

    sealed interface Status {
        data object Idle : Status
        data object Loading : Status
        /** [mapped]: finding and mapping the file; [opened]: reading the header; [warmed]: touching the top of the trie. */
        data class Loaded(val words: Int, val mapped: Duration, val opened: Duration, val warmed: Duration) : Status
        data object Missing : Status
        data class Failed(val reason: String) : Status
    }

    /** Starts loading once; later calls do nothing. */
    fun start() {
        if (!mutableStatus.compareAndSet(Status.Idle, Status.Loading)) return
        scope.launch { load() }
    }

    private fun load() {
        var mark = timeSource.markNow()
        fun lap(): Duration = mark.elapsedNow().also { mark = timeSource.markNow() }
        val region = files?.map(PACK)
        if (region == null) {
            mutableStatus.value = Status.Missing
            return
        }
        val mapped = lap()
        try {
            val pack = MappedDictionary(region)
            val opened = lap()
            pack.warmUp()
            val warmed = lap()
            deferred.install(pack)
            mutableStatus.value = Status.Loaded(pack.wordCount, mapped, opened, warmed)
        } catch (e: RuntimeException) {
            // A corrupt pack (MkdFormatException, or a read past its end): keep the starter list
            // rather than crash the keyboard from a background thread.
            region.close()
            mutableStatus.value = Status.Failed(e.message ?: e::class.simpleName ?: "unreadable pack")
        }
    }

    companion object {
        const val PACK = BundledPacks.EN_US
    }
}
