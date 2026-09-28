---
id: 93
title: Guided setup
type: feature
priority: P0
effort: S
milestone: MVP
category: settings-sync-backup
android: full
ios: full
modules: [feature:onboarding, core:settings, shared:keyboard]
seen_in: [FlorisBoard, KeyboardKit, fcitx5-ios, Keyman]
created: 2026-09-27
---

First-run flow to enable and select the keyboard, explain permissions (Full Access on iOS), and pick languages.

## Platform notes

iOS: users must add the keyboard in Settings manually, and a deep link can only open the app's settings page.

## Acceptance criteria

- [x] Setup detects when the keyboard is enabled, on both platforms
- [x] On iOS it marks Full Access and first use once the keyboard reports them

## Tasks

- [x] Read the AppleKeyboards user default on iOS
- [x] The keyboard records Full Access and first use in the App Group
- [x] Check on the simulator

## Progress

Android detects enabled/selected. iOS now detects that MaKeeb is enabled (the AppleKeyboards user default; unknown if absent), and the keyboard reports Full Access and first use through the App Group (only possible with Full Access, so those steps stay unknown without it). Verified on the simulator: 'Add MaKeeb ✓ MaKeeb is on'. Picking languages comes with APP-17 (Ready): there is one language so far.
