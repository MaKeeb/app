package com.makeeb.shared.keyboard

import com.makeeb.engine.dictionary.BundledPacks
import com.makeeb.engine.dictionary.DeferredDictionary
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.InstalledPack
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.SelectedDictionaries
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.forLanguage
import com.makeeb.platform.storage.BundledFiles
import com.makeeb.platform.storage.ByteRegion
import com.makeeb.platform.storage.PackFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Maps the dictionary packs for the selected languages ([languageTags], primary first) on
 * [scope], a background dispatcher, and swaps them into [dictionary]:
 *
 * - The primary language's pack is the main dictionary (completions, corrections, next words):
 *   the bundled English pack for English, a downloaded one ([PackFiles]) for the others. If the
 *   primary has none, the next selected language with a pack stands in, and the bundled English
 *   pack when none has; autocorrect then pauses for the languages without one.
 * - The other selected languages' packs are mapped too, and vouch for their words
 *   ([SelectedDictionaries]), so autocorrect runs once every selected language has a pack.
 * - Until the first pack is mapped, or for good if none can be, the starter list answers, so
 *   showing the keyboard never waits.
 *
 * It resolves again when the languages change and on [refresh], which the session calls for
 * every field: the companion app may have installed or removed a pack since, in this process
 * (Android) or another (iOS). Packs it no longer needs are dropped, and their mappings go once
 * nothing reads them. Mapping reads only a pack's header; [MappedDictionary.warmUp] touches the
 * top of its trie. Nothing is copied onto the heap, and nothing here touches the network.
 */
class DictionaryLoader(
    private val bundled: BundledFiles?,
    private val packs: PackFiles?,
    private val languageTags: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val deferred = DeferredDictionary(StarterDictionaries.english())

    /** The dictionary to hand the engines: the starter list now, the packs once they are mapped. */
    val dictionary: Dictionary get() = deferred

    private val mutableStatus = MutableStateFlow<Status>(Status.Idle)

    /** Where loading stands, for debug logs. Languages, durations and counts only, never text. */
    val status: StateFlow<Status> = mutableStatus.asStateFlow()

    sealed interface Status {
        data object Idle : Status
        data object Loading : Status

        /**
         * [language]: the main dictionary's; [others]: the languages that vouch for words.
         * [mapped]: finding and mapping the main pack; [opened]: reading its header; [warmed]:
         * touching the top of its trie (all zero when it was already mapped).
         */
        data class Loaded(
            val language: String,
            val words: Int,
            val mapped: Duration,
            val opened: Duration,
            val warmed: Duration,
            val others: List<String> = emptyList(),
        ) : Status

        data object Missing : Status
        data class Failed(val reason: String) : Status
    }

    /** Where a language's words come from. */
    private sealed interface Source {
        data object Bundled : Source

        data class Downloaded(val fileName: String) : Source
    }

    private class Opened(val dictionary: MappedDictionary, val mapped: Duration, val opened: Duration, val warmed: Duration)

    /** Resolutions are serialised through this: one worker, the latest request wins. */
    private val requests = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var tags: List<String> = emptyList()

    /** What is mapped now, by source. Only the worker touches it. */
    private val open = HashMap<Source, Opened>()
    private var installedSources: List<Source>? = null

    /** Starts loading once; later calls do nothing. */
    fun start() {
        if (!mutableStatus.compareAndSet(Status.Idle, Status.Loading)) return
        scope.launch {
            languageTags.distinctUntilChanged().collect {
                tags = it
                requests.trySend(Unit)
            }
        }
        scope.launch {
            for (request in requests) resolve()
        }
    }

    /** Looks for installed or removed packs again. Cheap: a directory listing, off the main thread. */
    fun refresh() {
        requests.trySend(Unit)
    }

    private fun resolve() {
        val installed = packs?.let(InstalledPack::list).orEmpty()
        val selected = tags.ifEmpty { listOf(BundledPacks.LANGUAGE) }
        val sources = selected.mapNotNull { tag ->
            if (tag.substringBefore('-').equals(BundledPacks.LANGUAGE, ignoreCase = true)) Source.Bundled
            else installed.forLanguage(tag)?.let { Source.Downloaded(it.fileName) }
        }.distinct()
        // English stands in when no selected language has a pack.
        val wanted = sources.ifEmpty { listOf(Source.Bundled) }
        if (wanted == installedSources) return

        var failure: String? = null
        val dictionaries = wanted.mapNotNull { source ->
            open[source] ?: try {
                map(source)?.also { open[source] = it }
            } catch (e: RuntimeException) {
                // A corrupt pack (MkdFormatException, or a read past its end): skip it rather than
                // crash the keyboard from a background thread.
                failure = e.message ?: e::class.simpleName ?: "unreadable pack"
                null
            }
        }
        // Whatever is no longer wanted is dropped; its mapping goes once nothing reads it.
        open.keys.retainAll(wanted.toSet())
        val main = dictionaries.firstOrNull()
        if (main == null) {
            mutableStatus.value = failure?.let(Status::Failed) ?: Status.Missing
            return
        }
        val others = dictionaries.drop(1).map { it.dictionary }
        deferred.install(if (others.isEmpty()) main.dictionary else SelectedDictionaries(main.dictionary, others))
        installedSources = wanted
        mutableStatus.value = Status.Loaded(
            language = main.dictionary.languageTag,
            words = main.dictionary.wordCount,
            mapped = main.mapped,
            opened = main.opened,
            warmed = main.warmed,
            others = others.map { it.languageTag },
        )
    }

    private fun map(source: Source): Opened? {
        var mark = timeSource.markNow()
        fun lap(): Duration = mark.elapsedNow().also { mark = timeSource.markNow() }
        val region: ByteRegion = when (source) {
            Source.Bundled -> bundled?.map(BundledPacks.EN_US)
            is Source.Downloaded -> packs?.map(source.fileName)
        } ?: return null
        val mapped = lap()
        return try {
            val dictionary = MappedDictionary(region)
            val opened = lap()
            dictionary.warmUp()
            Opened(dictionary, mapped, opened, lap())
        } catch (e: RuntimeException) {
            region.close()
            throw e
        }
    }
}
