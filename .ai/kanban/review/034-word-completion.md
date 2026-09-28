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
modules: [engine:dictionary, engine:prediction, tools:dictionaries]
seen_in: [HeliBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Frequency-ranked completion of the word being typed from a compact on-device dictionary (trie or DAWG), including diacritic-insensitive matching ('naive' finds 'naïve').

## Platform notes

Shared engine in commonMain. Dictionary memory footprint is critical inside the iOS extension.

## Acceptance criteria

- [x] The word being typed is completed from the dictionary, most frequent first
- [x] Matching ignores case, accents, letter expansions and apostrophes ("naive" finds "naïve")
- [x] A typed form that differs from a word only by accents or an apostrophe corrects to it (im → I'm)

## Tasks

- [x] KeyFold, and packs declaring keyFold=fold-v2
- [x] The in-memory trie folds the same way and keeps several spellings per key
- [x] Run the typing harness; check on the Pixel

## Progress

Stage 2 done (2026-09-29). Dictionary keys fold case, diacritics, letter expansions (ß→ss, æ→ae, ø→o, ł→l) and apostrophes (KeyFold, pack keyFold=fold-v2, the in-memory trie folds the same way and now keeps several spellings per key). So "naive" offers Naïve beside Naive, "caf" completes to café, "don" to don't. A typed form that isn't a word but matches one except for accents or an apostrophe autocorrects to it (im → I'm, cafe → café; its/were/ill stay). Best-first completion from Stage 1. Harness: keystroke savings 35.0%, no false corrections, typo target in the strip 74.8%. Verified on the Pixel 6 Pro ("naive dont im" → strip Naïve | Naive | Naively; "don't I'm").
