package com.makeeb.feature.settings

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Requires `PreferencesRepository`, `SnippetsRepository` and `LayoutProvider` bindings from the
 * app. Learned words take the keyboard's `LearnedWordsStore` where it runs in the app's process
 * (Android), or a `LearnedWordsResetRequest` where it doesn't (iOS). Dictionaries take the app's
 * `PackInstaller`; without one, Settings offers no downloads.
 */
val settingsModule = module {
    viewModelOf(::SettingsViewModel)
    viewModel { LearnedWordsViewModel(store = getOrNull(), resetRequest = getOrNull()) }
    viewModel { LanguagePacksViewModel(installer = getOrNull()) }
}
