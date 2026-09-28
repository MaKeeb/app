# MaKeeb visual test plan

A repeatable pass over everything the keyboard shows, on Android and iOS, with the stock keyboard as the reference. Run it after any change to rendering, layout, theming, insets, icons or the companion app, and before a release.

- **Android**: a physical phone with gesture navigation (reference: Pixel 6 Pro, Android 17, 1440×3120, 560 dpi), stock keyboard Gboard. Steps that would expose personal data (clipboard) run on the emulator instead.
- **iOS**: the simulator (reference: iPhone 17, iOS 27), stock keyboard Apple's English (US). Driven by the `MaKeebUITests` UI-test target; press-and-hold states are captured from the host with `simctl io screenshot`.
- **Where things go**: raw captures and comparison sheets in `.ai/local/visual-test/<run>/` (gitignored). A run report (`report.md`) goes next to them. Curated images that document a decision go in `docs/screenshots/`.
- **Harness**: the companion app's **Try it** tab has one field per input type (text, e-mail, URL, number, phone, password, search, send, multi-line), labelled, so every case starts from a known field.

## How to judge a screenshot

Every case lists its own expectations. These apply to all of them:

1. **Nothing overlaps system UI.** On Android the bottom row clears the navigation-bar buttons (back/hide, keyboard switcher) by at least the gap Gboard leaves. On iOS the keyboard ends above the system globe/dictation bar and the home indicator.
2. **No key touches a screen edge.** Android keeps a 10dp side inset for curved edges; iOS keys run to the key gap (3pt), like the system keyboard. Nothing is clipped by curved edges or rounded corners.
3. **Constant height.** Letters, symbols, number pad, emoji and clipboard all occupy the same key-area height; switching never makes the app underneath jump.
4. **Icons are icons.** Function keys show real glyphs (no Unicode arrows, emoji or tofu), sized like the stock keyboard's, not squashed or clipped.
5. **Readable.** Labels and icons have at least 4.5:1 contrast against their key. Function keys are visibly distinct from letter keys in both themes. The accent (return) key stands out.
6. **Same behaviour, native look.** Android and iOS show the same keys and states. Platform differences are limited to what each OS dictates (the globe key, return labels, system fonts).
7. **Stock parity.** Differences from the stock keyboard are deliberate, not accidental: margins, key proportions, spacing, strip height.

## Cases

Legend: **A** = Android, **i** = iOS, **auto** = captured by the scripts, **manual** = needs a person (with the reason).

### Presence and geometry

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-01 | Idle letters | Open Try it, focus **Text** | QWERTY with digit hints on the top row. The strip shows the toolbar (clipboard, settings on Android). Shift is outlined unless the field auto-capitalises. | auto | auto |
| VT-02 | Bottom clearance | Same screen, look at the bottom edge | Bottom row fully above the system buttons (A) / globe bar and home indicator (i), with a margin comparable to stock | auto | auto |
| VT-03 | Edges | Same screen | Side inset on both sides. `q`, `p`, `a`, `l`, shift, delete, `?123` and return are not on the curved edge. | auto | auto |
| VT-04 | Stock reference | Switch to the stock keyboard on the same field | Reference capture for VT-01..03 | auto | auto |

### Keys and states

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-05 | Shift one-shot | Tap shift once | Filled arrow, key lit to the letter-key tone, letters uppercase | auto | auto |
| VT-06 | Caps lock | Double-tap shift | Caps-lock glyph (filled arrow with bar), letters uppercase | auto | auto |
| VT-07 | Symbols | Tap `?123` | Digits, `@#$_&-+()/`, `=\<` key, `ABC` bottom-left; same rows as letters: the bottom row, backspace and the `=\<`/shift slot don't move (holding `ABC` opens the number pad) | auto | auto |
| VT-08 | More symbols | Tap `=\<` | Maths and currency rows (~ • √ π ÷ × ¶ ∆, £ ¢ € ¥ ^ ° = { }), `?123` key; no key shared with symbols moves | auto | auto |
| VT-09 | Number row | Settings → Number row on, back to Try it | Digit row above the letters, 80% row height, no digit hints, total height grows by 0.8 rows; both symbols pages keep the same digit row and row heights | auto | auto |
| VT-10 | Key preview | Press and hold `g` | Enlarged `g` bubble above the key, inside the keyboard | auto | auto (host capture) |
| VT-11 | Top-row preview | Press and hold `q` | Bubble overlaps the strip and never leaves the keyboard's bounds (iOS cannot draw above the keyboard) | auto | auto (host capture) |
| VT-12 | Alternates popup | Long-press `e` | Popup row (`3 é è ê ë ē ė ę`), first option highlighted, clamped inside the keyboard | auto | auto (host capture) |

### Typing results

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-13 | Suggestions | Type `hel` | Strip shows three suggestions, best in the middle in bold (`he · hello · help`) | auto | auto |
| VT-14 | Autocorrect | Type `teh ` | Field reads `the ` | auto | auto |
| VT-15 | Revert | Then tap delete | Field reads `teh` | auto | auto |
| VT-16 | Sentence start | Focus **Text** (sentence caps), type `hi. ok` | `Hi. Ok`: shift engaged at the start and after `. ` | auto | auto |

