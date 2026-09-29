---
id: 37
title: Per-language dictionary packs
type: feature
priority: P0
effort: M
milestone: MVP
category: prediction-autocorrect
android: full
ios: full
modules: [tools:dictionaries, engine:packs, engine:dictionary, engine:prediction, platform:network, platform:storage, shared:keyboard, shared:companion, feature:settings, feature:onboarding, app:android, app:ios]
seen_in: [HeliBoard, FUTO Keyboard, AnySoftKeyboard, Unexpected Keyboard, Keyman, FlorisBoard]
created: 2026-09-27
---

Bundled and importable dictionaries per language in a documented format, so languages can be added without an app update.

## Platform notes

Offline-first Android keyboards have users download files in a browser and import them. On iOS, network access and a shared container both involve Full Access (see platform research). Wordlist licences vary per language.

## Acceptance criteria

- [x] Packs for German, Spanish, French, Italian, Dutch, Polish, Portuguese, Swedish and Hungarian, each with next-word statistics
- [x] A catalogue lists every pack with its size and SHA-256
- [x] The companion downloads, verifies and installs a pack where the keyboard can map it, on iOS without Full Access too
- [x] The keyboard uses every selected language's pack
- [x] Settings shows, downloads, updates and removes each language's dictionary, and setup asks for languages
- [ ] A way to publish the packs as release assets of MaKeeb/dicts
- [ ] The app reads the published catalogue

## Tasks

- [x] Build the packs from the AOSP word lists and the Leipzig corpora
- [x] A catalogue, a release folder, and English from the release
- [x] Ports to download packs and store them where the keyboard maps them
- [x] An installer that checks every pack before the keyboard can see it
- [x] The keyboard maps the selected languages' packs
- [x] Settings → Dictionaries
- [x] Setup's language step
- [x] Memory probe, device docs and a local-server test recipe
- [x] Check on the Pixel and the simulator against a local server
- [ ] Hosting in the MaKeeb org

## Progress

Built 2026-09-29. Packs for de, es, fr, it, nl, pl, pt (pt_BR), sv from AOSP LatinIME word lists (Apache-2.0, pinned) and hu from the Leipzig Hungarian news corpus (CC BY 4.0: 232k words, min count 3), each with next-word statistics from Leipzig news (6.4–8.2 MB per pack, 64 MB in all). `./gradlew :tools:dictionaries:packRelease` writes the upload folder: the packs, catalogue.json (format 1, URLs relative, SHA-256 per pack) and en_US.mkd; with the catalogue URL set, normal builds download the published en_US.mkd instead of 517 MB of corpora. The companion downloads and verifies packs (size, SHA-256, MKD header, CRC, language), atomically, into device-protected app storage (Android) or the App Group (iOS, readable by the extension without Full Access). The keyboard maps the selected languages' packs: the primary's is the main dictionary, the others vouch for their words (never corrected, never capitalised as another language's name); autocorrect's pause lifts once every selected language has a pack. Settings → Dictionaries per language (built in / download with size / progress / installed / update / remove / errors with retry); setup ends with Choose your languages. Android gained INTERNET (companion downloads only). Pixel (local server): Magyar downloaded, the pause note went, with Magyar primary szeretnem → Szeretném and Hungarian next words (az | a | nem); fixed English hello being capitalised as the Hungarian name Hello. iOS simulator (test23_packs): downloaded through the companion into the App Group (Installed · 8.2 MB), the extension types Szeretném. To publish: the user names the GitHub repo and tag; then set makeeb.packs.catalogueUrl and upload build/pack-release. To confirm: if the primary language has no pack, the next selected language with one stands in (not English).
