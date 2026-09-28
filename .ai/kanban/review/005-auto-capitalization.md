---
id: 5
title: Auto-capitalization
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:input, core:common]
seen_in: [AnySoftKeyboard, HeliBoard, AOSP LatinIME, Gboard, Apple Keyboard]
created: 2026-09-27
---

Automatically shift at sentence starts and respect the text field's requested capitalization mode (words, sentences, characters, none).

## Platform notes

Android: EditorInfo/getCursorCapsMode. iOS: autocapitalizationType trait plus documentContextBeforeInput, which only exposes limited context.

## Acceptance criteria

- [x] Sentences start with a capital
- [x] The field's capitalisation mode (sentences, words, characters, none) is respected

## Tasks

- [x] Read the mode from each platform's field attributes
- [x] Engine tests; check on both platforms (VT-05, VT-16)

## Progress

Sentence, word and all-caps modes from the field attributes; unit-tested and verified on both platforms (VT-05, VT-16).
