---
id: 84
title: Works fully offline
type: feature
priority: P0
effort: S
milestone: MVP
category: privacy-security
android: full
ios: full
modules: [app:android]
seen_in: [HeliBoard, FUTO Keyboard, Fossify Keyboard, CleverKeys, Urik, Simple Keyboard (rkkr), LeanType]
created: 2026-09-27
---

Every typing feature works without a network connection (and on iOS without Full Access, as App Review requires). Typed text never leaves the device. Online extras such as GIFs appear only when a connection exists.

## Platform notes

iOS: Full Access is all-or-nothing (network, pasteboard, haptics, shared container), so a clear 'what works without it' matrix is needed.

## Acceptance criteria

- [x] Nothing on the typing path uses the network
- [x] Typing works on iOS without Full Access

## Tasks

- [x] Check typing on the simulator without Full Access

## Progress

No network code on the typing path. Typing verified on iOS without Full Access (the simulator never had it).
