---
id: 94
title: Settings app with in-keyboard quick settings
type: feature
priority: P0
effort: M
milestone: MVP
category: settings-sync-backup
android: full
ios: full
modules: [feature:settings, shared:companion]
seen_in: [FlorisBoard, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Gboard, SwiftKey]
created: 2026-09-27
---

Companion app for all settings with search, plus a quick-settings panel reachable from the keyboard.

## Platform notes

Shared Compose Multiplatform UI for both platforms.

## Acceptance criteria

- [ ] The companion has every setting, with search
- [ ] A quick-settings panel in the keyboard changes the common settings at once
- [ ] On iOS without Full Access the panel is read-only and says why

## Tasks

- [ ] A title and search in Settings
- [ ] The quick-settings panel with eight tiles, on both platforms
- [ ] "All settings" opens the companion on Android
- [ ] Check on the Pixel and the simulator, with and without Full Access

## Progress

Companion app settings; in-keyboard quick settings not yet. Visual test (2026-09-28): Settings has no title on either platform: content starts right under the status bar (VT-30). iOS Setup doesn't detect whether MaKeeb is enabled. Missing: search, in-keyboard quick settings, a title (VT-30).
