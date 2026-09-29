---
id: 18
title: Multilingual typing
type: feature
priority: P1
effort: L
milestone: '1.0'
category: layouts-languages
android: full
ios: full
modules: [engine:dictionary, engine:prediction, engine:input, engine:layout, core:settings, feature:settings, shared:keyboard]
seen_in: [HeliBoard, AnySoftKeyboard, Urik, CleverKeys, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Enable several languages (for example English, Swedish and Hungarian) and type any of them without switching. Every enabled language's dictionary stays loaded, and suggestions, autocorrect and next-word predictions come from whichever language the words being typed are in, whatever the primary language. Long-pressing the space bar picks the primary language: its layout and long-press alternates, and the tie-breaker when a word fits more than one language.

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

Requested by the user (2026-09-29): English, Swedish and Hungarian typed side by side, with the right autocorrect detected regardless of the primary language; space-bar long press switches the primary language. Design notes: (1) all enabled packs are memory-mapped at once; clean file-backed pages are cheap, but the iOS footprint with three packs still needs measuring against the 30 MB budget. (2) Candidates from every dictionary go into one ranking, weighted by which language the sentence so far looks like (each language's model scores the recent words), so a Swedish sentence prefers Swedish corrections. (3) A word that is valid in any enabled language is never autocorrected. (4) Next-word predictions follow the detected language. Depends on APP-37 (Swedish and Hungarian packs) and APP-17 (the space-bar menu). Hungarian's rich inflection needs a large word list or affix data; check licences (no GPL). The accents half is done by APP-138 (2026-09-29): the selected languages' accents are merged on one keyboard; this card is now about dictionaries, autocorrect and predictions across them.
