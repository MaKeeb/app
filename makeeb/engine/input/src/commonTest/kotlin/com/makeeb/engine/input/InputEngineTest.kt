package com.makeeb.engine.input

import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.FieldType
import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.ShiftState
import com.makeeb.core.model.stripSlots
import com.makeeb.core.model.Suggestion
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.engine.prediction.Prediction
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.engine.prediction.TapPoint
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun alternatesFollowTheLanguageSettingOnAnyLayout() {
        start()
        fun alternatesOf(char: Char) = engine.state.value.layout.characterKeys.getValue(char).alternates
        assertEquals("à", alternatesOf('a').first())
        preferences.value = preferences.value.copy(languageTags = listOf("de"))
        engine.refreshLayout() // KeyboardSession does this on every preferences change
        assertEquals("qwerty", engine.state.value.layout.id)
        assertEquals("ä", alternatesOf('a').first(), "German alternates on QWERTY")
        preferences.value = preferences.value.copy(letterLayoutId = "azerty", languageTags = listOf("en"))
        engine.refreshLayout()
        assertEquals("azerty", engine.state.value.layout.id)
        assertEquals("à", alternatesOf('a').drop(1).first(), "after the digit hint, English alternates on AZERTY")
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
    fun noAutocorrectWithTheCaretInsideAWord() {
        val host = start(FakeTextHost("The", cursor = 1))
        type("eh ")
        assertEquals("Teh |he", host.toString(), "typing inside a word is never corrected")
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
        assertFalse(userDictionary.isLearned("zorblax"))
        engine.setIncognito(false)
        type("zorblax ")
        assertTrue(userDictionary.isLearned("zorblax"))
    }

    @Test
    fun cursorMovesByWordBothWays() {
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None, autoCorrect = false))
        type("one two three")
        engine.onKey(KeyAction.MoveCursorByWord(-1))
        engine.onKey(KeyAction.MoveCursorByWord(-1))
        type("x")
        assertEquals("one xtwo three", host.text)
        engine.onKey(KeyAction.MoveCursorByWord(1))
        type("y")
        assertEquals("one xtwoy three", host.text)
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
        assertFalse(userDictionary.isLearned("zorblax"))

        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("zorblax ")
        assertTrue(userDictionary.isLearned("zorblax"))
    }

    @Test
    fun aTypoThatSlippedThroughOnceIsStillCorrectedNextTime() {
        preferences.value = preferences.value.copy(autoCorrect = false)
        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("teh ")
        preferences.value = preferences.value.copy(autoCorrect = true)
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("teh ")
        assertEquals("the ", host.text)
    }

    @Test
    fun aWordCommittedTwiceIsNoLongerCorrected() {
        preferences.value = preferences.value.copy(autoCorrect = false)
        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("teh teh ")
        preferences.value = preferences.value.copy(autoCorrect = true)
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("teh ")
        assertEquals("teh ", host.text)
    }

    @Test
    fun undoingAnAutocorrectionKeepsTheWordAtOnce() {
        val first = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("teh ")
        assertEquals("the ", first.text)
        engine.onKey(KeyAction.Backspace)
        type(" ")
        assertEquals("teh ", first.text)

        val next = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("teh ")
        assertEquals("teh ", next.text, "kept once: the user's word now")
    }

    @Test
    fun longPressingALearnedWordOffersToForgetIt() {
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("zorblax zorblax zorb")
        val learned = engine.state.value.suggestions.single { it.text == "zorblax" }
        assertTrue(learned.learned, "flagged for the renderers")
        assertTrue(engine.onSuggestionLongPressed(learned))
        assertEquals("zorblax", engine.state.value.forgetOffer)

        engine.forgetOfferedWord()
        assertNull(engine.state.value.forgetOffer)
        assertFalse(userDictionary.isLearned("zorblax"))
        assertTrue(engine.state.value.suggestions.none { it.text == "zorblax" }, "the strip no longer offers it")
        assertEquals("zorblax zorblax zorb", host.text, "the text is left alone")
    }

    @Test
    fun dictionaryWordsCannotBeForgotten() {
        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("hel")
        val hello = engine.state.value.suggestions.first { it.text == "hello" }
        assertFalse(hello.learned)
        assertFalse(engine.onSuggestionLongPressed(hello))
        assertNull(engine.state.value.forgetOffer)
    }

    @Test
    fun typingOnKeepsTheWord() {
        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("zorblax zorb")
        assertTrue(engine.onSuggestionLongPressed(engine.state.value.suggestions.single { it.text == "zorblax" }))
        type("l")
        assertNull(engine.state.value.forgetOffer)
        assertTrue(userDictionary.isLearned("zorblax"))
        engine.onSuggestionLongPressed(engine.state.value.suggestions.single { it.text == "zorblax" })
        engine.dismissForgetOffer()
        assertNull(engine.state.value.forgetOffer)
        assertTrue(userDictionary.isLearned("zorblax"))
    }

    @Test
    fun aWordForgottenWhileTypingItIsNotLearnedBackOnSpace() {
        val host = start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("zorblax zorblax")
        engine.onSuggestionLongPressed(engine.state.value.suggestions.single { it.text == "zorblax" })
        engine.forgetOfferedWord()
        type(" ")
        assertEquals("zorblax zorblax ", host.text)
        assertFalse(userDictionary.isLearned("zorblax"))
        type("zorblax ")
        assertTrue(userDictionary.isLearned("zorblax"), "typed again later, it is learned again")
    }

    @Test
    fun pickingTheWordAsTypedKeepsItAtOnce() {
        start(attributes = EditorAttributes(capitalization = Capitalization.None))
        type("zorblax")
        engine.onSuggestionSelected(engine.state.value.suggestions.single { it.kind == Suggestion.Kind.Typed })
        assertEquals("zorblax", userDictionary.lookup("zorblax")?.word, "known after one pick")
    }

    @Test
    fun passwordFieldsAndFieldsWithoutAutocorrectNeverLearn() {
        start(attributes = EditorAttributes(fieldType = FieldType.Password, incognito = true, capitalization = Capitalization.None))
        type("hunter2x ")
        start(attributes = EditorAttributes(autoCorrect = false, capitalization = Capitalization.None))
        type("jdoe1987 ")
        start(attributes = EditorAttributes(fieldType = FieldType.Email, autoCorrect = false, capitalization = Capitalization.None))
        type("someone@example ")
        assertEquals(0, userDictionary.size)
    }

    @Test
    fun globeKeyDelegatesToTheHost() {
        start()
        engine.onKey(KeyAction.NextInputMethod)
        assertEquals(1, keyboardHost.switchCount)
    }

    @Test
    fun emojiSearchTypesIntoTheQueryNotTheField() {
        val host = start(FakeTextHost("Hi "))
        engine.startEmojiSearch()
        assertEquals("", engine.state.value.emojiSearch)
        assertEquals(KeyboardPanel.Keys, engine.state.value.panel)
        type("Pizz")
        engine.onKey(KeyAction.Backspace)
        type("za")
        assertEquals("pizza", engine.state.value.emojiSearch, "lower case, backspace edits the query")
        assertEquals("Hi |", host.toString(), "the field is untouched")

        engine.commitRawText("🍕")
        assertEquals("Hi 🍕|", host.toString())
        assertEquals("pizza", engine.state.value.emojiSearch, "search stays open for another pick")

        engine.onKey(KeyAction.SwitchMode(KeyboardMode.Symbols))
        engine.onKey(KeyAction.Text("1"))
        assertEquals("pizza1", engine.state.value.emojiSearch, "symbols type into the query too")

        engine.onKey(KeyAction.Enter)
        assertNull(engine.state.value.emojiSearch)
        assertEquals(KeyboardPanel.Emoji, engine.state.value.panel)
        assertEquals("Hi 🍕|", host.toString(), "Enter closed the search instead of reaching the field")
    }

    @Test
    fun theEmojiKeyOrANewFieldEndsEmojiSearch() {
        start()
        engine.startEmojiSearch()
        engine.onKey(KeyAction.ShowPanel(KeyboardPanel.Emoji))
        assertNull(engine.state.value.emojiSearch)
        assertEquals(KeyboardPanel.Emoji, engine.state.value.panel)

        engine.startEmojiSearch()
        start()
        assertNull(engine.state.value.emojiSearch)
    }

    @Test
    fun aWordThatNamesAnEmojiOffersItAndPickingItReplacesTheWord() {
        val emojiEngine = InputEngine(
            layouts = BuiltInLayoutProvider(),
            suggestionEngine = DictionarySuggestionEngine(StarterDictionaries.english(), userDictionary),
            preferences = preferences,
            timeSource = time,
            emojiForWord = { if (it.lowercase() == "pizza") "🍕" else null },
        )
        val host = FakeTextHost("I want ")
        emojiEngine.startInput(host, keyboardHost, EditorAttributes())
        "pizza".forEach { emojiEngine.onKey(KeyAction.Text(it.toString())) }
        val emoji = emojiEngine.state.value.suggestions.single { it.kind == Suggestion.Kind.Emoji }
        assertEquals("🍕", emoji.text)
        assertEquals(emoji, emojiEngine.state.value.suggestions.stripSlots()[2], "always the right-hand slot")
        emojiEngine.onSuggestionSelected(emoji)
        assertEquals("I want 🍕 |", host.toString())

        preferences.value = KeyboardPreferences(emojiSuggestions = false)
        "pizza".forEach { emojiEngine.onKey(KeyAction.Text(it.toString())) }
        assertTrue(emojiEngine.state.value.suggestions.none { it.kind == Suggestion.Kind.Emoji }, "off in settings")
    }

    @Test
    fun tapsFollowTheComposingWordToTheSuggestionEngine() {
        val seen = mutableListOf<TypingContext>()
        val recording = object : SuggestionEngine {
            override fun suggest(context: TypingContext, limit: Int): Prediction = Prediction.Empty.also { seen += context }
            override fun learn(word: String) = Unit
        }
        val tapped = InputEngine(BuiltInLayoutProvider(), recording, preferences, time)
        tapped.startInput(FakeTextHost(), keyboardHost, EditorAttributes(capitalization = Capitalization.None))
        tapped.onKey(KeyAction.Text("h"), TapPoint(5.5f, 1.5f))
        tapped.onKey(KeyAction.Text("w"), TapPoint(1.9f, 0.5f))
        tapped.onKey(KeyAction.Text("x"))
        assertEquals(listOf(TapPoint(5.5f, 1.5f), TapPoint(1.9f, 0.5f), null), seen.last().taps)
        tapped.onKey(KeyAction.Backspace)
        assertEquals(listOf(TapPoint(5.5f, 1.5f), TapPoint(1.9f, 0.5f)), seen.last().taps, "a delete drops the last tap")
        assertTrue(seen.last().keys?.centre('q') != null, "key positions from the letters layout")
    }
}
