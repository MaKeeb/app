import XCTest

/// Executes docs/testing/visual-test-plan.md on iOS. Captures go to MAKEEB_SCREENSHOT_DIR
/// (pass TEST_RUNNER_MAKEEB_SCREENSHOT_DIR to xcodebuild) as I-VT-xx-{makeeb,stock}[-variant].png;
/// press-and-hold states are captured by the host (`.hold-<name>` marker files, see
/// .ai/local/visual-test/run-ios.sh), because XCUITest blocks while a press is held.
///
/// MaKeeb's keys are accessibility elements with `key-…` identifiers (KeyboardView), which is how
/// the tests tell it from Apple's keyboard. Taps use the shared geometry rules (KeyboardMetrics,
/// LayoutGeometry) for an iPhone 17 (402×874pt), which match those elements' frames.
/// Preconditions (set by run-ios.sh): MaKeeb enabled and first in the keyboard list, hardware
/// keyboard disconnected.
final class KeyboardVisualTests: XCTestCase {
    let app = XCUIApplication()
    lazy var outDir = ProcessInfo.processInfo.environment["MAKEEB_SCREENSHOT_DIR"] ?? NSTemporaryDirectory()
    lazy var theme = ProcessInfo.processInfo.environment["MAKEEB_THEME"] ?? "dark"
    var results: [String: String] = [:]

    // Geometry (points). Keyboard bottom edge = top of the system globe/dictation bar.
    let screenWidth: CGFloat = 402
    let keyboardBottom: CGFloat = 800
    let strip: CGFloat = 44
    let sideInset: CGFloat = 10

    override func setUp() {
        continueAfterFailure = true
        app.launch()
        XCTAssertTrue(app.buttons["Try it"].waitForExistence(timeout: 30))
    }

    override func tearDown() {
        let text = results.map { "\($0.key)\t\($0.value)" }.sorted().joined(separator: "\n") + "\n"
        let url = URL(fileURLWithPath: outDir).appendingPathComponent("results-\(name.replacingOccurrences(of: " ", with: "_")).tsv")
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }

    // MARK: Capture

