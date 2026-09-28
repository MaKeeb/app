package com.makeeb.feature.onboarding

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Binds the platform's [KeyboardSetup]. */
internal expect val onboardingPlatformModule: Module

val onboardingModule = module {
    includes(onboardingPlatformModule)
    viewModelOf(::OnboardingViewModel)
}
