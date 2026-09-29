package com.makeeb.shared.keyboard

import com.makeeb.engine.clipboard.ClipboardHistory
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.LearnedWordsStore
import com.makeeb.engine.dictionary.UserDictionary
import com.makeeb.engine.emoji.BundledEmojiCatalog
import com.makeeb.engine.emoji.EmojiCatalog
import com.makeeb.engine.emoji.EmojiRecents
import com.makeeb.engine.gesture.GestureDecoder
import com.makeeb.engine.gesture.KeySequenceGestureDecoder
import com.makeeb.engine.input.InputEngine
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.LayoutProvider
import com.makeeb.engine.prediction.DictionarySuggestionEngine
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.platform.storage.BundledFiles
import com.makeeb.platform.storage.PrivateFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Engine bindings for the keyboard process. Requires a `PreferencesRepository` from the
 * platform root, takes the dictionary pack from its `BundledFiles` and keeps learned words in its
 * `PrivateFiles` when it binds them. Koin singles are lazy, so nothing heavy loads until the
 * keyboard shows.
 */
val keyboardRuntimeModule = module {
    single<LayoutProvider> { BuiltInLayoutProvider() }
    // Mapping starts when the engine first asks for the dictionary, as the keyboard is created.
    single { BundledDictionaryLoader(files = getOrNull<BundledFiles>(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<Dictionary> { get<BundledDictionaryLoader>().also { it.start() }.dictionary }
    // Learned words load from the keyboard's private files (memory only when the platform binds
    // none) while the keyboard shows, and save back in batches, both off the main thread.
    single {
        LearnedWordsStore(
            dictionary = UserDictionary(languageTag = "en"),
            files = getOrNull<PrivateFiles>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            io = Dispatchers.Default,
        ).also { it.load() }
    }
    single<SuggestionEngine> { DictionarySuggestionEngine(main = get(), user = get<LearnedWordsStore>()) }
    single<GestureDecoder> { KeySequenceGestureDecoder(dictionary = get()) }
    single<EmojiCatalog> { BundledEmojiCatalog() }
    single {
        val emoji = get<EmojiCatalog>()
        InputEngine(
            layouts = get(),
            suggestionEngine = get(),
            preferences = get<PreferencesRepository>().preferences,
            emojiForWord = { word -> emoji.forWord(word)?.value },
        )
    }
    single { EmojiRecents() }
    single { ClipboardHistory() }

    // One session per keyboard surface; the platform shell supplies its ports and lifetime.
    factory { (ports: KeyboardPorts, scope: CoroutineScope) ->
        KeyboardSession(
            engine = get(),
            preferencesRepository = get(),
            emojiCatalog = get(),
            emojiRecents = get(),
            clipboardHistory = get(),
            ports = ports,
            scope = scope,
            snippetsRepository = getOrNull(),
            learnedWords = get(),
        )
    }
}
