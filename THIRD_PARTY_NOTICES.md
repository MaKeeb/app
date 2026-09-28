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
