package com.makeeb.feature.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val onboardingPlatformModule: Module = module {
    single<KeyboardSetup> { AndroidKeyboardSetup(androidContext()) }
}

/** Android can report both states: enabled in system settings, and selected as current. */
class AndroidKeyboardSetup(private val context: Context) : KeyboardSetup {
    private val inputMethodManager get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun status(): SetupStatus {
        val enabled = inputMethodManager.enabledInputMethodList.any { it.packageName == context.packageName }
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return SetupStatus(enabled = enabled, selected = current?.startsWith(context.packageName + "/") == true)
    }

    override fun steps(status: SetupStatus): List<SetupStep> = listOf(
        SetupStep(
            title = "Enable MaKeeb",
            body = "Turn on MaKeeb in the system's on-screen keyboard list. Android warns that keyboards can see what you type: MaKeeb works offline and never sends your typing anywhere.",
            actionLabel = "Open keyboard settings",
            action = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            done = status.enabled,
        ),
        SetupStep(
            title = "Switch to MaKeeb",
            body = "Pick MaKeeb as the keyboard you type with.",
            actionLabel = "Choose keyboard",
            action = { inputMethodManager.showInputMethodPicker() },
            done = status.selected,
        ),
    )
}
