---
id: 81
title: Screen reader support
type: feature
priority: P1
effort: L
milestone: '1.0'
category: accessibility
android: full
ios: full
modules: [engine:layout, engine:touch, feature:keyboard, shared:keyboard, app:ios, app:android]
seen_in: [AOSP LatinIME, Gboard, Apple Keyboard]
created: 2026-09-27
---

Full TalkBack/VoiceOver support: announced keys and suggestions, explore-by-touch with lift-to-type, and accessible panels.

## Platform notes

Custom Compose-drawn keys need explicit semantics. Compose Multiplatform accessibility inside an Android IME window and an iOS extension needs early validation.

## Acceptance criteria

- [x] Every key is a screen-reader button with a spoken label
- [x] Double-tap and lift-to-type type through the same path as a tap
- [x] Long-press alternates are custom actions
- [ ] Suggestions and a pending autocorrection are announced
- [ ] Checked by a person with VoiceOver on an iPhone and TalkBack on a phone

## Tasks

- [x] Shared spoken labels (Key.spokenLabel)
- [x] Android: semantics for every key in KeyboardKeys
- [x] iOS: labels and custom actions in KeyboardView
- [x] KeyboardAccessibilityTest (instrumented, UiAutomation) on the Pixel

## Progress

Keys are screen-reader buttons on both platforms (2026-09-29). Spoken labels are shared (Key.spokenLabel in engine:layout): "Capital A" when shifted, "Shift, on", "Caps lock", "Delete", "Symbols", "Letters", the return key's action. Android: every key is its own accessibility node (role Button) whose click types through TouchController.perform, the same path as a tap, so TalkBack's double-tap and lift-to-type work; long-press alternates are custom actions (é on e), the globe key's keyboard list is the long-click. iOS: the UIAccessibilityElements read the same labels and offer alternates as VoiceOver custom actions. KeyboardAccessibilityTest (Android instrumented, UiAutomation, the API TalkBack uses) passes 5/5 on the Pixel: labels, typing, delete, shift and capitals, alternates, mode keys. Found on the way: `adb shell input` bypasses TalkBack, so only the accessibility API can test this. Not done: announcing a pending autocorrection. To check: VoiceOver on the iPhone (deployed 2026-09-29) and TalkBack on the Pixel.
