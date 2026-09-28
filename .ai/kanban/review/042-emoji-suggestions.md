---
id: 42
title: Emoji suggestions
type: feature
priority: P1
effort: S
milestone: '1.0'
category: prediction-autocorrect
android: full
ios: full
modules: [core:model, engine:emoji, engine:input, shared:keyboard, feature:settings, core:settings]
seen_in: [FlorisBoard, HeliBoard, SwiftKey, Gboard, Apple Keyboard]
created: 2026-09-27
---

Offer matching emoji in the suggestion strip when a typed word matches an emoji name or keyword.

## Acceptance criteria

- [x] A typed word that names an emoji offers it in the strip
- [x] Picking it replaces the word and records a recent, except in incognito
- [x] A setting turns it off

## Tasks

- [x] Match exact names, then keywords; the most used emoji on a tie
- [x] The Emoji suggestions setting
- [x] Check on the Pixel and the simulator (test18_emojiSuggestion)

## Progress

Typing a whole word that names an emoji offers it in the strip's right-hand slot (exact name first, then an exact keyword; the most used emoji on a tie: pizza → 🍕, love → 😍, dog → 🐕); picking it replaces the word and records it in emoji recents (not in incognito). Words of 3+ letters only; a setting (Emoji suggestions, on by default) turns it off. Verified on the Pixel 6 Pro and the iPhone 17 Pro simulator ("i want pizza" → "I want 🍕 "), 2026-09-28.
