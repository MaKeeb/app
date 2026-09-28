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
        }
    }

    /// Emoji and clipboard replace the keys below the strip; the strip stays for its toolbar.
    private func showPanel(for render: KeyboardRender) {
        let palette = render.palette(systemDark: traitCollection.userInterfaceStyle == .dark)
        if render.panel != shownPanel {
            shownPanel = render.panel
            for panel in [emojiPanel, clipboardPanel] as [UIView] where panel.superview != nil {
                panel.removeFromSuperview()
            }
            let panel: UIView? = render.panel == .emoji ? emojiPanel : render.panel == .clipboard ? clipboardPanel : nil
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
        }
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
