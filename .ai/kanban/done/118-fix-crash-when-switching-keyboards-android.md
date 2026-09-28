---
id: 118
title: 'Fix: crash when switching keyboards (Android)'
type: bug
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [app:android]
created: 2026-09-28
---

Switching away from MaKeeb destroyed the IME service and crashed it: the lifecycle was marked DESTROYED before the framework finished tearing down the input view.

## Acceptance criteria

- [x] Switching away from MaKeeb no longer crashes it

## Tasks

- [x] Guarded lifecycle moves (moveLifecycleTo)
- [x] Switch between MaKeeb and Gboard three times on the Pixel

## Progress

Fixed with a guarded moveLifecycleTo; three MaKeeb ↔ Gboard switch cycles on the Pixel with no crash. Check by switching keyboards a few times.
