---
id: 11
title: Punctuation conveniences
type: feature
priority: P1
effort: S
milestone: '1.0'
category: core-typing
android: full
ios: full
modules: [engine:input]
seen_in: [AOSP LatinIME, HeliBoard, FlorisBoard, Thumb-Key, Gboard, Apple Keyboard]
created: 2026-09-27
---

Double-space inserts a period, auto-space after punctuation, and swapping a trailing space with punctuation typed next ('word ,' becomes 'word, ').

## Acceptance criteria

- [x] A double space types a full stop
- [x] Punctuation typed after a word and a space takes the space's place ("hi ," → "hi, ")
- [x] Only in running text, and only right after the space key

## Tasks

- [x] The swap in the engine, also after an autocorrection, with tests
- [x] The strip's punctuation shortcuts do the same

## Progress

Double-space period. Punctuation typed right after a word and the space key takes the space's place ('hi ,' → 'hi, '), also after an autocorrection; only in running text, and only when the space was the previous key. Punctuation shortcuts in the strip do the same (APP-33). Auto-space after punctuation typed on the keys is deliberately left out: it would break 'e.g.', decimals and addresses; say if you want it as an option. Unit-tested; shared engine, so both platforms.
