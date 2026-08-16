package cc.sunglint.weekclip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cc.sunglint.weekclip.R
import cc.sunglint.weekclip.ui.theme.InterFontFamily
import cc.sunglint.weekclip.ui.theme.WeekclipTheme

/**
 * The brand mark and the mark+wordmark lockup.
 *
 * This is a port of the design system's `.wc-logo` / `.wc-logo-lockup`
 * (`weekclip-design-system/packages/web/src/components/logo.css`), which states
 * its markup contract in a header comment. The sizes, the 8px gap, the bold
 * 16px wordmark and its -0.01em tracking are that file's values.
 *
 * The one thing not ported literally is *how* the mark takes its colour. On web
 * the SVG is a `mask-image` and `background-color` paints through it, so the
 * mark inherits a token. Compose has the same idea in [Icon], which applies the
 * tint as a ColorFilter over the drawable — so `ic_weekclip_logo.xml` can carry
 * the accent as its own fill (correct when drawn directly) and still be
 * recoloured here.
 */
enum class WeekclipLogoSize(val dp: Dp) {
  /** `.wc-logo--sm` */
  Small(20.dp),

  /** `.wc-logo` */
  Medium(28.dp),

  /** `.wc-logo--lg` */
  Large(40.dp),

  /** `.wc-logo--xl` */
  ExtraLarge(64.dp)
}

/**
 * The mark on its own.
 *
 * @param contentDescription what a screen reader announces. Pass `null` when
 *   the mark sits next to the wordmark — [WeekclipLogoLockup] does, because the
 *   wordmark is already readable text and announcing "WeekClip WeekClip" is the
 *   default outcome of tagging both.
 */
@Composable
fun WeekclipLogoMark(
  modifier: Modifier = Modifier,
  size: WeekclipLogoSize = WeekclipLogoSize.Medium,
  contentDescription: String? = stringResource(R.string.brand_name),
  tint: Color = MaterialTheme.colorScheme.primary
) {
  Icon(
    painter = painterResource(R.drawable.ic_weekclip_logo),
    contentDescription = contentDescription,
    tint = tint,
    modifier = modifier.size(size.dp).testTag("brand-logo")
  )
}

/**
 * Mark + "WeekClip" wordmark, the horizontal lockup.
 *
 * The wordmark is the base face (Inter) at Bold, not the heading face — web's
 * `.wc-logo-lockup` sets `font-family: var(--typography-font-family-base)` and
 * `font-weight: var(--typography-font-weight-bold)`. Space Grotesk is for
 * headings; the wordmark is neither.
 *
 * `clearAndSetSemantics` collapses the row to one accessibility node reading
 * "WeekClip", instead of an untagged image followed by a text node.
 */
@Composable
fun WeekclipLogoLockup(
  modifier: Modifier = Modifier,
  size: WeekclipLogoSize = WeekclipLogoSize.Medium,
  tint: Color = MaterialTheme.colorScheme.primary
) {
  val name = stringResource(R.string.brand_name)

  Row(
    modifier = modifier
      .clearAndSetSemantics { contentDescription = name }
      .testTag("brand-lockup"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    WeekclipLogoMark(size = size, contentDescription = null, tint = tint)
    Text(
      text = name,
      color = MaterialTheme.colorScheme.onBackground,
      fontFamily = InterFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 16.sp,
      letterSpacing = (-0.01).em
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun WeekclipLogoLockupPreview() {
  WeekclipTheme {
    WeekclipLogoLockup()
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WeekclipLogoMarkPreview() {
  WeekclipTheme(darkTheme = true) {
    WeekclipLogoMark(size = WeekclipLogoSize.ExtraLarge)
  }
}
