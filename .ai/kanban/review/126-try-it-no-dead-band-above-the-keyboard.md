---
id: 126
title: 'Try it: no dead band above the keyboard'
type: bug
priority: P1
effort: S
milestone: MVP
category: settings-sync-backup
android: full
ios: full
modules: [shared:companion, ui:components]
created: 2026-09-28
---

The Try it screen padded for the bottom bar and the keyboard at once, leaving an empty band that slid up with the keyboard on both platforms.

## Acceptance criteria

- [x] No empty band above the keyboard on the Try it screen, on both platforms

## Tasks

- [x] Consume the Scaffold padding before imePadding, and end screens with ScrollEndSpacer
- [x] Check on the simulator and the emulator

## Progress

Fixed on both platforms: the Scaffold padding is consumed before imePadding, and screens end with ScrollEndSpacer. Verified: iOS simulator (Search field sits on the keyboard) and Android emulator (Phone field sits on the keyboard, no band).
