package com.makeeb.shared.keyboard

import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.core.settings.SnippetsRepository
import com.makeeb.core.settings.appGroupPreferencesRepository
import com.makeeb.core.settings.appGroupSnippetsRepository
import com.makeeb.platform.storage.BundleFiles
import com.makeeb.platform.storage.BundledFiles
import com.makeeb.platform.storage.ContainerFiles
import com.makeeb.platform.storage.PrivateFiles
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/** Koin for the keyboard extension process. The extension may be re-instantiated; start once. */
internal object IosKeyboardKoin {
    private val platformModule = module {
        single<PreferencesRepository> { appGroupPreferencesRepository() }
        single<SnippetsRepository> { appGroupSnippetsRepository() }
        // The extension's own bundle, readable without Full Access; packs are mapped, not loaded.
        single<BundledFiles> { BundleFiles() }
        // Learned words: the extension's own container, writable without Full Access (the App
        // Group isn't) and out of the companion app's reach.
        single<PrivateFiles> { ContainerFiles() }
    }

    fun ensureStarted() {
        if (KoinPlatform.getKoinOrNull() != null) return
        startKoin { modules(platformModule, keyboardRuntimeModule) }
    }
}
