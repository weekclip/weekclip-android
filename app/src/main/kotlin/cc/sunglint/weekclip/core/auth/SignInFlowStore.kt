package cc.sunglint.weekclip.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two things that must outlive the app during a sign-in.
 *
 * ### Why this is on disk and not in a ViewModel
 *
 * Signing in leaves the process. The user goes out to Chrome, may spend a
 * minute at Google's prompt, and Android is free to kill weekclip while they
 * are gone — a low-memory device *will*. Anything held in a `ViewModel`,
 * `SavedStateHandle` or `remember` is gone by the time the redirect brings the
 * app back, and the two values below are exactly the two that make the return
 * meaningful:
 *
 * - the **PKCE verifier**, without which the authorization code cannot be
 *   exchanged. Losing it turns a completed Google prompt into "log in again".
 * - the **intended route**, the deep link that sent the user to the login
 *   screen in the first place. Losing it drops them on the dashboard having
 *   forgotten what they tapped — the wireframe's "돌아갈 곳을 잃었다"
 *   (`screens/auth/mobile.html`, state `denied`).
 *
 * ### Both are read once and deleted
 *
 * `take` rather than `get`. A verifier is single-use by construction: after the
 * exchange it authenticates nothing, and leaving it on disk only widens the
 * window in which it is worth stealing. An intended route that survived its own
 * navigation would re-fire on the next unrelated sign-in.
 *
 * ### Not encrypted, unlike the session
 *
 * `DataStoreSessionStore` seals its payload with the Keystore because a refresh
 * token is a long-lived credential. Neither value here is: the verifier is a
 * nonce that is worthless the moment it is used or abandoned, and a route is
 * not a secret. Encrypting them would add a Keystore round trip to the
 * critical path of every launch and protect nothing that is not already
 * protected by the app sandbox.
 *
 * Shares the session DataStore file rather than opening a second one — these
 * keys belong to the same subsystem and the same lifecycle, which is the
 * distinction `SessionModule`'s file comment is actually drawing.
 */
@Singleton
class SignInFlowStore @Inject constructor(
  private val dataStore: DataStore<Preferences>
) {

  suspend fun putVerifier(verifier: String) {
    dataStore.edit { it[VERIFIER_KEY] = verifier }
  }

  suspend fun takeVerifier(): String? = take(VERIFIER_KEY)

  /** @param route a `WeekclipRoutes` path, already resolved from the link. */
  suspend fun putIntendedRoute(route: String) {
    dataStore.edit { it[INTENDED_ROUTE_KEY] = route }
  }

  suspend fun takeIntendedRoute(): String? = take(INTENDED_ROUTE_KEY)

  /**
   * Whether a route is waiting, without consuming it.
   *
   * The login screen shows a "you will be taken back to what you opened" line
   * off this, and it has to still be there afterwards for the navigation that
   * follows the exchange.
   */
  suspend fun hasIntendedRoute(): Boolean = dataStore.data.first()[INTENDED_ROUTE_KEY] != null

  /** Abandons a sign-in in progress. Called on sign-out and on a refusal. */
  suspend fun clear() {
    dataStore.edit {
      it.remove(VERIFIER_KEY)
      it.remove(INTENDED_ROUTE_KEY)
    }
  }

  private suspend fun take(key: Preferences.Key<String>): String? {
    val value = dataStore.data.first()[key] ?: return null
    dataStore.edit { it.remove(key) }
    return value
  }

  private companion object {
    val VERIFIER_KEY = stringPreferencesKey("sign_in_pkce_verifier_v1")
    val INTENDED_ROUTE_KEY = stringPreferencesKey("sign_in_intended_route_v1")
  }
}
