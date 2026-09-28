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

- [x] Holding a key or flicking up on it opens its alternates
- [x] Releasing at once types the first alternate; sliding picks another
- [x] A slower move up still slides to the key above
- [x] The alternates match the layout's language (German on QWERTZ, French on AZERTY)

## Tasks

- [x] Flick detection in TouchController (distance, time, direction), tested with a virtual clock
- [x] Per-language alternates for QWERTZ and AZERTY
- [x] Try the flick on the Pixel

## Progress

Long-press or a quick upward flick (≥20dp within 250 ms, mostly vertical) opens the alternates; releasing right away types the first one (the top row's digit, or é on 'e' with the number row on). A slower move up still slides to the key above. Alternates follow the layout's language: German first on QWERTZ (ä ö ü ß), French first on AZERTY (é è à ç ù). Unit-tested with a virtual clock; flick verified on the Pixel ('É').
