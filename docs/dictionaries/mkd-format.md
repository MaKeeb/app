# MKD: MaKeeb's dictionary pack format (v1)

MKD packs hold a language's lexicon in a form the keyboard can memory-map and query in place. Nothing is parsed onto the heap. The design follows [docs/research/dictionaries-autocorrect.md](../research/dictionaries-autocorrect.md) §5 and §10.2. This file describes format version 1.0, as shipped by the APP-110 card (Stage 1).

- **Codec:** `makeeb/engine/dictionary/src/commonMain/kotlin/com/makeeb/engine/dictionary/pack/`. `MkdFormat` holds the layout in KDoc, `MkdWriter` writes packs, and `MkdPack` reads them. `MappedDictionary` answers `Dictionary` queries from a pack.
- **Builder:** `makeeb/tools/dictionaries` runs `./gradlew :tools:dictionaries:dictionaryPacks`, which writes `tools/dictionaries/build/packs/en_US.mkd`.
- **Reading:** `ByteRegion` from `:platform:storage`. Android maps the APK asset in place (`AssetBundledFiles`); iOS uses `mmap` on the extension bundle (`BundleFiles`); tests use `ByteArrayRegion`.

## Layout

All integers are little-endian, except the two "marker first" encodings noted below.

| Part | Contents |
|---|---|
| Header (16 bytes) | `u32` magic `"MKD" 0x1A`; `u16` major version (1, and readers reject any other); `u16` minor version (0, additive changes only); `u32` CRC-32 of bytes 12..end; `u16` section count; `u16` reserved |
| Section table | One `{u32 id, u32 offset, u32 length}` entry per section. The id is four ASCII characters. Readers skip sections they don't know |
| `META` | UTF-8 `key=value` lines. Required: `language` (BCP 47) and `keyFold`: `fold-v2` (lower case without diacritics or apostrophes, `KeyFold`; written since 2026-09-29) or `lowercase` (the first packs; still readable). Also present: `name`, `source`, `sourceSha256`, `sourceVersion`, `licence`, `attribution`, `words` |
| `WORD` | `u32` count, then `count × u24` record offsets, then the records. Each record is `u8` frequency (0–255, log scale), `u8` flags, `u8` byte length and the UTF-8 spelling |
| `LEXI` | Radix trie over folded keys, laid out breadth-first |

**Word ids** run in descending frequency, so ids `0..k-1` are the `k` most frequent words. This gives next-word data (Stage 5) small delta-coded ids and a free top-N list.

**Word flags:** `0x01` offensive (AOSP `possibly_offensive`), `0x02` cased (the canonical form isn't all lower case: names, acronyms, "I"). The other bits are reserved and must be 0.

**Trie nodes.** A node array is a count followed by its nodes, sorted by best score, highest first. The count takes 1 byte if it is below `0x80`; otherwise 2 bytes, with the marker first: `0x80 | high 7 bits`, then the low 8 bits. Each node holds:

| Field | Encoding |
|---|---|
| flags | `0x80` terminal, `0x40` multi-character label, `0x20` several words, `0x03` child-offset width (0 means no children) |
| best | Highest frequency of any word at or below the node. This is the bound best-first search uses |
| label | Code points. `0x20`–`0xFF` take one byte. Anything else takes three bytes, big-endian, with the marker first: the first byte is below `0x20`. A multi-character label ends with `0x1F` |
| terminal | `u8` frequency (the highest among its words), then one `u24` word id. With "several words", a `u8` count and that many ids, most frequent first |
| children | 1–3 byte offset from this node's first byte to its child array. Children always come later, breadth-first |

## Why it looks like this

- **Radix trie with a best-descendant score per node** (research §5.3): top-k completions pop about `k × depth` nodes whatever the vocabulary size. Siblings are sorted by that score, so the search queues the next sibling only when it pops the current one.
  - Measured on the en_US pack (JVM): 3–19 nodes and about 1 µs per completion query.
  - A DAFSA would be smaller but can't store a per-prefix best score.
- **Breadth-first layout:** the levels every query starts from share the first pages. `MappedDictionary.warmUp` touches them off the main thread.
- **Several words per key:** keys are folded, so one key can have several spellings ("us" and "US"). `lookup` returns the exact spelling when it exists, otherwise the most frequent one. Since Stage 2 keys also fold diacritics, letter expansions (ß→ss, æ→ae) and apostrophes (`keyFold=fold-v2`), so "naive" finds "naïve" and "dont" finds "don't"; the fold is code (`KeyFold`), not a table in the pack. Readers reject a `keyFold` they don't know.
- **Offensive words** are known to `lookup` (typing one exactly isn't a typo). `completions`, `corrections` and `entries` never offer them unless `suggestOffensive` is set. AOSP behaves the same way by default.
- **Reproducible:** the writer uses no timestamps, so the same word list always builds the same bytes. The CRC is checked when a pack is built (and, from Stage 4, when one is installed), never on every mapping: checking reads every page.

## The bundled English pack

| | |
|---|---|
| Source | AOSP LatinIME `dictionaries/en_US_wordlist.combined.gz` at commit `8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6` (2014-10-31, the file's last change). SHA-256 `0f78dd455b532be169a23f233227b811fabced4b5bd7fc9c40cc05839793bcbd`. Licence Apache-2.0 (see `THIRD_PARTY_NOTICES.md`) |
| Mirrors | android.googlesource.com (gitiles, base64), then the LineageOS GitHub mirror at the same commit. The hash pins the bytes whichever mirror serves them. The download is cached in `tools/dictionaries/build/downloads/` |
| Kept | 160,668 words. 1,300 are flagged offensive and 42,614 are cased |
| Dropped | 47 `not_a_word` entries (they exist only to carry shortcuts such as "im" → "I'm") and 99 shortcut lines. Shortcuts return with Stage 3 as a `SHRT` section |
| Size | 4,138,289 bytes: `LEXI` 1,763 KiB, `WORD` 2,278 KiB, `META` 0.5 KiB. Built in about 1.4 s |
| Packaging | Android: a generated asset, stored uncompressed (`androidResources.noCompress += "mkd"`) so it can be mapped out of the APK. iOS: a resource of the keyboard extension only (`app/ios/project.yml`); its pre-build phase runs the Gradle task. `-Pmakeeb.dictionaries=false` builds an APK without it, and the keyboard then falls back to `StarterDictionaries` |

`WORD` is the larger section because it repeats each key's text. It could store only the spellings that differ from their key, and rebuild the others from the trie path. That would save about 1.3 MB, but it only saves APK size: the pages are clean and mapped either way.

## Loading at runtime

`BundledDictionaryLoader` (`:shared:keyboard`) maps the pack on a background dispatcher when the engine first asks for its dictionary, as the keyboard is created. Until the pack is mapped, `DeferredDictionary` serves the starter list, so the keyboard never waits for the pack. Debug Android builds log the timings with `adb logcat -s MaKeebDictionary`.

On a Pixel 6 Pro (Android 17, debug build, 2026-09-28):

- Loading costs 1.0 ms to map, 5.7 ms to open the header and 0.35 ms to warm the top of the trie. The pack is ready 44 ms after the IME service is created; the no-pack path takes 40 ms, which is scheduling time.
- The Java and native heaps are unchanged: 20.4 and 13.2 MB with the pack against 21.0 and 13.5 MB without, after the same typing.
- The mapping (4,044 kB) becomes resident as it is read, and counts as clean, file-backed PSS on Android.
