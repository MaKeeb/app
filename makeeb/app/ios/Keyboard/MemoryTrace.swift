import Foundation
import os

/// Debug builds only: the extension's memory as iOS counts it for the silent kill
/// (`phys_footprint`), and how far it is from the limit (`os_proc_available_memory`). Appended to
/// `tmp/memory.txt` in the extension's data container at lifecycle points and panel changes, with
/// the peak since launch. `scripts/ios-memory-check.py` reads it and enforces the budget.
enum MemoryTrace {
    #if DEBUG
    private static let file = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("memory.txt")
    private static var peak: UInt64 = 0

    /// Cheap enough to call on every render: one `task_info`.
    static func sample() {
        peak = max(peak, footprint())
    }

    static func mark(_ event: String) {
        let now = footprint()
        peak = max(peak, now)
        let available = os_proc_available_memory()
        let line = String(
            format: "%.3f pid=%d footprint=%.1fMB peak=%.1fMB available=%.1fMB %@\n",
            Date().timeIntervalSince1970, getpid(), mb(now), mb(peak), mb(UInt64(available)), event
        )
        guard let data = line.data(using: .utf8) else { return }
        if let handle = try? FileHandle(forWritingTo: file) {
            handle.seekToEndOfFile()
            handle.write(data)
            try? handle.close()
        } else {
            try? data.write(to: file)
        }
    }

    private static func footprint() -> UInt64 { MemoryGuard.footprint() }

    private static func mb(_ bytes: UInt64) -> Double { Double(bytes) / 1_048_576 }
    #else
    @inline(__always) static func sample() {}
    @inline(__always) static func mark(_ event: String) {}
    #endif
}
