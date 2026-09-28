#!/usr/bin/env python3
"""Convert AOSP LatinIME layouts and per-locale "more keys" into MaKeeb layout data.

    scripts/import-aosp-layouts.py [--offline]

Reads, from AOSP LatinIME at a pinned commit (Apache-2.0):
  - java/res/xml/rows_<layout>.xml and everything it includes: the three letter rows;
  - tools/make-keyboard-text/res/values[-<lang>]/donottranslate-more-keys.xml: long-press keys;
  - java/res/values/donottranslate.xml and java/res/xml/method.xml: each language's layout;
and CLDR's main exemplar characters (cldr-json, Unicode-3.0) for the coverage test.

Writes engine/layout/data/layouts/<id>.json and engine/layout/data/languages/<tag>.json in
MaKeeb's schema (docs/layouts/schema.md), the test-only exemplar table, and then runs
generate-layout-data.py to embed the data in Kotlin. Files not listed below (workman.json) are
hand-written and left alone. Downloads are cached in build/layout-sources/.

The engine owns the frame (number row, shift, backspace, bottom row, symbols pages), so only
characters are taken from AOSP. Where AOSP's letter rows lean on its frame, MAKEEB_RULES below
adapt them, each with its reason; everything else is converted as published.
"""
import argparse
import base64
import json
import os
import re
import subprocess
import sys
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET

AOSP_COMMIT = "8b211dd233e99f59c9853a9c44c627dfb431ebd8"  # AOSP main, 2025-08-08
AOSP_MIRRORS = [
    # gitiles serves raw files as base64; LineageOS mirrors AOSP with the same commit ids.
    ("https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/{commit}/{path}?format=TEXT", True),
    ("https://raw.githubusercontent.com/LineageOS/android_packages_inputmethods_LatinIME/{commit}/{path}", False),
]
CLDR_JSON = "48.2.2"
CLDR_URL = "https://raw.githubusercontent.com/unicode-org/cldr-json/{tag}/cldr-json/cldr-misc-full/main/{lang}/characters.json"

LATIN = "{http://schemas.android.com/apk/res/com.android.inputmethod.latin}"
ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
DATA = os.path.join(ROOT, "engine", "layout", "data")
CACHE = os.path.join(ROOT, "build", "layout-sources")
EXEMPLARS_KT = os.path.join(ROOT, "engine", "layout", "src", "commonTest", "kotlin", "com", "makeeb",
                            "engine", "layout", "CldrExemplars.kt")

# Layouts converted from AOSP: id -> display name.
LAYOUTS = {
    "qwerty": "QWERTY",
    "qwertz": "QWERTZ",
    "azerty": "AZERTY",
    "dvorak": "Dvorak",
    "colemak": "Colemak",
}

# Where AOSP's letter rows depend on its frame, MaKeeb's rules. Keys never move between pages, so
# the letters page has exactly three character rows between the engine's shift and backspace.
MAKEEB_RULES = {
    # AOSP puts Dvorak's q and z in the shared bottom row (the comma and period slots). MaKeeb's
    # bottom row belongs to the engine, so they join the third letter row where ANSI Dvorak has them.
    "dvorak": {"bottom_row_letters": True},
    # ';' lives on the symbols page, as on every other MaKeeb layout; the top row keeps nine letters.
    "colemak": {"drop": [";"]},
    # AOSP takes the apostrophe's long-press keys from the language's quote table. Until symbols
    # follow the language (layout-formats.md, stage D) it keeps MaKeeb's pair.
    "azerty": {"keys": {"'": {"alternates": "’ \""}}},
}

# Languages: tag -> (English name, autonym). AOSP's more-keys come from values-<tag>.
LANGUAGES = {
    "en": ("English", "English"),
    "de": ("German", "Deutsch"),
    "fr": ("French", "Français"),
    "es": ("Spanish", "Español"),
    "it": ("Italian", "Italiano"),
    "pt": ("Portuguese", "Português"),
    "hu": ("Hungarian", "Magyar"),
    "pl": ("Polish", "Polski"),
    "nl": ("Dutch", "Nederlands"),
    "sv": ("Swedish", "Svenska"),
}

# AOSP layouts MaKeeb doesn't bundle yet (board card APP-15): the closest bundled one.
LAYOUT_STAND_INS = {"spanish": "qwerty", "nordic": "qwerty", "swiss": "qwertz"}

