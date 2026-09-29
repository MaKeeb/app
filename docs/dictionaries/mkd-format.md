# MKD: MaKeeb's dictionary pack format (v1)

MKD packs hold a language's lexicon, and optionally its next-word statistics, in a form the keyboard can memory-map and query in place. Nothing is parsed onto the heap. The design follows [docs/research/dictionaries-autocorrect.md](../research/dictionaries-autocorrect.md) §4, §5 and §10.2. This file describes format version 1.1: 1.0 shipped with the APP-110 card (Stage 1), and 1.1 added the `NGRM` section with the APP-38 card (Stage 5).

- **Codec:** `makeeb/engine/dictionary/src/commonMain/kotlin/com/makeeb/engine/dictionary/pack/`. `MkdFormat` holds the layout in KDoc, `MkdWriter` writes packs (with an `MkdNgramTable` for `NGRM`), and `MkdPack` and `MkdNgrams` read them. `MappedDictionary` answers `Dictionary` queries from a pack, and its `nextWords` answers `NextWordModel` queries.
- **Builder:** `makeeb/tools/dictionaries`. `./gradlew :tools:dictionaries:dictionaryPacks` writes the bundled `tools/dictionaries/build/packs/en_US.mkd`; `languagePacks` writes the downloadable packs to `build/language-packs/` (below). `-Pmakeeb.ngrams=false` builds them without `NGRM` (no corpus download, except Hungarian's, whose words come from its corpus).
- **Reading:** `ByteRegion` from `:platform:storage`. Android maps the APK asset in place (`AssetBundledFiles`); iOS uses `mmap` on the extension bundle (`BundleFiles`); tests use `ByteArrayRegion`.

## Layout

All integers are little-endian, except the two "marker first" encodings noted below.

| Part | Contents |
|---|---|
| Header (16 bytes) | `u32` magic `"MKD" 0x1A`; `u16` major version (1, and readers reject any other); `u16` minor version (1, additive changes only); `u32` CRC-32 of bytes 12..end; `u16` section count; `u16` reserved |
| Section table | One `{u32 id, u32 offset, u32 length}` entry per section. The id is four ASCII characters. Readers skip sections they don't know |
| `META` | UTF-8 `key=value` lines. Required: `language` (BCP 47) and `keyFold`: `fold-v2` (lower case without diacritics or apostrophes, `KeyFold`; written since 2026-09-29) or `lowercase` (the first packs; still readable). Also present: `name`, `source`, `sourceSha256`, `sourceVersion`, `licence` (an SPDX expression for the whole pack), `attribution`, `words`; with `NGRM` also `ngramSource`, `ngramSha256`, `ngramLicence`, `ngramAttribution`, `bigrams` and `trigrams` |
| `WORD` | `u32` count, then `count × u24` record offsets, then the records. Each record is `u8` frequency (0–255, log scale), `u8` flags, `u8` byte length and the UTF-8 spelling |
| `LEXI` | Radix trie over folded keys, laid out breadth-first |
| `NGRM` | Optional. Next-word statistics: unigram, sentence-start, bigram and trigram successor lists (below) |

**Word ids** run in descending frequency, so ids `0..k-1` are the `k` most frequent words. This gives the next-word lists small delta-coded ids.

**Word flags:** `0x01` offensive (AOSP `possibly_offensive`), `0x02` cased (the canonical form isn't all lower case: names, acronyms, "I"). The other bits are reserved and must be 0.

**Trie nodes.** A node array is a count followed by its nodes, sorted by best score, highest first. The count takes 1 byte if it is below `0x80`; otherwise 2 bytes, with the marker first: `0x80 | high 7 bits`, then the low 8 bits. Each node holds:

| Field | Encoding |
|---|---|
| flags | `0x80` terminal, `0x40` multi-character label, `0x20` several words, `0x03` child-offset width (0 means no children) |
| best | Highest frequency of any word at or below the node. This is the bound best-first search uses |
| label | Code points. `0x20`–`0xFF` take one byte. Anything else takes three bytes, big-endian, with the marker first: the first byte is below `0x20`. A multi-character label ends with `0x1F` |
| terminal | `u8` frequency (the highest among its words), then one `u24` word id. With "several words", a `u8` count and that many ids, most frequent first |
| children | 1–3 byte offset from this node's first byte to its child array. Children always come later, breadth-first |

**Next-word statistics (`NGRM`).** Offsets are relative to the section. A reader that doesn't know the layout byte skips the section, as it would an unknown section, and a 1.0 reader never looks at it: `LEXI`, `WORD` and the word ids are byte-for-byte the same with or without it (`NextWordModelTest`).

| Offset | Field |
|---|---|
| 0 | `u8` layout (1); `u8` score scale S (10); `u8` backoff B (13); `u8` reserved |
| 4 | `u32` C: bigram contexts. Word ids `0..C-1` have a slot; later ids have no list |
| 8 | `u32` T: trigram contexts |
| 12, 16 | `u32` offset and `u32` length of the unigram list (the commonest words) |
| 20, 24 | `u32` offset and `u32` length of the sentence-start list |
| 28 | `u32` offset of the bigram lengths: C × `u8`, each context's list length in bytes |
| 32 | `u32` offset of the bigram anchors: ⌈C / 32⌉ × `u32`, where the lists of contexts `32k..` start, relative to the bigram data |
| 36 | `u32` offset of the bigram data |
| 40 | `u32` offset of the trigram keys: T × {`u24` first, `u24` second}, sorted; first `0xFFFFFF` is the sentence start |
| 44 | `u32` offset of the trigram lengths: T × `u8` |
| 48 | `u32` offset of the trigram anchors: ⌈T / 32⌉ × `u32` |
| 52 | `u32` offset of the trigram data |

- **Lists.** A list is its successors in ascending id order, each a LEB128 varint gap (the id minus the previous id minus 1; the first gap is the id itself) and a `u8` score. A score q means a probability of 2^(−q / S): tenths of a bit, so 0–255 covers p down to 2^−25.5.
- **Finding a list.** Context c's list starts at `anchor[c / 32]` plus the lengths of contexts `32 × (c / 32)` up to c: at most 31 byte reads. Trigram contexts are found by binary search over the 6-byte keys. A list is at most 255 bytes (its length is a `u8`); the builder keeps at most 32 successors, 128 bytes at most.
- **Ranking: stupid backoff** (Brants et al. 2007; research §4.2). For the words (w1, w2) before the caret, the trigram list of (w1, w2) scores as stored, w2's bigram list B worse (1.3 bits, the paper's 0.4), and the unigram list 2B worse. A word keeps the score of the highest order that lists it. With no word before the caret, a sentence start uses the sentence-start list (with B). Words the lexicon lacks (names, numbers) break the context, as they did when counting. Offensive words are left out of the lists, and filtered again when predicting.
- **Sentence-start capitals.** A context word that starts a sentence prefers its lower-case spelling where both exist ("Will you" is `will`; mid-sentence "Will" is the name), both when counting and when predicting (`MappedDictionary.wordId(word, sentenceInitial)`).

## Why it looks like this

- **Radix trie with a best-descendant score per node** (research §5.3): top-k completions pop about `k × depth` nodes whatever the vocabulary size. Siblings are sorted by that score, so the search queues the next sibling only when it pops the current one.
  - Measured on the en_US pack (JVM): 3–19 nodes and about 1 µs per completion query.
  - A DAFSA would be smaller but can't store a per-prefix best score.
- **Breadth-first layout:** the levels every query starts from share the first pages. `MappedDictionary.warmUp` touches them off the main thread.
- **Several words per key:** keys are folded, so one key can have several spellings ("us" and "US"). `lookup` returns the exact spelling when it exists, otherwise the most frequent one. Since Stage 2 keys also fold diacritics, letter expansions (ß→ss, æ→ae) and apostrophes (`keyFold=fold-v2`), so "naive" finds "naïve" and "dont" finds "don't"; the fold is code (`KeyFold`), not a table in the pack. Readers reject a `keyFold` they don't know.
- **Offensive words** are known to `lookup` (typing one exactly isn't a typo). `completions`, `corrections` and `entries` never offer them unless `suggestOffensive` is set. AOSP behaves the same way by default.
- **Reproducible:** the writer uses no timestamps, so the same word list always builds the same bytes. The CRC is checked when a pack is built (and, from Stage 4, when one is installed), never on every mapping: checking reads every page.
- **Next-word lists sorted by id, not by score.** Ids run by frequency, so the gaps between successors are small (1–2 bytes). A prediction decodes at most three lists of up to 32 entries each, so sorting by score would save nothing measurable.
- **u8 lengths plus anchors instead of an offset per context.** An offset per word would cost 3–4 bytes for each of 160k words. A length byte plus a 4-byte anchor every 32 contexts costs 1.1 bytes, and finding a list reads at most 31 lengths.
- **Trigram contexts that change nothing are left out.** A two-word context whose three best predictions are the ones its last word's bigrams give costs a key and a list and changes nothing in the strip. The builder drops those, about a fifth of the section, with no measured loss. Grouping trigram keys under their first word (KenLM style, research §4.5) would halve the 624 KiB of trigram keys, less a small index per first word, if the section needs to shrink.

## The bundled English pack

| | |
|---|---|
| Source | AOSP LatinIME `dictionaries/en_US_wordlist.combined.gz` at commit `8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6` (2014-10-31, the file's last change). SHA-256 `0f78dd455b532be169a23f233227b811fabced4b5bd7fc9c40cc05839793bcbd`. Licence Apache-2.0 (see `THIRD_PARTY_NOTICES.md`) |
| Mirrors | android.googlesource.com (gitiles, base64), then the LineageOS GitHub mirror at the same commit. The hash pins the bytes whichever mirror serves them. The download is cached in `tools/dictionaries/build/downloads/` |
| Kept | 160,668 words. 1,300 are flagged offensive and 42,614 are cased |
| Dropped | 47 `not_a_word` entries (they exist only to carry shortcuts such as "im" → "I'm") and 99 shortcut lines. Shortcuts return with Stage 3 as a `SHRT` section |
| Next-word source | [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download) `eng_news_2024_1M` (SHA-256 `8f1d4d07b9771f8a7fc219ad587d5382eabf5009ef563f1bc4c12a467a8e3a97`, 288,362,970 bytes) and `eng-com_web-public_2018_1M` (SHA-256 `de8849fe30c7d5bf3502f620093232f4dceca10a6ac19a9ad49f474b62df0a5f`, 228,887,647 bytes), from `https://downloads.wortschatz-leipzig.de/corpora/`. Licence CC BY 4.0 (see `THIRD_PARTY_NOTICES.md`). No second mirror: `corpora.uni-leipzig.de` answers scripts with a bot check |
| Counting | The `*-sentences.txt` of each tarball, in NFC, tokenised by `CorpusTokens` with the rules the keyboard reads context by (`TextBoundaries.wordsBefore`), and mapped to the word list's ids. 1,992,000 sentences and 37.4M words, 94.7% of them in the word list. One sentence in 250 (by corpus id) is held out for the typing harness. The counts (every bigram and trigram seen twice: 1.72M and 2.60M) are cached in `build/downloads`, so only the first build counts: 32 s, after a 517 MB download |
| Pruning (`NgramPruning`) | The 64 commonest words. Per word, up to 32 successors seen at least 3 times. Per two-word context seen at least 16 times, up to 8 successors seen at least 4 times, unless its three best predictions equal its bigrams'. Result: 371,440 bigrams in 160,666 contexts (2.75 bytes each with the index) and 359,718 trigrams in 106,474 contexts (4.40 bytes each with the keys) |
| Size | 6,642,361 bytes: `LEXI` 1,663 KiB, `WORD` 2,278 KiB, `NGRM` 2,545 KiB, `META` 1.2 KiB. Without `NGRM` it is 4,035,993 bytes. Built in 46 s from cold (counting), about 10 s from cached counts |
| Packaging | Android: a generated asset, stored uncompressed (`androidResources.noCompress += "mkd"`) so it can be mapped out of the APK. iOS: a resource of the keyboard extension only (`app/ios/project.yml`); its pre-build phase runs the Gradle task. `-Pmakeeb.dictionaries=false` builds an APK without it, and the keyboard then falls back to `StarterDictionaries` |

`WORD` is the larger section because it repeats each key's text. It could store only the spellings that differ from their key, and rebuild the others from the trie path. That would save about 1.3 MB, but it only saves APK size: the pages are clean and mapped either way.

## The downloadable packs

Every language but English is a pack the companion app downloads (docs/dictionaries/pack-catalogue.md). `./gradlew :tools:dictionaries:languagePacks` builds them the same way as English, each from pinned sources (hashes in `THIRD_PARTY_NOTICES.md`), and checks each one's golden words, folds, completions and next words before writing it. Only this task downloads their sources: about 1 MB of AOSP list and a 190–290 MB Leipzig news corpus per language, cached in `build/downloads` with their counts: 2.1 GB of corpora in all. With the corpora downloaded, counting and building all nine takes under 3 minutes on an M-series Mac; with the counts cached too, about a minute.

Built 2026-09-29:

| Pack | Words | Offensive | Next words (bigrams, trigrams) | Bytes | Sections |
|---|---|---|---|---|---|
| `de.mkd` | 205,888 | 66 | 219,962, 120,688 | 7,500,407 | LEXI 2,535 KiB, WORD 3,537 KiB, NGRM 1,252 KiB |
| `es.mkd` | 236,193 | 195 | 285,177, 241,438 | 8,104,171 | LEXI 2,381 KiB, WORD 3,654 KiB, NGRM 1,878 KiB |
| `fr.mkd` | 190,113 | 251 | 291,361, 225,790 | 7,140,064 | LEXI 2,175 KiB, WORD 2,979 KiB, NGRM 1,818 KiB |
| `it.mkd` | 172,831 | 190 | 312,933, 230,798 | 6,660,748 | LEXI 1,971 KiB, WORD 2,639 KiB, NGRM 1,894 KiB |
| `nl.mkd` | 178,444 | 112 | 199,041, 140,947 | 6,571,586 | LEXI 2,270 KiB, WORD 2,909 KiB, NGRM 1,237 KiB |
| `pl.mkd` | 195,099 | 124 | 282,053, 94,287 | 6,458,134 | LEXI 1,991 KiB, WORD 2,923 KiB, NGRM 1,392 KiB |
| `pt_BR.mkd` | 170,043 | 146 | 290,156, 243,005 | 6,404,145 | LEXI 1,853 KiB, WORD 2,489 KiB, NGRM 1,911 KiB |
| `sv.mkd` | 196,739 | 140 | 219,043, 138,079 | 6,999,578 | LEXI 2,297 KiB, WORD 3,208 KiB, NGRM 1,329 KiB |
| `hu.mkd` | 232,189 | 32 | 348,763, 107,285 | 8,224,316 | LEXI 2,530 KiB, WORD 3,898 KiB, NGRM 1,602 KiB |

- **Words:** the AOSP list at the same pinned commit as English, converted the same way (`AospWordList`). French keeps 190,113 of 190,425 entries (312 `not_a_word`); Portuguese uses the Brazilian list (`pt_BR`), since far more people type it; the European one (`pt_PT`, 218,457 words) could become a second pack for `pt`.
- **Hungarian** has no AOSP list. `CorpusWordList` counts `hun_news_2024_1M` with the keyboard's tokeniser (`CorpusTokens`), holding out the same one sentence in 250 as the n-gram counts:
  - Kept: words made only of the Hungarian alphabet (`aábcdeéfghiíjklmnoóöőpqrstuúüűvwxyz`, inner hyphens allowed: "EU-s", "van-e"), seen at least 3 times, at most 300,000. That leaves 232,189 of 723,689 distinct forms, covering 96.5% of the corpus's words; digits, foreign letters and the mis-encoded "õ"/"û" some sources put for "ő"/"ű" are out.
  - Case: a word is kept capitalised ("Budapest", "NATO") when at least 90% of its uses inside a sentence are; otherwise in lower case. Sentence starts don't count.
  - Frequency: by rank, on the curve the AOSP lists share (`RankFrequency`: their median by rank across the nine lists is 221 at rank 1, 138 at 1,000, 106 at 10,000, 55 at 100,000). The suggestion engine's costs were tuned on AOSP frequencies, so the same rank means the same thing to it.
  - Offensive: a short list of vulgar words and slurs written for MaKeeb (`HungarianOffensiveWords`, 32 words flagged). Prefixes only where no ordinary word shares them ("fasz" would flag "faszerkezet", a wooden frame), and only lower-case words, so names stay names.
  - Budget: agglutinative languages get 16 MB (research §10.5); `hu.mkd` is 8.2 MB.
- **Next words:** one Leipzig news corpus per language (1M sentences, 2024; Swedish 2023, the latest), counted and pruned as for English. They are smaller than English's `NGRM` (1.2–1.9 MiB against 2.5 MiB) because English counts two corpora.

## Loading at runtime

`BundledDictionaryLoader` (`:shared:keyboard`) maps the pack on a background dispatcher when the engine first asks for its dictionary, as the keyboard is created. Until the pack is mapped, `DeferredDictionary` serves the starter list, so the keyboard never waits for the pack. Debug Android builds log the timings with `adb logcat -s MaKeebDictionary`.

On a Pixel 6 Pro (Android 17, debug build, 2026-09-28):

- Loading costs 1.0 ms to map, 5.7 ms to open the header and 0.35 ms to warm the top of the trie. The pack is ready 44 ms after the IME service is created; the no-pack path takes 40 ms, which is scheduling time.
- The Java and native heaps are unchanged: 20.4 and 13.2 MB with the pack against 21.0 and 13.5 MB without, after the same typing.
- The mapping (4,044 kB) becomes resident as it is read, and counts as clean, file-backed PSS on Android.
- Next-word prediction reads `NGRM` in place and keeps nothing on the heap but a few fields. On the JVM (2026-09-29), a prediction costs 3.0 µs p50 and 4.4 µs p95 and allocates about 2.7 KB (the result strings and the lookup of the two context words). `DictionarySuggestionEngine` asks once per context for 48 predictions, which serve the strip and raise completions, and keeps only those until the context changes. Not yet measured on a device.
