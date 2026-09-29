package com.makeeb.engine.input

import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.FieldType
import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.ShiftState
import com.makeeb.core.model.Suggestion
import com.makeeb.core.model.stripSlots
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.dictionary.pack.MkdFormat
import com.makeeb.engine.dictionary.pack.MkdNgramTable
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** Next-word predictions in the strip, from a pack with next-word statistics. */
class NextWordPredictionTest {
    private val words = listOf(
        MkdWord("the", 230), MkdWord("of", 225), MkdWord("and", 224), MkdWord("I", 220), MkdWord("you", 215),
        MkdWord("good", 200), MkdWord("very", 190), MkdWord("see", 195), MkdWord("morning", 170), MkdWord("luck", 160),
        MkdWord("idea", 150), MkdWord("hi", 180), MkdWord("there", 205), MkdWord("London", 140), MkdWord("in", 226),
        MkdWord("damn", 150, offensive = true), MkdWord("hello", 190),
    )
    private val meta = mapOf("language" to "en-US")
    private val ids = MappedDictionary(ByteArrayRegion(MkdWriter.write(words, meta))).let { lexicon -> words.associate { it.text to lexicon.wordId(it.text) } }
    private fun id(word: String) = ids.getValue(word)

    private val table = MkdNgramTable().apply {
        unigram(id("the"), 0.05)
        unigram(id("of"), 0.03)
        unigram(id("and"), 0.02)
        bigram(id("good"), id("morning"), 0.3)
        bigram(id("good"), id("luck"), 0.2)
        bigram(id("good"), id("damn"), 0.6)
        trigram(id("very"), id("good"), id("idea"), 0.5)
        bigram(id("in"), id("London"), 0.4)
        bigram(MkdFormat.SENTENCE_START, id("I"), 0.3)
        trigram(MkdFormat.SENTENCE_START, id("hi"), id("there"), 0.4)
    }
    private val dictionary = MappedDictionary(ByteArrayRegion(MkdWriter.write(words, meta, table)))

    private val time = TestTimeSource()
    private val preferences = MutableStateFlow(KeyboardPreferences())
    private val engine = InputEngine(
        layouts = BuiltInLayoutProvider(),
        suggestionEngine = DictionarySuggestionEngine(dictionary, UserDictionary("en")),
        preferences = preferences,
        timeSource = time,
    )

    private fun start(attributes: EditorAttributes = EditorAttributes()): FakeTextHost =
        FakeTextHost().also { engine.startInput(it, FakeKeyboardHost(), attributes) }

    private fun type(text: String) = text.forEach { char ->
        engine.onKey(if (char == ' ') KeyAction.Space else KeyAction.Text(char.toString()))
    }

    private fun strip(): List<String> = engine.state.value.suggestions.map { it.text }

    @Test
    fun aWordAndASpacePredictTheNextWordInPlaceOfPunctuation() {
        start()
        type("good ")
        val suggestions = engine.state.value.suggestions
        assertTrue(suggestions.all { it.kind == Suggestion.Kind.NextWord })
        assertEquals(listOf("morning", "luck", "the"), strip(), "best first; never the offensive one")
        assertEquals(listOf("luck", "morning", "the"), suggestions.stripSlots().map { it?.text }, "the best in the middle")
        type("very good ")
        assertEquals("idea", strip().first(), "two words of context")
    }

    @Test
    fun theStripKeepsItsToolbarAtTheStartOfAFieldOrASentence() {
        start()
        assertEquals(emptyList(), strip(), "empty field")
        type("good. ")
        assertEquals(emptyList(), strip(), "after a full stop")
        type("hi, ")
        assertEquals("there", strip().first(), "a comma doesn't end the sentence: \"Hi,\" still started it")
    }

    @Test
    fun noPredictionsOutsideRunningText() {
        start(EditorAttributes(imeAction = ImeAction.Search))
        type("good ")
        assertEquals(emptyList(), strip())
        start(EditorAttributes(fieldType = FieldType.Email, capitalization = Capitalization.None))
        type("good ")
        assertEquals(emptyList(), strip())
        start(EditorAttributes(fieldType = FieldType.Password))
        type("good ")
        assertEquals(emptyList(), strip())
    }

    @Test
    fun tappingAPredictionInsertsTheWordAndASpace() {
        val host = start()
        type("good ")
        engine.onSuggestionSelected(engine.state.value.suggestions.first { it.text == "morning" })
        assertEquals("Good morning ", host.text)
        assertEquals(ShiftState.Off, engine.state.value.shift)
        assertTrue(engine.state.value.suggestions.all { it.kind == Suggestion.Kind.NextWord }, "and predicts the next")
    }

    @Test
    fun punctuationAfterAPredictionTakesItsSpace() {
        val host = start()
        type("good ")
        engine.onSuggestionSelected(engine.state.value.suggestions.first { it.text == "luck" })
        type(".")
        assertEquals("Good luck. ", host.text)
        type("good ")
        engine.onSuggestionSelected(engine.state.value.suggestions.first { it.text == "luck" })
        engine.onKey(KeyAction.Text("!"))
        assertEquals("Good luck. Good luck! ", host.text, "the full stop's long-press alternates too")
    }

    @Test
    fun aSpaceRightAfterAPredictionIsNoDoubleSpace() {
        val host = start(EditorAttributes(capitalization = Capitalization.None))
        type("good ")
        engine.onSuggestionSelected(engine.state.value.suggestions.first { it.text == "luck" })
        time += 100.milliseconds
        type(" ")
        assertEquals("good luck  ", host.text)
    }

    @Test
    fun predictionsFollowTheShiftKey() {
        start()
        type("good ")
        assertEquals("morning", strip().first(), "mid-sentence: lower case")
        engine.onKey(KeyAction.Shift)
        assertEquals(listOf("Morning", "Luck", "The"), strip(), "Shift capitalises them, as it does the next letter")
        time += 1000.milliseconds
        engine.onKey(KeyAction.Shift)
        assertEquals("morning", strip().first(), "and turning it off again doesn't")
        time += 1000.milliseconds
        engine.onKey(KeyAction.Shift)
        time += 100.milliseconds
        engine.onKey(KeyAction.Shift)
        assertEquals(ShiftState.Locked, engine.state.value.shift)
        assertEquals(listOf("MORNING", "LUCK", "THE"), strip(), "caps lock")

        start(EditorAttributes(capitalization = Capitalization.Words))
        type("good ")
        assertEquals("Morning", strip().first(), "a field that capitalises every word")
    }

    @Test
    fun namesKeepTheirCapital() {
        start()
        type("in ")
        assertEquals("London", strip().first(), "after \"In\" at the start of a sentence")
        start(EditorAttributes(capitalization = Capitalization.None))
        type("hello in ")
        assertEquals("London", strip().first(), "mid-sentence")
    }
}
