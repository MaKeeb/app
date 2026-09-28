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
modules: [engine:layout]
seen_in: [FlorisBoard, HeliBoard, Unexpected Keyboard, FUTO Keyboard, Trime, Keyman, Thumb-Key]
created: 2026-09-27
---

Layouts, popups, and per-state key variants (shift, field type, RTL) defined in data files parsed by shared code instead of hard-coded views. Candidate formats: FlorisBoard-style JSON (also read by HeliBoard) or CLDR Keyboard 3.0 XML.

## Platform notes

Pure shared logic and a good fit for commonMain.

## Acceptance criteria

- [ ] Letter rows and long-press alternates come from data files read by shared code
- [ ] The data is converted from AOSP LatinIME (Apache-2.0)
- [ ] Every page matches the old hand-written layouts (a parity test)
- [ ] Alternates follow the language, not the layout
- [ ] The reader adds little to the iOS keyboard framework

## Tasks

- [x] Research layout data formats
- [x] Stage A: layout data from AOSP, with a parity test
- [ ] Stage B: layouts from data, and alternates that follow the language
- [ ] Stage C: a small hand-written JSON reader in place of kotlinx.serialization

## Progress

Research done: docs/research/layout-formats.md (2026-09-28). CLDR Keyboard 3.0 has almost no touch layouts yet and FlorisBoard dropped its JSON format; recommended: a small MaKeeb JSON schema (letter rows + per-language data such as alternates) converted at build time from AOSP (Apache-2.0) with CLDR checks, while the engine keeps building the number row, function keys, bottom row and symbols pages (so the mode-switch geometry holds by construction). Stage A (today's layouts as data with a parity test) can start; later stages wait on your decisions (§9.9). Decided 2026-09-29: our own JSON schema based on AOSP; alternates follow the language.
