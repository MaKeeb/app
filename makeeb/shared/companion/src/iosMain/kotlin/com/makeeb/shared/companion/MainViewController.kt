package com.makeeb.shared.companion

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.makeeb.core.model.KeyboardPalette
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.core.settings.appGroupPreferencesRepository
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.LayoutProvider
import com.makeeb.ui.theme.MaKeebAppTheme
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarItem
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.colorWithDynamicProvider
import platform.UIKit.tabBarItem

private val iosCompanionPlatformModule = module {
    single<PreferencesRepository> { appGroupPreferencesRepository() }
    single<LayoutProvider> { BuiltInLayoutProvider() }
}

/**
 * Entry point for the iOS container app (`MaKeebApp.swift`). The tabs live in a real
 * UITabBarController, so iOS draws its own tab bar: Liquid Glass on iOS 26, the standard bar on
 * earlier releases. Each tab is its own composition.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin { modules(iosCompanionPlatformModule, companionModule) }
    }
    return UITabBarController().apply {
        setViewControllers(CompanionTab.entries.map(::tabController), animated = false)
        // The selected tab in MaKeeb's accent (the app theme's primary), not system blue.
        tabBar.tintColor = UIColor.colorWithDynamicProvider { traits: UITraitCollection? ->
            val palette = if (traits?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) KeyboardPalette.Dark else KeyboardPalette.Light
            palette.accentKey.toUIColor()
        }
    }
}

private fun Long.toUIColor() = UIColor(
    red = ((this shr 16) and 0xFF) / 255.0,
    green = ((this shr 8) and 0xFF) / 255.0,
    blue = (this and 0xFF) / 255.0,
    alpha = ((this shr 24) and 0xFF) / 255.0,
)

private fun tabController(tab: CompanionTab): UIViewController =
    ComposeUIViewController {
        MaKeebAppTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // Content runs beneath the tab bar; screens clear it with ScrollEndSpacer.
                CompanionScreen(tab, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
            }
        }
    }.apply {
        tabBarItem = UITabBarItem(tab.title, UIImage.systemImageNamed(tab.systemImage), tab.ordinal.toLong())
    }
