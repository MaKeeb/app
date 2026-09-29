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
modules: [engine:dictionary]
seen_in: [HeliBoard, FUTO Keyboard, AnySoftKeyboard, Unexpected Keyboard, Keyman, FlorisBoard]
created: 2026-09-27
---

Bundled and importable dictionaries per language in a documented format, so languages can be added without an app update.

## Platform notes

Offline-first Android keyboards have users download files in a browser and import them. On iOS, network access and a shared container both involve Full Access (see platform research). Wordlist licences vary per language.

## Acceptance criteria

- [ ] Packs for German, Spanish, French, Italian, Dutch, Polish, Portuguese, Swedish and Hungarian, each with next-word statistics
- [ ] A catalogue lists every pack with its size and SHA-256
- [ ] The companion downloads, verifies and installs a pack where the keyboard can map it, on iOS without Full Access too
- [ ] The keyboard uses every selected language's pack
- [ ] Settings shows, downloads, updates and removes each language's dictionary, and setup asks for languages
- [ ] A way to publish the packs as release assets of MaKeeb/dicts
- [ ] The app reads the published catalogue

## Tasks

- [x] Build the packs from the AOSP word lists and the Leipzig corpora
- [ ] A catalogue, a release folder, and English from the release
- [ ] Ports to download packs and store them where the keyboard maps them
- [ ] An installer that checks every pack before the keyboard can see it
- [ ] The keyboard maps the selected languages' packs
- [ ] Settings → Dictionaries
- [ ] Setup's language step
- [ ] Memory probe, device docs and a local-server test recipe
- [ ] Check on the Pixel and the simulator against a local server
- [ ] Hosting in the MaKeeb org

## Progress

Decided 2026-09-29: English ships in the app; other languages are packs hosted on GitHub (release assets), downloaded during setup like Gboard and later from settings. Share-alike (CC BY-SA) packs are acceptable. The MKD format and the mapped reader exist since APP-110. Build size (2026-09-29): the Leipzig corpora download is 517 MB because Leipzig ships each corpus as a full research bundle (inverted word index, source URLs, co-occurrence tables); the builder reads only the sentences (~225 MB of ~1.2 GB unpacked). Plan: host the built en_US.mkd (6.5 MB) as a pinned GitHub release asset like the other packs, so normal builds skip the corpora.
