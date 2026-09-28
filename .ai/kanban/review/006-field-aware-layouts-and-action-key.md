---
id: 6
title: Field-aware layouts and action key
type: feature
priority: P0
effort: M
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:layout, engine:input, core:model, feature:keyboard, app:ios]
seen_in: [AnySoftKeyboard, HeliBoard, FlorisBoard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Adapt the layout to the field type (email '@' and '.com', URL, number, date/time, password) and label the action key from the field's IME action (Go, Search, Send, Next).

## Platform notes

iOS: secure text fields and phone-pad fields always switch to the system keyboard, so a custom keyboard never sees them.

## Acceptance criteria

- [x] E-mail fields offer @; URL fields offer / and .com with more domains on a long press
- [x] Number and phone fields open the number or phone pad
- [x] The action key shows the field's action (Go, Search, Send, Next)

## Tasks

- [x] Field-aware letters layouts in the shared engine, with tests
- [x] The phone pad's letter captions, space glyph and + under 0
- [x] Check on the emulator and the simulator

## Progress

Field-aware letters layouts from the shared engine: E-mail has @ in place of the comma, URL has / and .com (long-press for .net/.org/.io/.co.uk) with the Go key. The phone pad has ABC/DEF captions, a space-bar glyph and + under 0. Verified on the Android emulator and the iOS simulator; layout and engine tests added.
