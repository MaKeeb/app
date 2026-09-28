package com.makeeb.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.ThemeMode
import com.makeeb.engine.layout.LayoutInfo
import com.makeeb.ui.components.ChoiceRow
import com.makeeb.ui.components.ScrollEndSpacer
import com.makeeb.ui.components.SettingsSection
import com.makeeb.ui.components.SliderRow
import com.makeeb.ui.components.SwitchRow
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = koinViewModel()) {
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
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection("Typing") {
            SwitchRow("Auto-capitalization", preferences.autoCapitalize, { v -> onUpdate { it.copy(autoCapitalize = v) } })
            SwitchRow("Double-space period", preferences.doubleSpacePeriod, { v -> onUpdate { it.copy(doubleSpacePeriod = v) } }, subtitle = "Tap space twice to end a sentence")
            SwitchRow("Autocorrect", preferences.autoCorrect, { v -> onUpdate { it.copy(autoCorrect = v) } }, subtitle = "Backspace right after a correction undoes it")
            SwitchRow("Show suggestions", preferences.showSuggestions, { v -> onUpdate { it.copy(showSuggestions = v) } })
        }
        SettingsSection("Layout") {
            ChoiceRow(
                title = "Letters",
                options = letterLayouts,
                selected = letterLayouts.firstOrNull { it.id == preferences.letterLayoutId } ?: letterLayouts.first(),
                label = { it.displayName },
                onSelect = { layout -> onUpdate { it.copy(letterLayoutId = layout.id) } },
            )
            SwitchRow("Number row", preferences.numberRow, { v -> onUpdate { it.copy(numberRow = v) } })
            SliderRow(
                title = "Keyboard height",
                value = preferences.heightScale,
                onValueChange = { v -> onUpdate { it.copy(heightScale = v) } },
                valueRange = KeyboardPreferences.MIN_HEIGHT_SCALE..KeyboardPreferences.MAX_HEIGHT_SCALE,
                valueLabel = "${(preferences.heightScale * 100).roundToInt()}%",
            )
        }
        SettingsSection("Feedback") {
            SwitchRow("Vibrate on keypress", preferences.keyPressHaptics, { v -> onUpdate { it.copy(keyPressHaptics = v) } })
            SwitchRow("Sound on keypress", preferences.keyPressSound, { v -> onUpdate { it.copy(keyPressSound = v) } })
            SwitchRow("Popup on keypress", preferences.keyPopupPreview, { v -> onUpdate { it.copy(keyPopupPreview = v) } })
        }
        SettingsSection("Appearance") {
            ChoiceRow(
                title = "Theme",
                options = ThemeMode.entries,
                selected = preferences.theme,
                label = { it.name },
                onSelect = { mode -> onUpdate { it.copy(theme = mode) } },
            )
        }
        ScrollEndSpacer()
    }
}
