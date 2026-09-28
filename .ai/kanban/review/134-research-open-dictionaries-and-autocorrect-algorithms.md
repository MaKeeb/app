---
id: 134
title: 'Research: open dictionaries and autocorrect algorithms'
type: spike
priority: P0
effort: M
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [docs]
created: 2026-09-28
---

Survey free/open word lists, frequency and n-gram data, and correction/prediction algorithms (licences, sizes, formats, iOS memory fit), and recommend what MaKeeb adopts for its dictionaries and autocorrect.

## Acceptance criteria

- [x] Word lists, n-gram data and correction algorithms compared by licence, size, format and iOS memory fit
- [x] A recommendation and a staged plan

## Tasks

- [x] docs/research/dictionaries-autocorrect.md

## Progress

docs/research/dictionaries-autocorrect.md (2026-09-28). Recommends building English on the AOSP LatinIME en_US list (Apache-2.0, 160k words, next-word links, offensive flags) with Leipzig (CC BY) frequencies and n-grams; a MaKeeb memory-mapped pack format (radix trie with best-descendant scores, ~7 MB for English with bi/trigrams); a LatinIME-style weighted beam search combining a touch model, edit costs and the language model, with margin-based autocorrect and explicit don't-correct rules; a simulated-typing harness; a staged plan across APP-110 → APP-34 → autocorrect → dictionary-packs/language-switching → APP-38 → APP-39. Needs your decisions first, above all the app licence (Apache-2.0 recommended; GPL data is an App Store risk).
