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

InputEngine ⇄ SuggestionEngine (:engine:prediction) ⇄ Dictionary + UserDictionary (:engine:dictionary)
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
- **Autocorrect** runs only on a separator (space or `. , ! ? ; :`), and only with a confident `Prediction.autoCorrection`. It never runs in password fields, when the field or the user has disabled it, or on a word the user just reverted (`rejectedCorrection`).
- **Revert**: Backspace straight after an autocorrection restores the original word (`pendingRevert`). Any other key, a cursor move, or an external change that no longer ends with the correction cancels the pending revert.
- **Double-space period**: a second space within 800 ms after a letter or digit becomes ". ".
- **Shift**: Off → OneShot, and a double tap within 350 ms goes to Locked. Auto-capitalisation sets OneShot according to `Capitalization`, and typing a character clears OneShot.
- **Learning** goes only through `learn()`, which checks `editor.incognito`. Never call `suggestionEngine.learn` directly. Password fields are always incognito.
- **Panels** (emoji, clipboard) commit through `commitRawText`: no shift, no autocorrect.
- Timing constants use the injected `TimeSource`, never the wall clock.

## Suggestions and dictionaries

- `SuggestionEngine.suggest(TypingContext)` returns `Prediction(suggestions, autoCorrection)`. `TypingContext.previousWords` (up to two) is there for next-word prediction (APP-38).
- Today `DictionarySuggestionEngine` runs over a `TrieDictionary` plus a `UserDictionary`. `StarterDictionaries` are tiny in-code word lists for development.
- `suggest` runs on the main thread on every keystroke. Its cost must be bounded and must not grow with dictionary size, and it should allocate little.
- Real dictionaries (APP-110, APP-37) are memory-mapped binary files loaded off the main thread, never Kotlin collections. Check word-list licences before importing any: AOSP and HeliBoard lists are Apache-2.0, and anything else needs a review (roadmap).
- Scope: statistical prediction, autocorrect, learning and swipe decoding are in scope. LLM or generative features are not (`.ai/instructions.md` → Scope).

## Layouts and touch

- Layouts are a Kotlin DSL (`LayoutDsl.kt`, `BuiltInLayouts.kt`) behind `LayoutProvider`. `LayoutGeometry` places keys for a given size and answers `keyAt`. Moving layouts into data files is the APP-14 card.
- Setting new geometry cancels touches in flight (`TouchController.geometry`).
- Timings live in `TouchConfig`. Density and `overflowAbove` come from the platform shell (`TouchConfig.forDensity`).

## Testing

- Every behaviour change gets a `commonTest` case in the module that owns it. Run `./gradlew jvmTest`, or a single module such as `:engine:input:jvmTest`.
- `InputEngineTest` shows the pattern: the real `BuiltInLayoutProvider`, `DictionarySuggestionEngine(StarterDictionaries.english(), UserDictionary("en"))`, a `FakeTextHost`, a `FakeKeyboardHost`, a `TestTimeSource` passed as `timeSource`, and a `type("...")` helper. Assert on `host.text` or `host.toString()` (`|` marks the caret) and on `engine.state.value`.
- For time, advance the `TestTimeSource` for double-space and double-tap, and use `runTest` virtual time for `TouchController` timers (`TouchControllerTest`). Never use real delays.
- Cover the iOS shape (no editor actions) and the external-change path (`host.placeCursor(n)` then `engine.onExternalChange()`).
