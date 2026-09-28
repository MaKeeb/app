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
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.platform.feedback.HapticFeedback
import com.makeeb.platform.feedback.KeyFeedbackType
import com.makeeb.platform.feedback.SoundFeedback
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

    private val haptics = mutableListOf<Pair<KeyFeedbackType, Float>>()
    private val sounds = mutableListOf<Pair<KeyFeedbackType, Float>>()

    private fun TestScope.session(preferences: KeyboardPreferences = KeyboardPreferences()): KeyboardSession {
        val prefs = FakePreferencesRepository(preferences)
        val engine = InputEngine(BuiltInLayoutProvider(), DictionarySuggestionEngine(StarterDictionaries.english()), prefs.preferences)
        return KeyboardSession(
            engine, prefs, catalog, EmojiRecents(), ClipboardHistory(),
            KeyboardPorts(
                clipboard,
                haptics = HapticFeedback { type, intensity -> haptics += type to intensity },
                sound = SoundFeedback { type, volume -> sounds += type to volume },
            ),
            backgroundScope,
        )
    }

    @Test
    fun keyFeedbackUsesTheChosenStrengthAndVolume() = runTest {
        val session = session(KeyboardPreferences(keyPressHaptics = true, hapticIntensity = 0.8f, keyPressSound = true, soundVolume = 0.3f))
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        session.setKeysAreaSize(1000f, 216f)
        runCurrent()
        val g = session.geometry.value!!.keyFor('g')!!.bounds
        session.touch.down(1, g.centerX, g.centerY)
        session.touch.up(1, g.centerX, g.centerY)
        assertEquals(listOf(KeyFeedbackType.Standard to 0.8f), haptics)
        assertEquals(listOf(KeyFeedbackType.Standard to 0.3f), sounds)
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
