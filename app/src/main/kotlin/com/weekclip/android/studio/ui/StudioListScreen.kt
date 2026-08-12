package com.weekclip.android.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.weekclip.android.studio.model.Studio
import com.weekclip.android.studio.viewmodel.StudioListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioListScreen(
  viewModel: StudioListViewModel = hiltViewModel(),
  onStudioSelected: (String) -> Unit,
  onLogout: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.loadStudios()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Studios") },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primary,
          titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = {
          IconButton(onClick = onLogout) {
            Icon(
              Icons.Default.Logout,
              contentDescription = "Logout",
              tint = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      )
    }
  ) { innerPadding ->
    when {
      uiState.isLoading -> {
        // Skeleton loading state
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          repeat(3) {
            StudioCardSkeleton()
          }
        }
      }

      uiState.studios.isEmpty() -> {
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
            Text("No studios yet")
            Text(
              "Create your first studio to get started",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      else -> {
        // Studio list
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(uiState.studios) { studio ->
            StudioCard(
              studio = studio,
              onClick = { onStudioSelected(studio.id) }
            )
          }
        }
      }
    }

    if (!uiState.error.isNullOrEmpty()) {
      ErrorSnackbar(
        message = uiState.error ?: "Error loading studios",
        onRetry = { viewModel.loadStudios() },
        onDismiss = { viewModel.clearError() }
      )
    }
  }
}

@Composable
fun StudioCard(
  studio: Studio,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = studio.name,
          style = MaterialTheme.typography.headlineSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )

        RoleBadge(studio.role)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        MediaCountBadge(studio.mediaCount)
        Text(
          text = "Updated ${studio.updatedAtLabel}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun StudioCardSkeleton() {
  Card(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)
        .padding(16.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {}
  }
}

@Composable
fun RoleBadge(role: String) {
  Box(
    modifier = Modifier
      .background(
        color = when (role) {
          "Owner" -> MaterialTheme.colorScheme.primary
          "Editor" -> MaterialTheme.colorScheme.secondary
          else -> MaterialTheme.colorScheme.tertiary
        },
        shape = RoundedCornerShape(4.dp)
      )
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = role,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White
    )
  }
}

@Composable
fun MediaCountBadge(count: Int) {
  Box(
    modifier = Modifier
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
      )
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = "$count media",
      style = MaterialTheme.typography.labelSmall
    )
  }
}

@Composable
fun ErrorSnackbar(
  message: String,
  onRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer)
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

// Missing import
import androidx.compose.foundation.layout.PaddingValues
