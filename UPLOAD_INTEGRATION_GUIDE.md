# Upload System Integration Guide

This guide shows how to integrate the Phase 3 Upload System into existing screens (StudioListScreen, MediaListScreen).

## Prerequisites

- Dependencies already added to `gradle/libs.versions.toml`
- NetworkModule already updated with UploadApiClient
- UploadScreen already integrated into Navigation
- All upload components created in `app/src/main/kotlin/com/weekclip/android/upload/`

## File Picker Implementation

You'll need to implement file picker functionality. Here's a helper composable:

```kotlin
// In a new file: studio/ui/FilePickerLauncher.kt
package com.weekclip.android.studio.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberVideoPickerLauncher(onFilePicked: (String) -> Unit) {
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri ->
    uri?.let {
      // Convert URI to file path
      // For now, you can use MediaPickerViewModel.getFileFromUri()
      onFilePicked(it.toString())
    }
  }

  return {
    launcher.launch("video/*")
  }
}

@Composable
fun rememberPhotoPickerLauncher(onFilesPicked: (List<String>) -> Unit) {
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetMultipleContents()
  ) { uris ->
    val paths = uris.map { it.toString() }
    onFilesPicked(paths)
  }

  return {
    launcher.launch("image/*")
  }
}
```

## Integration into StudioListScreen

```kotlin
// In studio/ui/StudioListScreen.kt
@Composable
fun StudioListScreen(
  onStudioSelected: (String) -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
  uploadManagerViewModel: UploadManagerViewModel = hiltViewModel(),  // Add this
  mediaPickerViewModel: MediaPickerViewModel = hiltViewModel()       // Add this
) {
  var showVideoUploadDialog by remember { mutableStateOf(false) }
  var selectedVideoTitle by remember { mutableStateOf("") }

  Column(modifier = modifier.fillMaxSize()) {
    // Existing TopAppBar
    TopAppBar(
      title = { Text("Studios") },
      actions = {
        // Add upload button
        IconButton(onClick = { showVideoUploadDialog = true }) {
          Icon(Icons.Default.CloudUpload, contentDescription = "Upload Video")
        }

        IconButton(onClick = onLogout) {
          Icon(Icons.Default.Logout, contentDescription = "Logout")
        }
      }
    )

    // Existing studio list content
    // ...

    // Add this upload button
    Button(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(16.dp),
      onClick = { showVideoUploadDialog = true }
    ) {
      Icon(Icons.Default.CloudUpload, modifier = Modifier.padding(end = 8.dp))
      Text("Upload Video")
    }
  }

  // Video upload dialog
  if (showVideoUploadDialog) {
    VideoUploadDialog(
      onConfirm = { title ->
        // Open file picker
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
          type = "video/*"
        }
        // Note: Actual implementation needs activity context for launcher
        showVideoUploadDialog = false
      },
      onDismiss = { showVideoUploadDialog = false }
    )
  }
}

@Composable
private fun VideoUploadDialog(
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var title by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Upload Video") },
    text = {
      TextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Video Title") },
        modifier = Modifier.fillMaxWidth()
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(title) },
        enabled = title.isNotEmpty()
      ) {
        Text("Select File")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
```

## Integration into MediaListScreen

```kotlin
// In studio/ui/MediaListScreen.kt
@Composable
fun MediaListScreen(
  studioId: String,
  onBack: () -> Unit,
  onMediaSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
  uploadManagerViewModel: UploadManagerViewModel = hiltViewModel(),  // Add this
  mediaPickerViewModel: MediaPickerViewModel = hiltViewModel()       // Add this
) {
  var showPhotoUploadDialog by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    // Existing TopAppBar
    TopAppBar(
      title = { Text("Media") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
      },
      actions = {
        // Add upload button
        IconButton(onClick = { showPhotoUploadDialog = true }) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload Photos")
        }
      }
    )

    // Existing media list content
    // ...

    // Add this upload button
    Button(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(16.dp),
      onClick = { showPhotoUploadDialog = true }
    ) {
      Icon(Icons.Default.PhotoLibrary, modifier = Modifier.padding(end = 8.dp))
      Text("Upload Photos")
    }
  }

  // Photo upload dialog
  if (showPhotoUploadDialog) {
    PhotoUploadDialog(
      onConfirm = { title ->
        // Open file picker for multiple photos
        // Implementation similar to video picker
        showPhotoUploadDialog = false
      },
      onDismiss = { showPhotoUploadDialog = false }
    )
  }
}

@Composable
private fun PhotoUploadDialog(
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var title by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Upload Photos") },
    text = {
      TextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Bundle Title") },
        modifier = Modifier.fillMaxWidth()
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(title) },
        enabled = title.isNotEmpty()
      ) {
        Text("Select Photos")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
```

