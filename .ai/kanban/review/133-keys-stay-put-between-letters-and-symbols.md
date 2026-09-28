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
modules: [engine:layout, engine:touch, app:ios]
created: 2026-09-28
---

Switching letters/symbols/more-symbols keeps the bottom row, row heights and shared keys (shift/backspace positions, number row digits) exactly where they were; only the characters change.

## Acceptance criteria

- [x] Switching between letters, symbols and more symbols moves no key that is on both pages
- [x] The bottom row and the row heights stay the same
- [x] Checked in text, e-mail and URL fields, with and without the number row

## Tasks

- [x] One row structure for letters, symbols and more symbols
- [x] Hold ABC for the number pad in place of the 1234 key
- [x] Angle brackets and braces behind the parentheses

## Progress

Letters, symbols and more-symbols now share one row structure and the field's bottom row: only characters and the mode key's label change, and nothing that exists on two pages moves (0.0 pixel difference on shared keys; iPhone 17 simulator in Text/E-mail/URL fields with and without the number row, and the Pixel 6 Pro with the number row). The 1234 key is gone: holding ABC on a symbols page opens the number pad. With the number row on, the symbols page keeps the short digit row and gains ~ = | % < > [ ] { }. Dvorak narrows only its letters so shift and backspace keep their place. < > [ ] { } are also held behind ( and ). The number pad hold isn't discoverable yet.
