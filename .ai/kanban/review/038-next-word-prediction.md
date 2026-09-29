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

- [x] After a space mid-sentence, the strip shows three likely next words, the best in the middle
- [x] The statistics come from openly licensed corpora, pinned by SHA-256 and memory-mapped with the dictionary
- [x] Completions and autocorrect don't get worse
- [ ] iOS memory with the larger pack is measured

## Tasks

- [x] Trigram statistics from the Leipzig corpora in an NGRM section of the pack
- [x] Predictions in the strip after a space; the toolbar stays at a sentence start
- [x] Harness runs; check on the Pixel

## Progress

Merged to main (2026-09-29): trigram next-word statistics from the Leipzig news and web corpora (CC BY 4.0, pinned by SHA-256), stored as an NGRM section in en_US.mkd (+2.6 MB, memory-mapped). After a space mid-sentence the strip shows three predictions, best in the middle, where the punctuation shortcuts were; at a field or sentence start the toolbar stays. Harness: keystroke savings 34.7% → 41.0% with completions alone, 46.3% when tapping predictions; next-word top-3 29.7%; autocorrect unchanged (0 false corrections). To confirm: no predictions at sentence start (keeps the toolbar reachable); CC BY needs a licences screen before release; the first build downloads 517 MB of corpora. Pixel (2026-09-29): after "Thank you for " the strip shows your | the | sharing; tapping "the" types it with its space and offers quarter | first | current. On the iPhone since the 2026-09-29 deploy: please check predictions after a space. iOS memory with the larger pack (6.5 MB, memory-mapped) is not measured yet.
