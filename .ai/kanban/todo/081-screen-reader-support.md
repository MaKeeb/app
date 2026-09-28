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
modules: [app:ios, feature:keyboard]
seen_in: [AOSP LatinIME, Gboard, Apple Keyboard]
created: 2026-09-27
---

Full TalkBack/VoiceOver support: announced keys and suggestions, explore-by-touch with lift-to-type, and accessible panels.

## Platform notes

Custom Compose-drawn keys need explicit semantics. Compose Multiplatform accessibility inside an Android IME window and an iOS extension needs early validation.

## Acceptance criteria

- [ ] Every key is a screen-reader button with a spoken label
- [ ] Double-tap and lift-to-type type through the same path as a tap
- [ ] Long-press alternates are custom actions
- [ ] Suggestions and a pending autocorrection are announced
- [ ] Checked by a person with VoiceOver on an iPhone and TalkBack on a phone

## Tasks

- [ ] Shared spoken labels (Key.spokenLabel)
- [ ] Android: semantics for every key in KeyboardKeys
- [ ] iOS: labels and custom actions in KeyboardView
- [ ] KeyboardAccessibilityTest (instrumented, UiAutomation) on the Pixel

## Progress

iOS: keys and suggestions exposed as UIAccessibilityElements with the keyboardKey trait and spoken labels (also lets UI tests find keys). Android: Compose icon keys have content descriptions; explore-by-touch not yet.
