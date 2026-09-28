---
id: 109
title: Editor text mirror
type: refactor
priority: P0
effort: L
milestone: MVP
category: foundation
android: full
ios: full
modules: [engine:input, platform:host]
created: 2026-09-28
---

A shadow copy of the text around the caret so the engine stops doing blocking IPC reads on every key (Android getters block up to 2 s; iOS context is truncated).

## Acceptance criteria

- [ ] Typing costs no InputConnection reads per key on Android
- [ ] Changes made outside the keyboard drop the copy
- [ ] A field that refuses input is caught before anything is deleted, with at most one read per word
- [ ] Backspace never splits an emoji

## Tasks

- [ ] TextMirror in engine:input
- [ ] onSelectionChanged tells the keyboard's own edits from real changes
- [ ] Mirror tests; every engine test runs through the mirror
- [ ] Check on the Pixel

## Progress

Today the engine re-reads text before the cursor after each edit.
