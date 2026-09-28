---
id: 125
title: Native Liquid Glass tab bar (iOS companion)
type: feature
priority: P1
effort: S
milestone: MVP
category: settings-sync-backup
android: none
ios: full
modules: [shared:companion, ui:components, feature:settings, feature:onboarding]
created: 2026-09-28
---

On iOS the companion app's tabs live in a real UITabBarController (one ComposeUIViewController per tab), so iOS 26 draws its Liquid Glass tab bar and earlier releases the standard one. Android keeps the Material 3 navigation bar.

## Acceptance criteria

- [x] The iOS companion uses the system tab bar (Liquid Glass on iOS 26)
- [x] Android keeps the Material 3 navigation bar

## Tasks

- [x] A UITabBarController with one Compose screen per tab

## Progress

iOS: UITabBarController with SF Symbols (checklist, gearshape, keyboard), tinted with MaKeeb's accent; one Compose screen per tab, content scrolls beneath the Liquid Glass bar. Android keeps the Material 3 bar. Settings switch rows are now single toggleable rows (screen readers read title and state).
