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
modules: [app:ios, engine:emoji, shared:keyboard]
created: 2026-09-28
---

Measure phys_footprint of the extension with dictionaries loaded; set a budget (~30 MB) and a regression check.

## Acceptance criteria

- [x] The extension's memory is measured by a repeatable script
- [x] Budgets: typing at most 30 MB, a whole session at most 60 MB
- [x] Heavy emoji use no longer pushes the extension to the limit
- [ ] Device numbers and the real limit, measured on an iPhone

## Tasks

- [x] MemoryTrace and scripts/ios-memory-check.py
- [x] 18 pt panel emoji and a compact emoji catalogue
- [x] MemoryGuard recycles the process on hide above 60% of the limit

## Progress

Measured and guarded (iPhone 17 simulator, 2026-09-28). Budgets: typing ≤ 30 MB (now 19 MB); whole session ≤ 60 MB (a heavy session with every emoji category, search, clipboard and quick settings: 52 MB, was 69–74 MB). scripts/ios-memory-check.py runs the session UI test and reads the extension's MemoryTrace. Findings: iOS's emoji font costs ~18 MB on the first emoji drawn, and Core Text keeps every emoji glyph drawn forever (~52 KB each at panel size, ~20 KB at 18 pt). Fixes: panel emoji at 18 pt (smaller than Apple's own keyboard; your call), a compact emoji catalogue (no objects until a category or search needs them), and MemoryGuard exiting the process on hide above 60% of the limit (verified: the keyboard cold-starts in ~2 s afterwards). Still to do on a real iPhone once MaKeeb is enabled there: device numbers and the real limit (os_proc_available_memory). Dictionary packs will be memory-mapped (APP-110). Pinned by the user (2026-09-29): keep 18 pt emoji and recycling for now; emoji are secondary.
