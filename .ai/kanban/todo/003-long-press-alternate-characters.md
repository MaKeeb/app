---
id: 3
title: Long-press alternate characters
type: feature
priority: P0
effort: M
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:touch, engine:layout]
seen_in: [HeliBoard, FlorisBoard, AnySoftKeyboard, fcitx5-android, FUTO Keyboard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Long-press (or swipe-up) on a key opens a popup of accented letters, symbols or digits defined per layout and locale.

## Acceptance criteria

- [ ] Holding a key or flicking up on it opens its alternates
- [ ] Releasing at once types the first alternate; sliding picks another
- [ ] A slower move up still slides to the key above
- [ ] The alternates match the layout's language (German on QWERTZ, French on AZERTY)

## Tasks

- [ ] Flick detection in TouchController (distance, time, direction), tested with a virtual clock
- [ ] Per-language alternates for QWERTZ and AZERTY
- [ ] Try the flick on the Pixel

## Progress

Popup with drag-to-select; digit hints first on the top row. Missing: swipe-up to open; per-locale alternates.
