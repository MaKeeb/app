---
id: 15
title: Latin layout variants
type: feature
priority: P0
effort: S
milestone: MVP
category: layouts-languages
android: full
ios: full
modules: [engine:layout, ui:components]
seen_in: [AnySoftKeyboard, HeliBoard, Urik, Hacker's Keyboard, Gboard]
created: 2026-09-27
---

QWERTY, QWERTZ, AZERTY plus alternative layouts (Dvorak, Colemak, Workman) selectable independently of the language.

## Acceptance criteria

- [x] QWERTY, QWERTZ, AZERTY, Dvorak, Colemak and Workman can be picked
- [x] Every layout has a to z and fits the keyboard's width

## Tasks

- [x] Dvorak, Colemak and Workman
- [x] Rows wider than the layout's standard width are compressed to fit
- [x] More than three choices in Settings wrap as chips
- [x] Check on the Pixel

## Progress

QWERTY, QWERTZ, AZERTY, Dvorak (with its ' , . top row), Colemak and Workman. Layouts declare a standard width; a wider row (Dvorak's nine-letter bottom row) is compressed to fit. Settings shows more than three choices as wrapping chips. Unit tests: every layout has a-z and fits. Verified on the Pixel 6 Pro.
