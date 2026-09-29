---
id: 137
title: Scrolling hides the keyboard in the companion app
type: bug
priority: P2
effort: S
milestone: 1.x
category: foundation
android: full
ios: full
modules: [ui:components, shared:companion, feature:settings]
created: 2026-09-29
---

Dragging the companion app's Try it and Settings lists hides the keyboard, as scrolling does in other iOS apps.

## Platform notes

iOS lists get this from UIScrollView.keyboardDismissMode. The companion's screens are Compose scrollers, not UIScrollViews, so they never did.

## Acceptance criteria

- [x] Dragging the Try it and Settings lists hides the keyboard, on both platforms
- [x] Scrolling a newly focused field into view doesn't close it

## Tasks

- [x] Modifier.dismissKeyboardOnDrag() in ui:components
- [x] Check on the Pixel

## Progress

Fixed (2026-09-29): Modifier.dismissKeyboardOnDrag() (ui:components) clears text focus once a finger drags the list past the touch slop, which hides the keyboard on both platforms, like iOS's keyboardDismissMode = .onDrag (Compose has no interactive, follow-the-finger variant). It watches the finger, not the scroll, so scrolling a newly focused field into view doesn't close the keyboard. Applied to Try it and Settings. Verified on the Pixel (Try it and Settings search). On the iPhone since the 2026-09-29 deploy: please check.
