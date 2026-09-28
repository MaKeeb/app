---
id: 131
title: SwiftUI renderer for the iOS keyboard (spike)
type: spike
priority: P2
effort: M
milestone: '1.0'
category: foundation
android: none
ios: full
modules: [app:ios, shared:keyboard]
seen_in: [KeyboardKit]
created: 2026-09-28
---

Draw the KeyboardRender snapshot with SwiftUI instead of Core Graphics: native Liquid Glass APIs, simpler panels, accessibility for free. Keep a thin UIKit layer forwarding raw touches to the shared TouchController (SwiftUI gestures don't handle keyboard multitouch rollover), and keep all behaviour in Kotlin. KeyboardKit (MIT, SwiftUI) is a reference for key views and gestures. Measure memory and latency on the iPhone against the ~30 MB budget.

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

Not started.