    func save(_ name: String) {
        Thread.sleep(forTimeInterval: 0.6)
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: URL(fileURLWithPath: outDir).appendingPathComponent("\(name).png"))
    }

    /// Hold a point long enough for the host to screenshot it.
    func heldCapture(_ name: String, at point: CGPoint, hold: TimeInterval) {
        let marker = URL(fileURLWithPath: outDir).appendingPathComponent(".hold-\(name)")
        try? "\(hold)".write(to: marker, atomically: true, encoding: .utf8)
        coordinate(point).press(forDuration: hold)
        Thread.sleep(forTimeInterval: 0.5)
    }

    // MARK: Taps and fields

    func coordinate(_ p: CGPoint) -> XCUICoordinate {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: p.x, dy: p.y))
    }

    func tap(_ p: CGPoint, pause: TimeInterval = 0.25) {
        coordinate(p).tap()
        Thread.sleep(forTimeInterval: pause)
    }

    func openTab(_ name: String) {
        app.buttons[name].firstMatch.tap()
        Thread.sleep(forTimeInterval: 1)
    }

    /// The QA field labelled `label`, by its Compose test tag: Compose doesn't expose every
    /// field's label to accessibility.
    func field(_ label: String) -> XCUIElement {
        app.textViews.matching(identifier: "qa-\(label)").firstMatch
    }

    /// Focus the QA field labelled `label`, scrolling it into the area above the keyboard. Frames
    /// of fields clipped by the scroll viewport still read as on screen, so the band is kept well
    /// inside what is visible with the keyboard up (the keyboard's top edge is at 535pt).
    func focus(_ label: String) {
        let field = field(label)
        XCTAssertTrue(field.waitForExistence(timeout: 10), "field \(label)")
        for _ in 0..<3 {
            for _ in 0..<8 {
                let f = field.frame
                if f.minY > 100 && f.maxY < 480 { break }
                let dy: CGFloat = f.midY > 300 ? -200 : 200
                coordinate(CGPoint(x: 380, y: 400)).press(forDuration: 0.05, thenDragTo: coordinate(CGPoint(x: 380, y: 400 + dy)))
                Thread.sleep(forTimeInterval: 0.5)
            }
            tap(CGPoint(x: field.frame.midX, y: field.frame.midY), pause: 1.2)
            // A tap that lands beside a clipped or still-scrolling field focuses nothing.
            if (field.value(forKey: "hasKeyboardFocus") as? Bool) ?? true { return }
        }
        XCTFail("could not focus \(label)")
    }

    /// Guards against invalid captures: iOS reopens the last-used keyboard, and the simulator
    /// sometimes reverts to hardware-keyboard mode and shows none.
    func assertMaKeebVisible(_ context: String, numberRow: Bool = false, file: StaticString = #filePath, line: UInt = #line) {
        let start = Date()
        var visible = waitForMaKeeb(numberRow: numberRow, timeout: 6)
        // iOS reopens the last-used keyboard. If that isn't MaKeeb, advance once and give the
        // extension time to cold-start (up to ~20s for a debug build right after boot) before
        // trying again: switching away mid-launch makes iOS fall back to the system keyboard.
        var switches = 0
        var trail: [String] = []
        while !visible && switches < 4 {
            let globe = app.buttons["Next keyboard"].firstMatch
            trail.append("before \(switches + 1): globe=\(String(describing: globe.value)) stock=\(stockKeyboardShowing)")
            globe.tap()
            switches += 1
            visible = waitForMaKeeb(numberRow: numberRow, timeout: 20)
            if !visible { save("diag-\(context)-switch\(switches)") }
        }
        if !trail.isEmpty { results["\(context)-switch-trail"] = trail.joined(separator: " | ") }
        if !visible {
            save("diag-\(context)")
            let globe = app.buttons["Next keyboard"].firstMatch
            let info = "globe exists=\(globe.exists) value=\(String(describing: globe.value)) makeeb=\(makeebShowing(numberRow: numberRow)) stock=\(stockKeyboardShowing)\n" + app.debugDescription
            try? info.write(to: URL(fileURLWithPath: outDir).appendingPathComponent("diag-\(context).txt"), atomically: true, encoding: .utf8)
        }
        results["\(context)-keyboard-visible-after"] = visible
            ? String(format: "%.1fs (%d globe switches)", Date().timeIntervalSince(start), switches)
            : "never"
        XCTAssertTrue(visible, "MaKeeb not visible (\(context))", file: file, line: line)
    }

    func waitForMaKeeb(numberRow: Bool, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if makeebShowing(numberRow: numberRow) { return true }
            Thread.sleep(forTimeInterval: 0.5)
        } while Date() < deadline
        return false
    }

    /// The return-key action each QA field asks for; the number and phone pads have none.
    let fieldActions = ["Text": "return", "E-mail": "return", "URL": "go", "Password": "return",
                        "Search": "search", "Send": "send", "Multi-line": "return"]

    /// Waits until the keyboard shows the focused field's return key, so a capture never shows the
    /// previous field's keyboard (both keyboards take a moment to follow a focus change).
    func settle(on label: String, stock: Bool) {
        guard let action = fieldActions[label] else {
            Thread.sleep(forTimeInterval: 2.0)
            return
        }
        let predicate = stock
            ? NSPredicate(format: "identifier == 'Return' AND label ==[c] %@", action)
            : NSPredicate(format: "identifier == %@", "key-\(action)")
        let settled = app.descendants(matching: .any).matching(predicate).firstMatch.waitForExistence(timeout: 6)
        results["\(label)-\(stock ? "stock" : "makeeb")-settled"] = settled ? "yes" : "timed out"
    }

    func value(_ label: String) -> String {
        (field(label).value as? String) ?? ""
    }

    func expect(_ id: String, _ label: String, _ want: String) {
        let got = value(label)
        results[id] = (got == want ? "pass" : "fail") + "\tfield=\(got.debugDescription) want=\(want.debugDescription)"
    }

    // MARK: Keyboard geometry (mirrors LayoutGeometry for the built-in layouts)

    typealias Row = (weight: CGFloat, keys: [(String, CGFloat)])

    let letters: [Row] = [
        (1, "qwertyuiop".map { (String($0), 1) }),
        (1, "asdfghjkl".map { (String($0), 1) }),
        (1, [("SHIFT", 1.5)] + "zxcvbnm".map { (String($0), 1) } + [("BKSP", 1.5)]),
        (1, [("MODE", 1.5), ("EMOJI", 1), (",", 1), (" ", 4), (".", 1), ("ENTER", 1.5)]),
    ]
    lazy var symbols: [Row] = [
        (1, "1234567890".map { (String($0), 1) }),
        (1, "@#$_&-+()/".map { (String($0), 1) }),
        (1, [("MORE", 1.5)] + "*\"':;!?".map { (String($0), 1) } + [("BKSP", 1.5)]),
        letters[3],
    ]

    func key(_ label: String, rows: [Row]? = nil, numberRow: Bool = false) -> CGPoint {
        var rows = rows ?? letters
        if numberRow && rows.count == 4 && rows[0].keys.first?.0 == "q" {
            rows.insert((0.8, "1234567890".map { (String($0), 1) }), at: 0)
        }
        let keysArea = 54 * (numberRow ? 4.8 : 4) + 4 // KeyboardMetrics: rows + bottom padding
        var top = keyboardBottom - keysArea
        let std = keysArea / rows.reduce(0) { $0 + $1.weight }
        let units = rows.map { $0.keys.reduce(0) { $0 + $1.1 } }.max() ?? 10
        let unit = (screenWidth - 2 * sideInset) / units
        for row in rows {
            let height = std * row.weight
            var x = sideInset + (units - row.keys.reduce(0) { $0 + $1.1 }) / 2 * unit
            for (name, width) in row.keys {
                if name == label { return CGPoint(x: x + width * unit / 2, y: top + height / 2) }
                x += width * unit
            }
            top += height
        }
        XCTFail("no key \(label)")
        return .zero
    }

    func type(_ text: String, rows: [Row]? = nil) {
        for ch in text {
            let s = String(ch)
            tap(key(s == " " ? " " : s.lowercased(), rows: rows), pause: 0.2)
        }
    }

    // MARK: Cases

    func test01_idleAndSymbols() {
        openTab("Try it")
        focus("Text")
        assertMaKeebVisible("VT-01")
        save("I-VT-01-makeeb-\(theme)")
        tap(key("MODE"), pause: 0.6)
        save("I-VT-07-makeeb-\(theme)")
        tap(key("MODE"), pause: 0.6)
    }

    func test02_statesAndHolds() {
        openTab("Try it")
        focus("Text")
        assertMaKeebVisible("VT-05")
        save("I-VT-05-makeeb")
        tap(key("SHIFT"), pause: 0.8)       // auto one-shot → off
        coordinate(key("SHIFT")).doubleTap() // off → one-shot → caps lock
        save("I-VT-06-makeeb")
        tap(key("SHIFT"), pause: 0.6)
        tap(key("MODE"), pause: 0.5)
        tap(key("MORE", rows: symbols), pause: 0.6)
        save("I-VT-08-makeeb")
        tap(key("MODE"), pause: 0.6)          // ABC on the more-symbols layout → letters
        // Holds last 2s so the host can capture them; top-row keys open their popup after 350ms
        // (every top-row key has a digit alternate), so VT-11 is the popup of a top-row key.
        heldCapture("I-VT-10-makeeb", at: key("g"), hold: 2.0)
        focus("Search")
        heldCapture("I-VT-11-makeeb", at: key("q"), hold: 2.0)
        heldCapture("I-VT-12-makeeb", at: key("e"), hold: 2.0)
    }

    func test03_typing() {
        openTab("Try it")
        focus("Search")
        assertMaKeebVisible("VT-13")
        type("hel")
        save("I-VT-13-makeeb")
        focus("Send") // sentence caps: "teh " → "The ", delete → "Teh"
        type("teh ")
        expect("VT-14", "Send", "The ")
        save("I-VT-14-makeeb")
        tap(key("BKSP"), pause: 0.6)
        expect("VT-15", "Send", "Teh")
        save("I-VT-15-makeeb")
        focus("Text")
        type("hi. ok")
        expect("VT-16", "Text", "Hi. Ok")
        save("I-VT-16-makeeb")
        type(" ") // a word and a space: the strip offers punctuation
        save("I-VT-16-punctuation-makeeb")
    }

    func test04_fieldTypes() {
        openTab("Try it")
        let fields = [("VT-17", "E-mail"), ("VT-18", "URL"), ("VT-19", "Number"), ("VT-20", "Phone"),
                      ("VT-21", "Password"), ("VT-22", "Search"), ("VT-23", "Send"), ("VT-24", "Multi-line")]
        focus("Text")
        assertMaKeebVisible("VT-17..24")
        for (id, label) in fields {
            focus(label)
            settle(on: label, stock: false)
            save("I-\(id)-makeeb")
        }
        focus("E-mail")
        type("teh ")
        expect("VT-17b", "E-mail", "teh ")
        save("I-VT-17b-makeeb")
        focus("Multi-line")
        type("ab")
        tap(key("ENTER"), pause: 0.5)
        type("cd")
        expect("VT-24b", "Multi-line", "Ab\nCd")
        save("I-VT-24b-makeeb")
    }

    func test05_panels() {
        openTab("Try it")
        focus("Text")
        assertMaKeebVisible("VT-25")
        tap(key("EMOJI"), pause: 1.0)
        save("I-VT-25-makeeb")
        let letters = app.buttons["Letters"].firstMatch
        tap(letters.waitForExistence(timeout: 3) ? CGPoint(x: letters.frame.midX, y: letters.frame.midY)
                                                 : CGPoint(x: 42, y: keyboardBottom - 23), pause: 0.8)
        // The strip's clipboard button: first in the toolbar while there are no suggestions.
        let clipboard = app.descendants(matching: .any).matching(identifier: "strip-clipboard").firstMatch
        let point = clipboard.waitForExistence(timeout: 3)
            ? CGPoint(x: clipboard.frame.midX, y: clipboard.frame.midY)
            : CGPoint(x: 30, y: keyboardBottom - 220 - strip / 2)
        tap(point, pause: 1.0)
        save("I-VT-26-makeeb")
        tap(point, pause: 0.8) // toggles back to the keys
    }

    func test06_numberRow() {
        openTab("Settings")
        let row = app.descendants(matching: .any).matching(identifier: "switch-Number row").firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        // Revisited tabs don't always re-expose their elements; the row stays where it was.
        let rowCenter = CGPoint(x: row.frame.midX, y: row.frame.midY)
        tap(rowCenter, pause: 1.0)
        openTab("Try it")
        focus("Text")
        assertMaKeebVisible("VT-09", numberRow: true)
        save("I-VT-09-makeeb")
        openTab("Settings")
        tap(rowCenter, pause: 1.0)
    }

    func test07_landscape() {
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 1.5)
        openTab("Try it")
        let field = field("Text")
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        tap(CGPoint(x: field.frame.midX, y: field.frame.midY), pause: 1.5)
        save("I-VT-29-makeeb")
        XCUIDevice.shared.orientation = .portrait
        Thread.sleep(forTimeInterval: 1.0)
    }

    // MARK: Stock references

    /// MaKeeb's keys carry `key-…` identifiers. Hidden keys of either keyboard linger in the tree
    /// after switching away, so only on-screen ones count, and MaKeeb only while Apple's is gone.
    /// The extension's tree can reach XCUITest many seconds late (first launch after boot), so a
    /// return key in MaKeeb's accent colour also counts.
    func makeebShowing(numberRow: Bool = false) -> Bool {
        if stockKeyboardShowing { return false }
        let key = app.keys.matching(NSPredicate(format: "identifier BEGINSWITH 'key-'")).firstMatch
        if key.exists && key.frame.minY > 400 && key.frame.maxY < 874 { return true }
        return accentReturnKey(numberRow: numberRow)
    }

    /// KeyboardPalette.accentKey is blue in both themes (0x3F5EFB light, 0x7B90FF dark). Two
    /// points on the key body, clear of the glyph.
    func accentReturnKey(numberRow: Bool) -> Bool {
        guard let image = XCUIScreen.main.screenshot().image.cgImage else { return false }
        let enter = key("ENTER", numberRow: numberRow)
        return [CGPoint(x: enter.x - 14, y: enter.y + 12), CGPoint(x: enter.x + 14, y: enter.y - 12)].allSatisfy {
            let px = pixel(image, $0)
            return Int(px[2]) - Int(px[0]) > 80
        }
    }

    func pixel(_ image: CGImage, _ p: CGPoint) -> [UInt8] {
        let scale = CGFloat(image.width) / screenWidth
        var data = [UInt8](repeating: 0, count: 4)
        let ctx = CGContext(data: &data, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(image, in: CGRect(x: -p.x * scale, y: -(CGFloat(image.height) - p.y * scale), width: CGFloat(image.width), height: CGFloat(image.height)))
        return data
    }

    /// Apple's letter or digit keys, on screen and hittable.
    var stockKeyboardShowing: Bool {
        let keys = app.keys.matching(NSPredicate(format: "label IN {'q', 'Q', '1'} AND NOT (identifier BEGINSWITH 'key-')"))
        for i in 0..<min(keys.count, 3) {
            let key = keys.element(boundBy: i)
            if key.isHittable && key.frame.minY > 400 && key.frame.maxY < 874 { return true }
        }
        return false
    }

    /// Cycle input modes with the globe until the system English keyboard shows (the cycle
    /// passes through Emoji, whose layout has no letter keys).
    func switchToStock() {
        for _ in 0..<4 where !stockKeyboardShowing {
            app.buttons["Next keyboard"].firstMatch.tap()
            let deadline = Date().addingTimeInterval(4)
            while !stockKeyboardShowing && Date() < deadline { Thread.sleep(forTimeInterval: 0.5) }
        }
        XCTAssertTrue(stockKeyboardShowing, "could not reach the system keyboard")
    }

    func switchToMaKeeb() {
        assertMaKeebVisible("return to MaKeeb")
    }

    func test09_stockReferences() {
        openTab("Try it")
        focus("Text")
        assertMaKeebVisible("stock setup")
        switchToStock()
        save("I-VT-04-stock-\(theme)")
        let more = app.keys["more"].firstMatch
        if more.exists { more.tap() } else { tap(CGPoint(x: 32, y: keyboardBottom - 26)) }
        save("I-VT-07-stock-\(theme)")
        if theme == "dark" {
            for (id, label) in [("VT-18", "URL"), ("VT-19", "Number"), ("VT-20", "Phone"), ("VT-22", "Search"),
                                ("VT-23", "Send"), ("VT-24", "Multi-line")] {
                focus(label)
                settle(on: label, stock: true)
                save("I-\(id)-stock")
            }
            XCUIDevice.shared.orientation = .landscapeLeft
            Thread.sleep(forTimeInterval: 1.5)
            save("I-VT-29-stock")
            XCUIDevice.shared.orientation = .portrait
            Thread.sleep(forTimeInterval: 1.0)
        }
        switchToMaKeeb()
    }

    /// Cold start without accessibility polling (queries reach into the keyboard process and may
    /// slow it down): focus the field, wait, capture. Run with MaKeeb as the last-used keyboard and
    /// its process killed; the extension's LaunchTrace shows the timeline.
    func test10_coldOpen() {
        openTab("Try it")
        tap(CGPoint(x: 201, y: 290), pause: 0)
        Thread.sleep(forTimeInterval: 25)
        save("cold-open")
    }

    func test08_companion() {
        openTab("Setup")
        save("I-VT-30-setup")
        openTab("Settings")
        save("I-VT-30-settings")
    }
}
