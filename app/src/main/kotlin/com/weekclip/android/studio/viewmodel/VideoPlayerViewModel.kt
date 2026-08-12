package com.weekclip.android.studio.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
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

data class VideoPlayerUiState(
  val studioId: String = "",
  val mediaId: String = "",
  val media: StudioMedia? = null,
  val isLoading: Boolean = false,
  val error: String? = null,
  val isPlaying: Boolean = false,
  val currentPosition: Long = 0L,
  val duration: Long = 0L,
  val isFullscreen: Boolean = false,
  val showControls: Boolean = true,
  val isMuted: Boolean = false,
  val volume: Float = 1f,
  val bufferedPosition: Long = 0L
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
  private val apiClient: StudioApiClient,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val _uiState = MutableStateFlow(VideoPlayerUiState())
  val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

  private var exoPlayer: ExoPlayer? = null

  init {
    val studioId = savedStateHandle.get<String>("studioId") ?: ""
    val mediaId = savedStateHandle.get<String>("mediaId") ?: ""
    _uiState.update {
      it.copy(
        studioId = studioId,
        mediaId = mediaId
      )
    }
  }

  fun setExoPlayer(player: ExoPlayer) {
    exoPlayer = player
    player.addListener(
      object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          when (playbackState) {
            androidx.media3.common.Player.STATE_READY -> {
              _uiState.update {
                it.copy(
                  isLoading = false,
                  duration = player.duration
                )
              }
            }
            androidx.media3.common.Player.STATE_BUFFERING -> {
              _uiState.update { it.copy(isLoading = true) }
            }
            androidx.media3.common.Player.STATE_ENDED -> {
              _uiState.update { it.copy(isPlaying = false) }
            }
          }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPositionDiscontinuity(
          oldPosition: androidx.media3.common.Player.PositionInfo,
          newPosition: androidx.media3.common.Player.PositionInfo,
          reason: Int
        ) {
          _uiState.update { it.copy(currentPosition = newPosition.positionMs) }
        }
      }
    )
  }

  fun loadMedia(studioId: String? = null, mediaId: String? = null) {
    val sId = studioId ?: _uiState.value.studioId
    val mId = mediaId ?: _uiState.value.mediaId

    if (sId.isEmpty() || mId.isEmpty()) {
      _uiState.update { it.copy(error = "Studio ID and Media ID are required") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      try {
        // Fetch media list to get full media details
        val response = apiClient.fetchMediaList(sId, limit = 100)
        val media = response.data.items.find { it.id == mId }

        if (media == null) {
          _uiState.update {
            it.copy(
              isLoading = false,
              error = "Media not found"
            )
          }
          return@launch
        }

        _uiState.update {
          it.copy(
            studioId = sId,
            mediaId = mId,
            media = media,
            isLoading = false,
            error = null
          )
        }

        // Load video URL into player
        loadMediaUrl(media)
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

  private fun loadMediaUrl(media: StudioMedia) {
    val player = exoPlayer ?: return

    // Try to load HLS manifest first (preview manifest)
    val hlsUrl = media.preview.manifestUrl
    if (!hlsUrl.isNullOrEmpty()) {
      try {
        val mediaItem = MediaItem.Builder()
          .setUri(hlsUrl)
          .setMimeType(MimeTypes.APPLICATION_M3U8)
          .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        Timber.d("Loaded HLS media from: $hlsUrl")
      } catch (e: Exception) {
        Timber.e(e, "Failed to load HLS media, URL: $hlsUrl")
        _uiState.update { it.copy(error = "Failed to load video: ${e.message}") }
      }
    } else {
      _uiState.update { it.copy(error = "No media URL available") }
    }
  }

  fun play() {
    exoPlayer?.play()
    _uiState.update { it.copy(isPlaying = true) }
  }

  fun pause() {
    exoPlayer?.pause()
    _uiState.update { it.copy(isPlaying = false) }
  }

  fun togglePlayPause() {
    if (_uiState.value.isPlaying) {
      pause()
    } else {
      play()
    }
  }

  fun seek(positionMs: Long) {
    exoPlayer?.seekTo(positionMs)
    _uiState.update { it.copy(currentPosition = positionMs) }
  }

  fun seekForward(deltaMs: Long = 10000) {
    val newPosition = (exoPlayer?.currentPosition ?: 0) + deltaMs
    seek(newPosition)
  }

  fun seekBackward(deltaMs: Long = 10000) {
    val newPosition = maxOf(0, (exoPlayer?.currentPosition ?: 0) - deltaMs)
    seek(newPosition)
  }

  fun setVolume(volume: Float) {
    val normalizedVolume = volume.coerceIn(0f, 1f)
    exoPlayer?.volume = normalizedVolume
    _uiState.update { it.copy(volume = normalizedVolume) }
  }

  fun toggleMute() {
    val isMuted = _uiState.value.isMuted
    if (isMuted) {
      exoPlayer?.volume = _uiState.value.volume
    } else {
      exoPlayer?.volume = 0f
    }
    _uiState.update { it.copy(isMuted = !isMuted) }
  }

  fun toggleFullscreen() {
    _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
  }

  fun setControlsVisible(visible: Boolean) {
    _uiState.update { it.copy(showControls = visible) }
  }

  fun updatePlaybackState() {
    val player = exoPlayer ?: return
    _uiState.update {
      it.copy(
        currentPosition = player.currentPosition,
        duration = player.duration,
        bufferedPosition = player.bufferedPosition,
        isPlaying = player.isPlaying
      )
    }
  }

  fun release() {
    exoPlayer?.release()
    exoPlayer = null
  }

  override fun onCleared() {
    super.onCleared()
    release()
  }
}
