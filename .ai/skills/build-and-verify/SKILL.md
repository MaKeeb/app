---
name: build-and-verify
description: Build, test and run MaKeeb: JVM unit tests, Android APK, iOS frameworks and the Xcode project, plus running the keyboard on the Android emulator and the iOS simulator safely.
---

# Build and verify

## Environment

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer   # iOS only; xcode-select points at CommandLineTools
```

Run Gradle from `makeeb/` (the Gradle root). `makeeb/local.properties` needs `sdk.dir=/Users/<you>/Library/Android/sdk` (gitignored). The shell is zsh: it does not word-split `$VAR`, so wrap multi-word commands in a function (e.g. `adbe() { adb -s emulator-5554 "$@"; }`).

## Commands

| Goal | Command | Notes |
| --- | --- | --- |
| Shared unit tests | `./gradlew jvmTest` | All engine/core logic. Seconds once warm. |
| Same tests on iOS | `scripts/ios-sim-test.sh` | Runs `iosSimulatorArm64Test` in every module on Kotlin/Native. Boots "iPhone 17" (override with `MAKEEB_IOS_TEST_DEVICE`) and shuts it down only if it booted it. Don't call the Gradle task directly on a shut-down device: standalone `simctl spawn` hangs with the Xcode 27 simulator. |
| Android APK | `./gradlew :app:android:assembleDebug` | `app/android/build/outputs/apk/debug/android-debug.apk`, package `com.makeeb.debug`. Bundles `en_US.mkd` as an uncompressed asset. |
| Dictionary packs | `./gradlew :tools:dictionaries:dictionaryPacks` | Runs as part of the APK and Xcode builds, for the bundled `en_US.mkd`. With `makeeb.packs.catalogueUrl` set (`gradle.properties`) it downloads the published pack (6.6 MB, pinned by SHA-256); otherwise, or if that fails, it builds it from the pinned AOSP list and Leipzig corpora (517 MB, counts cached). Everything is cached in `tools/dictionaries/build/downloads/`. Offline without that cache, add `-Pmakeeb.dictionaries=false` to build an APK without the pack (the keyboard falls back to its starter list). |
| Language packs | `./gradlew :tools:dictionaries:languagePacks` | The downloadable packs (de, es, fr, it, nl, pl, pt_BR, sv, hu) into `build/language-packs/`. Downloads 2.1 GB of corpora the first time. `-Pmakeeb.languagePacks=hu,sv` rebuilds a few. |
| Pack release folder | `./gradlew :tools:dictionaries:packRelease` | `build/pack-release/`: every pack, `catalogue.json`, `en_US.mkd`, `SHA256SUMS`, to upload as release assets by hand. Serve it locally for device tests: docs/dictionaries/pack-catalogue.md. |
| Pack memory | `./gradlew :tools:dictionaries:packMemory` | Heap kept per mapped pack and per-key cost with three packs, on the JVM. |
| Typing harness | `./gradlew :tools:dictionaries:typingHarness` | Keystroke savings, false corrections and typo fixes on held-out text; `-Pharness.pack=starter` for the starter list. |
| iOS frameworks | `./gradlew :shared:keyboard:linkDebugFrameworkIosSimulatorArm64 :shared:companion:linkDebugFrameworkIosSimulatorArm64` | Needs `DEVELOPER_DIR`. A cold Kotlin/Native build takes ~5 min. |
| Xcode project | `cd app/ios && xcodegen generate` | After any `project.yml` change. Commit the regenerated project. |
| iOS app + extension | `xcodebuild -project makeeb/app/ios/MaKeeb.xcodeproj -scheme MaKeeb -configuration Debug -sdk iphonesimulator -destination "generic/platform=iOS Simulator" -derivedDataPath <scratch> build` | The pre-build phases run Gradle `embedAndSignAppleFrameworkForXcode` for each target. Simulator builds sign ad hoc (`Shared.xcconfig`); don't pass `CODE_SIGNING_ALLOWED=NO`, which strips the App Group entitlement so the keyboard never sees the companion's settings. |

Known, harmless: the linker warns that Skia's `libicu.icudtl_dat.o` in `MaKeebCompanion` was built for a newer iOS than the 17.0 deployment target. It is ICU data only.

## Running on a physical Android device

Test Android on the user's Pixel 6 Pro whenever it is connected; the emulator is the fallback. `adb devices -l` may list a Car Thing (`<car-thing-serial>`, Linux, not Android): never target it. The real serials and addresses are in `.ai/local/devices.md`, which is never committed. For the user's Pixel (`<pixel-serial>`), save `settings get secure default_input_method` first and `ime set` it back when done. MaKeeb stays installed and enabled; the user switches keyboards with the navigation-bar switcher.

The Pixel is usually on ADB over Wi-Fi, as `<pixel-ip>:5555` (a DHCP address; if it changes, `adb devices -l` shows `model:Pixel_6_Pro`, and `getprop ro.serialno` is `<pixel-serial>`). TCP mode lasts until the phone reboots. To restore it, plug it in once and run:

```
adb -s <pixel-serial> tcpip 5555
adb -s <pixel-serial> shell ip -f inet addr show wlan0   # its Wi-Fi address
adb connect <address>:5555
```

While it is plugged in as well, it is listed twice; either serial reaches the same phone. Over Wi-Fi, installs and screenshots are slower, so allow longer pauses.

## Running on the Android emulator

A physical phone may be connected. Always pass `-s emulator-5554`, and never install on a device you weren't asked to use.

```
emulator -avd Medium_Phone_API_37.0 -read-only -no-snapshot-save -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb -s emulator-5554 wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
adb -s emulator-5554 install -r app/android/build/outputs/apk/debug/android-debug.apk
IME=com.makeeb.debug/com.makeeb.android.MaKeebInputMethodService
adb -s emulator-5554 shell ime enable $IME && adb -s emulator-5554 shell ime set $IME   # retry if "unknown IME": PACKAGE_ADDED is async
adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1          # the AVD reports a hardware keyboard
adb -s emulator-5554 shell am start -n com.makeeb.debug/com.makeeb.android.MainActivity
```

`-read-only` leaves the AVD untouched. Software rendering is slow: put `sleep` between `input tap`s inside one `adb shell '…'`, and check state with `uiautomator dump` before tapping on. The companion's "Try it" tab has a text field for testing. Kill the emulator with `adb -s emulator-5554 emu kill` when done.

### Instrumented tests

`app/android/src/androidTest` holds tests that drive the running keyboard, such as `KeyboardAccessibilityTest`, which clicks key nodes through UiAutomation the way TalkBack does. They type only into the companion's Try it field and put the current keyboard back afterwards, so they may run on the Pixel as well as the emulator. `connectedAndroidTest` runs on every connected device at once, so never use it. Install both APKs on one device and run them with `am instrument`:

```
./gradlew :app:android:assembleDebug :app:android:assembleDebugAndroidTest
adb -s <serial> install -r app/android/build/outputs/apk/debug/android-debug.apk
adb -s <serial> install -r app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
adb -s <serial> shell am instrument -w -e class com.makeeb.android.KeyboardAccessibilityTest com.makeeb.debug.test/androidx.test.runner.AndroidJUnitRunner
```

A loaded host (a freshly booted iOS simulator, parallel builds) starves the emulator into system-wide ANRs ("failed to complete startup"). Check `uptime` and rerun once the load is down; don't raise timeouts past what a quiet emulator needs.

`adb shell input` events bypass TalkBack's explore-by-touch: with TalkBack on, an injected tap still reaches the keyboard's pointer handler. They cannot test screen-reader behaviour, so use the instrumented test. TalkBack's own speech can be read from logcat once its developer settings have "Log output level" set to VERBOSE (`Speaking fragment text=`).

## Running on the iOS simulator

Create a throwaway device instead of using the user's: `xcrun simctl create MaKeeb-verify com.apple.CoreSimulator.SimDeviceType.iPhone-17 com.apple.CoreSimulator.SimRuntime.iOS-27-0`, then boot, `install`, `launch com.makeeb.ios`, `io <udid> screenshot`, and `delete` it afterwards. `simctl` cannot tap, so testing the keyboard extension itself needs Simulator.app or a device: Settings → General → Keyboard → Keyboards → Add → MaKeeb.

The app icon is `app/ios/MaKeeb/AppIcon.icon`, an Icon Composer document (edit it in Xcode's Icon Composer; check a render with its `ictool --export-image`). XcodeGen adds it as a `wrapper.icon` file, which actool compiles into `Assets.car` with fallbacks for older iOS. SpringBoard caches icons, so reboot the simulator to see a change (`KeyboardVisualTests/test13_homeIcon` captures the home screen). Android's adaptive icon layers are `app/android/src/main/res/drawable/ic_launcher_*.xml`; `docs/brand/app-icon.svg` is the shared geometry.

## Definition of done

`jvmTest` passes, plus the builds for each platform you touched. For UI or IME changes, one emulator screenshot of the change. Write logs and screenshots to the session scratchpad or `.ai/local/`, not the repo (curated screenshots go in `docs/screenshots/`. Paths such as `app/android/build/...` above are relative to `makeeb/`).
