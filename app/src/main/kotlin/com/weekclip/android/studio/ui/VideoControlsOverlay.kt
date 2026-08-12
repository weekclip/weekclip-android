package com.weekclip.android.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun VideoControlsOverlay(
  modifier: Modifier = Modifier,
  isVisible: Boolean = true,
  isPlaying: Boolean = false,
  currentPosition: Long = 0L,
  duration: Long = 0L,
  bufferedPosition: Long = 0L,
  volume: Float = 1f,
  isMuted: Boolean = false,
  isFullscreen: Boolean = false,
  onPlayPause: () -> Unit = {},
  onSeek: (Long) -> Unit = {},
  onVolumeChange: (Float) -> Unit = {},
  onMuteToggle: () -> Unit = {},
  onFullscreenToggle: () -> Unit = {},
  onTap: () -> Unit = {}
) {
  val controlsAlpha = if (isVisible) 1f else 0f
  val controlsVisible = remember { mutableStateOf(isVisible) }

  LaunchedEffect(isVisible) {
    controlsVisible.value = isVisible
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectTapGestures {
          onTap()
        }
      }
  ) {
    // Top gradient overlay (semi-transparent)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = 0.4f),
              Color.Transparent
            )
          )
        )
        .align(Alignment.TopCenter)
    )

    // Bottom gradient overlay (semi-transparent)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              Color.Black.copy(alpha = 0.6f)
            )
          )
        )
        .align(Alignment.BottomCenter)
    )

    // Center play button
    AnimatedVisibility(
      visible = controlsVisible.value && !isPlaying,
      modifier = Modifier.align(Alignment.Center),
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      IconButton(
        onClick = onPlayPause,
        modifier = Modifier
          .size(64.dp)
          .background(
            color = Color.White.copy(alpha = 0.9f),
            shape = CircleShape
          )
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = "Play",
          modifier = Modifier.size(40.dp),
          tint = Color.Black
        )
      }
    }

    // Bottom controls row
    AnimatedVisibility(
      visible = controlsVisible.value,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth(),
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp)
      ) {
        // Progress bar with timeline
        ProgressBar(
          currentPosition = currentPosition,
          duration = duration,
          bufferedPosition = bufferedPosition,
          onSeek = onSeek,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Controls row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Play/Pause button
          IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(40.dp)
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              modifier = Modifier.size(24.dp),
              tint = Color.White
            )
          }

          // Volume control section
          Row(
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Mute button
            IconButton(
              onClick = onMuteToggle,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                modifier = Modifier.size(20.dp),
                tint = Color.White
              )
            }

            // Volume slider
            Slider(
              value = if (isMuted) 0f else volume,
              onValueChange = { newVolume ->
                onVolumeChange(newVolume)
              },
              modifier = Modifier
                .width(100.dp)
                .height(4.dp),
              colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
              )
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Fullscreen button
          IconButton(
            onClick = onFullscreenToggle,
            modifier = Modifier.size(40.dp)
          ) {
            Icon(
              imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
              contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
              modifier = Modifier.size(24.dp),
              tint = Color.White
            )
          }
        }
      }
    }
  }
}

@Composable
fun ProgressBar(
  currentPosition: Long = 0L,
  duration: Long = 0L,
  bufferedPosition: Long = 0L,
  onSeek: (Long) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
  val bufferedProgress = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

  Column(
    modifier = modifier
  ) {
    // Slider
    Slider(
      value = progress,
      onValueChange = { newProgress ->
        val newPosition = (newProgress * duration).toLong()
        onSeek(newPosition)
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp),
      colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color(0xFF4CAF50),
        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
      )
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Time display
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = formatDuration(currentPosition),
        fontSize = 12.sp,
        color = Color.White,
        fontFamily = FontFamily.Monospace
      )
      Text(
        text = formatDuration(duration),
        fontSize = 12.sp,
        color = Color.White,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}

private fun formatDuration(durationMs: Long): String {
  val totalSeconds = (durationMs / 1000).toInt()
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60

  return if (hours > 0) {
    String.format("%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format("%d:%02d", minutes, seconds)
  }
}
