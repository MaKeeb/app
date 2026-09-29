package com.makeeb.engine.packs

import com.makeeb.core.common.Sha256
import com.makeeb.engine.dictionary.BundledPacks
import com.makeeb.engine.dictionary.InstalledPack
import com.makeeb.engine.dictionary.forLanguage
import com.makeeb.engine.dictionary.pack.MkdPack
import com.makeeb.platform.network.BodySink
import com.makeeb.platform.network.HttpTransport
import com.makeeb.platform.network.TransportException
import com.makeeb.platform.storage.PackFiles
import com.makeeb.platform.storage.PackFilesException
import com.makeeb.platform.storage.PendingPackFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Downloads, checks, installs and removes dictionary packs for the companion app (setup and
 * Settings share one instance). The keyboard never uses it: it maps whatever is installed.
 *
 * A pack is streamed into a pending file while its SHA-256 is computed, and becomes visible only
 * once its size, hash, MKD header, CRC and language all match the catalogue ([PackFiles] renames
 * it into place). A version replaced by an update is deleted afterwards, so the keyboard always
 * finds a complete pack.
 *
 * [state] changes on [scope] (the main thread in the apps); file and network work runs on [io].
 * Without a [catalogueUrl] or a [transport] nothing is offered, and installed packs still work.
 */
