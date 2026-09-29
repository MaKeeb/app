package com.makeeb.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.makeeb.core.model.AutocorrectStrength
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.ThemeMode
import com.makeeb.core.settings.matchesSettingsSearch
import com.makeeb.engine.layout.LanguageInfo
import com.makeeb.engine.layout.LayoutInfo
import com.makeeb.ui.components.ChoiceRow
import com.makeeb.ui.components.ScrollEndSpacer
import com.makeeb.ui.components.SettingsSection
import com.makeeb.ui.components.SliderRow
import com.makeeb.ui.components.SwitchRow
import com.makeeb.ui.components.dismissKeyboardOnDrag
import com.makeeb.ui.theme.KeyboardIcons
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    learnedWords: LearnedWordsViewModel = koinViewModel(),
    packs: LanguagePacksViewModel = koinViewModel(),
) {
    // Picks up what the keyboard's quick settings changed while the app was in the background,
    // and packs installed from setup (or a connection that came back).
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        packs.refresh()
        onPauseOrDispose {}
    }
    val preferences by viewModel.preferences.collectAsState()
    val snippets by viewModel.snippets.collectAsState()
    val packState by packs.state.collectAsState()
    SettingsContent(
        preferences,
        viewModel.letterLayouts,
        viewModel.languages,
        viewModel::update,
        modifier,
        Snippets(snippets, viewModel::addSnippet, viewModel::removeSnippet),
        viewModel::accents,
        packState.lexiconLanguages,
        learnedWords = if (learnedWords.access == LearnedWordsViewModel.Access.None) null else { { LearnedWordsEditor(learnedWords) } },
        dictionaries = { selected -> LanguagePacksList(selected, packState, packs::install, packs::cancel, packs::remove, packs::refresh) },
    )
}

@Composable
fun SettingsContent(
    preferences: KeyboardPreferences,
    letterLayouts: List<LayoutInfo>,
    languages: List<LanguageInfo>,
    onUpdate: ((KeyboardPreferences) -> KeyboardPreferences) -> Unit,
    modifier: Modifier = Modifier,
    snippets: Snippets? = null,
    /** What the long-press keys offer for a set of languages ([com.makeeb.engine.layout.LayoutProvider.accents]). */
    accents: (List<String>) -> Map<String, List<String>> = { emptyMap() },
    /** Language subtags with a full lexicon (built in or installed); null when unknown (no autocorrect note). */
    dictionaryLanguages: Set<String>? = null,
    /** The learned-words editor ([LearnedWordsEditor]); none where the app can't reach them. */
    learnedWords: (@Composable () -> Unit)? = null,
    /** The selected languages' dictionaries ([LanguagePacksList]), under the languages; none without packs. */
    dictionaries: (@Composable (List<LanguageInfo>) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val extras = listOfNotNull(snippets?.let(::snippetsSection), learnedWords?.let { learnedWordsSection(it) })
    val sections = (settingsSections(preferences, letterLayouts, languages, accents, dictionaryLanguages, dictionaries, onUpdate) + extras)
        .mapNotNull { it.search(query) }
    Column(
        modifier.fillMaxSize().dismissKeyboardOnDrag().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(KeyboardIcons.Search, contentDescription = null) },
                trailingIcon = if (query.isEmpty()) null else {
                    { TextButton(onClick = { query = "" }) { Text("Clear") } }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().testTag("settings-search"),
            )
        }
        if (sections.isEmpty()) {
            Text(
                "No settings match \"${query.trim()}\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        sections.forEach { section ->
            SettingsSection(section.title) {
                section.rows.forEach { it.content() }
            }
        }
        ScrollEndSpacer()
    }
}

/** The user's reusable texts and how to change them. */
class Snippets(val texts: List<String>, val onAdd: (String) -> Unit, val onRemove: (Int) -> Unit)

private fun snippetsSection(snippets: Snippets) = SettingsGroup(
    "Snippets",
    listOf(SettingRow("Snippets", "Texts you use often, one tap away in the keyboard's clipboard panel", "quick text canned address signature clipboard") { SnippetsEditor(snippets) }),
)

private fun learnedWordsSection(editor: @Composable () -> Unit) = SettingsGroup(
    "Learned words",
    listOf(SettingRow("Learned words", "Words MaKeeb learned from your typing", "personal dictionary vocabulary forget remove delete clear reset history privacy names slang", editor)),
)

