---
id: 124
title: Visual test plan, run on Android and iOS
type: docs
priority: P1
effort: L
milestone: '1.0'
category: foundation
android: full
ios: full
modules: [shared:companion, app:ios]
created: 2026-09-28
---

A written, repeatable visual test plan (docs/testing/visual-test-plan.md) covering every keyboard state, field type, theme and orientation, executed on both platforms with side-by-side comparisons against the stock keyboard.

## Acceptance criteria

- [x] A written plan covers every keyboard state, field type, theme and orientation (VT-01 to VT-30)
- [x] It has been run on Android and iOS, with its findings filed on their cards

## Tasks

- [x] docs/testing/visual-test-plan.md
- [x] The iOS test harness
- [x] Run on the Pixel and the simulator

## Progress

Plan: docs/testing/visual-test-plan.md (VT-01..30). Executed on Android (Pixel 6 Pro) and iOS (iPhone 17 simulator); report and comparison sheets in .ai/local/visual-test/2026-09-27/ (report.md, sheets/). iOS harness: detects the keyboard via key accessibility identifiers, finds fields by test tag, confirms focus and resets settings per pass. Findings are filed on their cards.