class PackInstaller(
    private val catalogueUrl: String,
    private val transport: HttpTransport?,
    private val files: PackFiles?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext = Dispatchers.Default,
) {
    private val mutableState = MutableStateFlow(PacksState())
    val state: StateFlow<PacksState> = mutableState.asStateFlow()

    /** Downloads in flight, by pack language. Touched only on [scope]'s thread. */
    private val jobs = HashMap<String, Job>()
    private var catalogueJob: Job? = null

    /** Held while pending files are created or swept, so a sweep never takes a download's file. */
    private val pendingLock = Mutex()

    /**
     * Rescans the installed packs, and fetches the catalogue unless it is loaded or loading. Call
     * whenever a screen that shows packs appears: the other process (iOS) or a failed network may
     * have changed things. Also sweeps away what a killed download left behind.
     */
    fun refresh() {
        scope.launch {
            rescan()
            pendingLock.withLock {
                if (jobs.isEmpty()) withContext(io) { files?.deletePending() }
            }
        }
        val catalogue = state.value.catalogue
        if (catalogue is CatalogueState.Loaded || catalogueJob?.isActive == true) return
        catalogueJob = scope.launch { fetchCatalogue() }
    }

    /** Downloads the pack for [languageTag] ("pt" takes the catalogue's first "pt-…" pack). */
    fun install(languageTag: String) {
        val entry = (state.value.catalogue as? CatalogueState.Loaded)?.catalogue?.forLanguage(languageTag) ?: return
        val language = entry.language
        if (jobs[language]?.isActive == true || files == null || transport == null) return
        mutableState.update { it.copy(downloads = it.downloads + (language to 0L), failures = it.failures - language) }
        jobs[language] = scope.launch {
            try {
                withContext(io) { download(entry, files, transport) }
            } catch (e: InstallException) {
                mutableState.update { it.copy(failures = it.failures + (language to e.failure)) }
            } finally {
                jobs.remove(language)
                mutableState.update { it.copy(downloads = it.downloads - language) }
                rescan()
            }
        }
    }

    /** Stops a download; nothing of it is kept. */
    fun cancel(languageTag: String) {
        val entry = (state.value.catalogue as? CatalogueState.Loaded)?.catalogue?.forLanguage(languageTag)
        jobs[entry?.language ?: languageTag]?.cancel()
    }

    /** Deletes the installed pack for [languageTag], every version of it. The keyboard falls back to English. */
    fun remove(languageTag: String) {
        val files = files ?: return
        val pack = state.value.installed.forLanguage(languageTag) ?: return
        jobs[pack.language]?.cancel()
        scope.launch {
            try {
                withContext(io) {
                    files.list().mapNotNull(InstalledPack::parse).filter { it.language == pack.language }.forEach { files.delete(it.fileName) }
                }
            } catch (_: PackFilesException) {
                mutableState.update { it.copy(failures = it.failures + (pack.language to InstallFailure.Storage)) }
            }
            rescan()
        }
    }

    private suspend fun rescan() {
        val installed = files?.let { withContext(io) { InstalledPack.list(it) } }.orEmpty()
        mutableState.update { it.copy(installed = installed) }
    }

    private suspend fun fetchCatalogue() {
        if (catalogueUrl.isEmpty() || transport == null) {
            mutableState.update { it.copy(catalogue = CatalogueState.NotConfigured) }
            return
        }
        mutableState.update { it.copy(catalogue = CatalogueState.Loading) }
        val catalogue = try {
            val body = BufferSink(MAX_CATALOGUE_BYTES)
            withContext(io) { transport.get(catalogueUrl, body) }
            CatalogueState.Loaded(PackCatalogue.parse(body.bytes().decodeToString()))
        } catch (_: TransportException) {
            CatalogueState.Unreachable
        } catch (_: CatalogueFormatException) {
            CatalogueState.Unreadable
        }
        mutableState.update { it.copy(catalogue = catalogue) }
    }

    /** Streams [entry] into a pending file, checks it, and commits it; then drops older versions. */
    private suspend fun download(entry: PackEntry, files: PackFiles, transport: HttpTransport) {
        val name = InstalledPack.fileName(entry.language, entry.sha256)
        val pending = try {
            pendingLock.withLock { files.create(name) }
        } catch (e: PackFilesException) {
            throw InstallException(InstallFailure.Storage, e)
        }
        try {
            val sha = Sha256()
            var received = 0L
            var reported = 0L
            transport.get(
                entry.downloadUrl(catalogueUrl),
                object : BodySink {
                    override fun start(contentLength: Long) {
                        if (contentLength >= 0 && contentLength != entry.size) throw InstallException(InstallFailure.Corrupt)
                    }

                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        if (received + length > entry.size) throw InstallException(InstallFailure.Corrupt)
                        sha.update(bytes, offset, length)
                        pending.write(bytes, offset, length)
                        received += length
                        // A state update per 1% is plenty for a progress bar.
                        if (received - reported >= entry.size / 100 || received == entry.size) {
                            reported = received
                            mutableState.update { it.copy(downloads = it.downloads + (entry.language to received)) }
                        }
                    }
                },
            )
            if (received != entry.size || sha.hexDigest() != entry.sha256) throw InstallException(InstallFailure.Corrupt)
            check(pending, entry)
            pending.commit()
        } catch (e: CancellationException) {
            pending.discard()
            throw e
        } catch (e: InstallException) {
            pending.discard()
            throw e
        } catch (e: TransportException) {
            pending.discard()
            throw InstallException(InstallFailure.Network, e)
        } catch (e: PackFilesException) {
            pending.discard()
            throw InstallException(InstallFailure.Storage, e)
        }
        try {
            files.list().mapNotNull(InstalledPack::parse)
                .filter { it.language == entry.language && it.fileName != name }
                .forEach { files.delete(it.fileName) }
        } catch (_: PackFilesException) {
            // The new version is in place; a stale old one is only disk space, gone at the next update or removal.
        }
    }

    /** The bytes are the catalogue's, but they must also be a pack for the language it claims. */
    private fun check(pending: PendingPackFile, entry: PackEntry) {
        val region = pending.map()
        try {
            val pack = MkdPack.open(region)
            if (!pack.checksumMatches() || !pack.languageTag.equals(entry.language, ignoreCase = true)) {
                throw InstallException(InstallFailure.Corrupt)
            }
        } catch (e: RuntimeException) {
            throw InstallException(InstallFailure.Corrupt, e)
        } finally {
            region.close()
        }
    }

    /** Collects a small body in memory, refusing more than [limit] bytes. */
    private class BufferSink(private val limit: Int) : BodySink {
        private var buffer = ByteArray(16 * 1024)
        private var size = 0

        override fun start(contentLength: Long) {
            if (contentLength > limit) throw TransportException("catalogue too large")
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (size + length > limit) throw TransportException("catalogue too large")
            if (size + length > buffer.size) buffer = buffer.copyOf(maxOf(size + length, buffer.size * 2))
            bytes.copyInto(buffer, size, offset, offset + length)
            size += length
        }

        fun bytes(): ByteArray = buffer.copyOf(size)
    }

    private class InstallException(val failure: InstallFailure, cause: Throwable? = null) : Exception(failure.name, cause)

    private companion object {
        const val MAX_CATALOGUE_BYTES = 1024 * 1024
    }
}

