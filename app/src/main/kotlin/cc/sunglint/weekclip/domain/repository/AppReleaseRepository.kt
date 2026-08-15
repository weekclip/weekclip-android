package cc.sunglint.weekclip.domain.repository

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.model.AppReleasePolicy

/** Reads the server's client-compatibility policy for **this** platform. */
interface AppReleaseRepository {
  suspend fun policy(): AppResult<AppReleasePolicy>
}
