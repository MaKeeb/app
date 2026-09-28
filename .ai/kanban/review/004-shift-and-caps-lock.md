---
id: 4
title: Shift and caps lock
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:input]
seen_in: [FlorisBoard, HeliBoard, AnySoftKeyboard, Unexpected Keyboard, Gboard, Apple Keyboard]
created: 2026-09-27
---

One-shot shift, double-tap (or gesture) caps lock, and correct shifted labels on all keys.

## Acceptance criteria

- [x] Shift types one capital, then turns off
- [x] Double-tapping shift locks capitals
- [x] Keys show shifted labels, and caps lock differs from shift by its glyph, not only its colour

## Tasks

- [x] Shift state in the engine, with tests
- [x] Check on both platforms (VT-05, VT-06)

## Progress

Verified on both platforms (VT-05/06): one-shot, double-tap caps lock, shifted labels. Caps lock differs from one-shot by its glyph, not the key colour.
