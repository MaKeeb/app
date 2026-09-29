---
id: 136
title: Pixel on wireless ADB
type: chore
priority: P3
effort: S
milestone: Later
category: foundation
android: full
ios: unavailable
created: 2026-09-29
---

Test on the user's Pixel over Wi-Fi instead of USB.

## Acceptance criteria

- [x] The Pixel is reachable over ADB on Wi-Fi
- [x] The build-and-verify skill says how to restore it after a reboot

## Tasks

- [x] adb tcpip 5555, then connect over Wi-Fi

## Progress

Set up 2026-09-29 at the user's request: adb tcpip 5555, connected as `<pixel-ip>:5555` (serial `<pixel-serial>`). Lasts until the phone reboots; the build-and-verify skill has the steps to restore it. Used for this session's Pixel checks.
