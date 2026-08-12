package com.weekclip.android.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")
private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

/**
 * Manages authentication session state and persistence.
 */
@Singleton
class SessionManager @Inject constructor(private val context: Context) {

  private val _isAuthenticated = MutableStateFlow(false)
  val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  private val _accessToken = MutableStateFlow<String?>(null)
  val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  /**
   * Initialize session from stored credentials.
   */
  suspend fun initializeSession() {
    _isLoading.value = true
    try {
      val session = getCurrentSession()
      if (session != null) {
        _accessToken.value = session.accessToken
        _isAuthenticated.value = true
      } else {
        _isAuthenticated.value = false
      }
    } catch (e: Exception) {
      _error.value = e.message
      _isAuthenticated.value = false
    } finally {
      _isLoading.value = false
    }
  }

  /**
   * Sign in with Google OAuth.
   */
  suspend fun signInWithGoogle() {
    _isLoading.value = true
    _error.value = null
    try {
      signInWithGoogle()
      val session = getCurrentSession()
      if (session != null) {
        _accessToken.value = session.accessToken
        _isAuthenticated.value = true
      }
    } catch (e: Exception) {
      _error.value = e.message
      _isAuthenticated.value = false
    } finally {
      _isLoading.value = false
    }
  }

  /**
   * Sign out the current user.
   */
  suspend fun logout() {
    _isLoading.value = true
    try {
      signOut()
      _accessToken.value = null
      _isAuthenticated.value = false
    } catch (e: Exception) {
      _error.value = e.message
    } finally {
      _isLoading.value = false
    }
  }

  /**
   * Clear any stored error messages.
   */
  fun clearError() {
    _error.value = null
  }
}
