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
modules: [engine:touch, feature:keyboard, shared:keyboard, app:android]
seen_in: [FlorisBoard, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Hacker's Keyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Low-latency tap-to-type key grid that handles fast overlapping touches (rollover), cancelled touches and edge taps correctly. The rendering and hit-testing pipeline everything else builds on.

## Platform notes

iOS keyboard extensions run under a tight memory ceiling, so renderer and Compose Multiplatform overhead must be measured early (see platform research).

## Acceptance criteria

- [x] Fast overlapping taps type in the order they were pressed
- [x] A touch the system cancels types nothing
- [x] Taps on edges and gaps reach the nearest key
- [x] The engine costs well under a frame per key on a phone
- [ ] Fast two-thumb typing on a phone comes out right (multi-touch can't be injected over adb)

## Tasks

- [x] Rollover: a finger landing while another holds a key types the earlier one; modifiers wait for their own release
- [x] Android: Compose's synthetic cancel no longer types the key
- [x] Tests for rollover, cancel and edge taps
- [x] Measure per-key cost and frame times on the Pixel (KeyLatency, a benchmark build)

## Progress

Rollover: when a finger lands while another still holds a character or space, the earlier key types at once, so fast overlapping taps come out in press order ("hi", not "ih"); held modifiers wait for their own release. Android: Compose's synthetic cancel (the system taking the gesture, e.g. a navigation swipe from the space bar) no longer types the key; iOS already mapped touchesCancelled. Edge taps (corners, the strip overlap, the gap beside the indented row) reach the nearest key (tests). Latency on the Pixel 6 Pro (2026-09-28): the engine costs 1.8 ms p50 / 2.8 ms p95 per key on the main thread (debug KeyLatency log); frames in the new R8 benchmark build are 8 ms p50 / 13 ms p95 with 0.4% janky. Multi-touch can't be injected over adb without root: please try fast two-thumb typing on your phone.
