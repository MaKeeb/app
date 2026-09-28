---
id: 35
title: Autocorrect
type: feature
priority: P0
effort: L
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [engine:prediction, engine:input]
seen_in: [HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Replace likely typos on space or punctuation using edit distance, keyboard proximity and word frequency, with adjustable aggressiveness and a per-language off switch.

## Acceptance criteria

- [ ] Corrections are costed as typing errors on the layout's key positions, plus how rare the word is
- [ ] A word is corrected only when the correction beats it by a margin
- [ ] Names, digits and capitals are left alone
- [ ] Where each letter was tapped counts
- [ ] A strength setting: Modest, Normal, Aggressive
- [ ] Corrections pause while a selected language has no dictionary, and Settings says why
- [ ] Suggestions cost well under a frame per key on a phone

## Tasks

- [ ] Keyboard-aware typo costs and a margin over the typed word
- [ ] Tap points from the touch layer
- [ ] The strength setting, and the pause for languages without a dictionary

## Progress

Damerau edit distance + frequency; applied on space and punctuation. Missing: keyboard-proximity weighting, aggressiveness setting, per-language off switch. Since APP-135 (2026-09-28): only known typos are corrected and never with the caret inside a word; the real rework follows docs/research/dictionaries-autocorrect.md (stage 3).
