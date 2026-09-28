# MaKeeb: agent instructions

MaKeeb is a third-party on-screen keyboard for Android and iOS, built with Kotlin Multiplatform and Compose Multiplatform. The goal is to share as much logic as possible across both platforms. This file is the single source of instructions for every AI tool: `AGENTS.md` (Codex), `CLAUDE.md`, `CODEX.md`, `GEMINI.md` and `.github/copilot-instructions.md` are symlinks to it. Edit only `.ai/instructions.md`. Skills live in `.ai/skills` and are linked from `.claude/skills`, `.agents/skills` (Codex, Gemini CLI), `.codex/skills` and `.github/skills`; agents live in `.ai/agents`, linked from `.claude/agents` and `.github/agents`.

## Scope

- Core keyboard features only. No AI/LLM features: no generative rewriting, AI replies, tone changers or chat assistants. Statistical prediction, autocorrect, user learning and swipe decoding are core typing and are in scope.
- The feature list and its status live on the board: `.ai/kanban`, one Markdown card per feature (the `kanban` skill).
- Research that drives decisions: `docs/research/open-source-keyboards.md`, `docs/research/platform-apis.md`, `docs/research/feature-candidates.json`. They are dated snapshots (2026-09-27); recheck before relying on a version number or limit.

## Layout

The repository root holds only AI tooling, `docs/`, `.gitignore` and the README. All source lives in `makeeb/`, the Gradle root.

```
makeeb/
  build-logic/        convention plugins: makeeb.kmp.library, makeeb.kmp.compose, makeeb.android.application
  gradle/             version catalog (libs.versions.toml) and wrapper
  core/               model, common, settings                      foundations, no DI, no UI
  platform/           host, feedback, clipboard                    OS ports: commonMain interface + androidMain/iosMain adapters
  engine/             layout, input, touch, dictionary,            pure input logic, commonMain only, unit-tested on the JVM
                      prediction, gesture, emoji, clipboard
  ui/                 theme, components                            Compose design system (incl. KeyboardIcons)
  feature/            keyboard, suggestions, emoji, clipboard,     Compose UI slices; settings/onboarding own ViewModels + Koin modules
                      settings, onboarding
  testing/            fakes for the platform ports (commonTest only)
  shared/keyboard     keyboard composition root, no Compose; iOS framework "MaKeebKeyboard" (+ Swift bridge)
  shared/surface      Compose keyboard surface: strip + keys + panels
  shared/companion    companion app root (setup, settings, try-it); iOS framework "MaKeebCompanion"
  app/android         Android wrapper: manifest, resources, Application, MaKeebInputMethodService, MainActivity
  app/ios             Xcode wrapper (XcodeGen): MaKeeb app + MaKeebKeyboardExtension
docs/                 research, screenshots
.ai/                  instructions.md (this file), kanban/ (the board), skills/, agents/, commands/, plans/; local/ is gitignored
```

## Module graph

Layers, lowest first: `core` → `platform` → `engine` → `ui` → `feature` → `shared` → `app`. A module may depend on its own layer or any layer below, never above. `:testing` is used only from test source sets.

- `app/` holds only the platform wrappers: the Android application module and the Xcode project. Each platform's entry points live there (Android: `MaKeebApplication`, `MaKeebInputMethodService`, `MainActivity`; iOS: `MaKeebApp.swift`, `KeyboardViewController.swift`). Everything they host comes from `shared/`.
- Feature modules do not depend on each other. The `shared` layer composes them (e.g. `KeyboardSurface` puts the strip, keys and panels together).
- Engine modules keep all code in `commonMain` and never touch Compose, Koin or platform APIs. Anything OS-specific is a port in `:platform:*`.
- Namespaces and packages follow the path: `:engine:input` is `com.makeeb.engine.input`.
- Add modules with `.ai/skills/kmp-module`.

## Architecture rules

- **The engine owns behaviour; renderers only draw.** `InputEngine` (typing state machine), `TouchController` (hit-testing, long-press, repeat, cursor slide), `LayoutGeometry` and `KeyboardRender` are shared. Function keys are semantic `KeyIcon`s (`core:model`), drawn as Material Symbols on Android (`KeyboardIcons`) and SF Symbols on iOS. Never put Unicode arrows or emoji on keys. Android draws with Compose (`:feature:keyboard`). The iOS extension draws `KeyboardRender` snapshots natively (`app/ios/Keyboard/KeyboardView.swift`). A behaviour change belongs in Kotlin, with a test, not in a renderer.
- **The iOS keyboard extension links only `:shared:keyboard`.** Compose Multiplatform is not app-extension safe: it calls `UIApplication.shared`, needs a foreground window scene, and costs memory the extension doesn't have (docs/research/platform-apis.md §6). Never add Compose, or anything that touches `UIApplication`, to the runtime's dependency graph. `:shared:surface` still compiles for iOS so a future spike is only a linking change.
- **iOS memory ceiling is roughly 48–70 MB and the kill is silent.** Budget about 30 MB for the extension. Large data (dictionaries) must be memory-mapped, not loaded into the Kotlin heap.
- **iOS without Full Access:** no network, clipboard, haptics or sound, and read-only App Group access. Everything must still work (App Review 4.4.1). Gate those features on `hasFullAccess`.
- **Settings flow one way:** the companion app writes, the keyboard reads (`PreferencesRepository.reload()` when the iOS extension appears). Learned words stay keyboard-local.
- **Privacy:** never log typed text or clipboard contents, and typed text never leaves the device. Respect `EditorAttributes.incognito`: no learning, no clipboard history, no network features.
- **Network:** there is no "no network" policy. The keyboard must work fully offline and without iOS Full Access (App Review 4.4.1), so nothing on the typing path may depend on the network. Online extras (GIF search, pack downloads) are fine: offer them only when a connection exists (and Full Access on iOS), and degrade quietly otherwise.
- **Android system UI:** Android 10+ draws a keyboard switcher and a hide button in the navigation bar under the IME, so the Android keyboard shows neither a globe key nor a hide button. `InputMethodService` teardown calls back into `onFinishInputView`: lifecycle moves must tolerate `DESTROYED` (`moveLifecycleTo`).
- **DI:** Koin. Bindings live in the `shared` modules (`keyboardRuntimeModule`, `companionModule`) and the platform wrappers (`MaKeebApplication`, `IosKeyboardKoin`, `MainViewController`). Feature modules that own ViewModels expose their own Koin module (`settingsModule`, `onboardingModule`). Core, platform and engine code stays DI-free with plain constructors.
- **Android IPC:** `InputConnection` getters are blocking cross-process calls. Minimise reads per keystroke. A shadow text mirror is planned (board card APP-109).

