---
id: 10
title: Optional number row
type: feature
priority: P1
effort: S
milestone: '1.0'
category: core-typing
android: full
ios: full
modules: [engine:layout, core:settings, shared:keyboard]
seen_in: [HeliBoard, Simple Keyboard (rkkr), FlorisBoard, Gboard]
created: 2026-09-27
---

Persistent top row of digits, toggleable per user preference and shown automatically where layouts expect it.

## Acceptance criteria

- [x] A setting adds a row of digits above the letters
- [x] The row is shorter than a letter row, and the digit hints go

## Tasks

- [x] The setting in Settings → Layout
- [x] The row at 80% of a letter row

## Progress

Optional (Settings → Layout → Number row). The row is 80% of a letter row, as on Gboard, and drops the digit hints. Enabled on your Pixel now.
