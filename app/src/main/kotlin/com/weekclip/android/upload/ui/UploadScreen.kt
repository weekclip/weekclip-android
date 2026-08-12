package com.weekclip.android.upload.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.weekclip.android.upload.model.UploadManagerUiState
import com.weekclip.android.upload.model.UploadQueueItem
import com.weekclip.android.upload.model.UploadStatus
import com.weekclip.android.upload.viewmodel.UploadManagerViewModel

/**
 * Floating upload panel screen - can be shown as an overlay on other screens.
 * In the real app, this would be composed into the main navigation.
 */
@Composable
fun UploadScreen(
  modifier: Modifier = Modifier,
  viewModel: UploadManagerViewModel = hiltViewModel()
) {
  val uiState by viewModel.uiState.collectAsState()

  if (uiState.uploadQueue.isEmpty()) {
    return // Don't show anything if no uploads
  }

  var isExpanded by remember { mutableStateOf(true) }

  FloatingUploadPanel(
    uiState = uiState,
    isExpanded = isExpanded,
    onToggleExpand = { isExpanded = !isExpanded },
    onCancel = { viewModel.cancelUpload(it) },
    onRetry = { viewModel.retryUpload(it) },
    onDismiss = { viewModel.dismissUpload(it) },
    onConfirmPartialFailure = { viewModel.confirmPartialFailure(it) },
    onCancelPartialFailure = { viewModel.cancelOnPartialFailure(it) },
    modifier = modifier
  )
}

@Composable
private fun FloatingUploadPanel(
  uiState: UploadManagerUiState,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onCancel: (String) -> Unit,
  onRetry: (String) -> Unit,
  onDismiss: (String) -> Unit,
  onConfirmPartialFailure: (String) -> Unit,
  onCancelPartialFailure: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .animateContentSize()
      .background(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
      )
      .padding(bottom = 16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      // Header with collapse button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Uploads",
            style = MaterialTheme.typography.titleMedium
          )

          val activeCount = uiState.uploadQueue.count { it.status == UploadStatus.UPLOADING }
          val doneCount = uiState.uploadQueue.count { it.status == UploadStatus.DONE }
          val errorCount = uiState.uploadQueue.count { it.status == UploadStatus.ERROR }

          Text(
            text = buildString {
              if (activeCount > 0) append("$activeCount uploading")
              if (doneCount > 0) {
                if (isNotEmpty()) append(" • ")
                append("$doneCount done")
              }
              if (errorCount > 0) {
                if (isNotEmpty()) append(" • ")
                append("$errorCount error")
              }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        IconButton(onClick = onToggleExpand) {
          Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (isExpanded) "Collapse" else "Expand"
          )
        }
      }

      // Upload items list (only if expanded)
      if (isExpanded) {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(uiState.uploadQueue) { item ->
            UploadProgressItem(
              item = item,
              onCancel = { onCancel(item.id) },
              onRetry = { onRetry(item.id) },
              onDismiss = { onDismiss(item.id) }
            )
          }
        }
      }

      // Partial failure dialog (if needed)
      if (uiState.showPartialFailureDialog && uiState.partialFailureData != null) {
        PartialFailureDialog(
          data = uiState.partialFailureData,
          onConfirm = { onConfirmPartialFailure(uiState.partialFailureData.queueItemId) },
          onCancel = { onCancelPartialFailure(uiState.partialFailureData.queueItemId) }
        )
      }
    }
  }
}

@Composable
private fun PartialFailureDialog(
  data: com.weekclip.android.upload.viewmodel.PartialFailureData,
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onCancel,
    title = {
      Text("Some Photos Failed to Upload")
    },
    text = {
      Text("${data.successCount} photos uploaded successfully, but ${data.failureCount} failed. Would you like to finalize with the successful photos?")
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Yes, Finalize")
      }
    },
    dismissButton = {
      TextButton(onClick = onCancel) {
        Text("Cancel All")
      }
    }
  )
}

/**
 * Compact upload indicator (for placing in a corner).
 * Shows just a small badge with upload count.
 */
@Composable
fun CompactUploadIndicator(
  uploadCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (uploadCount == 0) {
    return
  }

  Badge(
    modifier = modifier
      .clickable(onClick = onClick)
      .padding(8.dp)
  ) {
    Text(
      text = uploadCount.toString(),
      modifier = Modifier.padding(4.dp)
    )
  }
}
