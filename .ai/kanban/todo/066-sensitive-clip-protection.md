---
id: 66
title: Sensitive clip protection
type: feature
priority: P1
effort: S
milestone: '1.0'
category: clipboard
android: full
ios: limited
modules: [platform:clipboard, shared:keyboard]
seen_in: [FlorisBoard, HeliBoard, Gboard]
created: 2026-09-27
---

Never store clips flagged as sensitive (for example passwords from managers), auto-expire unpinned items, and exclude clips copied in password fields.

## Platform notes

Android 13+: ClipDescription EXTRA_IS_SENSITIVE.

## Acceptance criteria

- [ ] Clips flagged sensitive are never kept, on Android and iOS
- [ ] Nothing is kept while incognito
- [ ] Unpinned clips expire

## Tasks

- [ ] iOS: skip pasteboard items with the concealed, transient or auto-generated types
- [ ] A "Copy concealed sample" button on the Try it screen
- [ ] test17_sensitiveClip on the simulator
- [ ] Check Android on the emulator

## Progress

Skips EXTRA_IS_SENSITIVE clips and clips copied in incognito fields; 1 h expiry. Missing: iOS detection of concealed pasteboard items; the Android clipboard case on the emulator (VT-26).