## Build and test

Run Gradle from `makeeb/`. JDK 21 comes from `~/.gradle/gradle.properties` for the daemon, but `./gradlew` itself needs `JAVA_HOME`. iOS work needs the Xcode beta:

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer   # iOS tasks only

cd makeeb
./gradlew jvmTest                                   # all shared unit tests (fast)
scripts/ios-sim-test.sh                             # the same tests on the iOS simulator (Kotlin/Native)
./gradlew :app:android:assembleDebug                # Android APK
./gradlew :shared:keyboard:linkDebugFrameworkIosSimulatorArm64 :shared:companion:linkDebugFrameworkIosSimulatorArm64
cd app/ios && xcodegen generate                     # after editing project.yml
xcodebuild -project app/ios/MaKeeb.xcodeproj -scheme MaKeeb -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" build   # signs ad hoc; never CODE_SIGNING_ALLOWED=NO (drops the App Group)
```

Full steps, emulator use and gotchas: `.ai/skills/build-and-verify`. Before calling work done, run `jvmTest` plus the build for every platform you touched.

## Conventions

- Match the surrounding code: KDoc on public types explains why, not what. No commented-out code.
- Tests live in `commonTest` and run on the JVM target. Use `FakeTextHost` / `FakeKeyboardHost` from `:testing`, and `TestTimeSource` or `runTest` virtual time for timing.
- Versions come from `makeeb/gradle/libs.versions.toml` with explicit versions; no BOMs (they don't propagate across KMP source sets).
- **The board tracks all work:** `.ai/kanban`, one Markdown card per task, worked with the `kanban` skill. Read it before picking work and whenever a task finishes (the user moves cards on the dashboard and by hand), then take Ready to start from the top. Move the card to In progress, implement and verify, rewrite its Progress, tick the acceptance criteria that are met, move it to In review and commit. Only the user moves a card to Done. Work without a card (a bug fix, a restructure, research) gets one, created with the task.
- One commit per finished, verified task, carrying its card's move and Progress. The subject starts with the card's ticket: `APP-12: What changed`. Board upkeep (new cards, reordering, the user's moves) is its own commit, `Board: <what changed>`. No Co-Authored-By or other AI attribution trailers. Push only when asked. Keep investigation notes and logs under `.ai/local/`.
- Always pass `adb -s <serial>`. The user's Pixel 6 Pro (`<pixel-serial>`) may be used for trials only when they ask; record its current keyboard first and restore it afterwards. `<car-thing-serial>` is a Car Thing (Linux, not Android): never target it. Otherwise use the emulator (`emulator-5554`).
- **This repository is public: never commit device identifiers or secrets** (serials, UDIDs, IP or MAC addresses, Apple team IDs, keys, tokens). Write placeholders instead: `<pixel-serial>`, `<pixel-ip>`, `<car-thing-serial>`, `<ios-device-udid>`, `<simulator-udid>`, `<team-id>`. The real values live in `.ai/local/devices.md`, which is never committed; signing settings stay in the ignored `makeeb/app/ios/Config/Local.xcconfig`.

## Skills, agents, plans

- `.ai/skills/kmp-module`: adding a module (layer, plugin, dependencies, iOS export).
- `.ai/skills/build-and-verify`: building, testing and running on the emulator and simulator.
- `.ai/kanban` and the `kanban` skill ([fonix232/kanban](https://github.com/fonix232/kanban)): the board, its cards and dashboard, and moving work through review.
- `.ai/skills/keyboard-platforms`: Android IME and iOS extension constraints and where each adapter lives.
- `.ai/skills/android-engineering`: IME lifecycle, InputConnection cost, EditorInfo, insets, direct boot, API 35–37 changes.
- `.ai/skills/ios-engineering`: extension lifecycle, text proxy, Full Access, memory, Swift interop, renderer, App Review.
- `.ai/skills/kmp-cmp`: source sets, ports vs expect/actual, Kotlin/Native, Swift export rules, Compose performance, upgrades.
- `.ai/skills/input-engine`: the touch → engine → host pipeline, TextHost contract, autocorrect rules, engine tests.
- `.ai/skills/mobile-ux-design`: key geometry, feedback, strip and panels, tablets, accessibility, theme tokens, onboarding.
- `.ai/skills/performance-budget`: memory, start-up and latency budgets, and how to measure them on each platform.
- `.ai/agents/keyboard-reviewer.agent.md`: reviews changes for layering, extension safety, privacy and IPC cost.
- `.ai/plans/roadmap.md`: the path from this scaffold to an MVP.
