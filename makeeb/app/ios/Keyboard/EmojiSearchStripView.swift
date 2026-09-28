import UIKit
import MaKeebKeyboard

/// The strip while searching emoji, the counterpart of Android's Compose `EmojiSearchStrip`: a way
/// back to the panel, the query typed on the keys below, and the matches in a scrolling row.
/// The keyboard keeps its size and its keys stay where they are.
final class EmojiSearchStripView: UIView, UICollectionViewDataSource, UICollectionViewDelegate {
    private let bridge: KeyboardExtensionBridge
    private let backButton = UIButton(type: .system)
    private let queryBox = UIView()
    private let glass = UIImageView(image: UIImage(systemName: "magnifyingglass"))
    private let queryLabel = UILabel()
    private let emptyLabel = UILabel()
    private let results: UICollectionView
    private var emoji: [String] = []

    init(bridge: KeyboardExtensionBridge) {
        self.bridge = bridge
        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .horizontal
        layout.itemSize = CGSize(width: 42, height: 44)
        layout.minimumLineSpacing = 0
        results = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(frame: .zero)

        backButton.setImage(UIImage(systemName: "chevron.left", withConfiguration: UIImage.SymbolConfiguration(pointSize: 18)), for: .normal)
        backButton.accessibilityLabel = "Close emoji search"
        backButton.accessibilityIdentifier = "emoji-search-close"
        backButton.addAction(UIAction { [weak self] _ in self?.bridge.endEmojiSearch() }, for: .touchUpInside)

        queryBox.layer.cornerRadius = 16
        glass.contentMode = .scaleAspectFit
        queryLabel.font = .systemFont(ofSize: 15)
        queryLabel.lineBreakMode = .byTruncatingHead // the end is what's being typed
        queryLabel.accessibilityIdentifier = "emoji-search-query"
        emptyLabel.text = "No emoji"
        emptyLabel.font = .systemFont(ofSize: 14)

        results.backgroundColor = .clear
        results.showsHorizontalScrollIndicator = false
        // A one-row strip: no safe-area insets, and none of iOS 26's soft scroll-edge blur, which
        // at this height would cover every result.
        results.contentInsetAdjustmentBehavior = .never
        if #available(iOS 26.0, *) {
            for edge in [results.topEdgeEffect, results.bottomEdgeEffect, results.leftEdgeEffect, results.rightEdgeEffect] {
                edge.isHidden = true
            }
        }
        results.dataSource = self
        results.delegate = self
        results.register(ResultCell.self, forCellWithReuseIdentifier: ResultCell.reuseId)

        for view in [backButton, queryBox, glass, queryLabel, emptyLabel, results] as [UIView] {
            view.translatesAutoresizingMaskIntoConstraints = false
        }
        addSubview(backButton)
        addSubview(queryBox)
        queryBox.addSubview(glass)
        queryBox.addSubview(queryLabel)
        addSubview(emptyLabel)
        addSubview(results)
        NSLayoutConstraint.activate([
            backButton.leadingAnchor.constraint(equalTo: leadingAnchor),
            backButton.topAnchor.constraint(equalTo: topAnchor),
            backButton.bottomAnchor.constraint(equalTo: bottomAnchor),
            backButton.widthAnchor.constraint(equalToConstant: 44),
            queryBox.leadingAnchor.constraint(equalTo: backButton.trailingAnchor),
            queryBox.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            queryBox.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -6),
            queryBox.widthAnchor.constraint(greaterThanOrEqualToConstant: 112),
            queryBox.widthAnchor.constraint(lessThanOrEqualToConstant: 168),
            glass.leadingAnchor.constraint(equalTo: queryBox.leadingAnchor, constant: 10),
            glass.centerYAnchor.constraint(equalTo: queryBox.centerYAnchor),
            glass.widthAnchor.constraint(equalToConstant: 15),
            queryLabel.leadingAnchor.constraint(equalTo: glass.trailingAnchor, constant: 6),
            queryLabel.trailingAnchor.constraint(equalTo: queryBox.trailingAnchor, constant: -10),
            queryLabel.centerYAnchor.constraint(equalTo: queryBox.centerYAnchor),
            emptyLabel.leadingAnchor.constraint(equalTo: queryBox.trailingAnchor, constant: 12),
            emptyLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
            results.leadingAnchor.constraint(equalTo: queryBox.trailingAnchor, constant: 4),
            results.trailingAnchor.constraint(equalTo: trailingAnchor),
            results.topAnchor.constraint(equalTo: topAnchor),
            results.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    /// Called on every render while searching: the query grows as keys are typed.
    func update(query: String, results matches: [String], palette: KeyboardPalette) {
        let hint = UIColor(argb: palette.hint)
        backButton.tintColor = hint
        glass.tintColor = hint
        queryBox.backgroundColor = UIColor(argb: palette.key)
        queryLabel.text = query.isEmpty ? "Search emoji" : query
        queryLabel.textColor = query.isEmpty ? hint : UIColor(argb: palette.onKey)
        emptyLabel.textColor = hint
        emptyLabel.isHidden = !(matches.isEmpty && !query.trimmingCharacters(in: .whitespaces).isEmpty)
        guard matches != emoji else { return }
        emoji = matches
        results.reloadData()
        results.setContentOffset(.zero, animated: false)
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        emoji.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: ResultCell.reuseId, for: indexPath) as! ResultCell
        cell.label.text = emoji[indexPath.item]
        cell.accessibilityLabel = bridge.emojiName(value: emoji[indexPath.item])
        cell.accessibilityIdentifier = "emoji-result-\(indexPath.item)"
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        guard indexPath.item < emoji.count else { return }
        bridge.commitEmoji(value: emoji[indexPath.item])
    }
}

private final class ResultCell: UICollectionViewCell {
    static let reuseId = "result"
    let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        label.font = .systemFont(ofSize: 24)
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
