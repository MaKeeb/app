---
id: 120
title: Keyboard clears the system nav buttons (Android)
type: bug
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [app:android, shared:surface, platform:host]
created: 2026-09-28
---

Android 10+ draws a hide button and keyboard switcher under the IME. The keyboard pads for them (tappableElement insets, 48dp on a Pixel) and drops its own globe and hide buttons.

## Acceptance criteria

- [x] The bottom row clears Android's hide button and keyboard switcher

## Tasks

- [x] Pad for the tappable-element insets as well as the navigation bar
- [x] Measure on the Pixel

## Progress

Measured on the Pixel: navigationBars reports 24dp, the buttons need 48dp; now padded for both. Check that the bottom row clears ⌄ and the switcher.
