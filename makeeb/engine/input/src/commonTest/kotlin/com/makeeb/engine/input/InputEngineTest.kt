package com.makeeb.engine.input

import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.FieldType
import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.ShiftState
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class InputEngineTest {
    private val time = TestTimeSource()
    private val preferences = MutableStateFlow(KeyboardPreferences())
    private val userDictionary = UserDictionary("en")
    private val engine = InputEngine(
        layouts = BuiltInLayoutProvider(),
        suggestionEngine = DictionarySuggestionEngine(StarterDictionaries.english(), userDictionary),
        preferences = preferences,
        timeSource = time,
    )
    private val keyboardHost = FakeKeyboardHost()

    private fun start(
        host: FakeTextHost = FakeTextHost(),
        attributes: EditorAttributes = EditorAttributes(),
    ): FakeTextHost = host.also { engine.startInput(it, keyboardHost, attributes) }

    private fun type(text: String) = text.forEach { char ->
        engine.onKey(if (char == ' ') KeyAction.Space else KeyAction.Text(char.toString()))
    }

    @Test
    fun fieldTypePicksTheLettersVariantAndSurvivesModeSwitches() {
        fun bottomTexts() = engine.state.value.layout.rows.last().keys.mapNotNull { (it.action as? KeyAction.Text)?.text }
        start(attributes = EditorAttributes(fieldType = FieldType.Email))
        assertEquals(listOf("@", "."), bottomTexts())
        engine.onKey(KeyAction.SwitchMode(KeyboardMode.Symbols))
        engine.onKey(KeyAction.SwitchMode(KeyboardMode.Letters))
        assertEquals(listOf("@", "."), bottomTexts())
        start(attributes = EditorAttributes(fieldType = FieldType.Uri))
        assertEquals(listOf("/", ".", ".com"), bottomTexts())
        start(attributes = EditorAttributes())
        assertEquals(listOf(",", "."), bottomTexts())
    }

    @Test
    fun capitalisesTheFirstLetterOfASentence() {
        val host = start()
        assertEquals(ShiftState.OneShot, engine.state.value.shift)
        type("hi")
        assertEquals("Hi", host.text)
        assertEquals(ShiftState.Off, engine.state.value.shift)
    }

    @Test
    fun doubleSpaceInsertsAPeriodAndCapitalisesNext() {
        val host = start()
        type("hi  ")
        assertEquals("Hi. ", host.text)
        assertEquals(ShiftState.OneShot, engine.state.value.shift)
    }

    @Test
    fun slowDoubleSpaceStaysTwoSpaces() {
        val host = start()
        type("hi ")
        time += 2.seconds
        type(" ")
        assertEquals("Hi  ", host.text)
    }

    @Test
    fun autocorrectsOnSpaceAndBackspaceReverts() {
        val host = start()
        type("teh ")
        assertEquals("The ", host.text)

        engine.onKey(KeyAction.Backspace)
        assertEquals("Teh", host.text)

        type(" ")
        assertEquals("Teh ", host.text, "a reverted correction must not be applied again")
    }

    @Test
    fun autocorrectRespectsPreferenceAndField() {
        preferences.value = KeyboardPreferences(autoCorrect = false)
        assertEquals("Teh ", start().also { type("teh ") }.text)

        preferences.value = KeyboardPreferences()
        val email = start(attributes = EditorAttributes(fieldType = FieldType.Email, capitalization = Capitalization.None, autoCorrect = false))
        type("teh ")
        assertEquals("teh ", email.text)
    }

    @Test
    fun suggestionsTrackTheComposingWord() {
        start()
        type("hel")
        val words = engine.state.value.suggestions.map { it.text }
        assertTrue("Hello" in words, "got $words")

        engine.onSuggestionSelected(engine.state.value.suggestions.first { it.text == "Hello" })
        assertEquals("", engine.state.value.composing)
    }

    @Test
    fun selectingASuggestionReplacesTheWord() {
        val host = start()
        type("hel")
        engine.onSuggestionSelected(engine.state.value.suggestions.first { it.text == "Hello" })
        assertEquals("Hello ", host.text)
    }

    @Test
    fun punctuationShortcutsFollowAWordAndTakeTheSpace() {
        val host = start()
        type("hi ")
        val shortcuts = engine.state.value.suggestions
        assertEquals(listOf(",", ".", "?", "!"), shortcuts.map { it.text })
        engine.onSuggestionSelected(shortcuts.first { it.text == "." })
        assertEquals("Hi. ", host.text)
        assertEquals(ShiftState.OneShot, engine.state.value.shift)
        assertTrue(engine.state.value.suggestions.isEmpty(), "no shortcuts right after punctuation")
    }

    @Test
    fun punctuationTypedAfterAWordAndSpaceTakesTheSpacesPlace() {
        val host = start()
        type("hi ,")
        assertEquals("Hi, ", host.text)
        type("teh .")
        assertEquals("Hi, the. ", host.text, "also right after an autocorrection")
        assertEquals(ShiftState.OneShot, engine.state.value.shift)
    }

    @Test
    fun punctuationIsNotSwappedAwayFromOtherSpaces() {
        val url = start(attributes = EditorAttributes(fieldType = FieldType.Uri, capitalization = Capitalization.None))
        type("a .")
        assertEquals("a .", url.text, "not outside running text")

        val text = start()
        type("hi ")
        engine.onKey(KeyAction.MoveCursor(0)) // any other key in between: the space was deliberate
        type(",")
        assertEquals("Hi ,", text.text)
    }

    @Test
    fun noPunctuationShortcutsOutsideRunningText() {
        start(attributes = EditorAttributes(fieldType = FieldType.Email))
        type("me ")
        assertTrue(engine.state.value.suggestions.isEmpty())
        start(attributes = EditorAttributes(imeAction = ImeAction.Search))
        type("cats ")
        assertTrue(engine.state.value.suggestions.isEmpty(), "search queries aren't sentences")
    }

    @Test
    fun capsLockCapitalisesSuggestionsAndLeavingItRestoresThem() {
        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        engine.onKey(KeyAction.Shift)
        time += 100.milliseconds
        engine.onKey(KeyAction.Shift)
        type("h")
        assertTrue(engine.state.value.suggestions.isNotEmpty())
        assertTrue(engine.state.value.suggestions.all { it.text == it.text.uppercase() })
        engine.onKey(KeyAction.Shift) // caps lock off
        assertTrue(engine.state.value.suggestions.any { it.text != it.text.uppercase() })
    }

    @Test
    fun deleteWordRemovesTheWordAndTheSpacesAfterIt() {
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None, autoCorrect = false))
        type("hello big world ")
        engine.onKey(KeyAction.DeleteWord)
        assertEquals("hello big ", host.text)
        engine.onKey(KeyAction.DeleteWord)
        assertEquals("hello ", host.text)
        type("it's")
        engine.onKey(KeyAction.DeleteWord)
        assertEquals("hello ", host.text, "apostrophes stay part of the word")
    }

    @Test
    fun deleteWordTakesPunctuationAndEmojiOneAtATime() {
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None, autoCorrect = false))
        type("hi")
        engine.commitRawText("👍🏽")
        engine.onKey(KeyAction.DeleteWord)
        assertEquals("hi", host.text, "the whole emoji, never half of it")
        type("!")
        engine.onKey(KeyAction.DeleteWord)
        assertEquals("hi", host.text)
    }

    @Test
    fun manualIncognitoStopsLearningAndStaysOnAcrossFields() {
        start()
        engine.setIncognito(true)
        type("zorblax ")
        start()
        assertTrue(engine.state.value.incognito)
        type("zorblax ")
        assertNull(userDictionary.lookup("zorblax"))
        engine.setIncognito(false)
        type("zorblax ")
        assertEquals("zorblax", userDictionary.lookup("zorblax")?.word)
    }

    @Test
    fun doubleTapShiftLocksCaps() {
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        engine.onKey(KeyAction.Shift)
        time += 100.milliseconds
        engine.onKey(KeyAction.Shift)
        assertEquals(ShiftState.Locked, engine.state.value.shift)
        type("ok")
        assertEquals("OK", host.text)
    }

    @Test
    fun enterPerformsTheEditorActionOrInsertsANewline() {
        val android = start(attributes = EditorAttributes(imeAction = ImeAction.Search))
        engine.onKey(KeyAction.Enter)
        assertEquals(listOf(ImeAction.Search), android.performedActions)
        assertEquals("", android.text)

        val ios = start(host = FakeTextHost(supportsEditorActions = false), attributes = EditorAttributes(imeAction = ImeAction.Send))
        engine.onKey(KeyAction.Enter)
        assertEquals("\n", ios.text)
    }

    @Test
    fun numberFieldsOpenTheNumberPad() {
        start(attributes = EditorAttributes(fieldType = FieldType.Number))
        assertEquals(KeyboardMode.Numeric, engine.state.value.mode)
    }

    @Test
    fun externalCursorMovesResyncTheComposingWord() {
        val host = start(host = FakeTextHost("hello world"))
        host.placeCursor(3)
        engine.onExternalChange()
        assertEquals("hel", engine.state.value.composing)
    }

    @Test
    fun incognitoFieldsNeverLearn() {
        start(attributes = EditorAttributes(incognito = true, capitalization = Capitalization.None))
        type("zorblax ")
        assertNull(userDictionary.lookup("zorblax"))

        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("zorblax ")
        assertEquals("zorblax", userDictionary.lookup("zorblax")?.word)
    }

    @Test
    fun globeKeyDelegatesToTheHost() {
        start()
        engine.onKey(KeyAction.NextInputMethod)
        assertEquals(1, keyboardHost.switchCount)
    }
}
