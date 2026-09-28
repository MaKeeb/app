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
modules: [app:ios]
seen_in: [Apple Keyboard, KeyboardKit, HeliBoard, Gboard]
created: 2026-09-27
---

Key or long-press menu to switch to the next installed keyboard/input method.

## Platform notes

iOS: must show a switch-keyboard key when needsInputModeSwitchKey is true (App Review expectation). Android: shouldOfferSwitchingToNextInputMethod.

## Acceptance criteria

- [x] The globe key shows only when the OS asks for one
- [x] A tap switches to the next keyboard; a long press lists the keyboards

## Tasks

- [x] iOS: pass the globe key's touches to handleInputModeList(from:with:)
- [x] UI test test11_globeKey on an iPhone SE simulator

## Progress

Globe key only when the OS asks for one (iOS home-button iPhones; Android without a system switcher). Android: tap switches, long-press opens the picker. iOS: touches on our globe key go to handleInputModeList(from:with:), so a tap switches and a long press shows iOS's keyboard list. Verified on an iPhone SE (3rd gen) simulator with a new UI test (test11_globeKey): globe key drawn, long press lists Keyboard Settings / MaKeeb / English (UK) / Emoji.
