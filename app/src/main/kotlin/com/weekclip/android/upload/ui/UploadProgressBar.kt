package com.weekclip.android.upload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weekclip.android.upload.model.UploadQueueItem
import com.weekclip.android.upload.model.UploadStatus

@Composable
fun UploadProgressItem(
  item: UploadQueueItem,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(8.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
  ) {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      // Header row: title and status
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        StatusBadge(item.status)
      }

      // File info row
      Text(
        text = "${item.fileName} • ${formatBytes(item.totalBytes)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      // Progress bar (visible only when uploading)
      if (item.status == UploadStatus.UPLOADING) {
        LinearProgressIndicator(
          progress = { item.progress / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(bottom = 8.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Progress info row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${item.progress.toInt()}%",
            style = MaterialTheme.typography.labelSmall
          )

          if (item.photoCount > 0) {
            Text(
              text = "${(item.progress / 100f * item.photoCount).toInt()}/${item.photoCount} photos",
              style = MaterialTheme.typography.labelSmall
            )
          }
        }
      } else if (item.status == UploadStatus.DONE) {
        LinearProgressIndicator(
          progress = { 1f },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(bottom = 12.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }

      // Error message (if any)
      if (item.errorMessage != null) {
        Text(
          text = item.errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(bottom = 12.dp),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      // Action buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        when (item.status) {
          UploadStatus.UPLOADING -> {
            TextButton(onClick = onCancel) {
              Text("Cancel")
            }
          }
          UploadStatus.ERROR -> {
            TextButton(onClick = onRetry) {
              Text("Retry")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onDismiss) {
              Text("Dismiss")
            }
          }
          UploadStatus.DONE -> {
            TextButton(onClick = onDismiss) {
              Text("Dismiss")
            }
          }
          else -> {
            // QUEUED, CANCELED: no action buttons
          }
        }
      }
    }
  }
}

@Composable
private fun StatusBadge(status: UploadStatus) {
  val (backgroundColor, textColor, label) = when (status) {
    UploadStatus.QUEUED -> Triple(
      MaterialTheme.colorScheme.tertiaryContainer,
      MaterialTheme.colorScheme.onTertiaryContainer,
      "Queued"
    )
    UploadStatus.UPLOADING -> Triple(
      MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.onPrimaryContainer,
      "Uploading"
    )
    UploadStatus.DONE -> Triple(
      MaterialTheme.colorScheme.tertiaryContainer,
      MaterialTheme.colorScheme.onTertiaryContainer,
      "Done"
    )
    UploadStatus.ERROR -> Triple(
      MaterialTheme.colorScheme.errorContainer,
      MaterialTheme.colorScheme.onErrorContainer,
      "Error"
    )
    UploadStatus.CANCELED -> Triple(
      MaterialTheme.colorScheme.surfaceVariant,
      MaterialTheme.colorScheme.onSurfaceVariant,
      "Canceled"
    )
  }

  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(8.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = textColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

private fun formatBytes(bytes: Long): String {
  return when {
    bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
  }
}
