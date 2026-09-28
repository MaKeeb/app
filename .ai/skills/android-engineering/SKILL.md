---
name: android-engineering
description: Android engineering for MaKeeb's IME: InputMethodService lifecycle, InputConnection cost and batching, EditorInfo mapping, insets and edge-to-edge, Compose inside the IME window, subtypes and switching, direct boot, clipboard and feedback, and API 35–37 behaviour changes. Use when writing or reviewing androidMain code, the IME service, the Android shell, or anything that runs in the IME process.
---

# Android engineering

Read `keyboard-platforms` first for the constraint map and where each adapter lives. The evidence behind this skill is `docs/research/platform-apis.md` §2 (API levels checked against the Android 17 SDK) and §7 (policy risks).

Baseline: minSdk 26, targetSdk 36, compileSdk 37 (`libs.versions.toml`). Anything newer than 26 needs a `Build.VERSION.SDK_INT` check and a fallback that still works.

## The IME process

- `MaKeebInputMethodService` is the keyboard's only Android entry point. It provides the lifecycle, ViewModel-store and saved-state owners, installs them on the IME window's decor view, builds `KeyboardPorts`, and gets a `KeyboardSession` from Koin. Keep it a thin shell: new behaviour goes into `:shared:keyboard` or an engine module, with a test.
- The service outlives many input sessions and the process lives as long as MaKeeb is the selected IME. Whatever `onCreate` allocates stays resident. The platform docs say to release large allocations in `onWindowHidden`.
- Input arrives on the main thread, and `InputEngine` and `TouchController` are main-thread only. Slow work (dictionary loading, flushing learned words) runs in a coroutine off the main thread and posts results back.

## Lifecycle: what goes where

| Callback | Use it for | Watch out |
| --- | --- | --- |
| `onCreate` | Session, ports, lazy engine setup | No blocking I/O or dictionary parsing. |
| `onInitializeInterface` | Recompute metrics after rotation, fold or density changes | `onCreateInputView` runs again after configuration changes. |
| `onStartInput(info, restarting)` | A field is bound, even while the soft keyboard is hidden (hardware keyboard) | The service only uses `onStartInputView` today; APP-100 needs this callback. |
| `onStartInputView(info, restarting)` | `session.start(...)` with `info.toEditorAttributes()` | `restarting` means the same field was re-bound: keep user-visible state unless the attributes changed. |
| `onUpdateSelection(...)` | `engine.onExternalChange()` | Fires for our own edits too. Must be cheap and idempotent. |
| `onFinishInputView` / `onFinishInput` | `session.stop()`, flush learning (never for incognito fields) | Don't leave composing text behind. |
| `onWindowShown` / `onWindowHidden` | Lifecycle RESUMED/STARTED, trim caches | |
| `onComputeInsets` | Touchable region when the window is taller than the keys | Not called while the extract view shows. |
| `onEvaluateFullscreenMode` | Returns false: no extract UI in landscape | |

## InputConnection: every read is IPC

- Getters (`getTextBeforeCursor`, `getTextAfterCursor`, `getSelectedText`, `getSurroundingText`) are synchronous binder calls into the app, with a 2 s timeout. A slow app stalls our main thread and the user's typing. Budget: no reads per keystroke. The engine's `TextMirror` fetches a window of text once and applies its own edits to it; `onUpdateSelection` → `InputEngine.onSelectionChanged` tells the late reports of our own edits from real external changes, and only the latter trigger a read. Edits that delete what the mirror says is there (autocorrect, suggestion picks, delete-word) verify first: one read per word.
- Writes are one-way calls, applied in order. `onUpdateSelection` for our own write arrives later, so `onExternalChange` must tolerate seeing its own edits.
- Group multi-step edits with `TextHost.batchEdit {}` (`beginBatchEdit`/`endBatchEdit`), so the app applies them as one change and reports one selection update.
- Prefer one native call over several: `deleteSurroundingTextInCodePoints` (API 24) for emoji and surrogate pairs, and `replaceText` (API 34) for atomic autocorrect, behind a version check.
- `EditorInfo.getInitialTextBeforeCursor` (API 30) gives context at session start without a round trip.
- `currentInputConnection` can be null or change between sessions. That is why `InputConnectionTextHost` takes a provider lambda. Never cache the connection.
- Some editors (terminals, WebView, games) ignore parts of the API. `sendKeyEvent` with `KEYCODE_DEL` or `KEYCODE_ENTER` is the fallback. Test a WebView field when you change editing code.

