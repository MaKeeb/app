# MaKeeb: Platform API research for third-party keyboards (Android and iOS)

- **Date:** 2026-09-27
- **Target stack:** Kotlin 2.4.x (latest stable 2.4.20), Compose Multiplatform 1.12.x (latest 1.12.1), Android 16/17 (API 36/37), iOS 26/27
- **Scope:** what each OS lets a third-party keyboard do, the hard limits, and what that means for a Kotlin Multiplatform (KMP) architecture that shares as much logic and UI as possible.

**Evidence labels used throughout**

| Label | Meaning |
|---|---|
| *(verified)* | Checked against official docs, the Android SDK (`platforms/android-37.0/data/api-versions.xml` and sources), Apple's documentation JSON, or first-party source code (AOSP, compose-multiplatform-core, skiko). |
| *(reported)* | Comes from third-party write-ups, issue trackers, or vendor docs (KeyboardKit, dev.to, forums). It is credible but not official. |
| *(unverified)* | I could not confirm it. Treat it as a hypothesis for a spike. |
| *(estimate)* | My own calculation. The inputs are stated next to it. |

---

## 1. Executive summary

### Key constraints

**Android** is a full-power platform for keyboards.
- An IME is a normal `Service` (`InputMethodService`) in its own app process. It owns a real window and can host any View or Jetpack Compose UI. FlorisBoard ships this pattern in production.
- It gets rich editor metadata through `EditorInfo`. It edits text over an asynchronous IPC channel (`InputConnection`), where getters block for up to 2 s (AOSP `MAX_WAIT_TIME_MILLIS = 2000`). It can send images and stickers with `commitContent`.
- The default IME may read the clipboard without the Android 12+ "pasted" toast (AOSP `ClipboardService` exempts it). It can record audio while visible, as long as it has `RECORD_AUDIO`.
- It has no hard memory ceiling beyond normal low-memory-killer behaviour.
- Recent API churn is incremental. Android 16 (API 36) added the custom IME-switcher-button callback, the key-event HMAC verification hook, `EditorInfo.isWritingToolsEnabled()` and subtype layout labels. Android 17 (API 37) added subtype short labels and `TextAttribute.setTextSuggestionSelected`, and hardens background audio. Whether that hardening affects IME key clicks needs testing.

