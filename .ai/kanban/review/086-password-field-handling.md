---
id: 86
title: Password-field handling
type: feature
priority: P0
effort: S
milestone: MVP
category: privacy-security
android: full
ios: full
modules: [shared:keyboard, platform:host, engine:input]
seen_in: [AOSP LatinIME, HeliBoard, Trime]
created: 2026-09-27
---

In password fields, disable suggestions, learning, key previews and clipboard capture.

## Platform notes

iOS: secure fields always use the system keyboard.

## Acceptance criteria

- [x] Password fields get no suggestions, learning, autocorrect or key preview
- [ ] Checked on an iPhone, where secure fields use the system keyboard

## Tasks

- [x] Decide it in the shared KeyboardSession
- [x] Check on the emulator

## Progress

Password fields: no suggestions, learning or autocorrect, and now no key preview (decided in the shared KeyboardSession). Verified on the Android emulator: a held key in a password field shows no bubble. iOS hands secure fields to the system keyboard; check on the iPhone.
