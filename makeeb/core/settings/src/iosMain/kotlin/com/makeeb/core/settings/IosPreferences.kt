package com.makeeb.core.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/**
 * The App Group shared by the companion app and the keyboard extension. It must match the
 * `com.apple.security.application-groups` entitlement of both targets in `app/ios/project.yml`.
 */
const val APP_GROUP_ID = "group.com.makeeb"

/**
 * Preferences in the App Group's user defaults. The companion app writes them; the extension
 * reads them (and writes only with Full Access), calling [PreferencesRepository.reload] when shown.
 */
fun appGroupPreferencesRepository(appGroupId: String = APP_GROUP_ID): PreferencesRepository =
    SettingsPreferencesRepository(
        NSUserDefaultsSettings(NSUserDefaults(suiteName = appGroupId)),
        KeyboardPreferences(languageTags = deviceLanguageTags()),
    )

/**
 * The phone's languages, most preferred first: what MaKeeb types until the user picks. The app
 * and the extension read the same list, so they agree on it before anything is stored.
 */
private fun deviceLanguageTags(): List<String> =
    NSLocale.preferredLanguages.mapNotNull { (it as? String)?.substringBefore('-') }.distinct().ifEmpty { listOf("en") }

/** Snippets in the same App Group; the extension only reads them. */
fun appGroupSnippetsRepository(appGroupId: String = APP_GROUP_ID): SnippetsRepository =
    SettingsSnippetsRepository(NSUserDefaultsSettings(NSUserDefaults(suiteName = appGroupId)))
