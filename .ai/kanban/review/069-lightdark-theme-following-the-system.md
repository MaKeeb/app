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
modules: [core:settings, core:common, shared:keyboard, shared:surface, feature:settings, ui:components]
seen_in: [AnySoftKeyboard, Fossify Keyboard, FlorisBoard, HeliBoard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Built-in light and dark themes that follow the system setting, with optional time-based switching.

## Acceptance criteria

- [x] Light and dark follow the system
- [x] A scheduled theme is dark between two chosen hours

## Tasks

- [x] One shared useDarkTheme() for both renderers
- [x] Hour sliders in Settings
- [x] Tests; check on the Pixel

## Progress

Light and dark follow the system, or a 'Scheduled' theme is dark between two hours (default 21:00–07:00, may wrap past midnight), set with hour sliders in Settings. One shared useDarkTheme() drives both renderers, resolved when drawn; local time via core:common's currentMinuteOfDay() (java.time / NSCalendar). Unit-tested; verified on the Pixel (light keyboard at 18:29 in system dark mode).
