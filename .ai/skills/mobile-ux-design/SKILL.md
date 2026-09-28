---
name: mobile-ux-design
description: UX and visual design for MaKeeb on phones, tablets and foldables: key geometry and hit areas, feedback and previews, the suggestion strip, panels, one-handed/split/floating modes, Android and iOS conventions, accessibility (TalkBack, VoiceOver, font scale, contrast, reduced motion), theme tokens, and the companion app's onboarding and settings. Use when designing or changing anything the user sees or touches, or when reviewing screenshots.
---

# Mobile and tablet UX

People use a keyboard thousands of times a day, often one-handed, while looking at the text rather than the keys. Speed, predictability and never losing what they typed matter more than novelty. `docs/research/open-source-keyboards.md` §4 records what users praise and punish in other keyboards. Feature definitions are on the board (`.ai/kanban`).

## Principles

1. **Protect muscle memory.** Keys never move between modes or while settings load. The key area has a fixed height per preferences (`KeyboardMetrics`), so letters, symbols, emoji and clipboard all fill the same area. Don't animate layout changes, and don't reflow keys when suggestions change.
2. **Feedback on touch down, commit on release.** The pressed state, preview, haptic and click happen on down (`TouchListener.onKeyDown`). Text commits on up, so a user who slides to a neighbouring key fixes a miss. Down-to-feedback must fit in one frame.
3. **No dead zones.** Every point of the key area belongs to a key: `LayoutGeometry.keyAt` falls back to the nearest key in the row. Gaps are only visual.
4. **Everything automatic can be undone.** One Backspace undoes an autocorrection or a double-space period. If you add an automatic behaviour, add its undo in the same change.
5. **Same behaviour, native look.** Both platforms behave identically because behaviour lives in Kotlin. The platform look (globe key, return label, system font) follows each OS.
6. **Design for the worst case too:** no Full Access, a password field, a locked device, the largest font, a screen reader, a landscape phone, a 13" iPad.

## Key geometry

- On a phone in portrait the top row has 10 keys, so keys end up roughly 32–40 dp/pt wide. That is below the 48 dp (Material) and 44 pt (HIG) touch-target guidance. Keyboards accept this because the hit area covers the gaps, and touch-model correction (APP-12) will bias towards likely keys. Don't shrink keys below the current defaults.
- Draw keys as inset rectangles, but hit-test the whole cell.
- Height is 44 for the strip, plus 54 × `heightScale` (0.8–1.3) per row, times 4 rows (4.8 with the number row), plus bottom padding. That's about 264 dp by default. A landscape phone has roughly 360–430 dp of height, so the default keyboard leaves the app very little room. Landscape needs shorter rows, which aren't implemented yet.
- Tablets: don't stretch phone keys across 800+ dp. Either cap the key width and centre the keyboard, or use a tablet layout: extra keys (tab, caps lock, a dismiss key on iPad), a split layout in landscape (APP-79), or floating on Android (APP-80; iOS extensions can't float). Choose by width: compact below 600 dp, medium 600–840, expanded from 840 (Material breakpoints). iPad reports a regular horizontal size class.
- Foldables change width on fold, unfold and posture change. Recompute geometry on every size change (`KeyboardSession.setKeysAreaSize`), and never cache layouts per device.
- One-handed mode: a narrower keyboard docked left or right, with a quick control to switch sides or go back to full width. The empty side holds only that control, nothing that types.

## Previews, popups, long-press

- The key preview shows the character above the finger, inside the keyboard's bounds. iOS can't draw above its top edge, so previews for the top row overlap the strip. That is expected.
- No previews in password fields (APP-86), and none when `keyPopupPreview` is off.
- Long-press alternates open after a fixed delay with the most likely alternate pre-selected. The user slides to choose, releases to commit, and slides off to cancel. Keys with alternates show a hint glyph (`RenderKey.hint`).
- Timings (long-press delay, repeat rate) live in `TouchConfig` and will become user settings (APP-83) for motor accessibility. Never hard-code a timing in a renderer.

## Suggestion strip

- Three slots, with the best in the middle (`inStripOrder`). When the next space will autocorrect, mark the middle slot (bold or the accent colour) so the correction doesn't surprise anyone. The word the user actually typed must always be one tap away.
- The strip keeps the same height when empty. It turns into the quick-action bar there (APP-54) rather than collapsing.
- No suggestions in password fields, or when the field or the user turns them off.

