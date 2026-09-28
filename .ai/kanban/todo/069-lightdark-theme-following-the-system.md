---
id: 69
title: Light/dark theme following the system
type: feature
priority: P0
effort: S
milestone: MVP
category: theming-appearance
android: full
ios: full
modules: [core:model, ui:theme]
seen_in: [AnySoftKeyboard, Fossify Keyboard, FlorisBoard, HeliBoard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Built-in light and dark themes that follow the system setting, with optional time-based switching.

## Acceptance criteria

- [ ] Light and dark follow the system
- [ ] A scheduled theme is dark between two chosen hours

## Tasks

- [ ] One shared useDarkTheme() for both renderers
- [ ] Hour sliders in Settings
- [ ] Tests; check on the Pixel

## Progress

One shared palette drives both renderers. Missing: time-based switching.
