import Foundation

/// Debug builds only: timestamps of the extension's lifecycle, appended to `tmp/launch-trace.txt`
/// in the extension's data container, to see where the time goes when the keyboard is slow or
/// blank on opening. Each line: wall-clock time, pid, seconds since the process started, event.
enum LaunchTrace {
    #if DEBUG
    private static let processStart: TimeInterval = {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        guard sysctl(&mib, 4, &info, &size, nil, 0) == 0 else { return Date().timeIntervalSince1970 }
        let start = info.kp_proc.p_un.__p_starttime
        return TimeInterval(start.tv_sec) + TimeInterval(start.tv_usec) / 1_000_000
    }()

    private static let file = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("launch-trace.txt")

    private static var heartbeat: DispatchSourceTimer?

    /// Logs main-thread stalls longer than a second for the first minute after launch: iOS talks
    /// to the extension on the main thread, so a blocked one delays or aborts showing the keyboard.
    static func watchMainThread() {
        guard heartbeat == nil else { return }
        var last = Date().timeIntervalSince1970
        let started = last
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now(), repeating: 0.25)
        timer.setEventHandler {
            let now = Date().timeIntervalSince1970
            if now - last > 1 { mark(String(format: "main thread stalled %.2fs", now - last)) }
            last = now
            if now - started > 60 { heartbeat?.cancel(); heartbeat = nil }
        }
        timer.resume()
        heartbeat = timer
    }

    static func mark(_ event: String) {
        let now = Date().timeIntervalSince1970
        let line = String(format: "%.3f pid=%d +%.3fs %@\n", now, getpid(), now - processStart, event)
        guard let data = line.data(using: .utf8) else { return }
        if let handle = try? FileHandle(forWritingTo: file) {
            handle.seekToEndOfFile()
            handle.write(data)
            try? handle.close()
        } else {
            try? data.write(to: file)
        }
    }
    #else
    @inline(__always) static func mark(_ event: String) {}
    @inline(__always) static func watchMainThread() {}
    #endif
}
