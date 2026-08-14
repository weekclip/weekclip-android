package cc.sunglint.weekclip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
  primary = WeekclipAccent,
  background = WeekclipInk,
  surface = WeekclipInk
)

private val LightColors = lightColorScheme(
  primary = WeekclipAccent,
  background = WeekclipPaper,
  surface = WeekclipPaper
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
