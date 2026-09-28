---
id: 63
title: Clipboard history
type: feature
priority: P1
effort: M
milestone: '1.0'
category: clipboard
android: full
ios: limited
modules: [engine:clipboard, platform:clipboard, feature:clipboard]
seen_in: [FlorisBoard, HeliBoard, Fossify Keyboard, FUTO Keyboard, fcitx5-android, Thumb-Key, Trime, CleverKeys, Urik, KeyboardKit, Gboard, SwiftKey]
created: 2026-09-27
---

Local history of copied text with tap-to-paste, configurable retention and size, and an option for a private keyboard-internal clipboard.

## Platform notes

Android 10+: only the focused default IME can read the clipboard. iOS: requires Full Access, and copies can only be captured while the keyboard is running (verify).

## Acceptance criteria

- [x] Copied text is kept in a panel, and a tap pastes it
- [x] On iOS the panel explains that it needs Full Access
- [ ] Retention and size settings
- [ ] The Android clipboard case (VT-26) passes on the emulator

## Tasks

- [x] Android and iOS clipboard panels

## Progress

Android (Compose) and iOS (native UIKit) panels. iOS needs Full Access and says so when it isn't granted. Android clipboard case (VT-26) still to run on the emulator.
