---
id: 62
title: GIF and sticker insertion
type: feature
priority: P3
effort: L
milestone: Later
category: emoji-symbols-media
android: full
ios: limited
seen_in: [Gboard, SwiftKey, CleverKeys, EweSticker]
created: 2026-09-27
---

Search or browse GIFs and sticker packs (online or offline packs) and insert them into apps that accept rich content.

## Platform notes

iOS: network needs Full Access, and a keyboard cannot insert images: copy to the pasteboard and prompt to paste. Android: commitContent when the field accepts image/gif.

## Acceptance criteria

- [ ] Written when the card is scheduled

## Tasks

- [ ] Broken down when the card is scheduled

## Progress

Providers (Sept 2026): Tenor API shut down 30 June 2026. KLIPY: free, Tenor-compatible API, optional ads with revenue share. GIPHY: free rate-limited beta key; production key needs approval. Plan: GifProvider port with KLIPY first; online-only, never in incognito or password fields; Android commitContent with a link fallback; iOS needs Full Access and inserts via the pasteboard.
