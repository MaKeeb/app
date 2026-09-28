---
id: 105
title: Shared touch engine
type: feature
priority: P0
effort: M
milestone: MVP
category: foundation
android: full
ios: full
modules: [engine:touch]
created: 2026-09-28
---

Hit-testing, long-press, repeat and cursor-slide in Kotlin so both renderers only draw.

## Acceptance criteria

- [x] Hit-testing, long-press, repeat and cursor slide are shared, and the renderers only draw
- [x] Unit tests run on virtual time

## Tasks

- [x] TouchController in engine:touch
- [x] Check previews, popups and repeat in the visual tests

## Progress

Drives both renderers; unit tests with virtual time; previews, popups and repeat verified in the visual tests.
