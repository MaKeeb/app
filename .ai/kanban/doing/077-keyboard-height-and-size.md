---
id: 77
title: Keyboard height and size
type: feature
priority: P1
effort: S
milestone: '1.0'
category: one-handed-floating-split
android: full
ios: full
modules: [core:settings, shared:keyboard]
seen_in: [Simple Keyboard (rkkr), HeliBoard, FlorisBoard, AnySoftKeyboard, Gboard]
created: 2026-09-27
---

Adjust keyboard height, width and bottom offset, stored per orientation.

## Platform notes

iOS: height is set with constraints on the input view, while width follows the screen (verify).

## Acceptance criteria

- [ ] Height and bottom gap are set separately for portrait and landscape
- [ ] Keys stop at 840 dp/pt wide and are centred on wider screens
- [ ] In landscape the keys stay clear of the camera cutout and a side navigation bar
- [ ] Rotating with the keyboard up on the iPhone keeps the right size

## Tasks

- [x] Height and bottom gap per orientation
- [ ] Cap the key width on wide screens
- [ ] Size settings per orientation in the companion
- [ ] Keyboard background under a landscape camera cutout

## Progress

Height scale 80–130%. Missing: width and bottom offset, stored per orientation.
