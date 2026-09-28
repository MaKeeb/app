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
modules: [engine:input]
seen_in: [AnySoftKeyboard, FlorisBoard, HeliBoard, Gboard, SwiftKey]
created: 2026-09-27
---

Manual and automatic incognito that stops learning, history and emoji recents, turned on automatically when an app requests no personalized learning.

## Platform notes

Android: IME_FLAG_NO_PERSONALIZED_LEARNING. iOS has no equivalent hint beyond secure fields (verify).

## Acceptance criteria

- [ ] Incognito turns on in fields that ask for no personalised learning
- [ ] A toggle in the strip turns it on by hand, and it stays on until turned off
- [ ] Incognito stops learning, clipboard history and emoji recents

## Tasks

- [ ] The strip toggle
- [ ] No emoji recents while incognito
- [ ] KeyboardSession tests; check on the Pixel

## Progress

Respects the field’s no-learning flag; no manual toggle yet. Missing: manual toggle; confirm emoji recents aren't recorded while incognito.
