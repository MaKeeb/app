package com.makeeb.shared.keyboard

import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.clipboard.ClipboardHistory
import com.makeeb.engine.dictionary.LearnedWordsStore
import com.makeeb.engine.dictionary.StarterDictionaries
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.emoji.BundledEmojiCatalog
import com.makeeb.engine.emoji.EmojiCategory
import com.makeeb.engine.emoji.EmojiRecents
import com.makeeb.engine.input.InputEngine
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.platform.clipboard.Clip
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.QuickSetting
import com.makeeb.platform.feedback.HapticFeedback
import com.makeeb.platform.feedback.KeyFeedbackType
import com.makeeb.platform.feedback.SoundFeedback
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakePreferencesRepository
import com.makeeb.testing.FakePrivateFiles
import com.makeeb.testing.FakeSystemClipboard
import com.makeeb.testing.FakeTextHost
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardSessionTest {
    private val clipboard = FakeSystemClipboard()
    private val catalog = BundledEmojiCatalog()

    private val haptics = mutableListOf<Pair<KeyFeedbackType, Float>>()
    private val ticks = mutableListOf<Float>()
    private var prepared = 0
    private val sounds = mutableListOf<Pair<KeyFeedbackType, Float>>()

    private lateinit var prefs: FakePreferencesRepository

    private fun TestScope.session(
        preferences: KeyboardPreferences = KeyboardPreferences(),
        settingsWritable: Boolean = true,
        learnedWords: LearnedWordsStore? = null,
    ): KeyboardSession {
        prefs = FakePreferencesRepository(preferences)
        val engine = InputEngine(BuiltInLayoutProvider(), DictionarySuggestionEngine(StarterDictionaries.english(), learnedWords), prefs.preferences)
        return KeyboardSession(
            engine, prefs, catalog, EmojiRecents(), ClipboardHistory(),
            KeyboardPorts(
                clipboard,
                haptics = object : HapticFeedback {
                    override fun keyPress(type: KeyFeedbackType, intensity: Float) {
                        haptics += type to intensity
                    }

                    override fun prepare() {
                        prepared++
                    }

                    override fun selectionTick(intensity: Float) {
                        ticks += intensity
                    }
                },
                sound = SoundFeedback { type, volume -> sounds += type to volume },
                settingsWritable = { settingsWritable },
            ),
            backgroundScope,
            learnedWords = learnedWords,
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
    fun onAWideScreenTheKeysStayCentredAtTheirWidestOnEveryPage() = runTest {
        val session = session()
        session.density = 2f
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        // A 1366pt-wide tablet at 2x: the keys stop at MAX_KEYS_WIDTH and sit in the middle.
        session.setKeysAreaSize(2732f, 432f)
        runCurrent()
        val letters = session.geometry.value!!
        val keysWidth = KeyboardMetrics.MAX_KEYS_WIDTH * 2
        assertEquals((2732f - keysWidth) / 2, letters.horizontalInset, absoluteTolerance = 0.01f)
        assertEquals(keysWidth, letters.keys.maxOf { it.bounds.right } - letters.keys.minOf { it.bounds.left }, absoluteTolerance = 0.01f)

        session.onKey(KeyAction.SwitchMode(KeyboardMode.Symbols))
        runCurrent()
        val symbols = session.geometry.value!!
        assertEquals(KeyboardMode.Symbols, symbols.layout.mode)
        assertEquals(letters.horizontalInset, symbols.horizontalInset, "no key moves between modes")
        assertEquals(letters.keys.filter { it.row == 3 }.map { it.bounds }, symbols.keys.filter { it.row == 3 }.map { it.bounds })

        // A phone keeps its own side inset.
        session.setKeysAreaSize(822f, 432f)
        runCurrent()
        assertEquals(KeyboardMetrics.SIDE_INSET * 2, session.geometry.value!!.horizontalInset, absoluteTolerance = 0.01f)
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

    @Test
    fun theSettingsButtonTogglesTheQuickSettingsPanel() = runTest {
        val session = session()
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        session.perform(StripAction.Settings)
        assertEquals(KeyboardPanel.Settings, session.engine.state.value.panel)
        session.perform(StripAction.Settings)
        assertEquals(KeyboardPanel.Keys, session.engine.state.value.panel)
    }

    @Test
    fun aQuickSettingWritesThroughAndTheLayoutFollowsWithThePanelStillOpen() = runTest {
        val session = session(KeyboardPreferences(numberRow = false))
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        runCurrent()
        val rowsBefore = session.engine.state.value.layout.rows.size
        session.perform(StripAction.Settings)
        session.toggle(QuickSetting.NumberRow)
        runCurrent()
        assertTrue(prefs.preferences.value.numberRow, "stored, so the companion app sees it")
        assertEquals(rowsBefore + 1, session.engine.state.value.layout.rows.size)
        assertEquals(KeyboardPanel.Settings, session.engine.state.value.panel, "the panel stays open for more changes")
    }

    @Test
    fun quickSettingsAreReadOnlyWhereTheKeyboardCannotWriteSettings() = runTest {
        val session = session(KeyboardPreferences(keyPressSound = false), settingsWritable = false)
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        assertFalse(session.quickSettingsEditable)
        session.toggle(QuickSetting.Sound)
        assertFalse(prefs.preferences.value.keyPressSound)
    }

    @Test
    fun clipsFlaggedSensitiveAreNeverKept() = runTest {
        val session = session()
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        runCurrent()
        clipboard.copy(Clip("hunter2", isSensitive = true))
        clipboard.copy(Clip("see you at noon"))
        runCurrent()
        assertEquals(listOf("see you at noon"), session.clipboardEntries.value.map { it.text })
    }

    @Test
    fun choosingALanguageOnTheSpaceBarMakesItThePrimary() = runTest {
        val session = session(KeyboardPreferences(languageTags = listOf("en-GB", "sv", "hu")))
        session.selectLanguage("hu")
        assertEquals(listOf("hu", "en-GB", "sv"), prefs.preferences.value.languageTags)
        session.selectLanguage("en")
        assertEquals(listOf("en-GB", "hu", "sv"), prefs.preferences.value.languageTags, "keeps the stored regional form")
    }

    @Test
    fun learnedWordsAreSavedWhenTheKeyboardHides() = runTest {
        val files = FakePrivateFiles()
        val session = session(learnedWords = LearnedWordsStore(UserDictionary("en"), files, backgroundScope, EmptyCoroutineContext))
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes(capitalization = Capitalization.None))
        runCurrent()
        "zorblax ".forEach { session.onKey(if (it == ' ') KeyAction.Space else KeyAction.Text(it.toString())) }
        runCurrent()
        assertEquals(0, files.writes, "nothing written while typing")
        session.stop()
        runCurrent()
        assertEquals(1, files.writes)
    }

    @Test
    fun afterABootTheFirstFieldAfterTheUnlockReadsTheSavedWords() = runTest {
        val files = FakePrivateFiles()
        val saved = LearnedWordsStore(UserDictionary("en"), files, backgroundScope, EmptyCoroutineContext)
        saved.load()
        runCurrent()
        saved.learn("zorblax")
        saved.flush()
        runCurrent()

        files.locked = true
        val store = LearnedWordsStore(UserDictionary("en"), files, backgroundScope, EmptyCoroutineContext)
        val session = session(learnedWords = store)
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        runCurrent()
        assertFalse(store.isLearned("zorblax"), "locked: the keyboard types without them")
        session.stop()
        files.locked = false
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        runCurrent()
        assertTrue(store.isLearned("zorblax"))
    }

    @Test
    fun hapticsWakeWhenTheKeyboardAppearsAndSlidesTick() = runTest {
        val session = session(KeyboardPreferences(keyPressHaptics = true, hapticIntensity = 0.6f))
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        session.setKeysAreaSize(1000f, 216f)
        runCurrent()
        assertEquals(1, prepared, "ready before the first tap")
        val space = session.geometry.value!!.keys.first { it.key.action == KeyAction.Space }.bounds
        session.touch.down(1, space.centerX, space.centerY)
        session.touch.move(1, space.centerX + 40f, space.centerY) // past the slide threshold
        session.touch.move(1, space.centerX + 200f, space.centerY)
        session.touch.up(1, space.centerX + 200f, space.centerY)
        assertTrue(ticks.isNotEmpty() && ticks.all { it == 0.6f }, "a tick per cursor step, at the user's strength")

        ticks.clear()
        prepared = 0
        val quiet = session(KeyboardPreferences(keyPressHaptics = false))
        quiet.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        quiet.setKeysAreaSize(1000f, 216f)
        runCurrent()
        val bar = quiet.geometry.value!!.keys.first { it.key.action == KeyAction.Space }.bounds
        quiet.touch.down(1, bar.centerX, bar.centerY)
        quiet.touch.move(1, bar.centerX + 40f, bar.centerY)
        quiet.touch.move(1, bar.centerX + 200f, bar.centerY)
        quiet.touch.up(1, bar.centerX + 200f, bar.centerY)
        assertEquals(0, prepared)
        assertTrue(ticks.isEmpty(), "haptics off: no ticks either")
    }
}
