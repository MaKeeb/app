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

- [ ] A double space types a full stop
- [ ] Punctuation typed after a word and a space takes the space's place ("hi ," → "hi, ")
- [ ] Only in running text, and only right after the space key

## Tasks

- [ ] The swap in the engine, also after an autocorrection, with tests
- [ ] The strip's punctuation shortcuts do the same

## Progress

Double-space period done; smart spacing around punctuation not yet. Missing: auto-space after punctuation, swapping a trailing space with punctuation ('word ,' → 'word, ').
