package com.makeeb.shared.companion

import com.makeeb.feature.onboarding.onboardingModule
import com.makeeb.feature.settings.settingsModule
import org.koin.dsl.module

/** Requires `PreferencesRepository` and `LayoutProvider` bindings from the platform root. */
val companionModule = module {
    includes(settingsModule, onboardingModule)
}
