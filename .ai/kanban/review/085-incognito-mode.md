---
id: 85
title: Incognito mode
type: feature
priority: P0
effort: S
milestone: MVP
category: privacy-security
android: full
ios: full
modules: [engine:input, shared:keyboard, shared:surface, feature:suggestions, ui:theme, testing, app:ios]
seen_in: [AnySoftKeyboard, FlorisBoard, HeliBoard, Gboard, SwiftKey]
created: 2026-09-27
---

Manual and automatic incognito that stops learning, history and emoji recents, turned on automatically when an app requests no personalized learning.

## Platform notes

Android: IME_FLAG_NO_PERSONALIZED_LEARNING. iOS has no equivalent hint beyond secure fields (verify).

## Acceptance criteria

- [x] Incognito turns on in fields that ask for no personalised learning
- [x] A toggle in the strip turns it on by hand, and it stays on until turned off
- [x] Incognito stops learning, clipboard history and emoji recents

## Tasks

- [x] The strip toggle
- [x] No emoji recents while incognito
- [x] KeyboardSession tests; check on the Pixel

## Progress

Automatic for fields that ask for no personalised learning, plus a manual toggle in the strip (eye-slash; lit in the accent colour while on; stays on across fields until turned off). Incognito stops learning, clipboard history and emoji recents (recents were still recorded before: fixed). Toolbar buttons for an open panel light up on Android too. New KeyboardSession tests with fake clipboard and preferences; verified on the Pixel.
