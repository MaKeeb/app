package com.makeeb.engine.input

import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.Suggestion
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.Prediction
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The engine's text mirror, driven the way Android drives it: edits, then late selection reports. */
class TextMirrorTest {
    /** Corrects any word starting "te" to "the", so autocorrection is predictable. */
    private val suggestions = object : SuggestionEngine {
        override fun suggest(context: TypingContext, limit: Int) = Prediction(
            suggestions = listOf(Suggestion(context.composing, Suggestion.Kind.Typed)),
            autoCorrection = "the".takeIf { context.composing.startsWith("te") && context.composing != "the" },
        )

        override fun learn(word: String) = Unit
    }
    private val engine = InputEngine(BuiltInLayoutProvider(), suggestions, MutableStateFlow(KeyboardPreferences(autoCapitalize = false)))

    private fun start(host: FakeTextHost) = host.also { engine.startInput(it, FakeKeyboardHost(), EditorAttributes(), it.selection) }

    /** Type, with the field's selection report after each key, as Android sends them. */
    private fun type(host: FakeTextHost, text: String) = text.forEach { char ->
        engine.onKey(if (char == ' ') KeyAction.Space else KeyAction.Text(char.toString()))
        report(host)
    }

    private fun report(host: FakeTextHost) = engine.onSelectionChanged(host.selection.start, host.selection.end)

    @Test
    fun typingReadsTheFieldOnceNotOnEveryKey() {
        val host = start(FakeTextHost("Dear Sam, "))
        val afterStart = host.reads
        type(host, "see you at noon ")
        assertEquals("Dear Sam, see you at noon |", host.toString())
        assertEquals(afterStart, host.reads, "no reads while typing words that aren't corrected")
        assertEquals(1, afterStart, "one fetch of context at the start")
    }

    @Test
    fun lateReportsOfTheKeyboardsOwnEditsAreNotExternalChanges() {
        val host = start(FakeTextHost())
        val positions = mutableListOf<Int>()
        "abc".forEach { engine.onKey(KeyAction.Text(it.toString())); positions += host.cursor }
        val before = host.reads
        positions.forEach { engine.onSelectionChanged(it, it) }
        assertEquals(before, host.reads)
        assertEquals("abc", engine.state.value.composing)
    }

    @Test
    fun aTapElsewhereIsNoticedAndReadOnce() {
        val host = start(FakeTextHost())
        type(host, "hello world")
        val before = host.reads
        host.placeCursor(5)
        report(host)
        assertEquals("hello", engine.state.value.composing)
        assertEquals(before + 1, host.reads)
    }

    @Test
    fun aFieldThatRefusesInputIsCheckedBeforeAnAutocorrection() {
        // The field drops 'x' without moving its caret, so no report reveals it: the mirror
        // believes "ok texh" while the field holds "ok teh".
        val host = start(FakeTextHost(inputFilter = { it.replace("x", "") }))
        type(host, "ok tex")
        type(host, "h")
        type(host, " ")
        assertEquals("ok the |", host.toString(), "the real word was corrected, nothing else deleted")
    }

    @Test
    fun backspaceRemovesAWholeEmojiWithoutReadingTheField() {
        val host = start(FakeTextHost("hi 👍🏽"))
        val before = host.reads
        engine.onKey(KeyAction.Backspace)
        report(host)
        assertEquals("hi |", host.toString())
        assertEquals(before, host.reads)
    }

    @Test
    fun anAppRewriteThatMovesTheCaretIsPickedUp() {
        val host = start(FakeTextHost())
        type(host, "message ")
        host.replaceAll("") // a chat app clears the field after sending
        report(host)
        assertEquals("", engine.state.value.composing)
        type(host, "next")
        assertEquals("next|", host.toString())
        assertEquals("next", engine.state.value.composing)
    }

    @Test
    fun cheapHostsAreReadDirectly() {
        val host = start(FakeTextHost(readsAreCheap = true))
        type(host, "ab")
        assertTrue(host.reads > 2, "no mirror where reads are in-process (iOS)")
    }
}
