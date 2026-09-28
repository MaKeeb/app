package com.makeeb.shared.keyboard

import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.ShiftState
import com.makeeb.core.model.Suggestion
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.input.KeyboardState
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.LayoutOptions
import com.makeeb.engine.touch.TouchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardRendererTest {
    private val layout = BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions())

    @Test
    fun rendersShiftedLabelsAndStripOrder() {
        val state = KeyboardState(
            layout = layout,
            shift = ShiftState.OneShot,
            suggestions = listOf("best", "second", "third").map { Suggestion(it, Suggestion.Kind.Completion) },
        )
        val render = KeyboardRenderer.render(state, TouchState(), LayoutGeometry(layout, 375f, 54f), KeyboardPreferences())

        assertEquals(listOf("second", "best", "third"), render.suggestions)
        assertEquals("Q", render.keys.first().label)
        val shift = render.keys.single { it.icon == KeyIcon.ShiftActive }
        assertTrue(shift.isActive)
    }

    @Test
    fun aPanelHidesTheKeysAndSuggestions() {
        val state = KeyboardState(
            layout = layout,
            panel = KeyboardPanel.Emoji,
            suggestions = listOf(Suggestion("best", Suggestion.Kind.Completion)),
        )
        val render = KeyboardRenderer.render(state, TouchState(), LayoutGeometry(layout, 375f, 54f), KeyboardPreferences())

        assertEquals(KeyboardPanel.Emoji, render.panel)
        assertTrue(render.keys.isEmpty())
        assertTrue(render.suggestions.isEmpty())
    }

    @Test
    fun stripOffersEmojiOnlyWithoutAnEmojiKey() {
        val withEmojiKey = KeyboardState(layout = layout)
        assertEquals(listOf(StripAction.Clipboard, StripAction.Incognito, StripAction.Settings), KeyboardRenderer.stripActions(withEmojiKey))

        val globeLayout = BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions(switchKey = true))
        assertEquals(
            listOf(StripAction.Emoji, StripAction.Clipboard, StripAction.Incognito, StripAction.Settings),
            KeyboardRenderer.stripActions(KeyboardState(layout = globeLayout)),
        )
    }

    @Test
    fun theBestSuggestionKeepsTheMiddleSlotAndPunctuationShowsInOrder() {
        fun render(vararg suggestions: Suggestion) =
            KeyboardRenderer.render(KeyboardState(layout = layout, suggestions = suggestions.toList()), TouchState(), null, KeyboardPreferences())

        val one = render(Suggestion("best", Suggestion.Kind.Completion))
        assertEquals(listOf("", "best", ""), one.suggestions)
        assertEquals(1, one.bestSuggestion)

        val two = render(Suggestion("Okay", Suggestion.Kind.Completion), Suggestion("Ok", Suggestion.Kind.Typed))
        assertEquals(listOf("Ok", "Okay", ""), two.suggestions)

        val punctuation = render(*listOf(",", ".", "?", "!").map { Suggestion(it, Suggestion.Kind.Punctuation) }.toTypedArray())
        assertEquals(listOf(",", ".", "?", "!"), punctuation.suggestions)
        assertEquals(-1, punctuation.bestSuggestion)
    }

    @Test
    fun anEmojiSearchReplacesTheStripAndEnterClosesIt() {
        val state = KeyboardState(
            layout = layout,
            emojiSearch = "piz",
            suggestions = listOf(Suggestion("pizza", Suggestion.Kind.Completion)),
        )
        val render = KeyboardRenderer.render(state, TouchState(), LayoutGeometry(layout, 375f, 54f), KeyboardPreferences(), emojiResults = listOf("🍕"))
        assertEquals("piz", render.emojiSearch)
        assertEquals(listOf("🍕"), render.emojiResults)
        assertTrue(render.suggestions.isEmpty(), "the query takes the strip")
        assertTrue(render.stripActions.isEmpty())
        assertTrue(render.keys.isNotEmpty(), "the letters stay for typing the query")
        assertEquals(KeyIcon.Done, render.keys.last().icon)
    }
}
