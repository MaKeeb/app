# Roadmap: scaffold → MVP

Status on 2026-09-27: the scaffold builds for Android and iOS, and 49 JVM tests pass. The Android keyboard runs on a Pixel 6 Pro (Android 17). Scripted trials there passed for autocorrect and revert, double-space period, shift and caps lock, symbols, suggestion pick, long-press alternates, space-bar cursor slide and backspace repeat. The iOS app and extension build, but the extension has not been run yet. Tickets (APP-N) are the cards on the board, `.ai/kanban`.

## Iteration 1: make it real on devices

1. APP-112: Android done on the Pixel (fixed: a crash on keyboard switch, font-glyph icons, duplicated globe/hide). Still needed on iOS: height constraint, globe key, Full Access paths, settings reload.
2. APP-109: a shadow of the text around the caret inside `:engine:input`, reconciled on `onUpdateSelection` / `textDidChange`. Removes the per-keystroke `textBeforeCursor` IPC.
3. APP-111: measure the extension's `phys_footprint` on device; add a debug overlay and a budget.
4. iOS native emoji and clipboard panels (APP-58, APP-63: extend `KeyboardRender` and `KeyboardView.swift`).

## Iteration 2: language data

1. APP-110: a compact binary dictionary format, memory-mapped (Okio/kotlinx-io on Android, `NSData` mapped on iOS), with a builder script. Check licences before choosing sources: AOSP/HeliBoard word lists are Apache-2.0; review any others.
2. APP-37 + APP-17: per-language packs; the companion app installs them into the App Group (iOS) or app storage (Android).
3. APP-14: move layouts from the Kotlin DSL to data files. Evaluate FlorisBoard's JSON format and CLDR Keyboard 3.0; k3lp, a KMP Apache-2.0 parser, may be reusable.
4. APP-39: persist the user dictionary (keyboard-local; respect incognito).

## Iteration 3: MVP polish

- APP-38 (n-grams), APP-8 (accelerating word delete), APP-11.
- APP-48: wire `:engine:gesture` into `TouchController` (path capture) and the strip; upgrade the decoder.
- APP-113: GitHub Actions for jvmTest, the APK, the iOS frameworks and xcodebuild.
- Remaining P0 cards on the board.

## Open decisions

- Keep the native iOS renderer, or run the Compose-in-extension spike (APP-116, plan in platform-apis.md §6.7)?
- Swift interop: adopt SKIE for flows and sealed types (APP-115), or keep the callback bridge?
- Distribution: Play plus F-Droid? Watch Google's developer-verification rollout (see open-source-keyboards.md).
