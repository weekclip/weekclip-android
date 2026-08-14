package cc.sunglint.weekclip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cc.sunglint.weekclip.ui.navigation.WeekclipApp
import cc.sunglint.weekclip.ui.theme.WeekclipTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      WeekclipTheme {
        WeekclipApp()
      }
    }
  }
}
