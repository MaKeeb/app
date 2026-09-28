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
modules: [engine:touch]
seen_in: [Thumb-Key, Unexpected Keyboard, FlorisBoard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Holding backspace auto-repeats and accelerates, optionally switching to whole-word deletion, without splitting composite emoji or grapheme clusters.

## Acceptance criteria

- [ ] Holding delete repeats and speeds up
- [ ] After a while it deletes whole words, unless a setting keeps it to characters
- [ ] Emoji and other grapheme clusters are never split

## Tasks

- [ ] Repeat timings in TouchController, tested with virtual time
- [ ] KeyAction.DeleteWord in the engine
- [ ] The "Hold delete to erase words" setting

## Progress

Repeat on hold works; accelerating word delete not yet. Missing: acceleration, word-by-word delete, grapheme-safe deletion check.