## Using the Upload System from Code

Once you have the file picker implementation, here's how to use the upload system:

```kotlin
// Get the upload manager ViewModel
val uploadManagerViewModel: UploadManagerViewModel = hiltViewModel()
val mediaPickerViewModel: MediaPickerViewModel = hiltViewModel()

// When user picks a video file
val videoInput = mediaPickerViewModel.createVideoUploadInput(
  uri = selectedVideoUri,
  title = "My Video Title"
)

if (videoInput != null) {
  uploadManagerViewModel.enqueueUpload(videoInput)
}

// When user picks photo files
val photoInput = mediaPickerViewModel.createPhotoBundleUploadInput(
  uris = selectedPhotoUris,
  title = "My Photo Bundle"
)

if (photoInput != null) {
  uploadManagerViewModel.enqueueUpload(photoInput)
}
```

## State Management

The upload progress is automatically managed by the UploadManagerViewModel:

```kotlin
// Listen to upload queue changes
val uploadState by viewModel.uiState.collectAsState()

// Access current uploads
uploadState.uploadQueue.forEach { item ->
  println("${item.title}: ${item.progress}%")
}

// Show upload count in UI
val activeUploads = uploadState.uploadQueue.count { it.status == UploadStatus.UPLOADING }
Text("Active uploads: $activeUploads")
```

## Error Handling UI

The error handling is built into UploadProgressItem:

```kotlin
// Errors are shown in the upload panel
// User can:
// - Retry: viewModel.retryUpload(itemId)
// - Cancel: viewModel.cancelUpload(itemId)
// - Dismiss: viewModel.dismissUpload(itemId)

// For 402 Insufficient Capacity errors:
if (item.errorCode == "INSUFFICIENT_CREDIT") {
  // Show capacity details and CTA to add capacity
}
```

## Testing

### Acceptance Criteria Checklist

- [ ] File picker opens when upload button clicked
- [ ] Selected file is enqueued and appears in upload list
- [ ] Progress bar updates during upload
- [ ] Multipart upload shows progress by parts
- [ ] Photo bundle upload shows progress by photos
- [ ] Cancel button stops upload
- [ ] Retry button resumes failed upload
- [ ] 402 error handled with capacity message
- [ ] Error message shown for network failures
- [ ] Upload persists during screen rotation
- [ ] Multiple uploads queue and process sequentially
- [ ] Upload completes successfully
- [ ] Done/error items can be dismissed
- [ ] No crashes during upload

### Manual Testing Steps

1. Build and run the app
2. Login and navigate to StudioList or MediaList
3. Tap upload button
4. Select a file from picker
5. Verify progress updates
6. Test cancel during upload
7. Enqueue multiple files
8. Rotate device
9. Verify queue persists and upload continues

## Next Steps (Phase 4)

- Add WorkManager integration for background uploads
- Implement offline queue persistence (database)
- Add resume capability for interrupted uploads
- Add bandwidth throttling UI options
- Add image compression options for photos

## Troubleshooting

### Upload doesn't start
- Check API client is properly injected
- Verify API base URL in NetworkModule
- Check network connectivity

### Progress not updating
- Verify onProgress callback is called
- Check StateFlow updates from UI coroutine
- Verify Compose recomposition triggered

### File picker doesn't work
- Ensure activity has MANAGE_EXTERNAL_STORAGE permission (for some devices)
- Check URI permissions are granted
- Verify file MIME types match picker filter

## API Error Codes

- **402**: Insufficient storage capacity
- **400**: Invalid request (bad file, metadata, etc.)
- **401**: Unauthorized (token expired)
- **403**: Forbidden (permission denied)
- **404**: Not found (media/session not found)
- **409**: Conflict (duplicate upload, session expired)
- **429**: Rate limited
- **500**: Server error (retry recommended)

## Performance Notes

- Multipart uploads: 4 chunks in parallel, 10MB each
- Photo uploads: 3 photos in parallel
- Total parallelism is controlled to avoid overwhelming device
- Retry uses exponential backoff to reduce server load
- Progress updates throttled to 60fps to avoid excessive recompositions

---

For more details, see:
- `app/src/main/kotlin/com/weekclip/android/upload/README.md` - Technical documentation
- `app/src/main/kotlin/com/weekclip/android/upload/model/UploadModels.kt` - Data structures
- `app/src/main/kotlin/com/weekclip/android/upload/viewmodel/UploadManagerViewModel.kt` - State management
