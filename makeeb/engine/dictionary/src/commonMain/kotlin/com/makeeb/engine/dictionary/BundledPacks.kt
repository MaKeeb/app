package com.makeeb.engine.dictionary

/**
 * The dictionary pack inside the apps. Every other language is a downloaded pack
 * ([InstalledPack]); which languages have a full lexicon is English plus those.
 */
object BundledPacks {
    /** Built by :tools:dictionaries; an APK asset on Android, in the extension bundle on iOS. */
    const val EN_US = "en_US.mkd"

    /** The language subtag the bundled pack serves: English, whatever the region. */
    const val LANGUAGE = "en"
}
