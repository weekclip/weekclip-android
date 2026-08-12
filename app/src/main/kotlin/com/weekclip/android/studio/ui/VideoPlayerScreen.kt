package com.weekclip.android.studio.ui

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.weekclip.android.studio.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale

@Composable
fun VideoPlayerScreen(
  studioId: String,
  mediaId: String,
  viewModel: VideoPlayerViewModel = hiltViewModel(),
  onBack: () -> Unit,
  onFullscreenChanged: (Boolean) -> Unit = {}
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()

  // Create ExoPlayer instance
  val exoPlayer = remember {
    ExoPlayer.Builder(context).build()
  }

  // Set up player when component mounts
  LaunchedEffect(Unit) {
    viewModel.setExoPlayer(exoPlayer)
    viewModel.loadMedia(studioId, mediaId)
  }

  // Clean up player when component unmounts
  DisposableEffect(Unit) {
    onDispose {
      exoPlayer.release()
      viewModel.release()
    }
  }

  // Notify parent of fullscreen changes
  LaunchedEffect(uiState.isFullscreen) {
    onFullscreenChanged(uiState.isFullscreen)
  }

  // Handle back button press
  BackHandler(enabled = uiState.isFullscreen) {
    if (uiState.isFullscreen) {
      viewModel.toggleFullscreen()
    }
  }

  // Update playback state periodically
  LaunchedEffect(Unit) {
    while (true) {
      viewModel.updatePlaybackState()
      delay(100)
    }
  }

  if (uiState.isFullscreen) {
    // Fullscreen player view
    FullscreenVideoPlayer(
      exoPlayer = exoPlayer,
      uiState = uiState,
      onBack = {
        viewModel.toggleFullscreen()
      },
      onPlayPause = { viewModel.togglePlayPause() },
      onSeek = { viewModel.seek(it) },
      onVolumeChange = { viewModel.setVolume(it) },
      onMuteToggle = { viewModel.toggleMute() },
      onFullscreenToggle = { viewModel.toggleFullscreen() }
    )
  } else {
    // Normal player view with back button
    Scaffold(
      topBar = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 8.dp)
        ) {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        VideoPlayerContent(
          exoPlayer = exoPlayer,
          uiState = uiState,
          posterUrl = uiState.media?.posterUrl,
          onPlayPause = { viewModel.togglePlayPause() },
          onSeek = { viewModel.seek(it) },
          onVolumeChange = { viewModel.setVolume(it) },
          onMuteToggle = { viewModel.toggleMute() },
          onFullscreenToggle = { viewModel.toggleFullscreen() }
        )
      }
    }
  }
}

@Composable
fun FullscreenVideoPlayer(
  exoPlayer: ExoPlayer,
  uiState: VideoPlayerUiState,
  onBack: () -> Unit = {},
  onPlayPause: () -> Unit = {},
  onSeek: (Long) -> Unit = {},
  onVolumeChange: (Float) -> Unit = {},
  onMuteToggle: () -> Unit = {},
  onFullscreenToggle: () -> Unit = {}
) {
  var showControls by remember { mutableStateOf(true) }

  LaunchedEffect(showControls) {
    if (showControls) {
      delay(3000)
      showControls = false
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    // Video player
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        PlayerView(context).apply {
          player = exoPlayer
          useController = false
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
        }
      }
    )

    // Custom controls overlay
    VideoControlsOverlay(
      modifier = Modifier.fillMaxSize(),
      isVisible = showControls,
      isPlaying = uiState.isPlaying,
      currentPosition = uiState.currentPosition,
      duration = uiState.duration,
      bufferedPosition = uiState.bufferedPosition,
      volume = uiState.volume,
      isMuted = uiState.isMuted,
      isFullscreen = true,
      onPlayPause = onPlayPause,
      onSeek = onSeek,
      onVolumeChange = onVolumeChange,
      onMuteToggle = onMuteToggle,
      onFullscreenToggle = onFullscreenToggle,
      onTap = { showControls = !showControls }
    )

    // Back button (top-left)
    if (showControls) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(8.dp)
      ) {
        Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Exit Fullscreen",
          tint = Color.White
        )
      }
    }
  }
}

@Composable
fun VideoPlayerContent(
  exoPlayer: ExoPlayer,
  uiState: VideoPlayerUiState,
  posterUrl: String? = null,
  onPlayPause: () -> Unit = {},
  onSeek: (Long) -> Unit = {},
  onVolumeChange: (Float) -> Unit = {},
  onMuteToggle: () -> Unit = {},
  onFullscreenToggle: () -> Unit = {}
) {
  var showControls by remember { mutableStateOf(true) }

  LaunchedEffect(showControls, uiState.isPlaying) {
    if (showControls && uiState.isPlaying) {
      delay(3000)
      showControls = false
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    // Video player
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        PlayerView(context).apply {
          player = exoPlayer
          useController = false
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
        }
      }
    )

    // Poster image overlay (shown during buffering)
    if (uiState.isLoading && !posterUrl.isNullOrEmpty()) {
      AsyncImage(
        model = posterUrl,
        contentDescription = "Poster",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )
    }

    // Loading indicator
    if (uiState.isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center),
        color = MaterialTheme.colorScheme.primary
      )
    }

    // Error message
    if (!uiState.error.isNullOrEmpty()) {
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxWidth(0.8f)
          .background(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium
          )
          .padding(16.dp)
      ) {
        Text(
          text = uiState.error,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.align(Alignment.Center)
        )
      }
    }

    // Custom controls overlay
    VideoControlsOverlay(
      modifier = Modifier.fillMaxSize(),
      isVisible = showControls,
      isPlaying = uiState.isPlaying,
      currentPosition = uiState.currentPosition,
      duration = uiState.duration,
      bufferedPosition = uiState.bufferedPosition,
      volume = uiState.volume,
      isMuted = uiState.isMuted,
      isFullscreen = false,
      onPlayPause = onPlayPause,
      onSeek = onSeek,
      onVolumeChange = onVolumeChange,
      onMuteToggle = onMuteToggle,
      onFullscreenToggle = onFullscreenToggle,
      onTap = { showControls = !showControls }
    )
  }
}
