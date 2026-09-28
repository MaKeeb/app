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
modules: [engine:touch, feature:keyboard]
seen_in: [HeliBoard, AOSP LatinIME, fcitx5-android, Fossify Keyboard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Enlarged bubble showing the pressed character above the finger, with an option to disable it.

## Platform notes

iOS: an extension cannot draw outside its own view, so top-row callouts need reserved headroom or an alternative design (verify).

## Acceptance criteria

- [ ] The preview is larger than the key and stays inside the keyboard
- [ ] It stands out from the keys in light and dark, on both platforms
- [ ] No preview in password fields, and a setting turns it off

## Tasks

- [ ] Size and position in the shared TouchController, with tests
- [ ] Popup colours, shadow and a 32 pt/sp label on Android and iOS
- [ ] The alternates popup gets the same contrast and shadow
- [ ] Check on the emulator and the simulator

## Progress

Preview bubble stays inside the keyboard (overlaps the strip), as iOS requires. Visual test (2026-09-28): Both platforms: the bubble is key-width and barely lighter than the keys; Gboard's is larger and contrasting (VT-10). Missing: a larger, contrasting bubble like Gboard's (VT-10).
