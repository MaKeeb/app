---
id: 49
title: Spacebar cursor slide
type: feature
priority: P0
effort: S
milestone: MVP
category: gesture-swipe
android: full
ios: full
modules: [engine:touch, engine:input, core:common, core:model, core:settings, feature:settings]
seen_in: [HeliBoard, Simple Keyboard (rkkr), Thumb-Key, Unexpected Keyboard, FUTO Keyboard, FlorisBoard, Gboard]
created: 2026-09-27
---

Slide horizontally on the spacebar to move the cursor character by character (optionally word by word).

## Platform notes

iOS: adjustTextPosition(byCharacterOffset:) supports horizontal movement.

## Acceptance criteria

- [x] Sliding on the space bar moves the caret a character at a time
- [x] A setting makes it move a word at a time

## Tasks

- [x] Word jumps in TextBoundaries, the engine and TouchController, with tests
- [x] The "Slide on space by word" setting
- [x] Check on the Pixel

## Progress

Slide on the space bar: after 24dp of travel the caret moves one character per 14dp, or one word per 36dp with the new 'Slide on space by word' setting (previous word start / next word end, skipping spaces and punctuation). Verified on the Pixel: 'Hello world' + slide + x → 'Hello wxorld'. Word jumps unit-tested in TextBoundaries, the engine and the touch controller.
