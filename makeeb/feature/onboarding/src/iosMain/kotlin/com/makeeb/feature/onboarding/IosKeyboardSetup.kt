package com.makeeb.feature.onboarding

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

internal actual val onboardingPlatformModule: Module = module {
    single<KeyboardSetup> { IosKeyboardSetup() }
}

/**
 * iOS gives apps no public way to check whether their keyboard is enabled, so the steps are
 * instructions only. Runs in the companion app, never in the extension (which may not touch
 * `UIApplication`).
 */
class IosKeyboardSetup : KeyboardSetup {
    override fun status() = SetupStatus(enabled = null, selected = null)

    override fun steps(status: SetupStatus): List<SetupStep> = listOf(
        SetupStep(
            title = "Add MaKeeb",
            body = "Open Settings → MaKeeb → Keyboards and turn on MaKeeb.",
            actionLabel = "Open Settings",
            action = ::openAppSettings,
            done = null,
        ),
        SetupStep(
            title = "Allow Full Access (optional)",
            body = "Full Access enables clipboard history and haptic feedback. MaKeeb never sends what you type anywhere, and works fully without it.",
            actionLabel = null,
            action = null,
            done = null,
        ),
        SetupStep(
            title = "Switch to MaKeeb",
            body = "In any text field, tap and hold the globe key and choose MaKeeb.",
            actionLabel = null,
            action = null,
            done = null,
        ),
    )

    private fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
