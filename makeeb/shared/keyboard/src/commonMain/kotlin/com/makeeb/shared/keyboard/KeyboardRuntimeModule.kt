package com.makeeb.shared.keyboard

import com.makeeb.engine.clipboard.ClipboardHistory
import com.makeeb.engine.dictionary.Dictionary
import com.makeeb.engine.dictionary.StarterDictionaries
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
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

/**
 * Engine bindings for the keyboard process. Requires a `PreferencesRepository` from the
 * platform root. Koin singles are lazy, so nothing heavy loads until the keyboard shows.
 */
val keyboardRuntimeModule = module {
    single<LayoutProvider> { BuiltInLayoutProvider() }
    single<Dictionary> { StarterDictionaries.english() }
    single { UserDictionary(languageTag = "en") }
    single<SuggestionEngine> { DictionarySuggestionEngine(main = get(), user = get<UserDictionary>()) }
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
        )
    }
}
