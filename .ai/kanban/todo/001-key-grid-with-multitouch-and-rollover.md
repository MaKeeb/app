---
id: 1
title: Key grid with multitouch and rollover
type: feature
priority: P0
effort: L
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:touch, engine:layout, feature:keyboard]
seen_in: [FlorisBoard, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Hacker's Keyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Low-latency tap-to-type key grid that handles fast overlapping touches (rollover), cancelled touches and edge taps correctly. The rendering and hit-testing pipeline everything else builds on.

## Platform notes

iOS keyboard extensions run under a tight memory ceiling, so renderer and Compose Multiplatform overhead must be measured early (see platform research).

## Acceptance criteria

- [ ] Fast overlapping taps type in the order they were pressed
- [ ] A touch the system cancels types nothing
- [ ] Taps on edges and gaps reach the nearest key
- [ ] The engine costs well under a frame per key on a phone
- [ ] Fast two-thumb typing on a phone comes out right (multi-touch can't be injected over adb)

## Tasks

- [ ] Rollover: a finger landing while another holds a key types the earlier one; modifiers wait for their own release
- [ ] Android: Compose's synthetic cancel no longer types the key
- [ ] Tests for rollover, cancel and edge taps
- [ ] Measure per-key cost and frame times on the Pixel (KeyLatency, a benchmark build)

## Progress

Shared TouchController: multitouch, slide-to-neighbour, nearest-key margins. Tested on JVM. Missing: device check of fast rollover, cancelled touches and edge taps; latency measurement.
