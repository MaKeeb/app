---
id: 141
title: Project website
type: feature
priority: P1
effort: M
milestone: '1.0'
category: foundation
android: full
ios: full
created: 2026-09-29
---

A landing page with features and screenshots, and the privacy policy the store listings will link to, published to GitHub Pages.

## Acceptance criteria

- [x] A landing page with features and screenshots
- [x] A privacy policy that matches the app
- [x] A Pages workflow builds it on every push
- [ ] Live at https://makeeb.github.io/site/ (Pages needs the repository public)
- [ ] A contact route on the privacy page

## Tasks

- [x] The Astro site, in MaKeeb/site
- [x] Screenshots from the test harness, status bars cropped
- [x] The Pages workflow

## Progress

Built 2026-09-29 in MaKeeb/site: Astro 7 (static, zero JavaScript, screenshots optimised to WebP at 1× and 2×), light and dark from the keyboard's palette, a landing page with the features in review or done and eight screenshots from the test harness (status bars cropped), and a privacy policy matching the app's privacy and network rules. Deploys to https://makeeb.github.io/site/ from .github/workflows/deploy.yml on every push; GitHub Pages on the free plan needs the repository public. Still to do: a contact route on the privacy page, store badges, light-mode and tablet screenshots, a social preview image.