@Composable
private fun SnippetsEditor(snippets: Snippets) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Texts you use often, one tap away in the keyboard's clipboard panel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        snippets.texts.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { snippets.onRemove(index) }) { Text("Remove") }
            }
        }
        var draft by rememberSaveable { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("New snippet") },
                maxLines = 4,
                modifier = Modifier.weight(1f).testTag("snippet-draft"),
            )
            TextButton(onClick = { snippets.onAdd(draft); draft = "" }, enabled = draft.isNotBlank()) { Text("Add") }
        }
    }
}

/**
 * What autocorrect is doing: the undo hint, or, while a selected language has no full lexicon,
 * why it only suggests (without one, that language's words look like typos).
 */
private fun autocorrectSubtitle(selected: List<LanguageInfo>, dictionaryLanguages: Set<String>?): String {
    val missing = dictionaryLanguages?.let { have -> selected.filter { it.tag.substringBefore('-') !in have } }.orEmpty()
    if (missing.isEmpty()) return "Backspace right after a correction undoes it"
    val names = missing.joinToString(" and ") { it.autonym }
    return "Suggests corrections without making them while $names ${if (missing.size == 1) "has" else "have"} no dictionary " +
        "(Layout → Dictionaries)"
}

/** A group of rows on the settings screen. */
private class SettingsGroup(val title: String, val rows: List<SettingRow>) {
    /** The rows [query] finds, or null when it finds none; a section title match counts for its rows. */
    fun search(query: String): SettingsGroup? {
        val found = rows.filter { matchesSettingsSearch(query, title, it.title, it.subtitle, it.keywords) }
        return if (found.isEmpty()) null else SettingsGroup(title, found)
    }
}

/** One row; [keywords] are other words people search for it by. */
internal class SettingRow(
    val title: String,
    val subtitle: String? = null,
    val keywords: String = "",
    val content: @Composable () -> Unit,
)

