package cc.sunglint.weekclip.domain.usecase

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.model.AppUpdateRequirement
import cc.sunglint.weekclip.domain.repository.AppReleaseRepository
import javax.inject.Inject

/**
 * Decides whether this build may keep running.
 *
 * Two rules, both of which are the kind that get written the wrong way round:
 *
 * 1. **Strictly below.** A build equal to the minimum is allowed. `>=` here
 *    would block the very build an operator just declared to be the floor —
 *    the release they are trying to force everyone onto.
 * 2. **Any failure means "carry on".** No network, a timeout, a WAF answering
 *    403, a body we cannot parse: none of those are evidence that this build is
 *    too old. Blocking on them would turn every outage into a force-update
 *    screen for the whole fleet, and the server that could take it back is the
 *    one that is down.
 *
 * The server side makes the same choice from the other end — an unconfigured
 * `APP_MIN_BUILD_*` returns 0 and blocks nobody (weekclip-api `app-release.ts`).
 * Both ends fail open, so it takes two deliberate acts to stop an app.
 */
class GetAppUpdateRequirementUseCase @Inject constructor(
  private val repository: AppReleaseRepository
) {
  suspend operator fun invoke(currentBuild: Int): AppUpdateRequirement =
    when (val result = repository.policy()) {
      is AppResult.Success ->
        if (currentBuild < result.value.minimumBuild) {
          AppUpdateRequirement.Required(storeUrl = result.value.storeUrl)
        } else {
          AppUpdateRequirement.NotRequired
        }

      is AppResult.Failure -> AppUpdateRequirement.NotRequired
    }
}
