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
modules: [core:settings, shared:keyboard, shared:surface, feature:settings, ui:components, app:android, app:ios]
seen_in: [Simple Keyboard (rkkr), HeliBoard, FlorisBoard, AnySoftKeyboard, Gboard]
created: 2026-09-27
---

Adjust keyboard height, width and bottom offset, stored per orientation.

## Platform notes

iOS: height is set with constraints on the input view, while width follows the screen (verify).

## Acceptance criteria

- [x] Height and bottom gap are set separately for portrait and landscape
- [x] Keys stop at 840 dp/pt wide and are centred on wider screens
- [x] In landscape the keys stay clear of the camera cutout and a side navigation bar
- [ ] Rotating with the keyboard up on the iPhone keeps the right size

## Tasks

- [x] Height and bottom gap per orientation
- [x] Cap the key width on wide screens
- [x] Size settings per orientation in the companion
- [x] Keyboard background under a landscape camera cutout

## Progress

Done 2026-09-29. Portrait and landscape each have a height (80–130%) and a bottom gap (0–48 dp/pt), in Settings → Layout with a Reset; the old height carries over to portrait. At 100% a landscape phone gets short rows (about 37 dp on the Pixel: rows fit 9% of the screen height), tablets keep full rows. Keys stop at 840 dp/pt wide and are centred on wider screens; touches beside them still reach the nearest key. Android: the gap adds to the navigation-bar padding; in landscape the keyboard background now runs under the camera cutout while the keys stay clear of it and of a side navigation bar. iOS: the extension's height includes the gap, keys and panels end above it. Pixel: portrait gap 48 dp lifts the keys, landscape 120% gives taller rows centred with margins, Reset restores defaults. iOS simulator: portrait and landscape UI tests pass. To check on the iPhone: rotate with the keyboard up (if it keeps the old size, applySize() should also run from viewWillTransition).
