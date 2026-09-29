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
modules: [platform:storage, engine:dictionary, engine:prediction, engine:input, core:settings, shared:keyboard, shared:surface, feature:suggestions, feature:settings, shared:companion, app:ios, app:android]
seen_in: [HeliBoard, AOSP LatinIME, FUTO Keyboard, AnySoftKeyboard, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Learn new words and personal word pairs locally, and let users see, remove or reset learned data (for example long-press a suggestion to forget it).

## Acceptance criteria

- [x] New words are learned on the device and suggested
- [x] A word counts as known only on evidence, so a one-off typo is still corrected
- [x] Learned words persist, capped, never written on a keystroke, and safe before the first unlock
- [x] Holding a learned suggestion forgets it
- [x] The companion can remove learned words (a list on Android, a clear on iOS)
- [x] Nothing is learned in incognito or password fields
- [ ] Personal word pairs for next-word predictions

## Tasks

- [x] A private-files port for keyboard-local data
- [x] Persist learned words, capped and off the typing path
- [x] A word is known only on evidence
- [x] Long-press a learned suggestion to forget it
- [x] View, remove and clear learned words from the companion
- [x] Picking a just-forgotten word on purpose learns it back
- [x] Check on the Pixel and in test22_learnedWords

## Progress

Built 2026-09-29. Learned words persist keyboard-locally (Android: credential-encrypted no_backup storage; iOS: the extension's own container, excluded from backup), capped at 3,000 words (least used go first), saved in 5-second batches and when the keyboard hides, never on a keystroke; safe in direct boot (nothing written before the first unlock, merged after). Learning needs evidence: a new word counts as known (blocks autocorrect) only after two commits or one deliberate keep (picking the typed word, or undoing its correction), so a one-off typo is still corrected next time. Hold a learned suggestion in the strip to forget it (Forget/Cancel prompt; TalkBack/VoiceOver actions). Android Settings → Learned words lists, searches, removes and clears them; the iOS companion can only ask for a clear (through the App Group), because the extension's container is private: no list on iOS, to avoid a second copy of typed words in a shared container. No learning in incognito fields, and none in fields that turn autocorrect off (usernames, e-mail, codes) — a choice to confirm. Pixel: zorblax typed twice is suggested, still after a force-stop; hold → Forget removes it; Settings lists and removes words (test words removed again). iOS simulator (test22_learnedWords): typed twice → suggested; hold → Forget prompt → gone. Not built: learning word pairs for next-word predictions; a slang word first typed at sentence starts is stored capitalised.
