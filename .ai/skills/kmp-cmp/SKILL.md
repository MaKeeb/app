---
name: kmp-cmp
description: Kotlin Multiplatform and Compose Multiplatform for MaKeeb: targets and source sets, ports versus expect/actual, Kotlin/Native threading and memory, Objective-C/Swift export rules, framework configuration, Compose performance in the keyboard surface, CMP in the companion app, and upgrading Kotlin, CMP and AGP together. Use when writing shared Kotlin, exposing API to Swift, changing build logic or dependencies, or optimising Compose.
---

# Kotlin Multiplatform and Compose Multiplatform

To add or move a module, use `kmp-module`. This skill covers writing the code inside one.

Read versions from `libs.versions.toml`. On 2026-09-27 they were Kotlin 2.4.20, Compose Multiplatform 1.12.1 (Material 3 1.9.0, versioned separately), AGP 9.4.1, coroutines 1.11.0 and Koin 4.2.2.

## Targets and source sets

| Convention plugin | Used by | Targets | Unit tests |
| --- | --- | --- | --- |
| `makeeb.kmp.library` | core, platform, engine, `:shared:keyboard` | Android (AGP's `com.android.kotlin.multiplatform.library`), JVM, iosArm64, iosSimulatorArm64 | `commonTest` on the JVM: `./gradlew jvmTest` |
| `makeeb.kmp.compose` | ui, feature, `:shared:surface`, `:shared:companion` | Android, iosArm64, iosSimulatorArm64 | None (no JVM target) |

- The default hierarchy gives you `iosMain` (shared by both iOS targets), `appleMain` and `nativeMain`. Put iOS code in `iosMain`, not in per-target source sets.
- The JVM target exists only for tests. `core:common`'s `Logger` has a `jvmMain` actual so tests link. Platform modules have no JVM adapter; tests use the fakes in `:testing`.
- Compose modules have no JVM target, so logic you want to unit-test doesn't belong in them. Move it into an engine module.
- Only arm64 iOS targets exist. There is no x86_64 simulator build.

## Platform code: ports first

- Default to an interface in `commonMain`, implemented in `androidMain`/`iosMain` and passed in through a constructor (`TextHost`, `SystemClipboard`, `HapticFeedback`). It's easy to fake in tests and keeps DI out of the engine.
- Use `expect`/`actual` only for small, stateless leaves such as `Logger`. Avoid `expect class`.
- Kotlin calls into `platform.UIKit.*` / `platform.Foundation.*` belong in `iosMain` of `:platform:*` or `:shared:keyboard`. Nothing the extension links may touch `UIApplication` (`ios-engineering`).

## Kotlin/Native

- Objects can be shared across threads; there's no freezing. `InputEngine`, `TouchController` and every UIKit object are still main-thread only. Use `MainScope()` / `Dispatchers.Main`, which is the main queue on iOS.
- Keep large data off the Kotlin heap (memory-map it), and avoid per-keystroke allocations on hot paths. Wrap interop-heavy loops in `autoreleasepool {}`.
- Mark anything Swift calls that can throw with `@Throws`. Any other exception that reaches Swift crashes the process.
- Cold Kotlin/Native links take minutes. Iterate with `jvmTest` and link frameworks only to verify the iOS side.

## Exposing Kotlin to Swift

Swift sees the Objective-C export. Shape the API for it:
- Types in the framework module and its `export(...)`ed modules keep their plain names. Types from other modules get module prefixes (`kmp-module` §4). `export` needs an `api` dependency.
- Top-level functions become static methods on a `<File>Kt` class. Prefer members of a class or object.
- Swift doesn't see default arguments, so it has to pass every parameter. For Swift callers, add explicit overloads.
- A `sealed` hierarchy becomes a class hierarchy with no exhaustive `switch`. An `enum class` becomes a class with static members, not a Swift enum.
- `suspend` functions become completion-handler/`async` methods. `Flow` is not usable directly: use a callback plus a cancel handle (`KeyboardExtensionBridge.observe` → `RenderSubscription`), unless the roadmap's SKIE decision changes that for the whole project.
- Generics survive on classes but are lost on interfaces. Kotlin `Int` is `Int32` in Swift and `Long` is `Int64`.
- `@ObjCName` renames, `@HiddenFromObjC` hides, and `@ShouldRefineInSwift` marks API meant to be wrapped by Swift (all opt-in).
- Keep each framework's Swift-facing surface in one place (`KeyboardExtensionBridge` for the extension, `MainViewController()` for the companion).

## Compose

Compose runs in the Android IME (`:shared:surface` and the feature modules) and in both companion apps. It does not run in the iOS extension.
- **A keystroke must not recompose the whole keyboard.** Read fast-changing state (pressed keys, preview, popup) as late as possible: in draw (`Canvas`, `drawBehind`) or in lambda modifiers (`Modifier.offset { }`, `graphicsLayer { }`), not as parameters of the whole key grid. Give lists stable keys (`key(...)`).
- Strong skipping is on by default in the Kotlin 2.x Compose compiler. Unstable parameters are compared by instance, so don't rebuild unchanged lists or data objects on every emission. Mark UI models `@Immutable` when they are.
- Measure recompositions with Android Studio's Layout Inspector before and after optimising.
- No `Popup` or `Dialog` inside the IME (`android-engineering`).
- Keyboard colours come from `KeyboardTheme` (built from `KeyboardPalette`, so iOS matches). The companion uses `AppTheme`.
- In `commonMain`, use JetBrains' multiplatform AndroidX forks (`org.jetbrains.androidx.lifecycle`, `…navigation`). Use plain AndroidX artifacts only in `androidMain`.
- ViewModels live in feature modules, with their Koin module (`settingsModule`, `onboardingModule`). The IME's ViewModel store comes from `MaKeebInputMethodService`.
- On iOS the companion is a `ComposeUIViewController` (`MainViewController()`). The app's Info.plist needs `CADisableMinimumFrameDurationOnPhone` for ProMotion.

## Dependencies and upgrades

- Every library goes in the catalog with an explicit version, and no BOMs.
- Kotlin (and with it the Compose compiler plugin), CMP, AGP, coroutines and any Kotlin compiler plugin such as SKIE move together. Before bumping, check JetBrains' Kotlin ↔ CMP compatibility page, the AGP KMP-plugin release notes, and whether Kotlin/Native supports the installed Xcode beta. `kotlin.apple.xcodeCompatibility.nowarn` only hides that warning.
- The configuration cache is on, so build logic must be compatible with it.
- After any bump, run `jvmTest`, the APK, both iOS frameworks and `xcodebuild` (`build-and-verify`).
