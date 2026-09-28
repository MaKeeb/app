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

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

Layouts are a Kotlin DSL today. Candidate formats: FlorisBoard JSON or CLDR Keyboard 3.0.
