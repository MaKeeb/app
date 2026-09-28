---
id: 13
title: Next-keyboard (globe) key
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [platform:host, engine:touch]
seen_in: [Apple Keyboard, KeyboardKit, HeliBoard, Gboard]
created: 2026-09-27
---

Key or long-press menu to switch to the next installed keyboard/input method.

## Platform notes

iOS: must show a switch-keyboard key when needsInputModeSwitchKey is true (App Review expectation). Android: shouldOfferSwitchingToNextInputMethod.

## Acceptance criteria

- [ ] The globe key shows only when the OS asks for one
- [ ] A tap switches to the next keyboard; a long press lists the keyboards

## Tasks

- [ ] iOS: pass the globe key's touches to handleInputModeList(from:with:)
- [ ] UI test test11_globeKey on an iPhone SE simulator

## Progress

Globe key when the OS asks for one; long-press opens the picker on Android. Missing: iOS long-press input-mode list on our own globe key (handleInputModeList), checked on a home-button iPhone.
