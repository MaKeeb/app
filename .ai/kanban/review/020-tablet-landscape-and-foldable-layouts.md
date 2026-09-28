---
id: 20
title: Tablet, landscape and foldable layouts
type: feature
priority: P1
effort: M
milestone: '1.0'
category: layouts-languages
android: full
ios: full
modules: [shared:keyboard, shared:surface, app:android, app:ios]
seen_in: [HeliBoard, Unexpected Keyboard, Gboard, Apple Keyboard]
created: 2026-09-27
---

Layouts and scaling tuned for large screens, landscape and folded or unfolded states, remembered separately per posture.

## Acceptance criteria

- [x] A landscape phone leaves the focused field visible
- [ ] Tablet layouts
- [ ] Foldable layouts, remembered per posture

## Tasks

- [x] Rows in landscape at most 9% of the screen height, with tests
- [ ] Tablet and foldable layouts

## Progress

Landscape phones: a row never exceeds 9% of the screen height (about 36dp), so the focused field stays visible (VT-29). Android passes screenHeightDp and iOS the screen height; covered by KeyboardMetricsTest. Tablet and foldable layouts remain backlog. Also seen: the iOS glass tint doesn't reach the side areas beside the keys in landscape.
