package com.makeeb.core.settings

import platform.Foundation.NSUserDefaults

/**
 * "Clear learned words" from the companion app, carried to the keyboard extension through the App
 * Group. The app counts up [requested]; the extension, which can always read the App Group (even
 * without Full Access), compares it with the last request it applied each time it appears
 * (`LearnedWordsStore.applyClearRequest`), so every request is applied once.
 */
object AppGroupLearnedWordsReset : LearnedWordsResetRequest {
    private const val CLEAR_REQUESTS = "learned_words.clear_requests"

    private val defaults get() = NSUserDefaults(suiteName = APP_GROUP_ID)

    /** How many clears the app has asked for so far. */
    val requested: Long get() = defaults.integerForKey(CLEAR_REQUESTS)

    override fun request() {
        defaults.setInteger(requested + 1, forKey = CLEAR_REQUESTS)
    }
}
