---
id: 34
title: Word completion
type: feature
priority: P0
effort: M
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [engine:prediction, engine:dictionary]
seen_in: [HeliBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Frequency-ranked completion of the word being typed from a compact on-device dictionary (trie or DAWG), including diacritic-insensitive matching ('naive' finds 'naïve').

## Platform notes

Shared engine in commonMain. Dictionary memory footprint is critical inside the iOS extension.

## Acceptance criteria

- [ ] The word being typed is completed from the dictionary, most frequent first
- [ ] Matching ignores case, accents, letter expansions and apostrophes ("naive" finds "naïve")
- [ ] A typed form that differs from a word only by accents or an apostrophe corrects to it (im → I'm)

## Tasks

- [ ] KeyFold, and packs declaring keyFold=fold-v2
- [ ] The in-memory trie folds the same way and keeps several spellings per key
- [ ] Run the typing harness; check on the Pixel

## Progress

Trie completions over a ~250-word starter list. Missing: a real dictionary (starter list only), diacritic-insensitive matching. The AOSP en_US pack is mapped since APP-110 (2026-09-28).
