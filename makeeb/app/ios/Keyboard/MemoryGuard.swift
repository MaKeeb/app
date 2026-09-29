import Foundation
import os

/// Keeps the extension clear of its memory limit, which iOS enforces with a silent kill (about
/// 77 MB on recent iPhones, less on older ones). Core Text keeps every emoji glyph it has drawn
/// (~50 KB each at panel size) for the life of the process, and neither dropping the font nor a
/// memory warning releases them, so a long emoji session only grows. When the keyboard hides with
/// its footprint past [threshold] of the limit, the process exits and the next field starts a
/// fresh one: a cold start, instead of the keyboard vanishing mid-use later. Settings live in the
/// App Group, and learned words in the extension's container, saved as the keyboard starts to hide
/// (`viewWillDisappear`); emoji recents and clipboard history are still in memory and go with it.
enum MemoryGuard {
    static let threshold = 0.6
    /// Where the limit is unknown (the simulator reports no available memory).
    static let fallbackLimitBytes: UInt64 = 45 * 1_048_576

    static func footprint() -> UInt64 {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? info.phys_footprint : 0
    }

    /// Called once the keyboard is off screen.
    static func recycleIfNeeded() {
        let used = footprint()
        let available = UInt64(os_proc_available_memory())
        let tooHigh = available > 0 ? Double(used) > threshold * Double(used + available) : used > fallbackLimitBytes
        guard tooHigh else { return }
        MemoryTrace.mark("recycling the process")
        exit(0)
    }
}
