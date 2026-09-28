---
id: 7
title: Haptic and sound feedback
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: limited
modules: [platform:feedback, shared:keyboard]
seen_in: [AnySoftKeyboard, HeliBoard, Fossify Keyboard, Thumb-Key, FUTO Keyboard, LeanType, Gboard, Apple Keyboard]
created: 2026-09-27
---

Configurable vibration strength and key click sounds (optionally sound packs), respecting system silent or do-not-disturb state.

## Platform notes

iOS: haptic (and per KeyboardKit docs, audio) feedback only works when the user grants Full Access.

## Acceptance criteria

- [ ] Vibration strength and click volume are settings
- [ ] Clicks stay quiet in silent or vibrate mode and Do Not Disturb
- [ ] On Android the system's touch-feedback setting still applies
- [ ] iOS gives haptics (with Full Access) and the system click
- [ ] Sound packs (optional)

## Tasks

- [ ] Strength and volume sliders in Settings → Feedback
- [ ] Android: vibration at the chosen amplitude, tagged as touch feedback; clicks at the chosen volume
- [ ] iOS: impact feedback at the chosen intensity
- [ ] Measure on the Pixel

## Progress

Android: KEYBOARD_TAP + system clicks. iOS: impact haptics gated on Full Access, playInputClick. Missing: vibration strength setting, sound packs, silent/do-not-disturb check.
