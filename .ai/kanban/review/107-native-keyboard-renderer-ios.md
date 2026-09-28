---
id: 107
title: Native keyboard renderer (iOS)
type: feature
priority: P0
effort: M
milestone: MVP
category: foundation
android: full
ios: full
modules: [shared:keyboard, app:ios]
created: 2026-09-28
---

Core Graphics renderer for the extension, fed by KeyboardRender snapshots from Kotlin. Compose Multiplatform is not extension-safe.

## Acceptance criteria

- [x] The extension draws KeyboardRender snapshots natively, without Compose
- [x] Native emoji and clipboard panels, and the strip's toolbar
- [x] Buttons and panels are accessible

## Tasks

- [x] KeyboardView
- [x] Emoji and clipboard panels in UIKit
- [x] Check on the simulator

## Progress

Native UIKit emoji panel (category tabs, grid, ABC/delete) and clipboard panel (header, 2-column clips with Pin/Delete, a Full Access explanation). The strip shows its toolbar when there are no suggestions: clipboard, plus emoji when there is no emoji key. The buttons and panels are all accessible. Verified on the simulator.
