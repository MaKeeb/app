---
id: 17
title: Fast language switching
type: feature
priority: P0
effort: S
milestone: MVP
category: layouts-languages
android: full
ios: full
seen_in: [HeliBoard, FlorisBoard, Fossify Keyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Switch enabled languages/layouts via a language key, spacebar swipe or long-press menu, with the current language shown on the spacebar.

## Acceptance criteria

- [ ] With two or more languages selected, the space bar names the primary one
- [ ] Holding the space bar offers the selected languages, and sliding to one makes it the primary
- [ ] Sliding along the space bar still moves the cursor
- [ ] Screen readers can pick a language too

## Tasks

- [ ] KeyAction.SelectLanguage and the space bar's alternates on every page
- [ ] KeyboardSession.selectLanguage stores the new order
- [ ] Check on the Pixel and in test21_spaceLanguage

## Progress

Decided 2026-09-29: long-press alternates follow the language, not the layout; other languages download during setup.
