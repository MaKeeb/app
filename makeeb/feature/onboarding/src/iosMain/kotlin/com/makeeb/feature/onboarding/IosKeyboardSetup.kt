package com.makeeb.feature.onboarding

import org.koin.core.module.Module
import org.koin.dsl.module
import com.makeeb.core.settings.KeyboardSignals
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

internal actual val onboardingPlatformModule: Module = module {
    single<KeyboardSetup> { IosKeyboardSetup() }
}

/**
 * iOS gives apps no public "is my keyboard enabled" API. The enabled keyboards are in the
 * `AppleKeyboards` user default (undocumented, stable since iOS 8); when it's missing the step
 * stays unknown. Full Access and first use come from the extension through the App Group, which
 * it can only write with Full Access. Runs in the companion app, never in the extension (which
 * may not touch `UIApplication`).
 */
class IosKeyboardSetup : KeyboardSetup {
    override fun status(): SetupStatus {
        val keyboards = NSUserDefaults.standardUserDefaults.arrayForKey("AppleKeyboards")
        val enabled = keyboards?.any { it == KEYBOARD_BUNDLE_ID }
        return SetupStatus(enabled = enabled, selected = KeyboardSignals.shownWithFullAccess)
    }

    override fun steps(status: SetupStatus): List<SetupStep> = listOf(
        SetupStep(
            title = "Add MaKeeb",
            body = if (status.enabled == true) "MaKeeb is on." else "Open Settings → MaKeeb → Keyboards and turn on MaKeeb.",
            actionLabel = if (status.enabled == true) null else "Open Settings",
            action = if (status.enabled == true) null else ::openAppSettings,
            done = status.enabled,
        ),
        SetupStep(
            title = "Allow Full Access (optional)",
            body = "Full Access enables clipboard history and haptic feedback. MaKeeb never sends what you type anywhere, and works fully without it.",
            actionLabel = null,
            action = null,
            done = KeyboardSignals.shownWithFullAccess,
        ),
        SetupStep(
            title = "Switch to MaKeeb",
            body = "In any text field, tap and hold the globe key and choose MaKeeb.",
            actionLabel = null,
            action = null,
            done = status.selected,
        ),
    )

    private fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}

/** The keyboard extension's bundle identifier (app/ios/project.yml). */
private const val KEYBOARD_BUNDLE_ID = "com.makeeb.ios.keyboard"
