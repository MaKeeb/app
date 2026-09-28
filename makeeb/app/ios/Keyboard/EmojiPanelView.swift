import UIKit
import MaKeebKeyboard

/// The emoji panel in place of the keys, the counterpart of Android's Compose `EmojiPanel`:
/// category tabs, a scrolling grid, and ABC / delete along the bottom. Built from UIKit controls
/// so VoiceOver reads it; data and actions come from the shared bridge.
final class EmojiPanelView: UIView, UICollectionViewDataSource, UICollectionViewDelegate {
    private let bridge: KeyboardExtensionBridge
    private let tabBar = UIStackView()
    private var tabButtons: [UIButton] = []
    private let grid: UICollectionView
    private let emptyLabel = UILabel()
    private let lettersButton = UIButton(type: .system)
    private let deleteButton = UIButton(type: .system)
    private var palette: KeyboardPalette?
    private var tab = 0
    private var emojis: [String] = []

    init(bridge: KeyboardExtensionBridge) {
        self.bridge = bridge
        let layout = UICollectionViewFlowLayout()
        layout.itemSize = CGSize(width: 44, height: 44)
        layout.minimumInteritemSpacing = 0
        layout.minimumLineSpacing = 0
        grid = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(frame: .zero)

        tabBar.axis = .horizontal
        tabBar.distribution = .fillEqually
        tabBar.spacing = 2
        for (index, icon) in bridge.emojiTabs.enumerated() {
            let button = UIButton(type: .custom)
            button.setTitle(icon, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 20)
            button.layer.cornerRadius = 10
            button.accessibilityLabel = index == 0 ? "Recent emoji" : icon
            button.addAction(UIAction { [weak self] _ in self?.select(tab: index) }, for: .touchUpInside)
            tabButtons.append(button)
            tabBar.addArrangedSubview(button)
        }

        grid.backgroundColor = .clear
        grid.dataSource = self
        grid.delegate = self
        grid.register(EmojiCell.self, forCellWithReuseIdentifier: EmojiCell.reuseId)

        emptyLabel.text = "No recent emoji yet"
        emptyLabel.font = .systemFont(ofSize: 14)
        emptyLabel.textAlignment = .center

        configure(lettersButton, title: "ABC", accessibilityLabel: "Letters") { [weak self] in self?.bridge.showKeys() }
        configure(deleteButton, symbol: "delete.left", accessibilityLabel: "Delete") { [weak self] in self?.bridge.deleteBackward() }

        let bottomBar = UIView()
        for view in [tabBar, grid, emptyLabel, bottomBar, lettersButton, deleteButton] as [UIView] {
            view.translatesAutoresizingMaskIntoConstraints = false
        }
        addSubview(tabBar)
        addSubview(grid)
        addSubview(emptyLabel)
        addSubview(bottomBar)
        bottomBar.addSubview(lettersButton)
        bottomBar.addSubview(deleteButton)
        NSLayoutConstraint.activate([
            tabBar.topAnchor.constraint(equalTo: topAnchor, constant: 2),
            tabBar.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            tabBar.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            tabBar.heightAnchor.constraint(equalToConstant: 36),
            grid.topAnchor.constraint(equalTo: tabBar.bottomAnchor, constant: 2),
            grid.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            grid.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            grid.bottomAnchor.constraint(equalTo: bottomBar.topAnchor),
            emptyLabel.centerXAnchor.constraint(equalTo: grid.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: grid.centerYAnchor),
            bottomBar.leadingAnchor.constraint(equalTo: leadingAnchor),
            bottomBar.trailingAnchor.constraint(equalTo: trailingAnchor),
            bottomBar.bottomAnchor.constraint(equalTo: bottomAnchor),
            bottomBar.heightAnchor.constraint(equalToConstant: 46),
            lettersButton.leadingAnchor.constraint(equalTo: bottomBar.leadingAnchor, constant: 10),
            lettersButton.centerYAnchor.constraint(equalTo: bottomBar.centerYAnchor),
            lettersButton.widthAnchor.constraint(equalToConstant: 64),
            lettersButton.heightAnchor.constraint(equalToConstant: 38),
            deleteButton.trailingAnchor.constraint(equalTo: bottomBar.trailingAnchor, constant: -10),
            deleteButton.centerYAnchor.constraint(equalTo: bottomBar.centerYAnchor),
            deleteButton.widthAnchor.constraint(equalToConstant: 64),
            deleteButton.heightAnchor.constraint(equalToConstant: 38),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    /// The panel just opened: start on recents, or on the first category when there are none.
    func open() {
        select(tab: Int(bridge.initialEmojiTab))
    }

    /// Called on every render while open: recents change as emoji are used, colours with the theme.
    func update(palette: KeyboardPalette) {
        self.palette = palette
        let onKey = UIColor(argb: palette.onKey)
        emptyLabel.textColor = UIColor(argb: palette.hint)
        for button in [lettersButton, deleteButton] {
            button.backgroundColor = UIColor(argb: palette.modifierKey)
            button.tintColor = onKey
            button.setTitleColor(onKey, for: .normal)
        }
        styleTabs()
        if tab == 0 { reloadEmojis() }
    }

    private func select(tab: Int) {
        self.tab = tab
        styleTabs()
        reloadEmojis()
        grid.setContentOffset(.zero, animated: false)
    }

    private func reloadEmojis() {
        emojis = bridge.emojis(tab: Int32(tab))
        emptyLabel.isHidden = !emojis.isEmpty
        grid.reloadData()
    }

    private func styleTabs() {
        let selected = palette.map { UIColor(argb: $0.modifierKey) } ?? .systemGray4
        for (index, button) in tabButtons.enumerated() {
            button.backgroundColor = index == tab ? selected : .clear
            button.accessibilityTraits = index == tab ? [.button, .selected] : .button
        }
    }

    private func configure(_ button: UIButton, title: String? = nil, symbol: String? = nil, accessibilityLabel: String, action: @escaping () -> Void) {
        if let title {
            button.setTitle(title, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 15)
        }
        if let symbol {
            button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 20)), for: .normal)
        }
        button.layer.cornerRadius = 8
        button.accessibilityLabel = accessibilityLabel
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
    }

    // MARK: Grid

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        emojis.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: EmojiCell.reuseId, for: indexPath) as! EmojiCell
        cell.label.text = emojis[indexPath.item]
        cell.accessibilityLabel = emojis[indexPath.item]
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        guard indexPath.item < emojis.count else { return }
        bridge.commitEmoji(value: emojis[indexPath.item])
    }
}

private final class EmojiCell: UICollectionViewCell {
    static let reuseId = "emoji"
    let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        label.font = .systemFont(ofSize: 28)
        label.textAlignment = .center
        label.frame = contentView.bounds
        label.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        contentView.addSubview(label)
        isAccessibilityElement = true
        accessibilityTraits = [.button, .keyboardKey]
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }
}
