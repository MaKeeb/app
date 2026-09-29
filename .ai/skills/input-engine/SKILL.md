---
name: input-engine
description: How MaKeeb's typing engine works and how to change it safely: the touch → engine → host pipeline, the TextHost contract on both platforms, InputEngine's state machine (shift, autocorrect and its revert, double-space, learning), suggestions and dictionaries, TouchController, and JVM tests with fakes and virtual time. Use before changing :engine:input, :engine:touch, :engine:prediction, :engine:dictionary, :engine:layout, KeyboardSession or a TextHost adapter.
---

# Input engine

## The pipeline

```
touches (Compose / UIKit)
  → TouchController (:engine:touch)        hit-testing, sliding, long-press, repeat, cursor slide, preview
  → KeyAction
  → InputEngine (:engine:input)            shift, modes, composing word, autocorrect, suggestions, learning
  → TextHost (:platform:host)              InputConnection / UITextDocumentProxy

InputEngine ⇄ SuggestionEngine (:engine:prediction) ⇄ Dictionary + LearnedWordsStore/UserDictionary (:engine:dictionary)
KeyboardSession (:shared:keyboard) wires it together, plays feedback, and exposes the state flows renderers draw
```

- The split: `TouchController` decides which key and when, and `InputEngine` decides what happens to the text. Neither touches platform APIs, and both are main-thread only.
- `KeyboardSession` is where a new port or feature enters (clipboard, emoji recents, feedback). Keep behaviour itself in the engine modules, where it can be unit-tested.

## The TextHost contract

Both adapters (`InputConnectionTextHost`, `TextDocumentProxyTextHost`) and `FakeTextHost` must honour this. If you change the contract, change all three in the same change.
- Reads are snapshots. They can be truncated (iOS) or slow (Android IPC). Where `TextHost.readsAreCheap` is false (Android) the engine reads through `TextMirror`, a local copy kept in step with the engine's own edits and reconciled by `onSelectionChanged`; reads on the keystroke path cost nothing. Deleting what the copy claims is there goes through `confirmMirror()` first. iOS reads the proxy directly (in-process) and calls `onExternalChange()` on every text/selection callback.
- `deleteBackward` removes one grapheme (or the selection), not one UTF-16 unit.
- `replaceBeforeCursor(length, text)` is how corrections and suggestions land. The default implementation deletes `length` graphemes, which is only correct when every character is its own grapheme. Words with combining marks or emoji would over-delete, so adapters override it with a native call.
- `performEditorAction` returns false on iOS, and the engine inserts `\n` instead. `FakeTextHost(supportsEditorActions = false)` reproduces this.
- `batchEdit {}` groups edits into one change on Android and does nothing special on iOS.

## InputEngine rules

