---
id: 35
title: Autocorrect
type: feature
priority: P0
effort: L
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [engine:prediction, engine:input]
seen_in: [HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Replace likely typos on space or punctuation using edit distance, keyboard proximity and word frequency, with adjustable aggressiveness and a per-language off switch.

## Acceptance criteria

- [ ] Corrections are costed as typing errors on the layout's key positions, plus how rare the word is
- [ ] A word is corrected only when the correction beats it by a margin
- [ ] Names, digits and capitals are left alone
- [ ] Where each letter was tapped counts
- [ ] A strength setting: Modest, Normal, Aggressive
- [ ] Corrections pause while a selected language has no dictionary, and Settings says why
- [ ] Suggestions cost well under a frame per key on a phone

## Tasks

- [x] Keyboard-aware typo costs and a margin over the typed word
- [ ] Tap points from the touch layer
- [ ] The strength setting, and the pause for languages without a dictionary

## Progress

Stage 3 core done (2026-09-29): corrections are costed as typing errors with LatinIME's weights and the letters layout's key positions (neighbour slips cheap, far keys costlier with distance, skipped letters cheaper than extra ones, doubled letters forgiven), plus how rare the word is; autocorrect only when that beats keeping the typed word by a margin, only against a full lexicon, never for digits, capitals, capitalised words mid-sentence (names), never into a proper noun from lower case, and with bigger margins for short words and rare targets. Harness (AOSP en_US): typos fixed 0% → 72.6%, false corrections 0, unknown names/slang changed 1 of 27, strip has the target for 80.8% of typos. Pixel 6 Pro: "hwllo wirld … kiraly … kitcgen" → "Hello world … kiraly … kitchen". Still to do: aggressiveness setting and per-language switch (after the language work merges), real tap points instead of key centres, suggestions off the main thread.