### Field types

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-17 | E-mail | Focus **E-mail** | Letters with `@` in place of the comma, no auto-shift. Typing shows no suggestions and never autocorrects. | auto | auto |
| VT-18 | URL / Go | Focus **URL** | `/` and `.com` keys; return key shows the *go* arrow | auto | auto |
| VT-19 | Number | Focus **Number** | 4×4 number pad, `ABC` bottom-left | auto | auto |
| VT-20 | Phone | Focus **Phone** | Phone pad with letters under the digits (ABC…), `* 0 #`, `+`, and a space key with a space-bar glyph. iOS always shows its own phone pad. | auto | auto |
| VT-21 | Password | Focus **Password**, type `abc` | No suggestions and no key preview; nothing learned. On iOS the system keyboard takes over by design. | auto | auto |
| VT-22 | Search | Focus **Search** | Return key shows the search glyph | auto | auto |
| VT-23 | Send | Focus **Send** | Return key shows the send glyph | auto | auto |
| VT-24 | Multi-line | Focus **Multi-line** | Return key shows the return glyph and inserts a newline | auto | auto |

### Panels

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-25 | Emoji | Tap the emoji key | Category row, emoji grid, `ABC` and delete at the bottom, same height as the keys | auto | auto |
| VT-26 | Clipboard | Copy a known string, tap the strip's clipboard button | Card with the string, Pin/Delete actions; a second tap on the button returns to the keys. Emulator only on Android (personal clipboard). iOS needs Full Access; without it the panel explains why it is empty. | auto (emulator) | auto |

### Appearance

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-27 | Light theme | System light mode, VT-01 and VT-07 | Light palette, contrast rules hold | auto | auto |
| VT-28 | Dark theme | System dark mode, VT-01 and VT-07 | Dark palette; function keys lighter than letter keys | auto | auto |
| VT-29 | Landscape | Rotate to landscape, VT-01 | Keys span the width without distortion; no overlap with system UI. Known gap: rows are not shortened in landscape yet (board: APP-20). | auto | auto |

### Companion app

| ID | Case | Steps | Expected | A | i |
| --- | --- | --- | --- | --- | --- |
| VT-30 | Setup and Settings | Open Setup, then Settings | Readable in both themes; setup reflects the real state on Android; settings sections complete | auto | auto |

## Running it

- **Android**: `.ai/local/visual-test/run-android.py` (kept local, it targets a specific device serial). It records the phone's keyboard, theme and rotation, runs the cases with `adb input tap` and `input motionevent`, captures with `screencap`, and restores everything at the end.
- **iOS**: `.ai/local/visual-test/run-ios.py <udid> <out-dir>` runs `MaKeebUITests/KeyboardVisualTests` three times: dark references, light references, then the cases. It reboots the simulator before each pass and captures press-and-hold states from the host (`simctl io screenshot`) when a test drops a `.hold-<name>` marker, because XCUITest blocks while a press is held. `MAKEEB_SCREENSHOT_DIR` and `MAKEEB_THEME` reach the test runner as `TEST_RUNNER_…` variables. Use a dedicated simulator, never one someone is working in. Setup and quirks:
  - **Enable MaKeeb without Settings.** UI tests can't drive the Settings app (`kAXErrorServerNotFound`). With the simulator shut down, add `com.makeeb.ios.keyboard` to `AppleKeyboards` in its `data/Library/Preferences/.GlobalPreferences.plist` and set `AppleKeyboardsExpanded` to 1.
  - **Turn off the hardware keyboard.** A headless simulator connects the Mac's keyboard and shows only the globe/dictation bar. Set `ConnectHardwareKeyboard` to false for the UDID in `com.apple.iphonesimulator` `DevicePreferences`; it applies at boot.
  - **Which keyboard is up.** iOS reopens the last-used keyboard. MaKeeb's keys are accessibility elements with `key-…` identifiers, and Apple's are not, so the tests detect each by identifier, count only on-screen keys (hidden ones linger in the tree), and cycle with the globe until MaKeeb shows.
  - **Cold start.** A debug build of the extension takes 2–5 s to appear, so after each globe tap the tests wait up to 10 s before tapping again. Switching away mid-launch makes iOS fall back to the system keyboard.
  - **Fields.** Compose doesn't expose every field's label, so the tests find the QA fields by test tag (`qa-<label>`, the accessibility identifier on iOS) and tap them by coordinates (Compose elements don't report hittable).
  - **Landscape.** Screenshots come out in the portrait framebuffer; `compose-sheets.py` rotates them.
  - **Signing.** Build signed (the default ad hoc simulator signing); an unsigned build has no App Group, so settings such as the number row never reach the keyboard.
  - **A quiet host.** Keyboard launches slow down dramatically under load, and iOS gives up on a slow keyboard and shows its own. Run with one simulator booted and nothing else heavy running.
- **Android emulator**: its virtual hardware keyboard stops on-screen keyboards from showing. Run `adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1` once.
- **Sheets**: `compose-sheets.py` puts stock | MaKeeb (per platform) and Android | iOS (per case) side by side and writes `report.md` with a verdict per case.

## Recording results

For each case record **pass**, **fail** (what is wrong, with the sheet) or **n/a** (why). Every fail becomes, or is attached to, a card on the feature board.
