---
id: 135
title: Autocorrect stops damaging real words
type: bug
priority: P0
effort: S
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [engine:prediction, engine:input]
created: 2026-09-28
---

Until a real dictionary lands, autocorrect only fixes known typos (teh → the) instead of turning any word missing from the 250-word starter list into a listed neighbour (cat → at), and never corrects with the caret inside a word.

## Acceptance criteria

- [x] Autocorrect fixes only known typos until a real dictionary lands
- [x] No correction with the caret inside a word

## Tasks

- [x] KnownTypos
- [x] Check on the Pixel

## Progress

Autocorrect now rewrites only known English typos and missing apostrophes (teh → the, dont → don't, im → I'm) plus the capitals of a known word (i → I); anything else near a starter-list word stays as typed with the neighbours offered as suggestions (cat stays cat; it used to become at). No correction with the caret inside a word. Verified on the Pixel 6 Pro: "my cat is ok dont teh end i " → "My cat is ok don't the end I " (2026-09-28). Stopgap until the dictionary work (Stage 0 of the research plan).