# Explicit shifted forms where String.uppercase() is wrong for the language (data for later: the
# engine still upper-cases). German has had a capital sharp s since 2017.
SHIFTED = {"de": {"ß": "ẞ"}}

AOSP_SOURCE = f"AOSP LatinIME {AOSP_COMMIT[:12]} (Apache-2.0)"

ap = argparse.ArgumentParser()
ap.add_argument("--offline", action="store_true", help="use only the download cache")
args = ap.parse_args()


def fetch(url_template: str, cache_name: str, **fields) -> bytes:
    cached = os.path.join(CACHE, cache_name)
    if os.path.exists(cached):
        with open(cached, "rb") as f:
            return f.read()
    if args.offline:
        sys.exit(f"not cached: {cache_name}")
    url = url_template.format(**fields)
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    os.makedirs(os.path.dirname(cached), exist_ok=True)
    with open(cached, "wb") as f:
        f.write(data)
    return data


def aosp(path: str) -> bytes:
    """A file from AOSP LatinIME at AOSP_COMMIT, from the first mirror that has it."""
    failures = []
    for template, is_base64 in AOSP_MIRRORS:
        name = f"aosp-{AOSP_COMMIT}/{path}"
        try:
            data = fetch(template, name, commit=AOSP_COMMIT, path=path)
        except Exception as e:  # noqa: BLE001 - any mirror failure falls through to the next
            failures.append(f"{template.split('/')[2]}: {e}")
            continue
        if is_base64 and not data.lstrip().startswith(b"<"):
            data = base64.b64decode(data)
            with open(os.path.join(CACHE, name), "wb") as f:
                f.write(data)
        return data
    raise SystemExit(f"could not fetch {path}: {'; '.join(failures)}")


def xml(path: str) -> ET.Element:
    return ET.fromstring(aosp(path))


# region AOSP text table (KeyboardTextsTable sources)

TEXT_TABLES = {}


def text_table(lang: str) -> dict:
    """`values[-lang]/donottranslate-more-keys.xml` as name -> raw text."""
    if lang not in TEXT_TABLES:
        suffix = "" if lang == "" else f"-{lang}"
        root = xml(f"tools/make-keyboard-text/res/values{suffix}/donottranslate-more-keys.xml")
        TEXT_TABLES[lang] = {s.get("name"): (s.text or "") for s in root.iter("string")}
    return TEXT_TABLES[lang]


def lookup(name: str, lang: str) -> str:
    table = text_table(lang)
    if name in table:
        return table[name]
    return text_table("").get(name, "")


def split_unescaped(text: str, sep: str = ",") -> list:
    parts, current, i = [], "", 0
    while i < len(text):
        c = text[i]
        if c == "\\" and i + 1 < len(text):
            current += text[i:i + 2]
            i += 2
            continue
        if c == sep:
            parts.append(current)
            current = ""
        else:
            current += c
        i += 1
    parts.append(current)
    return parts


def unescape(spec: str) -> str:
    return re.sub(r"\\(.)", r"\1", spec)


FLAG = re.compile(r"^!(fixedColumnOrder|autoColumnOrder)!\d+$|^!(needsDividers|hasLabels|noPanelAutoMoreKey)!$")


def more_keys(text: str, lang: str) -> list:
    """Resolve an AOSP more-keys string: `!text/` references, `%` (the layout's extras) and flags."""
    text = text.strip().strip('"')
    out = []
    for part in split_unescaped(text):
        part = part.strip()
        if not part or part == "%" or FLAG.match(part):
            continue
        if part.startswith("!text/"):
            out += more_keys(lookup(part[len("!text/"):], lang), lang)
        elif part.startswith("!"):
            raise SystemExit(f"unsupported more-keys entry {part!r} ({lang})")
        else:
            out.append(unescape(part))
    return out

# endregion

# region AOSP layouts


def key_spec(key: ET.Element):
    spec = key.get(LATIN + "keySpec")
    if spec is None:
        return None
    if spec.startswith("!text/"):
        spec = lookup(spec[len("!text/"):], "")
    return unescape(spec)


def included(element: ET.Element) -> str:
    return element.get(LATIN + "keyboardLayout").removeprefix("@xml/")


