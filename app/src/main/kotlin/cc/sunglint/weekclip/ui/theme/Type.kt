package cc.sunglint.weekclip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cc.sunglint.weekclip.R

/**
 * Brand typography — the two faces weekclip-web already ships.
 *
 * `weekclip-design-system` names them in `typography.fontFamily`:
 *
 *   base     "Inter", -apple-system, …          -> body, labels
 *   heading  "Space Grotesk", "Inter", …        -> display, headline, titleLarge
 *
 * Both are SIL OFL 1.1 and bundled (`res/font/`, licences in
 * `assets/licenses/`). Provenance and the reason each weight exists is in
 * `docs/brand-assets.md`.
 *
 * ## CJK is deliberately NOT bundled
 *
 * The web stacks name "Noto Sans KR" / "JP" / "SC" after Inter, and
 * `weekclip-web/src/platform/i18n/fonts.ts` fetches whichever one the locale
 * needs. A full Noto CJK face is 5–10 MB and there is no `unicode-range`
 * slicing off a local file — bundling three would roughly quadruple the APK to
 * serve glyphs the platform already has.
 *
 * Neither Inter nor Space Grotesk carries a single Hangul, kana or Han glyph,
 * so Android's font fallback resolves those runs to the system CJK face on its
 * own. That is exactly the behaviour the web stacks describe: Latin keeps the
 * brand face, CJK falls through. The difference is only *where* the fallback
 * face comes from, and on a phone that is a better source than a download.
 *
 * ## Why the weights are pinned per style rather than left to Material
 *
 * These `FontFamily` declarations map a [FontWeight] to a *file*. Ask for a
 * weight with no file and Compose synthesises it by smearing the nearest one,
 * which looks like a rendering bug and is hard to trace back to a missing
 * `Font(...)` line. So the styles below only ever name a weight this file
 * supplies — Normal/Medium/SemiBold/Bold for Inter, Normal/Medium/Bold for
 * Space Grotesk.
 */
val InterFontFamily = FontFamily(
  Font(R.font.inter_regular, FontWeight.Normal),
  Font(R.font.inter_medium, FontWeight.Medium),
  Font(R.font.inter_semibold, FontWeight.SemiBold),
  Font(R.font.inter_bold, FontWeight.Bold)
)

val SpaceGroteskFontFamily = FontFamily(
  Font(R.font.space_grotesk_regular, FontWeight.Normal),
  Font(R.font.space_grotesk_medium, FontWeight.Medium),
  Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

/**
 * Material 3's own scale, with the family (and heading weight) swapped.
 *
 * Sizes, line heights and letter spacing are left exactly as Material defines
 * them. Inventing a type scale is a design decision the design system has not
 * made for native yet — `tokens.json` carries a web pixel ramp (11…28px) that
 * does not map onto Material's fourteen roles without someone deciding how.
 * Until that decision exists, borrowing Material's ramp is the honest default;
 * what this file *does* assert is which face renders it.
 *
 * Headings are Medium (500) because that is what web sets — `styles.css`
 * `h1..h6 { font-weight: 500 }` — not Material's Normal.
 */
private val Default = Typography()

val WeekclipTypography = Typography(
  // --- heading face: Space Grotesk -----------------------------------------
  displayLarge = Default.displayLarge.headingFace(),
  displayMedium = Default.displayMedium.headingFace(),
  displaySmall = Default.displaySmall.headingFace(),
  headlineLarge = Default.headlineLarge.headingFace(),
  headlineMedium = Default.headlineMedium.headingFace(),
  headlineSmall = Default.headlineSmall.headingFace(),
  // titleLarge is the app-bar/brand role — `.topbar .brand` uses the heading
  // family on web. titleMedium/Small below are row and section labels, which
  // web renders in the base family, so they stay Inter.
  titleLarge = Default.titleLarge.headingFace(),

  // --- base face: Inter -----------------------------------------------------
  titleMedium = Default.titleMedium.baseFace(),
  titleSmall = Default.titleSmall.baseFace(),
  bodyLarge = Default.bodyLarge.baseFace(),
  bodyMedium = Default.bodyMedium.baseFace(),
  bodySmall = Default.bodySmall.baseFace(),
  labelLarge = Default.labelLarge.baseFace(),
  labelMedium = Default.labelMedium.baseFace(),
  labelSmall = Default.labelSmall.baseFace()
)

private fun TextStyle.headingFace(): TextStyle =
  copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Medium)

/** Keeps Material's own weight for the role; only the family changes. */
private fun TextStyle.baseFace(): TextStyle = copy(fontFamily = InterFontFamily)
