---
id: 64
title: Pinned clips and snippets
type: feature
priority: P1
effort: S
milestone: '1.0'
category: clipboard
android: full
ios: limited
modules: [core:settings, shared:keyboard, feature:clipboard, feature:settings, app:ios, app:android]
seen_in: [Fossify Keyboard, FUTO Keyboard, CleverKeys, AnySoftKeyboard, Gboard, SwiftKey]
created: 2026-09-27
---

Pin clips so they never expire, and keep a list of reusable snippets or quick texts.

## Acceptance criteria

- [ ] Pinned clips never expire
- [ ] Snippets are edited in the companion and shown in the keyboard's clipboard panel
- [ ] A tap on a snippet types it
- [ ] Snippets work on iOS without Full Access

## Tasks

- [x] SnippetsRepository next to the preferences, and Settings → Snippets
- [x] Snippet chips in the clipboard panel on both platforms
- [ ] Check on iOS with test20_snippets

## Progress

Pinning works. Snippets (2026-09-29): reusable texts kept in the shared settings store (App Group on iOS; readable without Full Access), edited in the companion's Settings → Snippets (add, remove; searchable), shown as chips at the top of the keyboard's clipboard panel; a tap types one. Verified on the Android emulator (added "see you soon", tapped it into a field). iOS builds; the simulator check waits for the layout agent to free the simulator.
