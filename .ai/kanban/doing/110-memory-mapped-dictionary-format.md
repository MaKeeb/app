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
modules: [engine:dictionary]
created: 2026-09-28
---

Compact binary dictionaries read from mapped files, off the Kotlin heap, to fit the iOS extension memory ceiling (~48–70 MB).

## Acceptance criteria

- [ ] Dictionaries are read from memory-mapped files, off the Kotlin heap
- [ ] The English pack (from AOSP LatinIME) is built at build time into both apps
- [ ] No heap growth on Android, and typing stays within the iOS extension's budget
- [ ] Known typos are still corrected with the big list

## Tasks

- [x] The MKD format, ByteRegion and MappedDictionary
- [x] Build en_US from the pinned AOSP list
- [ ] Known typos first with the big list
- [ ] Benchmark the per-key cost

## Progress

Stage 1 of docs/research/dictionaries-autocorrect.md: memory-mapped MKD pack, builder from the AOSP en_US list, suggestion engine on it. In progress (2026-09-28).
