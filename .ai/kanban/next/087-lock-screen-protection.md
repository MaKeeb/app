---
id: 87
title: Lock-screen protection
type: feature
priority: P1
effort: S
milestone: '1.0'
category: privacy-security
android: full
ios: full
modules: [app:android, core:settings]
seen_in: [HeliBoard]
created: 2026-09-27
---

When the device is locked, hide clipboard, suggestions and learned data, and handle direct-boot storage correctly.

## Platform notes

Android direct boot: credential-protected storage is unavailable before the first unlock.

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

IME is direct-boot aware and reads device-protected prefs; lock-screen behaviour not reviewed.
