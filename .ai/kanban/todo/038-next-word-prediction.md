---
id: 38
title: Next-word prediction
type: feature
priority: P1
effort: L
milestone: '1.0'
category: prediction-autocorrect
android: full
ios: full
modules: [engine:prediction]
seen_in: [AOSP LatinIME, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Suggest likely next words after a space, using n-gram (bigram or trigram) statistics from the dictionary and the user's own history.

## Platform notes

iOS provides no usable next-word API to extensions (KeyboardKit says Apple removed it in iOS 16), so MaKeeb must ship its own model.

## Acceptance criteria

- [ ] After a space mid-sentence, the strip shows three likely next words, the best in the middle
- [ ] The statistics come from openly licensed corpora, pinned by SHA-256 and memory-mapped with the dictionary
- [ ] Completions and autocorrect don't get worse
- [ ] iOS memory with the larger pack is measured

## Tasks

- [ ] Trigram statistics from the Leipzig corpora in an NGRM section of the pack
- [ ] Predictions in the strip after a space; the toolbar stays at a sentence start
- [ ] Harness runs; check on the Pixel

## Progress

Not started.
