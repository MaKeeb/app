---
name: kmp-module
description: Add or restructure a Gradle module in MaKeeb: pick the layer and convention plugin, wire dependencies without breaking the layering, and export types to Swift when the iOS extension needs them.
---

# Adding a module

## 1. Pick the layer

| Layer | Put here | Plugin | Source sets |
| --- | --- | --- | --- |
| `core` | value types, settings storage, tiny utilities | `makeeb.kmp.library` | commonMain (+ platform actuals only if unavoidable) |
| `platform` | a port onto an OS service | `makeeb.kmp.library` | interface in commonMain, adapters in androidMain / iosMain, no JVM adapter |
| `engine` | input logic | `makeeb.kmp.library` | commonMain only |
| `ui` | design system | `makeeb.kmp.compose` | commonMain |
| `feature` | one Compose UI slice | `makeeb.kmp.compose` | commonMain (+ platform code for OS screens) |
| `shared` | composition roots: Koin wiring, iOS frameworks | per module | see existing |
| `app` | platform wrappers only (`app/android`, `app/ios`) | `makeeb.android.application` | Android entry points |
| `tools` | build-time JVM tools, never shipped (`tools/dictionaries`) | `kotlin-jvm` (catalog alias `libs.plugins.kotlin.jvm`) | main + test; alias `jvmTest` to `test` so `./gradlew jvmTest` runs them |

All paths below are relative to `makeeb/`, the Gradle root. A module depends only on its own layer or lower. Feature modules never depend on other feature modules; compose them in the `shared` layer. `:testing` holds fakes for `commonTest`.

## 2. Create it

1. `include(":layer:name")` in `settings.gradle.kts`, under the right layer comment.
2. `layer/name/build.gradle.kts`:
   ```kotlin
   plugins {
       id("makeeb.kmp.library") // or makeeb.kmp.compose
   }

   kotlin {
       sourceSets {
           commonMain.dependencies {
               api(project(":core:model"))          // types that appear in this module's public API
               implementation(project(":core:common"))
           }
           commonTest.dependencies {
               implementation(project(":testing"))
           }
       }
   }
   ```
3. Sources go in `layer/name/src/commonMain/kotlin/com/makeeb/layer/name/`. The Android namespace is derived from the path automatically.
4. Tests go in `src/commonTest/...` and run with `./gradlew :layer:name:jvmTest` (library modules only; Compose modules have no JVM target).

## 3. Dependencies

- Add every external library to `gradle/libs.versions.toml` with an explicit `version.ref`. Don't use BOMs: an implementation-scoped platform doesn't reach other modules' source sets.
- Use `api` for types that appear in your public signatures, `implementation` otherwise.
- Koin only in `shared/*`, the platform wrappers, and feature modules that own ViewModels (expose `val <name>Module = module { viewModelOf(::X) }`). Engine, core and platform code stays DI-free.

## 4. If the iOS keyboard extension needs it

The extension links only `:shared:keyboard` (framework `MaKeebKeyboard`). Before adding a dependency there:
- It must not pull in Compose or call `UIApplication` (extension-unsafe).
- Mind memory: nothing that loads large data eagerly.
- If Swift needs a type from the module by its plain name, add `export(project(":layer:name"))` to the framework block in `shared/keyboard/build.gradle.kts`, and make the dependency `api`. Otherwise Swift sees a prefixed name such as `ModelKeyboardPalette`.

## 5. Verify

Run `./gradlew jvmTest :app:android:assembleDebug`, then link both iOS frameworks (see `build-and-verify`). Update `.ai/instructions.md` → Layout if you added a new area.
