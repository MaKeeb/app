---
name: performance-budget
description: MaKeeb's performance and memory budgets (iOS extension footprint, cold start, touch-to-commit latency, show/hide leaks) and how to measure them on iOS, Android and the JVM. Use when changing the typing path, loading data, adding a dependency to the keyboard runtime, or investigating lag, jank, or the iOS keyboard vanishing.
---

# Performance budget

The numbers are the go/no-go gates from `docs/research/platform-apis.md` §6.7. They apply to both platforms, but iOS memory is the one that bites. Progress is tracked on the APP-111 card.

| Metric | Budget | Why |
| --- | --- | --- |
| iOS extension `phys_footprint`, steady, dictionary loaded | ≤ 30 MB | Jetsam kills silently at roughly 48–70 MB |
| iOS peak (fast typing, emoji panel open) | ≤ 40 MB | |
| Growth over 50 show/hide cycles | ≤ 1 MB | A leaked controller costs about 3 MB per cycle |
| Cold start to first frame | ≤ 300 ms p90 | iOS kills extensions that start slowly (around 1 s) |
| Touch → text committed | ≤ 16 ms p95 | One frame at 60 Hz |
| Jetsam kills in a 30-minute typing session | 0 | |

## Rules for the typing path

- At most one host read per keystroke (`android-engineering`).
- No main-thread work that grows with dictionary size, and no allocation per touch move that grows with the number of keys.
- Dictionaries are memory-mapped and loaded off the main thread. Caches are bounded and trimmed when the keyboard hides or gets a memory warning.
- Before adding anything to `:shared:keyboard`'s dependency graph, check the iOS framework size and footprint.

## iOS

- Measure memory on a device. The simulator doesn't enforce the extension limit (and `os_proc_available_memory` returns 0 there).
- **Regression check:** `makeeb/scripts/ios-memory-check.py --udid <sim>` runs `KeyboardVisualTests/test16_memorySession`, reads the extension's `MemoryTrace` (`tmp/memory.txt`, Debug builds) and enforces two budgets: typing ≤ 30 MB before any emoji is drawn, and ≤ 60 MB for the whole session. Measured on the iPhone 17 simulator (2026-09-28): typing 19 MB; session 52 MB.
- **Emoji are the memory hazard.** The first emoji drawn loads iOS's emoji font machinery (~18 MB, a transient +30 MB). Core Text then keeps every distinct emoji glyph it has drawn for the life of the process, and neither dropping the font nor a memory warning frees it. That costs ~20 KB per glyph at 18 pt or below and ~52 KB at 19–32 pt (VocaHQ/vocaphone#340 measured the same). So the panel draws emoji at 18 pt, never lists skin-tone variants, and `MemoryGuard` exits the process on hide above 60% of the limit (45 MB where the limit is unknown), so the next field cold-starts instead of the keyboard dying mid-use. Reading the font's `sbix` bitmaps directly doesn't work on iOS: its Apple Color Emoji stores `emjc`, not PNG.
- `phys_footprint` is the number jetsam uses (`MemoryGuard.footprint()` in the extension):

```swift
func physFootprint() -> UInt64 {
    var info = task_vm_info_data_t()
    var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
    let result = withUnsafeMutablePointer(to: &info) {
        $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
            task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
        }
    }
    return result == KERN_SUCCESS ? info.phys_footprint : 0
}
```

- Instruments: use Allocations and VM Tracker for footprint (the Kotlin/Native heap is tagged, `mmapTag` 246) and Time Profiler for CPU. For timings, add `os_signpost` intervals for `viewDidLoad` → first draw and for touch down → `insertText`, and read them in Points of Interest. The Kotlin/Native binary option `enableSafepointSignposts=true` shows GC pauses.
- Leak check: show and dismiss the keyboard 50 times in one host (Messages, Notes, Safari), compare the footprint, and confirm `KeyboardViewController.deinit` runs.
- The simulator's Debug → Simulate Memory Warning exercises the warning handler, even though limits aren't enforced there.

## Android

- Measure frames on the `benchmark` build type (`./gradlew :app:android:assembleBenchmark`): R8, not debuggable, debug-signed, installed as `com.makeeb.benchmark`. Debug Compose is about four times slower: typing on the Pixel 6 Pro measured 31 ms median frames in debug against 8 ms (p95 13 ms, 0.4% janky) in benchmark (2026-09-28). `.ai/local/visual-test/fixes-android.py <serial> <dir> --latency --benchmark` types two sentences and prints `gfxinfo`; it uninstalls the benchmark app and restores the IME afterwards.
- Per-key main-thread cost: debug builds log `KeyLatency` summaries every 50 keys (`adb logcat -s MaKeebLatency`: p50/p95/max of the engine edit plus suggestions plus host calls). Measured at 1.8 ms p50 and 2.8 ms p95 on the Pixel in debug (2026-09-28); keep it well under one frame.
- The IME runs in the app process: `adb -s emulator-5554 shell dumpsys meminfo com.makeeb.debug`.
- Frames: `dumpsys gfxinfo com.makeeb.debug framestats`, or a Perfetto trace with the `input`, `view`, `gfx` and `sched` categories. For touch-to-commit, compare the input event timestamp with the `commitText` binder call. Add temporary `android.os.Trace` sections in androidMain or the service while investigating, and remove them before committing.
- Cold start: run `am force-stop com.makeeb.debug`, then focus a text field and time the first show.
- The emulator renders in software (`swiftshader_indirect`). Its rendering numbers are only good for before/after comparisons; measure on the Pixel (`.ai/instructions.md`).
- Compose: check recomposition counts for the keyboard surface in Layout Inspector (`kmp-cmp`).

## JVM

- You can time engine hot paths (`suggest`, `keyAt`) locally in a `commonTest`, but don't commit timing assertions: they're flaky on CI. If benchmarking becomes routine, add kotlinx-benchmark in its own module.

## Reporting

Record numbers with the device, OS version and build type in the board card's `progress` or the PR. Raw logs go in `.ai/local/`. Never record typed text.
