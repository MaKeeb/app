---
id: 36
title: One-tap autocorrect undo
type: feature
priority: P0
effort: S
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [engine:input]
seen_in: [AOSP LatinIME, HeliBoard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Backspace immediately after an autocorrection restores the original word and stops correcting it again in the same spot.

## Acceptance criteria

- [x] Backspace right after an autocorrection restores the typed word
- [x] The word isn't corrected again in the same spot

## Tasks

- [x] Engine tests; check on both platforms (VT-14, VT-15)

## Progress

Unit-tested and verified on both platforms (VT-14 teh→the, VT-15 delete→teh).