**iOS** is a sandboxed, memory-starved extension point that has not really grown in years.
- A keyboard is an app extension (`com.apple.keyboard-service`) whose root is a `UIInputViewController`.
- It sees the document only through `UITextDocumentProxy`. That means insert, delete-backward, move the caret by a character offset, and marked text (iOS 13+). Context before and after the cursor is truncated in ways Apple does not document.
- It cannot select text, cannot draw above its own top edge, and cannot use the microphone.
- It is replaced by the system keyboard in secure and phone-pad fields, and in apps that opt out.
- Without **Full Access** it has no network, no pasteboard, no audio or haptics (per Apple's table and vendor reports), and **read-only** access to the App Group container.
- It is killed silently by jetsam at roughly **48 to 70 MB** of `phys_footprint`. The limit varies by device (see §3.6). Apple only says the limit "varies from model to model".
- App Store Guideline 4.4.1 requires a keyboard to work without Full Access and without network, to provide a next-keyboard key, and never to launch other apps except Settings.
- iOS 26/27 added no new public keyboard-extension APIs that I could find. They did add Liquid Glass layout quirks, a broken private host-bundle-ID trick in 26.4, and SwiftUI gesture regressions reported on 27.

### Verdict: Compose Multiplatform inside the iOS keyboard extension

**Do not plan on it. Treat it as an optional spike, not the baseline.**

1. **Not supported upstream.** JetBrains closed the app-extension feature request (CMP-3826) as *Obsolete* in November 2025 without implementing it. The only extension bug (CMP-4610, a Share extension rendering black) was root-caused to CMP and Skia calling `UIApplication.shared`. JetBrains said "We didn't plan supporting such a feature."
2. **CMP 1.12.1 still calls extension-unavailable APIs.** I checked the source. `ComposeContainer.ios.kt` reads `UIApplication.sharedApplication().userInterfaceLayoutDirection` when it initialises. `CMPViewController.m`, `CMPUIWindowSceneExtensions.m`, `PlatformUriHandler.ios.kt` and `UIKitIdleTimerManager.ios.kt` also use `sharedApplication`.
3. **The render loop is gated on a `UIWindowScene` being in the foreground.** In `ComposeContainerView`, `redrawer.isActive = foregroundStateListener?.isSceneInForeground ?: false`. If the keyboard's hosted window has no foreground `UIWindowScene`, Compose will not draw. Whether it has one is *(unverified)*.
4. **Memory is the killer even if rendering works.** CMP keeps three BGRA8 Metal drawables at view size. That is about 4.5 MiB on an SE-class iPhone, about 15 MiB on a Pro Max, and about 26 MiB on a 13-inch iPad in landscape *(estimate)*. On top of that come Skia caches, the Compose runtime and the Kotlin/Native heap. Reported CMP apps use about 2x the persistent memory of equivalent Swift apps *(reported)*. This does not fit a 48 to 70 MB ceiling that must also hold dictionaries and language models. Flutter, which has a similar self-rendering architecture, advises building extension UI only when the extension "supports at least 100MB of memory".
5. **No public production example exists.** I found no shipped or open-source CMP (or Flutter or React Native) keyboard extension.

**Recommendation:**
- Use Compose everywhere except the iOS keyboard surface. That covers the Android IME, the Android and iOS container apps (settings, onboarding, theme editor, dictionary management), and previews.
- Draw the iOS keyboard surface with a thin native renderer. I recommend UIKit/Core Animation for the key grid, with SwiftUI optional for secondary panels. It is driven by a shared Kotlin engine that also owns layout geometry, hit-testing, the gesture state machine and theme tokens. Export the engine with SKIE (0.10.15 supports Kotlin 2.4.20) or KMP-NativeCoroutines.
- Run the de-risking spike in §6.7 once. Switch the iOS keyboard to CMP only if it passes hard memory and latency gates.

### Five things the architecture must account for from day one

1. **A host-agnostic editing engine with a shadow document.** Keep a local mirror of the text around the cursor, the selection and the composing region in `commonMain`. Talk to the host through a narrow `TextHost` port with capability flags. On Android, mutations are async and getters are slow blocking IPC. On iOS, context is truncated, there is no absolute selection, and marked-text semantics differ. The engine must reconcile when the host reports external changes (`onUpdateSelection` on Android, `textDidChange` on iOS).
2. **Two processes, two trust levels and one-way data flow.**
   - The container app and the keyboard are separate processes. On iOS without Full Access, the keyboard can only read the App Group container.
   - Design settings to flow one way: the app writes, the keyboard reads.
   - Keep learned data (user dictionary, n-gram history) keyboard-local by default. Sync it only when Full Access is granted and the user has consented.
   - Basic typing must work fully offline and without Full Access (Guideline 4.4.1).
   - Honour no-learning signals: Android `IME_FLAG_NO_PERSONALIZED_LEARNING`, password input types, and iOS `autocorrectionType = .no`.
3. **A memory-lean engine.**
   - Store dictionaries and language models in memory-mapped binary files, outside the Kotlin/Native heap. Clean mapped pages are nearly free against jetsam. One report cut a keyboard's baseline from 52 to 27 MB this way.
   - Bound every cache, tune K/N GC (`GC.autotune` or `maxHeapBytes`, `latin1Strings`), and collect on memory warnings or when the keyboard hides.
   - Measure `phys_footprint` on real devices in CI.
   - Budget about 30 MB total for the iOS keyboard.
4. **A shared render model with per-target renderers.**
   - Kotlin computes a platform-neutral `KeyboardRenderModel`: key rectangles, labels, states, popups and theme tokens. It also does hit-testing and gesture recognition.
   - Renderers are Compose (Android IME and container apps) and native UIKit (iOS extension).
   - Put popups and key previews inside the keyboard bounds, because iOS cannot draw above its top edge.
   - Keep the iOS globe key a native `UIControl`, because `handleInputModeList(from:with:)` needs a real `UIView` and `UIEvent`.
5. **Separate, extension-safe Kotlin binaries.**
   - Publish a dedicated umbrella framework for the iOS extension: engine only, no Compose, static. Enforce by lint that it never touches `UIApplication`.
   - Give the container app its own umbrella framework (engine + CMP).
   - Each Mach-O binary must link exactly one Kotlin/Native framework, to avoid duplicated runtimes. The app extension must not embed nested frameworks.
   - Declare required-reason APIs in the privacy manifests (`mach_absolute_time` 35F9.1 and `stat`/`fstat` 0A2A.1 per JetBrains, plus `UserDefaults`).

---

## 2. Android

### 2.1 Packaging and declaration *(verified)*

Declare the IME in the manifest:

```xml
<service android:name=".MaKeebImeService"
    android:permission="android.permission.BIND_INPUT_METHOD"
    android:directBootAware="true"            <!-- optional, API 24+, see §2.10 -->
    android:exported="true">
  <intent-filter><action android:name="android.view.InputMethod"/></intent-filter>
  <meta-data android:name="android.view.im" android:resource="@xml/method"/>
</service>
```

**`res/xml/method.xml` (`<input-method>`) attributes.** API levels come from `api-versions.xml` in the Android 17 SDK.

| Attribute | API | Notes |
|---|---|---|
| `settingsActivity` | 3 | Settings entry point. |
| `supportsSwitchingToNextInputMethod` | 19 | Required for the globe-key flow. |
| `supportsInlineSuggestions` | 30 | Autofill inline suggestions. |
| `supportsInlineSuggestionsWithTouchExploration` | 33 | |
| `showInInputMethodPicker` | 31 | |
| `suppressesSpellChecker` | 31 | Useful if MaKeeb underlines errors itself. |
| `supportsStylusHandwriting` | 33 | |
| `stylusHandwritingSettingsActivity` | 34 | |
| `supportsConnectionlessStylusHandwriting` | 35 | |
| `languageSettingsActivity` | 36 | Exposed via `InputMethodInfo.createImeLanguageSettingsActivityIntent()` (36). |

**`<subtype>` attributes**

| Attribute | API |
|---|---|
| `imeSubtypeLocale`, `imeSubtypeMode` (`keyboard`, `voice`, `handwriting`), `imeSubtypeExtraValue` | 11 |
| `isAuxiliary`, `overridesImplicitlyEnabledSubtype` | 14 |
| `subtypeId` | 17 |
| `isAsciiCapable` | 19 |
| `languageTag` | 24 |
| `physicalKeyboardHintLanguageTag`, `physicalKeyboardHintLayoutType` | 34 |
| `layoutLabel` | 36 |
| `shortLabel` | 37 |

Subtypes can also be registered at runtime (`InputMethodManager.setAdditionalInputMethodSubtypes`) and enabled explicitly (`setExplicitlyEnabledInputMethodSubtypes`, API 34).

### 2.2 `InputMethodService` lifecycle *(verified against Android 17 sources and javadoc)*

| Callback | When | What MaKeeb should do |
|---|---|---|
| `onCreate()` | The service is created, once per process lifetime (usually long-lived). | Create the engine singleton lazily. Start async dictionary mapping. Set up Lifecycle, SavedState and ViewModel owners (§2.3). |
| `onInitializeInterface()` | Before any UI is created, both after create and after a configuration change. | Rebuild layout metrics (orientation, density, split or foldable). |
| `onCreateInputView()` | "Called once, when the input area is first displayed." It is recreated after configuration changes. | Return a `ComposeView`, or, as FlorisBoard does, return `null` and add your root to `android.R.id.content` for full-window control. |
| `onCreateCandidatesView()` | The first time candidates are shown. | Return `null`. Render suggestions inside the input view; the legacy candidates window is not worth it. |
| `onStartInput(EditorInfo, restarting)` | A new editor is bound, even when the IME is not visible (for example with a hardware keyboard). | Reset the shadow document from `EditorInfo.initialSel*` and `getInitialTextBeforeCursor` (API 30+). |
| `onStartInputView(EditorInfo, restarting)` | The input view is shown for that editor. | Choose the layout from `inputType`, `imeOptions` and `hintLocales`. Call `requestCursorUpdates(...)`. |
| `onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)` | Any selection change, whether caused by the IME, the app or the user. | Reconcile the shadow document. If the change is unexpected, drop the composing state. |
| `onUpdateCursorAnchorInfo(CursorAnchorInfo)` (21) | After `requestCursorUpdates`. | Get caret and character geometry, for example to anchor floating UI. API 33/34 filters narrow what is sent. |
| `onFinishInputView(finishing)` / `onFinishInput()` | The view hides or the editor unbinds. | Commit or finish composing, flush learning (unless no-learning), stop cursor updates. |
| `onWindowShown()` / `onWindowHidden()` | IME window visibility. | Drive Lifecycle RESUME/PAUSE. Docs: "Release large memory allocations immediately after the input method window is hidden." |
| `onComputeInsets(Insets)` | Layout and insets pass. | Set `contentTopInsets`, `visibleTopInsets`, and `touchableInsets` / `touchableRegion`. This lets the window be taller than the keyboard (previews drawn above it) while touches pass through. Not called while the extract view is shown. |
| `onEvaluateFullscreenMode()` | Default: fullscreen (extract UI) only in landscape. | Usually return `false`, and respect `IME_FLAG_NO_FULLSCREEN` / `IME_FLAG_NO_EXTRACT_UI`. |
| `onEvaluateInputViewShown()` | Whether to show the soft input, for example when a hardware keyboard is present. | Make it user-configurable. |
| `onCurrentInputMethodSubtypeChanged(subtype)` (11) | Subtype switch. | Swap layout and language. |
| `onCreateInlineSuggestionsRequest(Bundle)` / `onInlineSuggestionsResponse(...)` (30) | Autofill inline chips. | Show them in the suggestion strip. |
| `onKeyDown` / `onKeyUp` | Hardware keys. | Physical-keyboard support. `onShouldVerifyKeyEvent` (36) marks sensitive shortcuts for HMAC verification. |

### 2.3 Hosting Jetpack Compose inside the IME *(verified: FlorisBoard source)*

The IME window is a `Dialog`-style window, not an Activity, so Compose cannot find `ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner` or `ViewTreeSavedStateRegistryOwner`. FlorisBoard's `LifecycleInputMethodService` makes the service itself the owner and installs it on the window's decor view:

```kotlin
open class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
  private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
  private val store by lazy { ViewModelStore() }
  private val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
  override val lifecycle get() = lifecycleRegistry
  override val viewModelStore get() = store
  override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

  override fun onCreate() { super.onCreate()
    savedStateRegistryController.performRestore(null)
    lifecycleRegistry.handleLifecycleEvent(ON_CREATE); lifecycleRegistry.handleLifecycleEvent(ON_START) }
  fun installViewTreeOwners() { val decor = window!!.window!!.decorView
    decor.setViewTreeLifecycleOwner(this); decor.setViewTreeViewModelStoreOwner(this)
    decor.setViewTreeSavedStateRegistryOwner(this) }
  override fun onWindowShown() { super.onWindowShown(); lifecycleRegistry.handleLifecycleEvent(ON_RESUME) }
  override fun onWindowHidden() { super.onWindowHidden(); lifecycleRegistry.handleLifecycleEvent(ON_PAUSE) }
  override fun onDestroy() { super.onDestroy(); lifecycleRegistry.handleLifecycleEvent(ON_STOP); lifecycleRegistry.handleLifecycleEvent(ON_DESTROY) }
}
```

`FlorisImeService` then does three things:
- In `onCreate` it calls `WindowCompat.setDecorFitsSystemWindows(window.window!!, false)`, making the IME edge-to-edge and letting it handle nav-bar insets itself.
- In `onCreateInputView` it calls `installViewTreeOwners()`, adds its own root view to `android.R.id.content`, and returns `null`.
- In `onComputeInsets` it computes insets and a touchable region from its layout state.

The same `installViewTreeOwners()` is also called in `onCreateExtractTextView()`.

Practical notes:
- Avoid Compose `Popup` and `Dialog` inside the IME. Draw key previews, long-press menus and panels inside the IME's own full-height window, and limit touches with `touchableRegion`. *(This is a recommendation; FlorisBoard renders overlays in-tree.)*
- The same "draw inside your own bounds" rule is mandatory on iOS (§3.6). Design popups so they fit in both worlds, for example with an extra top row or a bounded preview area.

### 2.4 Window, insets, navigation bar and the IME switcher

- **Edge-to-edge.** Android 15 made edge-to-edge the default for apps targeting 35. Android 16 removes the opt-out for apps targeting 36 (`windowOptOutEdgeToEdgeEnforcement` is disabled). The IME must pad for `navigationBars` insets itself *(verified: behavior-changes pages; the IME-specific impact is inferred)*.
- **IME switcher button.**
  - The system shows an IME-switcher (keyboard or globe) button in the navigation-bar area when several IMEs are enabled.
  - Android 15 QPR1 redesigned it: a globe icon, tap to switch to the next IME, long-press for the picker *(reported: PhoneArena)*.
  - API 36 adds `InputMethodService.onCustomImeSwitcherButtonRequestedVisible(boolean)`. When the IME hides the system nav bar (`getWindowInsetsController().hide(captionBar())`), the system asks the IME to draw its own switcher button *(verified: javadoc)*.
- **Back handling.** Predictive back is default for apps targeting 36. Use `OnBackInvokedCallback` for in-IME panels, such as closing the emoji panel.

### 2.5 `InputConnection` surface *(verified: API levels from api-versions.xml)*

| Method | API | Notes |
|---|---|---|
| `commitText(text, newCursorPosition)` | 3 | The `TextAttribute` overload is 33. |
| `setComposingText` / `setComposingRegion(start, end)` / `finishComposingText()` | 3 / 9 / 3 | `TextAttribute` overloads are 33. `TextAttribute.Builder.setTextSuggestionSelected()` is 37 (CJK screen-reader feedback). |
| `deleteSurroundingText(before, after)` / `deleteSurroundingTextInCodePoints` | 3 / 24 | Prefer the code-point variant (emoji, surrogates). |
| `getTextBeforeCursor(n, flags)` / `getTextAfterCursor` / `getSelectedText` | 3 / 3 / 9 | **Synchronous IPC with a 2000 ms timeout** in AOSP `RemoteInputConnection`. Never call these on the UI hot path per key; use the shadow document. |
| `getSurroundingText(before, after, flags)` | 31 | Returns `SurroundingText`: text plus selection plus offset in one call. |
| `getExtractedText(request, flags)` | 3 | `GET_EXTRACTED_TEXT_MONITOR` streams updates. Mostly for extract (fullscreen) mode. |
| `setSelection(start, end)` | 3 | Absolute offsets. iOS has no equivalent. |
| `sendKeyEvent(KeyEvent)` | 3 | Needed for DPAD cursor keys, DEL in some editors, and Enter when there is no action. |
| `performEditorAction(actionId)` | 3 | IME_ACTION_GO, SEARCH, SEND, NEXT, DONE, PREVIOUS. |
| `performContextMenuAction(id)` | 3 | `android.R.id.selectAll`, `cut`, `copy`, `paste`, `pasteAsPlainText`, `undo`, `redo` *(ids beyond cut, copy, paste and selectAll are unverified per editor)*. |
| `beginBatchEdit()` / `endBatchEdit()` | 3 | Wrap multi-step edits such as autocorrect replacement. |
| `commitCorrection(CorrectionInfo)` | 11 | Lets the editor flash the corrected span. |
| `commitContent(InputContentInfo, flags, opts)` | 25 | Rich content (§2.7). |
| `requestCursorUpdates(mode)` / `(mode, filter)` | 21 / 33 | Filters: CHARACTER_BOUNDS, EDITOR_BOUNDS, INSERTION_MARKER (33), TEXT_APPEARANCE, VISIBLE_LINE_BOUNDS (34). |
| `performSpellCheck()` / `setImeConsumesInput(bool)` | 31 | The latter is for in-IME text fields such as emoji search: it hides the app's cursor. |
| `takeSnapshot()` | 33 | `TextSnapshot` of text, selection and composing region. |
| `replaceText(start, end, text, newCursor, attr)` | 34 | Atomic replace; good for autocorrect. |
| `performHandwritingGesture(...)` / `previewHandwritingGesture(...)` / `requestTextBoundsInfo(...)` | 34 | Stylus gestures: `SelectGesture`, `DeleteGesture`, `InsertGesture`, `RemoveSpaceGesture`, `JoinOrSplitGesture`, `SelectRangeGesture`, `DeleteRangeGesture`, `InsertModeGesture` (all API 34). |
| `closeConnection()` / `getHandler()` | 24 | |

Guidance: model the host as eventually consistent. LatinIME-style IMEs keep a local `RichInputConnection` cache and resync from `onUpdateSelection`. MaKeeb's common engine should do the same, so the iOS adapter can share it.

### 2.6 `EditorInfo` *(verified)*

- **`inputType`** (`android.text.InputType`):
  - Classes: `TYPE_CLASS_TEXT`, `TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`.
  - Text variations: `URI`, `EMAIL_ADDRESS`, `PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `PERSON_NAME`, `SHORT_MESSAGE`, `WEB_EDIT_TEXT`, `FILTER` and so on.
  - Number variation `NUMBER_VARIATION_PASSWORD`.
  - Flags: `CAP_CHARACTERS`, `CAP_WORDS`, `CAP_SENTENCES`, `AUTO_CORRECT`, `AUTO_COMPLETE`, `MULTI_LINE`, `IME_MULTI_LINE`, `NO_SUGGESTIONS`; number `SIGNED` and `DECIMAL`.
  - Docs: "Hide the password in your UI … Don't store passwords on a device."
- **`imeOptions`**:
  - Actions (`IME_MASK_ACTION`): `NONE`, `GO`, `SEARCH`, `SEND`, `NEXT`, `DONE`, `PREVIOUS` (11), `UNSPECIFIED`.
  - Flags: `IME_FLAG_NO_EXTRACT_UI`, `NO_ACCESSORY_ACTION`, `NO_ENTER_ACTION` (3); `NO_FULLSCREEN`, `NAVIGATE_NEXT`, `NAVIGATE_PREVIOUS` (11); `FORCE_ASCII` (16); **`IME_FLAG_NO_PERSONALIZED_LEARNING` (26)**. The javadoc says apps use it for incognito modes and "the flag is not a guarantee", but MaKeeb should honour it.
  - `actionLabel` and `actionId` give custom action labels.
- **Other fields:**
  - `hintLocales` (24): a language hint per field.
  - `contentMimeTypes` (25): rich-content acceptance.
  - `packageName`: the host app, useful for per-app tweaks.
  - `privateImeOptions`, `extras`, `hintText`, `label`, `fieldId`.
  - `initialSelStart`, `initialSelEnd`, `initialCapsMode`.
- **Initial surrounding text** (30/31):
  - `getInitialTextBeforeCursor`, `getInitialSelectedText`, `getInitialTextAfterCursor` (30) and `getInitialSurroundingText` (31).
  - The system may trim it; internal `MEMORY_EFFICIENT_TEXT_LENGTH = 2048`.
  - It removes IPC round-trips at session start.
- **Newer flags:**
  - `isStylusHandwritingEnabled` (35).
  - `isWritingToolsEnabled` (36). Editors set it `false` to opt out of IMEs replacing text "with generative AI text". Honour it before any AI rewrite feature.
  - `getAutofillId` (36).
  - `getSupportedHandwritingGestures` and `getInitialToolType` (34).

### 2.7 Rich content (images, GIFs, stickers) *(verified)*

1. The IME reads `EditorInfo.contentMimeTypes` (or `EditorInfoCompat.getContentMimeTypes`).
2. It builds an `InputContentInfo(Compat)` with a `content://` URI (FileProvider), a `ClipDescription` and an optional link URI.
3. It calls `InputConnection(Compat).commitContent(..., INPUT_CONTENT_GRANT_READ_URI_PERMISSION, ...)` (API 25).

Apps receive it through `OnReceiveContentListener`. Fall back to sharing a link or text when the MIME type is not accepted.

### 2.8 Switching IMEs and subtypes *(verified)*

- **Service-side (API 28):**
  - `shouldOfferSwitchingToNextInputMethod()` tells you whether to show the globe key.
  - `switchToNextInputMethod(onlyCurrentIme)`: pass `false` for globe behaviour.
  - `switchToPreviousInputMethod()`, `switchInputMethod(id)` (3) and `switchInputMethod(id, subtype)` (28).
  - The older `InputMethodManager` variants were deprecated in 28.
- **Picker:** `InputMethodManager.showInputMethodPicker()`, typically on globe long-press. `showInputMethodAndSubtypeEnabler(imiId)` opens the language enabler.
- **Onboarding (container app):**
  - Open `Settings.ACTION_INPUT_METHOD_SETTINGS`.
  - Check enabled state with `InputMethodManager.getEnabledInputMethodList()`.
  - Check the current IME with `Settings.Secure.DEFAULT_INPUT_METHOD` or `InputMethodManager.getCurrentInputMethodInfo()` (34).
  - Then call `showInputMethodPicker()`.
- **User warning:** enabling any third-party IME makes the system show a warning that it may collect all text you type. Plan onboarding copy around it.

### 2.9 Spell checker, user dictionary and autofill

- **`SpellCheckerService` (API 14)** is a separate service that the IME app can also ship. It needs `BIND_TEXT_SERVICE` and `android.service.textservice.SpellCheckerService` metadata. It serves spell suggestions to all apps through `Session.onGetSuggestionsMultiple` and `onGetSentenceSuggestionsMultiple`. It is optional for v1. It can reuse the same Kotlin engine.
- **`UserDictionary.Words`:** "Starting on API 23, the user dictionary is only accessible through IME and spellchecker." MaKeeb can import from and add to it (`addWord(context, word, freq, shortcut, locale)`, API 16). Keep MaKeeb's own learned store as the source of truth.
- **Autofill inline suggestions (API 30):** declare `supportsInlineSuggestions`. Build an `InlineSuggestionsRequest` with `InlinePresentationSpec` and an androidx.autofill style bundle. Render `InlineSuggestion.inflate(...)` views in the strip. This only works when the user's autofill service supports inline mode.

### 2.10 Direct boot

- Mark the service `android:directBootAware="true"` (API 24) so the keyboard works on the lock screen before first unlock.
- Until the user unlocks, only device-protected storage (`createDeviceProtectedStorageContext()`, 24) is available.
- Ship a DE-safe minimal mode: default layout, bundled dictionary, no learned data. Switch to credential-encrypted storage after `ACTION_USER_UNLOCKED`.

### 2.11 Clipboard *(verified)*

- **Android 10+:** "Unless your app is the default IME or is the app that currently has focus, your app cannot access clipboard data."
- **Android 12+:** `getPrimaryClip()` shows a "pasted from your clipboard" toast. AOSP `ClipboardService` excludes the default IME: `// Exclude special cases: IME, ContentCapture, Autofill. if (isDefaultIme(...)) return;`.
- **Android 13+:** respect `ClipDescription.EXTRA_IS_SENSITIVE` (33). Never show sensitive clips in a clipboard history UI, and do not persist them.
- `EXTRA_IS_REMOTE_DEVICE` (34) marks clips that came from another device.

### 2.12 Haptics and sound *(verified API levels)*

- **Haptics:**
  - `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP | KEYBOARD_PRESS | KEYBOARD_RELEASE)` (8/27/27). `performHapticFeedback(HapticFeedbackRequest)` is 36.1.
  - `FLAG_IGNORE_GLOBAL_SETTING` and `Settings.System.HAPTIC_FEEDBACK_ENABLED` were deprecated in 33, so haptics follow the system setting.
  - Custom strength needs `Vibrator.vibrate(VibrationEffect, VibrationAttributes)` (33) and the normal `VIBRATE` permission. API 36 adds envelope effects.
- **Sound:** `AudioManager.playSoundEffect(FX_KEYPRESS_STANDARD | FX_KEYPRESS_SPACEBAR | FX_KEYPRESS_DELETE | FX_KEYPRESS_RETURN, volume)` (3). The alternative is a `SoundPool`.
- **Android 17 risk:** "background audio hardening" silences `AudioTrack` playback and makes audio focus fail when an app has no visible activity or foreground service. It applies to all apps on Android 17. IMEs are not mentioned. A visible IME is bound with `BIND_TREAT_LIKE_ACTIVITY | BIND_FOREGROUND_SERVICE | BIND_INCLUDE_CAPABILITIES` (AOSP), but whether that counts is *(unverified)*. Test with `adb shell cmd audio set-enable-hardening throw`. Prefer `playSoundEffect`, which the system audio service plays.

### 2.13 Voice input

- **In-IME recognition.** Use `SpeechRecognizer.createSpeechRecognizer(ctx)` (8) or `createOnDeviceSpeechRecognizer(ctx)` / `isOnDeviceRecognitionAvailable` (31). This needs `RECORD_AUDIO`, which must be granted from an Activity in the container app because a service cannot show the prompt. A visible IME is bound with foreground-service and include-capabilities flags, so mic access while the keyboard is visible is feasible (AOSP bind flags; FUTO-style IMEs do this).
- **Handing off to a voice IME.** Find a subtype with mode `voice` via `InputMethodManager.getShortcutInputMethodsAndSubtypes()` (11), then call `switchInputMethod(id, subtype)` (28).
- **Android 15:** apps targeting 35 must be the top app or run a foreground service to request audio focus. Recording does not require focus, but some recognizers request it *(unverified per engine)*.

### 2.14 Stylus handwriting (Android 13+) *(verified API levels)*

- **API 33:** `onPrepareStylusHandwriting`, `onStartStylusHandwriting`, `onStylusHandwritingMotionEvent`, `onFinishStylusHandwriting`, `getStylusHandwritingWindow`.
- **API 34:** handwriting gestures on `InputConnection` (§2.5), `onUpdateEditorToolType`, and session timeouts.
- **API 35:** connectionless handwriting (`onStartConnectionlessStylusHandwriting` and `finishConnectionlessStylusHandwriting(CharSequence)`).
- **API 36:** `setStylusHandwritingRegion(Region)`.

This is out of scope for v1, but the `TextHost` port should not preclude gesture-based edits.

### 2.15 IME-relevant changes in Android 15, 16 and 17

| Version | Change | Source |
|---|---|---|
| **15 (API 35)** | Connectionless stylus handwriting APIs; `EditorInfo.set/isStylusHandwritingEnabled`; `InputMethod.SHOW_FORCED` deprecated | api-versions.xml |
| 15 | Edge-to-edge default for target 35; `Configuration` no longer excludes system bars; audio-focus requests need top app or FGS | behavior-changes-15 |
| 15 QPR1 | Redesigned IME switcher (globe; tap to cycle, long-press for picker) | PhoneArena *(reported)* |
| **16 (API 36)** | `onCustomImeSwitcherButtonRequestedVisible(boolean)`; `onShouldVerifyKeyEvent(KeyEvent)` (HMAC-verify sensitive key events); `setStylusHandwritingRegion`; `EditorInfo.isWritingToolsEnabled/setWritingToolsEnabled`; `EditorInfo.get/setAutofillId`; `InputMethodInfo.createImeLanguageSettingsActivityIntent` + `languageSettingsActivity`; `InputMethodSubtype` layout labels; `InputMethodManager.showSoftInput(View,int,ResultReceiver)` and `hideSoftInputFromWindow(...,ResultReceiver)` deprecated (app side) | api-versions.xml, javadoc |
| 16 | Edge-to-edge opt-out disabled for target 36; predictive back on by default for target 36; accessibility announcements (`announceForAccessibility`) deprecated, which affects IME TalkBack feedback design | behavior-changes-16 |
| 16.1 (36.1) | `View.performHapticFeedback(HapticFeedbackRequest)`; `ViewConfiguration.get*TimeoutMillis()` | api-versions.xml |
| **17 (API 37)** | `InputMethodSubtype.getSubtypeShortLabel` / `Builder.setSubtypeShortLabel` + `shortLabel` attribute; `TextAttribute.isTextSuggestionSelected` / `setTextSuggestionSelected`; `InputMethodManager.SHOW_IMPLICIT`, `HIDE_IMPLICIT_ONLY`, `HIDE_NOT_ALWAYS` and `InputMethod.SHOW_EXPLICIT` deprecated | api-versions.xml |
| 17 | IME visibility not restored after rotation when the app does not handle configuration changes (app-side); password characters hidden when typing on physical keyboards for target 37 apps; **background audio hardening** (see §2.12); `ACCESS_LOCAL_NETWORK` runtime permission for target 37 | behavior-changes-17, bg-audio |
| Play | New apps and updates must target **API 36 from 31 Aug 2026** (extension possible to 1 Nov 2026) | Play target-SDK page |

### 2.16 Play Store policy points for keyboards

Google Play has no IME-specific policy section that I could verify. The general **User Data** policy applies fully:
- A privacy policy is required.
- Prominent in-app disclosure plus affirmative consent is required before collecting or transmitting personal or sensitive data "not within the reasonable expectation of the user". Keystrokes, typed text, clipboard contents and audio clearly qualify.
- Selling personal or sensitive data is forbidden.
- The **Data safety** form must match actual behaviour.

Implications:
- Keep typing on-device by default.
- Make any cloud features (sync, AI rewrite, cloud voice) explicit opt-ins with a disclosure screen.
- Never learn from or transmit password fields or no-learning fields.
- Request `RECORD_AUDIO` only when the user starts voice input.
- Do not use Accessibility APIs as a keyboard workaround.
- `INTERNET` is an app-wide permission; declaring it is allowed, but the Data safety answers must reflect what the IME process actually does.

---

## 3. iOS

### 3.1 Extension structure and Info.plist *(verified)*

- The target type is a Custom Keyboard Extension, with `NSExtensionPointIdentifier = com.apple.keyboard-service` and a principal class that subclasses `UIInputViewController`.
- `NSExtensionAttributes`:
  - `IsASCIICapable`
  - `PrefersRightToLeft`
  - `PrimaryLanguage` (shown in Settings)
  - `RequestsOpenAccess`. Apple: "Set this to `true` if your keyboard needs access to network resources, to write to a shared group container, or other capabilities."
- Apple's current doc warns:
  - "The memory limits vary from model to model."
  - "Be sure to handle low memory notifications."
  - "Dismissing the keyboard won't necessarily terminate the keyboard extension process."
- The App Extension guide says to aim for launch "well under one second. An extension that launches too slowly is terminated by the system."

### 3.2 `UIInputViewController` surface *(verified; availability from Apple docs JSON)*

| Member | iOS | Notes |
|---|---|---|
| `inputView: UIInputView?` | 8 | Root view. `UIInputView(frame:inputViewStyle:)` with `.default` or `.keyboard`. `allowsSelfSizing` (9) enables Auto Layout self-sizing. |
| `textDocumentProxy` | 8 | See §3.3. |
| `advanceToNextInputMode()` | 8 | Mandatory switching. There is "no API to obtain a list of enabled keyboards". |
| `handleInputModeList(from: UIView, with: UIEvent)` | 10 | Attach to the globe control (`.allTouchEvents`) to get the system long-press keyboard list. It needs a real UIKit view and event. |
| `needsInputModeSwitchKey` | 11 | `false` on devices where the system draws a globe below the keyboard (Face ID iPhones). |
| `hasFullAccess` | 11 | Runtime Full Access check. |
| `hasDictationKey` | 8 | If `true`, iOS hides the system dictation mic. |
| `primaryLanguage` | 8 | Can be changed at runtime per layout. |
| `dismissKeyboard()` | 8 | |
| `requestSupplementaryLexicon(completion:)` | 8 | Returns a `UILexicon` (§3.7). |
| `UITextInputDelegate` conformance | 8 | `textWillChange`, `textDidChange`, `selectionWillChange`, `selectionDidChange`. The `textInput` argument is always `nil`. |

- **Height.** Set it with a height constraint on the controller's view. The width is always system-controlled. Avoid a `.required` priority, so the constraint does not fight the system constraint; this is common practice *(reported)*.
- **Layout on iOS 26.** Liquid Glass wraps keyboards in hosting chrome. There are reports of extra margins in first-party apps (Messages, Notes, Safari) that the keyboard cannot draw over, a forced glass bottom area, and a gray bar above the keyboard in betas *(reported: Apple forums 800838 and 793686, KeyboardKit)*.

### 3.3 `UITextDocumentProxy` surface *(verified)*

| Member | iOS | Notes |
|---|---|---|
| `insertText(_:)`, `deleteBackward()`, `hasText` | 8 (`UIKeyInput`) | `deleteBackward` granularity is decided by the host (typically one composed character) *(unverified per host)*. |
| `adjustTextPosition(byCharacterOffset:)` | 8 | The only caret control: relative moves only, with no absolute selection or select-range. |
| `documentContextBeforeInput` / `documentContextAfterInput` | 8 | **Truncated and undocumented.** Reports include "last two sentences" and "around 300 characters". Some hosts return `nil` until edited. Treat it as partial and eventually consistent. |
| `selectedText` | 11 | |
| `documentIdentifier: UUID` | 11 | Detects a switch to a new document or field; reset the engine state on change. |
| `documentInputMode: UITextInputMode?` | 10 | The field's preferred language, when the app sets one. |
| `setMarkedText(_:selectedRange:)`, `unmarkText()` | 13 | Composition. Host rendering varies; many keyboards avoid marked text for Latin scripts. |
| Traits (`UITextInputTraits`) | varies | `keyboardType`, `returnKeyType`, `enablesReturnKeyAutomatically`, `autocapitalizationType`, `autocorrectionType`, `spellCheckingType`, `smartQuotesType`/`smartDashesType`/`smartInsertDeleteType` (11), `textContentType` (10), `keyboardAppearance`, `isSecureTextEntry`, `passwordRules`, `inlinePredictionType` (17), `writingToolsBehavior` (18), `mathExpressionCompletionType` (18), `allowsNumberPadPopover` (26), `grammarCheckingType`, `conversationContext` (18.4). |

**`conversationContext` (iOS 18.4)** *(partly unverified)*
- `UITextInputTraits.conversationContext: UIConversationContext?` exists. It has subclasses `UIMessageConversationContext` and `UIMailConversationContext`, with `entries`, `participantNameByIdentifier`, `selfIdentifiers` and `threadIdentifier`. `UITextInputDelegate.conversationContext(_:didChange:)` exists too.
- Apple documents it for apps adopting Smart Reply: "The keyboard uses this context once per session for initialization."
- `UITextDocumentProxy` inherits the traits through `UIKeyInput`, and `UIInputViewController` is a `UITextInputDelegate`. I found **no documentation or report confirming that third-party keyboards receive a populated context**.
- Treat it as unverified. It is a nice-to-have for smart replies. Probe it in the spike.

**Hard behaviours** (App Extension Programming Guide):
- The keyboard "cannot select text".
- It "cannot offer inline autocorrection controls near the insertion point".
- It is "not possible to display key artwork above the top edge of a custom keyboard's primary view".
- It "does not have access to most of the general keyboard settings" (auto-capitalisation, caps lock and so on), so MaKeeb needs its own settings.

### 3.4 Full Access: exactly what changes *(verified: Apple's "Configuring open access" doc)*

**Without Full Access** (the default, or when the user disallows it). Apple lists:
- "Ability to perform all the normal duties expected of a basic keyboard"
- "Access to a common words lexicon for autocorrect and text suggestion"
- "Access to the text shortcuts list in Settings"
- "No access to the file system apart from the keyboard's own sandbox container, and **read-only access to the containing app's shared containers**"
- "No access to microphone and speaker"
- "No ability to participate directly or indirectly in iCloud, Game Center, or In-App Purchase"

**With Full Access** (`RequestsOpenAccess = true` in the plist and the user enabled "Allow Full Access"). Apple lists:
- Everything above, plus:
- "Location Services and Contacts, with user permission"
- "The keyboard and containing app can employ a shared container" (read and write)
- "Send keystrokes and other input events for server-side processing" (network)
- "iCloud" for settings and lexicon
- Game Center and In-App Purchase "through the containing app"
- MDM with managed apps

The archive guide adds:
- "Ability to play audio, including keyboard clicks using the `playInputClick` method"
- "Ability to use the `UIPasteboard` class"

| Capability | Without Full Access | With Full Access | Evidence |
|---|---|---|---|
| Typing, delete, caret move, marked text | Yes | Yes | Apple |
| `UILexicon`, text shortcuts | Yes | Yes | Apple |
| Own sandbox container (read/write) | Yes | Yes | Apple |
| App Group container | **Read-only** | Read/write | Apple. One developer reports writes "fail silently" without Full Access *(reported)*. |
| `UserDefaults(suiteName:)` writes from the keyboard | No (same as above) | Yes | Inferred from the above |
| Network | No | Yes | Apple |
| `UIPasteboard` | No | Yes | Apple archive |
| Audio and key clicks (`playInputClick`, `AudioServicesPlaySystemSound`) | No, per Apple ("no access to … speaker") | Yes | Apple. Some tutorials claim system sounds work without it *(conflicting; test)*. |
| Haptics (`UIImpactFeedbackGenerator`) | No *(reported by KeyboardKit)* | Yes | KeyboardKit |
| Location, Contacts | No | With user permission | Apple |
| Microphone | **No** | **No** (extensions never get the mic) | Apple |
| Keychain sharing (access group) | *(unverified)* | *(unverified)* | Not found in keyboard docs |
| iCloud | No | Yes | Apple |

### 3.5 App Store Review Guideline 4.4 and 4.4.1 *(verified, verbatim)*

4.4: extensions "should include some functionality, such as help screens and settings interfaces where possible". Apps must "clearly and accurately disclose what extensions are made available in the app's marketing text, and the extensions may not include marketing, advertising, or in-app purchases."

4.4.1 says keyboard extensions **must**:
- "Provide keyboard input functionality (e.g. typed characters)"
- "Follow Sticker guidelines if the keyboard includes images or emoji"
- "Provide a method for progressing to the next keyboard"
- "Remain functional without full network access and without requiring full access"
- "Collect user activity only to enhance the functionality of the user's keyboard extension on the iOS device"

They **must not**:
- "Launch other apps besides Settings"
- "Repurpose keyboard buttons for other behaviors (e.g. holding down the 'return' key to launch the camera)"

### 3.6 Hard limits

**Memory.** Apple publishes no number, only that it "varies from model to model". Best available evidence:

| Figure | Source | Notes |
|---|---|---|
| ~60 MB `phys_footprint` ceiling. A "fresh keyboard read 63MB"; baseline cut **52 → 27 MB** by moving a dictionary from parsed literals to a memory-mapped file. Leaked `UIInputViewController` views retain about 3 MB per host-app cycle. | dev.to "three hard constraints" (Kibo keyboard, Aug 2026; year inferred) | Measured with `task_vm_info`, the same metric jetsam uses. Most recent and most concrete. |
| "~60-70 MB, based on the device and OS" | KeyboardKit FAQ | Vendor of the most-used keyboard SDK. |
| 48 MB | react-native #31910 (2021) | The React Native keyboard exceeded it. |
| 30 MB | Apple forums 105815 (about 2018) | Old. |

Planning number: design for **≤ 30 MB steady state and ≤ 40 MB peak** on the oldest supported device, which leaves margin under a ceiling that may be as low as about 48 MB. Jetsam kills produce **no crash log**; the user just sees the keyboard vanish.

**Other limits:**
- **Microphone.** App extensions cannot access the microphone (Apple). Workarounds:
  1. Leave `hasDictationKey = false`, so the system dictation mic stays available below the keyboard on Face ID devices.
  2. The "relay" pattern used by Wispr Flow and KeyboardKit Pro. The keyboard opens the container app, which records (keeping itself alive in the background) and then returns the user; text flows back via the App Group. **Policy risk:** 4.4.1 says do not "launch other apps besides Settings"; keyboards have no public URL-opening API (see below); returning to the host app relied on a private host-bundle-ID lookup that broke in iOS 26.4.
- **Secure text entry and phone pads.** The system temporarily swaps in the system keyboard for `isSecureTextEntry` fields and `.phonePad` / `.namePhonePad` fields.
- **App opt-out.** `application(_:shouldAllowExtensionPointIdentifier:)` returning `false` for `UIApplication.ExtensionPointIdentifier.keyboard` (banking and HIPAA-style apps).
- **Opening URLs.** `NSExtensionContext.open(_:)` is supported only by "the Today and iMessage app extension points". Keyboards use responder-chain tricks to reach `UIApplication` and call `openURL`. That is undocumented, fragile, and in tension with 4.4.1.
- **Host app identity.** There is no public API. The widely used private XPC-based lookup "stopped working in iOS 26.4", and KeyboardKit shipped a new, review-pending approach in 10.9 *(reported)*. Do not depend on it.
- **Start-up.** Aim well under 1 s to first interactive frame; slow extensions are terminated.
- **Not controllable by the keyboard:** selection, inline autocorrect UI near the caret, the list of enabled keyboards, and the system keyboard settings.

### 3.7 Lexicon, spell checking, sound and haptics

- **`UILexicon`** (via `requestSupplementaryLexicon`) contains unpaired first and last names from Contacts, text replacements from Settings, and a common-words list. Apple positions it "as supplementary to an autocorrection/suggestion lexicon of your own design". MaKeeb must ship its own dictionaries.
- **`UITextChecker`** (iOS 3.2+) offers `rangeOfMisspelledWord`, `guesses(forWordRange:in:language:)`, `completions(forPartialWordRange:in:language:)`, `learnWord` and `availableLanguages`. It is `@MainActor`. It is not marked extension-unavailable in the docs I checked, so it is usable as a fallback. Its quality and memory use inside the extension are *(unverified)*.
- **Clicks.** Make the input view conform to `UIInputViewAudioFeedback` (`enableInputClicksWhenVisible = true`) and call `UIDevice.current.playInputClick()`. This respects the user's "Keyboard Clicks" setting. `AudioServicesPlaySystemSound` with IDs 1104, 1123, 1155 and 1156 is common but undocumented. Both require Full Access per Apple's speaker restriction *(test on device)*.
- **Haptics.** `UIImpactFeedbackGenerator` or `UISelectionFeedbackGenerator` are reported to work only with Full Access.

### 3.8 Sharing data between the container app and the extension

- **App Groups.** Share `group.<team>.makeeb` via `FileManager.containerURL(forSecurityApplicationGroupIdentifier:)` and `UserDefaults(suiteName:)`. Coordinate with SQLite, POSIX locks or `NSFileCoordinator`. If you use `NSFilePresenter`, remove presenters when the extension goes to the background (Apple).
- **Direction.** Without Full Access the keyboard can **read** but not write the group container. Settings go app → keyboard. Keyboard-learned data stays in the extension's own container until Full Access exists.
- **Keychain sharing.** Keychain access groups can share secrets such as sync tokens. Whether keyboards need Full Access for keychain access is *(unverified)*.
- **Change notification.** Use a Darwin notification (`CFNotificationCenterGetDarwinNotifyCenter`), or the keyboard re-reads the settings file in `viewWillAppear` (cheap and robust).
- **Privacy manifest.** `UserDefaults` usage in an App Group needs a required-reason code (`1C8F.1` for App Group sharing). JetBrains lists `mach_absolute_time` (35F9.1) and `stat`/`fstat` (0A2A.1) as coming from the Kotlin/Native runtime and Compose.

### 3.9 iOS 26 and 27 status *(reported unless noted)*

- **No new public keyboard-extension APIs** appear in the UIKit docs for `UIInputViewController` or `UITextDocumentProxy` beyond what is listed above *(verified by listing the doc symbols)*. New traits: `allowsNumberPadPopover` (26).
- **iOS 26.x:**
  - Liquid Glass layout and margin issues.
  - 26.4 broke the private host-bundle-ID resolution; old KeyboardKit versions crashed.
  - The `inputAccessoryView` integration change affects apps, not keyboards.
- **iOS 27.x:**
  - Random SwiftUI gesture lag in keyboards (KeyboardKit rebuilt its gesture engine in 10.9.4).
  - 27.2 beta crash from a `CALayerInvalidGeometry` NaN-bounds SwiftUI layer.
  - Both support a UIKit/Core Animation key surface with custom touch handling rather than relying on SwiftUI gestures.

---

## 4. Side-by-side capability matrix

| Feature | Android | iOS | Permission / requirement | Notes |
|---|---|---|---|---|
| Insert text | `commitText` | `insertText` | none | |
| Delete | `deleteSurroundingText(InCodePoints)`, before and after | `deleteBackward` only; forward delete via `adjustTextPosition(+1)` + `deleteBackward` | none | iOS granularity is host-defined. |
| Composition | `setComposingText` / `setComposingRegion` / `finishComposingText` | `setMarkedText` / `unmarkText` (13+) | none | Semantics differ; the engine should support a "no composition" mode. |
| Read context | `getTextBefore/AfterCursor`, `getSurroundingText` (31), initial surrounding text (30), `takeSnapshot` (33) | `documentContextBefore/AfterInput` (truncated, undocumented) | none | Keep a shadow document on both platforms. |
| Read selection | `getSelectedText` (9), selection indices in `onUpdateSelection` | `selectedText` (11); no indices | none | |
| Set selection / caret | `setSelection(abs)` | Relative `adjustTextPosition` only; no selection | none | Cursor-drag on the space bar is possible on both. |
| Atomic edits | `beginBatchEdit` / `endBatchEdit`, `replaceText` (34) | none | none | |
| Editor action (Enter) | `performEditorAction`, `imeOptions` action and label | `returnKeyType`; Enter = `insertText("\n")` | none | |
| Field type | `inputType` class, variation and flags | `keyboardType`, `textContentType`, `autocapitalizationType`, `autocorrectionType`, smart-punctuation traits | none | Map both to a common `FieldInfo`. |
| No-learning / incognito signal | `IME_FLAG_NO_PERSONALIZED_LEARNING` (26) | none; infer from `autocorrectionType = .no`, `textContentType` (e.g. `.oneTimeCode`) | none | Also add a user-facing incognito toggle. |
| Password fields | IME types into them; must not learn or display | System keyboard takes over | n/a | |
| Phone-number fields | IME handles `TYPE_CLASS_PHONE` | System keyboard takes over for `.phonePad` / `.namePhonePad` | n/a | |
| App can refuse the keyboard | no (only per-field `inputType`) | yes (`shouldAllowExtensionPointIdentifier`) | n/a | |
| Host app identity | `EditorInfo.packageName` | no public API (private trick broke in 26.4) | n/a | |
| Language hint per field | `hintLocales` (24) | `documentInputMode` (10) | none | |
| Rich content (images, GIF, stickers) | `commitContent` (25) + `contentMimeTypes` | none; only copy to pasteboard (Full Access) for the user to paste | iOS: Full Access | iOS 4.4.1 sticker rules apply. |
| Caret and character geometry | `requestCursorUpdates` → `CursorAnchorInfo` | none | none | |
| Stylus handwriting and gestures | yes (33–36) | none (Scribble is system-only) | none | |
| Next-keyboard key | `shouldOfferSwitchingToNextInputMethod` + `switchToNextInputMethod` | `needsInputModeSwitchKey` + `advanceToNextInputMode` | mandatory on iOS (4.4.1) | |
| Keyboard picker | `showInputMethodPicker()` | `handleInputModeList(from:with:)` (native control) | none | |
| Languages and subtypes | `InputMethodSubtype` (system-visible) | one extension = one entry in Settings (`PrimaryLanguage`); switch languages internally | none | iOS can ship several keyboard extensions for per-language entries. Each is a process with its own memory limit. |
| System spell-check service | `SpellCheckerService` (14) | none (keyboards cannot provide system spell-check) | `BIND_TEXT_SERVICE` | |
| System dictionary | `UserDictionary` (IME/spellchecker only since 23) | `UILexicon` (read-only), `UITextChecker` | none | |
| Autofill suggestions | Inline suggestions (30) | none; iOS autofill is system UI | none | |
| Clipboard | Read and write; default IME exempt from the "pasted" toast | `UIPasteboard` only with Full Access | iOS: Full Access | Respect `EXTRA_IS_SENSITIVE` on Android. |
| Network | yes | Full Access only | Android `INTERNET`; iOS Full Access | iOS must work offline (4.4.1). |
| Haptics | `performHapticFeedback`, `Vibrator` | Full Access (reported) | Android `VIBRATE` for custom vibration | |
| Key click sound | `AudioManager.playSoundEffect` / `SoundPool` | `playInputClick` / system sounds, Full Access per Apple | iOS: Full Access | Android 17 background-audio hardening: test. |
| Microphone and voice | `SpeechRecognizer` in-IME (on-device API 31) or switch to a voice IME | never in the extension; system dictation mic or container-app relay (policy risk) | Android `RECORD_AUDIO` (runtime, via Activity) | |
| Location and contacts | normal runtime permissions | Full Access + user permission | | Not needed for v1. |
| Settings shared with the app | Same app process: DataStore or SharedPreferences directly | App Group: keyboard read-only without Full Access | iOS: Full Access to write | |
| Conversation context | none | `conversationContext` (18.4), delivery to third-party keyboards unverified | | |
| AI / writing-tools opt-out | `EditorInfo.isWritingToolsEnabled` (36) | `writingToolsBehavior` trait (18); keyboard visibility unverified | | Honour it before any AI rewrite. |
| Height and fullscreen | Full control; insets; optional extract fullscreen | Height via constraint; width fixed | none | |
| Draw outside the keyboard (previews, popups) | Yes (tall window + `touchableRegion`) | **No**; nothing above the top edge | | Design previews inside bounds. |
| Memory limit | No special cap (normal LMK) | about 48–70 MB jetsam, silent kill | | |
| Start-up budget | No hard limit ("bring up the IME's UI quickly") | "well under one second" or terminated | | |
| Lock screen / before first unlock | `directBootAware` + DE storage | Keyboards are generally not used for the device passcode *(unverified detail)* | | |
| Hardware keyboard | `onKeyDown` / `onKeyUp`, physical-keyboard subtype hints | Not applicable to extensions *(unverified)* | | |

---

## 5. What must be platform-specific vs what can be shared

### 5.1 Proposed module layout

```
:engine            (KMP: commonMain + androidMain + iosMain)  <- extension-safe, NO Compose
   text/           shadow document, composition, autocorrect/autocap/double-space logic, reconciliation
   input/          touch → key hit-testing, gesture state machine (long-press, swipe-typing, cursor drag, shift/caps)
   layout/         layout definitions → KeyboardRenderModel (key rects, labels, states, popups) per width/height/density
   lang/           dictionaries (mmap reader), suggestion ranking, learning store
   settings/       settings model + (de)serialization (versioned file / proto)
   theme/          theme tokens (colors, radii, fonts, sizes) — pure data
   ports/          interfaces below (TextHost, Haptics, KeySound, Clipboard, ...)
:ui-compose        (KMP Compose: commonMain) KeyboardSurface(renderModel), settings screens, theme editor, previews
:ime-android       (Android app module) MaKeebImeService (LifecycleInputMethodService) + androidMain adapters
:app-shared        (KMP umbrella for container apps) depends on :engine + :ui-compose
:kb-ios-umbrella   (KMP umbrella → static framework "MaKeebEngine" for the iOS keyboard extension) depends on :engine only
iosApp/            SwiftUI/UIKit shell hosting ComposeUIViewController for the container app (framework "MaKeebApp")
iosKeyboard/       KeyboardViewController (UIInputViewController) + native KeySurfaceView (UIKit/CoreAnimation) + Swift adapters
```

### 5.2 Port interfaces (commonMain) and adapters

A sketch; names are illustrative:

```kotlin
// ports/TextHost.kt — the only way the engine touches the document
interface TextHost {
    val caps: HostCapabilities
    fun beginBatch(); fun endBatch()
    fun commitText(text: String, newCursorPosition: Int = 1)
    fun setComposing(text: String)               // may be emulated when !caps.composing
    fun finishComposing()
    fun deleteBefore(codePoints: Int)
    fun deleteAfter(codePoints: Int)
    fun moveCursor(byCodePoints: Int)
    fun textBeforeCursor(max: Int): String?      // may be truncated / null
    fun textAfterCursor(max: Int): String?
    fun selectedText(): String?
    fun performEnter(action: EnterAction)
    fun commitRichContent(item: RichContent): Boolean   // false when unsupported
}
data class HostCapabilities(
    val composing: Boolean, val absoluteSelection: Boolean, val atomicReplace: Boolean,
    val richContentMimeTypes: List<String>, val contextIsTruncated: Boolean,
)
// Host → engine events: onStartInput(FieldInfo), onSelectionChanged(...), onExternalTextChange(), onFinishInput(), onDocumentChanged(id)
```

| Port (commonMain) | androidMain adapter | iosMain / Swift adapter | Notes |
|---|---|---|---|
| `TextHost` + host events | `InputConnection` + `onStartInput`, `onUpdateSelection` and `onUpdateCursorAnchorInfo`; caps: composing, absolute selection and atomic replace (34+) all true | `textDocumentProxy` + `textWillChange`/`textDidChange` + `documentIdentifier`; caps: composing = marked text (policy), no absolute selection, no atomic replace, context truncated | Shared shadow document plus reconciliation in `:engine`. |
| `FieldInfo` source | `EditorInfo` mapper (class, variation, flags, imeOptions, hintLocales, noLearning, writingToolsEnabled, contentMimeTypes, packageName) | Proxy traits mapper (`keyboardType`, `returnKeyType`, `autocapitalizationType`, `autocorrectionType`, `textContentType`, smart punctuation, `documentInputMode`) | One `FieldInfo` data class drives layout choice and learning policy. |
| `KeyboardSwitcher` | `switchToNextInputMethod(false)`, `shouldOfferSwitchingToNextInputMethod()`, `showInputMethodPicker()` | `advanceToNextInputMode()`, `needsInputModeSwitchKey`, `handleInputModeList` (the globe is a native control) | |
| `Haptics` | `performHapticFeedback(KEYBOARD_TAP…)` / `Vibrator` | `UIImpactFeedbackGenerator` (no-op when `!hasFullAccess`) | Engine emits semantic events (`KeyDown`, `LongPressOpened`, …). |
| `KeySound` | `AudioManager.playSoundEffect(FX_KEYPRESS_*)` | `playInputClick()` / system sounds (no-op without Full Access) | |
| `Clipboard` | `ClipboardManager` (+ listener; sensitive flag) | `UIPasteboard` (Full Access only) | Clipboard history is a Full-Access-only feature on iOS. |
| `SettingsStore` | DataStore (same app, read/write); DE storage copy for direct boot | App Group file or `UserDefaults(suiteName:)`: **read-only** in the keyboard unless Full Access | Split into `AppSettings` (app-owned) and `KeyboardLocalState` (keyboard-owned). |
| `LearningStore` | App files (CE storage) | Extension sandbox container; mirrored to the App Group only with Full Access and consent | Honours no-learning and incognito. |
| `SystemLexicon` | `UserDictionary.Words` query | `requestSupplementaryLexicon` | Import-only sources. |
| `SpellFallback` (optional) | none (own engine) | `UITextChecker` | Android can additionally publish a `SpellCheckerService` backed by `:engine`. |
| `Voice` | `SpeechRecognizer` (on-device where available) + `RECORD_AUDIO` flow via an app Activity; or switch to a voice IME | `hasDictationKey = false` (system mic) in v1; relay via the container app only after a policy review | |
| `Trust` / `Capabilities` | always "full" (permissions checked individually) | `hasFullAccess`, network reachability | The engine hides features gracefully. |
| `ResourceMapper` | `FileChannel.map` / `MappedByteBuffer` or native mmap | `NSData(contentsOfFile:options:.mappedIfSafe)` or POSIX `mmap` via cinterop | Keep dictionaries off the Kotlin heap on both. |
| `Lifecycle` / memory | `onWindowShown`/`Hidden`, `onTrimMemory` | `viewWillAppear`/`Disappear`, `didReceiveMemoryWarning` | Engine `trim()` on hide or warning. |
| Renderer | Compose `KeyboardSurface(renderModel)` in the IME window | Native `KeySurfaceView` (CALayer per key or a single drawn layer) reading `renderModel`; raw touches forwarded to `:engine` | See §6. |

### 5.3 What is platform-specific no matter what

- **Service and extension entry points:** `InputMethodService` and `UIInputViewController`.
- **Window and insets management:** `onComputeInsets`, edge-to-edge and the IME switcher button on Android; height constraints and Liquid Glass quirks on iOS.
- **The iOS globe key**, as a real `UIControl`.
- **Onboarding flows:** the Android IME settings and picker; iOS Settings → Keyboards → Add + Allow Full Access, with no public "is my keyboard enabled" API *(unverified: heuristics exist)*.
- **Rich content** (Android only), **stylus** (Android only) and **inline autofill** (Android only).
- **Voice.**
- **Privacy manifests** (iOS), and **Data safety plus prominent disclosure** (Android).

---

## 6. Compose Multiplatform in the iOS keyboard extension: feasibility

### 6.1 Upstream status *(verified)*

- **CMP-3826, "Any plans to support `App Extensions`?" (GitHub #3826).** JetBrains (2023): "Eventually we want to support all sensible usage scenarios. We still didn't approach this one." Closed **Obsolete** in November 2025 ("hasn't been updated for several years") with no implementation.
- **CMP-4610, "[iOS] Using Compose inside a ShareExtensionViewController renders it black" (GitHub #4610, April 2024).** JetBrains reproduced it: "`UIApplication.shared` is illegal to access in App Extensions and we still wrongfully do it … the [Skia] version we use still performs `MtlIsAppInBackground` check … and wrongfully exits not even trying to perform the draw logic. … We didn't plan supporting such a feature." Closed as a *Duplicate* (March 2025); the target issue is not publicly visible.
- Current Skia `main` no longer contains `MtlIsAppInBackground`. CMP now uses its own Metal redrawer, gated by UIScene state, so that particular blocker may be gone. The following remain.

### 6.2 Code-level findings in CMP 1.12.1 (tag `v1.12.1` of compose-multiplatform-core) *(verified)*

| Location | Extension-relevant behaviour |
|---|---|
| `compose/ui/ui/src/iosMain/.../scene/ComposeContainer.ios.kt` | `private var layoutDirection = getApplicationLayoutDirection()` → `UIApplication.sharedApplication().userInterfaceLayoutDirection`. **Runs on every container init.** |
| `compose/ui/ui/src/iosMain/.../window/ComposeContainerView.ios.kt` | `SceneForegroundStateListener(getScene = { window?.windowScene })`; `redrawer.isActive = isSceneInForeground`; `updateRedrawerState()` uses `?: false`. **No `windowScene` means no drawing.** |
| `.../window/SceneForegroundStateListener.ios.kt` | Foreground = scene `activationState` not `Background` or `Unattached`; nil scene → `false`. |
| `compose/ui/ui-uikit/.../CMPViewController.m` | `cmp_isRootViewController` iterates `UIApplication.sharedApplication.connectedScenes` and `.windows`. |
| `compose/ui/ui-uikit/.../CMPUIWindowSceneExtensions.m` | Falls back to `UIApplication.sharedApplication.statusBarOrientation` when `windowScene == nil`. |
| `.../platform/PlatformUriHandler.ios.kt` | `UIApplication.sharedApplication.openURL(...)`, only if `LocalUriHandler` is used. |
| `.../platform/UIKitIdleTimerManager.ios.kt` | `UIApplication.sharedApplication.idleTimerDisabled`. |
| `.../uikit/PlistSanityCheck.ios.kt` | Reads `CADisableMinimumFrameDurationOnPhone` from `NSBundle.mainBundle`, which in an extension is the `.appex` bundle, and calls `error(...)` if it is missing. Add the key to the extension's Info.plist, or set `enforceStrictPlistSanityCheck = false`. |
| `CMPMetalLayer.m` | `kMaxDrawables = 3` IOSurface-backed BGRA8 drawables sized to the view's `drawableSize` (bounds × scale). |
| Skiko `MetalRedrawer.uikit.kt` (not used by CMP's own redrawer) | `UIApplication.sharedApplication.applicationState` at init. |

Implications:
- Kotlin/Native's `platform.UIKit` bindings do not carry `NS_EXTENSION_UNAVAILABLE`, and static frameworks bypass the "not safe for use in application extensions" linker check. So **it will compile and link**.
- The runtime behaviour of `+[UIApplication sharedApplication]` inside a keyboard extension is *(unverified)*. The long-standing responder-chain URL hack implies a `UIApplication` instance exists in keyboard processes. If it returned `nil`, K/N would most likely throw on the non-null return *(unverified)*.
- Whether a keyboard's hosted window exposes a foreground `UIWindowScene` is *(unverified)*. These two questions are the first things the spike must answer.
- A `ComposeUIView` (a view-based host that clears state when leaving the window) exists in 1.12.1 and is the better fit than `ComposeUIViewController` for an `inputView`.

### 6.3 Memory budget analysis

| Component | Estimate | Basis |
|---|---|---|
| Empty UIKit keyboard process | ~10 MB | dev.to ("a cold process read 10MB") *(reported)* |
| Metal drawables, 3 × BGRA8 at keyboard size | iPhone SE class (750×520 px): **~4.5 MiB**; Pro Max class (1320×~1010 px): **~15 MiB**; 13" iPad landscape (2752×~800 px): **~26 MiB** | *(estimate)* from `kMaxDrawables = 3`, 4 B/px, typical keyboard plus suggestion-strip heights. IOSurface alignment adds a little. |
| Skia GPU caches (glyph atlases, paths) plus the Compose runtime and K/N heap for the UI tree | unknown; likely several MB to 10+ MB | CMP apps reported at 55.75 MiB vs 29.10 MiB persistent for an equivalent Swift app *(reported via search snippet of Zhuravko, Medium, June 2026; article not accessible)*. JetBrains: a Compose view controller costs about 1.9x a native one for identical content (CMP-8458). |
| Kotlin engine heap (no dictionaries) | a few MB | *(estimate)* |
| Dictionaries and language model | must be mmap'd; dirty heap if parsed | dev.to: moving to mmap cut the baseline 52 → 27 MB |

A CMP keyboard plausibly lands at **30–50 MB before language data** on phones, and worse on iPads *(estimate)*. That sits against a 48–70 MB ceiling that also has to hold suggestion state. A native UIKit renderer typically adds a few MB on top of the 10 MB baseline. For comparison, Flutter (also its own renderer and runtime) recommends extension UI only where "the app extension supports at least 100MB of memory".

**Binary size and cold start.** JetBrains claims "Compose Multiplatform adds only ~9 MB to the size of an iOS app" and "startup time is comparable to native". A Software Mansion benchmark (CMP 1.10, full app) measured TTID of 802–870 ms on iPhone 13 mini through iPhone 17. That was a whole app, not a keyboard, but it shows the sub-second extension budget is not comfortable. A static CMP framework linked into both the app and the extension means two copies in the IPA.

### 6.4 Kotlin/Native memory management in a constrained extension *(verified: Kotlin docs)*

- **GC.** The default is a concurrent mark-and-sweep (`cms`, default since 2.4.0), non-generational. It is triggered by memory-pressure heuristics or a timer.
- **Runtime tuning** (`kotlin.native.runtime.GC`, `@NativeRuntimeApi`): `autotune`, `targetHeapBytes`, `minHeapBytes`/`maxHeapBytes`, `heapTriggerCoefficient`, `pauseOnTargetHeapOverflow`, `regularGCInterval`, `collect()` and `schedule()`. Call `GC.collect()` (or `schedule()`) on `didReceiveMemoryWarning` and when the keyboard hides. Cap `maxHeapBytes`.
- **Binary options to evaluate:**
  - `kotlin.native.binary.latin1Strings=true` (less string memory).
  - `pagedAllocator=false` ("may help you satisfy strict memory limitations or reduce memory consumption on the application's startup").
  - `smallBinary=true` (smaller release binary).
  - `appStateTracking=enabled`. The docs say the memory manager "does not integrate with App Extensions out of the box" and that this option stops timer-based GC in the background. Whether its state tracking works inside a keyboard extension is *(unverified)*.
- **Measure.** K/N memory is tagged (`mmapTag`, default 246) and visible in Instruments VM Tracker when paging is on. Use `enableSafepointSignposts=true` to see GC pauses, which matter for per-key latency.
- **Interop hygiene.** Wrap interop-heavy loops in `autoreleasepool {}`. `objcDisposeOnMain` defaults to `true`, so be careful with main-thread deinit spikes. Keep large data out of the K/N heap (mmap). Avoid per-keystroke allocation churn in the engine (pre-allocated buffers, primitive arrays).

### 6.5 Linking Kotlin frameworks into the container app and the extension *(verified or reported)*

- The KMP docs recommend **one umbrella framework per iOS binary**: "Whenever two or more modules use the same dependency and are exposed to iOS as separate frameworks, the Kotlin/Native compiler duplicates the dependencies."
- Therefore:
  - The **extension** links **only** `MaKeebEngine` (static, from `:kb-ios-umbrella`: engine without Compose).
  - The **app** links **only** `MaKeebApp` (static or dynamic, from `:app-shared`: engine plus CMP).
  - Separate processes mean duplicated code in the IPA is fine; duplicated runtimes inside one binary is not.
- **Do not embed frameworks inside the `.appex`.** Archive validation rejects "disallowed nested bundles" and "disallowed file 'Frameworks'". Use static frameworks for the extension. If you want a shared dynamic framework, embed it once in the app's `Frameworks/` and mark it extension-safe (`-application_extension`, *unverified for K/N link options*). A known workaround for build ordering is running `embedAndSignAppleFrameworkForXcode` from a scheme pre-action.
- Enable `APPLICATION_EXTENSION_API_ONLY = YES` on the extension's Swift code. Add a lint or detekt rule forbidding `platform.UIKit.UIApplication` in `:engine`.
- Put a `PrivacyInfo.xcprivacy` in both targets.

### 6.6 Options compared and recommendation

| Option | Sharing | Risk | Memory | Verdict |
|---|---|---|---|---|
| **A. CMP (`ComposeUIView`) as the iOS keyboard UI** | Max (same `KeyboardSurface` on both) | High: unsupported upstream; `sharedApplication` calls; scene gating; possible fork or patches of CMP | Highest (drawables + Skia + runtime) | Spike only (§6.7). Adopt only if every gate passes. |
| **B. Shared Kotlin engine + native UIKit/Core Animation renderer** | Engine, layout geometry, hit-testing, gestures and theme tokens shared; only drawing and platform glue are native (~1–2k lines of Swift) | Low | Lowest | **Recommended baseline.** |
| C. Shared Kotlin engine + SwiftUI renderer | Same as B | Medium: iOS 27 gesture lag and NaN-layer crash reports in SwiftUI keyboards | Low | OK for secondary panels (emoji, clipboard, settings sheet); not for the key grid. |
| D. Swift export instead of SKIE or ObjC headers | n/a (interop layer) | Swift export is **Alpha** in the Kotlin 2.4 docs | n/a | Revisit when Beta. Use **SKIE 0.10.15** (Kotlin 2.4.20 support) or **KMP-NativeCoroutines 1.0.6** now. |

How B works in practice:
- `:engine` exposes `StateFlow<KeyboardRenderModel>` and `fun onTouch(events: List<TouchSample>)`.
- SKIE turns the flows into `AsyncSequence` and sealed classes into Swift enums.
- Swift `KeySurfaceView` diffs the render model into CALayers (or draws text with Core Text into one layer) and forwards raw `UITouch` samples. Key identity, long-press timing, swipe paths and cursor-drag are decided in Kotlin, so behaviour is identical to Android's Compose renderer.
- Previews and popups are part of the render model and constrained to the keyboard bounds.
- The iOS container app still uses CMP (settings, theme editor), showing a Compose preview of the same render model for WYSIWYG theming.

### 6.7 De-risking spike plan (about 2 weeks, run B and A in parallel)

**Week 1: spike B (baseline) and the engine.**
1. Create an Xcode keyboard extension with a static `MaKeebEngine` framework (Kotlin 2.4.20, SKIE 0.10.15). Draw a UIKit key grid from a Kotlin render model. Commit text through a Swift `TextHost` adapter.
2. Instrument it:
   - Read `phys_footprint` via `task_vm_info` and log it every second.
   - Add `os_signpost` for `viewDidLoad` → first frame, and for touch → `insertText` latency.
   - Turn on K/N `enableSafepointSignposts`.
3. Load a 150–250k-word dictionary two ways: (a) parsed into Kotlin collections and (b) mmap'd binary. Measure both. Test GC settings (`autotune`, `maxHeapBytes`), `latin1Strings` and `pagedAllocator=false`.
4. Test on the oldest supported iPhone (4 GB RAM class), a current Pro Max, and an iPad. Hosts: Messages, Notes, Safari, and one third-party app (for example WhatsApp). Run 50 show/hide cycles per host (watch the ~3 MB/cycle leak pattern), rotation, and a memory-warning simulation.

**Week 1–2: spike A (CMP in extension), timeboxed to 5 days.**
1. Host `ComposeUIView` (and separately `ComposeUIViewController` as a child VC) in `inputView`, rendering the same render model with Compose.
2. Answer the blocking unknowns:
   - Is `view.window?.windowScene` non-nil and foreground?
   - Does `UIApplication.sharedApplication` return non-nil?
   - Does it render at all in each host?
   - Does the plist check need the key in the `.appex`?
   - Does the Compose host try to become first responder or interfere with the proxy?
   - Is ProMotion frame pacing honoured?
3. If blocked, estimate patch effort: fork the 4–5 call sites and allow an injected scene state. Record that it means carrying a CMP fork.

**Also:** read `textDocumentProxy.conversationContext` in Messages and Mail to settle §3.3.

**Go/no-go gates for adopting A** (all must pass on the oldest device; B must also pass them):
- Steady-state `phys_footprint` **≤ 30 MB** with the dictionary loaded; peak **≤ 40 MB** during fast typing and emoji panel open.
- Cold first frame **≤ 300 ms p90**. Touch-to-commit **≤ 16 ms p95** (one frame at 60 Hz).
- **Zero** jetsam kills across a 30-minute scripted typing session.
- **No growth > 1 MB** over 50 show/hide cycles.
- Renders correctly in all test hosts, in light and dark mode, in iOS 26 Liquid Glass hosts, and on iPad.
- No reachable `UIApplication.sharedApplication` calls, or a documented, reviewed, App-Store-tested patch.

**Android spike (2 days, in parallel).**
- `LifecycleInputMethodService` with a Compose `KeyboardSurface`, using the same `:engine` and `:ui-compose`.
- Measure touch-to-commit latency.
- Verify overlays drawn in the tall window with `touchableRegion`.
- Run click sounds under `adb shell cmd audio set-enable-hardening throw` on Android 17.
- Verify direct-boot minimal mode.

---

## 7. Platform policy and review risks

| # | Risk | Platform | Severity | Mitigation |
|---|---|---|---|---|
| 1 | Keyboard not "functional without full network access and without requiring full access" (4.4.1) | iOS | High (rejection) | Offline-first engine with bundled dictionaries; never gate basic typing on Full Access; explain Full Access benefits only in the container app. |
| 2 | Launching the container app from the keyboard (dictation relay, "open app" buttons) vs "must not … launch other apps besides Settings" (4.4.1); no public URL-open API for keyboards | iOS | High | v1: rely on the system dictation mic (`hasDictationKey = false`). Consider a relay only after a policy review, and label it as a user-initiated action. |
| 3 | Private API use (host bundle ID via XPC, responder-chain `openURL`) | iOS | High (breakage and rejection) | Do not use it. The 26.4 breakage shows the fragility. |
| 4 | Extension-unavailable API use pulled in by CMP (`UIApplication.shared`) | iOS | Medium *(unverified review stance)* | Keep CMP out of the extension (Option B), or patch it. |
| 5 | Marketing, ads or IAP inside the extension (4.4) | iOS | Medium | None in the keyboard; IAP only in the container app. |
| 6 | Emoji, image or sticker content must follow the Sticker guidelines (4.4.1) | iOS | Low–Medium | Licence the emoji and sticker assets; no trademarked stickers. |
| 7 | Repurposing keys (4.4.1) | iOS | Low | Keep Return as Return; shortcuts only on dedicated keys. |
| 8 | Data collection beyond "enhance the functionality of the … keyboard" (4.4.1, 5.1.1); App Privacy labels | iOS | High | No analytics on typed content; aggregate crash and perf telemetry only, and only with consent; accurate privacy labels. |
| 9 | Missing privacy manifest reasons (K/N `mach_absolute_time` 35F9.1, `stat`/`fstat` 0A2A.1, `UserDefaults` 1C8F.1 or CA92.1) | iOS | Medium (upload rejection) | `PrivacyInfo.xcprivacy` in both the app and the appex. |
| 10 | Silent jetsam kills read as "keyboard crashes" (App Store reviews, churn) | iOS | High | Memory budget in CI on device; mmap data; trim on hide. |
| 11 | Prominent disclosure and consent missing for any off-device use of typed text, clipboard or audio (User Data policy) | Android | High (removal) | On-device by default; explicit opt-in screens for cloud features; accurate Data safety form and privacy policy. |
| 12 | Learning from password or no-learning fields | Both | High (trust and policy) | The engine refuses to learn when `FieldInfo.noLearning` is set (password variations, `IME_FLAG_NO_PERSONALIZED_LEARNING`, secure fields never reach iOS keyboards). |
| 13 | Clipboard history leaking sensitive clips | Android | Medium | Respect `EXTRA_IS_SENSITIVE`; expire history; exclude it in incognito. |
| 14 | Target API requirement (API 36 since 31 Aug 2026) and new behaviour (edge-to-edge, predictive back, Android 17 audio hardening) | Android | Medium | Target 36 now and plan for 37; test audio hardening. |
| 15 | `RECORD_AUDIO` requested without a clear user action | Android | Medium | Request only from the container Activity when the user taps the mic for the first time. |
| 16 | Accessibility-service misuse as a keyboard workaround | Android | High (removal) | Do not use AccessibilityService. |
| 17 | AI rewrite features in editors that opted out | Android (36+), iOS | Medium | Honour `EditorInfo.isWritingToolsEnabled()` and the iOS `writingToolsBehavior` trait (visibility to keyboards unverified). |

---

## 8. Open questions and unverified items (resolve in the spike)

1. Does a keyboard extension's view have a non-nil, foreground `window.windowScene`? This gates CMP rendering.
2. What does `UIApplication.sharedApplication` return inside a keyboard extension, and does K/N crash on it?
3. The real `phys_footprint` ceiling on the oldest supported iPhone and on iPad under iOS 26 and 27.
4. Does `textDocumentProxy.conversationContext` get populated for third-party keyboards (Messages, Mail)?
5. Exact Full Access dependence of `playInputClick`, `AudioServicesPlaySystemSound` and `UIImpactFeedbackGenerator` on iOS 26 and 27.
6. Keychain-sharing availability for keyboards with and without Full Access.
7. Does Android 17 background audio hardening affect IME key-click playback (`playSoundEffect`, `SoundPool`)?
8. Is `kotlin.native.binary.appStateTracking` effective inside app extensions?
9. How is `deleteBackward` granularity and `documentContextBeforeInput` truncation length distributed across major hosts?
10. The App Store review stance on keyboards that open their container app for dictation (Wispr Flow and KeyboardKit Pro ship this; the guideline text says otherwise).

---

## 9. Sources

**Android: official docs and SDK**
- Create an input method: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- InputMethodService reference: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- InputConnection reference: https://developer.android.com/reference/android/view/inputmethod/InputConnection
- EditorInfo reference: https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- Receive rich content: https://developer.android.com/develop/ui/views/receive-rich-content
- Android 10 privacy changes (clipboard): https://developer.android.com/about/versions/10/privacy/changes
- Android 12 behavior changes (clipboard toast): https://developer.android.com/about/versions/12/behavior-changes-all
- Copy and paste (sensitive content): https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
- Android 15 behavior changes: https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16 behavior changes (targeting 36): https://developer.android.com/about/versions/16/behavior-changes-16
- Android 16 behavior changes (all apps): https://developer.android.com/about/versions/16/behavior-changes-all
- Android 17 behavior changes (all apps): https://developer.android.com/about/versions/17/behavior-changes-all
- Android 17 behavior changes (targeting 37): https://developer.android.com/about/versions/17/behavior-changes-17
- Android 17 background audio hardening: https://developer.android.com/about/versions/17/changes/bg-audio
- Direct Boot: https://developer.android.com/privacy-and-security/direct-boot
- Play target API level requirements: https://developer.android.com/google/play/requirements/target-sdk
- Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Android SDK Platform 17 (API 37, rev 2): `platforms/android-37.0/data/api-versions.xml` and `sources/android-37.0` (local SDK; API levels and javadoc quoted above)

**Android: AOSP and open-source code**
- AOSP ClipboardService (IME exemption): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/clipboard/ClipboardService.java
- AOSP InputMethodBindingController (IME bind flags): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/inputmethod/InputMethodBindingController.java
- AOSP RemoteInputConnection (2000 ms timeout): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/inputmethodservice/RemoteInputConnection.java
- FlorisBoard LifecycleInputMethodService: https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/ime/lifecycle/LifecycleInputMethodService.kt
- FlorisBoard FlorisImeService: https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt
- FlorisBoard ImeWindowController: https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/ImeWindowController.kt
- Android 15 QPR1 keyboard switcher (PhoneArena): https://phonearena.com/news/revamped-keyboard-switcher-android-15-easier-to-use_id161886

**iOS: official**
- UIInputViewController: https://developer.apple.com/documentation/uikit/uiinputviewcontroller
- UITextDocumentProxy: https://developer.apple.com/documentation/uikit/uitextdocumentproxy
- UITextInputTraits: https://developer.apple.com/documentation/uikit/uitextinputtraits
- conversationContext trait: https://developer.apple.com/documentation/uikit/uitextinputtraits/conversationcontext
- conversationContext(_:didChange:): https://developer.apple.com/documentation/uikit/uitextinputdelegate/conversationcontext(_:didchange:)
- UIConversationContext: https://developer.apple.com/documentation/uikit/uiconversationcontext
- Adopting Smart Reply: https://developer.apple.com/documentation/uikit/adopting-smart-reply-in-your-messaging-or-email-app
- Creating a custom keyboard: https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard
- Configuring a custom keyboard interface: https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface
- Handling text interactions in custom keyboards: https://developer.apple.com/documentation/uikit/handling-text-interactions-in-custom-keyboards
- Configuring open access: https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard
- UILexicon: https://developer.apple.com/documentation/uikit/uilexicon
- UITextChecker: https://developer.apple.com/documentation/uikit/uitextchecker
- NSExtensionContext.open: https://developer.apple.com/documentation/foundation/nsextensioncontext/open(_:completionhandler:)
- ExtensionPointIdentifier.keyboard: https://developer.apple.com/documentation/uikit/uiapplication/extensionpointidentifier/keyboard
- App Extension Programming Guide, Custom Keyboard: https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html
- App Extension Programming Guide, overview (unavailable APIs): https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionOverview.html
- App Extension Programming Guide, creation (performance and memory): https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionCreation.html
- App Extension Programming Guide, common scenarios (sharing data, frameworks): https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionScenarios.html
- App Store Review Guidelines: https://developer.apple.com/app-store/review/guidelines/

**iOS: forums, vendors and reports**
- Apple forums, 30 MB keyboard crash: https://developer.apple.com/forums/thread/105815
- Apple forums, documentContextBeforeInput only last sentences: https://developer.apple.com/forums/thread/772158
- Apple forums, iOS 26 extra margins: https://developer.apple.com/forums/thread/800838
- Apple forums, iOS 26 gray bar: https://developer.apple.com/forums/thread/793686
- dev.to, "The three hard constraints of an iOS keyboard extension": https://dev.to/tbds_2dadf2b626f315902eae/the-three-hard-constraints-of-an-ios-keyboard-extension-46af
- React Native issue #31910 (48 MB): https://github.com/facebook/react-native/issues/31910
- KeyboardKit FAQ (60–70 MB): https://keyboardkit.com/faq
- KeyboardKit, iOS 26.4 host application bug: https://keyboardkit.com/blog/2026/03/02/ios-26-4-host-application-bundle-id-bug
- KeyboardKit, new host application approach: https://keyboardkit.com/blog/2026/08/24/evaluating-a-new-host-application-approach
- KeyboardKit, iOS 27 gesture lag and 27.2 crash: https://keyboardkit.com/blog/2026/09/23/a-gentle-reminder-to-upgrade-keyboardkit-to-the-latest-version
- KeyboardKit, dictation experience: https://keyboardkit.com/blog/2026/01/03/a-brand-new-keyboard-dictation-experience
- KeyboardKit, Liquid Glass: https://keyboardkit.com/blog/2025/07/28/custom-ios-keyboard-extensions-and-liquid-glass
- KeyboardKit issue #1014: https://github.com/KeyboardKit/KeyboardKit/issues/1014
- Wispr Flow keyboard (9to5Mac): https://9to5mac.com/2025/06/30/wispr-flow-is-an-ai-that-transcribes-what-you-say-right-from-the-iphone-keyboard/

**Kotlin and Compose Multiplatform**
- CMP #3826, app extensions: https://github.com/JetBrains/compose-multiplatform/issues/3826 and https://youtrack.jetbrains.com/issue/CMP-3826
- CMP #4610, Share extension renders black: https://github.com/JetBrains/compose-multiplatform/issues/4610 and https://youtrack.jetbrains.com/issue/CMP-4610
- CMP-8458, memory (3 buffers, ComposeUIView): https://youtrack.jetbrains.com/issue/CMP-8458
- CMP-9918, clear Metal buffers: https://youtrack.jetbrains.com/issue/CMP-9918
- compose-multiplatform-core v1.12.1 sources:
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/scene/ComposeContainer.ios.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/window/ComposeContainerView.ios.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/window/SceneForegroundStateListener.ios.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui-uikit/src/iosMain/objc/CMPUIKitUtils/CMPUIKitUtils/CMPViewController.m
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui-uikit/src/iosMain/objc/CMPUIKitUtils/CMPUIKitUtils/CMPUIWindowSceneExtensions.m
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/platform/PlatformUriHandler.ios.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/platform/UIKitIdleTimerManager.ios.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/uikit/PlistSanityCheck.ios.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/window/ComposeUIView.uikit.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui-uikit/src/iosMain/objc/CMPUIKitUtils/CMPUIKitObjcUtils/CMPMetalLayer.m
- Skiko MetalRedrawer (uikit): https://github.com/JetBrains/skiko/blob/master/skiko/src/uikitMain/kotlin/org/jetbrains/skiko/redrawer/MetalRedrawer.uikit.kt
- CMP releases (1.12.1 latest): https://github.com/JetBrains/compose-multiplatform/releases
- CMP 1.8.0 blog (size and startup claims): https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- Software Mansion KMP vs RN benchmark (2026): https://swmansion.com/blog/we-built-the-same-app-in-kmp-and-react-native-here-s-what-we-found/
- Zhuravko, Swift vs CMP performance (June 2026; only the search snippet was accessible): https://medium.com/@zhuravl321/swift-vs-compose-multiplatform-a-real-world-performance-comparison-dff90b0f8522
- Flutter iOS app extensions (100 MB guidance): https://docs.flutter.dev/platform-integration/ios/app-extensions
- Kotlin/Native memory manager: https://kotlinlang.org/docs/native-memory-manager.html
- Kotlin/Native binary options: https://kotlinlang.org/docs/native-binary-options.html
- Kotlin/Native ARC integration (app extensions and appStateTracking): https://kotlinlang.org/docs/native-arc-integration.html
- Kotlin GC runtime API: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.native.runtime/-g-c/
- Swift export (Alpha): https://kotlinlang.org/docs/native-swift-export.html
- KMP project configuration (umbrella framework): https://kotlinlang.org/docs/multiplatform/multiplatform-project-configuration.html
- KMP privacy manifest: https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-privacy-manifest.html
- KMP framework in app + widget extension (Kotlin discussions): https://discuss.kotlinlang.org/t/ios-widget-extension-with-embedandsignappleframeworkforxcode/28406
- SKIE releases (0.10.15, Kotlin 2.4.20): https://github.com/touchlab/SKIE/releases
- KMP-NativeCoroutines releases (1.0.6): https://github.com/rickclephas/KMP-NativeCoroutines/releases
- Kotlin releases (2.4.20): https://github.com/JetBrains/kotlin/releases
