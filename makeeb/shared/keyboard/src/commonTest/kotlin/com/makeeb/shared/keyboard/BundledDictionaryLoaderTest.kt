package com.makeeb.shared.keyboard

import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.platform.storage.BundledFiles
import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.testing.FakePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BundledDictionaryLoaderTest {
    private val pack = MkdWriter.write(
        listOf(MkdWord("kitchen", 116), MkdWord("kitten", 90), MkdWord("the", 222)),
        mapOf("language" to "en-US"),
    )
    private var maps = 0

    private fun files(bytes: ByteArray?) = BundledFiles { name ->
        maps++
        if (name == BundledDictionaryLoader.PACK && bytes != null) ByteArrayRegion(bytes) else null
    }

    private fun SuggestionEngine.words(typed: String) = suggest(TypingContext(typed), limit = 5).suggestions.map { it.text }

    @Test
    fun suggestionsComeFromThePackOnceItIsMapped() = runTest {
        val loader = BundledDictionaryLoader(files(pack), scope = this)
        val engine = DictionarySuggestionEngine(loader.dictionary)
        loader.start()
        assertTrue("kitchen" !in engine.words("kitch"), "the starter list answers until the pack is mapped")

        advanceUntilIdle()
        assertEquals(3, assertIs<BundledDictionaryLoader.Status.Loaded>(loader.status.value).words)
        assertEquals("kitchen", engine.words("kitch").first())
    }

    @Test
    fun withoutAPackTheStarterListStays() = runTest {
        val loader = BundledDictionaryLoader(files(null), scope = this)
        loader.start()
        advanceUntilIdle()
        assertEquals(BundledDictionaryLoader.Status.Missing, loader.status.value)
        assertTrue("hello" in DictionarySuggestionEngine(loader.dictionary).words("hel"))
    }

    @Test
    fun anUnreadablePackKeepsTheStarterList() = runTest {
        val loader = BundledDictionaryLoader(files(pack.copyOf(40)), scope = this)
        loader.start()
        advanceUntilIdle()
        assertIs<BundledDictionaryLoader.Status.Failed>(loader.status.value)
        assertTrue("hello" in DictionarySuggestionEngine(loader.dictionary).words("hel"))
    }

    @Test
    fun startingAgainDoesNotMapAgain() = runTest {
        val loader = BundledDictionaryLoader(files(pack), scope = this)
        loader.start()
        loader.start()
        advanceUntilIdle()
        loader.start()
        advanceUntilIdle()
        assertEquals(1, maps)
    }

    @Test
    fun theRuntimeModuleMapsThePackThePlatformBinds() = runTest {
        val koin = koinApplication {
            modules(
                module {
                    single<PreferencesRepository> { FakePreferencesRepository() }
                    single<BundledFiles> { files(pack) }
                },
                keyboardRuntimeModule,
            )
        }.koin
        val engine = koin.get<SuggestionEngine>()
        val loader = koin.get<BundledDictionaryLoader>()
        // The runtime loads on a real background dispatcher.
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { loader.status.first { it is BundledDictionaryLoader.Status.Loaded } }
        }
        assertEquals("kitchen", engine.words("kitch").first())
        koin.close()
    }
}
