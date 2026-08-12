package com.weekclip.android.studio.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekclip.android.studio.api.StudioApiClient
import com.weekclip.android.studio.model.StudioMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MediaListUiState(
  val studioId: String = "",
  val mediaList: List<StudioMedia> = emptyList(),
  val isLoading: Boolean = false,
  val isLoadingMore: Boolean = false,
  val error: String? = null,
  val nextCursor: String? = null,
  val hasMore: Boolean = false
)

@HiltViewModel
class MediaListViewModel @Inject constructor(
  private val apiClient: StudioApiClient,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val _uiState = MutableStateFlow(MediaListUiState())
  val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

  init {
    val studioId = savedStateHandle.get<String>("studioId") ?: ""
    _uiState.update { it.copy(studioId = studioId) }
  }

  fun loadMedia(studioId: String? = null) {
    val id = studioId ?: _uiState.value.studioId
    if (id.isEmpty()) {
      _uiState.update { it.copy(error = "Studio ID is required") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      try {
        val response = apiClient.fetchMediaList(id)
        _uiState.update {
          it.copy(
            studioId = id,
            mediaList = response.data.items,
            nextCursor = response.data.nextCursor,
            hasMore = response.data.nextCursor != null,
            isLoading = false,
            error = null
          )
        }
      } catch (e: Exception) {
        Timber.e(e, "Failed to load media")
        _uiState.update {
          it.copy(
            isLoading = false,
            error = e.message ?: "Failed to load media"
          )
        }
      }
    }
  }

  fun loadMore() {
    val state = _uiState.value
    if (state.isLoadingMore || !state.hasMore || state.nextCursor == null) {
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingMore = true) }
      try {
        val response = apiClient.fetchMediaList(
          state.studioId,
          cursor = state.nextCursor
        )
        _uiState.update {
          it.copy(
            mediaList = it.mediaList + response.data.items,
            nextCursor = response.data.nextCursor,
            hasMore = response.data.nextCursor != null,
            isLoadingMore = false,
            error = null
          )
        }
      } catch (e: Exception) {
        Timber.e(e, "Failed to load more media")
        _uiState.update {
          it.copy(
            isLoadingMore = false,
            error = e.message ?: "Failed to load more media"
          )
        }
      }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }
}
