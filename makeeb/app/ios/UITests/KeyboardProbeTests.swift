import XCTest

/// Diagnostics for driving the keyboard from UI tests: which keyboard shows in a focused field,
/// and what of it XCUITest can see. The visual test plan (KeyboardVisualTests) builds on this.
final class KeyboardProbeTests: XCTestCase {
    let app = XCUIApplication()
    lazy var outDir = ProcessInfo.processInfo.environment["MAKEEB_SCREENSHOT_DIR"] ?? NSTemporaryDirectory()

    override func setUp() {
        continueAfterFailure = true
    }

    func save(_ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: URL(fileURLWithPath: outDir).appendingPathComponent("\(name).png"))
    }

    func log(_ name: String, _ text: String) {
        try? text.write(to: URL(fileURLWithPath: outDir).appendingPathComponent("\(name).txt"), atomically: true, encoding: .utf8)
    }

    /// Taps a point in screen points; Compose elements don't report themselves as hittable.
    func tap(_ x: CGFloat, _ y: CGFloat) {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: x, dy: y)).tap()
    }

    func test02_focusField() throws {
        app.launch()
        let tryIt = app.buttons["Try it"].firstMatch
        XCTAssertTrue(tryIt.waitForExistence(timeout: 30))
        tryIt.tap()
        sleep(1)
        let field = app.textViews["Text"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        let frame = field.frame
        tap(frame.midX, frame.midY)
        sleep(3)
        save("probe-20-focused")
        log("probe-20-tree", app.debugDescription)
        log("probe-20-frames", "screen=\(XCUIScreen.main.screenshot().image.size) keyboards=\(app.keyboards.count) " +
            "keyboardFrame=\(app.keyboards.firstMatch.exists ? "\(app.keyboards.firstMatch.frame)" : "none")")
    }
}
