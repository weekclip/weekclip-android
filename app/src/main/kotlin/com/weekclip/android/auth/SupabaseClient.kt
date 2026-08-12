package com.weekclip.android.auth

import android.content.Context
import io.github.jan_tennert.supabase.SupabaseClient
import io.github.jan_tennert.supabase.createSupabaseClient
import io.github.jan_tennert.supabase.auth.Auth
import io.github.jan_tennert.supabase.auth.FlowType

private const val SUPABASE_URL = "https://uwygkfgdoxxxxxxxxxxxx.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

/**
 * Supabase client singleton for authentication and API calls.
 * Configure SUPABASE_URL and SUPABASE_ANON_KEY from your Supabase project settings.
 */
val supabaseClient: SupabaseClient by lazy {
  createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
  ) {
    install(Auth) {
      flowType = FlowType.PKCE
      scheme = "com.weekclip.android"
      host = "callback"
    }
  }
}

/**
 * Get the current access token from the Supabase session.
 * Returns null if user is not authenticated.
 */
fun getAccessToken(): String? {
  return try {
    supabaseClient.auth.currentSessionOrNull()?.accessToken
  } catch (e: Exception) {
    null
  }
}

/**
 * Get the current user session.
 * Returns null if user is not authenticated.
 */
suspend fun getCurrentSession() = supabaseClient.auth.currentSessionOrNull()

/**
 * Sign in with Google using OAuth.
 */
suspend fun signInWithGoogle() {
  supabaseClient.auth.signInWith(Google)
}

/**
 * Sign out the current user.
 */
suspend fun signOut() {
  supabaseClient.auth.signOut()
}
