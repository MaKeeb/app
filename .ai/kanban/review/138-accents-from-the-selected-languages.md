---
id: 138
title: Accents from the selected languages
type: feature
priority: P0
effort: M
milestone: MVP
category: layouts-languages
android: full
ios: full
modules: [core:settings, engine:layout, engine:touch, engine:input, feature:settings, feature:keyboard, shared:keyboard, app:ios]
seen_in: [Gboard, SwiftKey]
created: 2026-09-29
---

Pick any number of languages in Settings; every letter's long-press offers the accents of all of them, on whichever layout. The layout only places letters; the selected languages decide the character set.

## Acceptance criteria

- [x] Settings → Languages takes any number of languages, the first being the primary
- [x] Every letter's long press offers the accents of all selected languages, primary first, on any layout
- [x] Layout files can't add letters
- [x] Before the user picks, the phone's own languages are used
- [x] A key with more options than fit across wraps them onto rows above

## Tasks

- [x] The layout.languages preference, carrying over layout.language
- [x] Merged accents from the language files
- [x] A multi-row popup in TouchController
- [x] Language chips and the accents line in Settings
- [x] Check on the Pixel and in UI tests 19a to 19d

## Progress

Built (2026-09-29): Settings → Languages takes any number of languages (chips; the first picked is the primary), with a line listing every accent they add up to. Every letter's long-press merges the selected languages' accents, primary first, on any layout; layout files may no longer add letters (validated). Stored as layout.languages (comma-separated; the old single layout.language carries over); before the user picks, the phone's own languages. The popup starts under the finger and fans out right then left (AOSP's order), and wraps onto rows stacked upwards when a key has more than fits across (every bundled language at once gives o 11 options). Pixel: English + Svenska + Magyar → o offers ó ô ö ò œ ø ō õ ő, a offers à á â ä æ ã å ā ą; Settings shows the combined set. iOS simulator (UI tests 19a–d): the same Languages row and merged popups; with English + Svenska + Magyar, o's digit and nine accents fill one row (a float rounding bug had split it into two rows of five on iOS, where the keys fill the width exactly; fixed with a tolerance and a test). To check on your phones: Settings → Languages, pick yours, hold a vowel.