def row_tokens(element: ET.Element, frame_styles: list) -> list:
    """Character keys under `element`, following includes and taking each switch's default."""
    tokens = []
    for child in element:
        tag = child.tag
        if tag == "Key":
            spec = key_spec(child)
            if spec is None:
                frame_styles.append(child.get(LATIN + "keyStyle"))
            else:
                tokens.append(spec)
        elif tag == "include":
            tokens += row_tokens(xml(f"java/res/xml/{included(child)}.xml"), frame_styles)
        elif tag == "switch":
            default = child.find("default")
            if default is None:
                raise SystemExit(f"switch without a default in {element.tag}")
            tokens += row_tokens(default, frame_styles)
        elif tag == "Spacer":
            raise SystemExit("spacers are not supported in letter rows")
    return tokens


def bottom_row_letters(layout: str) -> tuple:
    """Letters AOSP puts in the shared bottom row for `layout`, left and right of the space bar."""
    left, right = [], []

    def walk(element, side):
        for child in element:
            if child.tag == "include":
                name = included(child)
                if name.startswith("key_space"):
                    side = right
                    continue
                side = walk(xml(f"java/res/xml/{name}.xml"), side)
            elif child.tag == "switch":
                for case in child.findall("case"):
                    if layout in (case.get(LATIN + "keyboardLayoutSet") or "").split("|"):
                        side += [key_spec(k) for k in case.iter("Key") if key_spec(k)]
            elif child.tag == "Row":
                side = walk(child, side)
        return side

    walk(xml("java/res/xml/row_qwerty4.xml"), left)
    return left, right


def convert_layout(layout_id: str, name: str) -> dict:
    root = xml(f"java/res/xml/rows_{layout_id}.xml")
    rules = MAKEEB_RULES.get(layout_id, {})
    rows = []
    for row in root.findall("Row"):
        frame = []
        tokens = [t for t in row_tokens(row, frame) if t not in rules.get("drop", [])]
        unexpected = [s for s in frame if s not in ("shiftKeyStyle", "deleteKeyStyle")]
        if unexpected:
            raise SystemExit(f"{layout_id}: unexpected frame keys {unexpected}")
        rows.append(tokens)
    if len(rows) != 3:
        raise SystemExit(f"{layout_id}: expected 3 letter rows, found {len(rows)}")
    if rules.get("bottom_row_letters"):
        left, right = bottom_row_letters(layout_id)
        rows[2] = left + rows[2] + right
    spec = {
        "schema": 1,
        "id": layout_id,
        "name": name,
        "rows": [" ".join(r) for r in rows],
    }
    if "keys" in rules:
        spec["keys"] = rules["keys"]
    sources = [f"{AOSP_SOURCE}: java/res/xml/rows_{layout_id}.xml"]
    if rules:
        sources.append("MaKeeb: scripts/import-aosp-layouts.py MAKEEB_RULES")
    spec["sources"] = sources
    return spec

# endregion

# region Languages


def aosp_layout_map() -> dict:
    """Locale -> keyboard layout set: the compatibility map, then explicit subtype values."""
    root = xml("java/res/values/donottranslate.xml")
    found = {}
    for array in root.iter("string-array"):
        if array.get("name") == "locale_and_extra_value_to_keyboard_layout_set_map":
            items = [i.text for i in array.findall("item")]
            for key, value in zip(items[0::2], items[1::2]):
                found[key.split(":")[0]] = value
    method = aosp("java/res/xml/method.xml").decode("utf-8")
    for subtype in re.finditer(r"<subtype(.*?)/>", method, re.S):
        body = subtype.group(1)
        locale = re.search(r'imeSubtypeLocale="([^"]+)"', body).group(1)
        explicit = re.search(r"KeyboardLayoutSet=([a-z_]+)", body)
        if explicit and locale not in found:
            found[locale] = explicit.group(1)
    return found


def preferred_layouts(tag: str, layout_map: dict) -> list:
    candidates = [v for k, v in layout_map.items() if k == tag] + \
                 [v for k, v in layout_map.items() if k.startswith(tag + "_")]
    out = []
    for layout in candidates or ["qwerty"]:
        layout = LAYOUT_STAND_INS.get(layout, layout)
        if layout in LAYOUTS and layout not in out:
            out.append(layout)
    return out or ["qwerty"]


