import UIKit
import MaKeebKeyboard

/// Clipboard history in place of the keys, the counterpart of Android's Compose `ClipboardPanel`.
/// Without Full Access iOS withholds the pasteboard, so the panel says why it is empty.
final class ClipboardPanelView: UIView, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    private let bridge: KeyboardExtensionBridge
    private let titleLabel = UILabel()
    private let clearButton = UIButton(type: .system)
    private let lettersButton = UIButton(type: .system)
    private let messageLabel = UILabel()
    private let grid: UICollectionView
    /// The user's own texts, above the clips; they need no Full Access.
    private let snippetScroll = UIScrollView()
    private let snippetRow = UIStackView()
    private var shownSnippets: [String] = []
    private var snippetHeight: NSLayoutConstraint?
    private var clips: [ClipItem] = []
    private var palette: KeyboardPalette?

    init(bridge: KeyboardExtensionBridge) {
        self.bridge = bridge
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 8
        layout.minimumLineSpacing = 8
        layout.sectionInset = UIEdgeInsets(top: 4, left: 8, bottom: 8, right: 8)
        grid = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(frame: .zero)

        titleLabel.text = "Clipboard"
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.accessibilityTraits = .header
        for (button, title, action) in [
            (clearButton, "Clear", { [weak self] in self?.bridge.clearClips() }),
            (lettersButton, "ABC", { [weak self] in self?.bridge.showKeys() }),
        ] as [(UIButton, String, () -> Void)] {
            button.setTitle(title, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 14)
            button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        }
        clearButton.accessibilityLabel = "Clear unpinned clips"
        lettersButton.accessibilityLabel = "Letters"

        messageLabel.font = .systemFont(ofSize: 14)
        messageLabel.textAlignment = .center
        messageLabel.numberOfLines = 0

        grid.backgroundColor = .clear
        grid.dataSource = self
        grid.delegate = self
        grid.register(ClipCell.self, forCellWithReuseIdentifier: ClipCell.reuseId)

        snippetRow.axis = .horizontal
        snippetRow.spacing = 8
        snippetRow.translatesAutoresizingMaskIntoConstraints = false
        snippetScroll.showsHorizontalScrollIndicator = false
        snippetScroll.contentInsetAdjustmentBehavior = .never
        snippetScroll.addSubview(snippetRow)

        let header = UIStackView(arrangedSubviews: [titleLabel, UIView(), clearButton, lettersButton])
        header.axis = .horizontal
        header.spacing = 12
        header.alignment = .center
        for view in [header, snippetScroll, grid, messageLabel] as [UIView] {
            view.translatesAutoresizingMaskIntoConstraints = false
            addSubview(view)
        }
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: topAnchor),
            header.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 14),
            header.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -14),
            header.heightAnchor.constraint(equalToConstant: 40),
            snippetScroll.topAnchor.constraint(equalTo: header.bottomAnchor),
            snippetScroll.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            snippetScroll.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            snippetRow.topAnchor.constraint(equalTo: snippetScroll.contentLayoutGuide.topAnchor),
            snippetRow.bottomAnchor.constraint(equalTo: snippetScroll.contentLayoutGuide.bottomAnchor),
            snippetRow.leadingAnchor.constraint(equalTo: snippetScroll.contentLayoutGuide.leadingAnchor),
            snippetRow.trailingAnchor.constraint(equalTo: snippetScroll.contentLayoutGuide.trailingAnchor),
            snippetRow.heightAnchor.constraint(equalTo: snippetScroll.frameLayoutGuide.heightAnchor),
            grid.topAnchor.constraint(equalTo: snippetScroll.bottomAnchor),
            grid.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 2),
            grid.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -2),
            grid.bottomAnchor.constraint(equalTo: bottomAnchor),
            messageLabel.centerYAnchor.constraint(equalTo: grid.centerYAnchor),
            messageLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 24),
            messageLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -24),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    /// Called on every render while open: clips arrive, get pinned or deleted.
    func update(palette: KeyboardPalette) {
        updateSnippets(palette: palette)
        self.palette = palette
        let onKey = UIColor(argb: palette.onKey)
        titleLabel.textColor = onKey
        clearButton.setTitleColor(onKey, for: .normal)
        lettersButton.setTitleColor(onKey, for: .normal)
        messageLabel.textColor = UIColor(argb: palette.hint)

        let available = bridge.clipboardAvailable
        clips = available ? bridge.clips : []
        clearButton.isHidden = clips.isEmpty
        messageLabel.isHidden = !clips.isEmpty
        messageLabel.text = available
            ? "Copied text shows up here."
            : "Allow Full Access for MaKeeb in Settings to use clipboard history."
        grid.reloadData()
    }

    private func updateSnippets(palette: KeyboardPalette) {
        let snippets = bridge.snippets
        if snippetHeight == nil {
            snippetHeight = snippetScroll.heightAnchor.constraint(equalToConstant: 0)
            snippetHeight?.isActive = true
        }
        snippetHeight?.constant = snippets.isEmpty ? 0 : 40
        if snippets != shownSnippets {
            shownSnippets = snippets
            snippetRow.arrangedSubviews.forEach { $0.removeFromSuperview() }
            for (index, snippet) in snippets.enumerated() {
                let button = UIButton(type: .custom)
                button.setTitle(snippet.replacingOccurrences(of: "\n", with: " "), for: .normal)
                button.titleLabel?.font = .systemFont(ofSize: 14)
                button.titleLabel?.lineBreakMode = .byTruncatingTail
                button.layer.cornerRadius = 16
                button.contentEdgeInsets = UIEdgeInsets(top: 6, left: 12, bottom: 6, right: 12)
                button.widthAnchor.constraint(lessThanOrEqualToConstant: 200).isActive = true
                button.accessibilityLabel = snippet
                button.accessibilityHint = "Types this snippet"
                button.accessibilityIdentifier = "snippet-\(index)"
                button.addAction(UIAction { [weak self] _ in self?.bridge.pasteSnippet(index: Int32(index)) }, for: .touchUpInside)
                snippetRow.addArrangedSubview(button)
            }
        }
        for case let button as UIButton in snippetRow.arrangedSubviews {
            button.backgroundColor = UIColor(argb: palette.key)
            button.setTitleColor(UIColor(argb: palette.onKey), for: .normal)
        }
    }

    // MARK: Grid

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        clips.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: ClipCell.reuseId, for: indexPath) as! ClipCell
        let clip = clips[indexPath.item]
        cell.show(clip, palette: palette)
        cell.onPin = { [weak self] in self?.bridge.toggleClipPin(id: clip.id) }
        cell.onDelete = { [weak self] in self?.bridge.deleteClip(id: clip.id) }
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        guard indexPath.item < clips.count else { return }
        bridge.pasteClip(id: clips[indexPath.item].id)
    }

    func collectionView(_ collectionView: UICollectionView, layout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let width = (collectionView.bounds.width - 8 * 3) / 2
        return CGSize(width: max(0, width), height: 92)
    }
}

