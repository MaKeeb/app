# Open-source on-screen keyboards: 2025–2026 survey and MaKeeb feature catalogue

- **Prepared for:** MaKeeb (Kotlin Multiplatform + Compose Multiplatform keyboard for Android and iOS)
- **Date:** 2026-09-27
- **Machine-readable catalogue:** [`feature-candidates.json`](feature-candidates.json)
- **Scope:** Covers the core keyboard: typing, layouts and languages, classic statistical prediction/autocorrect, swipe decoding, editing, emoji, clipboard, theming, one-handed/floating, accessibility, privacy, settings/backup and hardware keyboards. Generative-AI features were **excluded at the product owner's request**: LLM rewriting, AI compose or reply, tone changers, chat assistants, and AI image or sticker generation. Platform API details are covered by a separate research track, so platform notes here are brief. Notes marked *(verify)* need confirmation from that track.

**How to read the data**

- **Stars** are rounded GitHub counts read from the GitHub API on 2026-09-27.
- **Release dates** come from GitHub releases/tags or project sites.
- **Feature claims** come from READMEs, release notes, docs and issue trackers (see [Sources](#sources)).
- For commercial keyboards, feature claims are general product knowledge that was not re-verified item by item. They are only used to set expectations.
- A keyboard missing from a feature's "seen in" list does **not** prove it lacks the feature. The lists record what was verified during this survey.
- Anything that could not be verified is marked *(unverified)*.

---

## 1. Executive summary

- **The open-source Android field is healthy but split into two lineages.**
  - **AOSP LatinIME descendants.** OpenBoard (dormant since 2022) → HeliBoard (very active, 4.1 in Aug 2026) → LeanType (a 2026 fork). FUTO Keyboard is a separate LatinIME fork under a source-available licence. These keyboards inherit a mature C++ dictionary engine: binary `.dict` files, proximity-aware correction and bigrams.
  - **Fresh Kotlin/Compose codebases.** FlorisBoard, Thumb-Key and the newcomers Urik and CleverKeys. They have more modern architecture but, historically, weak prediction.
- **FlorisBoard is the closest architectural analogue to MaKeeb, but it still has no word suggestions.** It is Kotlin + Compose, Apache-2.0, and has an extension system and a stylesheet theme engine (Snygg). Predictive text is the goal of its 0.6 milestone. Its authors are also building **k3lp**, a *Kotlin Multiplatform*, Apache-2.0 parser for **Unicode CLDR Keyboard 3.0** layouts. MaKeeb could reuse it directly as its layout-format foundation.
- **Swipe typing is the biggest historical gap in open source, and 2026 is when it is being closed:**
  - **FUTO Swipe** (June 2026) publishes small swipe-decoding models, a GPL C++ inference library and an MIT dataset of about 1M swipes. FUTO reports lower error than Gboard and iOS on its own test set (vendor benchmark).
  - **HeliBoard's NLnet-funded gesture library** is planned as Apache-2.0 and is explicitly meant for FlorisBoard and non-Android keyboards too. It is not released yet.
  - **Urik** (geometric matching) and **CleverKeys** (an on-device model) ship their own open decoders.
- **On iOS, open source is thin and shrinking:**
  - **KeyboardKit** switched from MIT to a **closed-source binary SDK** with v10 (2025-09-29). MIT code exists only up to 9.x.
  - **Hamster** (Rime for iOS) froze its open-source repo at 2.1.0 and went commercial.
  - What remains is language-specific or niche: Keyman, azooKey (Japanese), fcitx5-ios (developer beta, sideload), giellakbd-ios (minority languages) and Jyutping (Cantonese).
  - A cross-platform open-source keyboard with real offline prediction and swipe on iOS would therefore be nearly unique.
- **What users ask for most in open-source trackers:**
  1. Good swipe typing
  2. Working suggestions and autocorrect
  3. Emoji search
  4. Floating keyboard
  5. CJK input
  6. Ctrl/Alt/Esc keys
  7. Password-manager integration
  8. GIFs
  9. Backup/restore

  Reviews repeatedly penalize setup friction, such as side-loading dictionaries or a gesture library.
- **Neural models, in one sentence:** some keyboards now use neural language models (for example FUTO Keyboard's optional transformer LM for prediction and autocorrect), but per scope they are not catalogued as feature candidates here. Swipe *decoding* stays in scope regardless of technique.
- **Reusable building blocks, with licences to check against MaKeeb's own licence:**

  | Building block | Licence | Use |
  |---|---|---|
  | k3lp | Apache-2.0, KMP | CLDR Keyboard 3.0 layouts |
  | FlorisBoard JSON layout format | Apache-2.0 | Layouts (HeliBoard reads the same format) |
  | AOSP LatinIME | Apache-2.0 | Dictionary engine and `.dict` format |
  | Helium314/aosp-dictionaries wordlists | Mixed per language (CC-BY, CC-BY-SA, GPL, Apache, …) | Dictionaries |
  | cdict | MIT, OCaml/C | Dictionary used by Unexpected Keyboard |
  | librime | BSD-3-Clause | Chinese input |
  | Keyman | MIT | Keyboards and lexical models |
  | FUTO Swipe library | GPL | Swipe decoding |
  | FUTO Swipe models | FUTO Model License | Swipe decoding |

  GPL code (HeliBoard, Unexpected Keyboard, Trime) is reusable only if MaKeeb is GPL-compatible. **FUTO Keyboard's own code is under the FUTO Source First License 1.1**, which is not an OSI licence, so treat it as reference only.
- **Distribution risk (Android).** Google's developer-verification requirement for sideloaded apps is reported to start enforcement on **2026-09-30** in Brazil, Indonesia, Singapore and Thailand, with a global rollout planned for 2027. This matters if MaKeeb plans F-Droid or APK distribution.

**Catalogue size:** 101 features across 16 categories.

| Priority | Count |
|---|---|
| P0 | 28 |
| P1 | 39 |
| P2 | 24 |
| P3 | 10 |

---

## 2. Comparison table

| Keyboard | Platform | Licence | Stack | Stars | Latest release (stable unless noted) | Maintenance | Prediction / autocorrect | Swipe typing |
|---|---|---|---|---|---|---|---|---|
| [FlorisBoard](https://github.com/florisboard/florisboard) | Android | Apache-2.0 | Kotlin, Jetpack Compose | ~8.7k | v0.5.2 (2025-11-28); v0.6.0-alpha02 (2026-01-23) | Active; still beta | **None yet** (0.6 goal) | Statistical classifier, alpha quality |
| [HeliBoard](https://github.com/HeliBorg/HeliBoard) | Android | GPL-3.0 | Kotlin/Java + AOSP C++ | ~6.2k | v4.1 (2026-08-30) | Very active; NLnet-funded | AOSP dictionary engine, bigrams, confidence slider | Needs user-supplied proprietary Google library; open library in development |
| [OpenBoard](https://github.com/openboard-team/openboard) | Android | GPL-3.0 | Java + C++ | ~2.7k | v1.4.5 (2022-08-05) | Dormant (last commit 2022-12) | AOSP engine | No |
| [AnySoftKeyboard](https://github.com/AnySoftKeyboard/AnySoftKeyboard) | Android | Apache-2.0 | Java | ~3.4k | 1.13-r1 (2026-02-08); 1.13-r2 pre (2026-09-08) | Active | Own engine; mixable word-list packs; next-word | Statistical ("improved accuracy" in 1.13) |
| [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard) | Android | GPL-3.0 | Kotlin | ~0.7k | 1.9.1 (2026-02-02) | Active | None (top issue) | No |
| [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) | Android | GPL-3.0 | Java | ~3.3k | 2.1.0 (2026-09-06) | Active | **New in 2.0 (May 2026):** spellcheck and autocorrect via `cdict` | No (corner swipes instead) |
| [Thumb-Key](https://github.com/dessalines/thumb-key) | Android | AGPL-3.0 | Kotlin, Compose | ~1.5k | 5.1.17 (2026-09-08) | Active | None by design (issue open) | No (per-key swipes) |
| [Hacker's Keyboard](https://github.com/klausw/hackerskeyboard) | Android | Apache-2.0 | Java | ~2.4k | 1.40.7 (2018-11-26) | Unmaintained (README warns) | Old AOSP (Gingerbread) | No |
| [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/) | Android | Apache-2.0 | Java + C++ | n/a | Ships with AOSP | Public `main` last touched 2025-02-26 (test cleanup) | Dictionary + proximity + bigram engine | Decoder not open-sourced |
| [FUTO Keyboard](https://github.com/futo-org/android-keyboard) | Android | FUTO Source First 1.1 (source-available) | Java/Kotlin + C++ (LatinIME fork) | ~3.3k (mirror) | 0.1.30 (2026-08-04) | Very active; paid (one-time) | AOSP engine (plus neural LM, out of scope) | **FUTO Swipe** (June 2026) |
| [Trime](https://github.com/osfans/trime) | Android | GPL-3.0 | Kotlin + librime (JNI) | ~4.7k | v3.3.12 (2026-09-01); rolling nightly (updated 2026-09-27) | Active | Rime engine (Chinese-centric) | No |
| [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) | Android | LGPL-2.1 | Kotlin + C++ | ~5.7k | 0.1.3 (2026-07-26) | Active | Fcitx5 engines; English spell check | No |
| [Keyman](https://github.com/keymanapp/keyman) | Android, iOS, desktop, web | MIT | TS/JS engine in WebView + Java/Swift + C++ core | ~0.5k | 18.0.252 (2026-09-22); 19.0 alpha | Active (SIL Global) | Word-list lexical models (.kmp) | No |
| [8VIM](https://github.com/8VIM/8VIM) | Android | Apache-2.0 | Kotlin | ~0.6k | v0.17.5 (2024-06-28); v0.18.0-rc.1 (2024-10-29) | Slow (last commit 2026-03) | Text replacement only | Circle gestures (8pen) |
| [Simple Keyboard](https://github.com/rkkr/simple-keyboard) | Android | Apache-2.0 | Java | ~1.6k | 148 (2026-09-12) | Active, minimal | None by design | No |
| [Urik](https://github.com/urikdev/Urik) | Android | GPL-3.0 | Kotlin | ~0.4k | v0.23.1-beta (2026-06-12) | New (created 2025-09) | On-device spellcheck and autocorrect | Geometric path matching |
| [CleverKeys](https://github.com/tribixbite/CleverKeys) | Android | GPL-3.0 | Kotlin + ONNX Runtime | ~0.5k | Rolling dev builds (2026-09-27) | New (created 2025-09) | Dictionaries + autocorrect | On-device swipe model, 19 languages (self-reported) |
| [LeanType](https://github.com/LeanBitLab/LeanType) | Android | GPL-3.0 | Kotlin (HeliBoard fork) | ~1.0k | beta-427-2 (2026-09-26) | New (created 2026-01) | HeliBoard engine | Proprietary lib (as HeliBoard) |
| [KeyboardKit](https://github.com/KeyboardKit/KeyboardKit) | iOS (+ other Apple OSes) | **Closed-source binary since 10.0** (MIT ≤ 9.x) | Swift, SwiftUI | ~1.9k | 10.9.5 (2026-09-20); 11.0 beta | Active, commercial | Pro: local autocomplete; next-word via remote services | Unverified |
| [Hamster (仓输入法)](https://github.com/imfuxiao/Hamster) | iOS | MIT (open repo frozen) | Swift + librime + KeyboardKit | ~1.6k | 2.1.0 (2023-12-04) | Open repo frozen; app now commercial | Rime | No |
| [azooKey](https://github.com/azooKey/azooKey) | iOS | MIT | Swift | ~0.8k | v3.1.1 (2026-09-20) | Active | Own kana-kanji converter (MIT SwiftPM package) | n/a |
| [fcitx5-ios](https://github.com/fcitx-contrib/fcitx5-ios) | iOS | GPL-3.0 | Swift + C++ | ~0.1k | Rolling "latest" (2026-09-26) | Developer beta (sideload) | Fcitx5 engines | No |
| [giellakbd-ios](https://github.com/divvun/giellakbd-ios) | iOS | Apache-2.0 | Swift | ~60 | No GitHub releases | Moderate (last push 2026-06) | Divvun spellers *(unverified detail)* | No |
| [Jyutping](https://github.com/yuetyam/jyutping) | iOS, macOS | CC0-1.0 | Swift | ~160 | 0.83.0 (2026-09-11) | Active | Cantonese conversion | No |

---

## 3. Per-keyboard notes

### 3.1 Android

#### FlorisBoard

- **Repo and status:** github.com/florisboard/florisboard. Apache-2.0, Kotlin + Jetpack Compose, ~8.7k stars. Stable v0.5.2 (2025-11-28); v0.6.0-alpha02 (2026-01-23); commits daily. Distributed via F-Droid, IzzyOnDroid and a closed Google Play beta. A public Play beta is planned with 0.7.
- **Headline features:**
  - Smartbar with configurable quick actions: undo/redo, select all, arrows, language switch, hide keyboard.
  - Clipboard history, reworked in 0.5.2, with filtering, timestamps and sensitive-flag handling.
  - Emoji panel, with emoji suggestions by name and keyword since 0.5.
  - Material You themes.
  - Inline autofill (Android 11+).
  - Time-based theme switching.
  - Reproducible builds with published signing-certificate hashes.
  - Floating window mode (0.6 alpha), which replaced the separate one-handed and height preferences with an in-keyboard resize and compact mode.
- **Prediction and swipe:**
  - No word suggestions or spell checking in releases. The README says this outright, and it is the 0.6 goal together with a "Language Pack" extension type.
  - Glide typing uses `StatisticalGlideTypingClassifier`, based on Étienne Desticourt's statistical approach from AnySoftKeyboard PR #1870. A reimplementation is planned for 0.7+ on the new layout engine.
- **Architecture:**
  - JSON layouts, a format HeliBoard also reads.
  - **Snygg v2**, an element- and state-level stylesheet theme engine with an in-app theme editor.
  - **Extensions** (`extension.json` packages) for themes, keyboard layouts, composers and language packs, plus a community **Addons Store** that currently accepts themes only.
  - Work in progress on **k3lp**, a Kotlin Multiplatform, Apache-2.0 parser for CLDR Keyboard 3.0 (UTS #35 Part 7), NLnet-funded and pre-alpha. `keyboard3` assets and code already exist in `main`.
- **Weaknesses:** Missing suggestions and autocorrect is the main complaint in reviews: MakeUseOf (Nov 2025) wrote that suggestions "didn't work at all". Other gaps:
  - Emoji search: issue #45 (80 reactions), roadmapped for 0.7+.
  - Ctrl/Alt/Esc keys: #229.
  - CJK input: #149, #140.
  - A custom layout editor: #196.
  - An OOM crash: #677.
  - Slow cadence: no stable release since 2025-11.
- **Relevance to MaKeeb:** Very high. It is the closest stack, uses a permissive licence and offers k3lp for direct reuse.

#### HeliBoard

- **Repo and status:** github.com/HeliBorg/HeliBoard (moved from Helium314/HeliBoard). GPL-3.0 (with an Apache-2.0 file for AOSP-derived code), Kotlin/Java + AOSP C++ native library, ~6.2k stars. v4.0 (2026-07-10), v4.1 (2026-08-30). Very active and funded by NLnet (NGI Mobifree). Distributed via F-Droid, IzzyOnDroid and GitHub. No internet permission.
- **Headline features:**
  - Multilingual typing.
  - Clipboard history, with images and files since 4.0.
  - Emoji search (needs an emoji dictionary).
  - Floating keyboard (4.0).
  - One-handed and split modes.
  - Separate scale settings for foldables.
  - Touchpad mode on spacebar slide (4.0).
  - Customizable toolbar with pinned keys.
  - Backup and restore of settings and learned data.
  - Custom fonts.
  - A number pad.
  - A setting to show only the toolbar when a hardware keyboard is attached.
  - The toolbar is hidden when the device is locked.
  - Configurable autocorrect confidence (slider in 4.0).
  - Inline password suggestions.
  - Unicode 17 emoji.
- **Prediction and swipe:**
  - Prediction uses the AOSP LatinIME native engine with binary `.dict` dictionaries, community-maintained at codeberg.org/Helium314/aosp-dictionaries (licences vary per wordlist).
  - Gesture typing needs a **closed-source Google library** (`libjni_latinimegoogle.so`) that users must extract from GApps. The README states "there is no compatible open source library available".
  - The **NLnet GestureTyping project** (issue #2226, the most-reacted open issue) will overhaul the native library with an open decoder as a drop-in replacement. Opt-in gesture data gathering exists in two modes: active, and background (4.0) with blocklists, a review screen and email submission. The decoder library is planned as Apache-2.0 so that FlorisBoard and "other (non-Android) keyboards" can use it.
- **Architecture:**
  - Two layout formats: a simple text format (one key per line) and FlorisBoard-compatible JSON with selectors for shift state, input variation (email, URL, password…), keyboard state and layout direction.
  - Community web layout maker.
- **Weaknesses:**
  - Setup friction: dictionaries and the gesture library are side-loaded. HowToGeek and MakeUseOf both flag this, and an AlternativeTo summary says "getting it to work takes time and tinkering".
  - The inherited AOSP UI.
  - Requests: GIFs (#363), Japanese and Asian languages (#639, #786), a Gboard-like default look (#1055), separating the toolbar from suggestions (#695), finer spacebar cursor control (#1057).

#### OpenBoard

- **Repo and status:** github.com/openboard-team/openboard (Dslul/openboard redirects here). GPL-3.0, Java + C++, ~2.7k stars. Last release v1.4.5 (2022-08-05); last commit 2022-12-17. **Dormant.**
- **Summary:** An AOSP LatinIME fork without Google dependencies. HeliBoard explicitly continues it ("continuing the project from where it stopped").

#### AnySoftKeyboard (ASK)

- **Repo and status:** Apache-2.0, Java, ~3.4k stars. 1.13-r1 (2026-02-08); 1.13-r2 pre-release (2026-09-08). Continuous Play alpha, beta and stable channels, plus F-Droid.
- **Headline features:**
  - Language, theme and **Quick-Text** add-on packs, shipped as separate APKs.
  - Special layouts for number, email and URI fields.
  - Physical-keyboard support.
  - Next-word suggestions.
  - Mixable word lists (for example a French layout with German and Russian suggestions).
  - Gesture typing.
  - Power-saving mode.
  - **Per-app tint.**
  - Incognito mode.
  - Voice input.
  - Contact-based suggestions (MakeUseOf).
- **Prediction and swipe:** Its own dictionary engine. The statistical gesture classifier (PR #1870) was later adopted by FlorisBoard.
- **Weaknesses:** MakeUseOf (Nov 2025) found gesture typing "neither as smooth as Gboard's", predictions and autocorrect weak, and the clipboard awkward. The tracker shows:
  - Chinese and Japanese (#1304).
  - A customizable bottom row (#1832).
  - A floating keyboard (#1952).
  - An open TalkBack bug, "Unable to press any key with TalkBack activated" (#2316).
  - A still-open request to remove a COVID-19 message from the suggestion bar (#2803).

#### Fossify Keyboard

- **Repo and status:** GPL-3.0, Kotlin, ~0.7k stars. 1.9.1 (2026-02-02); commits continue. The community continuation of Simple Mobile Tools' Simple Keyboard after that suite was sold *(background from general knowledge)*. No internet permission.
- **Features:** Clipboard with pinned clips, custom colors, custom fonts (1.9), multiple languages, and emoji.
- **Weaknesses:** No suggestions or autocorrect (#58, 52 reactions), emoji search (#23, 44), and swipe typing (#94, 22).

#### Unexpected Keyboard

- **Repo and status:** GPL-3.0, Java, ~3.3k stars. 2.1.0 (2026-09-06). Its README campaigns against Google's developer verification (keepandroidopen.org).
- **Headline features:**
  - Originally built for Termux and programmers: each key carries up to 8 extra symbols reached by swiping towards corners or edges.
  - Circle gestures (delete a word, lock shift) and a compose key.
  - Spacebar slider and selection mode.
  - Foldable support; split layout on tablets and landscape (2.1).
  - **Spell checking and autocorrect since 2.0.0 (2026-05-02)**, funded by NLnet. It uses Julow's `cdict` (MIT, OCaml/C) with per-language downloadable dictionaries.
- **Architecture:** Custom layouts in an XML format (`<keyboard><row><key c="a" .../>`), with a community web editor.
- **Weaknesses:** Emoji search (#173), backup/restore (#856), floating keyboard (#621, #307, #1031), key-press popups (#132), and text expansion (#657).

#### Thumb-Key

- **Repo and status:** AGPL-3.0, Kotlin + Jetpack Compose, ~1.5k stars. 5.1.17 (2026-09-08).
- **Features:**
  - A 3x3 grid with swipe letters (a successor to MessagEase) that deliberately avoids prediction in favor of muscle memory.
  - Dynamic/Material 3 themes.
  - Ghost keys, dead keys and compose combos.
  - Clipboard history and a **private clipboard**.
  - Key modifications written in YAML.
- **Weaknesses:** Emoji search (#626), local autocorrect (#44), and backspace to cancel autocorrect (#573).

#### Hacker's Keyboard

- **Repo and status:** Apache-2.0, Java, ~2.4k stars. Last stable release 1.40.7 (2018-11-26). The README warns it is "rather ancient", may stop working on modern devices, and that language switching and popups are broken on modern Android.
- **Why it still matters:** It remains the reference for a **full 5-row PC layout** (Esc, Tab, Ctrl, Alt, arrows) for SSH users, and that demand now shows up as requests in FlorisBoard (#229) and FUTO (#25).

#### AOSP LatinIME

- **Repo and status:** android.googlesource.com/platform/packages/inputmethods/LatinIME. Apache-2.0, Java + C++. The public `main` branch last saw a commit on 2025-02-26 (a Mockito cleanup), so it is effectively in maintenance.
- **What it provides:** The dictionary engine (`.dict` v2/v4, proximity-aware correction, bigram prediction, user history), accessibility support and the spell-checker service.
- **Limitation:** The gesture decoder was never open-sourced. That is the root of every AOSP fork's swipe problem.

#### FUTO Keyboard

- **Repo and status:** Primary source is gitlab.futo.org/keyboard/latinime, with a GitHub mirror at futo-org/android-keyboard (~3.3k stars). **FUTO Source First License 1.1**: source-available, contributions require a CLA, and it is not an OSI licence (issue #1296 asks for relicensing). Java/Kotlin + C++. 0.1.30 (2026-08-04); roughly monthly releases.
- **Business model:** One-time payment with an honor-system "I already paid" button, since there is no online licence check. Distributed via Play, F-Droid and GitHub.
- **Headline features:**
  - Fully offline.
  - Clipboard history with images and screenshots (0.1.28) and search (0.1.29).
  - Japanese input, and Chinese input via RIME (0.1.28).
  - Vietnamese Telex/VNI (VietIME).
  - IPA and Toki Pona layouts.
  - YAML custom layouts.
  - Unicode 17 emoji with a trimmed 1 MB compatibility font.
  - Simple in-app theming, plus an advanced web theme editor producing ZIP themes (0.1.30).
  - Offline voice input via the separate FUTO Voice Input (whisper.cpp).
- **Prediction and swipe:**
  - A dual engine: the AOSP-style dictionary and bigram engine, plus the optional neural LM mentioned in the executive summary. Dictionaries come from HeliBoard's Codeberg project.
  - **FUTO Swipe** (0.1.29, 2026-06-01) combines a layout-agnostic encoder, a per-language context model and a layout-specific decoder: about 2.5M parameters in total, English QWERTY only for now.
  - FUTO reports top-1 error of 7.38% versus Gboard 11.05%, iOS 10.82% and HeliBoard with the Google library 13.12%, on its own public test set run in an emulator (vendor benchmark).
  - The inference library is GPL, the models use the FUTO Model License, and the dataset is MIT.
- **Weaknesses:**
  - No spell-checker service (#1334).
  - Password-manager button (#325).
  - More languages for its neural models (#1212).
  - Ctrl/Alt keys (#25).
  - Streaming voice (#130).
  - Font size (#90).
  - Vertical cursor movement (#260).
  - "Add to dictionary" (#311).
  - Its FAQ admits lag on low-end devices when the neural LM is on, and that next-word suggestions are "limited" outside English.

#### Trime (同文输入法)

- **Repo and status:** GPL-3.0, Kotlin + librime via JNI, ~4.7k stars. v3.3.12 (2026-09-01) plus a rolling nightly build. F-Droid and Play.
- **Architecture and features:** A Rime frontend for Chinese and its dialects. Keyboards and themes are configured in YAML (`trime.yaml`). The "liquid keyboard" provides symbol, emoji and clipboard panels.
- **Weakness (assessment):** Power comes through configuration files, which suits technical users and is a barrier for everyone else.

#### fcitx5-android

- **Repo and status:** LGPL-2.1, Kotlin + C++, ~5.7k stars. 0.1.3 (2026-07-26).
- **Features:**
  - The Fcitx5 framework ported to Android, with **plugin APKs**: Anthy (Japanese), Hangul, Chewing (Zhuyin), Jyutping, UniKey (Vietnamese), Sayura (Sinhala), Thai and RIME.
  - Built-in Pinyin, Shuangpin, Wubi and Cangjie.
  - Expandable candidate view.
  - Plain-text clipboard.
  - Monet (dynamic) colors.
  - A floating candidate panel for physical keyboards.
- **Weakness:** The README says the virtual keyboard layout is "not customizable yet".
- **Sibling projects:** Fcitx5 also exists for iOS (see fcitx5-ios), macOS, HarmonyOS, ChromeOS and Windows.

#### Keyman (SIL Global)

- **Repo and status:** keymanapp/keyman monorepo. MIT, ~0.5k stars. Stable 18.0.252 (2026-09-22); **19.0 in alpha**. CLDR Keyboard support is the 19.0 headline, but CLDR *mobile* keyboards were postponed in a 2026 roadmap update *(per search summary of the Keyman blog; not verified in full)*.
- **Reach:** Claims "over 2500 languages". Ships on Android, iOS, Windows, macOS, Linux and the web.
- **Architecture:**
  - Mobile apps embed **KeymanWeb (JavaScript) in a WebView** for the on-screen keyboard and the predictive-text layer.
  - Keyboards are authored in `.kmn` with Keyman Developer.
  - Predictive text uses word-list "lexical models" (a word list plus optional counts) distributed as `.kmp` packages.
- **Strength:** Unmatched breadth for minority and complex scripts. Its lexical-model format could be a source of word lists for less-served languages.

#### 8VIM

- **Repo and status:** 8VIM/8VIM (formerly flide/8VIM). Apache-2.0, Kotlin, ~0.6k stars. Last stable v0.17.5 (2024-06-28); last commit 2026-03-12, which added automated text replacement.
- **Features:** An 8pen-style circular gesture keyboard with vim-inspired editing. Niche, with a steep learning curve.

#### Simple Keyboard (rkkr)

- **Repo and status:** Apache-2.0, Java, ~1.6k stars. Release 148 (2026-09-12).
- **Features and scope:** Under 1 MB, needs only the vibrate permission, with a number row, spacebar cursor, delete swipe and custom colors. It explicitly will never have emoji, GIFs, a spell checker or swipe typing. Reviewers praise how light it is and criticize its lack of prediction.

#### Newer notable Android projects (2025–2026)

- **Urik** (GPL-3.0, Kotlin, ~0.4k stars, created 2025-09):
  - Geometric swipe typing.
  - On-device spellcheck and autocorrect.
  - **Encrypted local storage and clipboard.**
  - Password-manager autofill.
  - One-handed and split modes.
  - 16+ languages (per AlternativeTo).
- **CleverKeys** (GPL-3.0, Kotlin + ONNX Runtime, ~0.5k stars, created 2025-09):
  - The README claims an on-device swipe model covering 19 languages with public training code, 208 short-swipe actions, unlimited clipboard with tags and regex search, offline GIF packs, XML layouts with 8 sub-labels per key, and TrackPoint cursor control.
  - Its README comparison table against other keyboards is self-reported.
- **LeanType** (GPL-3.0, ~1.0k stars, created 2026-01): a HeliBoard fork adding plugin-based offline voice (Whisper), handwriting and OCR, custom sound packs, a self-updater, and optional assistant features that are out of scope here.
- **Traditional T9 (tt9)** (Apache-2.0, ~0.5k stars): T9 predictive input for keypad phones.
- **Voice-only IMEs:** Sayboard (Vosk) and Transcribro (on-device).
- **EweSticker** (MIT): a sticker keyboard using the commit-content API.

### 3.2 iOS

#### KeyboardKit

- **Repo and status:** github.com/KeyboardKit/KeyboardKit, Swift/SwiftUI, ~1.9k stars. 10.9.5 (2026-09-20); 11.0 in beta.
- **Licence history:** The LICENSE file was **MIT through April 2025** and a **"Closed Source License"** from the 2025-08-31 commit onwards. **KeyboardKit 10.0 (2025-09-29)** merged the open core and Pro into one closed binary SDK with a free tier and paid licence keys.
- **Pro features:**
  - 76 locales.
  - Autocomplete and autocorrect.
  - Emoji keyboard.
  - Theme engine.
  - Dictation: it opens the containing app to record, then returns to the keyboard.
  - Clipboard and text clips (10.0).
  - "Unicode fonts" (10.0).
  - Next-word prediction through external services, disabled by default and needing user consent. KeyboardKit says Apple removed the on-device next-word capability in iOS 16.
- **Relevance to MaKeeb:** Not reusable, since it is closed and Swift-only. It remains a useful reference for iOS extension quirks; for example, 10.9.4 rebuilt gesture handling because of an iOS 27 beta typing-lag regression.

#### Hamster (仓输入法)

- **Repo and status:** imfuxiao/Hamster. GPL-3.0 at first, **MIT from v2.1.0**, ~1.6k stars. Last release 2.1.0 (2023-12-04).
- **Architecture:** Swift + librime (prebuilt via LibrimeKit) + KeyboardKit (MIT era) + Runestone for editing Rime YAML in-app.
- **Status:** The README (updated 2025-05) says the author is building commercial features and **does not plan to open-source further work**. The App Store app continues.

#### azooKey

- **Repo and status:** MIT, Swift, ~0.8k stars. v3.1.1 (2026-09-20).
- **Features:** A Japanese keyboard for iPhone and iPad with its own conversion engine, **AzooKeyKanaKanjiConverter** (MIT, SwiftPM; runs on iOS, macOS, visionOS and Ubuntu), live conversion, and custom keys and tabs. There is also a macOS version.

#### Keyman for iOS

Same codebase and model as Keyman for Android (see [Keyman](#keyman-sil-global) above).

#### fcitx5-ios

- **Repo and status:** GPL-3.0, Swift + C++, ~0.1k stars. A developer beta distributed as an IPA through SideStore.
- **Limitation:** Without a paid developer account there is no App Group, so users must grant Full Access and press "Sync config" after changes. This is a concrete example of the app-to-extension data-sharing problem.

#### giellakbd-ios (Divvun)

- **Repo and status:** Apache-2.0, Swift, ~60 stars. Last push 2026-06-30.
- **Summary:** An open reimplementation of Apple's keyboard focused on minority and indigenous languages. Layouts come from Divvun's `kbdgen` (a Rust layout generator, Apache-2.0), and there is an Android sibling, giellakbd-android.

#### Jyutping

- **Repo and status:** CC0-1.0, Swift, ~160 stars. 0.83.0 (2026-09-11).
- **Summary:** A Cantonese keyboard for iOS and macOS with Android, Windows and HarmonyOS sister projects.

#### Other iOS repos

A GitHub search found many small keyboard-extension demos and single-purpose keyboards: LaTeX, Armenian, Shan, snippets, several voice-dictation keyboards, and assistant keyboards that are out of scope. **No maintained, general-purpose, open-source iOS keyboard with prediction and swipe was found.**

### 3.3 Commercial reference points (expectations only)

- **Gboard.**
  - Core feature set: glide typing, voice typing, emoji search, Emoji Kitchen, GIFs and stickers, clipboard with pinning, one-handed/floating/resize, a text-editing panel, translation, handwriting, incognito, multilingual typing, Material You and image themes, a personal-dictionary shortcut list, and inline autofill.
  - 2025–2026 additions are mostly generative (Gemini writing tools and dictation clean-up) and out of scope.
- **Microsoft SwiftKey.** Flow (swipe) typing, multilingual typing in several languages at once, cloud backup and sync via a Microsoft account, clipboard with pinning, a toolbar, themes and photo themes, and incognito. Microsoft **removed the Copilot and Compose integrations**; the Editor proofreading feature remains.
- **Apple keyboard.**
  - QuickPath swipe, long-press-space trackpad, inline predictions, text replacements, multilingual typing, emoji search, one-handed mode on iPhone, and split and floating modes on iPad.
  - Autocorrect has drawn **widespread complaints since iOS 26** (TechRadar, BGR, MacObserver: wrong letters registered, "broken" autocorrect). That is an opening for third-party keyboards.
- **Fleksy.** Became an SDK company and has now **stopped developing its SDK and shut down its website** (KeyboardKit blog, June 2026).
- **Typewise.** A hexagonal "honeycomb" layout with an offline privacy mode. Still shipping (4.4.44, April 2026, per APKMirror).

---

## 4. What makes users switch

These signals come from reaction counts on open issues (GitHub search, 2026-09-27) and from 2025 reviews.

### 4.1 Pain points (reasons to leave, or never adopt, a keyboard)

1. **Swipe typing is missing or poor.**
   - HeliBoard's most-reacted issue is the open gesture library (#2226, 70).
   - Reviewers name SwiftKey and Gboard as swipe benchmarks, and call FUTO's pre-2026 swipe "alpha".
   - FlorisBoard's glide typing is labelled alpha, and Fossify's swipe request has 22 reactions.
   - Needing to side-load a proprietary library (HeliBoard) is itself a pain point.
2. **Suggestions or autocorrect are missing or weak.**
   - Fossify #58 (52), FUTO's spell checker #1334 (45), FlorisBoard #1283 (33), Thumb-Key #44 (20).
   - MakeUseOf rates ASK's predictions as weak and FlorisBoard's as non-functional.
   - On iOS, the reverse drives switching: Apple's own autocorrect is widely criticized since iOS 26.
3. **No emoji search.** One of the most consistent requests across projects: FlorisBoard #45 (80), Fossify #23 (44), Unexpected #173 (27), Thumb-Key #626 (25). HeliBoard's request had 140 reactions before it shipped.
4. **Setup friction and tinkering.** Downloading dictionaries by hand and hunting for gesture libraries (HeliBoard, FUTO for non-English) put off mainstream users (HowToGeek, MakeUseOf, AlternativeTo summaries).
5. **No floating keyboard on tablets.** HeliBoard #326 (35; shipped in 4.0), ASK #1952, and three separate Unexpected issues.
6. **No CJK or Asian-language input.** ASK #1304 (35), HeliBoard #639 and #786, and FlorisBoard #149 and #140. FUTO's Japanese tracking issue (#1403, 48) was closed when the feature shipped.
7. **No power-user keys.** Ctrl/Alt/Esc/F-keys: FUTO #25 (32), FlorisBoard #229 (24). Hacker's Keyboard is still recommended despite being unmaintained.
8. **No password-manager integration.** FUTO #325 (43). FlorisBoard, HeliBoard and Urik already support inline autofill.
9. **No GIFs or stickers.** HeliBoard #363 (25), FlorisBoard #53 (17). FUTO explicitly declines GIF search on privacy grounds.
10. **Data portability.** Backup/restore (Unexpected #856, 21), and layout changes after updates (Unexpected #955).
11. **Accessibility gaps.** The ASK TalkBack bug (#2316) is still open, and FUTO had TalkBack typing issues (#262). There is little evidence that the open-source keyboards test with screen readers.
12. **Licensing trust.** FUTO's source-available licence draws a relicensing request (#1296) and is a recurring caveat in reviews.

### 4.2 Praised features (reasons to switch *to* an open-source keyboard)

- **Privacy and offline operation.** No internet permission, and incognito mode (every review).
- **Gesture delete.** HeliBoard's delete gesture is called "the smoothest gesture delete".
- **FlorisBoard's Smartbar.** Its undo/redo, clipboard manager and polished modern look.
- **ASK's customization.** Per-app tint, incognito and customizable gestures.
- **Simple Keyboard's size.** Tiny and fast.
- **FUTO's offline extras.** Offline voice input and, since June 2026, competitive swipe.
- **Unexpected Keyboard and Thumb-Key.** Loyal niche audiences: programmers and Termux users for corner swipes, and MessagEase refugees for the grid.
- **Deep customization.** Layouts, themes and dictionaries, for power users.

### 4.3 Implications for MaKeeb's positioning

- The "privacy + actually good typing" niche is under-served, and no open-source keyboard serves it on iOS. The MVP has to avoid what reviewers punish: no suggestions, side-loaded dictionaries, and missing emoji search.
- Swipe typing should be on the 1.0 plan (P1). It is XL work, but open decoders with public datasets now exist to build on or learn from.

---

## 5. Priority and effort definitions

| Priority | Meaning |
|---|---|
| **P0** | MVP: the keyboard is unusable or uncompetitive without it. |
| **P1** | Expected by users of a modern keyboard; needed for 1.0. |
| **P2** | Differentiator or nice to have. |
| **P3** | Long tail; later. |

| Effort | Rough size (one experienced developer, shared KMP code plus both platform shells) |
|---|---|
| **S** | Up to about 1 week |
| **M** | About 1–3 weeks |
| **L** | About 1–2 months |
| **XL** | More than 2 months, or research-heavy |

### 5.1 Features by priority

**P0 (28):** `key-grid-multitouch` (L), `key-press-preview` (S), `long-press-alternates` (M), `shift-caps-lock` (S), `auto-capitalization` (S), `input-type-adaptation` (M), `haptic-sound-feedback` (S), `backspace-repeat-word-delete` (S), `symbol-number-layers` (M), `ime-switcher-key` (S), `declarative-layout-engine` (L), `latin-layout-variants` (S), `language-coverage` (L), `language-switching` (S), `suggestion-strip` (M), `word-completion` (M), `autocorrect` (L), `autocorrect-revert` (S), `dictionary-packs` (M), `spacebar-cursor-slide` (S), `emoji-panel` (M), `system-dark-light` (S), `offline-no-network` (S), `incognito-mode` (S), `secure-field-handling` (S), `onboarding-setup-flow` (S), `settings-app` (M), `shared-app-extension-storage` (S)

**P1 (39):** `number-row` (S), `punctuation-conveniences` (S), `touch-model-hit-correction` (L), `multilingual-typing` (L), `rtl-layout-support` (M), `tablet-foldable-layouts` (M), `custom-layout-import` (M), `composition-engine-api` (M), `next-word-prediction` (L), `on-device-learning` (M), `personal-dictionary` (M), `text-expansion-shortcuts` (M), `emoji-suggestions` (S), `inline-autofill-suggestions` (M), `glide-typing` (XL), `trackpad-cursor-mode` (M), `backspace-swipe-delete` (S), `toolbar-quick-actions` (M), `cursor-navigation-panel` (M), `selection-clipboard-actions` (S), `emoji-search` (M), `emoji-unicode-currency` (M), `clipboard-history` (M), `clipboard-pinned-snippets` (S), `clipboard-paste-suggestion` (S), `clipboard-sensitive-handling` (S), `dynamic-color` (S), `color-theme-editor` (M), `key-visual-options` (S), `keyboard-resize` (S), `one-handed-mode` (S), `split-keyboard` (M), `screen-reader-support` (L), `display-accessibility-prefs` (S), `timing-tuning` (S), `locked-device-protection` (S), `voice-input-handoff` (S), `backup-restore` (M), `hardware-keyboard-mode` (M)

**P2 (24):** `visual-layout-editor` (L), `cldr-keyboard3-support` (L), `pc-modifier-keys` (M), `dead-keys-compose` (M), `syllable-composition-scripts` (M), `indic-complex-script-input` (L), `revisit-word-suggestions` (M), `system-spell-checker` (M), `contact-name-suggestions` (S), `offensive-word-filter` (S), `directional-key-swipes` (M), `configurable-swipe-actions` (M), `undo-redo` (S), `symbol-kaomoji-panels` (S), `clipboard-media-items` (M), `clipboard-search` (S), `theme-media-fonts` (S), `stylesheet-theme-engine` (L), `theme-sharing` (M), `floating-keyboard` (L), `encrypted-local-storage` (M), `verifiable-builds` (M), `extension-packages` (L), `hardware-layout-mapping` (M)

**P3 (10):** `alternative-layout-paradigms` (M), `chinese-input` (XL), `japanese-input` (XL), `handwriting-input` (XL), `gif-sticker-insertion` (L), `per-app-tint` (M), `opt-in-data-donation` (M), `on-device-dictation` (XL), `cloud-sync` (L), `automation-integration` (M)


### 5.2 Suggested differentiators

1. **Open, accurate swipe typing on both Android and iOS** (`glide-typing`). No open-source iOS keyboard offers it. Build on FUTO Swipe (mind the GPL library and custom model licence) or the upcoming Apache-2.0 NLnet library, or implement a SHARK2-style decoder in shared Kotlin, evaluated against FUTO's MIT dataset.
2. **Real offline prediction and autocorrect on iOS in an open-source keyboard** (`word-completion`, `autocorrect`, `next-word-prediction`). KeyboardKit is now closed and Hamster is frozen, so the space is empty.
3. **Standards-based layouts via CLDR Keyboard 3.0** (`cldr-keyboard3-support`, `declarative-layout-engine`). Reusing the KMP k3lp parser gives one source of truth for many languages and a path to user-importable layouts and a visual editor.
4. **Emoji search with always-current emoji data** (`emoji-search`, `emoji-unicode-currency`). One of the most-requested gaps across open-source keyboards and cheap to build from CLDR annotations.
5. **Transparent privacy.** Offline by design, a published "works without Full Access" matrix, sensitive-clip handling, lock-screen protection and encrypted local storage (`offline-no-network`, `clipboard-sensitive-handling`, `encrypted-local-storage`).

### 5.3 Excluded by scope (not in the catalogue)

- AI writing tools (proofread, rephrase, tone)
- AI compose and reply
- Chat assistants in the keyboard
- AI image, sticker and emoji generation
- AI dictation clean-up (for example Gboard's "Rambler")
- Translation
- OCR
- Neural-LM prediction as a distinct feature

Classic statistical prediction and autocorrect, and swipe decoding, remain in scope.

---

## 6. Feature catalogue

The categories are:

- `core-typing`
- `layouts-languages`
- `input-methods-cjk-indic`
- `prediction-autocorrect`
- `gesture-swipe`
- `editing-cursor`
- `emoji-symbols-media`
- `clipboard`
- `theming-appearance`
- `one-handed-floating-split`
- `accessibility`
- `privacy-security`
- `voice-input`
- `settings-sync-backup`
- `extensibility`
- `hardware-keyboard`

The same data is in [`feature-candidates.json`](feature-candidates.json).

### 6.1 Core typing (`core-typing`, 13)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `key-grid-multitouch` | Key grid with multitouch and rollover | Low-latency tap-to-type key grid that handles fast overlapping touches (rollover), cancelled touches and edge taps correctly. The rendering and hit-testing pipeline everything else builds on. | FlorisBoard, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Hacker's Keyboard, Gboard, SwiftKey, Apple Keyboard | P0 | L | iOS keyboard extensions run under a tight memory ceiling, so renderer and Compose Multiplatform overhead must be measured early (see platform research). |
| `key-press-preview` | Key press preview popup | Enlarged bubble showing the pressed character above the finger, with an option to disable it. | HeliBoard, AOSP LatinIME, fcitx5-android, Fossify Keyboard, Gboard, Apple Keyboard | P0 | S | iOS: an extension cannot draw outside its own view, so top-row callouts need reserved headroom or an alternative design (verify). |
| `long-press-alternates` | Long-press alternate characters | Long-press (or swipe-up) on a key opens a popup of accented letters, symbols or digits defined per layout and locale. | HeliBoard, FlorisBoard, AnySoftKeyboard, fcitx5-android, FUTO Keyboard, Gboard, Apple Keyboard | P0 | M | – |
| `shift-caps-lock` | Shift and caps lock | One-shot shift, double-tap (or gesture) caps lock, and correct shifted labels on all keys. | FlorisBoard, HeliBoard, AnySoftKeyboard, Unexpected Keyboard, Gboard, Apple Keyboard | P0 | S | – |
| `auto-capitalization` | Auto-capitalization | Automatically shift at sentence starts and respect the text field's requested capitalization mode (words, sentences, characters, none). | AnySoftKeyboard, HeliBoard, AOSP LatinIME, Gboard, Apple Keyboard | P0 | S | Android: EditorInfo/getCursorCapsMode. iOS: autocapitalizationType trait plus documentContextBeforeInput, which only exposes limited context. |
| `input-type-adaptation` | Field-aware layouts and action key | Adapt the layout to the field type (email '@' and '.com', URL, number, date/time, password) and label the action key from the field's IME action (Go, Search, Send, Next). | AnySoftKeyboard, HeliBoard, FlorisBoard, Gboard, Apple Keyboard | P0 | M | iOS: secure text fields and phone-pad fields always switch to the system keyboard, so a custom keyboard never sees them. |
| `haptic-sound-feedback` | Haptic and sound feedback | Configurable vibration strength and key click sounds (optionally sound packs), respecting system silent or do-not-disturb state. | AnySoftKeyboard, HeliBoard, Fossify Keyboard, Thumb-Key, FUTO Keyboard, LeanType, Gboard, Apple Keyboard | P0 | S | iOS: haptic (and per KeyboardKit docs, audio) feedback only works when the user grants Full Access. |
| `backspace-repeat-word-delete` | Backspace repeat and word delete | Holding backspace auto-repeats and accelerates, optionally switching to whole-word deletion, without splitting composite emoji or grapheme clusters. | Thumb-Key, Unexpected Keyboard, FlorisBoard, Gboard, Apple Keyboard | P0 | S | – |
| `symbol-number-layers` | Symbol, number and numpad layers | Symbol and 'more symbols' pages plus a dedicated number pad shown automatically for numeric fields and reachable manually. | HeliBoard, AnySoftKeyboard, FlorisBoard, Fossify Keyboard, Gboard, Apple Keyboard | P0 | M | – |
| `ime-switcher-key` | Next-keyboard (globe) key | Key or long-press menu to switch to the next installed keyboard/input method. | Apple Keyboard, KeyboardKit, HeliBoard, Gboard | P0 | S | iOS: must show a switch-keyboard key when needsInputModeSwitchKey is true (App Review expectation). Android: shouldOfferSwitchingToNextInputMethod. |
| `number-row` | Optional number row | Persistent top row of digits, toggleable per user preference and shown automatically where layouts expect it. | HeliBoard, Simple Keyboard (rkkr), FlorisBoard, Gboard | P1 | S | – |
| `punctuation-conveniences` | Punctuation conveniences | Double-space inserts a period, auto-space after punctuation, and swapping a trailing space with punctuation typed next ('word ,' becomes 'word, '). | AOSP LatinIME, HeliBoard, FlorisBoard, Thumb-Key, Gboard, Apple Keyboard | P1 | S | – |
| `touch-model-hit-correction` | Probabilistic key hit correction | Resolve ambiguous taps with a spatial touch model combined with the language model (dynamic key targets), rather than fixed rectangular hit boxes. | AOSP LatinIME, HeliBoard, FUTO Keyboard, Gboard, Apple Keyboard | P1 | L | – |

### 6.2 Layouts & languages (`layouts-languages`, 12)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `declarative-layout-engine` | Data-driven layout engine | Layouts, popups, and per-state key variants (shift, field type, RTL) defined in data files parsed by shared code instead of hard-coded views. Candidate formats: FlorisBoard-style JSON (also read by HeliBoard) or CLDR Keyboard 3.0 XML. | FlorisBoard, HeliBoard, Unexpected Keyboard, FUTO Keyboard, Trime, Keyman, Thumb-Key | P0 | L | Pure shared logic and a good fit for commonMain. |
| `latin-layout-variants` | Latin layout variants | QWERTY, QWERTZ, AZERTY plus alternative layouts (Dvorak, Colemak, Workman) selectable independently of the language. | AnySoftKeyboard, HeliBoard, Urik, Hacker's Keyboard, Gboard | P0 | S | – |
| `language-coverage` | Broad language coverage | Launch with a core set of languages (layout, long-press alternates, dictionary) and a data pipeline (e.g. CLDR exemplar characters) to scale to many more. | Keyman, HeliBoard, AnySoftKeyboard, FlorisBoard, Gboard, SwiftKey, Apple Keyboard | P0 | L | – |
| `language-switching` | Fast language switching | Switch enabled languages/layouts via a language key, spacebar swipe or long-press menu, with the current language shown on the spacebar. | HeliBoard, FlorisBoard, Fossify Keyboard, Gboard, SwiftKey, Apple Keyboard | P0 | S | – |
| `multilingual-typing` | Multilingual typing | Type in two or more languages on one layout without switching, with suggestions and autocorrect drawn from all active dictionaries. | HeliBoard, AnySoftKeyboard, Urik, CleverKeys, Gboard, SwiftKey, Apple Keyboard | P1 | L | – |
| `rtl-layout-support` | Right-to-left scripts | Arabic, Hebrew, Persian and other RTL layouts with mirrored function keys, correct bidi cursor movement and RTL-aware suggestion strip. | HeliBoard, AnySoftKeyboard, Keyman, Gboard, Apple Keyboard | P1 | M | – |
| `tablet-foldable-layouts` | Tablet, landscape and foldable layouts | Layouts and scaling tuned for large screens, landscape and folded or unfolded states, remembered separately per posture. | HeliBoard, Unexpected Keyboard, Gboard, Apple Keyboard | P1 | M | – |
| `custom-layout-import` | Custom layout import | Users can paste or import a layout file (and edit key popups) in the app, with validation so broken layouts cannot crash the keyboard. | HeliBoard, Unexpected Keyboard, FUTO Keyboard, CleverKeys, Urik, Thumb-Key, Trime | P1 | M | – |
| `visual-layout-editor` | Visual layout editor | Graphical editor for creating and rearranging keys and popups without hand-writing layout files. In the open-source keyboards this exists mostly as community web tools. | HeliBoard, Unexpected Keyboard, Keyman | P2 | L | – |
| `cldr-keyboard3-support` | CLDR Keyboard 3.0 (LDML) layouts | Read Unicode CLDR Keyboard 3.0 (UTS #35 Part 7) files so one standard source defines touch and hardware layouts for a language. | FlorisBoard, Keyman | P2 | L | k3lp (Kotlin Multiplatform, Apache-2.0, pre-alpha) already parses Keyboard3 files and could be reused directly. |
| `pc-modifier-keys` | PC-style keys (Ctrl, Alt, Esc, Tab, F-keys) | Optional layout or row with modifier, Esc, Tab, arrow and function keys for terminals, SSH and remote desktops. | Hacker's Keyboard, Unexpected Keyboard, CleverKeys, HeliBoard | P2 | M | iOS: the text document proxy only inserts text, deletes and moves the cursor, so modifier combinations cannot reach the host app (verify). |
| `alternative-layout-paradigms` | Alternative input paradigms | Non-QWERTY paradigms such as 3x3 grids with swipe letters (MessagEase style), circular 8pen-style input, hexagonal layouts or T9 multi-tap for keypad phones. | Thumb-Key, 8VIM, Typewise, Traditional T9 | P3 | M | – |

### 6.3 Input methods: CJK, Indic & composition (`input-methods-cjk-indic`, 7)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `composition-engine-api` | Pluggable composition engine | An internal interface for composing input (dead keys, Hangul, Telex, kana, pinyin via librime) with preedit/composing text and candidate lists. This lets new scripts plug in without reworking the core. | FlorisBoard, fcitx5-android, Trime, Hamster, FUTO Keyboard, Keyman | P1 | M | Android supports composing regions natively. iOS uses setMarkedText on the proxy, which some host apps handle inconsistently (verify). |
| `dead-keys-compose` | Dead keys and compose sequences | Dead keys and multi-key compose sequences for diacritics and rare characters. | Unexpected Keyboard, Thumb-Key, FlorisBoard, Keyman | P2 | M | – |
| `syllable-composition-scripts` | Korean Hangul and Vietnamese Telex/VNI | Composition engines that assemble syllables from keystrokes: Hangul jamo composition and Vietnamese Telex/VNI tone-mark input. | fcitx5-android, FUTO Keyboard, FlorisBoard, Gboard, Apple Keyboard | P2 | M | – |
| `indic-complex-script-input` | Indic and complex-script input | Layouts and rules for Indic, Southeast Asian and other complex scripts (conjuncts, vowel signs, reordering), including phonetic or transliteration schemes that turn Latin keystrokes into native script. | Keyman, HeliBoard, fcitx5-android, Gboard | P2 | L | – |
| `chinese-input` | Chinese input methods | Pinyin (full, fuzzy, double), Zhuyin, Wubi, Cangjie, stroke and Cantonese Jyutping with a candidate window and user phrase learning. | Trime, fcitx5-android, fcitx5-ios, Hamster, FUTO Keyboard, Jyutping, Gboard, Apple Keyboard | P3 | XL | librime (BSD-3-Clause) already runs on Android (Trime, fcitx5, FUTO) and iOS (Hamster), so integrating it is far cheaper than building an engine. |
| `japanese-input` | Japanese kana-kanji input | Romaji and 12-key flick input with kana-kanji conversion and live conversion. | azooKey, fcitx5-android, FUTO Keyboard, Gboard, Apple Keyboard | P3 | XL | AzooKeyKanaKanjiConverter (MIT) is Swift-only, so it is reusable on iOS but not from shared Kotlin code. |
| `handwriting-input` | Handwriting input | Draw characters on a canvas and have them recognized, mainly valuable for CJK and for users who cannot type. | Gboard, LeanType, Apple Keyboard | P3 | XL | – |

### 6.4 Prediction & autocorrect (`prediction-autocorrect`, 15)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `suggestion-strip` | Suggestion strip | Candidate bar with three or more suggestions (typed word, best guess, alternatives), punctuation shortcuts when idle, and case-aware suggestions that follow shift state. | HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard | P0 | M | – |
| `word-completion` | Word completion | Frequency-ranked completion of the word being typed from a compact on-device dictionary (trie or DAWG), including diacritic-insensitive matching ('naive' finds 'naïve'). | HeliBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Keyman, Gboard, SwiftKey, Apple Keyboard | P0 | M | Shared engine in commonMain. Dictionary memory footprint is critical inside the iOS extension. |
| `autocorrect` | Autocorrect | Replace likely typos on space or punctuation using edit distance, keyboard proximity and word frequency, with adjustable aggressiveness and a per-language off switch. | HeliBoard, OpenBoard, AOSP LatinIME, AnySoftKeyboard, FUTO Keyboard, Unexpected Keyboard, Urik, CleverKeys, Keyman, Gboard, SwiftKey, Apple Keyboard | P0 | L | – |
| `autocorrect-revert` | One-tap autocorrect undo | Backspace immediately after an autocorrection restores the original word and stops correcting it again in the same spot. | AOSP LatinIME, HeliBoard, Gboard, Apple Keyboard | P0 | S | – |
| `dictionary-packs` | Per-language dictionary packs | Bundled and importable dictionaries per language in a documented format, so languages can be added without an app update. | HeliBoard, FUTO Keyboard, AnySoftKeyboard, Unexpected Keyboard, Keyman, FlorisBoard | P0 | M | Offline-first Android keyboards have users download files in a browser and import them. On iOS, network access and a shared container both involve Full Access (see platform research). Wordlist licences vary per language. |
| `next-word-prediction` | Next-word prediction | Suggest likely next words after a space, using n-gram (bigram or trigram) statistics from the dictionary and the user's own history. | AOSP LatinIME, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Gboard, SwiftKey, Apple Keyboard | P1 | L | iOS provides no usable next-word API to extensions (KeyboardKit says Apple removed it in iOS 16), so MaKeeb must ship its own model. |
| `on-device-learning` | On-device learning and forgetting | Learn new words and personal word pairs locally, and let users see, remove or reset learned data (for example long-press a suggestion to forget it). | HeliBoard, AOSP LatinIME, FUTO Keyboard, AnySoftKeyboard, Gboard, SwiftKey, Apple Keyboard | P1 | M | – |
| `personal-dictionary` | Personal dictionary editor | View, add and delete user words (with an 'add to dictionary' action on the typed word) so names and jargon stop being autocorrected. | HeliBoard, AOSP LatinIME, Gboard, Apple Keyboard | P1 | M | iOS: UILexicon exposes the user's system text replacements (read-only) to keyboards. |
| `text-expansion-shortcuts` | Text expansion shortcuts | User-defined abbreviations that expand into phrases ('omw' becomes 'On my way!'), offered as a suggestion or applied automatically. | 8VIM, CleverKeys, Gboard, Apple Keyboard | P1 | M | iOS: Apple's text replacements can be imported through UILexicon. |
| `emoji-suggestions` | Emoji suggestions | Offer matching emoji in the suggestion strip when a typed word matches an emoji name or keyword. | FlorisBoard, HeliBoard, SwiftKey, Gboard, Apple Keyboard | P1 | S | – |
| `inline-autofill-suggestions` | Password-manager inline autofill | Show password-manager and autofill chips inline in the suggestion strip or toolbar. | FlorisBoard, HeliBoard, Urik | P1 | M | Android 11+ only (inline suggestions API). On iOS, AutoFill belongs to the system keyboard. |
| `revisit-word-suggestions` | Suggestions when revisiting a word | Moving the cursor back into an earlier word shows its alternatives again, so a missed correction or wrong swipe can be fixed with one tap. | AOSP LatinIME, HeliBoard, FUTO Keyboard, Gboard | P2 | M | iOS: limited surrounding-text access makes this harder (verify). |
| `system-spell-checker` | System-wide spell checker service | Expose MaKeeb's dictionaries as the OS spell checker that underlines misspellings in any app. | AOSP LatinIME, HeliBoard, Unexpected Keyboard | P2 | M | Android-only (SpellCheckerService). iOS has no third-party spell-checker extension point. |
| `contact-name-suggestions` | Contact-name suggestions | Opt-in use of contact names as dictionary words so they are suggested and not autocorrected. | AOSP LatinIME, AnySoftKeyboard | P2 | S | Android needs READ_CONTACTS, which is a privacy trade-off (LeanType removed it). iOS UILexicon may supply some contact names (verify). |
| `offensive-word-filter` | Offensive-word filter | Keep offensive words out of suggestions and autocorrect targets by default. The user can still type them. | AOSP LatinIME, HeliBoard, Gboard | P2 | S | – |

### 6.5 Gesture & swipe (`gesture-swipe`, 6)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `spacebar-cursor-slide` | Spacebar cursor slide | Slide horizontally on the spacebar to move the cursor character by character (optionally word by word). | HeliBoard, Simple Keyboard (rkkr), Thumb-Key, Unexpected Keyboard, FUTO Keyboard, FlorisBoard, Gboard | P0 | S | iOS: adjustTextPosition(byCharacterOffset:) supports horizontal movement. |
| `glide-typing` | Glide / swipe typing | Enter whole words by sliding across letters, decoded against the dictionary and context, with a visible gesture trail, a floating preview, alternatives after each swipe, and learning of swiped words. | HeliBoard, FUTO Keyboard, AnySoftKeyboard, FlorisBoard, Urik, CleverKeys, LeanType, Gboard, SwiftKey, Apple Keyboard | P1 | XL | Reuse options: FUTO Swipe (GPL C++ library, models under the FUTO Model License, MIT dataset) or HeliBoard's NLnet-funded replacement library (planned Apache-2.0, unreleased). Decoding and trail rendering are shared logic. |
| `trackpad-cursor-mode` | Trackpad cursor mode | Long-press the spacebar to turn the keyboard into a trackpad for two-dimensional cursor movement and selection. | Apple Keyboard, HeliBoard, CleverKeys | P1 | M | iOS: the proxy only exposes character offsets, so vertical movement and selection must be approximated (verify). |
| `backspace-swipe-delete` | Swipe-to-delete words | Swipe left from backspace to select a variable number of previous words and delete them on release. | HeliBoard, Simple Keyboard (rkkr), FlorisBoard, Thumb-Key, Unexpected Keyboard, Gboard | P1 | S | iOS: word boundaries come from documentContextBeforeInput, which may be truncated. |
| `directional-key-swipes` | Directional key swipes | Short swipes in up to eight directions on a key produce secondary characters or actions, so symbols can be reached without switching layers. | Unexpected Keyboard, Thumb-Key, CleverKeys, 8VIM | P2 | M | – |
| `configurable-swipe-actions` | Configurable keyboard gestures | User-mappable gestures (swipe up, down, left or right on the keyboard or specific keys) for actions such as hide keyboard, shift, switch language or open the clipboard. | FlorisBoard, AnySoftKeyboard, CleverKeys, Unexpected Keyboard | P2 | M | – |

### 6.6 Editing & cursor control (`editing-cursor`, 4)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `toolbar-quick-actions` | Toolbar / quick-action bar | Configurable row of actions (clipboard, emoji, settings, one-handed, select all, undo, incognito) that shares or alternates with the suggestion strip, with pinnable keys. | FlorisBoard, HeliBoard, FUTO Keyboard, Gboard, SwiftKey | P1 | M | – |
| `cursor-navigation-panel` | Cursor and navigation panel | Panel or layout with arrow keys, Home/End, word jumps and a select mode for precise editing. | HeliBoard, Unexpected Keyboard, Hacker's Keyboard, FlorisBoard, 8VIM, Gboard | P1 | M | iOS: only horizontal cursor moves are possible and extensions cannot create selections (verify). |
| `selection-clipboard-actions` | Select, cut, copy and paste actions | One-tap Select All, Cut, Copy and Paste from the keyboard. | HeliBoard, FlorisBoard, Unexpected Keyboard, Gboard | P1 | S | Android uses performContextMenuAction or key events. iOS: pasting needs Full Access to read the pasteboard, and cut/copy/select-all are largely unavailable to extensions (verify). |
| `undo-redo` | Undo / redo | Undo and redo keys that send the platform undo command to the focused app. | FlorisBoard, HeliBoard | P2 | S | Android: Ctrl+Z / Ctrl+Shift+Z key events, which work only in some apps. iOS: no undo API for extensions (verify). |

### 6.7 Emoji, symbols & media (`emoji-symbols-media`, 5)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `emoji-panel` | Emoji panel | Categorized, scrollable emoji picker with recents and a skin-tone/gender variant picker, reachable from a key or long-press. | FlorisBoard, HeliBoard, AnySoftKeyboard, Fossify Keyboard, FUTO Keyboard, fcitx5-android, Thumb-Key, Gboard, SwiftKey, Apple Keyboard | P0 | M | – |
| `emoji-search` | Emoji search | Search emoji by name or keyword in the user's language, using CLDR annotations. Among the most-requested features in several open-source keyboards. | HeliBoard, Gboard, SwiftKey, Apple Keyboard | P1 | M | Needs text entry inside the keyboard itself (a search field that captures keystrokes), which both platforms allow. |
| `emoji-unicode-currency` | Up-to-date emoji with compatibility | Keep emoji data current (Unicode 17 in 2026) while hiding or rendering emoji the device font cannot show. | HeliBoard, FUTO Keyboard, FlorisBoard, AnySoftKeyboard | P1 | M | Android: FUTO found the bundled emoji2 font adds about 9 MB and ships a trimmed 1 MB compatibility font instead. iOS: filter emoji by OS version. |
| `symbol-kaomoji-panels` | Symbols and kaomoji panels | Extra panels for Unicode symbols (math, arrows, currency) and text emoticons or kaomoji. | fcitx5-android, Trime, FlorisBoard, Fossify Keyboard, Gboard | P2 | S | – |
| `gif-sticker-insertion` | GIF and sticker insertion | Search or browse GIFs and sticker packs (online or offline packs) and insert them into apps that accept rich content. | Gboard, SwiftKey, CleverKeys, EweSticker | P3 | L | Android: commitContent works only in apps that support it. iOS: copy to the pasteboard and paste (Full Access). Online search needs network, which conflicts with an offline stance (FUTO declined GIF search for this reason). |

### 6.8 Clipboard (`clipboard`, 6)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `clipboard-history` | Clipboard history | Local history of copied text with tap-to-paste, configurable retention and size, and an option for a private keyboard-internal clipboard. | FlorisBoard, HeliBoard, Fossify Keyboard, FUTO Keyboard, fcitx5-android, Thumb-Key, Trime, CleverKeys, Urik, KeyboardKit, Gboard, SwiftKey | P1 | M | Android 10+: only the focused default IME can read the clipboard. iOS: requires Full Access, and copies can only be captured while the keyboard is running (verify). |
| `clipboard-pinned-snippets` | Pinned clips and snippets | Pin clips so they never expire, and keep a list of reusable snippets or quick texts. | Fossify Keyboard, FUTO Keyboard, CleverKeys, AnySoftKeyboard, Gboard, SwiftKey | P1 | S | – |
| `clipboard-paste-suggestion` | Recent-copy paste suggestion | Show a freshly copied item as a one-tap chip in the suggestion strip for a short time. | HeliBoard, Gboard, SwiftKey | P1 | S | iOS: requires Full Access. |
| `clipboard-sensitive-handling` | Sensitive clip protection | Never store clips flagged as sensitive (for example passwords from managers), auto-expire unpinned items, and exclude clips copied in password fields. | FlorisBoard, HeliBoard, Gboard | P1 | S | Android 13+: ClipDescription EXTRA_IS_SENSITIVE. |
| `clipboard-media-items` | Images and files in clipboard | Keep copied images, screenshots and files in history (with size caps) and paste them into apps that accept rich content. | HeliBoard, FUTO Keyboard, FlorisBoard, CleverKeys | P2 | M | Android: commitContent / content URIs. iOS: pasteboard only. |
| `clipboard-search` | Clipboard search and filter | Search or filter clipboard history by text (optionally regex or tags). | FUTO Keyboard, FlorisBoard, CleverKeys | P2 | S | – |

### 6.9 Theming & appearance (`theming-appearance`, 8)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `system-dark-light` | Light/dark theme following the system | Built-in light and dark themes that follow the system setting, with optional time-based switching. | AnySoftKeyboard, Fossify Keyboard, FlorisBoard, HeliBoard, Gboard, Apple Keyboard | P0 | S | – |
| `dynamic-color` | Material You dynamic color | Derive keyboard colors from the Android 12+ wallpaper palette. | FlorisBoard, Urik, Thumb-Key, fcitx5-android, Gboard | P1 | S | Android 12+ only. iOS has no equivalent (could use the host's tint or the system accent). |
| `color-theme-editor` | Custom colors and theme editor | In-app editing of key, background, text and accent colors with a live preview. | FlorisBoard, HeliBoard, Fossify Keyboard, FUTO Keyboard, CleverKeys, Simple Keyboard (rkkr) | P1 | M | – |
| `key-visual-options` | Key borders, gaps, labels and hints | Toggle key borders, adjust corner radius and key gaps, key label and hint size, and show long-press hints on keys. | HeliBoard, fcitx5-android, FlorisBoard, AnySoftKeyboard, Gboard | P1 | S | – |
| `theme-media-fonts` | Background images and custom fonts | Use a photo or image as the keyboard background and choose custom key-label fonts. | HeliBoard, fcitx5-android, FUTO Keyboard, Fossify Keyboard, Gboard, SwiftKey | P2 | S | iOS: importing a user photo happens in the containing app and needs the shared container to reach the extension. |
| `stylesheet-theme-engine` | Stylesheet-based theme engine | Declarative, element-level theme format (CSS-like selectors for keys, states, popups, toolbar) so artists can build complete themes beyond a color picker. | FlorisBoard, FUTO Keyboard, Trime, KeyboardKit | P2 | L | Shared engine in commonMain mapping to Compose styles on both platforms. |
| `theme-sharing` | Theme import/export and gallery | Share themes as files and optionally browse a community gallery. | FlorisBoard, FUTO Keyboard, AnySoftKeyboard | P2 | M | – |
| `per-app-tint` | Per-app keyboard tint | Tint the keyboard to match the color of the app being typed into. | AnySoftKeyboard | P3 | M | iOS: the host app identity and colors are not reliably available to extensions (verify). |

### 6.10 One-handed, floating & split (`one-handed-floating-split`, 4)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `keyboard-resize` | Keyboard height and size | Adjust keyboard height, width and bottom offset, stored per orientation. | Simple Keyboard (rkkr), HeliBoard, FlorisBoard, AnySoftKeyboard, Gboard | P1 | S | iOS: height is set with constraints on the input view, while width follows the screen (verify). |
| `one-handed-mode` | One-handed mode | Compact keyboard docked left or right with a quick toggle to switch sides. | FlorisBoard, HeliBoard, OpenBoard, Urik, Gboard, SwiftKey, Apple Keyboard | P1 | S | – |
| `split-keyboard` | Split keyboard | Split the layout into two halves for thumb typing on tablets, foldables and landscape phones. | HeliBoard, Unexpected Keyboard, Urik, Gboard, Apple Keyboard | P1 | M | – |
| `floating-keyboard` | Floating keyboard | Undocked, movable and resizable keyboard window, which is especially useful on tablets. Repeatedly requested in several open-source keyboards. | HeliBoard, FlorisBoard, Gboard, SwiftKey, Apple Keyboard | P2 | L | Android: feasible through IME window and insets handling (HeliBoard 4.0, FlorisBoard 0.6 alpha). iOS: floating is a system-keyboard feature and likely unavailable to extensions (verify). |

### 6.11 Accessibility (`accessibility`, 3)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `screen-reader-support` | Screen reader support | Full TalkBack/VoiceOver support: announced keys and suggestions, explore-by-touch with lift-to-type, and accessible panels. | AOSP LatinIME, Gboard, Apple Keyboard | P1 | L | Custom Compose-drawn keys need explicit semantics. Compose Multiplatform accessibility inside an Android IME window and an iOS extension needs early validation. |
| `display-accessibility-prefs` | Font scale, contrast and reduced motion | Respect system font scaling without breaking layouts, offer high-contrast themes, and honor reduce-motion by disabling popups and animations. | Gboard, Apple Keyboard, FUTO Keyboard | P1 | S | – |
| `timing-tuning` | Long-press and repeat timing | Adjustable long-press delay, key repeat rate and accidental-touch debounce for users with motor impairments. | HeliBoard, AOSP LatinIME | P1 | S | – |

### 6.12 Privacy & security (`privacy-security`, 7)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `offline-no-network` | Offline by design | Core keyboard needs no network access: no INTERNET permission on Android, and every core feature works without Full Access on iOS. | HeliBoard, FUTO Keyboard, Fossify Keyboard, CleverKeys, Urik, Simple Keyboard (rkkr), LeanType | P0 | S | iOS: Full Access is all-or-nothing (network, pasteboard, haptics, shared container), so a clear 'what works without it' matrix is needed. |
| `incognito-mode` | Incognito mode | Manual and automatic incognito that stops learning, history and emoji recents, turned on automatically when an app requests no personalized learning. | AnySoftKeyboard, FlorisBoard, HeliBoard, Gboard, SwiftKey | P0 | S | Android: IME_FLAG_NO_PERSONALIZED_LEARNING. iOS has no equivalent hint beyond secure fields (verify). |
| `secure-field-handling` | Password-field handling | In password fields, disable suggestions, learning, key previews and clipboard capture. | AOSP LatinIME, HeliBoard, Trime | P0 | S | iOS: secure fields always use the system keyboard. |
| `locked-device-protection` | Lock-screen protection | When the device is locked, hide clipboard, suggestions and learned data, and handle direct-boot storage correctly. | HeliBoard | P1 | S | Android direct boot: credential-protected storage is unavailable before the first unlock. |
| `encrypted-local-storage` | Encrypted local storage | Encrypt learned words and clipboard history at rest. | Urik | P2 | M | Android Keystore. iOS Keychain / Data Protection classes. |
| `verifiable-builds` | Reproducible builds and signing transparency | Reproducible Android builds and published signing-certificate hashes so users can verify binaries. | FlorisBoard, fcitx5-android | P2 | M | Android-only in practice. App Store binaries are re-signed by Apple. |
| `opt-in-data-donation` | Opt-in local data donation | Explicitly opt-in, user-reviewed export of anonymized typing or gesture samples to improve models, with blocklists and a review screen. | HeliBoard, FUTO Keyboard | P3 | M | – |

### 6.13 Voice input (`voice-input`, 2)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `voice-input-handoff` | Voice input key | Microphone key that hands off to the system or an installed voice input method and returns afterwards. | AnySoftKeyboard, HeliBoard, FlorisBoard, Gboard, Apple Keyboard | P1 | S | iOS: keyboard extensions cannot use the microphone. KeyboardKit works around this by opening the containing app to record. |
| `on-device-dictation` | Built-in offline dictation | Speech-to-text built into the keyboard or a companion plugin, running fully on-device. | FUTO Voice Input, Sayboard, Transcribro, LeanType, Gboard, Apple Keyboard | P3 | XL | Android: RECORD_AUDIO inside the IME is allowed. iOS: only possible through a round-trip to the containing app. |

### 6.14 Settings, sync & backup (`settings-sync-backup`, 5)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `onboarding-setup-flow` | Guided setup | First-run flow to enable and select the keyboard, explain permissions (Full Access on iOS), and pick languages. | FlorisBoard, KeyboardKit, fcitx5-ios, Keyman | P0 | S | iOS: users must add the keyboard in Settings manually, and a deep link can only open the app's settings page. |
| `settings-app` | Settings app with in-keyboard quick settings | Companion app for all settings with search, plus a quick-settings panel reachable from the keyboard. | FlorisBoard, HeliBoard, AnySoftKeyboard, FUTO Keyboard, Gboard, SwiftKey | P0 | M | Shared Compose Multiplatform UI for both platforms. |
| `shared-app-extension-storage` | Shared storage between app and keyboard | Settings, dictionaries and themes shared between the companion app and the keyboard process. | KeyboardKit, fcitx5-ios | P0 | S | iOS: needs an App Group, and Apple's guide ties the shared container to Open Access, so a fallback is needed when Full Access is denied. Android: same app process, so this is trivial. |
| `backup-restore` | Backup and restore | Export and import settings, learned words, custom layouts, themes and clipboard pins as a file, with safe archive handling. | HeliBoard, FlorisBoard | P1 | M | Validate archives on import (HeliBoard 4.0 fixed a backup-restore issue with manipulated zip files). |
| `cloud-sync` | Cross-device sync | Sync settings, the personal dictionary and optionally the clipboard across devices, ideally through user-owned storage rather than a vendor account. | SwiftKey, Gboard | P3 | L | Conflicts with the offline stance. It could run in the containing app only (iCloud / Drive / WebDAV) without giving the keyboard network access. |

### 6.15 Extensibility (`extensibility`, 2)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `extension-packages` | Extension packages | Installable data packages bundling layouts, themes, dictionaries and emoji data (similar to FlorisBoard .flex or Keyman .kmp). | FlorisBoard, AnySoftKeyboard, Keyman, fcitx5-android | P2 | L | iOS forbids loading executable plugins, so packages must be data-only on both platforms for parity. |
| `automation-integration` | Automation and macros | Keys that run user macros or fire intents, and integration with automation apps. | CleverKeys | P3 | M | Android-only in practice (intents, Tasker). |

### 6.16 Hardware keyboard (`hardware-keyboard`, 2)

| ID | Feature | Description | Seen in | P | Effort | Platform note |
|---|---|---|---|---|---|---|
| `hardware-keyboard-mode` | Hardware keyboard companion mode | When a physical keyboard is attached, hide the soft keys and keep only the suggestion or candidate bar (and CJK candidate window). | HeliBoard, fcitx5-android, Gboard | P1 | M | iOS: third-party keyboards get little or no hardware-key input (verify with platform research). |
| `hardware-layout-mapping` | Hardware layout mapping and shortcuts | Map physical keys to the selected language layout (for example Cyrillic on a US keyboard) and support language-switch shortcuts such as Ctrl+Space. | AnySoftKeyboard, fcitx5-android | P2 | M | Android: onKeyDown in InputMethodService. iOS: likely not feasible for extensions (verify). |


---

## 7. Sources

GitHub metadata (stars, licences, push dates, releases, issue reaction counts) was read directly from the GitHub REST API via `gh` on 2026-09-27.

### Android keyboards

- FlorisBoard
  - Repo: https://github.com/florisboard/florisboard
  - Roadmap: https://github.com/florisboard/florisboard/blob/main/ROADMAP.md
  - v0.5.0 release: https://github.com/florisboard/florisboard/releases/tag/v0.5.0
  - v0.5.2 release: https://github.com/florisboard/florisboard/releases/tag/v0.5.2
  - v0.6.0-alpha01 release: https://github.com/florisboard/florisboard/releases/tag/v0.6.0-alpha01
  - Addons Store: https://beta.addons.florisboard.org
  - Emoji search issue: https://github.com/florisboard/florisboard/issues/45
- k3lp (CLDR Keyboard 3.0 in KMP): https://codeberg.org/k3lp/k3lp
- Unicode CLDR Keyboard 3.0 (UTS #35 Part 7): https://www.unicode.org/reports/tr35/tr35-keyboards.html
- HeliBoard
  - Repo: https://github.com/HeliBorg/HeliBoard
  - Layouts documentation: https://github.com/HeliBorg/HeliBoard/blob/main/layouts.md
  - v4.0-alpha1 release: https://github.com/HeliBorg/HeliBoard/releases/tag/v4.0-alpha1
  - v4.1 release: https://github.com/HeliBorg/HeliBoard/releases/tag/v4.1
  - Gesture library project issue: https://github.com/HeliBorg/HeliBoard/issues/2226
  - Background gesture data gathering wiki: https://github.com/HeliBorg/HeliBoard/wiki/Background-Gesture-Data-Gathering
  - NLnet project page: https://nlnet.nl/project/GestureTyping/
- AOSP dictionaries (Codeberg): https://codeberg.org/Helium314/aosp-dictionaries
- OpenBoard: https://github.com/openboard-team/openboard
- AnySoftKeyboard
  - Repo: https://github.com/AnySoftKeyboard/AnySoftKeyboard
  - 1.13-r1 release: https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases/tag/1.13-r1
  - Gesture classifier PR: https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/1870
- Fossify Keyboard: https://github.com/FossifyOrg/Keyboard
- Unexpected Keyboard
  - Repo: https://github.com/Julow/Unexpected-Keyboard
  - 2.0.0 release: https://github.com/Julow/Unexpected-Keyboard/releases/tag/2.0.0
  - Custom layouts documentation: https://github.com/Julow/Unexpected-Keyboard/blob/master/doc/Custom-layouts.md
  - cdict: https://github.com/Julow/cdict
- Thumb-Key: https://github.com/dessalines/thumb-key
- Hacker's Keyboard: https://github.com/klausw/hackerskeyboard
- AOSP LatinIME: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
- FUTO Keyboard
  - GitHub mirror: https://github.com/futo-org/android-keyboard
  - Primary repo: https://gitlab.futo.org/keyboard/latinime
  - Product site: https://keyboard.futo.tech/
  - FAQ: https://docs.keyboard.futo.tech/troubleshooting/faq
  - Languages and models docs: https://docs.keyboard.futo.tech/settings/languagesmodels
  - 0.1.28 release: https://github.com/futo-org/android-keyboard/releases/tag/0.1.28
  - 0.1.29 release: https://github.com/futo-org/android-keyboard/releases/tag/0.1.29
  - 0.1.30 release: https://github.com/futo-org/android-keyboard/releases/tag/0.1.30
  - llama.cpp announcement issue: https://github.com/ggml-org/llama.cpp/issues/8204
- FUTO Swipe
  - Site: https://swipe.futo.tech/
  - Dataset: https://huggingface.co/datasets/futo-org/swipe.futo.org
  - Inference library: https://gitlab.futo.org/keyboard/swipe-library
- Trime
  - Repo: https://github.com/osfans/trime
  - trime.yaml documentation: https://github.com/osfans/trime/wiki/trime.yaml-%E8%A9%B3%E8%A7%A3
- librime: https://github.com/rime/librime
- fcitx5-android: https://github.com/fcitx5-android/fcitx5-android
- Keyman
  - Repo: https://github.com/keymanapp/keyman
  - Home page: https://keyman.com/
  - Downloads: https://keyman.com/en/downloads/
  - Roadmap (May 2025): https://blog.keyman.com/2025/05/keyman-roadmap-may-2025/
  - Update (19 June 2026): https://blog.keyman.com/2026/06/keyman-update-for-19-june-2026/
  - Lexical models guide: https://help.keyman.com/developer/current-version/guides/lexical-models/intro/index
  - Keyman Engine for Android: https://help.keyman.com/developer/engine/android/18.0/
- 8VIM: https://github.com/8VIM/8VIM
- Simple Keyboard (rkkr): https://github.com/rkkr/simple-keyboard
- Urik
  - Repo: https://github.com/urikdev/Urik
  - AlternativeTo entry: https://alternativeto.net/software/urik-keyboard/about/
- CleverKeys: https://github.com/tribixbite/CleverKeys
- LeanType: https://github.com/LeanBitLab/LeanType
- Traditional T9: https://github.com/sspanak/tt9
- Sayboard: https://github.com/ElishaAz/Sayboard
- Transcribro: https://github.com/soupslurpr/Transcribro
- EweSticker: https://github.com/FredHappyface/Android.EweSticker

### iOS keyboards and platform docs

- KeyboardKit
  - Repo: https://github.com/KeyboardKit/KeyboardKit
  - Licence file: https://github.com/KeyboardKit/KeyboardKit/blob/main/LICENSE
  - 10.0.0 release: https://github.com/KeyboardKit/KeyboardKit/releases/tag/10.0.0
  - Next-word prediction blog post: https://keyboardkit.com/blog/2024/12/03/next-word-prediction
  - Dictation feature page: https://keyboardkit.com/features/dictation
  - Pro page: https://keyboardkit.com/pro
- Hamster: https://github.com/imfuxiao/Hamster
- azooKey
  - Repo: https://github.com/azooKey/azooKey
  - Converter: https://github.com/azooKey/AzooKeyKanaKanjiConverter
- fcitx5-ios: https://github.com/fcitx-contrib/fcitx5-ios
- giellakbd-ios: https://github.com/divvun/giellakbd-ios
- kbdgen: https://github.com/divvun/kbdgen
- Jyutping: https://github.com/yuetyam/jyutping
- Apple App Extension Programming Guide, Custom Keyboard: https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html
- Apple, Configuring open access for a custom keyboard: https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard
- Apple Developer Forums, haptics in keyboard extensions: https://developer.apple.com/forums/thread/63493

### Reviews, commercial context and distribution

- HowToGeek, "4 open-source Android keyboards that rival Gboard" (2025-11-01): https://www.howtogeek.com/open-source-android-keyboards-that-rival-gboard/
- MakeUseOf, "I tested 4 open-source alternatives to Google's Gboard" (2025-11-16): https://www.makeuseof.com/best-open-source-gboard-alternatives-tested/
- AlternativeTo, Gboard alternatives: https://alternativeto.net/software/gboard
- TechRadar on iOS 26 autocorrect: https://www.techradar.com/phones/ios/youre-not-bad-at-typing-ios-26s-autocorrect-is-broken-and-theres-still-no-fix-in-ios-26-2
- MacObserver on the iOS 26 keyboard: https://www.macobserver.com/news/ios-keyboard-is-a-mess-in-ios-26-and-users-have-had-enough/
- BGR on the iOS 26 keyboard: https://www.bgr.com/2006625/why-iphone-users-hate-ios-26-keyboard/
- Google, Android features (September 2025): https://blog.google/products-and-platforms/platforms/android/new-android-features-september-2025/
- Microsoft, SwiftKey Copilot changes FAQ: https://support.microsoft.com/en-us/topic/faqs-for-copilot-changes-in-swiftkey-c02289e6-c5b3-401c-af8d-f6c88409a2d2
- KeyboardKit blog, "Fleksy Shuts Down Their Website" (2026-06-08): https://keyboardkit.com/blog/2026/06/08/fleksy-shuts-down-their-website
- Typewise
  - Wikipedia: https://en.wikipedia.org/wiki/Typewise
  - APKMirror: https://www.apkmirror.com/apk/typewise/typewise-keyboard-big-keys-privacy-swipe/
- F-Droid on Google's developer registration: https://f-droid.org/en/2025/09/29/google-developer-registration-decree.html
- The Hacker News on the 30 Sep verification deadline: https://thehackernews.com/2026/06/google-sets-sept-30-deadline-for.html
