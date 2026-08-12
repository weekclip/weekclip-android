package com.weekclip.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.weekclip.android.auth.AuthViewModel
import com.weekclip.android.platform.NavigationHost
import com.weekclip.android.platform.Route
import com.weekclip.android.ui.theme.WeekclipTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      WeekclipTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val authViewModel: AuthViewModel = hiltViewModel()
          val authState by authViewModel.uiState.collectAsState()

          // Determine starting destination based on auth state
          val startDestination = if (authState.isAuthenticated) {
            Route.StudioList.path
          } else {
            Route.Login.path
          }

          NavigationHost(
            authViewModel = authViewModel,
            startDestination = startDestination
          )
        }
      }
    }
  }
}
