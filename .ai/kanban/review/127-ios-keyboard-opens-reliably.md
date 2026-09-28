---
id: 127
title: iOS keyboard opens reliably
type: bug
priority: P0
effort: M
milestone: MVP
category: foundation
android: none
ios: full
modules: [app:ios, shared:keyboard]
created: 2026-09-28
---

MaKeeb sometimes doesn't appear when selected: iOS shows the system keyboard instead, or it takes many seconds. Find the cause (launch time, crash, memory, activation) and fix it.

## Acceptance criteria

- [x] The cause of slow or missing launches is found
- [ ] MaKeeb opens reliably on a device

## Tasks

- [x] A debug launch trace with a main-thread stall watchdog
- [x] Measure launches with and without memory pressure

## Progress

Cause: the Mac is out of memory, not MaKeeb's code. It has 16 GB of RAM with 19.4 of 20.5 GB swap used and about 16 GB compressed; OrbStack takes 7.8 GB and Chrome about 10 GB. Simulator processes page in from swap, so iOS takes 4s to 60s+ to start the extension, and it drops a keyboard that takes too long, showing its own. MaKeeb itself is on screen 0.4s after iOS asks for it. Under that memory pressure the same code took 13s, against 0.12s on a quiet run. Added a debug launch trace (LaunchTrace.swift) with a main-thread stall watchdog. With free memory (or on a device) MaKeeb should open reliably.
