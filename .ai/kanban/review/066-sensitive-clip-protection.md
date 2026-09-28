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
modules: [platform:clipboard, shared:companion, app:ios]
seen_in: [FlorisBoard, HeliBoard, Gboard]
created: 2026-09-27
---

Never store clips flagged as sensitive (for example passwords from managers), auto-expire unpinned items, and exclude clips copied in password fields.

## Platform notes

Android 13+: ClipDescription EXTRA_IS_SENSITIVE.

## Acceptance criteria

- [x] Clips flagged sensitive are never kept, on Android and iOS
- [x] Nothing is kept while incognito
- [x] Unpinned clips expire

## Tasks

- [x] iOS: skip pasteboard items with the concealed, transient or auto-generated types
- [x] A "Copy concealed sample" button on the Try it screen
- [x] test17_sensitiveClip on the simulator
- [ ] Check Android on the emulator

## Progress

Clips flagged sensitive are never kept: Android 13+ EXTRA_IS_SENSITIVE, and now iOS pasteboard items carrying the nspasteboard.org concealed/transient/auto-generated types that password managers set. Clips copied while incognito (including password fields) are skipped; unpinned clips expire after an hour. The Try it screen has a "Copy concealed sample" button for QA. Verified on the iPhone 17 simulator by test17_sensitiveClip (the concealed sample never appears, plain text does), 2026-09-28. Still to run: the Android case on the emulator (never on your phone's clipboard).
