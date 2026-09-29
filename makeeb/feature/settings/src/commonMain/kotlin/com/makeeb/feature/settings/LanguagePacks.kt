package com.makeeb.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.layout.LanguageInfo
import com.makeeb.engine.packs.CatalogueState
import com.makeeb.engine.packs.InstallFailure
import com.makeeb.engine.packs.PackInstaller
import com.makeeb.engine.packs.PackStatus
import com.makeeb.engine.packs.PacksState
import com.makeeb.ui.components.MultiChoiceRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * The companion's handle on dictionary packs: the app's one [PackInstaller], shared with setup,
 * so a download started there shows its progress here. Without an installer (a build that can't
 * download) the state says so and nothing is offered.
 */
class LanguagePacksViewModel(private val installer: PackInstaller?) : ViewModel() {
    val state: StateFlow<PacksState> = installer?.state ?: MutableStateFlow(PacksState(catalogue = CatalogueState.NotConfigured))

    /** On every resume: a pack may have been installed from setup, or the connection come back. */
    fun refresh() {
        installer?.refresh()
    }

    fun install(languageTag: String) {
        installer?.install(languageTag)
    }

    fun cancel(languageTag: String) {
        installer?.cancel(languageTag)
    }

    fun remove(languageTag: String) {
        installer?.remove(languageTag)
    }
}

/**
 * The selected languages as the keyboard resolves them: each tag or its base language; tags
 * without data (a phone language MaKeeb doesn't have yet) are left out; none at all reads as
 * English.
 */
internal fun selectedLanguages(preferences: KeyboardPreferences, languages: List<LanguageInfo>): List<LanguageInfo> =
    preferences.languageTags
        .mapNotNull { tag -> languages.firstOrNull { it.tag == tag } ?: languages.firstOrNull { it.tag == tag.substringBefore('-') } }
        .distinct()
        .ifEmpty { listOfNotNull(languages.firstOrNull { it.tag == "en" }) }

/** The language chips: picking adds at the end, so the first stays the primary; one always stays. */
@Composable
internal fun LanguagesPicker(
    selected: List<LanguageInfo>,
    languages: List<LanguageInfo>,
    accents: (List<String>) -> Map<String, List<String>>,
    onUpdate: ((KeyboardPreferences) -> KeyboardPreferences) -> Unit,
) {
    val tags = selected.map { it.tag }
    MultiChoiceRow(
        title = "Languages",
        subtitle = "Long-press a letter for the accents of every language you pick, on any layout",
        options = languages,
        selected = selected,
        label = { it.autonym },
        onToggle = { language ->
            val next = if (language.tag in tags) tags - language.tag else tags + language.tag
            if (next.isNotEmpty()) onUpdate { it.copy(languageTags = next) }
        },
        footer = accents(tags).values.joinToString("  ·  ") { it.joinToString(" ") }.ifEmpty { null },
    )
}

/**
 * Each selected language's dictionary: built in, installed (with remove, and update when the
 * catalogue has a newer one), downloading with its progress, or available with its size.
 */
@Composable
fun LanguagePacksList(
    selected: List<LanguageInfo>,
    state: PacksState,
    onInstall: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Dictionaries", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Suggestions and autocorrect use each language's dictionary. English is built in; the others download once, " +
                    "from GitHub, and then work offline. Nothing you type is sent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        selected.forEach { language ->
            PackRow(language, state.statusOf(language.tag), onInstall, onCancel, onRemove)
        }
        if (state.catalogue == CatalogueState.Unreachable && selected.any { state.statusOf(it.tag) is PackStatus.Unknown }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Can't reach the dictionary downloads. Check your connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry, modifier = Modifier.testTag("packs-retry")) { Text("Retry") }
            }
        }
        Text(
            "Word lists from the Android Open Source Project (Apache 2.0). Next-word statistics, and the Hungarian word list, " +
                "from the Leipzig Corpora Collection, Leipzig University (CC BY 4.0).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackRow(
    language: LanguageInfo,
    status: PackStatus,
    onInstall: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("pack-${language.tag}")) {
        Column(Modifier.weight(1f)) {
            Text(language.autonym, style = MaterialTheme.typography.bodyLarge)
            Text(describe(status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status is PackStatus.Downloading) {
                LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        val tag = language.tag
        when (status) {
            is PackStatus.Available -> PackButton(if (status.failure == null) "Download" else "Try again", tag) { onInstall(tag) }
            is PackStatus.Downloading -> PackButton("Cancel", tag) { onCancel(tag) }
            is PackStatus.Installed -> {
                if (status.update != null) PackButton("Update", tag) { onInstall(tag) }
                PackButton("Remove", tag) { onRemove(tag) }
            }
            else -> Unit
        }
    }
}

@Composable
private fun PackButton(label: String, languageTag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag("pack-$languageTag-$label")) { Text(label) }
}

/** One line on where a language's dictionary stands. */
private fun describe(status: PackStatus): String = when (status) {
    PackStatus.BuiltIn -> "Built in"
    is PackStatus.Installed -> {
        val failed = status.failure
        val update = status.update
        when {
            failed != null -> "Installed · ${megabytes(status.pack.size)} · the last change failed: ${failure(failed)}"
            update != null -> "Update available · ${megabytes(update.size)}"
            else -> "Installed · ${megabytes(status.pack.size)}"
        }
    }
    is PackStatus.Downloading -> "Downloading · ${(status.fraction * 100).toInt()}% of ${megabytes(status.entry.size)}"
    is PackStatus.Available -> status.failure?.let { "Download failed: ${failure(it)}" } ?: "Not downloaded · ${megabytes(status.entry.size)}"
    PackStatus.NotOffered -> "No dictionary for this language yet: suggestions only, no autocorrect"
    is PackStatus.Unknown -> when (status.catalogue) {
        CatalogueState.Loading -> "Checking…"
        CatalogueState.Unreachable -> "Not downloaded: connect to the internet to get it"
        CatalogueState.Unreadable -> "Update MaKeeb to download it"
        CatalogueState.NotConfigured -> "Downloads aren't available in this build"
        is CatalogueState.Loaded -> "Not downloaded"
    }
}

private fun failure(failure: InstallFailure): String = when (failure) {
    InstallFailure.Network -> "check your connection and try again"
    InstallFailure.Corrupt -> "the download was damaged, try again"
    InstallFailure.Storage -> "not enough storage"
}

/** "8.2 MB", as the platforms count storage (decimal megabytes). */
internal fun megabytes(bytes: Long): String {
    val tenths = (bytes + 50_000) / 100_000
    return "${tenths / 10}.${tenths % 10} MB"
}

/**
 * Picking languages and getting their dictionaries in one place, for setup (the companion shows
 * it as an onboarding step). Settings has the same two parts as separate rows.
 */
@Composable
fun LanguageSetup(
    modifier: Modifier = Modifier,
    settings: SettingsViewModel = koinViewModel(),
    packs: LanguagePacksViewModel = koinViewModel(),
) {
    LifecycleResumeEffect(packs) {
        packs.refresh()
        onPauseOrDispose {}
    }
    val preferences by settings.preferences.collectAsState()
    val state by packs.state.collectAsState()
    val selected = selectedLanguages(preferences, settings.languages)
    Column(modifier) {
        LanguagesPicker(selected, settings.languages, settings::accents, settings::update)
        LanguagePacksList(selected, state, packs::install, packs::cancel, packs::remove, packs::refresh)
    }
}
