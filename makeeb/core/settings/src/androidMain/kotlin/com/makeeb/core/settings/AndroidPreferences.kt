package com.makeeb.core.settings

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings

private const val PREFERENCES_FILE = "makeeb_preferences"

/**
 * Preferences live in device-protected storage so the keyboard can read them before the user
 * unlocks the device after a reboot (direct boot: the IME must work on the lock screen).
 * Nothing in [KeyboardPreferences] is sensitive; typed data never goes here.
 */
fun androidPreferencesRepository(context: Context): PreferencesRepository =
    SettingsPreferencesRepository(deviceProtectedSettings(context))

/** Snippets beside the preferences, readable on the lock screen too. */
fun androidSnippetsRepository(context: Context): SnippetsRepository = SettingsSnippetsRepository(deviceProtectedSettings(context))

private fun deviceProtectedSettings(context: Context): SharedPreferencesSettings {
    val storageContext = context.createDeviceProtectedStorageContext()
    return SharedPreferencesSettings(storageContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE))
}
