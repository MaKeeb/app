package com.makeeb.core.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSUserDefaults

/**
 * The App Group shared by the companion app and the keyboard extension. It must match the
 * `com.apple.security.application-groups` entitlement of both targets in `app/ios/project.yml`.
 */
const val APP_GROUP_ID = "group.com.makeeb"

/**
 * Preferences in the App Group's user defaults. The companion app writes them; the extension
 * reads them (and writes only with Full Access), calling [PreferencesRepository.reload] when shown.
 */
fun appGroupPreferencesRepository(appGroupId: String = APP_GROUP_ID): PreferencesRepository {
    val defaults = NSUserDefaults(suiteName = appGroupId)
    return SettingsPreferencesRepository(NSUserDefaultsSettings(defaults))
}
