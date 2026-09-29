package com.makeeb.core.model

/**
 * How sure autocorrect must be before it replaces a word. Modest leaves more slips for the user to
 * pick from the strip; Aggressive fixes more of them and, now and then, a word the user meant.
 */
enum class AutocorrectStrength { Modest, Normal, Aggressive }
