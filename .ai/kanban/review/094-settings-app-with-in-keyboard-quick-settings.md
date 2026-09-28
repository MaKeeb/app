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
modules: [core:settings, core:model, shared:keyboard, shared:surface, feature:settings, shared:companion, platform:host, app:android, app:ios]
seen_in: [FlorisBoard, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Gboard, SwiftKey]
created: 2026-09-27
---

Companion app for all settings with search, plus a quick-settings panel reachable from the keyboard.

## Platform notes

Shared Compose Multiplatform UI for both platforms.

## Acceptance criteria

- [x] The companion has every setting, with search
- [x] A quick-settings panel in the keyboard changes the common settings at once
- [x] On iOS without Full Access the panel is read-only and says why

## Tasks

- [x] A title and search in Settings
- [x] The quick-settings panel with eight tiles, on both platforms
- [x] "All settings" opens the companion on Android
- [x] Check on the Pixel and the simulator, with and without Full Access

## Progress

Settings has a title and search (every word must match the row's title, subtitle, section or keywords). In-keyboard quick settings: the strip's gear opens a panel of 8 tiles (number row, suggestions, autocorrect, auto-caps, key popup, vibration, sound, theme) on both platforms. Taps write through to the shared preferences, and the keyboard follows at once. On iOS writes need Full Access; without it the panel is read-only and says why. Android's 'All settings' opens the companion on its Settings tab. iOS keys now run to the key gap (no 10pt side inset). Verified on the Pixel 6 Pro and the iPhone 17 simulator (with and without Full Access), 2026-09-28.
