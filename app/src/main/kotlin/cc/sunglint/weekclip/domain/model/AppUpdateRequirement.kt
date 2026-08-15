package cc.sunglint.weekclip.domain.model

/**
 * Whether this build is still allowed to run (PRD-0008 D6①).
 *
 * Two states, not three: "we could not find out" is deliberately folded into
 * [NotRequired] by [cc.sunglint.weekclip.domain.usecase.GetAppUpdateRequirementUseCase].
 * A gate that blocks the app when it cannot reach the server would brick every
 * install during an outage — including the outage of the very endpoint that
 * would say "you are fine".
 */
sealed interface AppUpdateRequirement {
  data object NotRequired : AppUpdateRequirement

  /**
   * This build is below the server's minimum and must stop.
   *
   * [storeUrl] is null when there is nowhere to send the user yet — the app
   * then states the fact without offering a button that goes nowhere.
   */
  data class Required(val storeUrl: String?) : AppUpdateRequirement
}

/** One platform's slice of the server's release policy. */
data class AppReleasePolicy(
  val minimumBuild: Int,
  val storeUrl: String?
)
