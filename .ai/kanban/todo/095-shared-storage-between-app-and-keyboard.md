---
id: 95
title: Shared storage between app and keyboard
type: feature
priority: P0
effort: S
milestone: MVP
category: settings-sync-backup
android: full
ios: full
modules: [core:settings]
seen_in: [KeyboardKit, fcitx5-ios]
created: 2026-09-27
---

Settings, dictionaries and themes shared between the companion app and the keyboard process.

## Platform notes

iOS: needs an App Group, and Apple's guide ties the shared container to Open Access, so a fallback is needed when Full Access is denied. Android: same app process, so this is trivial.

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

App Group user defaults; the extension reloads on every appearance. Settings verified end to end on iOS (App Group, number row). Missing: sharing dictionaries and themes. Dictionaries now shared (2026-09-29): the companion writes downloaded packs to the App Group, the extension maps them read-only (APP-37). Themes wait for the theme editor (APP-71).
