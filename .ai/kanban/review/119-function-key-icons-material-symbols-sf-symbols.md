---
id: 119
title: Function-key icons (Material Symbols / SF Symbols)
type: feature
priority: P0
effort: S
milestone: MVP
category: theming-appearance
android: full
ios: full
modules: [core:model, engine:layout, ui:theme, feature:keyboard, shared:keyboard]
created: 2026-09-28
---

Shift, caps lock, backspace, return/action, globe and emoji keys draw real icons from a shared KeyIcon model instead of Unicode or emoji glyphs; shift state is visible (outline, filled, caps lock).

## Acceptance criteria

- [x] Function keys draw icons from a shared KeyIcon model: Material Symbols on Android, SF Symbols on iOS
- [x] The shift icon shows its state
- [x] They match Gboard on the Pixel
- [ ] Checked on an iPhone

## Tasks

- [x] The KeyIcon model
- [x] Android drawings for shift, caps lock and backspace
- [x] SF Symbols on iOS from the same size token

## Progress

Shift, caps lock and backspace are now drawn for the keyboard (broad arrow, wide delete tag, 2dp stroke) and all key icons are 16% larger (28dp, a shared KeyboardTokens size). They match Gboard on the Pixel. iOS uses SF Symbols at 22pt from the same token; not yet seen on a device.
