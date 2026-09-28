package com.makeeb.shared.keyboard

import com.makeeb.core.model.EditorAttributes
import com.makeeb.engine.clipboard.ClipboardHistory
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.emoji.BundledEmojiCatalog
import com.makeeb.engine.emoji.EmojiCategory
import com.makeeb.engine.emoji.EmojiRecents
import com.makeeb.engine.input.InputEngine
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.platform.clipboard.Clip
import com.makeeb.platform.feedback.NoFeedback
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakePreferencesRepository
import com.makeeb.testing.FakeSystemClipboard
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardSessionTest {
    private val clipboard = FakeSystemClipboard()
    private val catalog = BundledEmojiCatalog()

    private fun TestScope.session(): KeyboardSession {
        val prefs = FakePreferencesRepository()
        val engine = InputEngine(BuiltInLayoutProvider(), DictionarySuggestionEngine(StarterDictionaries.english()), prefs.preferences)
        return KeyboardSession(
            engine, prefs, catalog, EmojiRecents(), ClipboardHistory(),
            KeyboardPorts(clipboard, NoFeedback, NoFeedback), backgroundScope,
        )
    }

    @Test
    fun manualIncognitoKeepsEmojiAndClipsOutOfHistoryUntilTurnedOff() = runTest {
        val session = session()
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        runCurrent()
        session.perform(StripAction.Incognito)
        assertTrue(session.engine.state.value.incognito)

        val smile = catalog.emojis(EmojiCategory.SmileysAndPeople).first()
        session.onEmoji(smile)
        clipboard.copy(Clip("copied while incognito"))
        runCurrent()
        assertTrue(session.emojiRecentsState.value.isEmpty(), "no emoji recents while incognito")
        assertTrue(session.clipboardEntries.value.isEmpty(), "no clipboard history while incognito")

        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        assertTrue(session.engine.state.value.incognito, "stays on across fields")

        session.perform(StripAction.Incognito)
        session.onEmoji(smile)
        assertEquals(listOf(smile), session.emojiRecentsState.value)
    }

    @Test
    fun aFieldThatAsksForNoLearningIsIncognitoWithoutTheToggle() = runTest {
        val session = session()
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes(incognito = true))
        session.onEmoji(catalog.emojis(EmojiCategory.SmileysAndPeople).first())
        assertTrue(session.engine.state.value.incognito)
        assertTrue(session.emojiRecentsState.value.isEmpty())
    }
}
