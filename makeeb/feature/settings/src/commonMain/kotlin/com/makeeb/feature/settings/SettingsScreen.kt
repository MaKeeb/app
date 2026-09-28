package com.makeeb.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.ThemeMode
import com.makeeb.core.settings.matchesSettingsSearch
import com.makeeb.engine.layout.LayoutInfo
import com.makeeb.ui.components.ChoiceRow
import com.makeeb.ui.components.ScrollEndSpacer
import com.makeeb.ui.components.SettingsSection
import com.makeeb.ui.components.SliderRow
import com.makeeb.ui.components.SwitchRow
import com.makeeb.ui.theme.KeyboardIcons
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = koinViewModel()) {
    // Picks up what the keyboard's quick settings changed while the app was in the background.
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    val preferences by viewModel.preferences.collectAsState()
    SettingsContent(preferences, viewModel.letterLayouts, viewModel::update, modifier)
}

@Composable
fun SettingsContent(
    preferences: KeyboardPreferences,
    letterLayouts: List<LayoutInfo>,
    onUpdate: ((KeyboardPreferences) -> KeyboardPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = settingsSections(preferences, letterLayouts, onUpdate).mapNotNull { it.search(query) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

/** A group of rows on the settings screen. */
private class SettingsGroup(val title: String, val rows: List<SettingRow>) {
    /** The rows [query] finds, or null when it finds none; a section title match counts for its rows. */
    fun search(query: String): SettingsGroup? {
        val found = rows.filter { matchesSettingsSearch(query, title, it.title, it.subtitle, it.keywords) }
        return if (found.isEmpty()) null else SettingsGroup(title, found)
    }
}

/** One row; [keywords] are other words people search for it by. */
private class SettingRow(
    val title: String,
    val subtitle: String? = null,
    val keywords: String = "",
    val content: @Composable () -> Unit,
)

private fun settingsSections(
    preferences: KeyboardPreferences,
    letterLayouts: List<LayoutInfo>,
    onUpdate: ((KeyboardPreferences) -> KeyboardPreferences) -> Unit,
): List<SettingsGroup> {
    fun switch(title: String, checked: Boolean, keywords: String, subtitle: String? = null, set: (KeyboardPreferences, Boolean) -> KeyboardPreferences) =
        SettingRow(title, subtitle, keywords) {
            SwitchRow(title, checked, { v -> onUpdate { set(it, v) } }, subtitle = subtitle)
        }

    fun percent(value: Float) = "${(value * 100).roundToInt()}%"

    return listOf(
        SettingsGroup(
            "Typing",
            listOf(
                switch("Auto-capitalization", preferences.autoCapitalize, "capital letters shift sentence caps") { p, v -> p.copy(autoCapitalize = v) },
                switch("Double-space period", preferences.doubleSpacePeriod, "full stop dot space", "Tap space twice to end a sentence") { p, v -> p.copy(doubleSpacePeriod = v) },
                switch("Autocorrect", preferences.autoCorrect, "spelling correction typo fix", "Backspace right after a correction undoes it") { p, v -> p.copy(autoCorrect = v) },
                switch("Show suggestions", preferences.showSuggestions, "prediction words strip completion") { p, v -> p.copy(showSuggestions = v) },
                switch("Emoji suggestions", preferences.emojiSuggestions, "emoji strip", "Typing pizza offers 🍕") { p, v -> p.copy(emojiSuggestions = v) },
                switch("Slide on space by word", preferences.cursorSlideByWord, "cursor caret move spacebar", "Sliding on the space bar moves the cursor a word at a time") { p, v -> p.copy(cursorSlideByWord = v) },
                switch("Hold delete to erase words", preferences.holdDeleteWords, "backspace erase repeat", "Holding delete speeds up, then removes whole words") { p, v -> p.copy(holdDeleteWords = v) },
            ),
        ),
        SettingsGroup(
            "Layout",
            listOf(
                SettingRow("Letters", keywords = "layout language qwerty qwertz azerty dvorak colemak workman") {
                    ChoiceRow(
                        title = "Letters",
                        options = letterLayouts,
                        selected = letterLayouts.firstOrNull { it.id == preferences.letterLayoutId } ?: letterLayouts.first(),
                        label = { it.displayName },
                        onSelect = { layout -> onUpdate { it.copy(letterLayoutId = layout.id) } },
                    )
                },
                switch("Number row", preferences.numberRow, "digits numbers") { p, v -> p.copy(numberRow = v) },
                SettingRow("Keyboard height", keywords = "size tall short bigger smaller") {
                    SliderRow(
                        title = "Keyboard height",
                        value = preferences.heightScale,
                        onValueChange = { v -> onUpdate { it.copy(heightScale = v) } },
                        valueRange = KeyboardPreferences.MIN_HEIGHT_SCALE..KeyboardPreferences.MAX_HEIGHT_SCALE,
                        valueLabel = percent(preferences.heightScale),
                    )
                },
            ),
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
