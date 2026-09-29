# Dictionary pack hosting and the pack catalogue

English ships inside the apps. Every other language is a dictionary pack (an MKD file, docs/dictionaries/mkd-format.md) that the companion app downloads, during setup or later from Settings, like Gboard. The packs and a small catalogue listing them are published as the assets of one GitHub release. The user decided this on 2026-09-29.

## The catalogue

`catalogue.json` sits beside the packs. The companion app fetches it to learn what it can offer. `makeeb/engine/packs` defines it (`PackCatalogue`): `:tools:dictionaries` writes it with `toJson()` and the app reads it with `parse()`, so both sides share one definition.

```json
{
  "format": 1,
  "packs": [
    {
      "language": "hu",
      "name": "Magyar",
      "file": "hu.mkd",
      "url": "hu.mkd",
      "size": 8224316,
      "sha256": "2adf8e4a986909837b7fcfbfb7cba0e73ca3627b81c74c050a40367ab3102b20",
      "mkdVersion": "1.1",
      "words": 232189,
      "nextWords": true,
      "licence": "CC-BY-4.0",
      "attribution": "Words and next-word statistics counted from the Leipzig Corpora Collection (hun_news_2024_1M), …"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `format` | The catalogue's own version, 1. It changes only for changes an older app would misread; an app that doesn't know the format offers no downloads. |
| `language` | The pack's BCP 47 tag, as in its own `META` (`de`, `pt-BR`, `hu`). A keyboard language takes the pack with its exact tag, else the first listed for its base language (`pt` → `pt-BR`). The app puts it in a file name, so anything but letters, digits and hyphens rejects the whole catalogue. |
| `name` | The language's name for itself, shown in the app. |
| `file` | The release asset's name. |
| `url` | Where to download it: absolute, or relative to the catalogue's own URL. `packRelease` writes the file name, so the same folder works from GitHub or a local server. |
| `size`, `sha256` | Bytes and lower-case hex SHA-256. A download that differs in either is thrown away before anything maps it. |
| `mkdVersion` | The pack's MKD version. Packs of a major version the app can't read are left out, so a newer catalogue still works in an older app. |
| `words`, `nextWords` | The word count, and whether the pack has next-word statistics (`NGRM`). |
| `licence`, `attribution` | SPDX expression for the whole pack, and the attribution its sources require, both from the pack's `META`. |

Fields the app doesn't know are ignored, so later catalogues can add some.

## Building the release folder

```
cd makeeb
./gradlew :tools:dictionaries:packRelease
```

This builds every pack from its pinned sources (English with `dictionaryPacks`, the others with `languagePacks`; mkd-format.md has the sources and sizes) and writes `makeeb/tools/dictionaries/build/pack-release/`:

- the nine downloadable packs (`de.mkd`, `es.mkd`, `fr.mkd`, `hu.mkd`, `it.mkd`, `nl.mkd`, `pl.mkd`, `pt_BR.mkd`, `sv.mkd`; 64 MB in all, 6.1–8.2 MB each);
- `catalogue.json`, listing them;
- `en_US.mkd`, which the apps bundle: builds download it instead of counting 517 MB of corpora (below). It isn't in the catalogue;
- `SHA256SUMS`, to check an upload by hand (`shasum -a 256 -c SHA256SUMS`).

The first run downloads about 2.1 GB of Leipzig corpora (plus English's 517 MB if it isn't cached) into `tools/dictionaries/build/downloads`; the counts are cached there too, so later runs take a minute or two. The writer is reproducible: the same sources always give the same bytes and hashes.

Nothing in the build publishes. To publish, upload every file in the folder as assets of one release, for example with `gh release create dictionaries-v1 build/pack-release/* --repo <owner>/<repo>`.

## Pointing the apps at it

One Gradle property, in `makeeb/gradle.properties`, says where the catalogue is:

```
makeeb.packs.catalogueUrl=https://github.com/<owner>/<repo>/releases/download/<tag>/catalogue.json
```

- It is empty until the repository exists. An empty URL builds apps that offer no downloads (Settings says so) and makes `en_US.mkd` from its sources.
- `:shared:companion` turns it into the constant `PackHosting.CATALOGUE_URL`, which the companion app's installer uses. The keyboard never reads it: it only maps packs that are already installed.
- A fixed tag keeps the URL stable. To update packs later, upload new assets to the same tag: the app sees a different `sha256` and offers the update. A new tag works too, but needs a new app build.
- `https://github.com/<owner>/<repo>/releases/latest/download/catalogue.json` also works, and follows whichever release is newest; use it only in a repository whose releases are all pack releases.

### English from the release

Once the URL is set, `dictionaryPacks` (which every Android and iOS build runs) downloads the published `en_US.mkd`, 6.6 MB, instead of building it from 517 MB of corpora. It is pinned like any other source: `PackSpecs.ENGLISH_US_RELEASE_SHA256`, today `9f80ed21568253055533e625dcae82e4c077b72baadf521d21653b4b3545c884` (6,642,361 bytes), the bytes `dictionaryPacks` builds. The download is cached in `build/downloads`, so offline builds keep working.

- `-Pmakeeb.dictionaries.english=build` builds English from its sources anyway (the typing harness wants its held-out sentences); `=download` fails instead of falling back. The default, `auto`, downloads when the URL is set and builds when it isn't or the download fails.
- `-Pmakeeb.dictionaries=false` still builds an app without the pack, as before.
- After changing how English is built, run `packRelease` (it prints a note when English no longer matches the pin), publish the folder, and pin the new hash.
