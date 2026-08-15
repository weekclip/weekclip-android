package cc.sunglint.weekclip.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.usecase.GetStudiosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dashboard state holder.
 *
 * This is the reference shape for every screen that follows (PRD-0008 Phase 5):
 * one immutable [DashboardUiState] behind a read-only [StateFlow], all work in
 * `viewModelScope`, no `Context` and no Compose types in here.
 *
 * `uiState` uses a Kotlin explicit backing field (android-viewmodel skill,
 * "Kotlin 2.3+"): inside this class the name is the `MutableStateFlow`, outside
 * it is a read-only `StateFlow`. Verified to compile with no compiler flag on
 * the 2.4.10 toolchain this project pins. It replaces the `_uiState`/`uiState`
 * pair, whose failure mode is that the mutable half is one typo away from being
 * public.
 *
 * There is no `SharedFlow` of one-off events yet. The skill is right that
 * navigation and snackbars need one; adding it before there is an event to send
 * would ship an empty channel and an unread `LaunchedEffect`.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
  private val getStudios: GetStudiosUseCase
) : ViewModel() {

  val uiState: StateFlow<DashboardUiState>
    field = MutableStateFlow(DashboardUiState())

  init {
    load(isRefresh = false)
  }

  /** Pull-to-refresh and the error state's retry both land here. */
  fun refresh() = load(isRefresh = true)

  private fun load(isRefresh: Boolean) {
    viewModelScope.launch {
      uiState.update {
        it.copy(
          isLoading = !isRefresh,
          isRefreshing = isRefresh,
          // Clear the previous error now: leaving it up while a retry is in
          // flight shows a failure and a spinner at the same time.
          error = null
        )
      }

      when (val result = getStudios()) {
        is AppResult.Success -> uiState.update {
          it.copy(isLoading = false, isRefreshing = false, studios = result.value, error = null)
        }
        // On a failed refresh the already-loaded list stays on screen. Replacing
        // real rows with an error because a background reload failed is a
        // regression the user did not ask for.
        is AppResult.Failure -> uiState.update {
          it.copy(isLoading = false, isRefreshing = false, error = result.error)
        }
      }
    }
  }
}
