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

Nothing in the build publishes. Releases are published from the `MaKeeb/dicts` repository, checked out next to this one: `scripts/publish.sh --check` verifies the folder, and `scripts/publish.sh packs-YYYY-MM-DD` creates the release (see its `publish-packs` skill). Every build gets a new tag; published assets are never replaced.

## Pointing the apps at it

One Gradle property, in `makeeb/gradle.properties`, says where the catalogue is:

```
makeeb.packs.catalogueUrl=https://github.com/MaKeeb/dicts/releases/latest/download/catalogue.json
```

- It stays empty until `MaKeeb/dicts` is public: GitHub serves release assets without a login only for public repositories, and the app downloads without one. An empty URL builds apps that offer no downloads (Settings says so) and makes `en_US.mkd` from its sources.
- `:shared:companion` turns it into the constant `PackHosting.CATALOGUE_URL`, which the companion app's installer uses. The keyboard never reads it: it only maps packs that are already installed.
- `latest` follows the newest release, and every release of `MaKeeb/dicts` is a pack release, so a new release reaches installed apps without an app build: they see a different `sha256` and offer the update. Releases are immutable, so apps never find a pack changed under a hash they already have.

### English from the release

Once the URL is set, `dictionaryPacks` (which every Android and iOS build runs) downloads the published `en_US.mkd`, 6.6 MB, instead of building it from 517 MB of corpora. It is pinned like any other source: `PackSpecs.ENGLISH_US_RELEASE_SHA256`, today `9f80ed21568253055533e625dcae82e4c077b72baadf521d21653b4b3545c884` (6,642,361 bytes), the bytes `dictionaryPacks` builds. The download is cached in `build/downloads`, so offline builds keep working.

- `-Pmakeeb.dictionaries.english=build` builds English from its sources anyway (the typing harness wants its held-out sentences); `=download` fails instead of falling back. The default, `auto`, downloads when the URL is set and builds when it isn't or the download fails.
- `-Pmakeeb.dictionaries=false` still builds an app without the pack, as before.
- After changing how English is built, run `packRelease` (it prints a note when English no longer matches the pin), publish the folder, and pin the new hash.

## On the device

### Downloading and installing

`PackInstaller` (`makeeb/engine/packs`) does it for the companion app. There is one per app (`companionModule`), shared by setup and Settings, so a download started in one shows its progress in the other and doesn't stop when the screen changes. Nothing on the keyboard's typing path uses it: the keyboard only maps what is installed.

1. On every resume of Setup or Settings it lists the installed packs, and fetches the catalogue (at most 1 MB) unless it has it. Offline, or with the server down, installed packs keep working and the screens say so, with Retry.
2. A download streams (`HttpTransport`: `HttpURLConnection` on Android, an ephemeral `NSURLSession` on iOS) into `<name>.part`, hashing as it goes. A wrong `Content-Length`, or more bytes than the catalogue's size, stops it at once.
3. Then it checks the size, the SHA-256, the MKD header and its CRC-32 over the whole file, and that the pack's `META` language is the catalogue's. Only then is the file synced and renamed into place in one step, as `<language>-<first 16 hex digits of its SHA-256>.mkd` (`InstalledPack`), and any older version of that language is deleted.
4. Anything that fails leaves nothing behind: the reason (network, damaged download, storage) is shown on the row, which offers to try again. A download killed with the app leaves a `.part` file, which the next refresh sweeps away.

The name carries the version, so a new version never overwrites a file the keyboard may have mapped (in another process, on iOS): mapped bytes never change under it.

### Where packs are stored

| | Android | iOS |
|---|---|---|
| Directory | `packs/` in the app's device-protected `noBackupFilesDir` (`DirectoryPackFiles`) | `Library/Application Support/Packs` in the App Group container `group.com.makeeb` (`AppGroupPackFiles`), excluded from backups |
| Written by | the companion app, in the same process as the keyboard | the companion app only |
| Read by | the keyboard, mapped with `FileChannel.map` | the keyboard extension, mapped with `mmap`; it can read the App Group without Full Access |
| Before the first unlock | readable (device-protected storage), like the preferences | iOS's default file protection: readable from the first unlock, like the App Group preferences |

Packs aren't personal data, so they sit outside credential-encrypted storage on Android; they can be downloaded again, so both platforms keep them out of backups (Apple's data storage guidelines).

### What the keyboard loads

`DictionaryLoader` (`makeeb/shared/keyboard`) maps packs off the main thread and swaps them in when they're ready (the starter list answers until then):

