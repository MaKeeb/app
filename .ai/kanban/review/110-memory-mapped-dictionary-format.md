---
id: 110
title: Memory-mapped dictionary format
type: feature
priority: P0
effort: L
milestone: MVP
category: foundation
android: full
ios: full
modules: [platform:storage, engine:dictionary, engine:prediction, shared:keyboard, tools:dictionaries, app:android, app:ios]
created: 2026-09-28
---

Compact binary dictionaries read from mapped files, off the Kotlin heap, to fit the iOS extension memory ceiling (~48–70 MB).

## Acceptance criteria

- [x] Dictionaries are read from memory-mapped files, off the Kotlin heap
- [x] The English pack (from AOSP LatinIME) is built at build time into both apps
- [x] No heap growth on Android, and typing stays within the iOS extension's budget
- [x] Known typos are still corrected with the big list

## Tasks

- [x] The MKD format, ByteRegion and MappedDictionary
- [x] Build en_US from the pinned AOSP list
- [x] Known typos first with the big list
- [x] Benchmark the per-key cost

## Progress

Stage 1 done (2026-09-28). en_US pack (MKD v1, memory-mapped, 160,668 words from the pinned AOSP LatinIME list, offensive words blocked) built at build time into the APK (uncompressed asset) and the iOS extension bundle; the keyboard serves the starter list until it is mapped (ready ~42 ms after start on both). Pixel 6 Pro: no heap growth; benchmark build per-key cost p50 ~1.1 ms, p95 2.3-5 ms; 1.7% janky frames (0.4% before). iPhone simulator: typing peak 19.4 MB, session 53.9 MB. Harness: keystroke savings 21.5% → 35.0%, typo target in the strip 41% → 76%. Autocorrect still only fixes known typos (can't/won't restored over the big list's cant/wont; nad → and, not NAD). Next: Stage 2 (APP-34: diacritics, apostrophes) and Stage 3 (beam search off the main thread).
