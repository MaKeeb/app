---
id: 132
title: App icon (Android and iOS)
type: feature
priority: P1
effort: S
milestone: MVP
category: theming-appearance
android: full
ios: full
modules: [app:android, app:ios]
created: 2026-09-28
---

A proper MaKeeb icon: Android adaptive icon with a themed (monochrome) layer, iOS app icon with dark and tinted variants.

## Acceptance criteria

- [x] An Android adaptive icon with a themed (monochrome) layer
- [x] An iOS icon with dark, tinted and clear variants

## Tasks

- [x] The keycap design and its shared geometry (docs/brand)
- [x] iOS: an Icon Composer icon and fallback PNGs
- [x] Android: adaptive and round icons
- [x] Check on the simulator and the Pixel

## Progress

Keycap with a geometric M on MaKeeb blue. iOS: a layered Icon Composer icon (AppIcon.icon) with real Liquid Glass; dark (light legend), tinted and clear variants render cleanly; fallback PNGs for iOS 17-25. Android: adaptive icon (gradient background, keycap foreground sized for the 66dp safe zone, monochrome layer for themed icons), round icon too. Shared geometry and a 512px Play Store render in docs/brand/. Verified on the iPhone 17 simulator home screen (light and dark) and the Pixel 6 Pro (App info), 2026-09-28.
