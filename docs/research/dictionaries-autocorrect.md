# MaKeeb: dictionaries, autocorrect and prediction (survey and recommendation)

- **Date:** 2026-09-28
- **Builds on:** [`open-source-keyboards.md`](open-source-keyboards.md) and [`platform-apis.md`](platform-apis.md) (both 2026-09-27). This document does not repeat their keyboard and platform surveys. It cites them by section and corrects them where needed (§2.3).
- **Scope:** free word lists and frequency data, n-gram data and compact language models, memory-mapped on-disk formats, correction, completion and next-word algorithms, touch models, platform helpers, user learning, and licences. Statistical methods only (AGENTS.md → Scope). A small on-device neural language model appears once, as a later option with its cost (§4.1).
- **Board cards served:** `mmap-dictionaries`, `dictionary-packs`, `word-completion`, `autocorrect`, `next-word-prediction`, `on-device-learning`, `language-switching`, plus `proximity-correction`, `personal-dictionary` and `offensive-word-filter`.
- **Not legal advice.** Licence findings come from the licence files and terms pages as they read on 2026-09-28. Have the licence choice (§9) reviewed before the first store release.

**Evidence labels used throughout**

| Label | Meaning |
|---|---|
| *(verified)* | Checked against a primary source: a licence file, source code, a paper, or an official terms page. |
| *(measured)* | My own measurement on the named files, with the method stated. Scripts and outputs were kept in a scratch directory, not in the repo. |
| *(reported)* | From a third-party write-up, issue tracker or vendor page. Credible but not official. |
| *(estimate)* | My own calculation. The inputs are stated next to it. |

AOSP's googlesource web UI refused scripted fetches (HTTP 503) on the research date, so LatinIME source and licence metadata were read from the LineageOS mirror, which tracks AOSP (`github.com/LineageOS/android_packages_inputmethods_LatinIME`).

---

## 1. Executive summary

- **Why it feels "horrid" today.** The engine runs on a ~250-word list with a correction rule that fires whenever a nearby listed word scores at least 0. Any real word missing from the list gets "corrected" to a listed word within one edit: typing `cat ` produces `at ` (§2.1). There is no key-proximity model, no context, and touch coordinates never reach the engine. Better data and a stricter decision rule will fix more than any clever algorithm.
- **Data.** Build English on the **AOSP LatinIME `en_US` word list**: Apache-2.0, 160,715 words, 481,875 next-word links, offensive-word flags and proper-noun casing *(verified, measured)*. Refresh its frequencies and build n-grams from **Leipzig Corpora Collection downloads** (CC BY). **FineWeb** (ODC-By) and **Google Books Ngram** (CC BY 3.0) are optional extras, and **SCOWL/ESDB** (MIT-like) filters out misspellings. Use the same recipe for German, French, Spanish, Italian and Portuguese: AOSP lists exist for all of them, and so do Leipzig 1M-sentence corpora. Keep share-alike data (wordfreq, the OpenSubtitles frequency lists, Wikipedia) and GPL data (several Hunspell dictionaries, some HeliBoard lists) out of the bundled packs.
- **Format.** Use MaKeeb's own versioned pack format, read in `commonMain` through a memory-mapped byte-region port. The lexicon is a LatinIME-v2-style radix trie that stores a best-descendant score on each node, so the top-k completions come out without scanning the whole subtree. A word table and a quantised n-gram section sit alongside it. Expect about 1.6–3 MB of lexicon per 150–250k words. A full English pack with bigrams and trigrams comes to about 7 MB, inside a 10 MB per-pack budget *(measured, §4.5)*. Mapped read-only pages are clean memory, which does not count toward the iOS jetsam footprint. The heap cost stays around 1–2 MB per language.
- **Algorithms.** Use one weighted beam search over the trie, the design AOSP LatinIME uses. LatinIME is Apache-2.0, so its tuned cost constants can be reused.
  - Each hypothesis is scored by a Gaussian touch model on the real tap points, plus edit operations (omission, insertion, transposition), plus a weighted language-model cost.
  - Completions come from a best-first search.
  - Next-word prediction uses trigram → bigram → unigram backoff, with stupid backoff first.
  - Autocorrect fires only when the best candidate beats the literal typed string by a margin and passes explicit "don't correct" rules (§6.7).
  - Tune everything with a simulated-typing harness on the JVM.
