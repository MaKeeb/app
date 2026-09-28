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
modules: [feature:onboarding]
seen_in: [FlorisBoard, KeyboardKit, fcitx5-ios, Keyman]
created: 2026-09-27
---

First-run flow to enable and select the keyboard, explain permissions (Full Access on iOS), and pick languages.

## Platform notes

iOS: users must add the keyboard in Settings manually, and a deep link can only open the app's settings page.

## Acceptance criteria

- [ ] Setup detects when the keyboard is enabled, on both platforms
- [ ] On iOS it marks Full Access and first use once the keyboard reports them

## Tasks

- [ ] Read the AppleKeyboards user default on iOS
- [ ] The keyboard records Full Access and first use in the App Group
- [ ] Check on the simulator

## Progress

Android detects enabled/selected; iOS shows instructions. Missing: detecting on iOS whether MaKeeb is enabled; picking languages.
