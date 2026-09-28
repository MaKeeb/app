---
id: 26
title: Pluggable composition engine
type: feature
priority: P1
effort: M
milestone: '1.0'
category: input-methods-cjk-indic
android: full
ios: full
seen_in: [FlorisBoard, fcitx5-android, Trime, Hamster, FUTO Keyboard, Keyman]
created: 2026-09-27
---

An internal interface for composing input (dead keys, Hangul, Telex, kana, pinyin via librime) with preedit/composing text and candidate lists. This lets new scripts plug in without reworking the core.

## Platform notes

Android supports composing regions natively. iOS uses setMarkedText on the proxy, which some host apps handle inconsistently (verify).

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

Not started.
