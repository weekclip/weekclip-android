package cc.sunglint.weekclip.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.sunglint.weekclip.BuildConfig
import cc.sunglint.weekclip.domain.model.AppUpdateRequirement
import cc.sunglint.weekclip.domain.usecase.GetAppUpdateRequirementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launch gate: one version check, once, before the app is usable
 * (PRD-0008 D6① — human: "진입할 때 업데이트하게 하는 거").
 *
 * `Checking` is a real state and the initial one. Starting at "allowed" would
 * flash the dashboard for a frame before the block lands — the same shape of
 * bug as 148.3d, where a refresh with no rows rendered as "No studios yet."
 * Starting at "blocked" would flash a force-update screen at everyone.
 */
@HiltViewModel
class AppGateViewModel @Inject constructor(
  private val getAppUpdateRequirement: GetAppUpdateRequirementUseCase
) : ViewModel() {

  val uiState: StateFlow<AppGateUiState>
    field = MutableStateFlow<AppGateUiState>(AppGateUiState.Checking)

  init {
    viewModelScope.launch {
      // `VERSION_CODE` rather than `VERSION_NAME`: the server compares build
      // numbers precisely because integer comparison cannot be got subtly wrong
      // the way semver can.
      uiState.value = when (val requirement = getAppUpdateRequirement(BuildConfig.VERSION_CODE)) {
        AppUpdateRequirement.NotRequired -> AppGateUiState.Allowed
        is AppUpdateRequirement.Required -> AppGateUiState.UpdateRequired(requirement.storeUrl)
      }
    }
  }
}

sealed interface AppGateUiState {
  /** The check has not answered yet. Nothing of the app is shown. */
  data object Checking : AppGateUiState

  data object Allowed : AppGateUiState

  /** [storeUrl] is null when there is nowhere to send the user yet. */
  data class UpdateRequired(val storeUrl: String?) : AppGateUiState
}
