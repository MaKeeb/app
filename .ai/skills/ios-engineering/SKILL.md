---
name: ios-engineering
description: iOS engineering for MaKeeb's keyboard extension and container app: UIInputViewController lifecycle, UITextDocumentProxy semantics, the memory budget and jetsam, Full Access gating, Swift↔Kotlin interop through the MaKeebKeyboard framework, the Core Graphics renderer, the globe key, App Groups, privacy manifests, XcodeGen and App Review 4.4.1. Use when touching iosMain code, Swift under app/ios, project.yml, or anything the keyboard extension links.
---

# iOS engineering

Read `keyboard-platforms` first. The evidence is in `docs/research/platform-apis.md`: §3 covers the extension surface, Full Access and hard limits, §6.4–6.5 cover Kotlin/Native memory and framework linking, and §7 covers review risks.

## Two binaries, two frameworks

| Target | Links | UI |
| --- | --- | --- |
| `MaKeeb` (container app) | `MaKeebCompanion` from `:shared:companion` | Compose Multiplatform through `MainViewController()` |
| `MaKeebKeyboardExtension` | `MaKeebKeyboard` from `:shared:keyboard` (no Compose) | `KeyboardViewController` + `KeyboardView` (Core Graphics) |

- Each binary links exactly one Kotlin framework, so each process has one Kotlin/Native runtime. Both frameworks are static. Never embed a framework inside the `.appex`: archive validation rejects nested frameworks.
- `APPLICATION_EXTENSION_API_ONLY = YES` on the extension catches Swift calls to `UIApplication.shared`, but not Kotlin ones. Kotlin/Native's UIKit bindings don't carry the extension-unavailable attribute, and static frameworks skip the linker check. The rule that nothing reachable from `:shared:keyboard` touches `UIApplication` is enforced only by review (`keyboard-reviewer`).
- `project.yml` is the source of truth. Edit it, then run `xcodegen generate` and commit the regenerated project. Pre-build phases run `embedAndSignAppleFrameworkForXcode`.

## Extension lifecycle

`loadView` → `viewDidLoad` (bridge, render subscription, height constraint) → `viewWillAppear` (`preferences.reload()`, `session.start`) → `textDidChange` / `selectionDidChange` → `viewWillDisappear` (`session.stop`) → `deinit` (cancel, dispose).

- Dismissing the keyboard doesn't necessarily end the process, and hosts create new controllers. A leaked controller keeps about 3 MB per show cycle (research §3.6). Keep closures `[weak self]`, cancel the `RenderSubscription`, and make sure `deinit` actually runs.
- The time to first frame must stay well under 1 s, or the system kills the extension. Keep `viewDidLoad` light and load data lazily and memory-mapped.
- `textDidChange` fires on field switches as well as edits. `KeyboardExtensionBridge.textDidChange` restarts the session when the attributes differ. `textDocumentProxy.documentIdentifier` is the more precise "new document" signal if that logic needs tightening.
- Memory warnings: override `didReceiveMemoryWarning`, drop caches, and have Kotlin run `GC.collect()`. Not done yet; see the APP-111 card.

## UITextDocumentProxy

- Available: insert, delete backward (the host decides the granularity, usually one grapheme), relative caret moves (`adjustTextPosition(byCharacterOffset:)`) and marked text. Not available: selecting, absolute positions, undo.
- `documentContextBeforeInput` and `documentContextAfterInput` are truncated, with no documented limit (reports say the last couple of sentences, or about 300 characters), and may be nil until the user edits. Never assume you can see the start of the field.
- Reports say context reads made right after `adjustTextPosition` can be stale. Don't build logic that moves the caret and immediately re-reads.
- Enter: `performEditorAction` returns false and the engine inserts `\n`, which is how iOS apps receive Return. Label the key from `returnKeyType`.
- Secure and phone-pad fields never reach us, because the system keyboard takes over.
- There's no iOS equivalent of Android's no-learning flag. The research (§1) treats `autocorrectionType == .no` as the closest hint. Map it in `InputTraitsMapping.kt` alongside the Android flags.

## Full Access

- `RequestsOpenAccess` is true, but users can refuse. Check `hasFullAccess` when a feature is used, not once at start; the bridge passes lambdas for this.
- Without Full Access there is no pasteboard, haptics, sound (per Apple; confirm on a device), network, or App Group writes. Typing, suggestions, autocorrect, emoji and reading settings must all still work (Guideline 4.4.1).
- Clipboard history is Full-Access-only, and it only sees copies made while the keyboard is running (`PasteboardSystemClipboard` polls `changeCount`).

## Memory

The budget is 30 MB steady and 40 MB peak on the oldest supported device. Jetsam kills the extension silently at roughly 48–70 MB. See `performance-budget` for how to measure.
- Memory-map large data (`NSData` with `.mappedIfSafe`, or POSIX `mmap` via cinterop). Never parse it into Kotlin collections. `StarterDictionaries` are small in-code lists; the APP-110 card replaces them.
- `KeyboardView` redraws the whole view for every `KeyboardRender`. That's cheap on memory. If profiling shows redraw cost on the typing path, move to a `CALayer` per key or dirty rectangles, and keep the renderer dumb.
- Kotlin/Native options worth measuring (research §6.4): `latin1Strings`, `pagedAllocator=false`, and a capped GC `maxHeapBytes`. Set them with `binaryOption(...)` in the `MaKeebKeyboard` framework block, and record before and after numbers.

