package com.makeeb.feature.settings

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Requires `PreferencesRepository`, `SnippetsRepository` and `LayoutProvider` bindings from the app. */
val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}
