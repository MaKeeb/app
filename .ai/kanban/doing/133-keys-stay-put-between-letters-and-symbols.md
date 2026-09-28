---
id: 133
title: Keys stay put between letters and symbols
type: feature
priority: P0
effort: S
milestone: MVP
category: core-typing
android: full
ios: full
modules: [engine:layout]
created: 2026-09-28
---

Switching letters/symbols/more-symbols keeps the bottom row, row heights and shared keys (shift/backspace positions, number row digits) exactly where they were; only the characters change.

## Acceptance criteria

- [ ] Switching between letters, symbols and more symbols moves no key that is on both pages
- [ ] The bottom row and the row heights stay the same
- [ ] Checked in text, e-mail and URL fields, with and without the number row

## Tasks

- [x] One row structure for letters, symbols and more symbols
- [x] Hold ABC for the number pad in place of the 1234 key
- [ ] Angle brackets and braces behind the parentheses

## Progress

Requested 2026-09-28 (iOS): the symbols page adds a 1234 key that shrinks and moves the space bar; with the number row on, letters have 5 rows and symbols 4, so every row changes height; e-mail/URL fields lose their bottom row on the symbols page.