## Panels (emoji, clipboard, symbols)

- Panels replace the key area at the same height. The keyboard never changes size.
- There is always a one-tap way back to letters, in the same place (bottom left).
- Emoji: recents first, a category row, a skin-tone picker on long-press, and search (APP-59, one of the most requested features in open-source keyboards) using the keyboard's own letters.
- Clipboard on iOS without Full Access: say plainly why the panel is empty and that Full Access can be turned on from the MaKeeb app. Say it once; don't nag.

## Platform conventions

| | Android | iOS |
| --- | --- | --- |
| Next keyboard | The system switcher in the nav bar; our globe key only when `shouldOfferSwitchingToNextInputMethod()` | Globe key when `needsInputModeSwitchKey`; long-press lists keyboards |
| Enter key | Icon or label from the `imeOptions` action (search, send, go, next, done) | Label from `returnKeyType`; disabled on an empty field when `enablesReturnKeyAutomatically` |
| Labels | System sans font | System font (SF) |
| Colour | Material You dynamic colour on 12+ (APP-70) | Follows light/dark and the host's `keyboardAppearance` |
| Dismiss | Back closes a panel first, then the keyboard | No Back; iPad layouts get a dismiss key |

## Accessibility

- **Screen readers.** Each key needs a spoken label, not its glyph ("shift", "delete", "capital A" when shifted), plus explore-by-touch with lift-to-type. On iOS, expose keys as `UIAccessibilityElement`s with the `.keyboardKey` trait, which VoiceOver's typing modes depend on. On Android, give each key semantics (Compose `Modifier.semantics`, role Button), or virtual nodes if the keys are drawn on one canvas. Announce suggestions and corrections. Android 16 deprecates `announceForAccessibility`, so use live regions or state descriptions instead. This is the APP-81 card, and the research found open-source keyboards rarely test it.
- **Font scale.** Labels scale with the system setting up to a cap, because keys don't grow. Compose labels are `sp` and scale on their own, so clamp them. On iOS, use `UIFontMetrics` with a maximum point size. Test at the largest setting.
- **Contrast.** Labels and hints need at least 4.5:1 against their key (WCAG 1.4.3). The pressed state and accent key need at least 3:1 against their neighbours (1.4.11). Offer a high-contrast theme rather than weakening the defaults. Check every palette change with a contrast calculator.
- **Motion.** Honour Android's "Remove animations" and iOS Reduce Motion: no preview animations and no sliding panels.
- **Never colour alone.** Shift and caps lock change the glyph as well as the colour.

## Theme tokens

- Both renderers use one palette: `KeyboardPalette` in `:core:model` (0xAARRGGBB). Compose converts it with `toColors()`, and Swift gets it from `KeyboardRender.palette(systemDark:)`. Add colours there, never in only one renderer.
- Spacing, radii and text sizes are currently duplicated: `KeyboardDimensions` in `:ui:theme` for Compose, and literals in `KeyboardView.swift`. When you change one, move the value into a shared token next to the palette instead of editing both.
- Design light and dark every time, and review screenshots of both.

## Companion app

- Onboarding is where most users drop off (research: setup friction). Keep only the steps the OS forces:
  - Android: enable MaKeeb in system settings, then select it in the picker. Android shows a warning that the keyboard "may collect all text you type". Before users see it, tell them plainly that what they type never leaves the device. Detect each state and move on automatically.
  - iOS: Settings → General → Keyboard → Keyboards → Add New Keyboard → MaKeeb. Full Access is optional. Say exactly what it adds (clipboard history, haptics, sound, online extras such as GIFs) and that typing works fully without it. Don't ask for it during first setup.
  - Finish with a "Try it" field so users can confirm it works.
- Settings follow the `KeyboardPreferences` groups (Typing, Feedback, Layout, Appearance). Every visual setting shows a live keyboard preview built from the same render model.

## Reviewing a visual change

- Capture light and dark, phone portrait and landscape, a tablet width, and the largest font.
- Android emulator (always `-s emulator-5554`): `shell wm size 1600x2560` and `shell wm density 320` fake a tablet, and `shell wm size reset` / `wm density reset` undo it. `shell settings put system font_scale 2.0` sets the largest font; set it back to `1.0` afterwards.
- iOS simulator: use an iPad device type, and Settings → Accessibility → Display & Text Size → Larger Text.
- Screenshots go where `build-and-verify` says. Curated ones go in `docs/screenshots/`.
