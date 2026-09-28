# MaKeeb: layout formats and per-language key data (survey and recommendation)

- **Date:** 2026-09-28
- **Builds on:** [`open-source-keyboards.md`](open-source-keyboards.md) and [`platform-apis.md`](platform-apis.md) (both 2026-09-27), and [`dictionaries-autocorrect.md`](dictionaries-autocorrect.md) (2026-09-28). This document does not repeat their surveys. It corrects them where needed (§2.3).
- **Scope:** data formats for keyboard layouts (CLDR Keyboard 3.0, FlorisBoard JSON, HeliBoard, AOSP LatinIME, FUTO, AnySoftKeyboard, Unexpected Keyboard, Keyman, Divvun kbdgen), where correct per-language key sets and long-press alternates come from, parsing in `commonMain`, licences, and a format and migration plan for MaKeeb.
- **Board cards served:** `declarative-layout-engine` (P0), plus `language-coverage`, `language-switching`, `latin-layout-variants`, `long-press-alternates`, `rtl-layout-support`, `custom-layout-import`, `cldr-keyboard3-support` and `extension-packages`.
- **Not legal advice.** Licence findings come from the licence files as they read on 2026-09-28. Have them reviewed together with the app licence (dictionaries-autocorrect.md §9) before the first store release.

**Evidence labels used throughout**

| Label | Meaning |
|---|---|
| *(verified)* | Checked against a primary source: a licence file, source code, a spec, a release note or a Maven Central listing. |
| *(measured)* | My own count on the named repository snapshot, with the method stated. Clones were kept in a scratch directory, not in the repo. |
| *(reported)* | From a blog post, issue tracker or search summary. Credible but not checked line by line. |
| *(from reading the code)* | MaKeeb behaviour inferred from its source; not run (no Gradle in this task). |

Repository snapshots read on 2026-09-28: `unicode-org/cldr` main at `396355a`, `florisboard/florisboard` main at `c64826c` (2026-09-23) and tag `v0.5.2`, `HeliBorg/HeliBoard` main at `5bfd34f` (2026-09-27), `codeberg.org/k3lp/k3lp` at `bbae608` (2026-09-18), AOSP LatinIME through the LineageOS mirror (branch `lineage-23.2`), and the GitHub trees of the other projects named in §5.

---

## 1. Executive summary

- **CLDR Keyboard 3.0 is not a source of touch layouts today.** The spec is stable (since CLDR 45) and well designed for hardware keyboards and for character output. But the CLDR repository holds 13 keyboard files. Only two have a touch form: a French *test* file and a Japanese flick layout *(verified)*. The spec puts frame keys (backspace, enter, globe, mode keys) out of scope, has no field-type variants and no direction attribute. k3lp and FlorisBoard had to add non-standard `@k3:action/backspace`-style descriptors to make it work. Keyman, the other implementer, postponed CLDR keyboards on mobile beyond Keyman 19 *(verified: Keyman blog, 2026-06-03)*. k3lp, the Kotlin parser, is pre-alpha and publishes no Apple targets (v0.0.1: JVM, Android, JS, Wasm) *(verified)*.
- **FlorisBoard dropped its own JSON format on 2026-09-23.** PR #3340 ("Keyboard3 integration, part 1") replaced it with Keyboard 3.0 through k3lp *(verified)*. The JSON format now lives on only in FlorisBoard 0.5.x and in HeliBoard's GPL-3.0 dialect. Its design is still the right one: letters-only "characters" layouts, a separate "mod" layout that adds shift, backspace and the bottom row, and per-language popup mappings.
- **The best licence-clean data is AOSP LatinIME and FUTO's layout repository.** AOSP has 41 language layouts, 88 subtypes and per-locale long-press sets for 70 locale entries, all Apache-2.0 *(verified, measured)*. `futo-org/futo-keyboard-layouts` is **Apache-2.0**, even though FUTO Keyboard itself is not open source. It has 167 YAML layouts and a language map covering 154 language codes, mostly converted from AOSP *(verified, measured)*. Unexpected Keyboard's layout files are **CC0** (all but two), even though the app is GPL-3.0 *(verified)*. CLDR exemplar characters (Unicode-3.0) are the right tool to check and complete alternates.
- **Recommendation: a small MaKeeb JSON schema, not an adopted format.** The data defines only the character rows of the letters page and per-language data (alternates, punctuation, digits, TLDs, preferred layouts). The engine keeps building everything the geometry rules depend on: the number row, the shift slot, backspace, the mode keys, the field-dependent bottom row and the symbols pages. Every rule in `ModeSwitchGeometryTest` is then true by construction, because the data cannot express a frame key. Converters (build-time scripts) import from AOSP, FUTO, FlorisBoard 0.5 and CLDR. Parse with kotlinx.serialization JSON in `:engine:layout`. No XML at runtime.
- **Split layout from language.** Today alternates are tied to the layout id (QWERTZ gets German, AZERTY French). Alternates, punctuation and digits belong to the *language*, so a German user on QWERTY gets German alternates. This is what AOSP, FlorisBoard, HeliBoard and FUTO all do.
- **Migration keeps both test classes green by construction.** Stage A reproduces today's six layouts as data and adds a parity test (`data.layout(m, o) == legacy.layout(m, o)` over every mode and option). Stage B switches `BuiltInLayoutProvider` to the data without renaming it. Languages, symbols-as-data, RTL and import follow in later stages (§9.7).
- **Licence risks.** Do not copy HeliBoard layout or `locale_key_texts` files (GPL-3.0; take the AOSP originals instead). Do not use Thumb-Key (AGPL-3.0), FUTO Keyboard's app code (source-available) or Unexpected Keyboard's `latn_neo2.xml` and `latn_qwertz.xml` (GPL-3.0). Everything recommended is Apache-2.0, MIT, CC0 or Unicode-3.0, and needs a notice (§8).

---

## 2. Where MaKeeb is today

### 2.1 The model *(from reading the code)*

- `KeyboardLayout(id, mode, rows, widthUnits)` holds `KeyRow(keys, heightWeight)`, and `Key(action, label, width, style, alternates, hint, caption, longPressAction)`. Widths are in key units (a letter is 1).
- `LayoutDsl.kt` builds rows: `chars(...)` for character keys with alternates and hints, and `shift()`, `backspace()`, `mode()`, `globe()`, `emoji()`, `space()`, `enter()` for frame keys.
- `BuiltInLayouts` hard-codes six letter arrangements (QWERTY, QWERTZ, AZERTY, Dvorak, Colemak, Workman) as three strings each, three alternate maps, the symbols and more-symbols pages, the number pad, the phone pad and the field-dependent bottom row.
- `BuiltInLayoutProvider` caches by `(KeyboardMode, LayoutOptions)`. `LayoutOptions` has `letterLayoutId`, `numberRow`, `switchKey` and `variant` (Text, Email, Url). `InputEngine.layoutFor` fills them from preferences, the host and `EditorAttributes.fieldType`.
- `LayoutGeometry` places keys. A row wider than `widthUnits` narrows only its `KeyStyle.Character` keys, so frame keys keep their size.
- Callers outside the module: `InputEngine`, `KeyboardRuntimeModule` and `MainViewController` (Koin bindings), `SettingsViewModel` (`letterLayouts`), and tests in `engine/touch`, `engine/input` and `shared/keyboard`, all through `BuiltInLayoutProvider()` with no arguments.

### 2.2 The rules the format must keep

| Rule | Where it lives today | Guarding test |
|---|---|---|
| Letters, symbols and more-symbols share one row structure and the field's bottom row; no shared key moves | `symbols()`, `symbolsMore()`, `bottomRow()`, `symbolsBottomRow()` | `ModeSwitchGeometryTest`: `theBottomRowIsTheSameOnEveryPage`, `everyPageHasTheSameRowsAtTheSameHeights`, `backspaceAndTheShiftSlotStayPut`, `otherLetterLayoutsKeep…` |
| Optional number row at 0.8 height | `numberRow()`, `NUMBER_ROW_HEIGHT_WEIGHT` | `LayoutTest.numberRowIsShorterThanLetterRows`, `ModeSwitchGeometryTest.theDigitsLineUp…` |
| Field variants change only a few bottom-row keys | `bottomRow(variant)` | `LayoutTest.emailAndUrlFieldsGetTheirKeys…` |
| Long-press alternates, with digit hints on the top row when there is no number row | `chars(alternates, hints)`, `digitHints()` | `LayoutTest.alternatesFollowTheLayoutsLanguage`, `numberRowAddsARowAndDropsHints` |
| Long-press actions (globe lists keyboards, ABC opens the number pad) | `Key.longPressAction`, `globe()`, `symbolsBottomRow()` | `LayoutTest.holdingABC…`, `theGlobeKeyListsInputMethodsWhenHeld` |
| Captions (phone pad) | `RowBuilder.keypad()` | `LayoutTest.phonePadShowsKeypadLetters…` |
| Rows wider than 10 units narrow only their character keys | `LayoutGeometry.keyUnits()` | `ModeSwitchGeometryTest.dvoraksLongBottomLetterRowNarrowsOnlyItsLetters` |

All seven rules are about the *frame* around the character keys, not about which characters sit where. That is why the recommendation (§9) keeps the frame in Kotlin and moves only characters and language data into files.

Three further observations *(from reading the code)*:

1. **Alternates follow the layout, not the language** (`alternatesFor(layoutId)`). A German speaker on QWERTY gets the English set, and a Swiss French speaker on QWERTZ gets German first.
2. **`Key.displayAlternates` upper-cases with `String.uppercase()`.** Under shift, the `ß` alternate on `s` becomes `SS` (Kotlin's locale-independent full case mapping), not `ẞ`. Turkish and Azerbaijani `i` would become `I` instead of `İ`. The data format needs explicit shifted forms (FlorisBoard's `case_selector` and FUTO's `case` key exist for exactly this).
3. **Engine modules have no resource loading.** The precedent is the emoji catalogue: `scripts/generate-emoji-data.py` writes the data into Kotlin raw-string constants, and `BundledEmojiCatalog` indexes them lazily, because materialising the whole catalogue cost the iOS extension about 18 MB. The version catalog already declares kotlinx.serialization 1.11.0 and its plugin (`apply false` at the root), but no module uses it yet.

### 2.3 Corrections to earlier notes

These were found during this research. The files themselves were not edited.

1. **`open-source-keyboards.md` §3.1 (FlorisBoard)** says "JSON layouts, a format HeliBoard also reads" and that k3lp "could be reused directly". Both need updating.
   - FlorisBoard `main` removed `LayoutManager` and the JSON merge code on 2026-09-07 (commit `ba75d40`, "Remove ComputingEvaluator, LayoutManager, and other dead code"). It then merged PR #3340 on 2026-09-23, which states "Drop support for FlorisBoard json layouts" *(verified)*. The JSON assets are still in the tree, but nothing reads them.
   - k3lp v0.0.1 (Maven Central, 2026-09-09) publishes `android`, `jvm`, `js` and `wasm-js` artifacts only. Its build logic declares no Apple targets *(verified)*. It cannot be linked into the iOS extension today. Its dependencies do ship iOS artifacts (kudzu, kotlin-codepoints, coroutines), so adding the targets upstream looks feasible.
2. **The board card `cldr-keyboard3-support`** repeats "could be reused directly". The same caveat applies.
3. **`open-source-keyboards.md` §3.1 and `dictionaries-autocorrect.md` §9.5** list Unexpected Keyboard as GPL-3.0. That is true for the app, but `srcs/layouts/LICENSE` puts every layout XML except `latn_neo2.xml` and `latn_qwertz.xml` under **CC0 1.0** *(verified)*. The subtype table in `res/xml/method.xml` (language → default layout, extra keys) is not covered by that licence and stays GPL-3.0.
4. **`dictionaries-autocorrect.md` §9.5** lists FUTO Keyboard under "Do not use". That holds for the app (FUTO Source First 1.1). The separate `futo-org/futo-keyboard-layouts` repository is **Apache-2.0** *(verified: `LICENSE`)*.
5. **`BuiltInLayouts` KDoc** points to a dashboard card `layout-definition-format`. No such card exists; the card is `declarative-layout-engine`.

---

## 3. CLDR Keyboard 3.0 (LDML keyboards, UTS #35 Part 7)

### 3.1 Status

| Item | Finding |
|---|---|
| Spec status | "Keyboard 3.0" arrived in CLDR 45, "advancing from Tech Preview to stable" (CLDR 45 release note) *(verified)*. The latest released spec is revision 78 (CLDR 48); revision 79 is in development (`docs/ldml/tr35-versions.yml`) *(verified)*. |
| CLDR 48 (2025-10-29) | Keyboard change: `keyboard3@conformsTo` now allows "48". Nothing else *(verified)*. |
| CLDR 49 (planned 2026-10; beta 2026-09-22 and 2026-09-25) | Adds Egyptian Hieroglyphic, Gandhari, Sanskrit and Classical Tibetan keyboards. "Spec clarifications were made for the `display`, `layer`, and backspace `transform` elements." *(verified: draft release note, GitHub release tags)* |
| Compatibility | The 3.0 DTD is not compatible with pre-45 keyboard files; the old `ldmlKeyboard.dtd` "will not be updated" *(verified: spec §Compatibility Notice)*. |
| Implementations | Keyman shipped LDML for desktop in Keyman 17. "This epic provides support for CLDR keyboards on mobile. This will not land in version 19" *(verified: Keyman blog, 2026-06-03)*. FlorisBoard `main` uses it through k3lp (§3.4). |

### 3.2 Data in CLDR *(measured: `keyboards/3.0` at `396355a`)*

| File | Form | Notes |
|---|---|---|
| `bn.xml`, `egy-Egyp-t-k0-qwerty.xml`, `mt-t-k0-47key.xml`, `pgd-Khar-t-k0-qwerty.xml`, `sa-Deva-t-k0-qwerty.xml`, `xct-Tibt-t-k0-qwerty.xml` | `us` (hardware) | Bengali, plus historical or liturgical scripts |
| `fr.xml`, `mt.xml`, `pcm.xml` | `iso` (hardware) | French, Maltese, Nigerian Pidgin |
| `ja-Latn.xml` | `jis` | Hardware |
| `pt-t-k0-abnt2.xml` | `abnt2` | Brazilian hardware |
| `ja-Hira-t-k0-flicks.xml` | **touch** | Japanese kana flick layout |
| `fr-t-k0-test.xml` | **touch** + `iso` | A test file. Its touch layers carry `<!--TODO: + bksp -->`, and `enter` is declared as a `gap` with "TODO: need discussion" |

So CLDR has **no production touch layout for any of the top 30 languages**. Its value to MaKeeb is the locale data around keyboards (§6.3), not the keyboards.

### 3.3 How it expresses layers, long-press, flicks, variants and RTL *(verified: spec text and DTD)*

- **Keys** are declared once in `<keys>` with an `id`, an `output`, and optional `longPressKeyIds`, `longPressDefaultKeyId`, `multiTapKeyIds`, `flickId`, `layerId` (switch layers), `gap`, `width` and `stretch` (space bar). Shared key sets come in through `<import base="cldr" path="45/keys-Zyyy-punctuation.xml"/>`.
- **Layers:** `<layers formId="touch" minDeviceWidth="…mm">` holds `<layer id="base">` (required), plus any other ids (`shift`, `numeric`, `symbol`), each with `<row keys="a z e r t y"/>`. Hardware forms use `modifiers="shift"` instead of ids. Shift on touch is simply another layer that keys switch to; there is no implied upper-casing.
- **Long-press:** `longPressKeyIds` lists other key ids, and `longPressDefaultKeyId` picks the default, which "could be different than the first element".
- **Flicks:** `<flick id>` with `<flickSegment directions="nw se" keyId="…"/>`, eight compass directions, multi-segment paths allowed.
- **Keycap display:** `<displays>` maps an output or a key id to a keycap label. `<displayOptions baseCharacter="x"/>` renders combining marks.
- **Transforms:** `<transforms type="simple">` for dead keys and reordering, and `type="backspace"` for backspace rules. They use UnicodeSet and regex-like syntax, variables and markers. Input is normalised to NFD unless `<settings normalization="disabled"/>`.
- **Field variants (e-mail, URL):** not in the spec; nothing in the text covers input types.
- **RTL:** no direction attribute; direction follows from the locale's script.
- **Frame keys:** explicitly a non-goal: "Platform-specific frame keys such as Fn, Numpad, IME swap keys, and cursor keys are out of scope."
- **Run-time use:** also a non-goal: "LDML is explicitly an interchange format, and so it is expected that data will be transformed to a more compact format for use by a keystroke processing engine."

### 3.4 Tooling and implementations

| Tool | What it is | Licence | Status |
|---|---|---|---|
| DTD, XSD, ABNF (`keyboards/dtd`, `keyboards/abnf`) and conformance test files (`keyboards/test`) | Validation inputs | Unicode-3.0 | Maintained with CLDR |
| Keyman `kmc-ldml` | Compiles LDML to Keyman's binary format (TypeScript) | MIT | Desktop since Keyman 17; mobile postponed |
| **k3lp** (codeberg.org/k3lp/k3lp) | Kotlin Multiplatform parser, model compiler and runtime (`K3InputMethod`, `K3KeystrokeEngine`); its own XML parser (`lib/xml`, on kudzu) and Unicode normaliser (`lib/text`) in `commonMain`; web playground at play.k3lp.org | Apache-2.0 | README: "Pre-alpha stage". v0.0.1 on Maven Central (2026-09-09), 18 stars. About 18,600 lines of `commonMain` Kotlin *(measured)*. Targets: JVM, Android, JS, Wasm; **no Apple targets** |
| FlorisBoard `keyboard3` | Uses k3lp and its `@k3:action/backspace` and `@k3:action/enter` descriptors, adds its own `@fl:icon/…` and `@fl:action/…`, and predefines the layers `base`, `shift`, `numpad`, `telpad` and `numrow` | Apache-2.0 | Merged 2026-09-23. Subtypes, shift state, flicks and reorder transforms listed as "currently broken" until part 2 and 3 |

k3lp's own `K3Descriptor` KDoc says why the extension exists: "descriptors are not defined in the keyboard3 spec … (e.g. how to describe that a key should emit a backspace), the k3lp library relies on descriptors to build a fully functional keyboard." A Keyboard 3.0 touch layout that works in FlorisBoard therefore does not work unchanged in another conformant implementation.

FlorisBoard's `qwertz.xml` (318 lines) shows the cost of "one file per language" in this format: each keyboard file repeats its own symbol layers, numeric layer, bottom row and upper-case keys (`<key id="A" output="A" longPressKeyIds="A-umlaut …"/>`).

### 3.5 What a `commonMain` parser would need

1. **An XML parser without JVM libraries.** Options:
   - **xmlutil** (pdvrieze): Apache-2.0, 1.0.2 (2026-08-25), publishes iOS arm64 and simulator arm64 artifacts, works with kotlinx.serialization *(verified: Maven Central)*.
   - **k3lp `lib/xml`**: pure Kotlin, but not published for Apple targets.
   - **A hand-written subset parser**: the files use elements, attributes, comments, `&` entities and `\u{…}` escapes only.
   - **Pre-conversion at build time:** parse XML on the JVM (the JDK's own parser) and emit JSON or Kotlin. This needs no XML code in the app at all.
2. **Import resolution:** bundle CLDR's `import` files (`keys-Zyyy-punctuation.xml`, `keys-Zyyy-currency.xml`, `keys-Latn-implied.xml`, `scanCodes-implied.xml`).
3. **Variables:** `${var}` strings, `$[set]` sets, and UnicodeSets.
4. **A Unicode normaliser.** Kotlin/Native has no `java.text.Normalizer`. k3lp carries its own (`lib/text` is 392 KB of source).
5. **The transform engine** (simple and backspace, markers, reorder groups) for anything beyond plain output.
6. **A MaKeeb profile on top:** what backspace, enter, globe, the mode keys and the field variants are. The spec does not say.

Items 3–5 are the bulk of k3lp's work. They matter for complex scripts (Indic reordering, dead keys) and not at all for the 30 major-language layouts, which are plain output plus long-press.

### 3.6 Licence

CLDR data and code are under the **Unicode License v3** (`SPDX: Unicode-3.0`), a permissive licence that needs the copyright and permission notice in the copies or in the documentation *(verified: `LICENSE`)*. The same `LICENSE` puts the *spec text itself* (UTS #35) under stricter terms (no public redistribution of modified copies). That matters only if MaKeeb republishes the spec, not for using the data.

### 3.7 Fit for MaKeeb

| For | Against |
|---|---|
| The only standard; FlorisBoard and Keyman are converging on it | No touch data for major languages to adopt |
| Long-press, flicks and multi-tap are well specified | Frame keys, field variants and direction are out of scope; every implementation extends it differently |
| Transforms cover dead keys and complex scripts, which MaKeeb will want for `indic-complex-script-input` | Full conformance needs XML, imports, UnicodeSets, a normaliser and a transform engine in the iOS extension (memory budget) |
| Unicode-3.0 is permissive | Per-file layers let a keyboard author move frame keys and change row counts, which is exactly what `ModeSwitchGeometryTest` forbids |

**Verdict:** keep `cldr-keyboard3-support` at P2 as an *importer* (build-time or companion-side) into MaKeeb's own format. Revisit a runtime implementation when k3lp publishes Apple targets and CLDR has touch layouts for major languages.

---

## 4. FlorisBoard's layout JSON (0.5.x)

### 4.1 Status

- Used through FlorisBoard v0.5.2 (2025-11-28) and the 0.6 alphas up to alpha02 (open-source-keyboards.md §2).
- Dropped on `main` by PR #3340 (merged 2026-09-23). The PR says: "Due to the major rework it was not possible to keep support for both FlorisBoard json layouts and keyboard3 layouts at the same time", and that FlorisBoard intends "to provide only minimal baseline layouts within the APK itself, and offer all other keyboards … via our addons store" *(verified)*.
- HeliBoard still reads it, with its own extensions (§5.1).

### 4.2 Schema *(verified: v0.5.2 `KeyData.kt`, `TextKeyData.kt`, `PopupSet.kt`, `Subtype.kt`, assets)*

- **A layout file** is a JSON array of rows; each row is an array of key objects.
- **Key classes** (the `"$"` discriminator):
  - `text_key` (default) and `auto_text_key` (changes case with shift): `code` (code point, or a negative special code), `label`, `type` (`character`, `modifier`, `enter_editing`, `system_gui`, `placeholder` …), `groupId`, `popup`.
  - `multi_text_key`: `codePoints` array.
  - **Selectors**, which make keys "computed" from state:
    - `case_selector` (`lower`, `upper`), e.g. `ß`/`ẞ` in `de.json`.
    - `shift_state_selector`.
    - `variation_selector` (`default`, `email`, `uri`, `normal`, `password` …), e.g. `,` becomes `@` in e-mail fields.
    - `layout_direction_selector` (`ltr`, `rtl`), e.g. `(` outputs `)` in RTL layouts so the keycap matches bidi mirroring.
    - `char_width_selector`, `kana_selector`.
- **Special codes and labels** are resolved at run time by the `ComputingEvaluator`: `-11` shift, `-7` delete, `-202 view_symbols`, `-227 language_switch`, `-801 … -806 currency_slot_1…6` (the subtype's currency set), and so on.
- **Popups:** `"popup": {"main": key, "relevant": [keys]}`. A **popup mapping** file per language maps a label to a popup set: `{"all": {"a": {"main": "ä", "relevant": [...]}}}`. Special entries such as `~enter` and `~left` attach popups to frame keys. The layout manager also merges "hints" from the symbols layer and the number row into letter popups.
- **Subtypes:** `Subtype(primaryLocale, secondaryLocales, composer, currencySet, punctuationRule, popupMapping, layoutMap)`. `SubtypeLayoutMap` picks one layout per type (`characters`, `symbols`, `symbols2`, `numeric`, `numericAdvanced`, `numericRow`, `phone`, `phone2`). `subtypePresets` in `org.florisboard.localization/extension.json` give each language tag its defaults (73 presets).

### 4.3 How it separates characters, symbols and function keys

This is the part worth copying.

- `characters/qwerty.json` holds **only the three letter rows** (`auto_text_key` q…p, a…l, z…m).
- `charactersMod/default.json` holds the frame:
  - Row 1: `shift`, a `placeholder`, `delete`.
  - Row 2: the bottom row (`view_symbols`, a `variation_selector` for `,`/`@`/`/`, `language_switch`, media, space, `.`, enter).
- `LayoutManager.mergeLayouts` (v0.5.2) splices the characters layout's last row into the placeholder, appends the mod layout's remaining rows, and puts the `numericRow` "extension" layout on top when enabled.
- `symbols` and `symbolsMod`, `symbols2` and `symbols2Mod` repeat the same pattern for the symbol pages. A characters layout can name its own modifier (`"modifier": "org.florisboard.layouts:arabic"`), for example to drop shift for Arabic.
- Language data (popups, currency, punctuation rules) is separate from layouts and joined by the subtype.

HeliBoard's "functional key layouts" (`functional_keys.json`: shift, placeholder, delete; then the bottom row) and FUTO's "template keys" (§5.3) use the same idea. MaKeeb's `BuiltInLayouts` already works this way in code: letters come from three strings, and the frame comes from `shift()`, `backspace()` and `bottomRow()`.

### 4.4 Coverage and licence *(measured: the JSON assets still in `main`)*

- 76 characters layout files (9 declared `rtl`), 15 `charactersMod`, 17 `numericRow` (native digits for Arabic, Persian, Devanagari, Bengali, Thai …), 9 `symbols`, 6 `symbols2`, 57 popup-mapping files, 73 subtype presets.
- Languages include en, es, pt, fr, de, it, pl, tr, ru, uk, sv, nb, da, fi, cs, hu, ro, el, ar, he, fa, ur, hi, bn, ta, th, vi, id, ko, ja (JIS). Dutch, Malay, Tagalog and Swahili have no preset. Thai and Tamil have layouts but no popup mapping.
- **Licence: Apache-2.0**, both repository-wide and in each extension's metadata (`"license": "apache-2.0"`). Popup mappings list their community authors, which is useful for attribution *(verified)*.

### 4.5 Fit

The *structure* fits MaKeeb well. The *format* does not:

- It is abandoned upstream.
- Selectors and special codes let a file place frame keys and bottom rows anywhere.
- HeliBoard's dialect has diverged (screen-fraction widths, `labelFlags` bitmasks, extra selectors, a different set of special labels).

Use FlorisBoard 0.5 files as an **import source** (Apache-2.0), not as the runtime format.

---

## 5. Other formats

### 5.1 HeliBoard: simple text and JSON *(verified: `layouts.md`, assets, `LICENSE`)*

- **Simple format:** one key per line, `label popup popup …`, with a blank line between rows. `label|text` gives a different output. Labels such as `shift`, `delete`, `comma`, `period`, `$$$` (local currency) and `!icon/…` are special.
- **JSON:** the FlorisBoard format, with "only 'normal' keys" for main layouts. HeliBoard adds `keyboard_state_selector` (`emojiKeyEnabled`, `languageKeyEnabled`, `symbols`, `moreSymbols` …), `labelFlags` and widths as screen fractions (`0.1` = 10% of the screen; `-1` fills). It also has its own `functional_keys.json` with a placeholder merge, as in §4.3.
- **Per-language data** lives in `locale_key_texts/<lang>.txt`:
  - `[popup_keys]`: a letter followed by its long-press keys; `%` marks the "important" group.
  - `[labels]`: localised mode labels, comma and question mark.
  - `[number_row]`: native digits.
  - `[extra_keys]`: extra letters appended to a Latin layout.
  - `[tlds]`.
- **Coverage:** 82 main layouts (33 JSON, 49 simple) and 81 `locale_key_texts` files *(measured)*. Many are converted from AOSP.
- **Licence:** **GPL-3.0** for the repository. `LICENSE-Apache-2.0` is provided because the app derives from AOSP, but individual layout files do not state their provenance. **Do not copy these files.** Take the AOSP originals (§5.2) and reproduce the structure, not the text.
- **Fit:** the per-language file design (popups, labels, digits, TLDs) is the model for MaKeeb's language file (§9.3).

### 5.2 AOSP LatinIME: XML layouts and the keyboard text table *(verified: LineageOS mirror)*

- **Layouts:** Android resource XML.
  - `kbd_<name>.xml` includes `rows_<name>.xml`, which includes `rowkeys_<name><n>.xml`. These use `<Key latin:keySpec="…" latin:moreKeys="…"/>`, key styles, `<switch>`/`<case>` on keyboard state, and `keyWidth="fillRight"`.
  - There are 41 language layouts (`kbd_*.xml`, not counting the emoji, symbols, number and phone keyboards): qwerty, qwertz, azerty, spanish, nordic, swiss, south_slavic, east_slavic, serbian_qwertz, turkish, turkish_f, greek, arabic, farsi, hebrew, hindi, hindi_compact, marathi, nepali (2), bengali (2), tamil, telugu, kannada, malayalam, sinhala, thai, lao, khmer, georgian, armenian_phonetic, mongolian, uzbek, bulgarian (3), bepo, colemak, dvorak and pcqwerty *(measured)*.
- **Language to layout:** `method.xml` has 88 subtypes with `KeyboardLayoutSet=<name>` per locale *(measured)*.
- **Long-press data:** `KeyboardTextsTable.java`, generated from `tools/make-keyboard-text/res/values-*/donottranslate-more-keys.xml`, has per-locale `morekeys_a`, `morekeys_e`, `keyspec_currency`, `keyspec_nordic_row1_11` and similar entries.
  - 70 locale entries plus `DEFAULT` (including `hi_ZZ`, `sr_ZZ` and three Tamil regions) *(measured)*. Examples: German `morekeys_a` = `ä,%,â,à,á,æ,ã,å,ā`; French `à,â,%,æ,á,ä,ã,å,ā,ª`.
  - `%` marks where layout-specific extras are inserted.
  - `!text/…` references other entries, and `!fixedColumnOrder!N`, `!needsDividers!` and `!hasLabels!` are display flags.
- **Frame:** shared (`row_qwerty4.xml` is the common bottom row). Hebrew and Arabic have **no shift key**, and backspace fills the right end of row 3 (`rows_hebrew.xml`, `rows_arabic.xml`). Thai has **four letter rows** (`rows_thai.xml`).
- **Licence: Apache-2.0** in every file header, including LineageOS/CyanogenMod additions such as `rowkeys_bepo1.xml` (2015) and `kbd_turkish_f.xml` (2025) *(verified)*.
- **Fit:** the best **primary source** for launch languages. The XML is too Android-specific to parse at run time, but converting it at build time is proven: FUTO's repository (§5.3) is largely such a conversion, down to comments like `# rowkeys_hebrew1.xml`.

### 5.3 FUTO keyboard-layouts: YAML *(verified: `LICENSE`, `LayoutSpec.md`, `mapping.yaml`)*

- **Format:** `name`, optional `languages`, and `rows`. Each row is `letters:`, `numbers:` or `bottom:`, written as a space-separated string or a list. `[a, ą]` means a key with long-press keys. `{type: case, normal, shifted}` gives explicit shifted forms. `label|code` key specs work as in AOSP.
- **"Template keys (automatic shift and backspace)":** "The keyboard parser automatically prepends `$shift` and appends `$delete` to the final letter row" unless a bottom row or those keys are given. "A default number row and bottom row will be added if they are not explicitly defined."
- **"Automatic moreKeys":** accented letters come from the language, not the layout, and `moreKeyMode: OnlyExplicit` turns this off.
- **Other options:** `layoutSetOverrides` for symbols, number and phone pages, `numberRowMode`, `rowHeight`, `altPages` and `useZWNJKey`.
- **Coverage:** 167 layout YAML files (plus `mapping.yaml` and `names.yaml`); `mapping.yaml` maps 154 language codes to layout lists (for example `de: [german, qwertz, swiss, qwerty …]`, `ar: [arabic, arabic_pc, lulua]`, `iw: [hebrew, hebrew-staggered]`) *(measured)*.
- **Licence: Apache-2.0** (repository `LICENSE`; last push 2026-07-28).
- **Caveat:** layouts still reference AOSP text-table entries (`"!text/keyspec_nordic_row1_11"`), so a converter must resolve them against `KeyboardTextsTable`.
- **Fit:** the closest existing format to what MaKeeb needs, with letters-only rows, an engine-built frame and language-driven alternates. It is also a clean import source. As a runtime format it allows too much (explicit `bottom` rows, relocatable `$shift`/`$delete`, 1–8 letter rows, per-row heights). And YAML would need a parser in the iOS extension. Use it as an import source and design reference.

### 5.4 AnySoftKeyboard: Android `Keyboard` XML *(verified)*

- **Format:** `<Keyboard><Row><Key android:codes android:keyLabel android:popupCharacters android:popupKeyboard ask:hintLabel ask:shiftedCodes ask:shiftedKeyLabel/>`, based on the deprecated `android.inputmethodservice.Keyboard` format. Widths are `%p` of the screen. Each pack's `*_keyboards.xml` declares keyboards with `defaultDictionaryLocale` and `layoutResId`.
- Alternates are baked into each key (`popupCharacters="äàáâãåæą"`). Frame keys are ordinary keys with negative codes (`-1` shift, `-5` delete).
- **Coverage:** 60 language-pack directories (for example german, french, hebrew, arabic, persian, urdu, hindi, thai, greek, russian2, ukrainian, polish) *(measured)*.
- **Licence: Apache-2.0** repository-wide. Only some packs' *dictionaries* carry their own licence files (Wikipedia-derived lists, for example) *(verified)*.
- **Fit:** a secondary source for specific languages. Its alternates mix digits and symbols per key, so they need curation.

### 5.5 Unexpected Keyboard: XML *(verified)*

- **Format:** `<keyboard name script bottom_row …><row><key c="q" ne="1" se="loc esc" width shift/>`. Each key has a centre symbol plus up to eight corner or edge symbols reached by swiping. `loc` symbols appear only when a language enables them.
- **Coverage:** 90 layout files by script prefix: `latn_*` (qwerty variants for about 30 locales, qwertz, azerty, bepo, colemak, dvorak, workman), `cyrl_*`, `arab_*`, `hebr_*`, `deva_*`, `beng_*`, `grek_qwerty`, `hang_dubeolsik_kr`, `tamil_default`, `urdu_phonetic_ur` and more *(measured)*.
- **Licence:** layouts **CC0 1.0**, except `latn_neo2.xml` and `latn_qwertz.xml`. App and `res/xml/method.xml` (the language → default layout table) are **GPL-3.0**.
- **Fit:** the letter arrangements are usable reference data. The corner symbols belong to a different interaction model and should not be imported as long-press alternates.

### 5.6 Keyman: `.kmn` rules and `.keyman-touch-layout` JSON *(verified)*

- **Touch layout JSON:** `phone`/`tablet` → `layer[]` (`default`, `shift`, `numeric`, `symbol` …) → `row[]` → `key[]`.
  - Key fields: `id` (`K_Q`, or `U_00E4` for a code point), `text`, `sp` (special style), `width` (in hundredths), `sk` (long-press subkeys), `flick`, `multitap`, `nextlayer` and `hint`.
  - The output of `K_*` keys comes from the `.kmn` rule file, so a touch layout is not self-contained.
- **Coverage:** 980 touch layouts in `keymanapp/keyboards` (874 under `release/`, 106 under `experimental/`). 186 of the released ones are `basic_kbd*` keyboards "generated from template" from Windows desktop layouts (tablet form only; for example `basic_kbdgr` lists Windows and macOS as platforms). The strength is minority and complex scripts (`sil_*`, `fv_*`, `gff_*` …) *(measured)*.
- **Licence:** per-keyboard `LICENSE.md`. A sample of 58 of 938 was **MIT** in every case *(measured)*; copyright is usually SIL International or the author. The Keyman app is MIT.
- **Fit:** a later source for minority languages, through a converter that evaluates simple `.kmn` rules. Not for the launch set.

### 5.7 Divvun kbdgen: YAML *(verified)*

- Per-platform sections (`android`, `iOS`, `macOS`, `windows`, `chromeOS`), each with `layers:` written as whitespace-separated grids and `\s{shift:1.25}` / `\s{backspace}` / `\s{spacer:0.25}` for special keys. A top-level `longpress:` map, `deadKeys`, `transforms` and `keyNames` (localised space and return labels).
- The Android sections of `giellalt/keyboard-smj` define only three letter rows; the bottom row comes from the generator.
- **Licence:** kbdgen is Apache-2.0; the 78 `giellalt/keyboard-*` repositories are MIT per GitHub metadata *(verified)*. Coverage is Sámi, Uralic and other minority languages.
- **Fit:** a good syntax reference (compact grids, a separate long-press map). A later data source for those languages.

### 5.8 Others

- **Thumb-Key:** Kotlin code, not data, and AGPL-3.0. Not usable.
- **FUTO Keyboard app:** FUTO Source First 1.1. Use only the separate Apache-2.0 layouts repository (§5.3).
- **KeyboardKit:** closed source since 10.0. Not usable.
- **xkeyboard-config (X11/XKB):** hardware layouts for hundreds of languages under permissive HPND/MIT-style notices *(verified: `COPYING`)*. Relevant later for `hardware-layout-mapping`, not for touch layouts.
- **Apple and Gboard layouts:** reference by observation only (screenshots, device checks). No data to take.

### 5.9 Comparison

| Format | Frame keys in data? | Language data separate? | Field variants | RTL | Coverage (touch) | Licence of data | Runtime fit for MaKeeb |
|---|---|---|---|---|---|---|---|
| CLDR Keyboard 3.0 | Out of scope (needs extensions) | Partly (imports) | None | Implied by locale | 1 test file + 1 Japanese flick | Unicode-3.0 | Poor today; importer later |
| FlorisBoard JSON 0.5 | Separate `*Mod` layouts | Yes (popup mappings, presets) | `variation_selector` | `layout_direction_selector` | 76 layouts | Apache-2.0 | Abandoned upstream; import source |
| HeliBoard simple/JSON | Separate functional layout | Yes (`locale_key_texts`) | Selectors | Selectors | 82 layouts | GPL-3.0 | Do not copy |
| AOSP LatinIME XML | Shared includes | Yes (`KeyboardTextsTable`) | `<switch>` on mode | Layout-specific | 41 layouts, 88 subtypes | Apache-2.0 | Build-time conversion only |
| FUTO YAML | Template keys, auto frame | Yes (auto moreKeys, `mapping.yaml`) | `$contextual` | Per layout | 167 layouts, 154 languages | Apache-2.0 | Close; import source |
| AnySoftKeyboard XML | Keys with negative codes | No (baked per key) | Separate layouts | Per layout | ~60 packs | Apache-2.0 | Import source |
| Unexpected Keyboard XML | Built-in bottom row | Partly (`loc` keys) | No | Per layout | 90 layouts | CC0 (2 files GPL) | Reference data |
| Keyman touch JSON + KMN | Yes, in each file | No | No | Per keyboard | 874 released (mostly minority) | MIT per keyboard | Later, via converter |
| Divvun kbdgen YAML | `\s{…}` specials | `longpress` map | No | Per layout | Minority languages | MIT / Apache-2.0 | Syntax reference |

---

## 6. Where to get correct per-language key sets

### 6.1 Coverage for about 30 major languages *(measured on the snapshots above)*

✓ = present, – = absent. "AOSP alt." is a per-locale entry in `KeyboardTextsTable`. "Floris" counts a subtype preset (layout) and a popup mapping (alt.).

| Language | AOSP layout | AOSP alt. | FUTO | Floris layout / alt. | ASK pack | Unexpected (CC0) | CLDR touch | CLDR exemplars |
|---|---|---|---|---|---|---|---|---|
| English | qwerty | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (us, gb) | – | ✓ |
| Spanish | spanish | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Portuguese (BR, PT) | qwerty | ✓ | ✓ | ✓ / ✓ | ✓ (2) | ✓ (br) | – | ✓ |
| French (FR, CA, CH) | azerty, qwerty, swiss | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (azerty) | test file only | ✓ |
| German (DE, AT, CH) | qwertz, swiss | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (qwertz_de) | – | ✓ |
| Italian | qwerty | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (qzerty) | – | ✓ |
| Dutch | qwerty; azerty (BE) | ✓ | ✓ | – / – | ✓ | – | – | ✓ |
| Polish | qwerty | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Turkish | turkish, turkish_f | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (q, f) | – | ✓ |
| Russian | east_slavic | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Ukrainian | east_slavic | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Swedish, Norwegian, Danish, Finnish | nordic | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Czech | qwertz | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (qwerty, qwertz) | – | ✓ |
| Hungarian | qwertz | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Romanian | qwerty | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Greek | greek | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Arabic (RTL) | arabic | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (pc, alt) | – | ✓ |
| Hebrew (RTL) | hebrew | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (2) | – | ✓ |
| Persian (RTL) | farsi | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Urdu (RTL) | – | – | ✓ | ✓ / ✓ | ✓ | ✓ | – | ✓ |
| Hindi (Devanagari) | hindi, hindi_compact | ✓ | ✓ | ✓ / ✓ | ✓ | ✓ (inscript, phonetic) | – | ✓ |
| Bengali | bengali, bengali_akkhor | ✓ | ✓ | ✓ / ✓ | – | ✓ | – (hardware only) | ✓ |
| Tamil | tamil | ✓ | ✓ | ✓ / – | – | ✓ | – | ✓ |
| Thai | thai (4 letter rows) | ✓ | ✓ | ✓ / – | ✓ | – | – | ✓ |
| Vietnamese | qwerty | ✓ | ✓ (+ Telex, VNI) | ✓ / ✓ | – | ✓ | – | ✓ |
| Indonesian, Malay | qwerty | – (not needed) | ✓ | id only | ✓ (id) | – | – | ✓ |
| Tagalog | spanish | ✓ | ✓ | – | – | – | – | ✓ |
| Swahili | qwerty | ✓ | ✓ | – | – | – | – | ✓ |
| Korean, Japanese, Chinese | – | – | ✓ | ✓ | – | ko | ja flicks | ✓ |

Notes:

- **Korean, Japanese and Chinese** need a composer or a conversion engine (Hangul jamo composition, kana-kanji, pinyin). A layout file alone does not make them work. They belong to the input-method cards (`indic-complex-script-input` and the CJK cards), not to this format.
- **Vietnamese Telex/VNI, Indic phonetic layouts and Thai** reordering need transforms. MaKeeb should start with plain layouts plus long-press, and add a composer interface later.
- **Every language in the table has an Apache-2.0 source** (AOSP or FUTO, often both). None requires HeliBoard or other GPL data.

### 6.2 Long-press alternates: sources and a generation recipe

1. **Start from AOSP `KeyboardTextsTable`** (Apache-2.0): 70 locale entries of `morekeys_<letter>` in the order AOSP chose for each language, with the `%` marker for layout extras. This is the same data HeliBoard's `[popup_keys]` and FUTO's "automatic moreKeys" derive from.
2. **Fill gaps from FlorisBoard 0.5 popup mappings** (Apache-2.0; `main` = the default long-press key, then `relevant`). They add languages AOSP lacks (Urdu, Asturian, Kabyle) and explicit `case_selector` pairs (`ß`/`ẞ`).
3. **Check and complete with CLDR exemplar characters** (Unicode-3.0; `cldr-json` 48.2.2, `characters.json`):
   - `exemplarCharacters` (main) are the letters the language needs. For example, German main `[aä b c … oö … s ß t uü …]` and auxiliary `[áàăâåãā æ ç éèĕêëē …]`; Polish main has `ą ć ę ł ń ó ś ź ż`.
   - **Coverage rule (build-time test):** every *main* exemplar must be reachable on the default layout as a key or an alternate.
   - **Generation rule for missing languages:** group main exemplars, then auxiliary exemplars, under their base letter after canonical decomposition (NFD, strip combining marks). Main exemplars come first, in CLDR order. Letters without a decomposition (`ß`, `æ`, `ø`, `ł`, `œ`, `ð`, `þ`) need a small hand table of "near" base letters (`s`, `a`, `o`, `l`, `o`, `d`, `t`), which is also what AOSP does.
   - Exemplar sets with multi-character sequences (Hungarian `{cs}`, `{dzs}`; Russian stressed vowels `{а́}`) are skipped for alternates and for the coverage check.
4. **Ordering:** CLDR has no frequency order. AOSP's order is curated for each language. Prefer AOSP, and use CLDR order only for generated languages until a dictionary-based frequency order (the language's own packs, dictionaries-autocorrect.md §10.1) can rank them.

### 6.3 Other per-language data from CLDR (Unicode-3.0) *(verified: `cldr-json` 48.2.2)*

| Need | CLDR source | Example |
|---|---|---|
| Comma, question mark and sentence punctuation for the bottom row and alternates | `characters.json` → `punctuation` | Arabic `، ؛ ؟`; Hindi `। ॥` |
| Quotation marks for the `'` and `"` alternates | `delimiters.json` | German `„ “ ‚ ‘`; French `« »` |
| Native digits for the number row and hints | `numbers.json` → `defaultNumberingSystem`, `otherNumberingSystems.native` | Persian default `arabext`; Arabic native `arab`; Hindi native `deva` |
| Language names for the space bar and settings | `localeDisplayNames` (autonyms) | "Deutsch", "العربية" |
| Currency for the symbols page | `currencyData` (region → currency) | de-CH → CHF |

### 6.4 Structural differences some scripts need

Found in AOSP's layouts *(verified)*:

- **No shift key** in caseless scripts. Hebrew and Arabic rows start at the left edge and backspace fills the right end of row 3.
- **Eleven keys per row** in Arabic (`keyWidth="9.091%p"`), and 11 in the Nordic and Swiss rows (the extra letters å, ö, ä, ü). MaKeeb's existing "narrow only character keys" rule handles this without changing the 10-unit frame.
- **Four letter rows** in Thai (and in some Indic and Southeast Asian layouts). This conflicts with "every page has the same rows" unless the symbols pages grow a row (§9.9, decision 6).
- **RTL frame keys are not mirrored in AOSP.** Backspace stays at the right and the bottom row is the shared `row_qwerty4`. Check Gboard and Apple on a device before deciding (`rtl-layout-support` currently says "mirrored function keys").

---

## 7. Parsing in `commonMain`

### 7.1 JSON with kotlinx.serialization

- Version 1.11.0 is already in `libs.versions.toml`, and the plugin is declared at the root.
- `Json { allowComments = true; allowTrailingComma = true }`: both options have been stable since 1.10 *(verified: changelog)*. They make hand-edited files pleasant without inventing a JSON5 dialect.
- 1.11.0 made `JsonDecodingException` public (still experimental), with `path` and `offset`, so the validator can say where an imported file is wrong. 1.12.0-RC (2026-09-04) adds a maximum nesting depth, so deeply nested input fails cleanly instead of overflowing the stack *(verified: changelog)*. Until MaKeeb moves to 1.12, the size cap in §9.4 bounds the input.
- **Cost to measure:** kotlinx-serialization-core and -json become part of the iOS extension's `MaKeebKeyboard` framework. Heap use is negligible (a layout is 1–3 KB of JSON and a few hundred objects), but check binary size and resident memory with `scripts/ios-memory-check.py` in stage B (§9.7). If that is unacceptable, the schema is small enough for a hand-written reader over `JsonElement`-like tokens. Do not plan for that unless the measurement demands it.

### 7.2 XML

No XML at run time.

- **Importers** (AOSP XML, CLDR keyboard3, AnySoftKeyboard, Unexpected Keyboard) run on the JVM or in Python at build time, like `scripts/generate-emoji-data.py`, which already parses CLDR XML with `xml.etree`.
- If a runtime Keyboard 3.0 import is ever wanted (for example users importing CLDR files in the companion app), use **xmlutil** 1.0.2 (Apache-2.0, iOS targets), or k3lp once it has Apple targets. Keep it in the **companion** (`:feature:settings` or a new `:feature:layouts`), which converts to MaKeeb JSON and writes it to the App Group. The keyboard extension then never links an XML parser.

### 7.3 Bundling

Follow the emoji precedent:

- JSON source files live in the repo, for example `makeeb/engine/layout/data/layouts/*.json` and `…/languages/*.json`.
- A script writes them into `BuiltInLayoutData.kt` as raw-string constants, and the result is checked in.
- The provider parses a file on first use and caches the parsed spec.
- Only enabled languages are parsed.

This keeps one code path for built-in and imported layouts, needs no resource system in engine modules, and keeps parsing off the per-keystroke path.

---

## 8. Licences

| Source | Licence | Use in MaKeeb | Duties |
|---|---|---|---|
| AOSP LatinIME layouts and `KeyboardTextsTable` (incl. LineageOS additions) | Apache-2.0 | **Primary source** | Keep the AOSP `NOTICE`; state that files were converted and changed |
| FUTO `futo-keyboard-layouts` | Apache-2.0 | Import source | Notice; mention FUTO in third-party notices |
| FlorisBoard 0.5 JSON layouts and popup mappings, `keyboard3` foundation files | Apache-2.0 | Import source | Notice; credit the listed authors |
| CLDR (exemplars, delimiters, numbering systems, names, keyboard3 files) | Unicode-3.0 | Validation and generation | Include the Unicode copyright and permission notice |
| Unexpected Keyboard `srcs/layouts/*.xml` (except 2) | CC0 1.0 | Reference data | None (courtesy credit) |
| Unexpected Keyboard `latn_neo2.xml`, `latn_qwertz.xml`, app code, `method.xml` | GPL-3.0 | **Do not use** | – |
| AnySoftKeyboard layouts | Apache-2.0 | Secondary source | Notice |
| Keyman keyboards | MIT per keyboard (sampled) | Later, minority languages | Keep each keyboard's copyright line |
| giellalt keyboards / kbdgen | MIT / Apache-2.0 | Later, Sámi and Uralic languages | Notice |
| HeliBoard layouts and `locale_key_texts` | GPL-3.0 (repo) | **Do not copy**; use the AOSP originals | – |
| Thumb-Key | AGPL-3.0 | **Do not use** | – |
| FUTO Keyboard app | FUTO Source First 1.1 | **Do not use** | – |
| xmlutil, k3lp (if ever linked) | Apache-2.0 | Libraries | Notice |

Points that follow from dictionaries-autocorrect.md §9:

- **With an Apache-2.0 MaKeeb** (recommended there), all "use" rows combine cleanly and App Store distribution is unaffected. Data converted from AOSP stays Apache-2.0, so the simplest rule is: **all bundled layout and language files are Apache-2.0**, with a `sources` field in each file naming its origin and licence.
- **Arrangements versus files.** Which letters sit where on a national layout is largely functional and widely re-implemented. Popup orderings, comments and file text are expression. Take data only from permissive sources and credit them, even where the underlying facts are common knowledge.
- **User-imported layouts** are not distributed by MaKeeb, so their licence is the user's business. A future community layout gallery (`extension-packages`) needs a contribution licence. Unexpected Keyboard's choice of CC0 for layouts is a good model because it lets layouts flow between projects.
- **Third-party notices:** add entries for AOSP LatinIME, FUTO layouts, FlorisBoard and Unicode CLDR to the planned `THIRD_PARTY_NOTICES` file and the companion's licences screen (dictionaries-autocorrect.md §9.4).

---

## 9. Recommendation

### 9.1 Format: MaKeeb layout JSON, version 1

**Write our own small schema, with converters from AOSP, FUTO, FlorisBoard 0.5 and CLDR.** Do not adopt any format as-is:

- **CLDR Keyboard 3.0:** no touch data, no frame, fields or direction, and the heaviest parser.
- **FlorisBoard JSON:** abandoned, and it lets data place frame keys.
- **FUTO YAML:** closest in spirit, but it allows explicit bottom rows and movable shift and delete, uses YAML, and depends on AOSP `!text/` references.

MaKeeb's rules need a format that *cannot* express what they forbid. That is easiest in a schema that simply has no field for it.

Two file kinds:

1. **Layout** (an arrangement, independent of language): the character rows of the letters page, plus optional per-key overrides. Identified by `id` (`qwertz`, `nordic`, `arabic`, `hebrew`).
2. **Language** (independent of layout): alternates, shifted-form exceptions, punctuation, native digits, quotation marks, currency, TLDs, localised mode labels, and the preferred layouts, with regional overrides. Identified by a BCP 47 tag (`de`, `de-CH`, `ar`).

A third kind, **symbols pages per script family** (`western`, `arabic`, `hebrew`, `devanagari` …), follows in stage D with the same "characters only" rule.

### 9.2 What the data may say, and what only the engine says

| Data may define | The engine alone defines (Kotlin, as today) |
|---|---|
| Characters of the letter rows, in visual left-to-right order | The number row (on/off, 0.8 height, digits from the language) |
| Per-key label/output split (`label\|output`) and explicit shifted forms | The shift slot (shift, `=\<`, `?123`) and backspace on the last character row, and their widths |
| Layout-specific alternates (Dvorak's `'`, AZERTY's `'`) | The bottom row for each field variant (Text, E-mail, URL), the mode key and its long-press action, the globe or emoji key, space and enter |
| Language alternates, punctuation, digits, quotes, currency, TLDs, labels | Digit hints on the top row when there is no number row |
| Script and direction | The symbols, more-symbols, number-pad and phone-pad frames (captions stay Kotlin) |
| Preferred layouts per language and region | Geometry: the 10-unit frame, narrowing only character keys, centring short rows |

Tokens beginning with `$` are reserved and rejected. They leave room for a future, deliberate template-key feature, if one is ever wanted.

### 9.3 Schema sketch

Layout (`layouts/azerty.json`):

```json
{
  "schema": 1,
  "id": "azerty",
  "name": "AZERTY",
  "script": "Latn",
  "rows": [
    "a z e r t y u i o p",
    "q s d f g h j k l m",
    "w x c v b n '"
  ],
  "keys": {
    "'": { "alternates": ["’", "\""] }
  },
  "sources": ["MaKeeb"]
}
```

Language (`languages/de.json`):

```json
{
  "schema": 1,
  "language": "de",
  "layouts": ["qwertz", "qwerty", "azerty", "dvorak", "colemak", "workman"],
  "regions": { "CH": { "layouts": ["swiss", "qwertz"] } },
  "alternates": {
    "a": "ä à á â æ ã å ā",
    "o": "ö ó ò ô õ ø œ ō",
    "u": "ü ú ù û ū",
    "s": "ß ś š"
  },
  "shifted": { "ß": "ẞ" },
  "quotes": "„ “ ‚ ‘",
  "digits": "0123456789",
  "currency": "€",
  "tlds": [".de", ".at", ".ch"],
  "sources": ["AOSP LatinIME KeyboardTextsTable (Apache-2.0)", "Unicode CLDR 48 (Unicode-3.0)"]
}
```

Details:

- **Rows** are a space-separated string or an array of tokens; arrays are needed for tokens containing spaces or quotes. A token is the output text, or `label|output` when they differ (the convention of AOSP, FUTO and HeliBoard). `\|` escapes a literal bar.
- **Alternates** are space-separated so that multi-code-point alternates (`ज्ञ`, `ch`) work. The effective list for a key is: layout alternates, then language alternates, then (for top-row keys without a number row) the engine's digit hint first, as today.
- **`shifted`** overrides `uppercase()` for the key and its alternates: `ß` → `ẞ`, Turkish `i` → `İ`, Dutch `ij` → `IJ`. A language-level `"casing": "tr"` could apply Turkic rules to every key instead of listing them.
- **`direction`** is optional and derived from `script` (`Arab`, `Hebr`, `Thaa`, `Syrc`, `Nkoo`, `Adlm` → RTL).
- **Kotlin model:** `@Serializable data class LayoutSpec(...)` and `LanguageSpec(...)` with a custom serializer for "string or array" rows. The assembler turns `(LayoutSpec, LanguageSpec, LayoutOptions, KeyboardMode)` into today's `KeyboardLayout` using today's `RowBuilder` functions. `KeyboardLayout`, `Key` and `LayoutGeometry` do not change.

### 9.4 Validation

The same validator runs at build time (JVM test over every bundled file) and at import time (companion app). It returns a list of errors with JSON paths and never throws into the keyboard.

- **Structure:**
  - `schema` known (newer schemas are rejected with "needs a newer MaKeeb").
  - `id` matches `[a-z0-9][a-z0-9_-]{0,31}`; `name` is 1–40 characters.
  - File size ≤ 64 KB, checked before parsing; JSON nesting ≤ 8, checked on the parsed tree (and by the parser itself from kotlinx.serialization 1.12).
- **Rows:**
  - Exactly 3 character rows (4 only if decision 6 allows tall scripts).
  - 1–12 keys per row, and at most 10 on the last row. With 3 units of frame keys that keeps character keys at 0.7 units or wider.
- **Tokens:**
  - Output and label 1–8 code points each.
  - No C0/C1 controls, lone surrogates or unassigned code points.
  - No whitespace except ZWNJ/ZWJ.
  - No `$` prefix.
- **Alternates:** at most 16 per key, no duplicates, each 1–8 code points.
- **Language:**
  - Well-formed BCP 47 tag.
  - Every `layouts` entry exists.
  - Alternate keys are single grapheme clusters.
- **Warnings (not errors):** duplicate outputs across keys (the swipe decoder's `characterKeys` map keeps one), and keys the language's exemplars do not contain.
- **Build-time only:**
  - The CLDR main-exemplar coverage test (§6.2).
  - Every layout × language × mode × option combination assembles.
  - `ModeSwitchGeometryTest` runs over every bundled layout (§9.7).
- **Failure behaviour:**
  - An invalid import is rejected in the companion with the first errors listed; it never reaches the keyboard.
  - A bundled file that fails at run time (it cannot, if the tests pass) falls back to QWERTY with no alternates rather than crashing.

### 9.5 Per-state key variants

- **Shift:** automatic upper-casing, with `shifted` exceptions (§9.3).
- **Field type:** engine only. The data can influence the content of the field keys, not their existence or place:
  - the language's comma and period (`،` for Arabic),
  - the `.com` key's alternates from `tlds`,
  - the e-mail `@` stays fixed.
- **RTL:**
  - Rows are written in visual order, so geometry does not change.
  - The engine swaps paired punctuation on symbols pages when the language is RTL (`(`↔`)`, `[`↔`]`, `{`↔`}`, `<`↔`>`, `«`↔`»`), as FlorisBoard's `layout_direction_selector` did.
  - The strip order, cursor-key direction and space-bar label are engine and renderer concerns under `rtl-layout-support`.
- **Caseless scripts:** the engine omits shift when no key of the letters page has a distinct upper-case form. What fills the slot is decision 6.

### 9.6 Language switching: choosing layouts per locale

1. **Preferences:** `KeyboardPreferences.letterLayoutId` becomes an ordered `languages: List<InputLanguage(tag, layoutId)>`. Migration: `[InputLanguage(defaultTagFor(letterLayoutId), letterLayoutId)]`, where QWERTZ maps to `de`, AZERTY to `fr` and everything else to `en`, which reproduces today's alternates exactly.
2. **Default layout for a tag:** the language file for the full tag, then its region override, then the language subtag's `layouts[0]`, then a script default (Latin → qwerty, Cyrillic → east_slavic, Arabic → arabic, Hebrew → hebrew …), then QWERTY. The index is generated from AOSP `method.xml` and FUTO `mapping.yaml` (both Apache-2.0), reviewed by hand.
3. **First run** (onboarding "pick languages"): offer the system's preferred locales that have a language file (Android `LocaleList`, iOS `Locale.preferredLanguages`), in the companion app. It writes, the keyboard reads, as AGENTS.md requires.
4. **Switching:** the globe or language key and the space-bar swipe cycle through enabled languages (`language-switching`). `LayoutOptions` gains `language`, and the provider cache key includes it. The space bar shows the autonym. The dictionary pack follows the tag (dictionaries-autocorrect.md §10).
5. **Platform hooks:**
   - iOS: set `UIInputViewController.primaryLanguage` to the active tag (platform-apis.md §3.2).
   - Android: optionally register additional `InputMethodSubtype`s for enabled languages, so the system switcher and hardware-keyboard mapping know them (decision 9).
   - `EditorInfo.hintLocales` can pre-select a language per field. It is a hint, not a switch, so leave it for later.

### 9.7 Migration plan

Each stage is one board task and one commit, and ends with `jvmTest` plus the platform builds.

**Stage A: model and parser, no behaviour change**
1. Apply the serialization plugin in `:engine:layout` and add `kotlinx-serialization-json` to `commonMain`.
2. Add `LayoutSpec`, `LanguageSpec`, the parser (returns a result with errors), the validator and the assembler.
3. Write today's six layouts and three languages (`en`, `de`, `fr`, with today's exact alternate orders) as JSON. Embed them as generated constants (§7.3).
4. Add a **parity test**: for every `KeyboardMode` × six layouts × `LetterVariant` × `numberRow` × `switchKey`, assert `dataProvider.layout(m, o) == legacyProvider.layout(m, o)`. `KeyboardLayout` and `Key` are data classes, and the lazy `characterKeys` is not part of `equals`.
5. Add validator tests: every rule in §9.4 has a failing example.

**Stage B: switch the built-ins to data**
1. `BuiltInLayoutProvider` keeps its name and no-argument constructor, but reads the bundled data. The Koin bindings (`KeyboardRuntimeModule`, `MainViewController`) and all test call sites stay unchanged.
2. Delete the letter strings and alternate maps from `BuiltInLayouts`. Symbols, numeric, phone and the bottom row stay Kotlin.
3. `LayoutTest` and `ModeSwitchGeometryTest` stay unchanged. With stage A's parity test they are green by construction. Delete the legacy copy once parity has held for one commit.
4. Measure the iOS extension (`scripts/ios-memory-check.py`) and the framework size with kotlinx.serialization linked.

**Stage C: languages**
1. Add `LayoutOptions.language`. `null` means "the layout's default language", so `LayoutTest.alternatesFollowTheLayoutsLanguage` passes unchanged.
2. Add the preferences migration (§9.6) and the `language-switching` card.
3. Generate language files for the launch set from AOSP plus CLDR (§6.2), with `sources` fields.
4. Add the exemplar-coverage test and explicit-language tests (German on QWERTY gets `ä` first; `ß` shifts to `ẞ`).

**Stage D: symbols as data, RTL, caseless**
1. Symbols and more-symbols pages as per-script data files, characters only, with the local currency.
2. Arabic, Hebrew and Persian layouts: the direction flag and bracket swap.
3. Caseless handling of the shift slot.
4. Parametrise `ModeSwitchGeometryTest` over **every bundled layout**. For caseless layouts, `backspaceAndTheShiftSlotStayPut` checks backspace and the bottom row, and a caseless variant checks the slot according to decision 6.

**Stage E: import**
1. `custom-layout-import` in the companion: paste or open a file, validate, show errors, and save the normalised JSON to app storage (Android) or the App Group (iOS; the keyboard reads it without Full Access).
2. Converters: FlorisBoard 0.5 JSON (characters layouts and popup mappings) and FUTO YAML in the companion. A CLDR keyboard3 converter later under `cldr-keyboard3-support`.

### 9.8 Tooling

- `makeeb/scripts/import-aosp-layouts.py`: reads the LatinIME `rowkeys_*.xml`, `method.xml` and `KeyboardTextsTable.java` (pinned to a commit), resolves `!text/` and `%`, and writes layout and language JSON with `sources`. It also emits a report of anything skipped (`<switch>` cases, key styles).
- `makeeb/scripts/import-cldr-language-data.py`: exemplars, punctuation, delimiters, numbering systems and autonyms from a pinned `cldr-json` release.
- `makeeb/scripts/generate-layout-data.py`: embeds the JSON sources into Kotlin constants, as `generate-emoji-data.py` does.
- Python matches the existing script. Validation lives in Kotlin tests using the real parser, so there is only one validator.

### 9.9 Open decisions for the user

**Decided 2026-09-29:** our own JSON schema, with layouts based on AOSP; long-press alternates follow the language, not the layout; MIT licence for MaKeeb.

1. **Format.** Own JSON schema with converters (recommended), FUTO YAML as-is, or a Keyboard 3.0 profile?
2. **Alternates follow the language, not the layout** (recommended). This changes behaviour for anyone on QWERTY who types German or French today, once they pick a language.
3. **Launch languages.** en only, the five dictionary languages (en, de, fr, es, it, pt), or the full table in §6.1 minus CJK? Layout data is cheap. Dictionaries decide what "supported" means.
4. **Data sources.** AOSP-first with a CLDR check (recommended), with FUTO and FlorisBoard as secondary sources? Should community edits be accepted directly into the JSON?
5. **Licence of MaKeeb's layout data.** Apache-2.0 for everything (simplest, and required for AOSP-derived files), or CC0 for layouts MaKeeb writes itself, to encourage reuse?
6. **Caseless and tall scripts.**
   - In Hebrew and Arabic, should the shift slot hold nothing (letters fill from the left edge, as AOSP does) or a page key?
   - For four-row scripts such as Thai, should the symbols pages grow a fourth row so every page keeps the same rows, or should four-row layouts be excluded?
7. **RTL frame.** Keep the frame unmirrored, as AOSP does (recommended until checked), or mirror it? Check Gboard and iOS on the Pixel and the simulator first, then update the `rtl-layout-support` card text.
8. **Native digits.** Number row and hints in native digits for Arabic, Persian and Hindi (HeliBoard and FlorisBoard do this), Latin digits, or a per-language setting?
9. **Android subtypes.** In-app language switching only, or also register `InputMethodSubtype`s for enabled languages?
10. **kotlinx.serialization in the iOS extension.** Accept it, subject to the stage B measurement (recommended), or plan a hand-written reader?
11. **Keyboard 3.0.** Defer (recommended), or invest in adding Apple targets to k3lp upstream (Apache-2.0, NLnet-funded, maintained by FlorisBoard's author)? Contributing there would make a future CLDR importer cheaper for MaKeeb and others.

---

## 10. Sources

Read on 2026-09-28. Repositories were cloned (shallow, sparse) or read through the GitHub REST API (`gh`), the Codeberg API and raw file URLs. Counts are from those snapshots.

**CLDR Keyboard 3.0**
- Spec source: https://github.com/unicode-org/cldr/blob/main/docs/ldml/tr35-keyboards.md; published: https://www.unicode.org/reports/tr35/tr35-78/tr35-keyboards.html
- Spec version map: https://github.com/unicode-org/cldr/blob/main/docs/ldml/tr35-versions.yml
- Keyboard data, imports, DTD, ABNF, tests: https://github.com/unicode-org/cldr/tree/main/keyboards
- CLDR licence: https://github.com/unicode-org/cldr/blob/main/LICENSE; Unicode License v3: https://www.unicode.org/license.txt
- CLDR 45 release note: https://cldr.unicode.org/downloads/cldr-45
- CLDR 48 release note: https://cldr.unicode.org/downloads/cldr-48
- CLDR 49 release note (draft): https://cldr.unicode.org/downloads/cldr-49; beta: https://blog.unicode.org/2026/09/unicode-cldr-49-beta-available-for.html; releases: https://github.com/unicode-org/cldr/releases
- Keyboard working group: https://cldr.unicode.org/index/keyboard-workgroup
- cldr-json 48.2.2 (`characters.json`, `delimiters.json`, `numbers.json`): https://github.com/unicode-org/cldr-json
- Keyman 19 roadmap update (CLDR on mobile postponed): https://blog.keyman.com/2026/06/keyman-19-roadmap-update/
- Keyman LDML compiler: https://github.com/keymanapp/keyman/tree/master/developer/src/kmc-ldml

**k3lp**
- Repository, README, build logic, `lib/xml`, `lib/text` (`K3Descriptor.kt`): https://codeberg.org/k3lp/k3lp
- NLnet project page: https://nlnet.nl/project/k3lp/
- Maven Central listing: https://repo1.maven.org/maven2/org/k3lp/

**FlorisBoard**
- Keyboard3 integration, part 1 (PR #3340, merged 2026-09-23): https://github.com/florisboard/florisboard/pull/3340; k3lp layouting issue #3233: https://github.com/florisboard/florisboard/issues/3233
- Removal of `LayoutManager` (commit `ba75d40`, 2026-09-07): https://github.com/florisboard/florisboard/commit/ba75d40a626522b4f4b91cf83d61c92b6c451910
- v0.5.2 sources: `ime/keyboard/LayoutManager.kt`, `ime/keyboard/KeyData.kt`, `ime/text/keyboard/TextKeyData.kt`, `ime/popup/PopupSet.kt`, `ime/core/Subtype.kt`: https://github.com/florisboard/florisboard/tree/v0.5.2/app/src/main/kotlin/dev/patrickgold/florisboard/ime
- JSON assets (layouts, popup mappings, subtype presets): https://github.com/florisboard/florisboard/tree/main/app/src/main/assets/ime/keyboard
- Keyboard3 foundation (`qwertz.xml`, implied imports): https://github.com/florisboard/florisboard/tree/main/app/src/main/assets/ime/keyboard3/org.florisboard.k3.foundation

**HeliBoard**
- Layout format documentation: https://github.com/HeliBorg/HeliBoard/blob/main/layouts.md
- Layouts and `locale_key_texts`: https://github.com/HeliBorg/HeliBoard/tree/main/app/src/main/assets
- Licence files and README licence section: https://github.com/HeliBorg/HeliBoard (`LICENSE`, `LICENSE-Apache-2.0`, `README.md`)

**AOSP LatinIME** (LineageOS mirror of AOSP, branch `lineage-23.2`)
- `java/res/xml/` (`kbd_*`, `rows_*`, `rowkeys_*`, `method.xml`, `rows_hebrew.xml`, `rows_arabic.xml`, `rows_thai.xml`): https://github.com/LineageOS/android_packages_inputmethods_LatinIME/tree/lineage-23.2/java/res/xml
- `KeyboardTextsTable.java`: https://github.com/LineageOS/android_packages_inputmethods_LatinIME/blob/lineage-23.2/java/src/com/android/inputmethod/keyboard/internal/KeyboardTextsTable.java
- Text-table sources: https://github.com/LineageOS/android_packages_inputmethods_LatinIME/tree/lineage-23.2/tools/make-keyboard-text
- Upstream: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/

**Other keyboards**
- FUTO layouts (`LICENSE`, `LayoutSpec.md`, `mapping.yaml`): https://github.com/futo-org/futo-keyboard-layouts
- AnySoftKeyboard language packs (`german/pack/src/main/res/xml/de_qwerty.xml`, `german_keyboards.xml`, `PACKS.md`): https://github.com/AnySoftKeyboard/AnySoftKeyboard/tree/main/addons/languages
- Unexpected Keyboard layouts and their licence: https://github.com/Julow/Unexpected-Keyboard/tree/master/srcs/layouts; custom layouts guide: https://github.com/Julow/Unexpected-Keyboard/blob/master/doc/Custom-layouts.md; subtypes: https://github.com/Julow/Unexpected-Keyboard/blob/master/res/xml/method.xml
- Keyman keyboards (`release/basic/basic_kbdgr`, `release/sil/sil_euro_latin`): https://github.com/keymanapp/keyboards; Keyman licence: https://github.com/keymanapp/keyman/blob/master/LICENSE.md
- Divvun kbdgen: https://github.com/divvun/kbdgen; example layout: https://github.com/giellalt/keyboard-smj/blob/main/smj.kbdgen/layouts/smj-NO.yaml
- xkeyboard-config `COPYING`: https://gitlab.freedesktop.org/xkeyboard-config/xkeyboard-config/-/blob/master/COPYING

**Libraries**
- xmlutil: https://github.com/pdvrieze/xmlutil; artifacts: https://repo1.maven.org/maven2/io/github/pdvrieze/xmlutil/
- kotlinx.serialization changelog (stable `allowComments` and `allowTrailingComma` in 1.10; public `JsonDecodingException` in 1.11.0; nesting-depth limit in 1.12.0-RC): https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md
- kudzu (k3lp's parser base, iOS artifacts): https://repo1.maven.org/maven2/io/github/copper-leaf/; kotlin-codepoints: https://repo1.maven.org/maven2/de/cketti/unicode/

**MaKeeb code read** (under `makeeb/`)
- `engine/layout/src/commonMain/kotlin/com/makeeb/engine/layout/`: `BuiltInLayouts.kt`, `LayoutDsl.kt`, `KeyboardLayout.kt`, `LayoutGeometry.kt`, `KeyLabels.kt`, `LayoutProvider.kt`
- `engine/layout/src/commonTest/kotlin/com/makeeb/engine/layout/`: `LayoutTest.kt`, `ModeSwitchGeometryTest.kt`
- `engine/input/src/commonMain/kotlin/com/makeeb/engine/input/InputEngine.kt` (`layoutFor`), `engine/emoji/…/BundledEmojiCatalog.kt`, `scripts/generate-emoji-data.py`, `gradle/libs.versions.toml`, `shared/keyboard/build.gradle.kts`
