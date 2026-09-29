package com.makeeb.core.settings

/**
 * Asks the keyboard to forget its learned words, where the companion app can't reach them itself:
 * on iOS they live in the keyboard extension's own container. Bound only there
 * (`AppGroupLearnedWordsReset`); on Android the app and the keyboard share a process, and the app
 * clears the words directly.
 */
fun interface LearnedWordsResetRequest {
    fun request()
}
