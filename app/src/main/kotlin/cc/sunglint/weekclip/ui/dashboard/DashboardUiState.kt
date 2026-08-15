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
  /** Distinguishes "no studios" from "not loaded yet", which read the same otherwise. */
  val isEmpty: Boolean get() = !isLoading && error == null && studios.isEmpty()
}
