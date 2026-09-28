---
id: 112
title: First on-device run
type: chore
priority: P0
effort: S
milestone: MVP
category: foundation
android: full
ios: full
created: 2026-09-28
---

Install on an Android phone and an iPhone, enable the keyboard and type: verify touch latency, insets, feedback and settings sync.

## Acceptance criteria

- [x] The keyboard runs on an Android phone and passes the scripted trials
- [ ] The keyboard runs on an iPhone

## Tasks

- [x] Install on the Pixel and run the trials
- [ ] Install on an iPhone

## Progress

Android: running on a Pixel 6 Pro (Android 17); scripted trials pass (autocorrect + revert, double-space, shift/caps, symbols, suggestions, long-press alternates, cursor slide, backspace repeat). iOS: app and extension build; next is running the extension.
