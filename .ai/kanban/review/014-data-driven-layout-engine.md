---
id: 14
title: Data-driven layout engine
type: feature
priority: P0
effort: L
milestone: MVP
category: layouts-languages
android: full
ios: full
modules: [engine:layout, engine:input, core:settings, feature:settings, ui:components, app:ios]
seen_in: [FlorisBoard, HeliBoard, Unexpected Keyboard, FUTO Keyboard, Trime, Keyman, Thumb-Key]
created: 2026-09-27
---

Layouts, popups, and per-state key variants (shift, field type, RTL) defined in data files parsed by shared code instead of hard-coded views. Candidate formats: FlorisBoard-style JSON (also read by HeliBoard) or CLDR Keyboard 3.0 XML.

## Platform notes

Pure shared logic and a good fit for commonMain.

## Acceptance criteria

- [x] Letter rows and long-press alternates come from data files read by shared code
- [x] The data is converted from AOSP LatinIME (Apache-2.0)
- [x] Every page matches the old hand-written layouts (a parity test)
- [x] Alternates follow the language, not the layout
- [x] The reader adds little to the iOS keyboard framework

## Tasks

- [x] Research layout data formats
- [x] Stage A: layout data from AOSP, with a parity test
- [x] Stage B: layouts from data, and alternates that follow the language
- [x] Stage C: a small hand-written JSON reader in place of kotlinx.serialization

## Progress

Merged to main (2026-09-29, stages A-C): letter rows and per-language long-press alternates come from JSON data converted from AOSP LatinIME (Apache-2.0), read by a small strict JSON reader (+106 KB on the iOS keyboard framework; kotlinx.serialization would have cost 1 MB). A parity test covers every page against the old Kotlin layouts; English QWERTY screenshots are pixel-identical. Alternates follow the language, not the layout (Settings → Language: English, Deutsch, Français, …). Behaviour changes to confirm: English alternates now match AOSP's (ė ę ł ÿ ý ž ź ż ć č gone), and QWERTZ/AZERTY users get English alternates until they pick a language; defaulting to the device language is planned. Pixel (2026-09-29): English e → é è ê ë ē; with Deutsch picked in Settings, a → ä â à á æ ã å ā on the same QWERTY. On the iPhone since the 2026-09-29 deploy: please check alternates (hold e; Settings → Language).
