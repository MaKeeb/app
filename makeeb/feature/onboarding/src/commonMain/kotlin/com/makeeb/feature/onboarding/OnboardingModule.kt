package com.makeeb.feature.onboarding

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Binds the platform's [KeyboardSetup]. */
internal expect val onboardingPlatformModule: Module

/**
 * The languages step needs the app's `PreferencesRepository` and `PackInstaller`; without them
 * setup has only the platform's steps.
 */
val onboardingModule = module {
    includes(onboardingPlatformModule)
    viewModel { OnboardingViewModel(setup = get(), preferences = getOrNull(), installer = getOrNull()) }
}
