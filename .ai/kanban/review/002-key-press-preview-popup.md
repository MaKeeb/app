---
id: 2
title: Key press preview popup
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:touch, core:model, feature:keyboard, app:ios]
seen_in: [HeliBoard, AOSP LatinIME, fcitx5-android, Fossify Keyboard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Enlarged bubble showing the pressed character above the finger, with an option to disable it.

## Platform notes

iOS: an extension cannot draw outside its own view, so top-row callouts need reserved headroom or an alternative design (verify).

## Acceptance criteria

- [x] The preview is larger than the key and stays inside the keyboard
- [x] It stands out from the keys in light and dark, on both platforms
- [x] No preview in password fields, and a setting turns it off

## Tasks

- [x] Size and position in the shared TouchController, with tests
- [x] Popup colours, shadow and a 32 pt/sp label on Android and iOS
- [x] The alternates popup gets the same contrast and shadow
- [x] Check on the emulator and the simulator

## Progress

The bubble is 1.4× the key's width and 1.25× its height, centred over the key and kept inside the keyboard (shared TouchController, unit-tested). Dark theme uses a clearly lighter popup colour; light theme is white with a shadow on both platforms; 32pt/sp label (KeyboardTokens.PREVIEW_TEXT_SIZE). The alternates popup got the same contrast and shadow. Still: off in password fields, and a setting turns it off. Verified on the Android emulator and the iOS simulator.
