---
id: 8
title: Backspace repeat and word delete
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:touch, engine:input, core:model, core:settings, feature:settings, testing]
seen_in: [Thumb-Key, Unexpected Keyboard, FlorisBoard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Holding backspace auto-repeats and accelerates, optionally switching to whole-word deletion, without splitting composite emoji or grapheme clusters.

## Acceptance criteria

- [x] Holding delete repeats and speeds up
- [x] After a while it deletes whole words, unless a setting keeps it to characters
- [x] Emoji and other grapheme clusters are never split

## Tasks

- [x] Repeat timings in TouchController, tested with virtual time
- [x] KeyAction.DeleteWord in the engine
- [x] The "Hold delete to erase words" setting

## Progress

Holding delete repeats after 400 ms at 60 ms, speeds up to 30 ms after 8 repeats, and after 20 switches to whole words every 180 ms (shared TouchController). Setting: 'Hold delete to erase words' (on by default; off keeps to characters). A word delete takes the spaces before the caret and the word before them; punctuation and emoji go one grapheme at a time, so emoji are never split (Android deletes by grapheme, iOS natively). Unit-tested with virtual time; both platforms share it.