private fun settingsSections(
    preferences: KeyboardPreferences,
    letterLayouts: List<LayoutInfo>,
    languages: List<LanguageInfo>,
    accents: (List<String>) -> Map<String, List<String>>,
    dictionaryLanguages: Set<String>?,
    dictionaries: (@Composable (List<LanguageInfo>) -> Unit)?,
    onUpdate: ((KeyboardPreferences) -> KeyboardPreferences) -> Unit,
): List<SettingsGroup> {
    val selectedLanguages = selectedLanguages(preferences, languages)

    fun switch(title: String, checked: Boolean, keywords: String, subtitle: String? = null, set: (KeyboardPreferences, Boolean) -> KeyboardPreferences) =
        SettingRow(title, subtitle, keywords) {
            SwitchRow(title, checked, { v -> onUpdate { set(it, v) } }, subtitle = subtitle)
        }

    fun percent(value: Float) = "${(value * 100).roundToInt()}%"

    return listOf(
        SettingsGroup(
            "Typing",
            listOfNotNull(
                switch("Auto-capitalization", preferences.autoCapitalize, "capital letters shift sentence caps") { p, v -> p.copy(autoCapitalize = v) },
                switch("Double-space period", preferences.doubleSpacePeriod, "full stop dot space", "Tap space twice to end a sentence") { p, v -> p.copy(doubleSpacePeriod = v) },
                switch("Autocorrect", preferences.autoCorrect, "spelling correction typo fix", autocorrectSubtitle(selectedLanguages, dictionaryLanguages)) { p, v -> p.copy(autoCorrect = v) },
                SettingRow("Autocorrect strength", keywords = "autocorrect aggressive modest careful spelling") {
                    ChoiceRow(
                        title = "Autocorrect strength",
                        options = AutocorrectStrength.entries,
                        selected = preferences.autoCorrectStrength,
                        label = { it.name },
                        onSelect = { strength -> onUpdate { it.copy(autoCorrectStrength = strength) } },
                    )
                }.takeIf { preferences.autoCorrect },
                switch("Show suggestions", preferences.showSuggestions, "prediction words strip completion") { p, v -> p.copy(showSuggestions = v) },
                switch("Emoji suggestions", preferences.emojiSuggestions, "emoji strip", "Typing pizza offers 🍕") { p, v -> p.copy(emojiSuggestions = v) },
                switch("Slide on space by word", preferences.cursorSlideByWord, "cursor caret move spacebar", "Sliding on the space bar moves the cursor a word at a time") { p, v -> p.copy(cursorSlideByWord = v) },
                switch("Hold delete to erase words", preferences.holdDeleteWords, "backspace erase repeat", "Holding delete speeds up, then removes whole words") { p, v -> p.copy(holdDeleteWords = v) },
            ),
        ),
        SettingsGroup(
            "Layout",
            listOfNotNull(
                SettingRow("Letters", keywords = "layout language qwerty qwertz azerty dvorak colemak workman") {
                    ChoiceRow(
                        title = "Letters",
                        options = letterLayouts,
                        selected = letterLayouts.firstOrNull { it.id == preferences.letterLayoutId } ?: letterLayouts.first(),
                        label = { it.displayName },
                        onSelect = { layout -> onUpdate { it.copy(letterLayoutId = layout.id) } },
                    )
                },
                // The selected languages decide the character set on any layout; the layout only
                // places the letters.
                SettingRow(
                    "Languages",
                    keywords = "language accents diacritics long press alternates umlaut characters " +
                        languages.joinToString(" ") { "${it.name} ${it.autonym}" },
                ) {
                    LanguagesPicker(selectedLanguages, languages, accents, onUpdate)
                },
                // Right under the languages, so picking one offers its dictionary there.
                dictionaries?.let { content ->
                    SettingRow(
                        "Dictionaries",
                        "Download a dictionary for each language",
                        "dictionary dictionaries download language pack offline words autocorrect suggestions update remove",
                    ) { content(selectedLanguages) }
                },
                switch("Number row", preferences.numberRow, "digits numbers") { p, v -> p.copy(numberRow = v) },
            ) + keyboardSizeRows(preferences, onUpdate),
        ),
        SettingsGroup(
            "Feedback",
            buildList {
                add(switch("Vibrate on keypress", preferences.keyPressHaptics, "haptic vibration buzz") { p, v -> p.copy(keyPressHaptics = v) })
                if (preferences.keyPressHaptics) {
                    add(
                        SettingRow("Vibration strength", keywords = "haptic intensity") {
                            SliderRow(
                                title = "Vibration strength",
                                value = preferences.hapticIntensity,
                                onValueChange = { v -> onUpdate { it.copy(hapticIntensity = v) } },
                                valueRange = 0.1f..1f,
                                valueLabel = percent(preferences.hapticIntensity),
                            )
                        },
                    )
                }
                add(switch("Sound on keypress", preferences.keyPressSound, "click audio", "Quiet in silent mode and Do Not Disturb") { p, v -> p.copy(keyPressSound = v) })
                if (preferences.keyPressSound) {
                    add(
                        SettingRow("Sound volume", keywords = "click loud quiet audio") {
                            SliderRow(
                                title = "Sound volume",
                                value = preferences.soundVolume,
                                onValueChange = { v -> onUpdate { it.copy(soundVolume = v) } },
                                valueRange = 0.1f..1f,
                                valueLabel = percent(preferences.soundVolume),
                            )
                        },
                    )
                }
                add(switch("Popup on keypress", preferences.keyPopupPreview, "preview bubble magnify") { p, v -> p.copy(keyPopupPreview = v) })
            },
        ),
        SettingsGroup(
            "Appearance",
            buildList {
                add(
                    SettingRow("Theme", keywords = "dark light night mode colour color scheduled") {
                        ChoiceRow(
                            title = "Theme",
                            options = ThemeMode.entries,
                            selected = preferences.theme,
                            label = { it.name },
                            onSelect = { mode -> onUpdate { it.copy(theme = mode) } },
                        )
                    },
                )
                if (preferences.theme == ThemeMode.Scheduled) {
                    add(SettingRow("Dark from", keywords = "night schedule time hour") { HourRow("Dark from", preferences.darkFromMinute) { m -> onUpdate { it.copy(darkFromMinute = m) } } })
                    add(SettingRow("Dark until", keywords = "night schedule time hour") { HourRow("Dark until", preferences.darkUntilMinute) { m -> onUpdate { it.copy(darkUntilMinute = m) } } })
                }
            },
        ),
    )
}

/** A whole hour of the day, stored as minutes. */
@Composable
private fun HourRow(title: String, minute: Int, onChange: (Int) -> Unit) {
    SliderRow(
        title = title,
        value = (minute / 60).toFloat(),
        onValueChange = { hour -> onChange(hour.roundToInt() * 60) },
        valueRange = 0f..23f,
        valueLabel = "${(minute / 60).toString().padStart(2, '0')}:00",
        steps = 22,
    )
}
