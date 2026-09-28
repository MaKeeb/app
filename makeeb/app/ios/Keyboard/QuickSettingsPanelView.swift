import UIKit
import MaKeebKeyboard

/// Quick settings in place of the keys, the counterpart of Android's Compose `QuickSettingsPanel`:
/// one tile per shared `QuickSetting`, lit while on. Without Full Access the App Group is
/// read-only, so the tiles show their state and the panel says why they don't respond.
final class QuickSettingsPanelView: UIView {
    private let bridge: KeyboardExtensionBridge
    private let titleLabel = UILabel()
    private let lettersButton = UIButton(type: .system)
    private let messageLabel = UILabel()
    private let rows = UIStackView()
    private var tiles: [UIButton] = []
    private var tileIds: [Int32] = []

    init(bridge: KeyboardExtensionBridge) {
        self.bridge = bridge
        super.init(frame: .zero)

        titleLabel.text = "Quick settings"
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.accessibilityTraits = .header
        lettersButton.setTitle("ABC", for: .normal)
        lettersButton.titleLabel?.font = .systemFont(ofSize: 14)
        lettersButton.accessibilityLabel = "Letters"
        lettersButton.accessibilityIdentifier = "panel-letters"
        lettersButton.addAction(UIAction { [weak self] _ in self?.bridge.showKeys() }, for: .touchUpInside)

        // iOS extensions can't open their app, so the way to everything else is spelled out.
        messageLabel.font = .systemFont(ofSize: 12)
        messageLabel.numberOfLines = 2
        // The tiles take the spare height, never the message.
        messageLabel.setContentHuggingPriority(.required, for: .vertical)
        messageLabel.setContentCompressionResistancePriority(.required, for: .vertical)

        rows.setContentHuggingPriority(.defaultLow, for: .vertical)
        rows.axis = .vertical
        rows.distribution = .fillEqually
        rows.spacing = 6
        let items = bridge.quickSettings
        for chunk in stride(from: 0, to: items.count, by: Self.columns).map({ Array(items[$0..<min($0 + Self.columns, items.count)]) }) {
            let row = UIStackView()
            row.axis = .horizontal
            row.distribution = .fillEqually
            row.spacing = 6
            for item in chunk {
                let tile = UIButton(type: .custom)
                tile.layer.cornerRadius = 10
                tile.titleLabel?.numberOfLines = 2
                tile.titleLabel?.textAlignment = .center
                tile.accessibilityIdentifier = "quick-" + item.title
                let id = item.id
                tile.addAction(UIAction { [weak self] _ in self?.bridge.toggleQuickSetting(id: id) }, for: .touchUpInside)
                tiles.append(tile)
                tileIds.append(id)
                row.addArrangedSubview(tile)
            }
            rows.addArrangedSubview(row)
        }

        let header = UIStackView(arrangedSubviews: [titleLabel, UIView(), lettersButton])
        header.axis = .horizontal
        header.alignment = .center
        let stack = UIStackView(arrangedSubviews: [header, messageLabel, rows])
        stack.axis = .vertical
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            header.heightAnchor.constraint(equalToConstant: 40),
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -6),
        ])
        header.isLayoutMarginsRelativeArrangement = true
        header.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    /// Called on every render while open: a tap changes a preference, which re-renders.
    func update(palette: KeyboardPalette) {
        let onKey = UIColor(argb: palette.onKey)
        let hint = UIColor(argb: palette.hint)
        titleLabel.textColor = onKey
        lettersButton.setTitleColor(onKey, for: .normal)
        messageLabel.textColor = hint

        let editable = bridge.quickSettingsEditable
        messageLabel.text = editable
            ? "Everything else is in the MaKeeb app."
            : "Allow Full Access for MaKeeb to change these here. They're all in the MaKeeb app too."

        let items = Dictionary(uniqueKeysWithValues: bridge.quickSettings.map { ($0.id, $0) })
        for (tile, id) in zip(tiles, tileIds) {
            guard let item = items[id] else { continue }
            let text = item.on ? UIColor(argb: palette.onAccentKey) : onKey
            let title = NSMutableAttributedString(
                string: item.title + "\n",
                attributes: [.font: UIFont.systemFont(ofSize: 13, weight: .medium), .foregroundColor: text]
            )
            title.append(NSAttributedString(
                string: item.value,
                attributes: [.font: UIFont.systemFont(ofSize: 12), .foregroundColor: item.on ? text : hint]
            ))
            tile.setAttributedTitle(title, for: .normal)
            tile.backgroundColor = UIColor(argb: item.on ? palette.accentKey : palette.key)
            tile.alpha = editable ? 1 : 0.5
            tile.isEnabled = editable
            tile.accessibilityLabel = item.title
            tile.accessibilityValue = item.value
            var traits: UIAccessibilityTraits = .button
            if item.isSwitch && item.on { traits.insert(.selected) }
            if !editable { traits.insert(.notEnabled) }
            tile.accessibilityTraits = traits
        }
    }

    private static let columns = 4
}
