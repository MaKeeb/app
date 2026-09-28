---
id: 117
title: 'Repo layout: makeeb/, shared/, app wrappers'
type: refactor
priority: P0
effort: M
milestone: MVP
category: foundation
android: full
ios: full
modules: [shared:keyboard, shared:surface, shared:companion, app:android]
created: 2026-09-28
---

All source under makeeb/ (the Gradle root); root keeps AI tooling, docs, .gitignore and README. KMP composition roots moved to a shared/ layer; app/ holds only the Android and iOS wrappers, each with its own entry points.

## Acceptance criteria

- [x] All source is under makeeb/; the root keeps AI tooling, docs and the README
- [x] Composition roots are in a shared/ layer, and app/ holds only the platform wrappers
- [x] jvmTest, the Android APK and the iOS app and extension build

## Tasks

- [x] Move the modules
- [x] Build both platforms

## Progress

Done: jvmTest, Android APK and the iOS app + extension build from makeeb/. Check the layout suits you.
