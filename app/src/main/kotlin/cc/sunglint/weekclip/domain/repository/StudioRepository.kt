package cc.sunglint.weekclip.domain.repository

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.model.Studio

/**
 * The domain's view of studio storage.
 *
 * Declared here rather than in `data/` so the dependency arrow points inward:
 * the data layer implements this, the UI depends on it, and neither depends on
 * the other (android-architecture skill §1).
 *
 * Returns [AppResult] instead of throwing. A repository that throws pushes
 * `try/catch` into every ViewModel, and the case it is easiest to forget —
 * `UnknownHostException` on a subway — is the one users hit most.
 */
interface StudioRepository {
  /** Studios the current session can see. Empty list is a valid success. */
  suspend fun getStudios(): AppResult<List<Studio>>
}
