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
modules: [core:model, engine:layout, engine:touch, engine:input, shared:keyboard, feature:keyboard, app:ios]
seen_in: [HeliBoard, FlorisBoard, Fossify Keyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Switch enabled languages/layouts via a language key, spacebar swipe or long-press menu, with the current language shown on the spacebar.

## Acceptance criteria

- [x] With two or more languages selected, the space bar names the primary one
- [x] Holding the space bar offers the selected languages, and sliding to one makes it the primary
- [x] Sliding along the space bar still moves the cursor
- [x] Screen readers can pick a language too

## Tasks

- [x] KeyAction.SelectLanguage and the space bar's alternates on every page
- [x] KeyboardSession.selectLanguage stores the new order
- [x] Check on the Pixel and in test21_spaceLanguage

## Progress

Built 2026-09-29: with two or more languages selected, the space bar names the primary one (on every page with a space bar) and holding it offers the selected languages; sliding to one and releasing makes it the primary (first in Settings → Languages), which puts its accents first. Sliding along the space bar still moves the cursor; screen readers get the languages as custom actions ('Space, Magyar'). On iOS without Full Access the choice lasts until the keyboard is next shown (the App Group is read-only). Pixel: English + Magyar → space reads English, hold → English | Magyar, slide to Magyar → space reads Magyar, o offers ó ö ő first. iOS simulator (test21_spaceLanguage): space reads English, hold and slide to Magyar → space reads Magyar; VoiceOver label 'Space, English'. Not built: switching layouts with the language (the layout stays the user's own choice), a dedicated language key.
