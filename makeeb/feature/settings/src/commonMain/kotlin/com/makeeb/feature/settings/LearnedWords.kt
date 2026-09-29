package com.makeeb.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makeeb.core.settings.LearnedWordsResetRequest
import com.makeeb.engine.dictionary.LearnedWordsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The companion's handle on the words the keyboard learned. On Android the keyboard runs in the
 * app's process, so [store] is the keyboard's own: the words can be listed, searched and removed
 * one by one. On iOS they live in the keyboard extension's container, which the app can't read
 * (and mirroring them into the App Group would need Full Access and put a second copy of what the
 * user types in a shared container), so there is no store, only [resetRequest]: the app asks and
 * the keyboard clears them the next time it opens.
 */
class LearnedWordsViewModel(
    private val store: LearnedWordsStore?,
    private val resetRequest: LearnedWordsResetRequest?,
) : ViewModel() {
    enum class Access {
        /** List, search, remove and clear (Android). */
        Manage,

        /** Only ask the keyboard to clear them (iOS). */
        ClearOnRequest,
        None,
    }

    val access: Access = when {
        store != null -> Access.Manage
        resetRequest != null -> Access.ClearOnRequest
        else -> Access.None
    }

    /** Every learned word, alphabetically; follows the keyboard's changes live. Empty without a [store]. */
    val words: StateFlow<List<String>> = store?.changes
        ?.map { sortedWords(store) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), sortedWords(store))
        ?: MutableStateFlow(emptyList<String>()).asStateFlow()

    private val mutableClearRequested = MutableStateFlow(false)

    /** A clear was asked for ([Access.ClearOnRequest]); the keyboard applies it when it next opens. */
    val clearRequested: StateFlow<Boolean> = mutableClearRequested.asStateFlow()

    fun forget(word: String) {
        store?.forget(word)
    }

    fun clearAll() {
        when {
            store != null -> store.clear()
            resetRequest != null -> {
                resetRequest.request()
                mutableClearRequested.value = true
            }
        }
    }

    private fun sortedWords(store: LearnedWordsStore): List<String> = store.words().map { it.word }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The learned-words row of the settings screen: what it offers follows [LearnedWordsViewModel.access]. */
@Composable
fun LearnedWordsEditor(viewModel: LearnedWordsViewModel) {
    when (viewModel.access) {
        LearnedWordsViewModel.Access.Manage -> ManageLearnedWords(viewModel)
        LearnedWordsViewModel.Access.ClearOnRequest -> RequestClear(viewModel)
        LearnedWordsViewModel.Access.None -> Unit
    }
}

@Composable
private fun ManageLearnedWords(viewModel: LearnedWordsViewModel) {
    val words by viewModel.words.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val matching = remember(words, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) words else words.filter { it.contains(trimmed, ignoreCase = true) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(
            "Words you type that aren't in the dictionary, such as names and slang. They stay on this device. " +
                "You can also forget one by holding it in the suggestion strip.",
        )
        if (words.isEmpty()) {
            Note("None yet.")
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(if (words.size == 1) "Search 1 learned word" else "Search ${words.size} learned words") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("learned-words-search"),
        )
        matching.take(MAX_SHOWN).forEach { word ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.forget(word) }, modifier = Modifier.semantics { contentDescription = "Remove $word" }) {
                    Text("Remove")
                }
            }
        }
        when {
            matching.isEmpty() -> Note("No learned words match.")
            matching.size > MAX_SHOWN -> Note("${matching.size - MAX_SHOWN} more. Search to find them.")
        }
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("learned-words-clear")) { Text("Clear all learned words") }
    }
    if (confirmClear) {
        ClearDialog(
            text = "MaKeeb forgets all ${words.size} words it learned from your typing. This can't be undone.",
            onConfirm = {
                viewModel.clearAll()
                query = ""
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun RequestClear(viewModel: LearnedWordsViewModel) {
    val requested by viewModel.clearRequested.collectAsState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(
            "The keyboard learns words you type that aren't in its dictionary, such as names and slang. It keeps them " +
                "to itself: they never leave this device, and this app can't see them. To forget one, hold it in the " +
                "suggestion strip.",
        )
        if (requested) {
            Text(
                "Done. The keyboard forgets its learned words the next time it opens.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("learned-words-clear-requested"),
            )
        } else {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("learned-words-clear")) { Text("Clear learned words") }
        }
    }
    if (confirmClear) {
        ClearDialog(
            text = "The keyboard forgets every word it learned from your typing the next time it opens. This can't be undone.",
            onConfirm = {
                viewModel.clearAll()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ClearDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear learned words?") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("learned-words-confirm-clear")) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Enough to scan; the search finds the rest. */
private const val MAX_SHOWN = 50
