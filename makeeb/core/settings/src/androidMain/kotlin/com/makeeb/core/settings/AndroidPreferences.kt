package com.makeeb.core.settings

import android.content.Context
import android.os.LocaleList
import com.russhwolf.settings.SharedPreferencesSettings

private const val PREFERENCES_FILE = "makeeb_preferences"

/**
 * Preferences live in device-protected storage so the keyboard can read them before the user
 * unlocks the device after a reboot (direct boot: the IME must work on the lock screen).
 * Nothing in [KeyboardPreferences] is sensitive; typed data never goes here.
 */
fun androidPreferencesRepository(context: Context): PreferencesRepository =
    SettingsPreferencesRepository(deviceProtectedSettings(context), KeyboardPreferences(languageTags = deviceLanguageTags()))

/** The phone's languages, most preferred first: what MaKeeb types until the user picks. */
private fun deviceLanguageTags(): List<String> {
    val locales = LocaleList.getDefault()
    // toLanguageTag, not language: it gives current codes ("he", not the legacy "iw").
    return List(locales.size()) { locales[it].toLanguageTag().substringBefore('-') }.distinct().ifEmpty { listOf("en") }
}

/** Snippets beside the preferences, readable on the lock screen too. */
fun androidSnippetsRepository(context: Context): SnippetsRepository = SettingsSnippetsRepository(deviceProtectedSettings(context))

private fun deviceProtectedSettings(context: Context): SharedPreferencesSettings {
    val storageContext = context.createDeviceProtectedStorageContext()
    return SharedPreferencesSettings(storageContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE))
}
