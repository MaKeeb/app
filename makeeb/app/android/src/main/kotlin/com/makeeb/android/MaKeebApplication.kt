package com.makeeb.android

import android.app.Application
import com.makeeb.shared.companion.companionModule
import com.makeeb.shared.keyboard.keyboardRuntimeModule
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.core.settings.androidPreferencesRepository
import com.makeeb.platform.storage.AssetBundledFiles
import com.makeeb.platform.storage.BundledFiles
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Android composition root. The IME service and the companion activity share this process, so
 * they share one Koin graph and one PreferencesRepository: settings apply to the keyboard live.
 */
class MaKeebApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MaKeebApplication)
            modules(androidPlatformModule, keyboardRuntimeModule, companionModule)
        }
    }
}

private val androidPlatformModule = module {
    single<PreferencesRepository> { androidPreferencesRepository(androidContext()) }
    // Dictionary packs are stored uncompressed in the APK and mapped in place (build.gradle.kts).
    single<BundledFiles> { AssetBundledFiles(androidContext().assets) }
}
