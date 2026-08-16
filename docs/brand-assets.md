# Brand assets — provenance

Where the bundled fonts and the logo artwork came from, and why they are shaped
the way they are. The iOS port has the same file; keep them in step.

## Fonts

| Family | Weights bundled | Licence | Upstream |
|---|---|---|---|
| Inter 4.1 | Regular 400, Medium 500, SemiBold 600, Bold 700 | SIL OFL 1.1 | [rsms/inter v4.1](https://github.com/rsms/inter/releases/tag/v4.1), `extras/ttf/` |
| Space Grotesk | Regular 400, Medium 500, Bold 700 | SIL OFL 1.1 | [floriankarsten/space-grotesk](https://github.com/floriankarsten/space-grotesk), `fonts/ttf/static/` |

Files live in `app/src/main/res/font/`, renamed to Android's resource rules
(lowercase, underscores). Licence texts ship **inside the APK** at
`app/src/main/assets/licenses/` — OFL 1.1 clause 2 requires the licence to
travel with the font, and "it is in the repo" is not the same as "it is in the
thing we distribute".

Static instances rather than the variable files. Both families publish a
variable TTF that would be one smaller file, but selecting a weight from a
variable font is a different mechanism on each platform, and getting it subtly
wrong renders at the default weight without erroring. Static instances are
boring and identical on both.

Measured cost: **0.93 MB** added to the release APK (7 faces + 2 licences,
after compression and R8 resource shrinking). Uncompressed the faces are 2.0 MB;
Inter carries Latin, Greek and Cyrillic, which is why each face is ~410 KB.

### Which face renders what

Mirrors `weekclip-design-system/packages/tokens/tokens.json`:

- `typography.fontFamily.base` → **Inter** → body, labels, list-row titles
- `typography.fontFamily.heading` → **Space Grotesk** → display, headline,
  `titleLarge`

Headings are Medium (500), because web sets `h1..h6 { font-weight: 500 }` in
`styles.css`. Material's own size/line-height scale is kept; only the face and
that weight change. Inventing a native type scale is a design decision the
design system has not made — `tokens.json` has a web pixel ramp (11…28px) that
does not map onto Material's fourteen roles without someone deciding how.

### CJK is deliberately not bundled

The web stacks name Noto Sans KR / JP / SC after Inter, and
`weekclip-web/src/platform/i18n/fonts.ts` fetches whichever the locale needs,
sliced by `unicode-range` down to ~30–60 KB of actual glyphs.

There is no such slicing for a file in an APK. A full Noto CJK face is 5–10 MB
and there are three of them, so bundling would roughly quadruple the app to ship
glyphs the phone already has.

Neither Inter nor Space Grotesk contains a single Hangul, kana or Han glyph, so
Android's font fallback resolves those runs to the system CJK face by itself.
That is the same outcome the web stacks describe — Latin keeps the brand face,
CJK falls through — with a better source for the fallback.

## Logo

Canonical vector source:
`weekclip-design-system/packages/assets/logo/logo.svg` — one filled path in a
`0 0 687 687` viewBox, `#5B53FF` (`color.accent.default`).

Every drawable here carries that file's `d` attribute **byte for byte**. No
re-drawing and no simplification, so the launcher icon, the in-app mark and the
web header are the same outlines.

| Resource | Purpose |
|---|---|
| `drawable/ic_launcher_foreground.xml` | Adaptive-icon foreground |
| `drawable/ic_launcher_monochrome.xml` | Themed icon (Android 13+), same geometry |
| `drawable/ic_weekclip_logo.xml` | In-app mark, source viewBox, no transform |
| `values/ic_launcher_background.xml` | `#0C0E16` = `color.bg.panel` (dark) |

The launcher background matches the plate in the design system's own
`favicon.svg`, so the launcher icon, the browser favicon and the PWA mark agree.

### The adaptive-icon transform

The path's ink is 539.422 × 610.100 centred at (343.211, 344.141) — **not** the
centre of its own 687 viewBox. Scaling alone therefore leaves the mark visibly
low and left once a launcher applies a circular mask. The `<group>` maps ink
centre onto (54, 54):

```
scale = 62 / 610.100 = 0.1016227
tx    = 54 - 343.211 × scale = 19.1221
ty    = 54 - 344.141 × scale = 19.0276
```

`BrandAssetsTest.kt` re-derives this from the XML on every run and fails if the
result leaves the 66dp keyline (21…87) or drifts off centre by more than 0.5dp.

## Wordmark casing

The design system's lockup spells it **WeekClip** (`.wc-logo-lockup__name`), and
`R.string.brand_name` follows. `R.string.app_name` — the launcher label — spells
it "Weekclip". The two disagree, that predates this work, and changing a
launcher label is a product decision rather than an asset one.

## Checks

- `scripts/check-brand-assets.sh` — wiring: fonts referenced and licensed, no
  placeholder palette, launcher background is the token. Runs in CI before the
  toolchain.
- `app/src/test/.../BrandAssetsTest.kt` — icon geometry, the three drawables
  agreeing, and that every bundled `.ttf` really is TrueType.
