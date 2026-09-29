package com.makeeb.engine.dictionary

/**
 * The dictionary packs inside the apps. Other languages come as downloaded packs (board card
 * APP-37); until then these are the only languages with a full lexicon, which is what
 * autocorrect needs before it replaces a word.
 */
object BundledPacks {
    /** Built by :tools:dictionaries; an APK asset on Android, in the extension bundle on iOS. */
    const val EN_US = "en_US.mkd"

    /** Language subtags that have a full lexicon. */
    val languages: Set<String> = setOf("en")
}
