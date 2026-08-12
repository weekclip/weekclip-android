package com.weekclip.android.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AuthUiState(
  val isAuthenticated: Boolean = false,
  val isLoading: Boolean = false,
  val error: String? = null,
  val accessToken: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
  private val sessionManager: SessionManager
) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      sessionManager.initializeSession()
      updateStateFromSessionManager()
    }
  }

  private suspend fun updateStateFromSessionManager() {
    _uiState.update {
      it.copy(
        isAuthenticated = sessionManager.isAuthenticated.value,
        accessToken = sessionManager.accessToken.value,
        isLoading = sessionManager.isLoading.value,
        error = sessionManager.error.value
      )
    }
  }

  fun signInWithGoogle() {
    viewModelScope.launch {
      try {
        sessionManager.signInWithGoogle()
        updateStateFromSessionManager()
      } catch (e: Exception) {
        Timber.e(e, "Sign in failed")
        _uiState.update { it.copy(error = e.message) }
      }
    }
  }

  fun logout() {
    viewModelScope.launch {
      try {
        sessionManager.logout()
        updateStateFromSessionManager()
      } catch (e: Exception) {
        Timber.e(e, "Logout failed")
        _uiState.update { it.copy(error = e.message) }
      }
    }
  }

  fun clearError() {
    sessionManager.clearError()
    _uiState.update { it.copy(error = null) }
  }
}
