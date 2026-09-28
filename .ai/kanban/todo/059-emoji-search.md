---
id: 59
title: Emoji search
type: feature
priority: P1
effort: M
milestone: '1.0'
category: emoji-symbols-media
android: full
ios: full
modules: [engine:emoji]
seen_in: [HeliBoard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Search emoji by name or keyword in the user's language, using CLDR annotations. Among the most-requested features in several open-source keyboards.

## Platform notes

Needs text entry inside the keyboard itself (a search field that captures keystrokes), which both platforms allow.

## Acceptance criteria

- [ ] Emoji can be searched by name or keyword from inside the keyboard
- [ ] The catalogue is every RGI emoji with CLDR names and keywords
- [ ] The keyboard keeps its size while searching
- [ ] Keywords in languages other than English

## Tasks

- [ ] Generate the catalogue from Unicode and CLDR data (scripts/generate-emoji-data.py)
- [ ] Search and ranking in the engine
- [ ] The strip turns into a query box and the letters type into it
- [ ] Check on the Pixel and the simulator (test15_emojiSearch)

## Progress

Search exists in the catalog; needs an in-keyboard search field.
