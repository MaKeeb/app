---
id: 39
title: On-device learning and forgetting
type: feature
priority: P1
effort: M
milestone: '1.0'
category: prediction-autocorrect
android: full
ios: full
modules: [engine:dictionary]
seen_in: [HeliBoard, AOSP LatinIME, FUTO Keyboard, AnySoftKeyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Learn new words and personal word pairs locally, and let users see, remove or reset learned data (for example long-press a suggestion to forget it).

## Acceptance criteria

- [ ] New words are learned on the device and suggested
- [ ] A word counts as known only on evidence, so a one-off typo is still corrected
- [ ] Learned words persist, capped, never written on a keystroke, and safe before the first unlock
- [ ] Holding a learned suggestion forgets it
- [ ] The companion can remove learned words (a list on Android, a clear on iOS)
- [ ] Nothing is learned in incognito or password fields
- [ ] Personal word pairs for next-word predictions

## Tasks

- [x] A private-files port for keyboard-local data
- [x] Persist learned words, capped and off the typing path
- [x] A word is known only on evidence
- [ ] Long-press a learned suggestion to forget it
- [ ] View, remove and clear learned words from the companion
- [ ] Picking a just-forgotten word on purpose learns it back
- [ ] Check on the Pixel and in test22_learnedWords

## Progress

In-memory user dictionary; not persisted yet. Missing: persistence, viewing/removing/resetting learned words, forgetting from a suggestion.