- **Composing word** is derived from the text rather than tracked separately: it's the trailing word before the caret (`TextBoundaries.trailingWord`). That's why an external edit resyncs cleanly (`onExternalChange` → `resyncWithHost`). Keep it derived.
- **Autocorrect** runs only on a separator (space or `. , ! ? ; :`), and only with a confident `Prediction.autoCorrection`. It never runs in password fields, when the field or the user has disabled it, or on a word the user just reverted (`rejectedCorrection`). `DictionarySuggestionEngine` decides it: known typos, then accent/apostrophe refolds and proper-noun capitals, then a real typo only against a full lexicon (`Dictionary.isComprehensive`) when its typing cost beats keeping the word by a margin. `KeyboardPreferences.autoCorrectStrength` moves that margin (Modest/Normal/Aggressive, tuned with `:tools:dictionaries:typingHarness`). While a selected language has no full lexicon (`TypingContext.languages` vs the dictionary's language; `BundledPacks.languages`), the correction is offered first in the strip but never applied: that language's words would look like typos.
- **Revert**: Backspace straight after an autocorrection restores the original word (`pendingRevert`). Any other key, a cursor move, or an external change that no longer ends with the correction cancels the pending revert.
- **Double-space period**: a second space within 800 ms after a letter or digit becomes ". ".
- **Shift**: Off → OneShot, and a double tap within 350 ms goes to Locked. Auto-capitalisation sets OneShot according to `Capitalization`, and typing a character clears OneShot.
- **Learning** goes only through `learn()`, which skips incognito fields (the field's flag or the user's toggle; password fields always are) and fields that turn autocorrection off (user names, codes, addresses; iOS has no other no-learning hint). Never call `suggestionEngine.learn` directly. `DictionarySuggestionEngine.learn` then keeps only words the main dictionary lacks.
- **Panels** (emoji, clipboard) commit through `commitRawText`: no shift, no autocorrect.
- Timing constants use the injected `TimeSource`, never the wall clock.

## Suggestions and dictionaries

- `SuggestionEngine.suggest(TypingContext)` returns `Prediction(suggestions, autoCorrection)`. `TypingContext.previousWords` holds up to two words before the current one in the same sentence (`TextBoundaries.wordsBefore`: commas and quotes are skipped, a sentence end stops it), and `previousWordsStartSentence` says whether a sentence start comes right before them.
- **Next-word prediction:** with nothing typed, `suggest` returns `Suggestion.Kind.NextWord`s from the main dictionary's `NextWordModel`. That is the pack's `NGRM` section: trigram → bigram → unigram stupid backoff, Leipzig counts (docs/dictionaries/mkd-format.md). The same predictions raise the completions they contain while a word is typed, but never change autocorrect.
  - `InputEngine` asks for them only mid-sentence after a space, in running text: after a word, or after `, ; :`. They take all three slots, best in the middle, where the punctuation shortcuts were. The shortcuts return only when there are no predictions (no pack yet).
  - At the start of a field or a sentence, the strip keeps its toolbar (emoji, clipboard, incognito, settings).
  - Predictions follow the shift key like letters do: a one-shot shift capitalises them, caps lock upper-cases them.
  - Tapping one inserts it with a space; punctuation typed next takes that space (`suggestionSpace`).
  - The builder tokenises its corpora by the same rules as `wordsBefore` (`CorpusTokens`); keep the two in step (`NgramCountsTest`).
- The main dictionary is a `MappedDictionary` over the bundled MKD pack `en_US.mkd` (160k words from AOSP LatinIME), read in place through `ByteRegion` (`:platform:storage`). `BundledDictionaryLoader` (`:shared:keyboard`) maps it off the main thread. Until then, or if the pack is missing, `DeferredDictionary` serves `StarterDictionaries`, a tiny in-code list that tests also use. The format and its reasoning are in docs/dictionaries/mkd-format.md.
- Learned words: `UserDictionary` holds them on the heap, compactly (sorted folded keys plus a map, about 150 bytes a word; a trie cost 1.3 KB), capped at 3,000 words with the least useful evicted (use count halved every 1,000 words learned since; time is counted in learned words, never read from a clock). Case alone doesn't make a new word, and lower case wins. `LearnedWordsStore` wraps it and persists it through the `PrivateFiles` port (`:platform:storage`): it reads the file off the main thread when the keyboard is created, saves changes in 5-second batches and when the keyboard hides (`KeyboardSession.stop` → `flush`), and never writes before a successful load, so a locked device (Android direct boot) can't overwrite saved words; what is learned meanwhile merges in once the load succeeds. The file format is `LearnedWordsFile` (magic, version, varints, CRC-32).
- `suggest` runs on the main thread on every keystroke. Its cost must be bounded and must not grow with dictionary size, and it should allocate little.
  - `MappedDictionary.completions` is a best-first search, about 1 µs per query on the JVM.
  - `corrections` is the unweighted edit-distance walk. It costs 12–16k DP rows for a 6+ letter word with two allowed edits, and dominates per-key cost on the Pixel. Stage 3 replaces it with a bounded beam search (docs/research/dictionaries-autocorrect.md §6).
- Offensive words (AOSP `possibly_offensive`) are known to `lookup` but never offered by completions or corrections.
- Never load word lists into Kotlin collections in the keyboard. Packs are built at build time by `:tools:dictionaries` from pinned, hash-checked sources.
- Check word-list licences before importing any. AOSP lists are Apache-2.0. HeliBoard's dictionary repository is GPL-3.0 as a whole, so rebuild from the original sources (research §9). Anything else needs a review. Record attributions in `THIRD_PARTY_NOTICES.md`.
- Measure changes with the typing harness: `./gradlew :tools:dictionaries:typingHarness` reports keystroke savings (with and without tapping predictions), next-word accuracy, false corrections and typo fixes on held-out text, plus the model's accuracy and cost on corpus sentences held out of the counts (baseline in research §6.8).
- Scope: statistical prediction, autocorrect, learning and swipe decoding are in scope. LLM or generative features are not (`.ai/instructions.md` → Scope).

## Layouts and touch

- Letter rows and long-press alternates are JSON data (`engine/layout/data/`, schema in docs/layouts/schema.md), converted from AOSP LatinIME by `scripts/import-aosp-layouts.py` and embedded by `scripts/generate-layout-data.py`. Alternates come from the selected languages, merged primary first (`LayoutOptions.languageTags`, from `KeyboardPreferences.languageTags`), never from the layout.
- The frame stays Kotlin (`BuiltInLayouts`, `LayoutDsl.kt`): the number row, shift, backspace, digit hints, the field's bottom row and the symbols, number and phone pages. The data has no field for them, so `ModeSwitchGeometryTest` holds for any data. `BuiltInLayoutProvider` parses a file on first use; `LayoutParityTest` holds the data-built pages to the old Kotlin ones.
- `LayoutGeometry` places keys for a given size and answers `keyAt`.
- Setting new geometry cancels touches in flight (`TouchController.geometry`).
- Timings live in `TouchConfig`. Density and `overflowAbove` come from the platform shell (`TouchConfig.forDensity`).

## Testing

- Every behaviour change gets a `commonTest` case in the module that owns it. Run `./gradlew jvmTest`, or a single module such as `:engine:input:jvmTest`.
- `InputEngineTest` shows the pattern: the real `BuiltInLayoutProvider`, `DictionarySuggestionEngine(StarterDictionaries.english(), UserDictionary("en"))`, a `FakeTextHost`, a `FakeKeyboardHost`, a `TestTimeSource` passed as `timeSource`, and a `type("...")` helper. Assert on `host.text` or `host.toString()` (`|` marks the caret) and on `engine.state.value`.
- Persistence tests use `FakePrivateFiles` (`locked` plays direct boot, `failWrites` a full disk) with `runTest`: pass `backgroundScope` as the store's scope and a `StandardTestDispatcher(testScheduler)` (or `EmptyCoroutineContext`) as its `io` (`LearnedWordsStoreTest`).
- For time, advance the `TestTimeSource` for double-space and double-tap, and use `runTest` virtual time for `TouchController` timers (`TouchControllerTest`). Never use real delays.
- Cover the iOS shape (no editor actions) and the external-change path (`host.placeCursor(n)` then `engine.onExternalChange()`).
