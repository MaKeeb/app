# MaKeeb layout data (schema 1)

MaKeeb's letter layouts and per-language key data are JSON files. The design and the reasons behind it are in [docs/research/layout-formats.md](../research/layout-formats.md) §9. This file describes schema version 1, as shipped by the APP-14 card.

There are two kinds of file:

- A **layout** is an arrangement of characters, independent of language: QWERTY, AZERTY, Dvorak.
- A **language** holds what a language adds, independent of layout: long-press alternates, shifted-form exceptions and the layouts it usually uses.

Long-press alternates follow the language, not the layout, so a German speaker on QWERTY gets German alternates.

## What the data cannot say

The engine builds everything the geometry rules depend on, in Kotlin (`BuiltInLayouts`):

- the number row and its 0.8 height;
- shift, backspace and their widths;
- the field-dependent bottom row (mode key, globe or emoji key, comma, space, full stop, enter);
- the digit hints on the top row;
- the symbols, more-symbols, number and phone pages.

The schema has no field for any of them. A data file can therefore never move a key that the letters and symbols pages share, and `ModeSwitchGeometryTest` holds by construction.

## Files

```
makeeb/engine/layout/data/
  bundle.json                 which files ship, in the order settings list them
  layouts/<id>.json           qwerty, qwertz, azerty, dvorak, colemak (from AOSP), workman (hand-written)
  languages/<tag>.json        en, de, fr, es, it, pt, hu, pl, nl, sv (from AOSP)
makeeb/scripts/import-aosp-layouts.py    AOSP LatinIME + CLDR -> the JSON above (network; cached in build/)
makeeb/scripts/generate-layout-data.py   the JSON -> engine/layout/src/commonMain/.../data/BundledLayoutData.kt
```

The generated Kotlin holds each file's text in a raw-string constant, as the emoji catalogue does. Engine modules have no resource loading. `BuiltInLayoutProvider` parses and validates a file the first time it needs one: the chosen layout and language only, plus all layouts and languages when the companion lists them in settings.

To change a layout or language, edit the JSON (or the converter, for files it writes) and run `scripts/generate-layout-data.py`. `import-aosp-layouts.py` rewrites the AOSP-derived files and then runs the generator. `workman.json` is hand-written, and the converter leaves it alone.

## Layout file

```json
{
  "schema": 1,
  "id": "azerty",
  "name": "AZERTY",
  "rows": [
    "a z e r t y u i o p",
    "q s d f g h j k l m",
    "w x c v b n '"
  ],
  "keys": {
    "'": { "alternates": "’ \"" }
  },
  "sources": ["AOSP LatinIME 8b211dd233e9 (Apache-2.0): java/res/xml/rows_azerty.xml"]
}
```

| Field | Required | Meaning |
|---|---|---|
| `schema` | yes | `1`. A reader rejects any other version |
| `id` | yes | `[a-z0-9][a-z0-9_-]{0,31}`, the same as the file name. Stored in preferences, so never rename one |
| `name` | yes | Shown in settings, 1–40 characters |
| `rows` | yes | Exactly three rows: the letters page, top to bottom, in visual left-to-right order. Each row is space-separated keys. A key is the text it types, which is also its label (lower case; the engine upper-cases it under shift) |
| `keys` | no | Per-key options, by key text: `width` in key units (0.5–2, default 1) and `alternates`, the long-press keys this arrangement adds before the language's (space-separated) |
| `sources` | yes | Where the data comes from and under what licence |

The engine puts shift before the third row and backspace after it. Rows are ten units wide. A narrower row is centred, and a wider one narrows only its character keys: Dvorak's nine-letter third row, with shift and backspace, is 12 units.

## Language file

```json
{
  "schema": 1,
  "language": "de",
  "name": "German",
  "autonym": "Deutsch",
  "layouts": ["qwertz"],
  "alternates": {
    "a": "ä â à á æ ã å ā",
    "s": "ß ś š"
  },
  "shifted": { "ß": "ẞ" },
  "sources": ["AOSP LatinIME 8b211dd233e9 (Apache-2.0): tools/make-keyboard-text/res/values-de/donottranslate-more-keys.xml"]
}
```

| Field | Required | Meaning |
|---|---|---|
| `schema` | yes | `1` |
| `language` | yes | A BCP 47 tag (`de`, `pt-BR`, `sr-Latn`), the same as the file name. Stored in preferences as `layout.language` |
| `name`, `autonym` | yes | The English name, for search, and the language's own name, shown in settings |
| `layouts` | yes | Bundled layout ids the language uses, most usual first. The first is its default. Not applied yet: the APP-17 card will use it. Picking a language never changes the user's layout |
| `alternates` | no | Long-press keys per base key, most likely first (space-separated). Keys are the text of a layout key |
| `shifted` | no | Shifted forms where `String.uppercase()` is wrong for the language (`ß` → `ẞ`; Turkish `i` → `İ`). Kept as data for now: the engine still upper-cases |
| `sources` | yes | Where the data comes from and under what licence |

## A key's long-press list

For each character key, the engine builds the list in this order, dropping repeats:

1. the digit hint, on top-row keys when the number row is off (`q` → `1`);
2. the layout's `keys.<key>.alternates`;
3. the language's `alternates.<key>`.

Upper case comes from `String.uppercase()` for now (see `shifted`).

## Validation

`LayoutDataParser` runs at load and in the tests (`LayoutDataTest`). It collects every problem with its file and JSON path. The keyboard never throws on bad data: a layout that fails falls back to QWERTY, and a language that fails falls back to English, then to no alternates.

- **Structure:** strict JSON; unknown fields are errors; `schema` must be 1; the file is at most 64 KB; `id` and `language` match their file names.
- **Rows:** exactly three; 1–12 keys each; at most 12 units wide, and at most 10 on the third row (so letters stay 0.7 units or wider beside shift and backspace).
- **Keys:** 1–8 code points; no whitespace, control characters or lone surrogates; no `$` prefix (reserved for template keys); no key twice in a layout. `keys` entries must name a key in the rows.
- **Alternates:** at most 16 per key; each a valid key; no repeats; not the key itself.
- **Language:** a well-formed tag; `layouts` not empty, without repeats, and every entry bundled; `shifted` keys must be base keys or alternates.
- **Build-time only (tests):**
  - every layout × language × mode × option assembles;
  - every letter of the language's CLDR main exemplars (`CldrExemplars.kt`, generated) can be typed on every bundled layout, as a key or an alternate;
  - the data-built layouts equal the old Kotlin ones (`LayoutParityTest`).

## Sources and licences

- **AOSP LatinIME** at commit `8b211dd233e99f59c9853a9c44c627dfb431ebd8` (Apache-2.0): the letter rows of five layouts, each language's long-press keys and its layouts. The converter changes a few rows so they fit MaKeeb's frame. Each change has its reason in `MAKEEB_RULES` in the script:
  - Dvorak's `q` and `z`, which AOSP puts in the bottom row, join the third letter row.
  - Colemak's `;` stays on the symbols page.
  - AZERTY's apostrophe keeps `’ "`.
- **Unicode CLDR** 48.2.2 (Unicode-3.0): main exemplar characters, for the coverage test only. They do not ship.
- **Workman:** written by hand; AOSP has no Workman layout.
- GPL data (HeliBoard's layouts and `locale_key_texts`) must never be copied in (layout-formats.md §8).

`THIRD_PARTY_NOTICES.md` carries the notices.