## EditorInfo → EditorAttributes

The Android mapping is `EditorInfoMapping.kt` and the iOS one is `InputTraitsMapping.kt`, both in `:platform:host`. Change them together, and add a test for the shared logic that consumes the new attribute.
- Password variations (`TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`) → `FieldType.Password`, which is always incognito.
- `IME_FLAG_NO_PERSONALIZED_LEARNING` → `incognito = true`. The javadoc calls it "not a guarantee"; we honour it anyway.
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS` → `suggestions = false`. E-mail and URI variations → `autoCorrect = false`.
- `IME_FLAG_NO_ENTER_ACTION` → Enter inserts a newline even when an action is set.
- `hintLocales` (API 24) → `languageTags`.

## Window, insets and drawing

- targetSdk 36 enforces edge-to-edge. The research (§2.4) infers that the IME must pad for the `navigationBars` inset itself. Check with both gesture and 3-button navigation that no key sits under the gesture handle.
- Previews, popups and panels draw inside the keyboard's own view. Don't use Compose `Popup` or `Dialog` in the IME. This also keeps parity with iOS, where nothing can draw above the keyboard (`TouchConfig.overflowAbove` lets previews reach into the strip).
- If the window ever needs to be taller than the visible keys, set `contentTopInsets`, `visibleTopInsets` and a `touchableRegion` in `onComputeInsets`, so the app underneath still receives touches.
- Predictive back is on for target 36. Closing an in-keyboard panel on Back needs an `OnBackInvokedCallback` (research §2.4). Check on the emulator that Back closes the panel first and hides the keyboard second.

## Switching, subtypes, languages

- Globe key: `ImeServiceKeyboardHost` calls `switchToNextInputMethod(false)`. `shouldOfferSwitchingToNextInputMethod()` (API 28) decides whether we draw one (`KeyboardHost.needsInputMethodSwitchKey`). Long-press opens `InputMethodManager.showInputMethodPicker()`.
- Languages are `<subtype>`s in `res/xml/method.xml`: `languageTag`, `imeSubtypeLocale`, a label, and on newer APIs `layoutLabel` (36) and `shortLabel` (37). Handle `onCurrentInputMethodSubtypeChanged`.

## Storage, direct boot, clipboard, feedback

- The service is `directBootAware`. Before the first unlock only device-protected storage exists, which is where preferences live (`AndroidPreferences.kt`). Learned words and clipboard history belong in credential-encrypted storage and must degrade gracefully (not crash) before unlock. Test it: set a password lock on the emulator, reboot, and type on the lock screen.
- Clipboard: as the default IME we can read the clipboard without the Android 12+ "pasted" toast. Skip and never persist clips flagged `ClipDescription.EXTRA_IS_SENSITIVE` (API 33). Keep nothing from incognito fields.
- Haptics: `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` follows the system haptics setting (`FLAG_IGNORE_GLOBAL_SETTING` is deprecated). Sound: `AudioManager.playSoundEffect(FX_KEYPRESS_*)`.
- Android 17's background-audio hardening may silence IME key clicks. Test with `adb -s emulator-5554 shell cmd audio set-enable-hardening throw`.
- Typing must work offline and typed text never leaves the device. Network is allowed for opt-in extras (GIF search, pack downloads) shown only when online. Adding `INTERNET` is a deliberate, visible change (it shows on the Play listing): record it on the board, and consider a build flavor without it.

## Debugging

```
adbe() { adb -s emulator-5554 "$@"; }
adbe shell dumpsys input_method          # bound IME, current EditorInfo, subtypes, window state
adbe shell ime list -s                   # enabled IMEs
adbe logcat --pid="$(adbe shell pidof com.makeeb.debug)"
adbe shell dumpsys meminfo com.makeeb.debug
adbe shell dumpsys gfxinfo com.makeeb.debug framestats
```

Never log typed text, `EditorInfo` extras or clip contents (`.ai/instructions.md` → Privacy).

## Before calling Android work done

- `jvmTest` and `:app:android:assembleDebug` (`build-and-verify`).
- On the emulator, try a plain text field, a password field (no suggestions, no learning), an e-mail or URL field, a multi-line field, a field with an action (search or send), rotation, dark mode, and gesture plus 3-button navigation.
- One screenshot of the change, per `build-and-verify`.