/** What the companion app knows about packs: the catalogue, what is installed, what is downloading. */
data class PacksState(
    val catalogue: CatalogueState = CatalogueState.Loading,
    val installed: List<InstalledPack> = emptyList(),
    /** Downloads in progress, by pack language: bytes received so far. */
    val downloads: Map<String, Long> = emptyMap(),
    /** The last failure per pack language, until it is tried again. */
    val failures: Map<String, InstallFailure> = emptyMap(),
) {
    /**
     * Language subtags that have a full lexicon: the built-in English and every installed pack's.
     * The keyboard maps the same packs, so this is what lifts autocorrect's pause (Settings'
     * autocorrect note reads it).
     */
    val lexiconLanguages: Set<String> get() = setOf(BundledPacks.LANGUAGE) + installed.map { it.baseLanguage }

    /** Every one of [languageTags] has its dictionary, built in or installed: setup's languages step is done. */
    fun hasDictionaries(languageTags: List<String>): Boolean =
        languageTags.all { statusOf(it).let { status -> status is PackStatus.BuiltIn || status is PackStatus.Installed } }

    /** Where the pack for the keyboard language [languageTag] stands. */
    fun statusOf(languageTag: String): PackStatus {
        if (languageTag.substringBefore('-').equals(BundledPacks.LANGUAGE, ignoreCase = true)) return PackStatus.BuiltIn
        val entry = (catalogue as? CatalogueState.Loaded)?.catalogue?.forLanguage(languageTag)
        entry?.let { downloads[it.language] }?.let { return PackStatus.Downloading(entry, it) }
        installed.forLanguage(languageTag)?.let { pack ->
            val newer = entry?.takeIf { it.language == pack.language && !pack.isVersion(it.sha256) }
            return PackStatus.Installed(pack, update = newer, failure = failures[pack.language])
        }
        return when {
            entry != null -> PackStatus.Available(entry, failures[entry.language])
            catalogue is CatalogueState.Loaded -> PackStatus.NotOffered
            else -> PackStatus.Unknown(catalogue)
        }
    }
}

sealed interface CatalogueState {
    data object Loading : CatalogueState

    data class Loaded(val catalogue: PackCatalogue) : CatalogueState

    /** No connection, or the server failed: installed packs still work, nothing new is offered. */
    data object Unreachable : CatalogueState

    /** Not a catalogue this app can read (a newer format, or not a catalogue at all). */
    data object Unreadable : CatalogueState

    /** This build has no catalogue URL (`makeeb.packs.catalogueUrl`). */
    data object NotConfigured : CatalogueState
}

sealed interface PackStatus {
    /** English ships in the app. */
    data object BuiltIn : PackStatus

    /** [update]: the catalogue has a different build of it. [failure]: the last update or removal failed. */
    data class Installed(val pack: InstalledPack, val update: PackEntry?, val failure: InstallFailure? = null) : PackStatus

    data class Downloading(val entry: PackEntry, val received: Long) : PackStatus {
        val fraction: Float get() = if (entry.size <= 0) 0f else (received.toFloat() / entry.size).coerceIn(0f, 1f)
    }

    /** Offered and not installed; [failure] is why the last try failed. */
    data class Available(val entry: PackEntry, val failure: InstallFailure?) : PackStatus

    /** The catalogue has no pack for this language. */
    data object NotOffered : PackStatus

    /** Not known until the catalogue loads (or because it can't). */
    data class Unknown(val catalogue: CatalogueState) : PackStatus
}

enum class InstallFailure {
    /** No connection, a timeout or a server error: try again later. */
    Network,

    /** The download isn't the pack the catalogue describes (size, hash, format or language). */
    Corrupt,

    /** The pack couldn't be written or deleted (no space, no App Group container). */
    Storage,
}