def convert_language(tag: str, layout_map: dict) -> dict:
    name, autonym = LANGUAGES[tag]
    alternates = {}
    for letter in "abcdefghijklmnopqrstuvwxyz":
        keys = more_keys(lookup(f"morekeys_{letter}", tag), tag)
        keys = list(dict.fromkeys(k for k in keys if k != letter))
        if keys:
            alternates[letter] = " ".join(keys)
    spec = {
        "schema": 1,
        "language": tag,
        "name": name,
        "autonym": autonym,
        "layouts": preferred_layouts(tag, layout_map),
        "alternates": alternates,
    }
    sources = [
        f"{AOSP_SOURCE}: tools/make-keyboard-text/res/values-{tag}/donottranslate-more-keys.xml",
        f"{AOSP_SOURCE}: java/res/values/donottranslate.xml, java/res/xml/method.xml (layouts)",
    ]
    if tag in SHIFTED:
        spec["shifted"] = SHIFTED[tag]
        sources.append("MaKeeb: shifted forms")
    spec["sources"] = sources
    return spec


def exemplar_letters(pattern: str) -> list:
    """A CLDR exemplar UnicodeSet as single letters; multi-letter sequences ({cs}, {ij}) are skipped."""
    body = pattern.strip()
    if not (body.startswith("[") and body.endswith("]")):
        raise SystemExit(f"unexpected exemplar set {pattern!r}")
    body = re.sub(r"\{[^}]*\}", " ", body[1:-1])
    body = re.sub(r"\\u([0-9A-Fa-f]{4})", lambda m: chr(int(m.group(1), 16)), body)
    chars, i = [], 0
    while i < len(body):
        c = body[i]
        if c == "\\" and i + 1 < len(body):
            chars.append(body[i + 1])
            i += 2
        elif c == "-" and chars and i + 1 < len(body) and not body[i + 1].isspace():
            start = chars.pop()
            chars += [chr(p) for p in range(ord(start), ord(body[i + 1]) + 1)]
            i += 2
        else:
            chars.append(c)
            i += 1
    letters = []
    for c in chars:
        if c.isspace():
            continue
        if unicodedata.combining(c) and letters:
            letters[-1] += c
        else:
            letters.append(c)
    return [unicodedata.normalize("NFC", l) for l in letters]


def cldr_exemplars(tag: str) -> list:
    data = json.loads(fetch(CLDR_URL, f"cldr-json-{CLDR_JSON}/{tag}/characters.json", tag=CLDR_JSON, lang=tag))
    return exemplar_letters(data["main"][tag]["characters"]["exemplarCharacters"])

# endregion


def write_json(path: str, spec: dict):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(spec, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    for layout_id, name in LAYOUTS.items():
        spec = convert_layout(layout_id, name)
        write_json(os.path.join(DATA, "layouts", f"{layout_id}.json"), spec)
        print(f"layout {layout_id}: {' / '.join(spec['rows'])}")
    layout_map = aosp_layout_map()
    exemplars = {}
    for tag in LANGUAGES:
        spec = convert_language(tag, layout_map)
        write_json(os.path.join(DATA, "languages", f"{tag}.json"), spec)
        exemplars[tag] = cldr_exemplars(tag)
        aosp_layout = layout_map.get(tag, "qwerty")
        note = f" (AOSP: {aosp_layout}, not bundled)" if aosp_layout not in LAYOUTS else ""
        print(f"language {tag}: layouts {spec['layouts']}{note}, {len(spec['alternates'])} keys with alternates")
    with open(EXEMPLARS_KT, "w", encoding="utf-8") as f:
        f.write("// Generated by scripts/import-aosp-layouts.py; do not edit.\n")
        f.write(f"// Unicode CLDR {CLDR_JSON} (cldr-json), main exemplar characters.\n")
        f.write("// Data © Unicode, Inc. SPDX-License-Identifier: Unicode-3.0\n")
        f.write("package com.makeeb.engine.layout\n\n")
        f.write("/** The letters each bundled language needs (CLDR main exemplars), without multi-letter sequences. */\n")
        f.write("internal val cldrMainExemplars: Map<String, String> = mapOf(\n")
        for tag, letters in exemplars.items():
            f.write(f'    "{tag}" to "{" ".join(letters)}",\n')
        f.write(")\n")
    subprocess.run([sys.executable, os.path.join(ROOT, "scripts", "generate-layout-data.py")], check=True)


main()
