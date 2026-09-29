package com.makeeb.shared.keyboard

import com.makeeb.core.common.Sha256
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.engine.clipboard.ClipboardHistory
import com.makeeb.engine.dictionary.BundledPacks
import com.makeeb.engine.dictionary.InstalledPack
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.engine.emoji.BundledEmojiCatalog
import com.makeeb.engine.emoji.EmojiRecents
import com.makeeb.engine.input.InputEngine
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.platform.feedback.HapticFeedback
import com.makeeb.platform.feedback.SoundFeedback
import com.makeeb.platform.storage.BundledFiles
import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.platform.storage.PackFiles
import com.makeeb.testing.FakeKeyboardHost
import com.makeeb.testing.FakePackFiles
import com.makeeb.testing.FakePreferencesRepository
import com.makeeb.testing.FakeSystemClipboard
import com.makeeb.testing.FakeTextHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DictionaryLoaderTest {
    private fun pack(language: String, vararg words: Pair<String, Int>) =
        MkdWriter.write(words.map { (word, frequency) -> MkdWord(word, frequency) }, mapOf("language" to language))

    private val english = pack("en-US", "kitchen" to 116, "kitten" to 90, "the" to 222, "hello" to 200)
    private val hungarian = pack("hu", "szia" to 200, "szeretlek" to 150, "szeretnék" to 160, "kérdés" to 120)
    private val swedish = pack("sv", "och" to 220, "hej" to 180)

    private var bundledMaps = 0
    private val packs = FakePackFiles()
    private val languages = MutableStateFlow(listOf("en"))

    private fun bundled(bytes: ByteArray?) = BundledFiles { name ->
        bundledMaps++
        if (name == BundledPacks.EN_US && bytes != null) ByteArrayRegion(bytes) else null
    }

    private fun install(language: String, bytes: ByteArray) {
        packs.files[InstalledPack.fileName(language, Sha256.hex(bytes))] = bytes
    }

    private fun TestScope.loader(bundledPack: ByteArray? = english, packFiles: PackFiles? = packs) =
        DictionaryLoader(bundled(bundledPack), packFiles, languages, scope = backgroundScope)

    private fun SuggestionEngine.words(typed: String) = suggest(TypingContext(typed), limit = 5).suggestions.map { it.text }

    private fun DictionaryLoader.loaded() = assertIs<DictionaryLoader.Status.Loaded>(status.value)

    @Test
    fun suggestionsComeFromThePackOnceItIsMapped() = runTest {
        val loader = loader()
        val engine = DictionarySuggestionEngine(loader.dictionary)
        loader.start()
        assertTrue("kitchen" !in engine.words("kitch"), "the starter list answers until the pack is mapped")

        runCurrent()
        assertEquals(4, loader.loaded().words)
        assertEquals("kitchen", engine.words("kitch").first())
    }

    @Test
    fun withoutAPackTheStarterListStays() = runTest {
        val loader = loader(bundledPack = null, packFiles = null)
        loader.start()
        runCurrent()
        assertEquals(DictionaryLoader.Status.Missing, loader.status.value)
        assertTrue("hello" in DictionarySuggestionEngine(loader.dictionary).words("hel"))
    }

    @Test
    fun anUnreadablePackKeepsTheStarterList() = runTest {
        val loader = loader(bundledPack = english.copyOf(40))
        loader.start()
        runCurrent()
        assertIs<DictionaryLoader.Status.Failed>(loader.status.value)
        assertTrue("hello" in DictionarySuggestionEngine(loader.dictionary).words("hel"))
    }

    @Test
    fun nothingIsMappedAgainUntilSomethingChanges() = runTest {
        val loader = loader()
        loader.start()
        loader.start()
        runCurrent()
        loader.refresh()
        runCurrent()
        languages.value = listOf("en-GB")
        runCurrent()
        assertEquals(1, bundledMaps)
    }

    @Test
    fun thePrimaryLanguagesPackIsTheMainDictionaryAndTheOthersVouchForWords() = runTest {
        install("hu", hungarian)
        languages.value = listOf("hu", "en")
        val loader = loader()
        val engine = DictionarySuggestionEngine(loader.dictionary)
        loader.start()
        runCurrent()
        assertEquals("hu", loader.loaded().language)
        assertEquals(listOf("en-US"), loader.loaded().others)
        assertEquals(listOf("szeretnék", "szeretlek"), engine.words("szer").take(2))
        assertTrue(engine.words("kitch").none { it == "kitchen" }, "suggestions are the primary's")
        assertTrue(loader.dictionary.covers("hu") && loader.dictionary.covers("en"))
        assertEquals("hello", loader.dictionary.lookup("hello")?.word, "English words are words")
    }

    @Test
    fun withoutThePrimarysPackTheNextLanguageWithOneOrEnglishStandsIn() = runTest {
        install("hu", hungarian)
        languages.value = listOf("sv", "hu")
        val loader = loader()
        loader.start()
        runCurrent()
        assertEquals("hu", loader.loaded().language)
        assertFalse(loader.dictionary.covers("sv"), "autocorrect still pauses for Swedish")

        languages.value = listOf("sv")
        runCurrent()
        assertEquals("en-US", loader.loaded().language, "English, the pack that ships")
        assertFalse(loader.dictionary.covers("sv"))
    }

    @Test
    fun switchingThePrimaryLanguageSwapsTheMainDictionary() = runTest {
        install("hu", hungarian)
        languages.value = listOf("hu", "en")
        val loader = loader()
        loader.start()
        runCurrent()
        assertEquals("hu", loader.loaded().language)

        languages.value = listOf("en", "hu")
        runCurrent()
        assertEquals("en-US", loader.loaded().language)
        assertEquals(listOf("hu"), loader.loaded().others)
        assertEquals(1, bundledMaps, "both packs stay mapped while both are selected")
        assertEquals(1, packs.mapped.size)
    }

    @Test
    fun onlyTheSelectedLanguagesPacksAreMapped() = runTest {
        install("hu", hungarian)
        install("sv", swedish)
        languages.value = listOf("hu")
        val loader = loader()
        loader.start()
        runCurrent()
        assertEquals(listOf(InstalledPack.fileName("hu", Sha256.hex(hungarian))), packs.mapped)
        assertEquals(0, bundledMaps, "English isn't selected")
    }

    @Test
    fun packsInstalledOrRemovedLaterArePickedUpOnRefresh() = runTest {
        languages.value = listOf("hu")
        val loader = loader()
        loader.start()
        runCurrent()
        assertEquals("en-US", loader.loaded().language)

        install("hu", hungarian)
        loader.refresh()
        runCurrent()
        assertEquals("hu", loader.loaded().language)
        assertTrue(loader.dictionary.covers("hu"))

        packs.files.clear()
        loader.refresh()
        runCurrent()
        assertEquals("en-US", loader.loaded().language)
    }

    @Test
    fun aCorruptDownloadedPackIsSkipped() = runTest {
        install("hu", hungarian.copyOf(40))
        languages.value = listOf("hu", "en")
        val loader = loader()
        loader.start()
        runCurrent()
        assertEquals("en-US", loader.loaded().language)
    }

    @Test
    fun eachFieldLooksForNewPacks() = runTest {
        val preferences = FakePreferencesRepository(KeyboardPreferences(languageTags = listOf("hu")))
        val loader = DictionaryLoader(bundled(english), packs, preferences.preferences.map { it.languageTags }, backgroundScope)
        val engine = InputEngine(BuiltInLayoutProvider(), DictionarySuggestionEngine(loader.dictionary), preferences.preferences)
        val session = KeyboardSession(
            engine, preferences, BundledEmojiCatalog(), EmojiRecents(), ClipboardHistory(),
            KeyboardPorts(FakeSystemClipboard(), HapticFeedback { _, _ -> }, SoundFeedback { _, _ -> }),
            backgroundScope,
            dictionaries = loader,
        )
        loader.start()
        runCurrent()
        assertEquals("en-US", loader.loaded().language)

        install("hu", hungarian)
        session.start(FakeTextHost(), FakeKeyboardHost(), EditorAttributes())
        runCurrent()
        assertEquals("hu", loader.loaded().language)
    }

    @Test
    fun theRuntimeModuleMapsThePacksThePlatformBinds() = runTest {
        install("hu", hungarian)
        val koin = koinApplication {
            modules(
                module {
                    single<PreferencesRepository> { FakePreferencesRepository(KeyboardPreferences(languageTags = listOf("hu", "en"))) }
                    single<BundledFiles> { bundled(english) }
                    single<PackFiles> { packs }
                },
                keyboardRuntimeModule,
            )
        }.koin
        val engine = koin.get<SuggestionEngine>()
        val loader = koin.get<DictionaryLoader>()
        // The runtime loads on a real background dispatcher.
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { loader.status.first { it is DictionaryLoader.Status.Loaded } }
        }
        assertEquals("szeretnék", engine.words("szeret").first())
        koin.close()
    }
}
