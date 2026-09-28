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
modules: [engine:clipboard]
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

- [ ] SnippetsRepository next to the preferences, and Settings → Snippets
- [ ] Snippet chips in the clipboard panel on both platforms
- [ ] Check on iOS with test20_snippets

## Progress

Pinning works; snippets not yet. Missing: snippets.
