---
id: 123
title: Install and run the iOS keyboard (simulator)
type: chore
priority: P0
effort: M
milestone: MVP
category: foundation
android: full
ios: full
modules: [shared:keyboard, app:ios]
created: 2026-09-28
---

Install the app and keyboard extension on the iOS simulator, enable MaKeeb in Settings, switch to it in a text field and type: the first time the native iOS renderer runs for real.

## Acceptance criteria

- [x] MaKeeb installs, is enabled and types on the simulator
- [x] The visual test cases pass, or are filed on their cards

## Tasks

- [x] Install, enable and type on the simulator
- [x] Run the visual test cases

## Progress

MaKeeb is installed, enabled and typing on the simulator: 25 of 30 cases pass or are by design; the rest are filed on their cards. The launch problem was memory pressure on the Mac (see APP-127).
