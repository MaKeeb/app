import UIKit
import MaKeebKeyboard

/// The iOS keyboard. Typing logic, touch handling and layout all live in the shared Kotlin
/// runtime; this controller forwards UIKit lifecycle and text callbacks and hosts the renderer.
final class KeyboardViewController: UIInputViewController {
    private var bridge: KeyboardExtensionBridge!
    private var subscription: RenderSubscription?
    private let keyboardView = KeyboardView()
    private lazy var emojiPanel = EmojiPanelView(bridge: bridge)
    private lazy var clipboardPanel = ClipboardPanelView(bridge: bridge)
    private lazy var quickSettingsPanel = QuickSettingsPanelView(bridge: bridge)
    private lazy var emojiSearchStrip = EmojiSearchStripView(bridge: bridge)
    private var shownPanel: KeyboardPanel = .keys
    private var heightConstraint: NSLayoutConstraint?

    override init(nibName: String?, bundle: Bundle?) {
        super.init(nibName: nibName, bundle: bundle)
        LaunchTrace.mark("controller created")
        LaunchTrace.watchMainThread()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
    }

    override func loadView() {
        let inputView = ClickingInputView(frame: .zero, inputViewStyle: .keyboard)
        inputView.allowsSelfSizing = true
        self.inputView = inputView
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        LaunchTrace.mark("viewDidLoad")
        bridge = KeyboardExtensionBridge(controller: self)
        LaunchTrace.mark("bridge ready")
        keyboardView.bridge = bridge
        keyboardView.inputController = self
        keyboardView.stripHeight = CGFloat(bridge.stripHeight)
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(keyboardView)
        NSLayoutConstraint.activate([
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboardView.topAnchor.constraint(equalTo: view.topAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        let height = view.heightAnchor.constraint(equalToConstant: preferredHeight())
        height.priority = UILayoutPriority(999)
        height.isActive = true
        heightConstraint = height

        subscription = bridge.observe { [weak self] render in
            guard let self else { return }
            if keyboardView.render == nil { LaunchTrace.mark("first render") }
            keyboardView.render = render
            showPanel(for: render)
            showEmojiSearch(for: render)
            // Quick settings can change the height (number row) while the keyboard is up.
            let height = preferredHeight()
            if let constraint = heightConstraint, constraint.constant != height { constraint.constant = height }
        }
    }

    /// Emoji, clipboard and quick settings replace the keys below the strip; the strip stays for its toolbar.
    private func showPanel(for render: KeyboardRender) {
        let palette = render.palette(systemDark: traitCollection.userInterfaceStyle == .dark)
        if render.panel != shownPanel {
            shownPanel = render.panel
            for panel in [emojiPanel, clipboardPanel, quickSettingsPanel] as [UIView] where panel.superview != nil {
                panel.removeFromSuperview()
            }
            let panel: UIView? = switch render.panel {
            case .emoji: emojiPanel
            case .clipboard: clipboardPanel
            case .settings: quickSettingsPanel
            default: nil
            }
            if let panel {
                panel.translatesAutoresizingMaskIntoConstraints = false
                view.addSubview(panel)
                NSLayoutConstraint.activate([
                    panel.topAnchor.constraint(equalTo: view.topAnchor, constant: keyboardView.stripHeight),
                    panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                    panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                    panel.bottomAnchor.constraint(equalTo: view.bottomAnchor),
                ])
                if panel === emojiPanel { emojiPanel.open() }
            }
        }
        if render.panel == .emoji {
            emojiPanel.update(palette: palette)
        } else if render.panel == .clipboard {
            clipboardPanel.update(palette: palette)
        } else if render.panel == .settings {
            quickSettingsPanel.update(palette: palette)
        }
    }


    /// While searching emoji the strip shows the query and results over the (then empty) strip.
    private func showEmojiSearch(for render: KeyboardRender) {
        guard let query = render.emojiSearch else {
            if emojiSearchStrip.superview != nil { emojiSearchStrip.removeFromSuperview() }
            return
        }
        if emojiSearchStrip.superview == nil {
            emojiSearchStrip.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(emojiSearchStrip)
            NSLayoutConstraint.activate([
                emojiSearchStrip.topAnchor.constraint(equalTo: view.topAnchor),
                emojiSearchStrip.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                emojiSearchStrip.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                emojiSearchStrip.heightAnchor.constraint(equalToConstant: keyboardView.stripHeight),
            ])
        }
        let palette = render.palette(systemDark: traitCollection.userInterfaceStyle == .dark)
        emojiSearchStrip.update(query: query, results: render.emojiResults, palette: palette)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        LaunchTrace.mark("viewWillAppear")
        keyboardView.drawnSinceAppearing = false
        bridge.viewWillAppear()
        heightConstraint?.constant = preferredHeight()
    }

    /// Rotation changes the screen height, and with it the row height (short rows in landscape).
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        let height = preferredHeight()
        if let constraint = heightConstraint, constraint.constant != height { constraint.constant = height }
    }

    private func preferredHeight() -> CGFloat {
        let screen = view.window?.windowScene?.screen ?? UIScreen.main
        return CGFloat(bridge.preferredHeight(screenHeight: Double(screen.bounds.height)))
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        LaunchTrace.mark("viewDidAppear")
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        bridge.viewWillDisappear()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        bridge.textDidChange()
    }

    override func selectionDidChange(_ textInput: UITextInput?) {
        super.selectionDidChange(textInput)
        bridge.selectionDidChange()
    }

    deinit {
        subscription?.cancel()
        bridge?.dispose()
    }
}

/// Adopting `UIInputViewAudioFeedback` lets `UIDevice.playInputClick()` play the system click
/// (it follows the user's Keyboard Clicks setting).
final class ClickingInputView: UIInputView, UIInputViewAudioFeedback {
    var enableInputClicksWhenVisible: Bool { true }
}
