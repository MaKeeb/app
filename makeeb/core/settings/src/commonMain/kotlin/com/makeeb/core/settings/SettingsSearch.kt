package com.makeeb.core.settings

/**
 * Whether a settings row matches what the user typed into the settings search. Every word of
 * [query] must appear somewhere in [fields] (title, subtitle, section, extra keywords), ignoring
 * case and order, so "dark theme" and "theme dark" find the same row. A blank query matches all.
 */
fun matchesSettingsSearch(query: String, vararg fields: String?): Boolean {
    val terms = query.lowercase().split(' ', '\t').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val haystack = fields.filterNotNull().joinToString(" ").lowercase()
    return terms.all { it in haystack }
}
