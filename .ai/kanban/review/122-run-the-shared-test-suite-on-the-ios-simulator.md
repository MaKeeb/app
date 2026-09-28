---
id: 122
title: Run the shared test suite on the iOS simulator
type: chore
priority: P1
effort: S
milestone: '1.0'
category: foundation
android: full
ios: full
modules: [build-logic]
created: 2026-09-28
---

Execute the commonTest suites on the iosSimulatorArm64 target (Kotlin/Native), so shared logic is verified on the runtime the iOS keyboard actually uses, not only on the JVM.

## Acceptance criteria

- [x] The shared tests run on the iOS simulator and pass, module for module as on the JVM

## Tasks

- [x] scripts/ios-sim-test.sh, which boots the device first

## Progress

scripts/ios-sim-test.sh: 51/51 tests pass on the iPhone 17 simulator (iOS 27), module for module the same as on the JVM. Standalone simctl spawn hangs on Xcode 27, so the script boots the device first.
