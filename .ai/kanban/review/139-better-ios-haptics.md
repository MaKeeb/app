---
id: 139
title: Better iOS haptics
type: feature
priority: P1
effort: M
milestone: '1.0'
category: core-typing
android: full
ios: limited
modules: [platform:feedback, engine:touch, shared:keyboard]
seen_in: [Apple Keyboard, Gboard]
created: 2026-09-29
---

Crisp, immediate key haptics on iOS that feel like the system keyboard: a light, sharp tap per key with no lag, plus distinct feedback for held delete, long-press popups, space-bar cursor sliding and caps lock.

## Platform notes

iOS only: Android already has tuned haptics (APP-7). Keyboard extensions get haptics only with Full Access. The simulator has no Taptic Engine, so tuning needs a physical iPhone.

## Acceptance criteria

- [x] Key taps on iOS are light and crisp, and the first one isn't late
- [x] Delete, return and the function keys feel different from letters
- [x] Sliding between alternates, moving the cursor on the space bar and holding delete tick at each step
- [x] Android gets the same ticks
- [ ] The feel is right on a real iPhone (the simulator has no Taptic Engine)

## Tasks

- [x] A light impact for letters and space, a rigid one for function keys
- [x] Prepare the Taptic Engine when the keyboard appears and after every tap
- [x] Selection ticks from TouchController: alternates, cursor steps, delete repeats
- [x] Android: a 6 ms tick at a quarter of the key press
- [x] Check the ticks on the Pixel

## Progress

Built 2026-09-29. iOS: letters and space use a light, crisp impact; delete, return and function keys a rigid one; the Taptic Engine is prepared when the keyboard appears and after every tap, so the first tap isn't late. A selection click (the system's picker tick) marks each step of a slide: moving to another long-press alternate, each cursor step on the space bar, each repeat of a held delete. Android gets the same ticks as a short, light vibration (6 ms at a quarter of the key press). Haptics stay behind Full Access on iOS. Pixel: a space-bar slide gives 7–10 ms ticks between the 20 ms key presses. The simulator has no Taptic Engine: please judge the feel on the iPhone (next deploy).
