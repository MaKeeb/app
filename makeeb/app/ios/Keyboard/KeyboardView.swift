import UIKit
import MaKeebKeyboard

/// Draws a `KeyboardRender` from the Kotlin runtime with Core Graphics and forwards touches.
/// Deliberately dumb: frames, labels, pressed state, previews and popups all come from Kotlin,
/// so Android and iOS behave identically. Cheap on memory, which the extension needs.
final class KeyboardView: UIView {
    weak var bridge: KeyboardExtensionBridge?
    var stripHeight: CGFloat = 44
    var render: KeyboardRender? {
        didSet {
            setNeedsDisplay()
            rebuildAccessibilityIfNeeded()
        }
    }

    /// VoiceOver sees keys and suggestions as elements with the `.keyboardKey` trait, since they
    /// are drawn rather than laid out as views. UI tests find keys the same way.
    private var keyElements: [UIAccessibilityElement] = []
    private var accessibilitySignature = ""

    /// Reset by the controller on each appearance, for LaunchTrace.
    var drawnSinceAppearing = false
    private var lastKeysAreaSize: CGSize = .zero
    private var stripTouches = Set<UITouch>()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        // iOS draws the keyboard's material (Liquid Glass on iOS 26) behind the input view. Nothing
        // covers it: only keys, strip and popups are drawn here.
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: KeyboardView, _) in
            view.setNeedsDisplay()
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let size = CGSize(width: bounds.width, height: max(0, bounds.height - stripHeight))
        if size != lastKeysAreaSize {
            lastKeysAreaSize = size
            bridge?.setKeysAreaSize(width: Double(size.width), height: Double(size.height))
        }
    }

    // MARK: Drawing

    override func draw(_ rect: CGRect) {
        guard let render else { return }
        if !drawnSinceAppearing {
            drawnSinceAppearing = true
            LaunchTrace.mark("first draw")
        }
        let palette = render.palette(systemDark: traitCollection.userInterfaceStyle == .dark)
        drawSuggestions(render.suggestions, palette: palette)
        drawStripActions(render, palette: palette)
        for key in render.keys {
            drawKey(key, palette: palette)
        }
        if let preview = render.preview {
            let frame = keyAreaRect(preview.frame)
            withShadow {
                UIColor(argb: palette.popup).setFill()
                UIBezierPath(roundedRect: frame, cornerRadius: 10).fill()
            }
            drawText(preview.label, in: frame, size: CGFloat(KeyboardTokens.shared.PREVIEW_TEXT_SIZE), color: UIColor(argb: palette.onPopup), weight: .medium)
        }
        if let popup = render.popup {
            drawPopup(popup, palette: palette)
        }
    }

    /// Cells come from the shared `stripSlots`: an empty string is an empty slot, so the best word
    /// keeps the middle.
    private func drawSuggestions(_ suggestions: [String], palette: KeyboardPalette) {
        guard !suggestions.isEmpty, let best = render?.bestSuggestion else { return }
        let width = bounds.width / CGFloat(suggestions.count)
        for (index, text) in suggestions.enumerated() where !text.isEmpty {
            let cell = CGRect(x: CGFloat(index) * width, y: 0, width: width, height: stripHeight)
            drawText(text, in: cell, size: 17, color: UIColor(argb: palette.onKey), weight: index == Int(best) ? .semibold : .regular)
            if index > 0 && !suggestions[index - 1].isEmpty {
                UIColor(argb: palette.divider).setFill()
                UIRectFill(CGRect(x: cell.minX, y: 10, width: 1, height: stripHeight - 20))
            }
        }
    }

    /// The toolbar in an empty strip; the button of an open panel is lit.
    private func drawStripActions(_ render: KeyboardRender, palette: KeyboardPalette) {
        for (action, frame) in stripActionFrames() {
            let open = (action == .emoji && render.panel == .emoji) || (action == .clipboard && render.panel == .clipboard)
            let color = UIColor(argb: open ? palette.onKey : palette.hint)
            let configuration = UIImage.SymbolConfiguration(pointSize: 19, weight: .regular)
            guard let symbol = UIImage(systemName: stripSymbol(action), withConfiguration: configuration)?
                .withTintColor(color, renderingMode: .alwaysOriginal) else { continue }
            symbol.draw(in: CGRect(x: frame.midX - symbol.size.width / 2, y: frame.midY - symbol.size.height / 2,
                                   width: symbol.size.width, height: symbol.size.height))
        }
    }

    /// Panel buttons from the leading edge, settings at the trailing edge (as on Android).
    private func stripActionFrames() -> [(StripAction, CGRect)] {
        guard let render, render.suggestions.isEmpty else { return [] }
        var frames: [(StripAction, CGRect)] = []
        var x: CGFloat = 4
        for action in render.stripActions where action != .settings {
            frames.append((action, CGRect(x: x, y: 0, width: 52, height: stripHeight)))
            x += 52
        }
        if render.stripActions.contains(.settings) {
            frames.append((.settings, CGRect(x: bounds.width - 56, y: 0, width: 52, height: stripHeight)))
        }
        return frames
    }

    private func stripSymbol(_ action: StripAction) -> String {
        switch action {
        case .emoji: return "face.smiling"
        case .clipboard: return "list.clipboard"
        default: return "gearshape"
        }
    }

    private func stripLabel(_ action: StripAction) -> String {
        switch action {
        case .emoji: return "Emoji"
        case .clipboard: return "Clipboard"
        default: return "MaKeeb settings"
        }
    }

    private func drawKey(_ key: RenderKey, palette: KeyboardPalette) {
        let frame = keyAreaRect(key.frame).insetBy(dx: 3, dy: 4)
        let fill: Int64
        if key.pressed {
            fill = palette.keyPressed
        } else if key.isAccent {
            fill = palette.accentKey
        } else if key.isModifier && !key.isActive {
            fill = palette.modifierKey
        } else {
            fill = palette.key
        }
        UIColor(argb: fill).setFill()
        UIBezierPath(roundedRect: frame, cornerRadius: 8).fill()

        let foreground = UIColor(argb: key.isAccent && !key.pressed ? palette.onAccentKey : palette.onKey)
        if let icon = key.icon, let symbol = symbolImage(for: icon, color: foreground) {
            let size = symbol.size
            symbol.draw(in: CGRect(x: frame.midX - size.width / 2, y: frame.midY - size.height / 2, width: size.width, height: size.height))
        } else if let caption = key.caption {
            // Phone-pad digit with its letters underneath, as on the system keypad.
            drawText(key.label, in: frame.offsetBy(dx: 0, dy: -6), size: 22, color: foreground)
            drawText(caption, in: frame.offsetBy(dx: 0, dy: 13), size: 9, color: UIColor(argb: palette.hint), weight: .semibold)
        } else {
            let isCharacter = !key.isModifier && !key.isAccent && key.label.count <= 2
            // Four characters ("1234", ".com") only fit a one-unit key a size down.
            drawText(key.label, in: frame, size: isCharacter ? 22 : key.label.count >= 4 ? 13 : 15, color: foreground)
        }
        if let hint = key.hint {
            let hintRect = CGRect(x: frame.maxX - 14, y: frame.minY + 2, width: 12, height: 12)
            drawText(hint, in: hintRect, size: 10, color: UIColor(argb: palette.hint))
        }
    }

    /// Lifts previews and popups off the keys, as on the system keyboard (light keys are white on white).
    private func withShadow(_ draw: () -> Void) {
        guard let context = UIGraphicsGetCurrentContext() else { return draw() }
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: 2), blur: 8, color: UIColor.black.withAlphaComponent(0.3).cgColor)
        draw()
        context.restoreGState()
    }

    private func drawPopup(_ popup: RenderPopup, palette: KeyboardPalette) {
        guard let first = popup.cells.first, let last = popup.cells.last else { return }
        let outer = keyAreaRect(first).union(keyAreaRect(last))
        withShadow {
            UIColor(argb: palette.popup).setFill()
            UIBezierPath(roundedRect: outer, cornerRadius: 8).fill()
        }
        for (index, cell) in popup.cells.enumerated() {
            let frame = keyAreaRect(cell).insetBy(dx: 3, dy: 3)
            let selected = index == Int(popup.selected)
            if selected {
                UIColor(argb: palette.popupSelected).setFill()
                UIBezierPath(roundedRect: frame, cornerRadius: 6).fill()
            }
            let color = selected ? palette.onPopupSelected : palette.onPopup
            drawText(popup.options[index], in: frame, size: 22, color: UIColor(argb: color))
        }
    }

    /// SF Symbols for the shared `KeyIcon`s; Android draws the matching Material Symbols.
    private func symbolImage(for icon: KeyIcon, color: UIColor) -> UIImage? {
        let name: String
        switch icon {
        case .shift: name = "shift"
        case .shiftactive: name = "shift.fill"
        case .capslock: name = "capslock.fill"
        case .backspace: name = "delete.left"
        case .return_: name = "return"
        case .search: name = "magnifyingglass"
        case .send: name = "paperplane"
        case .go: name = "arrow.right"
        case .next: name = "arrow.right.to.line"
        case .previous: name = "arrow.left.to.line"
        case .done: name = "checkmark"
        case .globe: name = "globe"
        case .emoji: name = "face.smiling"
        case .space: name = "space"
        default: return nil
        }
        let configuration = UIImage.SymbolConfiguration(pointSize: CGFloat(KeyboardTokens.shared.KEY_SYMBOL_POINT_SIZE), weight: .regular)
        return UIImage(systemName: name, withConfiguration: configuration)?.withTintColor(color, renderingMode: .alwaysOriginal)
    }

    private func keyAreaRect(_ rect: RenderRect) -> CGRect {
        CGRect(x: rect.x, y: rect.y + Double(stripHeight), width: rect.width, height: rect.height)
    }

    private func drawText(_ text: String, in rect: CGRect, size: CGFloat, color: UIColor, weight: UIFont.Weight = .regular) {
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: size, weight: weight),
            .foregroundColor: color,
        ]
        let string = text as NSString
        let textSize = string.size(withAttributes: attributes)
        string.draw(at: CGPoint(x: rect.midX - textSize.width / 2, y: rect.midY - textSize.height / 2), withAttributes: attributes)
    }

    // MARK: Accessibility

    override var isAccessibilityElement: Bool {
        get { false }
        set {}
    }

    override var accessibilityElements: [Any]? {
        get { keyElements }
        set {}
    }

    private func rebuildAccessibilityIfNeeded() {
        guard let render else { return }
        // Touch-state renders (pressed keys, previews) don't change what VoiceOver should read.
        let signature = render.keys.map { "\($0.label)|\($0.icon?.name ?? "")|\($0.frame.x),\($0.frame.y)" }.joined(separator: ";")
            + "#" + render.suggestions.joined(separator: ";")
            + "#" + render.stripActions.map(\.name).joined(separator: ";") + "#\(render.panel.name)"
        guard signature != accessibilitySignature else { return }
        accessibilitySignature = signature

        var elements: [UIAccessibilityElement] = []
        let count = render.suggestions.count
        for (index, suggestion) in render.suggestions.enumerated() where !suggestion.isEmpty {
            let element = UIAccessibilityElement(accessibilityContainer: self)
            element.accessibilityLabel = suggestion
            element.accessibilityHint = "Suggestion"
            element.accessibilityTraits = .keyboardKey
            let width = bounds.width / CGFloat(count)
            element.accessibilityFrameInContainerSpace = CGRect(x: CGFloat(index) * width, y: 0, width: width, height: stripHeight)
            elements.append(element)
        }
        for (action, frame) in stripActionFrames() {
            let element = UIAccessibilityElement(accessibilityContainer: self)
            element.accessibilityLabel = stripLabel(action)
            element.accessibilityIdentifier = "strip-" + action.name.lowercased()
            element.accessibilityTraits = .button
            element.accessibilityFrameInContainerSpace = frame
            elements.append(element)
        }
        for key in render.keys {
            let element = UIAccessibilityElement(accessibilityContainer: self)
            element.accessibilityLabel = spokenLabel(for: key)
            element.accessibilityIdentifier = "key-" + (key.icon.map { $0.name.lowercased() } ?? (key.label.isEmpty ? "space" : key.label))
            element.accessibilityTraits = .keyboardKey
            element.accessibilityFrameInContainerSpace = keyAreaRect(key.frame)
            elements.append(element)
        }
        keyElements = elements
        UIAccessibility.post(notification: .layoutChanged, argument: nil)
    }

    private func spokenLabel(for key: RenderKey) -> String {
        guard let icon = key.icon else {
            return key.label.isEmpty ? "space" : key.label
        }
        switch icon {
        case .shift: return "shift"
        case .shiftactive: return "shift, on"
        case .capslock: return "caps lock"
        case .backspace: return "delete"
        case .return_: return "return"
        case .search: return "search"
        case .send: return "send"
        case .go: return "go"
        case .next: return "next"
        case .previous: return "previous"
        case .done: return "done"
        case .globe: return "next keyboard"
        case .emoji: return "emoji"
        case .space: return "space"
        default: return key.label
        }
    }

    // MARK: Touches

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let point = touch.location(in: self)
            if point.y < stripHeight {
                stripTouches.insert(touch)
            } else {
                bridge?.touchDown(id: touchId(touch), x: Double(point.x), y: Double(point.y - stripHeight))
            }
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches where !stripTouches.contains(touch) {
            let point = touch.location(in: self)
            bridge?.touchMove(id: touchId(touch), x: Double(point.x), y: Double(point.y - stripHeight))
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let point = touch.location(in: self)
            if stripTouches.remove(touch) != nil {
                selectSuggestion(at: point)
            } else {
                bridge?.touchUp(id: touchId(touch), x: Double(point.x), y: Double(point.y - stripHeight))
            }
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            if stripTouches.remove(touch) == nil {
                bridge?.touchCancel(id: touchId(touch))
            }
        }
    }

    private func selectSuggestion(at point: CGPoint) {
        if let (action, _) = stripActionFrames().first(where: { $0.1.contains(point) }) {
            bridge?.performStripAction(action: action)
            return
        }
        guard let count = render?.suggestions.count, count > 0, point.y < stripHeight else { return }
        let index = min(count - 1, max(0, Int(point.x / (bounds.width / CGFloat(count)))))
        bridge?.selectSuggestion(stripIndex: Int32(index))
    }

    private func touchId(_ touch: UITouch) -> Int64 {
        Int64(ObjectIdentifier(touch).hashValue)
    }
}

extension UIColor {
    /// `0xAARRGGBB`, as produced by the shared `KeyboardPalette`.
    convenience init(argb: Int64) {
        let value = UInt32(truncatingIfNeeded: argb)
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat((value >> 24) & 0xFF) / 255
        )
    }
}
