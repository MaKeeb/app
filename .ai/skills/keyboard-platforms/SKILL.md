---
name: keyboard-platforms
description: Android IME and iOS keyboard-extension constraints for MaKeeb, and where each platform adapter lives. Use before touching platform code, the iOS extension, text editing, clipboard, feedback or settings sharing.
---

# Keyboard platforms

Full research with sources: `docs/research/platform-apis.md` (capability matrix in §4, shared-vs-platform split in §5, the Compose-in-extension verdict in §6).

## Where the platform code lives

| Concern | Port (commonMain) | Android | iOS |
| --- | --- | --- | --- |
| Focused text field | `platform:host` `TextHost` | `InputConnectionTextHost` | `TextDocumentProxyTextHost` |
| Field attributes | `core:model` `EditorAttributes` | `EditorInfo.toEditorAttributes()` | `UITextDocumentProxyProtocol.toEditorAttributes()` |
| Keyboard host actions | `platform:host` `KeyboardHost` | `ImeServiceKeyboardHost` | `InputViewControllerKeyboardHost` |
| Haptics / sound | `platform:feedback` | `ViewHapticFeedback`, `AudioManagerSoundFeedback` | `ImpactHapticFeedback` (Full Access), `InputClickSoundFeedback` |
| Clipboard | `platform:clipboard` `SystemClipboard` | `AndroidSystemClipboard` | `PasteboardSystemClipboard` (Full Access, polls `changeCount`) |
| Settings storage | `core:settings` `PreferencesRepository` | device-protected SharedPreferences | App Group `NSUserDefaults` (`group.com.makeeb`) |
| Keyboard surface | `shared:keyboard` `KeyboardSession` | `app/android` `MaKeebInputMethodService` + `shared:surface` `KeyboardSurface` (Compose) | `shared:keyboard` `KeyboardExtensionBridge` + `app/ios` `KeyboardView.swift` (Core Graphics) |
| Key glyphs | `core:model` `KeyIcon` | `ui:theme` `KeyboardIcons` (Material Symbols) | SF Symbols in `KeyboardView.swift` |

## Android

- The IME is a `Service`. `MaKeebInputMethodService` provides the lifecycle, ViewModel-store and saved-state owners for Compose and installs them on the IME window's decor view.
- `InputConnection` getters are blocking IPC (up to 2 s timeout). Read as little as possible per keystroke, and batch edits (`beginBatchEdit`).
- `onUpdateSelection` fires for our own edits as well as external ones; `InputEngine.onExternalChange()` must be cheap and idempotent.
- Android 10+ shows a keyboard switcher and hide button in the navigation bar under the IME: no globe key or hide button of our own (`ImeServiceKeyboardHost.needsInputMethodSwitchKey`).
- Switching to another keyboard destroys the service, and `super.onDestroy()` calls back into `onFinishInputView`. Lifecycle moves go through `moveLifecycleTo`, which ignores anything after `DESTROYED`.
- The soft keyboard is hidden while a hardware keyboard is attached, unless the user enables "show virtual keyboard". This is expected.
- The IME is direct-boot aware: before first unlock, touch only device-protected storage.
- The default IME may read the clipboard. Skip clips flagged `EXTRA_IS_SENSITIVE`.

## iOS

- The extension (`MaKeebKeyboardExtension`) links only the `MaKeebKeyboard` framework from `:shared:keyboard`. No Compose, no `UIApplication`: Compose Multiplatform 1.12 calls `UIApplication.shared` and only renders with a foreground window scene.
- Memory: jetsam kills the extension silently at roughly 48–70 MB. Budget about 30 MB and memory-map dictionaries.
- `UITextDocumentProxy` offers insert, delete-backward, relative caret moves, marked text and truncated context. There is no selection API and no absolute positions. Return inserts `\n` (`performEditorAction` returns false).
- The extension cannot draw above its own top edge. Popups and previews stay inside the view (`TouchConfig.overflowAbove` = strip height).
- Without Full Access: no network, pasteboard, haptics or sound, and read-only App Group. Everything must still work (App Review 4.4.1). There is no microphone even with Full Access.
- The system keyboard replaces ours in secure and phone-pad fields, and in apps that opt out.
- `textDidChange` fires on field switches as well as text changes; the bridge restarts the input session when the attributes change.
- Preferences are written by the companion app in another process; the extension calls `reload()` in `viewWillAppear`.
