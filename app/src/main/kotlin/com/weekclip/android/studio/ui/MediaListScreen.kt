package com.weekclip.android.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.weekclip.android.studio.model.StudioMedia
import com.weekclip.android.studio.viewmodel.MediaListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
  studioId: String,
  viewModel: MediaListViewModel = hiltViewModel(),
  onBack: () -> Unit,
  onMediaSelected: (String) -> Unit = {}
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(studioId) {
    viewModel.loadMedia(studioId)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Media") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primary,
          titleContentColor = MaterialTheme.colorScheme.onPrimary,
          navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
      )
    }
  ) { innerPadding ->
    when {
      uiState.isLoading && uiState.mediaList.isEmpty() -> {
        // Loading skeleton
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentPadding = PaddingValues(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(8) {
            MediaTileSkeleton()
          }
        }
      }

      uiState.mediaList.isEmpty() && !uiState.isLoading -> {
        // Empty state
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text("No media yet")
            Text(
              "Upload your first video or photos",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      else -> {
        // Media grid
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentPadding = PaddingValues(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(uiState.mediaList) { media ->
            MediaTile(
              media = media,
              onClick = { onMediaSelected(media.id) }
            )
          }

          // Load more indicator
          if (uiState.isLoadingMore) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                contentAlignment = Alignment.Center
              ) {
                CircularProgressIndicator()
              }
            }
          } else if (uiState.hasMore) {
            item {
              LaunchedEffect(Unit) {
                viewModel.loadMore()
              }
            }
          }
        }
      }
    }

    // Error message
    if (!uiState.error.isNullOrEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.errorContainer)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = uiState.error ?: "Error",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
fun MediaTile(
  media: StudioMedia,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(8.dp)
  ) {
    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      // Poster image
      AsyncImage(
        model = media.posterThumbUrl ?: media.posterUrl,
        contentDescription = media.title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )

      // Gradient overlay
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            brush = androidx.compose.foundation.background(Color.Black.copy(alpha = 0.3f)).brush
          )
      )

      // Status badge (top-right)
      StatusBadge(
        status = media.status,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
      )

      // Duration badge (bottom-left)
      if (media.duration != "--:--") {
        DurationBadge(
          duration = media.duration,
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(8.dp)
        )
      }

      // Title (bottom)
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(8.dp)
      ) {
        Text(
          text = media.title,
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun MediaTileSkeleton() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f),
    shape = RoundedCornerShape(8.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceVariant)
    )
  }
}

@Composable
fun StatusBadge(
  status: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .background(
        color = when (status) {
          "Ready" -> Color.Green.copy(alpha = 0.9f)
          "Processing" -> Color.Yellow.copy(alpha = 0.9f)
          else -> Color.Gray.copy(alpha = 0.9f)
        },
        shape = RoundedCornerShape(4.dp)
      )
      .padding(horizontal = 6.dp, vertical = 3.dp)
  ) {
    Text(
      text = status,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White
    )
  }
}

@Composable
fun DurationBadge(
  duration: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .background(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(3.dp)
      )
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    Text(
      text = duration,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White
    )
  }
}
