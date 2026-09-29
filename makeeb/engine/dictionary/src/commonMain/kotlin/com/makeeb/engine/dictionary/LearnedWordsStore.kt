package com.makeeb.engine.dictionary

import com.makeeb.platform.storage.PrivateFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The words the keyboard learned, kept on the device across restarts: a [UserDictionary] that
 * loads itself from the keyboard's [PrivateFiles] and saves itself back. Suggestions read it like
 * any dictionary.
 *
 * Nothing here is on the typing path. Loading reads and decodes the file in [io] while the keyboard
 * types with what it has; the saved words then slot in under anything learned meanwhile. Changes
 * are saved in batches: the first change starts a [saveDelay] timer and one write covers all
 * changes made before it fires. [flush] saves at once, when the keyboard hides. Writes run in
 * [io], one at a time, and replace the file atomically.
 *
 * The storage can be locked (Android before the first unlock, see [PrivateFiles]). Until a load
 * succeeds the file is never written, since that would replace saved words with the few learned
 * since boot. Meanwhile learning stays in memory, forgetting and clearing are remembered, and
 * every save retries the load; once it succeeds the two are merged and saved. If the process
 * dies first, what was learned while locked is dropped, never what was saved before.
 *
 * Main-thread only, like the dictionary: [scope] must run on the main thread.
 */
class LearnedWordsStore(
    private val dictionary: UserDictionary,
    /** Null keeps the words in memory only. */
    private val files: PrivateFiles?,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val saveDelay: Duration = SAVE_DELAY,
) : MutableDictionary by dictionary {
    private enum class State {
        /** Not read yet, or the storage was locked: [load] tries again. */
        Unloaded,
        Loading,
        Loaded,

        /** Memory only: no files, or a later MaKeeb wrote the file and it must be left alone. */
        Detached,
    }

    private var state = if (files == null) State.Detached else State.Unloaded
    private val fileName = "learned-words-${dictionary.languageTag}.mklw"

    /** Changes the file doesn't have yet. */
    private var dirty = false
    private var saveJob: Job? = null
    private var lastWrite: Job? = null

    // What happened before the saved words were loaded, applied to them when they are.
    private var clearedBeforeLoad = false
    private val forgottenBeforeLoad = HashSet<String>()
    private var clearRequestBeforeLoad = 0L

    /** The last clear request applied ([applyClearRequest]); saved with the words. */
    private var appliedClearRequest = 0L

    private val mutableChanges = MutableStateFlow(0)

    /** Bumped on every change to the words, for screens that list them. */
    val changes: StateFlow<Int> = mutableChanges.asStateFlow()

    /** The saved words are in (or there were none), so saves write the file. */
    val isLoaded: Boolean get() = state == State.Loaded

    /** Reads the saved words, unless they are in or on their way. Call it again after a failure (a locked device). */
    fun load() {
        val files = files ?: return
        if (state != State.Unloaded) return
        state = State.Loading
        scope.launch {
            val contents = withContext(io) { read(files) }
            when (contents) {
                null -> state = State.Unloaded
                LearnedWordsFile.Contents.Newer -> state = State.Detached
                LearnedWordsFile.Contents.Damaged -> install(null)
                is LearnedWordsFile.Contents.Words -> install(contents.snapshot)
            }
        }
    }

    override fun learn(word: String) {
        val before = dictionary.clock
        dictionary.learn(word)
        if (dictionary.clock != before) changed()
    }

    override fun forget(word: String) {
        if (state == State.Loaded || state == State.Detached) {
            if (!dictionary.isLearned(word)) return
        } else {
            forgottenBeforeLoad += word.lowercase()
        }
        dictionary.forget(word)
        changed()
    }

    /** Forgets every learned word. */
    fun clear() {
        dictionary.clear()
        if (state == State.Unloaded || state == State.Loading) {
            clearedBeforeLoad = true
            forgottenBeforeLoad.clear()
        }
        changed()
    }

    /**
     * Clears the words when [request] is newer than the last one applied. Requests are numbered
     * by whoever asks, and each is applied once: on iOS the companion app can't reach the
     * keyboard's files, so it counts up a number in the App Group and the keyboard applies it here
     * when it appears.
     */
    fun applyClearRequest(request: Long) {
        if (state == State.Loaded || state == State.Detached) {
            if (request <= appliedClearRequest) return
            appliedClearRequest = request
            clear()
        } else if (request > clearRequestBeforeLoad) {
            // Whether the saved words predate it is only known once they are read.
            clearRequestBeforeLoad = request
            dictionary.clear()
            changed()
        }
    }

    /** Every learned word, in no particular order. */
    fun words(): List<LearnedWord> = dictionary.words()

    /** Saves pending changes now (the keyboard is hiding), without waiting for the batch timer. */
    fun flush() {
        saveJob?.cancel()
        saveJob = null
        save()
    }

    private fun install(saved: LearnedWordsSnapshot?) {
        val clearRequest = saved?.clearRequest ?: 0L
        val dropSaved = clearedBeforeLoad || clearRequestBeforeLoad > clearRequest
        val kept = if (saved == null || dropSaved) emptyList() else saved.words.filter { it.word.lowercase() !in forgottenBeforeLoad }
        dictionary.restore(kept, saved?.clock ?: 0L)
        appliedClearRequest = maxOf(clearRequest, clearRequestBeforeLoad)
        clearedBeforeLoad = false
        forgottenBeforeLoad.clear()
        clearRequestBeforeLoad = 0L
        state = State.Loaded
        mutableChanges.value++
        if (dirty) save()
    }

    private fun changed() {
        dirty = true
        mutableChanges.value++
        if (state != State.Detached && saveJob?.isActive != true) {
            saveJob = scope.launch {
                delay(saveDelay)
                saveJob = null
                save()
            }
        }
    }

    private fun save() {
        val files = files ?: return
        when (state) {
            // Merged and saved once the load succeeds.
            State.Unloaded -> return load()
            State.Loading, State.Detached -> return
            State.Loaded -> if (!dirty) return
        }
        dirty = false
        val snapshot = LearnedWordsSnapshot(dictionary.words(), dictionary.clock, appliedClearRequest)
        val previous = lastWrite
        lastWrite = scope.launch {
            previous?.join()
            val written = withContext(io) { write(files, snapshot) }
            // Retried with the next change or flush; the words are still in memory.
            if (!written) dirty = true
        }
    }

    /** Null when the storage can't be read now (locked); a missing file reads as no words. */
    private fun read(files: PrivateFiles): LearnedWordsFile.Contents? = try {
        files.read(fileName)?.let(LearnedWordsFile::decode) ?: LearnedWordsFile.Contents.Words(LearnedWordsSnapshot(emptyList(), 0, 0))
    } catch (_: Exception) {
        null
    }

    private fun write(files: PrivateFiles, snapshot: LearnedWordsSnapshot): Boolean = try {
        files.write(fileName, LearnedWordsFile.encode(snapshot))
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        /** How long changes collect before one write saves them all. */
        val SAVE_DELAY: Duration = 5.seconds
    }
}
