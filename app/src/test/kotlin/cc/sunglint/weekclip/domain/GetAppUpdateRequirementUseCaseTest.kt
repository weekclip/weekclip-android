package cc.sunglint.weekclip.domain

import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.model.AppReleasePolicy
import cc.sunglint.weekclip.domain.model.AppUpdateRequirement
import cc.sunglint.weekclip.domain.repository.AppReleaseRepository
import cc.sunglint.weekclip.domain.usecase.GetAppUpdateRequirementUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that decides whether an installed app keeps working.
 *
 * Two things are pinned here, and both are the kind that get written the wrong
 * way round exactly once, in production, for everybody at the same time:
 * the comparison is **strictly below**, and **any failure means carry on**.
 */
class GetAppUpdateRequirementUseCaseTest {

  private fun useCase(result: AppResult<AppReleasePolicy>) =
    GetAppUpdateRequirementUseCase(object : AppReleaseRepository {
      override suspend fun policy(): AppResult<AppReleasePolicy> = result
    })

  private fun policy(minimumBuild: Int, storeUrl: String? = "https://store.example/app") =
    AppResult.Success(AppReleasePolicy(minimumBuild, storeUrl))

  @Test
  fun `a build below the minimum is blocked`() = runTest {
    val requirement = useCase(policy(minimumBuild = 10))(currentBuild = 9)

    assertEquals(AppUpdateRequirement.Required("https://store.example/app"), requirement)
  }

  @Test
  fun `a build equal to the minimum keeps running`() = runTest {
    // `>=` here would block the very release an operator just declared to be
    // the floor — the one they are trying to force everyone onto.
    val requirement = useCase(policy(minimumBuild = 10))(currentBuild = 10)

    assertEquals(AppUpdateRequirement.NotRequired, requirement)
  }

  @Test
  fun `a newer build than the minimum keeps running`() = runTest {
    assertEquals(
      AppUpdateRequirement.NotRequired,
      useCase(policy(minimumBuild = 10))(currentBuild = 11)
    )
  }

  @Test
  fun `a minimum of zero blocks nobody`() = runTest {
    // The server's default. Both ends fail open, so it takes two deliberate
    // acts to stop an app.
    assertEquals(
      AppUpdateRequirement.NotRequired,
      useCase(policy(minimumBuild = 0))(currentBuild = 1)
    )
  }

  @Test
  fun `a blocked build with nowhere to send the user is still blocked`() = runTest {
    // iOS has no App Store record yet, and a listing can disappear. The screen
    // states the fact without offering a button that goes nowhere.
    val requirement = useCase(policy(minimumBuild = 10, storeUrl = null))(currentBuild = 1)

    assertEquals(AppUpdateRequirement.Required(null), requirement)
  }

  @Test
  fun `every failure means carry on`() = runTest {
    // None of these is evidence that this build is too old. Blocking on them
    // would turn any outage — including an outage of this very endpoint — into
    // a force-update screen for the whole fleet, with the server that could
    // take it back being the one that is down.
    //
    // Unauthorized is in the list on purpose: off the VPN the dev tier answers
    // 403 with a Cloudflare page, which lands here as Unauthorized.
    listOf(
      AppError.Offline,
      AppError.Timeout,
      AppError.Unauthorized,
      AppError.NotFound,
      AppError.MalformedResponse,
      AppError.Server(status = 500),
      AppError.Unexpected(IllegalStateException("boom"))
    ).forEach { error ->
      assertEquals(
        "failed with $error",
        AppUpdateRequirement.NotRequired,
        useCase(AppResult.Failure(error))(currentBuild = 1)
      )
    }
  }
}
