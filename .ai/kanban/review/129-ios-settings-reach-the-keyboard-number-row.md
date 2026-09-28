---
id: 129
title: 'iOS: settings reach the keyboard (number row)'
type: bug
priority: P0
effort: S
milestone: MVP
category: settings-sync-backup
android: none
ios: full
modules: [app:ios]
created: 2026-09-28
---

The companion writes settings to the App Group and the keyboard reads them. Simulator builds were unsigned (CODE_SIGNING_ALLOWED=NO), which drops the App Group entitlement, so no setting (number row, theme, height) ever reached the iOS keyboard.

## Acceptance criteria

- [x] Settings made in the companion reach the iOS keyboard

## Tasks

- [x] Simulator builds sign ad hoc (Shared.xcconfig) so the App Group stays
- [x] The docs and the test runner no longer build unsigned

## Progress

Fixed and verified end to end on the simulator: turning on Number row in the companion puts the digit row on the iOS keyboard (hints removed, keyboard grows by 0.8 rows). Cause: unsigned simulator builds (CODE_SIGNING_ALLOWED=NO) dropped the App Group entitlement. Simulator builds now sign ad hoc (Shared.xcconfig), and the docs and test runner no longer build unsigned.
