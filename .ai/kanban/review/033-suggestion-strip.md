---
id: 33
title: Suggestion strip
type: feature
priority: P0
effort: M
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [core:model, engine:input, feature:suggestions, shared:keyboard, app:ios]
seen_in: [HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Candidate bar with three or more suggestions (typed word, best guess, alternatives), punctuation shortcuts when idle, and case-aware suggestions that follow shift state.

## Acceptance criteria

- [x] Three fixed slots, the best word in the middle
- [x] Suggestions follow caps lock
- [x] After a word and a space in running text, the strip offers punctuation
- [x] With nothing to suggest, the strip shows its toolbar

## Tasks

- [x] One shared stripSlots() for both renderers
- [x] Capitalised suggestions under caps lock
- [x] Punctuation shortcuts, except in e-mail, URL, search and go fields
- [x] Tests; check on the emulator and the simulator

## Progress

Three fixed slots: the best word always in the middle, missing ones blank (was: split halves, best on the left; VT-16). Shared stripSlots() drives both renderers. Caps lock turns suggestions to capitals, and turning it off restores them. Right after a word and a space in running text, the strip offers , . ? !, which take the space's place ('word, '); not in e-mail, URL, search or go fields. Idle, it shows the toolbar. 'ok' added to the starter list. Unit tests for slots, punctuation and caps lock; verified on the Android emulator and the iOS simulator.
