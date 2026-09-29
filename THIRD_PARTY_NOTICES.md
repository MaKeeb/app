# Third-party notices

MaKeeb ships or builds from the following third-party material. The list covers data and code that end up in the apps, not build tools.

## AOSP LatinIME English word list

- **Used in:** the bundled English dictionary pack `en_US.mkd` (Android APK asset and iOS keyboard extension resource). It is generated at build time by `makeeb/tools/dictionaries` and not stored in this repository.
- **Source:** `dictionaries/en_US_wordlist.combined.gz` from the Android Open Source Project's LatinIME, commit `8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6`: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/8dd31a28ae774c0f5cd43404ad4b78bf46e5aeb6/dictionaries/en_US_wordlist.combined.gz
- **Licence:** Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
- **Notice** (from LatinIME's `NOTICE`): Copyright (c) 2008, The Android Open Source Project. Licensed under the Apache License, Version 2.0; you may not use this file except in compliance with the License. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
- **Changes:**
  - Converted to MaKeeb's MKD format.
  - Spellings normalised to NFC, with typographic apostrophes folded to ASCII.
  - `not_a_word` entries and shortcut lines left out.
  - Frequencies and the `possibly_offensive` flags kept as published.

## AOSP LatinIME keyboard layouts and long-press keys

- **Used in:** the bundled layout and language data, `makeeb/engine/layout/data/` (embedded in both apps through the generated `BundledLayoutData.kt`). `makeeb/scripts/import-aosp-layouts.py` converts it; the format is in `docs/layouts/schema.md`.
- **Source:** the Android Open Source Project's LatinIME, commit `8b211dd233e99f59c9853a9c44c627dfb431ebd8` (AOSP main, 2025-08-08): https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/8b211dd233e99f59c9853a9c44c627dfb431ebd8
  - Letter rows: `java/res/xml/rows_{qwerty,qwertz,azerty,dvorak,colemak}.xml` and the `rowkeys_*`, `keys_dvorak_123`, `row_qwerty4`, `key_comma` and `key_period` files they include.
  - Long-press keys: `tools/make-keyboard-text/res/values-{en,de,fr,es,it,pt,hu,pl,nl,sv}/donottranslate-more-keys.xml` and the default `values/donottranslate-more-keys.xml` (the sources of `KeyboardTextsTable`).
  - Each language's layouts: `java/res/values/donottranslate.xml` and `java/res/xml/method.xml`.
- **Licence:** Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
- **Notice** (from LatinIME's `NOTICE`): Copyright (c) 2008, The Android Open Source Project. Licensed under the Apache License, Version 2.0; you may not use this file except in compliance with the License. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
- **Changes:**
  - Converted from Android resource XML to MaKeeb's JSON schema. Only characters are kept; MaKeeb's engine builds shift, backspace, the bottom row and the symbols pages.
  - `%` markers, column-order flags and the digits AOSP adds to top-row keys are dropped (MaKeeb adds its own digit hints).
  - Dvorak's `q` and `z` move from AOSP's bottom row to the third letter row. Colemak's `;` is left out (it is on MaKeeb's symbols page). AZERTY's apostrophe keeps MaKeeb's own long-press keys.
  - Layouts MaKeeb doesn't bundle (`spanish`, `nordic`, `swiss`) are replaced by the closest bundled one in each language's layout list.
  - German gains an explicit shifted form (`ß` → `ẞ`).

## Unicode CLDR exemplar characters (repository only)

- **Used in:** the test-only table `makeeb/engine/layout/src/commonTest/kotlin/com/makeeb/engine/layout/CldrExemplars.kt`, which checks that every letter a bundled language needs can be typed. It does not ship in the apps.
- **Source:** `cldr-json` 48.2.2, `cldr-misc-full/main/<language>/characters.json` (`exemplarCharacters`): https://github.com/unicode-org/cldr-json/tree/48.2.2
- **Licence:** Unicode License v3 (SPDX: Unicode-3.0), https://www.unicode.org/license.txt. Copyright © 1991-2025 Unicode, Inc.

## Leipzig Corpora Collection (English news and web text)

- **Used in:** the next-word statistics (the `NGRM` section) of the bundled English pack `en_US.mkd`. The pack holds only pruned word-pair and word-triple frequencies, not the text. It is generated at build time by `makeeb/tools/dictionaries`; neither the corpora nor the pack are stored in this repository.
- **Source:** Leipzig Corpora Collection, Wortschatz Leipzig, Leipzig University (https://wortschatz.uni-leipzig.de/en/download):
  - `eng_news_2024_1M` (English news, 2024, 1M sentences): https://downloads.wortschatz-leipzig.de/corpora/eng_news_2024_1M.tar.gz, SHA-256 `8f1d4d07b9771f8a7fc219ad587d5382eabf5009ef563f1bc4c12a467a8e3a97`.
  - `eng-com_web-public_2018_1M` (English .com web pages, 2018, 1M sentences): https://downloads.wortschatz-leipzig.de/corpora/eng-com_web-public_2018_1M.tar.gz, SHA-256 `de8849fe30c7d5bf3502f620093232f4dceca10a6ac19a9ad49f474b62df0a5f`.
- **Licence:** Creative Commons Attribution 4.0 International (CC BY 4.0), https://creativecommons.org/licenses/by/4.0/. The Wortschatz terms of usage (https://wortschatz.uni-leipzig.de/en/usage) state that "the text corpora offered for download are made available under the Creative Commons licence CC BY". Data used through the Wortschatz web portal is CC BY-NC and is not used.
- **Attribution:** D. Goldhahn, T. Eckart and U. Quasthoff (2012): Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages. In: Proceedings of the 8th International Language Resources and Evaluation Conference (LREC 2012).
- **Changes:**
  - Only the sentence files are read. They are normalised to NFC, split into words, and counted over the AOSP word list. Words outside it are not kept.
  - Word, bigram and trigram counts are pruned to the most frequent successors, offensive words are left out, and the rest is quantised to one byte per entry.
  - One sentence in 250 is held out of the counts to evaluate the predictions.
- **Pack licence:** `en_US.mkd` combines both sources. Its `META` records `licence=Apache-2.0 AND CC-BY-4.0`, with each source's attribution.