## Swift ↔ Kotlin

- Swift sees the Objective-C export of Kotlin; `kmp-cmp` has the rules. The framework exports `:core:model`. Types from other modules appear with module-prefixed names.
- Flows don't bridge cleanly. The pattern is a callback plus a cancel handle (`KeyboardExtensionBridge.observe` → `RenderSubscription`). Follow it until the roadmap's SKIE decision is made, and don't mix approaches ad hoc.
- A Kotlin exception that escapes into Swift terminates the extension. Catch it in the bridge, or declare `@Throws` and handle it in Swift.
- Keep the bridge small and Swift-shaped: numbers, strings and render snapshots in, callbacks out. Decisions stay in Kotlin.

## Renderer, touches, globe key

- `KeyboardView` draws a `KeyboardRender` and forwards raw touches (id, x, y in key-area points). It has no timers, hit-testing or long-press logic. Behaviour changes go into Kotlin, with a test.
- Use UIKit for the key grid, not SwiftUI: iOS 27 has reports of SwiftUI gesture lag and a NaN-geometry layer crash in keyboards (research §3.9). SwiftUI is acceptable for secondary panels.
- Nothing can draw above the view's top edge. Previews and popups extend into the strip area (`TouchConfig.overflowAbove`).
- Globe key: draw it only when `needsInputModeSwitchKey` is true. The system keyboard list comes from `handleInputModeList(from:with:)`, which needs a real `UIView` and `UIEvent`. Either overlay a native control on the key's frame (the research recommends this), or call it from `KeyboardView`'s touch handlers with the real event. In both cases the Kotlin side must not also act on that touch.
- Height comes from a priority-999 constraint on the controller's view; the system controls the width. iOS 26 Liquid Glass adds host chrome and margins you can't draw over. Test in Messages, Notes and Safari.
- Appearance: `KeyboardView` redraws on `UITraitUserInterfaceStyle` changes, and colours come from `KeyboardRender.palette(systemDark:)`. Hosts can also ask for a dark keyboard through `textDocumentProxy.keyboardAppearance`, so respect it.

## Shared data and privacy manifests

- The App Group is `group.com.makeeb`. The companion writes preferences (`IosPreferences.kt`, suite `NSUserDefaults`), and the extension reads them and calls `reload()` in `viewWillAppear`. Without Full Access the extension's group writes reportedly fail silently, so data the keyboard owns (learned words, recents) lives in the extension's own container.
- Both targets have a `PrivacyInfo.xcprivacy`. The Kotlin/Native runtime needs `mach_absolute_time` (35F9.1) and `stat`/`fstat` (0A2A.1), and App Group `UserDefaults` needs 1C8F.1. Add a reason whenever you use another required-reason API.

## Debugging

- The simulator runs the extension (Settings → General → Keyboard → Keyboards → Add New Keyboard → MaKeeb), but it does not enforce device memory limits. Memory work needs a device.
- The keyboard draws no background. iOS puts the keyboard's material (Liquid Glass on iOS 26) behind the input view from the host process, where the extension can't reach it, so it can't be tinted. Never paint a colour or add a glass layer over it: keys, strip and panels sit directly on the system glass. `KeyboardPalette.background` is Android-only.
- Phone-pad fields (`phonePad`, `namePhonePad`) and secure fields always get the system keyboard; iOS doesn't allow custom keyboards there.
- The App Group only exists when the binaries carry the entitlement. Simulator builds sign ad hoc for that (`Config/Shared.xcconfig`); an unsigned build (`CODE_SIGNING_ALLOWED=NO`) silently drops it and every setting falls back to its default in the keyboard. Check with `xcrun simctl get_app_container <udid> com.makeeb.ios groups`.
- Debug builds write a launch trace (`LaunchTrace.swift`) to `tmp/launch-trace.txt` in the extension's data container (`…/data/Containers/Data/PluginKitPlugin/*/tmp`): controller creation, `viewDidLoad`, bridge ready, first render and draw, appearance, and main-thread stalls over 1 s. If iOS waits too long for the controller it drops it and shows the system keyboard, so read the trace before blaming the code: on 2026-09-28 the same launch took 0.4 s on a quiet host and over 13 s on an overloaded one (two iOS 27 beta simulators with crash-looping daemons plus an emulator).
- To attach the debugger: Xcode → Debug → Attach to Process by PID or Name → `MaKeebKeyboardExtension`, then bring up the keyboard in any app. For logs: `xcrun simctl spawn booted log stream --predicate 'process == "MaKeebKeyboardExtension"'`.
- Never log typed text. If a log line has to include something user-derived, mark it `privacy: .private`.
- Use throwaway simulators only (`build-and-verify`).

## App Review (4.4 / 4.4.1)

- The keyboard works without Full Access and without network, provides a way to reach the next keyboard, launches no apps except Settings, keeps Return as Return, and has no ads, marketing or in-app purchases in the extension. Any data collection must only serve the keyboard itself.
- No private APIs: no responder-chain `openURL`, and no host-bundle-ID lookup (that broke in iOS 26.4).
- The container app's description must say that it ships a keyboard.
