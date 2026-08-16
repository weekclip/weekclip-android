package cc.sunglint.weekclip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The Material 3 roles that actually get painted, mapped onto the design
 * system's tokens (see [WeekclipAccent] and friends for provenance).
 *
 * The previous version set three roles — primary, background, surface — and let
 * Material derive the rest from its baseline purple. That is why a card drawn
 * by `CardDefaults` came out in a colour no token names: `Card` reads
 * `surfaceContainerHighest`, not `surface`, so the brand stopped at the edges
 * of anything Material fills for you. The container ramp below is the design
 * system's own `bg.*` ramp in that role's order, which is the mapping the
 * ramp was already built for.
 *
 * Dynamic colour (Material You) is deliberately not wired in. It would repaint
 * the app from the device wallpaper, and a media review tool whose accent
 * changes per phone cannot be reasoned about in a screenshot attached to a
 * comment.
 */
private val LightColors = lightColorScheme(
  primary = WeekclipAccent,
  onPrimary = WeekclipOnAccent,
  background = WeekclipBgAppLight,
  onBackground = WeekclipText1Light,
  surface = WeekclipBgAppLight,
  onSurface = WeekclipText1Light,
  surfaceVariant = WeekclipBgSurfaceLight,
  onSurfaceVariant = WeekclipText3Light,
  surfaceContainerLowest = WeekclipBgAppLight,
  surfaceContainerLow = WeekclipBgPanelLight,
  surfaceContainer = WeekclipBgSurfaceLight,
  surfaceContainerHigh = WeekclipBgSurface2Light,
  surfaceContainerHighest = WeekclipBgSurface3Light,
  outline = WeekclipBorderStrongLight,
  outlineVariant = WeekclipBorderLight,
  error = WeekclipErrorLight,
  onError = WeekclipOnAccent
)

private val DarkColors = darkColorScheme(
  primary = WeekclipAccent,
  onPrimary = WeekclipOnAccent,
  background = WeekclipBgAppDark,
  onBackground = WeekclipText1Dark,
  surface = WeekclipBgAppDark,
  onSurface = WeekclipText1Dark,
  surfaceVariant = WeekclipBgSurfaceDark,
  onSurfaceVariant = WeekclipText3Dark,
  surfaceContainerLowest = WeekclipBgAppDark,
  surfaceContainerLow = WeekclipBgPanelDark,
  surfaceContainer = WeekclipBgSurfaceDark,
  surfaceContainerHigh = WeekclipBgSurface2Dark,
  surfaceContainerHighest = WeekclipBgSurface3Dark,
  outline = WeekclipBorderStrongDark,
  outlineVariant = WeekclipBorderDark,
  error = WeekclipErrorDark,
  onError = WeekclipOnAccent
)

@Composable
fun WeekclipTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = WeekclipTypography,
    content = content
  )
}
