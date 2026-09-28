---
id: 104
title: Shared input engine
type: feature
priority: P0
effort: L
milestone: MVP
category: foundation
android: full
ios: full
modules: [engine:input]
created: 2026-09-28
---

One typing state machine for both platforms: shift, auto-cap, autocorrect, suggestions, editor actions.

## Acceptance criteria

- [x] One typing state machine drives both platforms
- [x] Unit tests cover shift, auto-caps, autocorrect, suggestions, editor actions and field-aware layouts

## Tasks

- [x] InputEngine in engine:input, with tests

## Progress

Drives Android and iOS; unit tests cover shift, auto-cap, autocorrect, suggestions, editor actions and field-aware layouts.
