---
id: 48
title: Glide / swipe typing
type: feature
priority: P1
effort: XL
milestone: '1.0'
category: gesture-swipe
android: full
ios: full
modules: [engine:gesture]
seen_in: [HeliBoard, FUTO Keyboard, AnySoftKeyboard, FlorisBoard, Urik, CleverKeys, LeanType, Gboard, SwiftKey, Apple Keyboard]
created: 2026-09-27
---

Enter whole words by sliding across letters, decoded against the dictionary and context, with a visible gesture trail, a floating preview, alternatives after each swipe, and learning of swiped words.

## Platform notes

Reuse options: FUTO Swipe (GPL C++ library, models under the FUTO Model License, MIT dataset) or HeliBoard's NLnet-funded replacement library (planned Apache-2.0, unreleased). Decoding and trail rendering are shared logic.

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

Baseline key-sequence decoder exists and is tested; not wired to the UI.
