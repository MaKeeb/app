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
modules: [engine:prediction, engine:input, engine:dictionary, core:model, core:settings, feature:settings, tools:dictionaries]
seen_in: [HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Replace likely typos on space or punctuation using edit distance, keyboard proximity and word frequency, with adjustable aggressiveness and a per-language off switch.

## Acceptance criteria

- [x] Corrections are costed as typing errors on the layout's key positions, plus how rare the word is
- [x] A word is corrected only when the correction beats it by a margin
- [x] Names, digits and capitals are left alone
- [x] Where each letter was tapped counts
- [x] A strength setting: Modest, Normal, Aggressive
- [x] Corrections pause while a selected language has no dictionary, and Settings says why
- [x] Suggestions cost well under a frame per key on a phone

## Tasks

- [x] Keyboard-aware typo costs and a margin over the typed word
- [x] Tap points from the touch layer
- [x] The strength setting, and the pause for languages without a dictionary

## Progress

Stage 3 core done (2026-09-29): corrections are costed as typing errors with LatinIME's weights and the letters layout's key positions (neighbour slips cheap, far keys costlier with distance, skipped letters cheaper than extra ones, doubled letters forgiven), plus how rare the word is; autocorrect only when that beats keeping the typed word by a margin, only against a full lexicon, never for digits, capitals, capitalised words mid-sentence (names), never into a proper noun from lower case, and with bigger margins for short words and rare targets. Harness (AOSP en_US): typos fixed 0% → 72.6%, false corrections 0, unknown names/slang changed 1 of 27, strip has the target for 80.8% of typos. Pixel 6 Pro: "hwllo wirld … kiraly … kitcgen" → "Hello world … kiraly … kitchen". Real tap points (2026-09-29): the touch layer reports where each letter was tapped and the engine keeps taps aligned with the word, so a tap on the edge towards the intended key is a near-free slip while a dead-centre tap costs a normal neighbour slip. Finished 2026-09-29: Settings → Autocorrect strength (Modest / Normal / Aggressive), tuned with the typing harness: Modest raises the bar (fixes 53.8% of typos, changes no name or slang), Normal is unchanged (72.6%, 1 of 27 slang words changed), Aggressive searches further instead of lowering the bar (two-letter words, a second slip from five letters: 79.1%, still 1 of 27; lowering the bar only changed more names). Per-language: while a selected language has no full lexicon (only English ships today), corrections are offered first in the strip but not applied, and Settings says why ("Suggests corrections without making them while Magyar has no dictionary yet"); a true per-language switch needs per-word language detection (APP-18). Harness now simulates finger taps (Gaussian, σ 0.22 key widths): passing the tap points fixes 79.6% of those typos vs 77.4% without (Modest 66.7% vs 57.0%), no clean word changed. Suggestions stay on the main thread: measured 1.1 ms p50 / 2–5 ms p95 per key on the Pixel, well inside a frame. Pixel: strength row and pause note shown; with Magyar selected "hwllo" stays, with English alone "hwllo wirld … kitcgen" → "Hello world … kitchen". Found: a typo committed once is learned and then never corrected; the learning threshold is being fixed in APP-39.
