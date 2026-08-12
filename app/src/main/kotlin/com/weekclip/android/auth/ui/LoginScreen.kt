package com.weekclip.android.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.weekclip.android.auth.AuthViewModel

@Composable
fun LoginScreen(
  viewModel: AuthViewModel = hiltViewModel(),
  onLoginSuccess: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(uiState.isAuthenticated) {
    if (uiState.isAuthenticated) {
      onLoginSuccess()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Logo / Title
    Text(
      text = "WeekClip",
      style = MaterialTheme.typography.displayLarge,
      color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Welcome back",
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Sign in to continue to your studios",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(48.dp))

    // Sign in button
    Button(
      onClick = { viewModel.signInWithGoogle() },
      enabled = !uiState.isLoading,
      modifier = Modifier
        .height(48.dp)
        .fillMaxSize(fraction = 1.0f)
    ) {
      if (uiState.isLoading) {
        CircularProgressIndicator(
          modifier = Modifier.height(24.dp),
          color = MaterialTheme.colorScheme.onPrimary
        )
      } else {
        Text("Sign in with Google")
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Error message
    if (!uiState.error.isNullOrEmpty()) {
      Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
          Button(onClick = { viewModel.clearError() }) {
            Text("Dismiss")
          }
        }
      ) {
        Text(uiState.error ?: "An error occurred")
      }
    }
  }
}
