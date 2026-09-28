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
modules: [platform:feedback, core:settings, shared:keyboard, feature:settings, app:android]
seen_in: [AnySoftKeyboard, HeliBoard, Fossify Keyboard, Thumb-Key, FUTO Keyboard, LeanType, Gboard, Apple Keyboard]
created: 2026-09-27
---

Configurable vibration strength and key click sounds (optionally sound packs), respecting system silent or do-not-disturb state.

## Platform notes

iOS: haptic (and per KeyboardKit docs, audio) feedback only works when the user grants Full Access.

## Acceptance criteria

- [x] Vibration strength and click volume are settings
- [x] Clicks stay quiet in silent or vibrate mode and Do Not Disturb
- [x] On Android the system's touch-feedback setting still applies
- [x] iOS gives haptics (with Full Access) and the system click
- [ ] Sound packs (optional)

## Tasks

- [x] Strength and volume sliders in Settings → Feedback
- [x] Android: vibration at the chosen amplitude, tagged as touch feedback; clicks at the chosen volume
- [x] iOS: impact feedback at the chosen intensity
- [x] Measure on the Pixel

## Progress

Vibration strength and click volume sliders (Settings → Feedback). Android: Vibrator with amplitude (predefined tick/click/heavy-click without amplitude control), tagged as touch feedback so the system's touch-feedback setting still applies; clicks at the chosen volume, silent in silent/vibrate mode and Do Not Disturb. iOS: impact feedback at the chosen intensity (Full Access), the system input click (silent switch applies; no volume control). Verified on the Pixel: 18 ms at amplitude 0.50, usage TOUCH. Sound packs (optional) not built; say if you want them.
