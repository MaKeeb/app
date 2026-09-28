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
modules: [feature:suggestions, engine:input]
seen_in: [HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Candidate bar with three or more suggestions (typed word, best guess, alternatives), punctuation shortcuts when idle, and case-aware suggestions that follow shift state.

## Acceptance criteria

- [ ] Three fixed slots, the best word in the middle
- [ ] Suggestions follow caps lock
- [ ] After a word and a space in running text, the strip offers punctuation
- [ ] With nothing to suggest, the strip shows its toolbar

## Tasks

- [ ] One shared stripSlots() for both renderers
- [ ] Capitalised suggestions under caps lock
- [ ] Punctuation shortcuts, except in e-mail, URL, search and go fields
- [ ] Tests; check on the emulator and the simulator

## Progress

Best suggestion centred; toolbar when idle. iOS renders it natively. Visual test (2026-09-28): With two candidates the strip splits in halves instead of centring the best word, and 'Okay' is marked as the correction for 'Ok' (VT-16, both platforms). One iOS run showed the previous field's suggestions after a field switch (VT-11), not reproduced. Missing: punctuation shortcuts when idle, case-aware suggestions, the two-candidate layout (VT-16).
