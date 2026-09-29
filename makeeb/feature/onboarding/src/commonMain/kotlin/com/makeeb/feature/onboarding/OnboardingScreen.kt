package com.makeeb.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.makeeb.ui.components.ScrollEndSpacer
import org.koin.compose.viewmodel.koinViewModel

/**
 * Setup. [languages] is the languages step's content, picking languages and downloading their
 * dictionaries (the companion passes Settings' `LanguageSetup`); without it there is no such step.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
    languages: (@Composable () -> Unit)? = null,
) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val steps by viewModel.steps.collectAsState()
    val ready by viewModel.languagesReady.collectAsState()
    val skipped by viewModel.languagesSkipped.collectAsState()
    val languagesStep = languages?.takeIf { viewModel.hasLanguagesStep }?.let { content ->
        LanguagesStep(ready, skipped, viewModel::skipLanguages, viewModel::reopenLanguages, content)
    }
    OnboardingContent(steps, modifier, languagesStep)
}

/** The languages step: [ready] once every selected language has a dictionary; skippable. */
class LanguagesStep(
    val ready: Boolean,
    val skipped: Boolean,
    val onSkip: () -> Unit,
    val onReopen: () -> Unit,
    val content: @Composable () -> Unit,
)

@Composable
fun OnboardingContent(steps: List<SetupStep>, modifier: Modifier = Modifier, languages: LanguagesStep? = null) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Set up MaKeeb", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A few steps and MaKeeb is ready in every app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { index, step -> StepCard(index + 1, step) }
        if (languages != null) LanguagesCard(steps.size + 1, languages)
        ScrollEndSpacer()
    }
}

@Composable
private fun StepCard(number: Int, step: SetupStep) {
    StepFrame(number, step.title, step.body, step.done == true) {
        if (step.actionLabel != null && step.action != null && step.done != true) {
            FilledTonalButton(onClick = step.action) { Text(step.actionLabel) }
        }
    }
}

/**
 * Languages come last, like Gboard asks for them once the keyboard is on. The step is done when
 * every selected language has its dictionary; skipping folds it away, and Settings has the same
 * choices any time.
 */
@Composable
private fun LanguagesCard(number: Int, step: LanguagesStep) {
    val body = when {
        step.skipped -> "Skipped. Pick languages and download their dictionaries any time in Settings."
        else -> "Pick the languages you type. Each needs its dictionary for suggestions and autocorrect: English is built in, " +
            "the others download once and then work offline."
    }
    StepFrame(number, "Choose your languages", body, done = step.ready) {
        if (step.skipped) {
            TextButton(onClick = step.onReopen, modifier = Modifier.testTag("languages-reopen")) { Text("Choose languages") }
        } else {
            step.content()
            if (!step.ready) {
                TextButton(onClick = step.onSkip, modifier = Modifier.testTag("languages-skip")) { Text("Skip for now") }
            }
        }
    }
}

@Composable
private fun StepFrame(number: Int, title: String, body: String, done: Boolean, actions: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓" else "$number",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}
