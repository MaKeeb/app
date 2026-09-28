package com.makeeb.engine.prediction

import com.makeeb.core.model.Suggestion
import com.makeeb.engine.dictionary.MappedDictionary
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.platform.storage.ByteArrayRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The suggestion engine is unchanged; only the dictionary under it is a mapped pack. */
class MappedDictionarySuggestionTest {
    private val pack = MkdWriter.write(
        listOf(
            MkdWord("the", 222), MkdWord("then", 180), MkdWord("cat", 120), MkdWord("at", 190),
            MkdWord("kitchen", 116), MkdWord("kitten", 90), MkdWord("London", 120),
            MkdWord("damn", 200, offensive = true), MkdWord("dance", 100), MkdWord("dam", 50),
        ),
        mapOf("language" to "en-US"),
    )
    private val engine = DictionarySuggestionEngine(MappedDictionary(ByteArrayRegion(pack)), UserDictionary("en"))

    private fun words(typed: String) = engine.suggest(TypingContext(typed), limit = 5).suggestions.map { it.text }

    @Test
    fun completionsComeFromThePack() {
        assertEquals(listOf("kitchen", "kitten"), words("kit").take(2))
        assertEquals("kitchen", words("kitch").first())
    }

    @Test
    fun offensiveWordsAreNeverSuggested() {
        assertTrue("damn" !in words("da"), "completion")
        assertTrue("damn" !in words("dan"), "correction")
        assertTrue("damn" !in words("damm"), "correction")
        // Typed in full, it is the user's own word: offered back as typed, never as a replacement.
        val typed = engine.suggest(TypingContext("damn")).suggestions
        assertTrue(typed.filter { it.text == "damn" }.all { it.kind == Suggestion.Kind.Typed })
        assertNull(engine.suggest(TypingContext("damn")).autoCorrection)
    }

    @Test
    fun theAutocorrectPolicyIsUnchanged() {
        assertNull(engine.suggest(TypingContext("cat")).autoCorrection, "a known word is never corrected")
        assertEquals("the", engine.suggest(TypingContext("teh")).autoCorrection)
        assertEquals("London", engine.suggest(TypingContext("london")).autoCorrection)
    }
}
