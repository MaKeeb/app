package com.makeeb.core.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetsRepositoryTest {
    @Test
    fun addsRemovesAndSurvivesAReload() {
        val store = MapSettings()
        val app = SettingsSnippetsRepository(store)
        app.add("  221B Baker Street  ")
        app.add("Kind regards,\nJo")
        app.add("221B Baker Street") // already there
        app.add("   ")
        assertEquals(listOf("221B Baker Street", "Kind regards,\nJo"), app.snippets.value)

        val keyboard = SettingsSnippetsRepository(store)
        assertEquals(app.snippets.value, keyboard.snippets.value, "the keyboard reads what the app wrote")

        app.remove(0)
        keyboard.reload()
        assertEquals(listOf("Kind regards,\nJo"), keyboard.snippets.value)
    }
}
