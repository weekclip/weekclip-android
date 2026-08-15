package cc.sunglint.weekclip.ui.dashboard

import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.domain.model.Studio

/**
 * The whole screen in one immutable value (android-viewmodel skill §6).
 *
 * One object rather than three `StateFlow`s, because the states are not
 * independent: "loading" and "error" are mutually exclusive, and separate flows
 * let the UI observe a combination that never legally exists (a spinner over an
 * error over stale rows) for one frame during recomposition.
 *
 * [isRefreshing] is separate from [isLoading] on purpose. A pull-to-refresh
 * must not blank out the list that is already on screen.
 */
data class DashboardUiState(
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val studios: List<Studio> = emptyList(),
  val error: AppError? = null
) {
  /**
   * Whether to cover the screen with a loader.
   *
   * True while refreshing *if there is nothing to keep on screen*. Found by
   * driving the app on a real device (2026-08-15): tapping retry from the error
   * state clears the error, and with no rows loaded the screen fell through to
   * `isEmpty` and flashed "No studios yet." before the error came back. A retry
   * that briefly claims the account has no studios is worse than a spinner.
   *
   * A refresh *with* rows still shows the rows — that is the whole reason
   * [isRefreshing] is separate from [isLoading].
   *
   * Pinned by `DashboardViewModelTest`, not by a Maestro flow. A device flow was
   * tried and **could not fail on the buggy build**: with no network the request
   * fails instantly, so the wrong frame is gone before the assertion runs. A
   * check that cannot fail is not a check.
   */
  val showFullScreenLoader: Boolean get() = isLoading || (isRefreshing && studios.isEmpty())

  /** Distinguishes "no studios" from "not loaded yet", which read the same otherwise. */
  val isEmpty: Boolean get() = !showFullScreenLoader && error == null && studios.isEmpty()
}
