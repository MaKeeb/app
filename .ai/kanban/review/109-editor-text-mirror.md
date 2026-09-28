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
modules: [engine:input, platform:host, core:common, shared:keyboard, app:android, testing]
created: 2026-09-28
---

A shadow copy of the text around the caret so the engine stops doing blocking IPC reads on every key (Android getters block up to 2 s; iOS context is truncated).

## Acceptance criteria

- [x] Typing costs no InputConnection reads per key on Android
- [x] Changes made outside the keyboard drop the copy
- [x] A field that refuses input is caught before anything is deleted, with at most one read per word
- [x] Backspace never splits an emoji

## Tasks

- [x] TextMirror in engine:input
- [x] onSelectionChanged tells the keyboard's own edits from real changes
- [x] Mirror tests; every engine test runs through the mirror
- [x] Check on the Pixel

## Progress

TextMirror (engine:input) keeps the text around the caret and applies the engine's own edits to it, so typing costs no InputConnection reads on Android (it used to be about four per key). onUpdateSelection → onSelectionChanged tells late reports of our own edits from real changes (taps, app rewrites), which drop the copy. A field that silently refuses input is caught by a check before any edit that deletes what the copy claims (autocorrect, suggestion pick, revert, delete word): at most one read per word. Backspace deletes by code points from the copy (Graphemes, core:common), never splitting an emoji. iOS keeps direct reads (in-process). Verified: 7 mirror tests, all engine tests run through the mirror, and the Pixel 6 Pro (autocorrect, backspace, tap-to-move caret), 2026-09-28.