- **Learning.** Keep a bounded on-device store of user words and word pairs, with LatinIME-style decay and a blocklist, and never learn in incognito fields. On iOS this store lives in the extension's own container. Without Full Access the companion app cannot see it, so a learned-words editor has to live in the keyboard.
- **Platform helpers are not a substitute.** Use `UILexicon` (contact names and text replacements). `UITextChecker` is at most an optional "is this a valid word?" guard for languages without a MaKeeb pack. Keep Android's spell-checker session off the typing path.
- **Licence risks.** MaKeeb has no LICENSE yet.
  - Apache-2.0 (recommended), MIT or MPL-2.0 all keep App Store distribution clean.
  - GPL code or data in the iOS build is a removal risk unless MaKeeb owns all of it (FSF's 2010 App Store position).
  - CC BY-SA data makes every derived pack CC BY-SA, including its no-DRM clause.
  - CC BY, ODC-By and CC BY 3.0 sources need visible attribution, and AOSP data and code need Apache NOTICE handling.
  - The HeliBoard dictionary repository is GPL-3.0 as a whole, so rebuild from the original sources instead of copying its processed files.

---

## 2. Where MaKeeb is today

### 2.1 What the code does

- **Data:** `StarterDictionaries.english()` has about 250 words in 12 hand-set frequency bands, held in an in-heap `TrieDictionary`.
- **Matching:** `TrieDictionary.corrections` walks the trie and computes one row of optimal-string-alignment (Damerau) distance per node, pruning branches that cannot get back under the limit. This part is sound and generalises to the weighted search recommended below.
- **Scoring** (`DictionarySuggestionEngine`):
  - Base score is `frequency / 255`.
  - Exact matches get +0.5, completions −0.05, corrections −0.5 per edit.
  - Allowed edits are 0 for words of 1–2 letters, 1 for 3–5 letters and 2 for longer words.
  - `autoCorrection` is the top candidate if it is a `Correction` scoring ≥ 0.0 and the typed word is not in the list.
- **Consequence** *(from reading the code)*: `cat` is not in the list. `at` is one deletion away with frequency 255, so it scores 1.0 − 0.5 = 0.5 ≥ 0, and `cat ` autocorrects to `at `. The same happens to every valid word or name that sits one edit from a word in the 128+ bands. With 250 words the engine cannot tell a typo from a word it has never seen, so autocorrect mostly damages text.
- **No geometry:** substituting `q` for `p` costs the same as `q` for `w`. `TouchController.up` emits `KeyAction.Text` and throws away the tap coordinates, so the engine never sees where the finger landed.
- **No context:** `TypingContext.previousWords` exists but nothing reads it.
- **Cost grows with the dictionary:** `completions()` collects the whole subtree under the prefix and then sorts it. That is fine for 250 words, but it breaks the "per-keystroke cost must not grow with dictionary size" rule in the input-engine skill once there are 200k words.
- **Learning:** `UserDictionary` is in-heap only and not persisted (board: `on-device-learning`).

### 2.2 What that implies

Most of the fix is data and decision policy, not search cleverness:

1. A real lexicon, so "not in the dictionary" actually means "probably a typo".
2. A rule that never corrects a valid word and needs a clear margin over the literal.
3. Key geometry, and then real touch points.
4. Context.

The trie walk the engine already has becomes the core of the weighted beam search.

### 2.3 Corrections to earlier notes

These were found during this research. The files themselves were not edited.

1. **`roadmap.md` and the `input-engine` skill** say "AOSP and HeliBoard lists are Apache-2.0". Only the AOSP lists are *(verified)*.
   - HeliBoard's `aosp-dictionaries` repository carries a GPL-3.0 `LICENSE` for the repo as a whole.
   - Its "experimental" lists are built from Leipzig word lists that the README describes as "source lists under CC BY 4.0".
   - A few lists are GPL (`el2`, `as`, `bn2`, `ur`) or CC BY-SA 4.0 (`nn`, `az`) (§3.1).
2. **`roadmap.md`** proposes "Okio/kotlinx-io" for memory mapping. Neither library maps files.
   - kotlinx-io's request for memory-mapped files is still open (Kotlin/kotlinx-io#397).
   - Okio's `FileHandle` does positional reads.
   - Android needs `java.nio` `FileChannel.map`, and iOS needs POSIX `mmap` or `NSData` with mapped reading (§5.4).
3. **The `dictionary-packs` card note** says "On iOS, network access and a shared container both involve Full Access". That is only true for the keyboard's own network use and for writes.
   - The companion app can download packs into the App Group container.
   - The keyboard can read and map them without Full Access, because it has "read-only access to the containing app's shared containers" (platform-apis.md §3.4).
   - So downloadable packs work for users who never grant Full Access.

---

## 3. Word lists and frequency data

### 3.1 Keyboard word lists (curated, ready-made)

| Source | Languages | Size (examples) | Frequencies | Next-word data | Licence | Keyboard fitness | Verdict |
|---|---|---|---|---|---|---|---|
| **AOSP LatinIME** `dictionaries/*_wordlist.combined.gz` | 34 word lists: bg, cs, da, de, el, en (US, GB, AU and generic), eo, es, fi, fr, hr, hu, hy, it, iw, ka, lb, lt, lv, nb, nl, pl, pt-BR, pt-PT, ro, ru, sl, sr, sv, tr, uk *(verified: OpenBoard 1.4.5 and LineageOS listings)* | en_US 160,715 words; de 205,914 *(measured)*; hu 66,005 (HeliBoard README) | 0–255 log scale | en_US only: three successors per word (481,875 links); most other languages have none *(measured)* | **Apache-2.0**: covered by the repo's `NOTICE`; `Android.bp` declares `SPDX-license-identifier-Apache-2.0` for the whole project *(verified)* | Hand-curated. Proper nouns keep their case (42,543 capitalised entries in en_US). Flags: 1,300 `possibly_offensive`, 99 shortcuts (`dont` → `don't`), `not_a_word`. Frequencies date from about 2014 | **Base for every language it covers** |
| **HeliBoard `aosp-dictionaries`** (Codeberg), stable lists | The AOSP set, taken from OpenBoard 1.4.5, plus new lists (el2, tr2, nn, az, bn) | as AOSP | as AOSP | as AOSP | Repo `LICENSE` is GPL-3.0. Per-list notes: AOSP lists; `el2` GPL-3.0; `as`, `bn2`, `ur` GPLv2; `nn` CC BY-SA 4.0 (Wikipedia); `az` CC BY-SA 4.0 and Leipzig *(verified: README)* | Same as AOSP | Take AOSP lists from AOSP, not from here |
| **HeliBoard** "experimental" lists | About 50 | en_US 279,960 words and 104,703 bigrams; de 1,343,314 words; pl 2,599,774 *(measured / README)* | yes | yes | README: built from Leipzig word lists, "source lists under CC BY 4.0". Its scripts and the repo are GPL-3.0, so the licence of the processed files is ambiguous | Names mostly missing (they fail the Hunspell check). `possibly_offensive` comes from Hunspell `nosuggest` flags and AOSP lists | **Copy the recipe, not the files.** Rebuild from Leipzig directly |
| FlorisBoard | none to reuse | – | – | – | Apache-2.0 | `main` loads a JSON placeholder map (`ime/dict/data.json`). The C++ `florisboard/nlp` repo was last pushed 2023-12 *(verified)* | Nothing to reuse yet |
| AnySoftKeyboard language packs | Many (APK add-ons) | English pack = the AOSP `en_wordlist` plus text inputs *(verified: repo tree)* | yes | own format | Apache-2.0 code; data per pack | – | Reuse means reusing AOSP |
| FUTO Keyboard | Uses HeliBoard's dictionaries (open-source-keyboards.md §3.1) | – | – | – | FUTO Source First 1.1 (code); FUTO model licence | Adds an optional transformer LM (§4.1) | Reference only |
| Unexpected Keyboard + `cdict` | HeliBoard lists, rebuilt | en_US about 0.96 MB (gzipped download) *(verified: repo listing)* | yes | no | `cdict` MIT; data inherits the source lists | – | Useful format reference (§5) |
| Keyman lexical models | Many minority languages | English model from Reddit (MTNT corpus) | yes | no | MIT per model (check each `LICENSE.md`) | Reddit register, "R-rated at times" (README) | Niche; later |

### 3.2 Frequency and corpus sources

| Source | Languages | What you get | Licence | Fitness | Verdict |
|---|---|---|---|---|---|
| **Leipzig Corpora Collection** (downloads) | 250+ | Per corpus (news, web, Wikipedia; by year): sentences, a word list with counts, neighbour co-occurrences (`co_n`, effectively bigrams) and sentence co-occurrences. Sizes 10K to 1M sentences. 1M corpora exist for eng, deu, fra, spa, ita, por, hun news 2023 (230–293 MB each) *(verified: file listing)*. A 1M-sentence English news corpus is 19.65M tokens *(measured, §4.5)* | **"The text corpora offered for download are made available under the Creative Commons licence CC BY."** Data used through the web portal is CC BY-NC *(verified: Terms of Usage)* | News and web register, not chat. Needs tokenising and cleanup. Fresh (2023–2024) | **Primary source for frequencies and n-grams** |
| **Google Books Ngram** v3 (20200217) | English (all, US, GB, Fiction), Chinese (simplified), French, German, Hebrew, Italian, Russian, Spanish | 1- to 5-grams with per-year counts | **CC BY 3.0 Unported** *(verified: datasets page)* | Book register, OCR noise, historical spellings | Optional: unigram smoothing for rare words (years ≥ 2000) |
| **FineWeb** (English) / **FineWeb-2** (1000+ languages) | en; 1000+ others (e.g. German 262B words, Hungarian 31B) | Cleaned, deduplicated Common Crawl text, 2013–2024 | **ODC-By 1.0**, "also subject to CommonCrawl's Terms of Use" *(verified: dataset card)* | Web register, fresh, huge; needs a sampling job | Optional when Leipzig is too small |
| OSCAR 23.01 / HPLT 2.0 | many | Web text | CC0 for the packaging; the authors keep their rights in the text *(verified: dataset cards)* | as FineWeb | Alternative to FineWeb |
| Tatoeba | 400+ | Short, everyday sentences | **CC BY 2.0 FR**; part also CC0 *(verified)* | Conversational; small | Test sets; small next-word boost |
| Mozilla Common Voice text | ~100 | Read-aloud prompts | CC0 *(reported)* | Small | Test sets |
| SCOWL / **ESDB** (its successor) | English (US, GB -ise/-ize, CA, AU) | Words with a "size" level (10–95, meaning commonness), variants, POS, inflections. No counts | "Freely available under an MIT-like license"; sources BSD-compatible *(verified: README)* | The best English validity filter | **Filter for English** |
| 12dicts | English | Word lists | Public domain (part of SCOWL) | – | Via SCOWL |
| SymSpell `frequency_dictionary_en_82_765` | English (SymSpell also has fr, de, es, it, he, ru, zh lists) | 82,765 lowercased words with counts, plus 243,342 bigrams | Repo MIT; built from Google Books Ngram (CC BY 3.0) ∩ SCOWL *(verified: README)* | Clean, but no names or case | Handy permissive cross-check |
| Hunspell (LibreOffice/dictionaries) | ~100 | Affix-compressed word lists, no frequencies | Varies per language (table below) | Validity only | Filter, where the licence allows |
| hermitdave **FrequencyWords** | ~60 (OpenSubtitles 2016/2018) | Top-50k and full lists with counts | Code MIT; "CC-by-sa-4.0 for content" *(verified)*. The rights in the underlying OpenSubtitles data are unclear | Conversational register (good), but full of names, profanity and subtitle artefacts | Optional downloadable pack only |
| **wordfreq** | 40+ | Zipf frequencies | Code Apache-2.0; data CC BY-SA 4.0. The author asks that it not be converted to CSV, because that drops the attribution. **Sunset:** "the data is unlikely to be updated again" *(verified: README)* | Well balanced | Avoid in bundled packs |
| Wikipedia / Wiktionary dumps | all | Text; inflection tables (via wiktextract) | CC BY-SA (plus GFDL) | Encyclopedic register | Share-alike; avoid in bundled packs |
| Norvig `count_1w.txt`, `count_2w.txt` | English | 333k words; 250k bigrams | Code MIT. The data derives from the Google Web Trillion Word Corpus (LDC) and has no data licence *(verified: page)* | – | **Avoid** (no grant) |

Hunspell licences for the languages MaKeeb is likely to add first *(verified: LibreOffice/dictionaries README and LICENSE files)*:

| Language | Licence |
|---|---|
| English (all variants) | SCOWL: MIT-like |
| German (de_DE_frami) | GPL-2.0 or GPL-3.0 |
| French | MPL-2.0 |
| Spanish | Your choice of GPL-3.0+, LGPL-3.0+ or MPL-1.1+ |
| Italian | GPL-3.0 |
| Portuguese (Brazil) | LGPL-3.0 and MPL |
| Dutch (OpenTaal) | Revised BSD and/or CC BY 3.0 |
| Polish | GPL, LGPL, MPL, Apache-2.0 or CC SA |
| Hungarian | MPL-2.0 or LGPL-3.0+ |
| Russian | BSD-style |

Using a Hunspell dictionary only as a build-time filter ("drop corpus tokens that fail the spell check") copies no dictionary content into the pack. It is low risk even for the GPL ones. Copying flags such as `nosuggest` into the pack, as HeliBoard's experimental script does, does copy data.

### 3.3 Measured: the two leading free English lists

*(measured: counts over the `.combined` files from Codeberg on 2026-09-28)*

| | AOSP en_US (2014) | HeliBoard experimental en_US (Leipzig, 2024) |
|---|---|---|
| Words | 160,715 | 279,960 |
| Next-word links | 481,875 (three per word, weights 1–3) | 104,703 |
| `possibly_offensive` | 1,300 | 1,344 |
| Capitalised entries | 42,543 | 61,472 |
| Shortcuts | 99 | 99 (merged from AOSP) |
| LatinIME `.dict` size | 2.96 MB | 2.50 MB |

The AOSP list is smaller, curated and permissively licensed, and it already carries next-word links. The Leipzig-built list is fresher and larger, but noisier. The right English base is the AOSP list, with its frequencies refreshed and its gaps filled from Leipzig counts that pass the ESDB filter.

### 3.4 What makes a list keyboard-ready

- **Case and proper nouns.** Store each word's canonical form ("London", "iPhone", "I"), look it up case-folded, and keep the user's shift state (the current `matchCase` does this). Decide proper-noun status from corpus case ratios. For example, a word capitalised in ≥ 90% of its mid-sentence occurrences is a name.
- **Profanity.** Carry an offensive flag per word (AOSP ships 1,300 for en_US). By default, never suggest a flagged word or autocorrect *to* it unless the user typed it exactly. This is the `offensive-word-filter` card; AOSP and HeliBoard behave the same way.
- **Misspellings in web data.** Corpus-derived lists contain frequent misspellings ("recieve"). Filter with ESDB for English, and elsewhere with Hunspell used as a filter plus frequency cut-offs. Keep known misspellings as *error-model* data (misspelling → correction pairs), never as words.
- **Register.** Keyboards write chat, not news. Subtitle and chat corpora match best but have the murkiest rights. Leipzig "web" corpora and FineWeb are acceptable. Personalisation closes the rest of the gap (§8).
- **Freshness.** AOSP frequencies date from about 2014, so recount from 2020–2024 corpora.
- **Normalisation.** Use NFC, fold `’` to `'`, and handle ligatures. Kotlin `commonMain` has no Unicode normaliser (`java.text.Normalizer` is JVM-only), so normalise at build time and ship any folding table inside the pack (§5.4). Turkish and Azerbaijani need their own casing rules (dotted and dotless i), which `lowercase()` in common code does not apply.
- **Vocabulary size.** English needs about 150–250k word forms. Inflecting and compounding languages need far more: HeliBoard's experimental German list has 1.34M, and Hungarian 1.06M. Cap the size, and let the literal-word path (§6.5) cover what is left.

---

## 4. N-gram data and compact language models

### 4.1 What existing keyboards ship

- **AOSP LatinIME, main dictionaries (format v2):**
  - One byte of log frequency per word.
  - An optional bigram list per word: a 4-bit frequency plus a relative address (`FormatSpec.java`, `MAX_BIGRAM_FREQUENCY = 15`) *(verified)*.
  - The AOSP en_US list stores three successors per word.
- **AOSP LatinIME, dynamic dictionaries (v4, used for user history):**
  - Store up to 4-grams (`MAX_PREV_WORD_COUNT_FOR_N_GRAM 3`) with timestamps, levels and counts for decay *(verified: `defines.h`, `forgetting_curve_utils.cpp`)*.
- **HeliBoard** uses the same engine with the Leipzig-built lists (§3.1).
- **FUTO Keyboard** uses the same engine plus an optional Llama-architecture transformer of about 36M parameters, run through llama.cpp *(reported)*.
  - At 4–8-bit quantisation that is roughly 18–36 MB of weights *(estimate)*, before activations.
  - That alone exceeds MaKeeb's 30 MB iOS extension budget, and FUTO's FAQ admits lag on low-end devices (open-source-keyboards.md §3.1).
  - It stays out of scope for now. It could only be a later, Android-first option, and only if its footprint and latency fit.
- **Gboard** (Ouyang et al. 2017) *(verified: paper)*:
  - Keyboard LMs "should not exceed 5 to 10 Mb, which typically allows them to model a couple hundred thousand words at most".
  - They are "low order n-grams over a limited vocabulary, e.g. 64K words", trained on a hand-curated vocabulary.
  - Decoding composes a spatial model, a lexicon and the n-gram LM as finite-state transducers.

### 4.2 Smoothing: what to use for a small keyboard model

| Method | For | Against | Use in MaKeeb |
|---|---|---|---|
| **Stupid backoff** (Brants et al. 2007) | Trivial to build and store (relative frequencies, a fixed 0.4 backoff factor). Good for ranking a context's successors | Scores are not probabilities, so the weight against the touch model has to be tuned empirically | **v1 next-word model** |
| **Katz / absolute discounting** | Proper probabilities; holds up under pruning | Slightly worse than Kneser-Ney when unpruned | v2, if scores need calibrating for autocorrect |
| **Kneser-Ney** (modified, interpolated; Chen & Goodman) | Best perplexity when unpruned | Degrades badly under aggressive entropy pruning. Chelba et al. 2010 measured about 10% relative WER loss when pruning to 0.1% of the original, and Katz held up better | Only if the model is lightly pruned |

The count data is the same for all three, so the builder can emit counts first and choose the estimator later.

### 4.3 Pruning to a few megabytes

- **Levers:**
  - Count cut-offs (bigrams ≥ 2–3, trigrams ≥ 3–5).
  - Restrict the vocabulary to the lexicon (everything else maps to `<unk>`).
  - Keep only the top-N successors per context for prediction (N = 8–16).
  - Relative-entropy pruning (Stolcke 1998) for the rest.
- **Quantisation:** one byte per unigram log-probability. Four to eight bits per n-gram log-probability; LatinIME uses 4 bits for bigrams.
- **Layout:**
  - Give each context word ID an offset into a block of successors sorted by ID.
  - Delta-varint encode the successor IDs, followed by a 1-byte quantised score.
  - Trigrams key on the pair (w1, w2) through a sorted context table.
  - A simple sorted-array trie is enough at this scale. KenLM's quantised trie, BerkeleyLM and Tongrams' Elias–Fano tries show how to go smaller if needed (Heafield 2011; Pauls & Klein 2011; Pibiri & Venturini 2019).
- **Size arithmetic:**
  - With this layout a bigram costs about 2.4 bytes including the index, and a trigram about 5.7 bytes with a naive (w1, w2) context table *(measured, §4.5)*.
  - About 1M bigrams and 0.4M trigrams therefore fit in roughly **5 MB**, in line with Gboard's "5 to 10 Mb" for the whole model.

### 4.4 Tools

- **KenLM** (`lmplz`, `build_binary`) is LGPL-2.1. Use it only as an offline build tool; it never ships, and its ARPA output is data, not a derivative of the code.
- SRILM's licence is not open source, so avoid it.
- For Leipzig-scale data (tens of millions of tokens per language) the Kotlin builder can count n-grams itself with an external sort (§10.3). FineWeb-scale counting (billions of tokens) belongs in a separate, occasional job. Only its pruned counts enter the build.

### 4.5 Measured n-gram counts from one Leipzig corpus

*(measured: `eng_news_2023_1M` from the Leipzig downloads. Lowercased; tokens `[a-z]+('[a-z]+)*`; the vocabulary is the AOSP en_US list, and everything else becomes `<unk>`. Encoding as in §4.3: successor IDs ranked by frequency and delta-varint coded, a 1-byte score, 4 bytes of index per context, and 10 bytes per trigram context key.)*

| Measure | Value |
|---|---|
| Sentences / tokens | 1,000,000 / 19.65M |
| Tokens outside the AOSP en_US vocabulary | 3.38% |
| Distinct bigrams (count ≥ 2 / ≥ 3 / ≥ 5) | 3.43M (1.10M / 676k / 398k) |
| Distinct trigrams (count ≥ 2 / ≥ 3 / ≥ 5) | 10.18M (1.60M / 803k / 384k) |
| Bigram section, count ≥ 2, all successors | 1.10M bigrams → **2.60 MB** (2.37 bytes per bigram including the index) |
| Bigram section, count ≥ 2, top 16 per context | 276k bigrams → 0.88 MB |
| Bigram section, count ≥ 2, top 8 per context | 187k bigrams → 0.68 MB |
| Trigram section, count ≥ 5 | 384k trigrams → **2.20 MB** (5.7 bytes per trigram; the context keys dominate) |
| Trigram section, count ≥ 3 | 803k trigrams → 4.43 MB |

- The AOSP vocabulary covers 96.6% of news tokens, which confirms it is a sound English backbone.
- A full English pack with a 2 MB lexicon, a 2.6 MB bigram section and a 2.2 MB trigram section comes to about **7 MB**, inside the 10 MB budget. That is before combining more corpora; more data mostly raises the counts, and the cut-offs are then set to fit the budget.
- Trigram context keys are the obvious place to save space later, for example by grouping trigrams under their bigram entry, as KenLM's trie does.

---

## 5. On-disk formats for memory mapping

### 5.1 Options

| Format | Structure | Lookup / completion / fuzzy search | Size | Pure Kotlin over a mapped region | Notes |
|---|---|---|---|---|---|
| **LatinIME v2** `.dict` | Radix (Patricia) trie. 1-byte characters for U+0000–U+00FF and 3 bytes otherwise; 1–3-byte relative child pointers; 1-byte frequency on terminals; bigram and shortcut lists on terminals *(verified: `FormatSpec.java`)* | All three; the native LatinIME search walks it directly | en_US 2.96 MB including 481,875 bigram links; de 1.61 MB for 205,914 words without bigrams *(measured: file sizes)* | Yes: simple byte parsing | No suffix sharing. To output a bigram target you have to reconstruct the word from a node address |
| LatinIME v4 | Several files (trie, frequencies, n-gram content, historical info), mutable | All | Larger | Yes, but complex | Built for user history; not needed for read-only packs |
| **Radix trie + best-descendant score** (proposed) | v2-style nodes plus one byte per node holding the best score below it, and a 3-byte word ID on terminals | Top-k completion by best-first search in O(k · depth) without scanning the subtree; fuzzy search as a beam | about 2.0 MB for 158k English words, 2.8 MB for 206k German *(estimate: 1 flag byte + label bytes + 3-byte pointer + 1 score byte per node, + 4 bytes per terminal, on the measured node counts)*. A LatinIME-tight encoding would come in lower | Yes | **Recommended for v1** |
| **DAFSA** (minimal acyclic automaton; Daciuk et al. 2000) + perfect hashing | Shares suffixes. Per-word data needs a word index, computed from counts stored on transitions (the scheme `cdict` uses) | Lookup and fuzzy search: yes. Top-k completion: the best score per state depends on the path (shared states serve many prefixes), so you need subtree enumeration or precomputed tables for short prefixes | 136,269 transitions for AOSP en_US, about **0.70 MB** at 4 bytes per transition + 1 byte per word; 1.19 MB for AOSP de; 6.58 MB for the 1.32M-word German list *(measured: own Daciuk build; estimate for bytes)* | Yes | Best choice for huge vocabularies (de, hu, fi, tr, pl, ru) |
| **LOUDS / marisa-trie** (Jacobson 1989; Yata) | Succinct tree with rank/select bit vectors, recursively compressed labels | Lookup, prefix enumeration and fuzzy search are all possible, but every navigation step costs rank/select work | **0.43 MB** (AOSP en_US), 0.88 MB (Leipzig en_US), 0.60 MB (AOSP de), 3.87 MB (1.32M de) *(measured: marisa-trie 1.x, words only)*. About the same as gzip -9 of the word list | Possible. Porting marisa is a large job | marisa is BSD-2-Clause or LGPL-2.1+. Only worth it if download size becomes the bottleneck |
| **Lucene FST** (weighted minimal transducer) | Minimal automaton with outputs. Weight pushing makes top-N weighted completion work (Lucene's `WFSTCompletionLookup`) | All | Between DAFSA and radix trie | Port of Apache-2.0 Java code (several thousand lines) | A good later upgrade that gets both compactness and top-k |
| SymSpell index | Hash of every delete-variant within distance *d* | Fast correction for plain edit distance only; no prefix completion; no touch model | Several times the word list for *d* = 2; the README says prefix indexing cuts it by "more than 90%" | Would need an on-disk hash table | Not a fit (§6.2) |
| n-gram tries (KenLM TRIE, BerkeleyLM, Tongrams) | Sorted arrays of word IDs per context with compressed pointers and quantised probabilities | n-gram lookup and successor lists | About 2–4 bytes per n-gram *(estimate)* | Yes (sorted arrays) | The n-gram section in §10.2 is a small version of this |

Raw baselines for comparison *(measured)*:

| List | Raw word text | gzip -9 |
|---|---|---|
| AOSP en_US | 1.53 MB | 0.44 MB |
| Leipzig en_US | 3.06 MB | 0.90 MB |
| AOSP de | 2.59 MB | 0.69 MB |
| 1.32M-word German list | 20.56 MB | 4.60 MB |

### 5.2 Why file size matters less than heap on iOS

- iOS counts **dirty and compressed** memory toward an app's footprint. Clean memory, "data that can be paged out, which includes memory-mapped files", does not count (WWDC 2018 session 416, *iOS Memory Deep Dive*).
- A keyboard developer measured a baseline drop from **52 to 27 MB** by moving a dictionary from parsed literals to a memory-mapped file (platform-apis.md §3.6).
- So a 3–10 MB read-only pack costs almost nothing against jetsam. The cost is in what the code does with it:
  - Never copy mapped bytes into Kotlin arrays or collections at load time.
  - Never build `String`s during the search. Compare code points and materialise strings only for the few final candidates.
  - Keep per-keystroke scratch space preallocated.
  - Bound every cache. Do not keep 200k decoded nodes around.
- The remaining costs of file size:
  - App download size.
  - Page-cache pressure.
  - The first-touch latency of cold pages. Lay the trie out breadth-first so the top levels share a few 16 KB pages, and touch that "hot" region off the main thread when the keyboard appears.

### 5.3 Why v1 should be a radix trie and not a DAFSA

- Completion is the most frequent query: on every keystroke, show the top three words under a prefix.
- A radix trie can store a per-node best-descendant score, so the top-k search is output-sensitive. A DAFSA cannot, because its shared states serve many prefixes.
- The radix trie is also the structure LatinIME's beam search walks, the easiest to debug, and at 2–3 MB per language its extra size over a DAFSA is clean memory.
- Move large-vocabulary languages to a DAFSA or a weighted FST later, behind the same `Dictionary` interface, if their packs grow beyond budget.

### 5.4 Feasibility in pure Kotlin `commonMain`

- **Port.** Define a small read-only interface in common code, for example `ByteRegion` with `size`, `u8(offset)`, `u16(offset)`, `u24(offset)`, `u32(offset)` and a bulk `copyInto`.
  - It lives in `:core:common` or `:engine:dictionary`. The OS adapter lives in a `:platform:*` module (AGENTS.md: "Anything OS-specific is a port in `:platform:*`").
  - The engine stays platform-free, and JVM tests use a `ByteArray`-backed implementation.
- **Android / JVM.**
  - `FileChannel.map(READ_ONLY, …)` gives a `MappedByteBuffer`. Its absolute `get(index)` reads need no copy, and the mapping lives outside the Java heap.
  - Bundled packs can be mapped straight out of the APK if they are stored uncompressed. This is exactly what LatinIME does: `aaptflags: ["-0 .dict"]` in `java/Android.bp`, `noCompress 'dict'` in `build.gradle`, and `openRawResourceFd(...)` → `(sourceDir, getStartOffset(), getLength())` in `BinaryDictionaryGetter` *(verified)*.
  - With AGP: `androidResources { noCompress += "mkd" }`.
  - APK assets are readable in direct boot, which credential-encrypted downloads are not.
- **iOS.**
  - Kotlin/Native's bundled POSIX bindings expose `platform.posix.mmap` and `open`, so no custom cinterop is needed. `NSData(contentsOfFile:options: .alwaysMapped)` plus `bytes` is the Foundation alternative.
  - Read through a `CPointer<ByteVar>`.
  - Packs in the App Group are readable (and mappable) without Full Access.
  - Check the data-protection class on packs the companion app writes. `completeUntilFirstUserAuthentication` keeps them readable after the first unlock.
- **Cost of the abstraction.**
  - An interface call per byte is measurable but small next to the search itself.
  - Keep one implementation per platform (monomorphic call sites) and read multi-byte fields in one call.
  - Keep node records simple enough that a node decodes in a handful of reads.
  - A beam search that expands at most ~150–200 hypotheses per input character stays well under a millisecond per keystroke on the JVM *(estimate; measure in the harness, §6.8)*.
- **Portability.** Fix the byte order. Use a magic number, a format version, and a section table with offsets and lengths. Store a CRC32 and verify it when a pack is installed, not every time it is mapped.

---

## 6. Correction and completion algorithms

### 6.1 One model: the noisy channel

Pick the word *w* that maximises P(taps | *w*) · P(*w* | context)^λ. This is the Kernighan, Church & Gale (1990) spelling formulation, with the "channel" replaced by the touch model.

The evidence that this combination is what matters:
- Goodman et al. (2002) reported that combining a key-press model with a language model reduced soft-keyboard error rates by a factor of 1.67–1.87.
- Google (Fowler et al., CHI 2015) measured that "a combined spatial/language model reduces word error rate from a pre-model baseline of 38.4% down to 5.7%, and that LM personalization can improve this further to 4.6%".

### 6.2 Candidate generation options

| Approach | How it works | Fit for a keyboard | Verdict |
|---|---|---|---|
| Norvig edits (2007 essay) | Generate every string within 1–2 edits and look each one up | Huge candidate sets (54n + 25 strings per edit for a 26-letter alphabet, squared for two edits); no touch weights | Teaching reference only |
| **SymSpell** (MIT) | Precompute delete-variants of the dictionary; at query time generate deletes of the input and intersect | Very fast for plain Damerau distance, but it cannot weight substitutions by key distance or tap position, does no prefix completion, and its index is large and hash-based (poor fit for mmap) | No |
| BK-tree (Burkhard & Keller 1973) | Metric tree over edit distance | Needs a true metric (weighted touch costs are not one); visits many nodes at distance 2 | No |
| Levenshtein automata (Schulz & Mihov 2002; used by Lucene) | An automaton for "within *d* edits", intersected with the lexicon | Elegant for unweighted distance; weighted costs lose the advantage | No |
| **Trie walk with a DP row per node** (what `TrieDictionary` does now) | Depth-first search with pruning by minimum row value | Correct for unweighted distance, but the search cost grows with fan-out, not with a beam | Keep as the reference in tests |
| **Weighted beam search over the trie** (LatinIME) | Priority queue of (trie node, input index, cost). Moves: match or substitute (spatial cost), omission, insertion, transposition, completion past the input, and optionally a space for multi-word input | Handles touch points, per-operation costs, completion and LM costs in one pass. Bounded work per keystroke | **Use this** |

LatinIME's beam keeps at most `MAX_CACHE_DIC_NODE_SIZE = 170` hypotheses (310 for single-point input, 50 for low-probability locales) *(verified: `scoring_params.cpp`)*.

### 6.3 Error model: weighted edits

The current engine charges 1 for every edit. LatinIME's tuned typing costs (Apache-2.0, `scoring_params.cpp`) show how uneven real errors are *(verified)*:

| Operation | LatinIME cost |
|---|---|
| Proximity (adjacent key) | 0.0694, plus 0.07788 for the first proximity error in a word |
| Substitution (non-adjacent) | 0.3806 |
| Omission (user skipped a letter) | 0.467; 0.345 for a doubled letter; 0.5256 for the first letter |
| Intentional omission (apostrophe or hyphen not typed) | 0.1 |
| Insertion (extra tap) | 0.7248; 0.5508 for a repeated letter; 0.674 next to a proximate key |
| Transposition | 0.5608 |
| Completion (first extra character / each further one) | 0.4836 / 0.00624 |
| Language weight (`DISTANCE_WEIGHT_LANGUAGE`) | 1.1214 |
| Spatial distance weight (`DISTANCE_WEIGHT_LENGTH`) | 0.1524 |

Apostrophes are cheap on purpose, so `dont` finds `don't`. Omissions are cheaper than insertions, and a first-letter error is expensive.

Brill & Moore (2000) generalise the error model to learned string-to-string edits ("ant" → "ent"). That is useful later for spelling (cognitive) errors: build a small substitution table from misspelling lists at build time. Fat-finger errors are better handled by the spatial model below.

### 6.4 Spatial touch model

- **Model.** P(tap | key) is a bivariate Gaussian per key, centred near the key centre. Gboard's decoder uses "a Gaussian distribution centered on each key center" (Ouyang et al. 2017). The cost of a tap for key *k* is ½ · ((dx/σx)² + (dy/σy)²), with dx and dy measured in key widths and heights. Start with σ of about 0.3–0.5 key widths, then tune.
- **Offsets are real and posture-dependent.** Azenkot & Zhai (2012) found that tap centroids shift with hand posture and keyboard region (for example, right-thumb taps land right of centre on the right side), so a single global offset is naive. LatinIME applies per-row "sweet spot" corrections: x and y offsets and a radius (`TouchPositionCorrection`, `ProximityInfo`) *(verified)*.
- **Adaptation (later).** After a word is committed and not reverted, and never in incognito, update each key's mean and variance from the taps that produced it, with slow learning rates and bounds.
- **Key-target resizing (optional, later).** Gunawardana, Paek & Meek (2010) used the language model to resize hit targets invisibly. Their anchoring rule keeps every key reachable. MaKeeb gets most of this benefit from decoding instead, without moving hit boxes.
- **What MaKeeb must change:**
  - `TouchController` must report the tap position with each letter, for example on `KeyAction.Text` or through a side channel, normalised to key units using `LayoutGeometry`.
  - `InputEngine` must keep a list of tap points aligned with the composing word.
  - When the points are unknown (after `resyncWithHost`, a cursor move back into a word, or a paste), fall back to key centres. The model then reduces to geometric key proximity.
  - Long-press alternates and popup picks are exact choices: give them no spatial noise.

### 6.5 Combining touch, edits and the language model

For a candidate *w* given taps *t* and context *h*:

```
cost(w)       = Σ spatial(tᵢ, keyᵢ(w)) + edit costs + λ · (−ln P(w | h)) + completion cost
cost(literal) = Σ spatial(tᵢ, typed keyᵢ) + λ · (−ln P_unk(typed))
```

- **P(*w* | *h*)** is the backoff n-gram score (§4.2), interpolated with the user model (§8).
- **The literal path** is what lets people type words the lexicon lacks. P_unk is a per-character penalty or a tiny character-level model. Gboard's decoder scores "the typed characters (known as the 'literal') using a character-level model" and "applies an additional cost for correcting away from an already valid word".
- **Weights.** Start from LatinIME's relative weights (the language weight is about 7× the spatial-distance weight) and tune λ and the thresholds on the harness (§6.8).
- **Multilingual typing.** Run the search over each active language's lexicon. A word that is valid in any active language is not corrected. LatinIME weights languages and has a "plausibility threshold" for this.

### 6.6 Completion

- Use best-first search from the prefix node using the stored best-descendant scores, plus up to a few fuzzy prefix nodes from the beam, so a typo early in the word still gets completions.
- Boost candidates that appear in the current context's successor list.
- Show a completion only if it beats the typed word by a margin and the prefix has at least 2 characters.
- Never show the typed word twice.
- **Diacritics:** match against folded keys (é → e) at a small cost, and show the surface form ("naive" finds "naïve"). LatinIME penalises accent, case and digraph differences on exact matches with 0.02, 0.01 and 0.03 respectively *(verified)*.
- **Apostrophes:** use intentional-omission costs (above). Strip trailing quotes before searching, as LatinIME does (`trailingSingleQuotesCount`).

### 6.7 When to autocorrect

**Threshold.** LatinIME normalises the best candidate's score by length and edit distance. It autocorrects when that normalised score exceeds 0.185 ("modest"), 0.067 ("aggressive") or −∞ ("very aggressive"), and never when the setting is off (`config-auto-correction-thresholds.xml`) *(verified)*. MaKeeb should express its threshold as a cost margin, cost(literal) − cost(best) ≥ θ, with three or four aggressiveness levels and a per-language off switch (board: `autocorrect`).

**LatinIME's "never autocorrect" rules** *(verified: `Suggest.java`)*:

- The typed word is in any dictionary (main, user or learned), unless a whitelist entry maps it to a correction.
- The word has one character.
- The strip is showing predictions (nothing is being typed).
- There are no results.
- The word contains digits (the code comment says such words are likely typed with care).
- The word is mostly capitals.
- Suggestions were resumed on an old word (revisiting it).
- No main dictionary is loaded, because a contact name such as "Will" would otherwise hijack "will".
- The top suggestion is a shortcut.

**Rules to add for MaKeeb**, some of which already exist (password fields, the user or field disabling it, `rejectedCorrection`):

- Tokens that look like URLs, e-mail addresses, @handles or #tags.
- A capitalised unknown word in mid-sentence. Treat it as a name; raise the margin or don't correct.
- Never correct *to* an offensive word.
- Words in the user dictionary or the learned store.
- After a revert, add the literal to the user dictionary (or count it towards learning), so "Kiraly" is not corrected again elsewhere.
- Words valid in any active language.
- Short words (≤ 3 letters) need a larger margin.
- Real-word errors ("form" → "from") are not corrected in v1. That needs context-driven post-correction (Gboard's "post-corrections") and comes much later.

**Interim fix before real data lands:** with a 250-word list, only a small whitelist of known typos (`teh` → `the`, `dont` → `don't`) should autocorrect. Everything else should stay a suggestion.

### 6.8 Measure it: a simulated-typing harness

- Google's Octopus / "remulation" (Bi, Azenkot, Partridge & Zhai, CHI 2013) and the simulator in Fowler et al. (2015) evaluate decoders without user studies. They replay or simulate noisy taps over held-out sentences.
- MaKeeb can do the same in a JVM test source set or a small benchmark module:
  - Sample taps from the Gaussian key model (with posture offsets) over sentences from Tatoeba (CC BY) or Common Voice (CC0).
  - Feed them through `TouchController` → `InputEngine` → `FakeTextHost`.
  - Report:
    - Word error rate after autocorrect.
    - The **false-correction rate** on clean input (the "autocorrupt" number that users hate).
    - The share of typos fixed.
    - Keystroke savings from completion.
    - Top-3 next-word accuracy.
- Gate regressions on these metrics. Do not commit timing assertions (performance-budget skill).
- Set the targets after the first run, from the baseline.

**Stage 1 baseline** *(measured 2026-09-28)*. The harness is `makeeb/tools/dictionaries/src/test/.../harness/TypingHarness.kt`; run it with `./gradlew :tools:dictionaries:typingHarness`, adding `-Pharness.pack=starter` for the starter list.

- **Corpus:** 125 held-out everyday sentences (1,170 words), written for MaKeeb (`src/test/resources/harness/everyday-en.txt`).
- **Typing:** every word goes through the real `InputEngine` and `DictionarySuggestionEngine` via `FakeTextHost`, in lower case, relying on auto-capitalisation. There is no user dictionary.
- **Typos:** each letter is replaced by a random neighbouring QWERTY key with probability 5%, seed 20260928.
- **Keystroke savings** assume a user who taps the word as soon as the strip shows it, so they are an upper bound.

| | Starter list (204 words) | AOSP en_US pack (160,668 words) |
|---|---|---|
| Corpus words the dictionary knows | 63.3% | 100% |
| Keystroke savings from completion | 21.5% | 35.0% |
| False corrections on clean typing | 0.0% (0 of 1,170) | 0.0% (0 of 1,170) |
| Typos fixed by autocorrect | 0.0% (0 of 234) | 0.0% (0 of 234) |
| Typos with the intended word in the strip | 41.0% | 76.1% |
| Key cost on the JVM, p50 / p95 | 9 / 26 µs | 5 / 81 µs |

Stage 0's policy only corrects whitelisted typos (`KnownTypos`), and random neighbour-key substitutions never produce those, so the typo-fix rate is 0%. Stage 3 has to raise it without raising the false-correction rate. On device, the pack raised per-key main-thread cost on the Pixel 6 Pro (debug build) from p50 1.9 ms / p95 2.4–3.0 ms to p50 3.1–3.4 ms / p95 5.4–8.7 ms. The first 50 keys, before the JIT warms up, reached p95 17 ms. Almost all of that is the unweighted edit-distance walk: 12–16k DP rows for a 6+ letter word with two allowed edits. That walk is what Stage 3's bounded beam search, run off the main thread (§6.10), replaces.

### 6.9 Swipe typing (pointer only)

- Gesture decoding reuses the same lexicon and language model with a different channel model:
  - Template matching of the path against each word's key sequence (SHARK² style; Kristensson & Zhai 2004), or an FST decoder (Gboard).
  - Reuse candidates: FUTO Swipe and HeliBoard's NLnet library (open-source-keyboards.md §1).
- The pack format should allow fast lookup by first and last letter (the trie gives the first; add a last-letter index later).

### 6.10 Where the work runs

- `suggest` runs on the main thread on every keystroke today (input-engine skill). With a beam search the cost is bounded but not trivial.
- Recommended split:
  - Compute suggestions on a single background worker with latest-wins cancellation.
  - Make the autocorrect decision synchronous on the separator. Use the finished result for the exact composing text, or run a tightly bounded search if it is stale.
- This keeps touch → commit inside the 16 ms budget (performance-budget skill).

---

## 7. Platform helpers

| Helper | What it gives | Cost and limits | Use in MaKeeb |
|---|---|---|---|
| **iOS `UILexicon`** (`requestSupplementaryLexicon`) | Unpaired first and last names from Contacts, the user's text replacements, and a common-words list. Available **without Full Access** ("Access to a common words lexicon…", platform-apis.md §3.4). Apple positions it "as supplementary to an autocorrection/suggestion lexicon of your own design" (platform-apis.md §3.7) | Asynchronous; small; load once per appearance | **Yes:** names go into a session "don't correct" set; text replacements go into shortcuts (`text-expansion-shortcuts`). Never persisted or sent anywhere |
| **iOS `UITextChecker`** | `rangeOfMisspelledWord`, `guesses`, `completions`, `learnWord`. Not marked extension-unavailable, but `@MainActor` *(verified: Apple docs JSON)* | No frequencies, context or touch model. On iOS, guesses come back "closer to being in alphabetical order" rather than by probability (ansonl/ios-uitextchecker-autocorrect) *(reported)*. `learnWord` is device-global (NSHipster). Memory and latency inside a 30 MB extension are unmeasured | **Optional guard only:** "don't autocorrect a word the system says is valid" for languages without a MaKeeb pack, once its cost has been measured on a device. Never call `learnWord` |
| **Android spell-checker session** (`TextServicesManager` → `SpellCheckerSession`) | Suggestions from the spell checker the user selected (typically Gboard's, Samsung's or AOSP's) | Asynchronous IPC to another app. May be disabled or absent. No scores or context. Hands typed words to a third-party process (still on the device, but outside MaKeeb's privacy guarantees) | **Not on the typing path.** At most an opt-in validity guard for unsupported languages. The reverse direction, MaKeeb *as* the system spell checker, is the `system-spell-checker` card and reuses this engine |
| **Android `UserDictionary.Words`** | The system personal dictionary (readable only by IMEs and spell checkers since API 23; platform-apis.md §2.9) | ContentProvider query | Import once into MaKeeb's user store with the user's consent. Keep MaKeeb's store as the source of truth |

iOS offers no next-word API to extensions (board note on `next-word-prediction`), so MaKeeb has to ship its own model on both platforms anyway.

---

## 8. User learning

- **What to record:**
  - Committed words (not words that were autocorrected away), with a count and a last-used timestamp.
  - Word pairs (and later triples) from committed text.
  - Per-key touch statistics for adaptation (§6.4).
- **When an unknown word becomes a suggestion:**
  - LatinIME gives entries below level 2 no probability at all (`MIN_VISIBLE_LEVEL = 2` in the probability table), so a new word has to recur before it is suggested *(verified)*.
  - Make it immediate when the user taps "add to dictionary" or reverts an autocorrection of that word.
- **Never learn:**
  - In incognito or password fields (already gated in `InputEngine.learn`).
  - From URLs, e-mail addresses, words containing digits, or text committed from panels.
- **Decay.** LatinIME's forgetting curve *(verified: `forgetting_curve_utils.cpp`)*:
  - Each use raises an entry's level (0–15). Words already in the main dictionary start at the visible level 2; unknown words start at 0.
  - The score decays with elapsed time in steps of 15 days / 32 (about 11 hours), through a probability table indexed by level and step.
  - A level drops for every 15 days without use.
  - Level-0 entries are discarded after 30 steps (about two weeks).
  - A decay pass runs at most every 2 hours (`DECAY_INTERVAL_SECONDS`), or sooner when entry counts pass 1.2× their caps.
  - Something equivalent (counts × exp(−age/τ) with a floor) keeps the store fresh and small.
- **Caps.** LatinIME's defaults are 10,000 unigrams and 30,000 each of bigrams, trigrams and 4-grams (`DEFAULT_MAX_NGRAM_COUNTS`) *(verified)*. Use similar caps, evicting the lowest decayed score first.
- **Forget and block.**
  - HeliBoard's `removeWord` deletes a word from every dictionary. If the word is in a read-only dictionary it is blacklisted instead, and typing it manually again removes it from the blacklist *(verified: `DictionaryFacilitatorImpl.kt`)*.
  - MaKeeb should copy that behaviour: long-press a suggestion to forget or block it (`on-device-learning`).
- **Mixing.** Interpolate P(*w* | *h*) = (1 − μ) · P_main + μ · P_user, with μ growing with the user's count for that context (Witten–Bell style). Search user words (in heap) in the same beam as the mapped lexicon.
- **Storage:**
  - *Android:* app-private files in credential-encrypted storage. Learning pauses before the first unlock (direct boot). Exclude the store from cloud backup unless the user opts in (`settings-sync-backup`).
  - *iOS:* the extension's own container, which it can read and write without Full Access.
    - The App Group is read-only without Full Access, so the companion app cannot see learned words.
    - Put the learned-words and personal-dictionary editor inside the keyboard. Alternatively, mirror the store to the App Group only when Full Access and consent exist (platform-apis.md §1, "one-way data flow").
  - *Format:* compact arrays keyed by word hash, not `HashMap<String, …>`; tens of thousands of boxed entries cost several MB in the Kotlin/Native heap *(estimate)*. Write atomically (temp file + rename) on hide and periodically, off the main thread, with a version header.
- **Privacy.** Nothing leaves the device. Export happens only through an explicit user backup, and there is a "clear learned data" action. Personalisation is worth it: WER 5.7% → 4.6% (Fowler et al. 2015), and "one rarely needs more than 100K words … but they may need the *right* 100K words" (Ouyang et al. 2017).

---

## 9. Licences

### 9.1 The app licence decides what MaKeeb may reuse

MaKeeb has no LICENSE yet. The choice decides which code and data can be reused, and whether App Store distribution is clean.

| MaKeeb licence | Can include | App Store | Notes |
|---|---|---|---|
| **Apache-2.0** (recommended) | AOSP LatinIME code and data (same licence; keep `NOTICE`, mark changes), MIT/BSD code, CC BY / ODC-By / CC0 data | Clean | Same licence as AOSP, FlorisBoard and k3lp. Includes a patent grant |
| MIT | As Apache, but Apache-licensed parts keep their own `NOTICE` duties | Clean | Simplest text; no patent grant |
| MPL-2.0 | As above, plus file-level copyleft on MaKeeb's own files | Generally fine (Firefox for iOS is MPL) | Forks must share changes to MaKeeb files |
| GPL-3.0 | Could also reuse HeliBoard and Unexpected Keyboard code | **Risky** (below) | Works on the App Store only with an added permission, and only if MaKeeb owns every GPL line in the iOS build |

### 9.2 GPL and LGPL on the App Store

- The FSF's position (2010, GNU Go): "Apple imposes numerous legal restrictions on use and distribution of GNU Go through the iTunes Store Terms of Service, which is forbidden by section 6 of GPLv2." Apple removed the app rather than change its terms.
- A 2025 counter-argument (App Fair Project) says GPL apps are distributable today. The question is contested, and any single copyright holder of included GPL code can still demand removal.
- Practical rules:
  1. Keep third-party GPL code and GPL data (the German and Italian Hunspell dictionaries; HeliBoard's `el2`, `as`, `bn2`, `ur` lists; AGPL Signal emoji data in HeliBoard's emoji dictionaries) out of the iOS app bundle.
  2. If MaKeeb ever goes GPL, add a GPLv3 §7 additional permission for app-store distribution from day one. Contributions must come under that permission.
  3. Avoid LGPL libraries in the iOS runtime. Static linking into an extension makes the LGPL relinking obligation awkward.
  4. LGPL build tools (KenLM) are fine.
- Google Play has no GPL conflict, but the MaKeeb licence applies to both stores' builds.
- A GPL-licensed *optional* pack downloaded by the companion app from MaKeeb's own server is not distributed through the App Store. It is a separate data file with its own licence and source (the word list plus the build script). This could make GPL-only languages possible later, but get it reviewed first.

### 9.3 CC BY-SA and share-alike on derived dictionaries

- A pack built from CC BY-SA data (wordfreq, FrequencyWords, Wikipedia or Wiktionary counts, HeliBoard's `nn` and `az` lists) is "Adapted Material" and must itself be released under CC BY-SA. Section 4 extends this to database rights, so a pack containing a substantial part of a CC BY-SA database becomes CC BY-SA too.
- The app code that reads the pack is a separate work and is not affected.
- CC BY-SA 4.0 §2(a)(5)(C) forbids applying "Effective Technological Measures" that restrict recipients. App Store FairPlay encrypts the executable's code, not bundled data files (Dark Wire Labs; stinger.io) *(reported)*, and publishing the same pack openly removes any doubt.
- Mixing is one-way. Once a pack contains CC BY-SA data, the whole pack is CC BY-SA.
- **Recommendation:** keep bundled base packs free of share-alike data. Offer CC BY-SA-derived packs, if ever, as separate optional downloads with the licence shown.

### 9.4 Attribution duties (all apply to the recommended sources)

- **Apache-2.0 (AOSP word lists and any ported LatinIME code):**
  - Ship the AOSP `NOTICE` text.
  - Keep licence headers on ported files and state the changes.
- **CC BY (Leipzig), CC BY 3.0 (Google Books Ngram), ODC-By (FineWeb), CC BY 2.0 FR (Tatoeba):**
  - Name the source and author, link the licence, and say the data was modified.
  - Google additionally asks for "acknowledgement of Google Books Ngram Viewer as the source, and inclusion of a link".
- **Where the attribution goes:**
  - A metadata block inside every pack (§10.2).
  - A "Licences and data sources" screen in the companion app.
  - A `THIRD_PARTY_NOTICES` file in the repo.

### 9.5 Quick classification for a permissive MaKeeb

| Class | Sources |
|---|---|
| **Safe** (keep the notice) | AOSP LatinIME code and word lists (Apache-2.0); SCOWL/ESDB (MIT-like); 12dicts (public domain); SymSpell code (MIT); cdict (MIT); Tongrams (MIT); marisa-trie (BSD-2-Clause option); Lucene FST code (Apache-2.0); Common Voice text (CC0); Unicode CLDR (Unicode License v3) |
| **Safe with attribution** | Leipzig downloads (CC BY); Google Books Ngram (CC BY 3.0); FineWeb and FineWeb-2 (ODC-By, plus Common Crawl terms); Tatoeba (CC BY 2.0 FR); OSCAR and HPLT (CC0 packaging; content rights stay with authors, and derived counts are low risk); OpenTaal Dutch (BSD / CC BY 3.0); SymSpell's English list (Google Books attribution) |
| **File-level copyleft** (fine if used only as a filter; if shipped, keep the files' source available) | French Hunspell (MPL-2.0); Hungarian (MPL-2.0 or LGPL); Spanish and Portuguese (MPL option) |
| **Share-alike** (optional downloads only) | wordfreq data, FrequencyWords data, Wikipedia and Wiktionary (CC BY-SA); HeliBoard `nn` and `az` |
| **Copyleft: not in the iOS bundle** | HeliBoard code (GPL-3.0) and its processed lists (repo GPL-3.0; ambiguous); Unexpected Keyboard (GPL-3.0); German and Italian Hunspell (GPL); FUTO Swipe library (GPL); Signal emoji data (AGPL-3.0); KenLM (LGPL; build tool only) |
| **Do not use** | FUTO Keyboard code (Source First 1.1, not open source) and FUTO models (FUTO Model License); Norvig and `google-10000-english` lists (derived from LDC Web 1T, no grant); Leipzig web-portal data (CC BY-NC); raw OpenSubtitles and SUBTLEX (unclear or custom terms); Reddit-derived lists |

---

## 10. Recommendation

### 10.1 Data sources per language

| Language | Lexicon backbone | Frequencies and n-grams | Filters and flags | Pack licence |
|---|---|---|---|---|
| **en-US** (bundled) | AOSP `en_US` (160,715 words), plus common words it lacks that pass ESDB | Leipzig English 1M corpora: news 2023 and 2024, web 2018, Wikipedia 2016 (about 80M tokens *(estimate)*); optional FineWeb sample; Google Books 2000–2019 for rare-word unigram smoothing. v1 next-word data: AOSP's three successors per word | ESDB validity; AOSP offensive flags and shortcuts; proper-noun case from corpus case ratios | Apache-2.0 components + CC BY (+ ODC-By, CC BY 3.0) → attribution-only |
| en-GB | AOSP `en_GB` | as en-US | ESDB British variants | as above |
| de, fr, es, it, pt-BR, pt-PT | AOSP lists (de 205,914 words) | Leipzig news 2023 1M per language, plus web and Wikipedia corpora; FineWeb-2 if needed | Hunspell only as a filter (fr MPL; es, pt MPL option); AOSP flags | attribution-only |
| nl, pl, ru, sv, … | AOSP lists | Leipzig | Hunspell filter where allowed (nl BSD, ru BSD, pl Apache option) | attribution-only |
| hu, fi, tr (agglutinative) | AOSP lists are small (hu 66,005) | Leipzig (hun news 2023 1M exists); FineWeb-2 | A larger vocabulary cap; a DAFSA lexicon; morphology later | attribution-only; budget up to 16 MB |

### 10.2 Pack format: "MKD" v1 (sketch)

```
header    magic, format version, section table (id, offset, length), pack id,
          language tag, pack version, build date, CRC32
meta      UTF-8 JSON: display name, sources [{name, url, licence, attribution, modified}],
          tokeniser settings, fold-table version, vocabulary and n-gram counts
fold      code point → folded code point (case and diacritics), generated at build time
          from Unicode data; per-language casing rules (Turkish i)
lexicon   radix trie over folded keys, breadth-first (hot top levels first).
          node: flags, label code points (1 byte ≤ U+00FF, else 3 bytes), relative
          child-array offset (1–3 bytes), best-descendant score (1 byte).
          terminal: unigram score (1 byte), word id (3 bytes)
words     word id → surface form (canonical case and diacritics) + flags
          (offensive, proper noun, not-a-word, has-shortcut). Ids ordered by
          descending frequency, so id 0..k is the "top words" list
ngrams    per context id: offset → successor block [count, (delta-varint next id,
          1-byte score)…, backoff byte]; <s> context for sentence starts;
          trigram contexts (w1, w2) in a sorted table with the same block layout
shortcuts optional: AOSP shortcut and whitelist entries ("dont" → "don't", "im" → "I'm")
```

Order word IDs by descending frequency. This keeps the delta-coded successor lists small and gives a free top-N unigram list for the empty-context case.

### 10.3 Build pipeline

- **Where:** a JVM-only CLI module, for example `makeeb/tools/dictionary-builder`, run from Gradle.
  - It is not part of the runtime graph.
  - It reuses the pack writer and reader from `:engine:dictionary` (`commonMain`), so the builder and the keyboard share one codec. Round-trip tests run in `jvmTest`.
- **Inputs:** a manifest per language listing each source with its pinned URL, SHA-256, licence and attribution text. Raw corpora are cached outside git.
- **Steps:**
  1. **Fetch.** Download and verify the SHA-256 of each source.
  2. **Normalise.** NFC, fold apostrophes, and tokenise with a simple Unicode word tokeniser that keeps internal apostrophes and hyphens and adds sentence markers.
  3. **Count.** Unigrams, bigrams and trigrams, using an external sort for large inputs.
  4. **Select the vocabulary.** Start from the AOSP list. Add frequent corpus words that pass the validity filter. Drop anything below the count thresholds. Decide canonical case.
  5. **Flags.** Offensive (AOSP lists plus a reviewed per-language list), proper noun, shortcuts.
  6. **Estimate.** Unigram scores (log-frequency, quantised to 1 byte) and the n-gram model (stupid backoff in v1).
  7. **Prune** to the per-language budget (§10.5).
  8. **Write** the MKD file and its metadata.
  9. **Verify:**
     - Golden queries: "teh" → "the", "dont" → "don't", "naive" → "naïve", "cat" must not be corrected.
     - Size budget checks.
     - Licence metadata present.
     - CRC32.
  10. **Publish.** Emit an `index.json` for downloads: language, version, size, SHA-256, licence summary.
- **Bundled English:** decide whether the built en-US pack is committed (Git LFS), downloaded at build time as a pinned release asset, or built in CI (open decision 6).

### 10.4 Algorithms, combined

1. **Per keystroke:**
   - Extend the weighted beam search (§6.2–6.5) by one input position, using tap points or key centres.
   - Cost per hypothesis = spatial + edit costs + λ · LM (context-aware) + completion cost. User words go into the same beam.
   - Produce the top candidates and the literal.
2. **Strip:** the middle slot holds the best candidate, and the typed literal stays available (existing `stripSlots`). Completions come from best-first search over best-descendant scores.
3. **On a separator:** autocorrect if the best candidate beats the literal by θ(aggressiveness) and all the don't-correct rules (§6.7) pass. The revert logic stays as it is.
4. **After a space:** next-word prediction from trigram → bigram → unigram backoff, mixed with user n-grams. Filter offensive words, and capitalise at sentence starts.
5. **Learning:** on commit, update user counts and decay (§8), unless the field is incognito.

### 10.5 Budgets (proposed)

| Item | Budget |
|---|---|
| Lexicon section (150–250k words) | 1.6–3 MB on disk |
| n-gram section | ≤ 6 MB |
| Pack total | ≤ 10 MB (≤ 16 MB for compounding and agglutinative languages) |
| Bundled in the iOS extension and the Android APK | en-US only (~6–8 MB); other languages downloaded |
| Heap per active language (caches, scratch, decoded hot nodes) | ≤ 1.5 MB |
| Heap for the user model | ≤ 2 MB |
| Heap, worst case with two active languages | ≤ 5 MB of the 30 MB iOS extension budget |
| Synchronous autocorrect path on a separator | ≤ 4 ms p95 on a mid-range device *(proposal; measure)* |

Check where the bundled pack lives on iOS so it is not duplicated between the app and extension bundles. Whether the container app can map a file from inside the `.appex` bundle needs a quick test.

### 10.6 Staged plan mapped onto the board

| Stage | Card(s) | Deliverable | Done when |
|---|---|---|---|
| 0 | `autocorrect` (hotfix) | Autocorrect only from a small typo whitelist until a real lexicon exists | `cat ` stays `cat `; `teh ` still becomes `the ` |
| 1 | **`mmap-dictionaries`** | `ByteRegion` port (Android `MappedByteBuffer` from uncompressed assets, iOS `mmap`, JVM `ByteArray`); MKD v1 lexicon + word table + flags; builder v0 turning AOSP en_US into MKD; `DictionarySuggestionEngine` running on it; the simulated-typing harness (§6.8) | Round-trip tests pass; iOS `phys_footprint` grows by < 2 MB with the pack loaded; suggestion cost independent of vocabulary size |
| 2 | **`word-completion`** | Best-first top-k; diacritic and case folding; apostrophe handling; completion-versus-typed ranking | Harness keystroke savings measured; "naive" → "naïve" |
| 3 | **`autocorrect`** + `proximity-correction` | Weighted beam search with LatinIME-style costs; key-centre proximity first, then real tap points from `TouchController`; margin-based decision and the rules in §6.7; aggressiveness setting and per-language switch | False-correction rate and typo-fix rate on the harness beat the stage-1 baseline; no regressions in the existing autocorrect and revert tests |
| 4 | **`dictionary-packs`** + **`language-switching`** | Pack metadata and download index; the companion app installs packs (Android app storage; iOS App Group, readable without Full Access); de, fr, es, it and pt packs built with Leipzig; licences screen; lazy mapping of the active language plus a recently used one; multilingual validity | A pack installs, verifies and maps on both platforms; switching languages does not grow the heap |
| 5 | **`next-word-prediction`** | v1: AOSP's three successors per word (Apache, available now). v2: a pruned trigram model with stupid backoff from Leipzig counts; mixing with user n-grams | Top-3 next-word accuracy measured on Tatoeba or Common Voice sentences |
| 6 | **`on-device-learning`** (+ `personal-dictionary`) | Persistent user store with decay, caps, blocklist and forget-from-suggestion; incognito respected; editor placed per platform (§8) | Learned words survive a restart; blocked words never reappear; nothing is learned in incognito fields |
| Later | `glide-typing`, `system-spell-checker`, `contact-name-suggestions`, key adaptation, a DAFSA or FST lexicon for large vocabularies, a small neural LM option (Android-first, only if it fits the budget) | – | – |

### 10.7 Open decisions for the user

1. **App licence.** Apache-2.0 (recommended), MIT, MPL-2.0 or GPL-3.0 with an app-store exception (§9.1). This gates everything that follows.
2. **Share-alike data.** Should bundled packs be attribution-only (recommended)? Are CC BY-SA packs acceptable as optional downloads?
3. **GPL-only languages.** Should GPL-licensed optional packs be offered from MaKeeb's own server (not via the App Store bundle), after legal review?
4. **Pack hosting.** GitHub Releases or MaKeeb's own server or CDN. A download reveals the user's language and IP address; state that in the privacy policy.
5. **Launch languages and variants.** en-US only, or also en-GB and de, fr, es, it, pt? Hungarian needs the larger budget.
6. **Bundled data in the repo.** Commit the built en-US pack (Git LFS), fetch a pinned release asset at build time, or build it in CI?
7. **Offensive-word default.** Block by default, as AOSP does (`config_block_potentially_offensive` is `true`) (recommended)? And who reviews the per-language lists?
8. **Builder language.** A Kotlin JVM CLI that shares the codec (recommended), or Python scripts like HeliBoard's?
9. **Contact names.** Opt-in on Android (`READ_CONTACTS`, a privacy trade-off) versus UILexicon only on iOS.
10. **Neural LM.** Keep ruled out for now. Revisit only as an Android-first experiment with a hard size and latency cap.

---

## 11. Sources

Licence files, READMEs and repository metadata were read directly (GitHub REST API via `gh`, the Codeberg API, raw file URLs) on 2026-09-28. Measurements used the files named in §3.3 and §5.1.

**Word lists and dictionaries**
- AOSP LatinIME (LineageOS mirror of AOSP): https://github.com/LineageOS/android_packages_inputmethods_LatinIME (`NOTICE`, `Android.bp`, `dictionaries/`)
- AOSP LatinIME upstream: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
- OpenBoard `dictionaries/`: https://github.com/openboard-team/openboard/tree/master/dictionaries
- HeliBoard dictionaries (README, per-list sources, LICENSE): https://codeberg.org/Helium314/aosp-dictionaries
- HeliBoard: https://github.com/HeliBorg/HeliBoard (`DictionaryFacilitatorImpl.kt`, `app/src/main/assets/dicts`)
- FlorisBoard: https://github.com/florisboard/florisboard; NLP repo: https://github.com/florisboard/nlp
- AnySoftKeyboard (English pack): https://github.com/AnySoftKeyboard/AnySoftKeyboard/tree/main/addons/languages/english
- Unexpected Keyboard dictionaries: https://github.com/Julow/Unexpected-Keyboard-dictionaries; cdict: https://github.com/julow/cdict (`libcdict/libcdict_format.h`)
- Keyman lexical models: https://github.com/keymanapp/lexical-models (`release/nrc/nrc.en.mtnt`)
- LibreOffice Hunspell dictionaries: https://github.com/LibreOffice/dictionaries (per-language README and LICENSE files)
- ESDB / SCOWL: https://github.com/en-wl/wordlist; http://wordlist.aspell.net/scowl-readme/
- SymSpell (and its frequency dictionaries): https://github.com/wolfgarbe/SymSpell
- FrequencyWords: https://github.com/hermitdave/FrequencyWords
- wordfreq (licence, SUNSET): https://github.com/rspeer/wordfreq
- Leipzig Corpora Collection, terms of usage: https://wortschatz.uni-leipzig.de/en/usage (read via https://web.archive.org/web/2025/https://wortschatz.uni-leipzig.de/en/usage); downloads: https://downloads.wortschatz-leipzig.de/corpora/
- Google Books Ngram datasets v3: https://storage.googleapis.com/books/ngrams/books/datasetsv3.html
- Norvig, Natural Language Corpus Data: https://norvig.com/ngrams/
- FineWeb-2: https://huggingface.co/datasets/HuggingFaceFW/fineweb-2; FineWeb: https://huggingface.co/datasets/HuggingFaceFW/fineweb
- OSCAR 23.01: https://huggingface.co/datasets/oscar-corpus/OSCAR-2301; HPLT 2.0: https://huggingface.co/datasets/HPLT/HPLT2.0_cleaned
- Tatoeba downloads: https://tatoeba.org/en/downloads
- Common Voice text corpus: https://common-voice.github.io/community-playbook/sub_pages/text.html
- FUTO Keyboard model notes (third party): https://github.com/itzune/futo-basque/blob/main/RESEARCH.md

**LatinIME internals** (all under https://github.com/LineageOS/android_packages_inputmethods_LatinIME)
- Binary format: `java/src/com/android/inputmethod/latin/makedict/FormatSpec.java`
- Autocorrect thresholds: `java/res/values/config-auto-correction-thresholds.xml`; `java/src/com/android/inputmethod/latin/utils/AutoCorrectionUtils.java`; `native/jni/src/utils/autocorrection_threshold_utils.cpp`
- Don't-correct rules: `java/src/com/android/inputmethod/latin/Suggest.java`
- Typing costs and beam size: `native/jni/src/suggest/policyimpl/typing/scoring_params.cpp`, `typing_weighting.h`
- Touch correction: `java/src/com/android/inputmethod/keyboard/ProximityInfo.java`, `keyboard/internal/TouchPositionCorrection.java`
- User history and decay: `java/src/com/android/inputmethod/latin/personalization/UserHistoryDictionary.java`; `native/jni/src/dictionary/utils/forgetting_curve_utils.cpp`; `native/jni/src/dictionary/header/header_policy.cpp`; `native/jni/src/defines.h`
- Mapping uncompressed dictionaries from the APK: `java/Android.bp`, `build.gradle`, `java/src/com/android/inputmethod/latin/BinaryDictionaryGetter.java`

**Papers**
- Kernighan, Church & Gale (1990), A spelling correction program based on a noisy channel model: https://aclanthology.org/C90-2036/
- Brill & Moore (2000), An improved error model for noisy channel spelling correction: https://aclanthology.org/P00-1037/
- Norvig, How to Write a Spelling Corrector: https://norvig.com/spell-correct.html
- Goodman, Venolia, Steury & Parker (2002), Language modeling for soft keyboards: https://www.microsoft.com/en-us/research/publication/language-modeling-for-soft-keyboards/
- Ouyang, Rybach, Beaufays & Riley (2017), Mobile Keyboard Input Decoding with Finite-State Transducers: https://arxiv.org/abs/1704.03987
- Fowler et al. (2015), Effects of Language Modeling and its Personalization on Touchscreen Typing Performance: https://research.google/pubs/effects-of-language-modeling-and-its-personalization-on-touchscreen-typing-performance/
- Bi, Azenkot, Partridge & Zhai (2013), Octopus: remulation: https://research.google/pubs/octopus-evaluating-touchscreen-keyboard-correction-and-recognition-algorithms-via-remulation/
- Azenkot & Zhai (2012), Touch behavior with different postures on soft smartphone keyboards: https://research.google/pubs/pub40589/
- Gunawardana, Paek & Meek (2010), Usability guided key-target resizing for soft keyboards: https://www.microsoft.com/en-us/research/publication/usability-guided-key-target-resizing-for-soft-keyboards/
- Kristensson & Zhai (2004), SHARK²: https://doi.org/10.1145/1029632.1029640
- Brants et al. (2007), Large Language Models in Machine Translation (stupid backoff): https://aclanthology.org/D07-1090/
- Chen & Goodman (1996), An empirical study of smoothing techniques for language modeling: https://aclanthology.org/P96-1041/
- Stolcke (1998), Entropy-based pruning of backoff language models: https://arxiv.org/abs/cs/0006025
- Chelba, Brants, Neveitt & Xu (2010), Study on interaction between entropy pruning and Kneser-Ney smoothing: https://research.google/pubs/pub36472/
- Heafield (2011), KenLM: https://aclanthology.org/W11-2123/; code: https://github.com/kpu/kenlm
- Pauls & Klein (2011), Faster and Smaller N-Gram Language Models: https://aclanthology.org/P11-1027/
- Pibiri & Venturini (2019), Handling Massive N-Gram Datasets Efficiently: https://arxiv.org/abs/1806.09447; Tongrams: https://github.com/jermp/tongrams
- Daciuk, Mihov, Watson & Watson (2000), Incremental construction of minimal acyclic finite-state automata: https://aclanthology.org/J00-1002/
- Jacobson (1989), Space-efficient static trees and graphs (LOUDS): https://doi.org/10.1109/SFCS.1989.63533
- marisa-trie: https://github.com/s-yata/marisa-trie
- Lucene FSTs (McCandless): https://blog.mikemccandless.com/2010/12/using-finite-state-transducers-in.html
- Burkhard & Keller (1973), Some approaches to best-match file searching (BK-tree): https://doi.org/10.1145/362003.362025
- Schulz & Mihov (2002), Fast string correction with Levenshtein automata: https://doi.org/10.1007/s10032-002-0082-8

**Platforms and memory**
- WWDC 2018 session 416, iOS Memory Deep Dive: https://developer.apple.com/videos/play/wwdc2018/416/
- Apple, UITextChecker: https://developer.apple.com/documentation/uikit/uitextchecker
- NSHipster, UITextChecker: https://nshipster.com/uitextchecker/
- ansonl/ios-uitextchecker-autocorrect (ordering of guesses on iOS): https://github.com/ansonl/ios-uitextchecker-autocorrect
- Android TextServicesManager: https://developer.android.com/reference/android/view/textservice/TextServicesManager
- kotlinx-io memory-mapped files request: https://github.com/Kotlin/kotlinx-io/issues/397
- FairPlay scope (executable code only): https://www.darkwirelabs.com/articles/dumping-repacking-decrypted-ios-binaries/; https://stinger.io/ios-re/Binary-Analysis/decrypt-ios-executable/

**Licences**
- CC BY-SA 4.0 legal code: https://creativecommons.org/licenses/by-sa/4.0/legalcode.en
- ODC-By 1.0: https://opendatacommons.org/licenses/by/1-0/
- FSF, GPL Enforcement in Apple's App Store (2010): https://www.fsf.org/news/2010-05-app-store-compliance
- FSF, More about the App Store GPL Enforcement: https://www.fsf.org/blogs/licensing/more-about-the-app-store-gpl-enforcement
- App Fair Project, The GPL and Commercial App Stores (2025): https://appfair.org/blog/gpl-and-the-app-stores/
