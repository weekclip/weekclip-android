package com.weekclip.android.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekclip.android.studio.api.StudioApiClient
import com.weekclip.android.studio.model.Studio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class StudioListUiState(
  val studios: List<Studio> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
  val selectedStudioId: String? = null
)

@HiltViewModel
class StudioListViewModel @Inject constructor(
  private val apiClient: StudioApiClient
) : ViewModel() {

  private val _uiState = MutableStateFlow(StudioListUiState())
  val uiState: StateFlow<StudioListUiState> = _uiState.asStateFlow()

  fun loadStudios() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      try {
        val response = apiClient.fetchStudios()
        _uiState.update {
          it.copy(
            studios = response.data.items,
            isLoading = false,
            error = null
          )
        }
      } catch (e: Exception) {
        Timber.e(e, "Failed to load studios")
        _uiState.update {
          it.copy(
            isLoading = false,
            error = e.message ?: "Failed to load studios"
          )
        }
      }
    }
  }

  fun selectStudio(studioId: String) {
    _uiState.update { it.copy(selectedStudioId = studioId) }
  }

  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }
}
