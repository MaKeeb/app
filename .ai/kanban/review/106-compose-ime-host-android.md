---
id: 106
title: Compose IME host (Android)
type: feature
priority: P0
effort: M
milestone: MVP
category: foundation
android: full
ios: full
modules: [shared:surface]
created: 2026-09-28
---

InputMethodService hosting Compose with its own lifecycle, ViewModel store and saved-state owners.

## Acceptance criteria

- [x] The InputMethodService hosts Compose with lifecycle, ViewModel-store and saved-state owners
- [x] It runs on a phone and on the emulator

## Tasks

- [x] MaKeebInputMethodService

## Progress

Runs on the Pixel 6 Pro and the emulator; every Android visual-test case goes through it.
