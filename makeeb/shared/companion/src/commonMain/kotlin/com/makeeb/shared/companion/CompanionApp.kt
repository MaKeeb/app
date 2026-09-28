package com.makeeb.shared.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.makeeb.feature.onboarding.OnboardingScreen
import com.makeeb.feature.settings.SettingsScreen
import com.makeeb.ui.components.ScrollEndSpacer
import com.makeeb.ui.theme.MaKeebAppTheme

/**
 * The companion's tabs. Android shows them in a Material 3 navigation bar ([CompanionApp]); iOS in
 * a native UITabBarController (`MainViewController`), which draws Liquid Glass on iOS 26.
 */
internal enum class CompanionTab(val title: String, val icon: String, val systemImage: String) {
    Setup("Setup", "①", "checklist"),
    Settings("Settings", "⚙", "gearshape"),
    Try("Try it", "⌨", "keyboard"),
}

/**
 * The Android companion: tabs in a Material 3 navigation bar. Each change of [settingsRequest]
 * (the keyboard's "All settings") switches to the Settings tab.
 */
@Composable
fun CompanionApp(settingsRequest: Int = 0) {
    MaKeebAppTheme {
        var tab by rememberSaveable { mutableStateOf(CompanionTab.Setup) }
        LaunchedEffect(settingsRequest) {
            if (settingsRequest > 0) tab = CompanionTab.Settings
        }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    CompanionTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Text(entry.icon) },
                            label = { Text(entry.title) },
                        )
                    }
                }
            },
        ) { padding ->
            // Consumed so the screens' own insets (imePadding, ScrollEndSpacer) don't count the bar again.
            CompanionScreen(tab, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

/**
 * One tab's content. The host pads for the top and side insets; screens handle the bottom
 * themselves (ScrollEndSpacer, imePadding), so on iOS content can scroll beneath the glass tab bar.
 */
@Composable
internal fun CompanionScreen(tab: CompanionTab, modifier: Modifier = Modifier) {
    when (tab) {
        CompanionTab.Setup -> OnboardingScreen(modifier)
        CompanionTab.Settings -> SettingsScreen(modifier)
        CompanionTab.Try -> TryItScreen(modifier)
    }
}

/** Known clipboard content for the clipboard-panel test, so no real clipboard data shows up. */
private const val SAMPLE_CLIP = "MaKeeb clipboard sample"

/** One labelled field per input type: the manual QA surface and the visual test plan's harness. */
private enum class QaField(val label: String, val options: KeyboardOptions, val singleLine: Boolean = true) {
    Text("Text", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)),
    Email("E-mail", KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false)),
    Url("URL", KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false)),
    Number("Number", KeyboardOptions(keyboardType = KeyboardType.Number)),
    Phone("Phone", KeyboardOptions(keyboardType = KeyboardType.Phone)),
    Password("Password", KeyboardOptions(keyboardType = KeyboardType.Password)),
    Search("Search", KeyboardOptions(imeAction = ImeAction.Search)),
    Send("Send", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send)),
    MultiLine("Multi-line", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = false),
}

@Composable
private fun TryItScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Try MaKeeb", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Type below. Try \"teh \", double-space, long-press a letter, or slide on the space bar. Each field asks for a different keyboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val clipboard = LocalClipboardManager.current
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(SAMPLE_CLIP)) }) {
            Text("Copy sample text")
        }
        QaField.entries.forEach { field ->
            var text by rememberSaveable(field) { mutableStateOf("") }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(field.label) },
                keyboardOptions = field.options,
                visualTransformation = if (field == QaField.Password) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = field.singleLine,
                minLines = if (field.singleLine) 1 else 3,
                // UI tests find fields by this tag (iOS: accessibilityIdentifier).
                modifier = Modifier.fillMaxWidth().testTag("qa-${field.label}"),
            )
        }
        ScrollEndSpacer()
    }
}
