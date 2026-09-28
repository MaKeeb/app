---
id: 111
title: iOS extension memory budget
type: chore
priority: P0
effort: M
milestone: MVP
category: foundation
android: full
ios: full
modules: [shared:keyboard]
created: 2026-09-28
---

Measure phys_footprint of the extension with dictionaries loaded; set a budget (~30 MB) and a regression check.

## Acceptance criteria

- [ ] The extension's memory is measured by a repeatable script
- [ ] Budgets: typing at most 30 MB, a whole session at most 60 MB
- [ ] Heavy emoji use no longer pushes the extension to the limit
- [ ] Device numbers and the real limit, measured on an iPhone

## Tasks

- [ ] MemoryTrace and scripts/ios-memory-check.py
- [ ] 18 pt panel emoji and a compact emoji catalogue
- [ ] MemoryGuard recycles the process on hide above 60% of the limit

## Progress

Not started.
