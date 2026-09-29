package com.makeeb.shared.companion

import com.makeeb.engine.packs.PackInstaller
import com.makeeb.feature.onboarding.onboardingModule
import com.makeeb.feature.settings.settingsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Requires `PreferencesRepository` and `LayoutProvider` bindings from the platform root, and for
 * dictionary downloads an `HttpTransport` and `PackFiles` (without them nothing is offered).
 */
val companionModule = module {
    includes(settingsModule, onboardingModule)
    // One installer for setup and Settings, so a download started in one shows in the other. It
    // lives as long as the app: leaving a screen doesn't stop a download.
    single {
        PackInstaller(
            catalogueUrl = PackHosting.CATALOGUE_URL,
            transport = getOrNull(),
            files = getOrNull(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
}
