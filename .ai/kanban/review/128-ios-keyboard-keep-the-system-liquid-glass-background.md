---
id: 128
title: 'iOS keyboard: keep the system Liquid Glass background'
type: bug
priority: P0
effort: S
milestone: MVP
category: theming-appearance
android: none
ios: full
modules: [app:ios, core:model]
created: 2026-09-28
---

The iOS renderer paints an opaque background over the system keyboard's Liquid Glass. Draw only the keys and strip so the system material shows through, like Apple's keyboard.

## Acceptance criteria

- [x] The iOS keyboard draws no background: keys, strip and panels sit on the system glass

## Tasks

- [x] Remove the background from the iOS renderer
- [x] Install on the iPhone

## Progress

iOS draws no background at all: keys, strip and panels sit directly on the system keyboard glass, which runs unbroken into the globe/dictation bar. The glass is drawn by iOS in the host process, outside the extension's views, so no API can tint it; per your call there's no overlay and no extra glass layer. The theme background is Android-only. Installed on your iPhone.
