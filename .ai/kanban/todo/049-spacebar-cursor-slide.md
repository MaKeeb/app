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
modules: [engine:touch]
seen_in: [HeliBoard, Simple Keyboard (rkkr), Thumb-Key, Unexpected Keyboard, FUTO Keyboard, FlorisBoard, Gboard]
created: 2026-09-27
---

Slide horizontally on the spacebar to move the cursor character by character (optionally word by word).

## Platform notes

iOS: adjustTextPosition(byCharacterOffset:) supports horizontal movement.

## Acceptance criteria

- [ ] Sliding on the space bar moves the caret a character at a time
- [ ] A setting makes it move a word at a time

## Tasks

- [ ] Word jumps in TextBoundaries, the engine and TouchController, with tests
- [ ] The "Slide on space by word" setting
- [ ] Check on the Pixel

## Progress

Built in the shared TouchController. Missing: device check, word-by-word option.