private final class ClipCell: UICollectionViewCell {
    static let reuseId = "clip"
    private let textLabel = UILabel()
    private let pinButton = UIButton(type: .system)
    private let deleteButton = UIButton(type: .system)
    var onPin: (() -> Void)?
    var onDelete: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.layer.cornerRadius = 10
        textLabel.font = .systemFont(ofSize: 14)
        textLabel.numberOfLines = 3
        textLabel.lineBreakMode = .byTruncatingTail
        pinButton.titleLabel?.font = .systemFont(ofSize: 12)
        deleteButton.titleLabel?.font = .systemFont(ofSize: 12)
        deleteButton.setTitle("Delete", for: .normal)
        pinButton.addAction(UIAction { [weak self] _ in self?.onPin?() }, for: .touchUpInside)
        deleteButton.addAction(UIAction { [weak self] _ in self?.onDelete?() }, for: .touchUpInside)

        let actions = UIStackView(arrangedSubviews: [UIView(), pinButton, deleteButton])
        actions.spacing = 10
        for view in [textLabel, actions] as [UIView] {
            view.translatesAutoresizingMaskIntoConstraints = false
            contentView.addSubview(view)
        }
        NSLayoutConstraint.activate([
            textLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 8),
            textLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 10),
            textLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -10),
            actions.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 10),
            actions.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -8),
            actions.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -2),
            actions.heightAnchor.constraint(equalToConstant: 26),
        ])
        // VoiceOver: the card pastes; its buttons stay separately reachable.
        textLabel.accessibilityTraits = .button
        textLabel.accessibilityHint = "Pastes this clip"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    func show(_ clip: ClipItem, palette: KeyboardPalette?) {
        textLabel.text = clip.text
        pinButton.setTitle(clip.pinned ? "Unpin" : "Pin", for: .normal)
        guard let palette else { return }
        contentView.backgroundColor = UIColor(argb: palette.key)
        textLabel.textColor = UIColor(argb: palette.onKey)
        pinButton.setTitleColor(UIColor(argb: palette.hint), for: .normal)
        deleteButton.setTitleColor(UIColor(argb: palette.hint), for: .normal)
    }
}