- The primary language's pack is the main dictionary: completions, corrections and next words. English uses the bundled pack.
- If the primary has no pack, the next selected language with one stands in, and English when none has.
- The other selected languages' packs are mapped too, but only vouch for words (`SelectedDictionaries`): a word any of them spells is never autocorrected, so English typed with Hungarian primary stays English. Merging their suggestions is the APP-18 card.
- Autocorrect replaces words only while every selected language has a lexicon (`Dictionary.covers`); otherwise it offers the correction first in the strip. That now lifts once a language's pack is installed. Settings' autocorrect note reads the same installed packs (`PacksState.lexiconLanguages`).
- It resolves again when the languages change and at every field (`KeyboardSession.start`), so a pack installed or removed in the companion is picked up: at once on Android (same process), and at the next `viewWillAppear` on iOS. Packs no longer selected are dropped, and their mappings are released once nothing reads them (a cleaner unmaps them on iOS).

### Memory

Mapped packs are clean, file-backed pages. iOS doesn't count them toward the extension's `phys_footprint`, and either OS can evict and re-read them. What a pack costs the heap was measured on the JVM with `./gradlew :tools:dictionaries:packMemory` (2026-09-29; it maps `hu`, `en_US` and `de` and types 2,000 common words through the suggestion engine):

- The first pack keeps about 150 KB of heap, most of it one-off state that any pack needs (classes' constants, the fold tables). Each further pack keeps about 4.5 KB.
- Heap after typing 16,000 keys with all three mapped: 0.6 MB over the baseline before mapping, the two suggestion engines and their caches included.
- Per-key cost with two other packs vouching for words is the same as with one pack: 248.6 against 249.1 µs per key with English primary (Hungarian and Swedish vouching), within noise with Hungarian primary. Corrections dominate it, as before (input-engine skill).
- Three packs map about 22 MB of address space, all of it clean.

So two or three packs stay well inside the extension's 30 MB budget. Confirm on a device with `makeeb/scripts/ios-memory-check.py` once packs are installed; the simulator doesn't enforce the limit.

## Testing with devices before publishing

A local server stands in for the release. Build the folder and serve it (Python's built-in server is enough):

```
cd makeeb
./gradlew :tools:dictionaries:packRelease
python3 -m http.server 8000 --directory tools/dictionaries/build/pack-release
```

Then build the apps against it; `-P` overrides `gradle.properties` for one build. The same URL makes the build take `en_US.mkd` from the server too, which the pinned hash accepts.

- **Android** (the Pixel or the emulator): forward the port so the phone's `localhost` is the Mac, and build a debug APK (only debug builds allow plain HTTP, and only to `localhost`, `127.0.0.1` and `10.0.2.2`):

  ```
  adb -s <serial> reverse tcp:8000 tcp:8000
  ./gradlew :app:android:assembleDebug -Pmakeeb.packs.catalogueUrl=http://localhost:8000/catalogue.json
  adb -s <serial> install -r app/android/build/outputs/apk/debug/android-debug.apk
  ```

- **iOS simulator:** the simulator shares the Mac's network, so `http://localhost:8000/catalogue.json` works. Xcode's pre-build phase runs Gradle, which doesn't see `-P`, so put the line `makeeb.packs.catalogueUrl=http://localhost:8000/catalogue.json` in `~/.gradle/gradle.properties` for the build, then build and run the `MaKeeb` scheme.
- **iOS device:** use the Mac's local hostname (System Settings → General → Sharing → Local hostname), serve on every interface with `--bind 0.0.0.0`, and set `makeeb.packs.catalogueUrl=http://<name>.local:8000/catalogue.json` the same way. The app allows plain HTTP only to local hosts (`NSAllowsLocalNetworking`) and asks for local network access the first time.

Remove the override afterwards.

What to check on each platform:

1. Setup → "Choose your languages": pick Magyar. Its row reads "Not downloaded · 8.2 MB"; Download shows progress, then "Installed · 8.2 MB". The step turns done once every selected language has its dictionary; "Skip for now" folds it away.
2. Settings → Layout → Dictionaries shows the same rows. The autocorrect note ("… while Magyar has no dictionary") disappears once the pack is installed. Remove brings the note back.
3. The keyboard, Magyar primary with English also selected: Hungarian completions and next words ("szeret" offers "szeretnék"), a Hungarian typo corrected, an English word such as "hello" left alone, and English suggestions back when English is made primary (hold the space bar).
4. iOS without Full Access: the same, since the extension only reads the App Group.
5. Offline (airplane mode): Settings says it can't reach the downloads and offers Retry; the keyboard keeps its installed packs.
6. Kill the app during a download: nothing is installed, and the next visit to Settings starts clean.
7. Android: after a reboot, before unlocking, the keyboard on the lock screen still uses the installed pack.
8. iOS memory, on a device: `scripts/ios-memory-check.py` with two or three languages selected; the extension should stay under 30 MB while typing.
