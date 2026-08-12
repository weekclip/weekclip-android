# Upload System - Phase 3

This directory contains the complete implementation of the Android upload system for weekclip, supporting:
- **Direct/Studio Upload** - Single video/file upload with multipart protocol
- **Photo Bundle Upload** - Batch upload of multiple images

## Architecture

### Data Layer
- **UploadModels.kt** - Data classes for API requests/responses and state management
- **UploadApiClient.kt** - Retrofit service and API client for upload endpoints

### Business Logic
- **MultipartUploadManager.kt** - Handles single video upload with chunked/multipart logic
- **PhotoBundleUploadManager.kt** - Handles batch photo upload with parallel uploads

### UI Layer
- **UploadScreen.kt** - Main floating panel showing active uploads
- **UploadProgressBar.kt** - Individual upload item UI with progress tracking

### ViewModel
- **UploadManagerViewModel.kt** - Orchestrates queue management and upload lifecycle
- **MediaPickerViewModel.kt** - File picker integration and URI to upload input conversion

## Key Features

### Multipart Video Upload
```kotlin
// Upload is automatically split into 10MB chunks
// Maximum 4 chunks upload in parallel
// Per-chunk retry: 3 attempts with exponential backoff
// Progress tracking at byte and chunk level
```

### Photo Bundle Upload
```kotlin
// Batch presign photos in one API call
// Maximum 3 photos upload in parallel
// Per-photo retry: 2 attempts
// Handles partial failures with user confirmation
```

### Queue Management
```kotlin
// Only one file/bundle uploads at a time
// Files are queued and processed sequentially
// Queue persists during screen rotations (via ViewModel)
// Manual retry and cancel options
```

### Error Handling
```kotlin
// Structured error codes (INSUFFICIENT_CREDIT, NETWORK_ERROR, etc.)
// 402 capacity errors show capacity details
// Automatic retries for transient errors
// User-facing error messages
```

## Integration

### 1. Add Upload Button to StudioListScreen

```kotlin
// In StudioListScreen.kt
Button(
  onClick = { 
    // Open file picker for video
    // Get selected video URI
    val uploadInput = mediaPickerViewModel.createVideoUploadInput(uri, "My Video")
    uploadManagerViewModel.enqueueUpload(uploadInput)
  }
) {
  Text("Upload Video")
}
```

### 2. Add Upload Button to MediaListScreen

```kotlin
// In MediaListScreen.kt
Button(
  onClick = { 
    // Open file picker for photos
    // Get selected photo URIs
    val uploadInput = mediaPickerViewModel.createPhotoBundleUploadInput(uris, "Photo Bundle")
    uploadManagerViewModel.enqueueUpload(uploadInput)
  }
) {
  Text("Upload Photos")
}
```

### 3. UploadScreen Already Integrated

The `UploadScreen` is automatically composed into the navigation overlay and will show when there are active uploads.

## State Management

### UploadQueueItem
```kotlin
data class UploadQueueItem(
  val id: String,                    // Unique queue ID
  val title: String,                 // User-friendly name
  val fileName: String,              // Original filename
  val totalBytes: Long,              // File size
  val status: UploadStatus,          // QUEUED, UPLOADING, DONE, ERROR, CANCELED
  val progress: Float,               // 0-100 percentage
  val errorMessage: String?,         // Human-readable error
  val errorCode: String?,            // Machine-readable error code
  val uploadType: String,            // "video" or "photo_bundle"
  val photoCount: Int                // For photo bundles
)
```

### UploadStatus
```kotlin
enum class UploadStatus {
  QUEUED,      // Waiting to upload
  UPLOADING,   // Currently uploading
  DONE,        // Successfully completed
  ERROR,       // Failed (can retry)
  CANCELED     // User cancelled
}
```

## API Integration

### Required Endpoints

1. **POST /medias/draft** - Create media draft
   - Request: `{ title: string, media_type: "video" | "photo_bundle" }`
   - Response: `{ id, title, studio_id, status }`

2. **POST /medias/{mediaId}/upload-session** - Start upload session
   - Request: `{ upload_type: "video" | "photos" }`
   - Response: `{ id, media_id, upload_type, part_size, created_at, expires_at }`

3. **POST /upload-sessions/{sessionId}/part-urls** - Get signed URLs for parts
   - Request: `{ part_count: number }`
   - Response: `[{ id, part_number, upload_url, size }, ...]`

4. **POST /upload-sessions/{sessionId}/complete** - Finalize multipart upload
   - Request: `{ session_id, part_etags: [{ part_number, etag }, ...] }`
   - Response: `{ status, media_id, upload_session_id }`

5. **POST /medias/{mediaId}/presign-photos** - Batch presign photos
   - Request: `{ media_id, photos: [{ name, mime_type, size, gps_lat, gps_lon }, ...] }`
   - Response: `{ media_id, photos: [{ id, name, upload_url, thumbnail_upload_url, size, created_at }, ...], session_id }`

6. **POST /medias/{mediaId}/finalize-photos** - Finalize photo upload
   - Request: `{ media_id, session_id, photo_ids: string[] }`
   - Response: `{ status, media_id, photo_count }`

7. **PUT {url}** - Upload to S3 presigned URL
   - Request body: raw file bytes
   - Response headers: must include `etag` (for multipart) or `ETag`

## Configuration Constants

### MultipartUploadManager
```kotlin
const val PART_SIZE = 10 * 1024 * 1024L          // 10MB
const val MAX_PARALLEL_PARTS = 4                  // Max 4 parts in parallel
const val MAX_RETRIES_PER_PART = 3                // Retry 3 times
const val RETRY_BASE_DELAY_MS = 700L              // 700ms base retry delay
```

### PhotoBundleUploadManager
```kotlin
const val MAX_PHOTO_COUNT = 50                    // Max 50 photos per bundle
const val MAX_PHOTO_SIZE = 25 * 1024 * 1024L      // 25MB per photo
const val MAX_PARALLEL_PHOTOS = 3                 // Max 3 photos in parallel
const val MAX_RETRIES_PER_PHOTO = 2               // Retry 2 times
const val RETRY_BASE_DELAY_MS = 700L              // 700ms base retry delay
```

## Error Codes

```kotlin
"INSUFFICIENT_CREDIT"      // 402 - Not enough storage capacity
"NETWORK_ERROR"             // Network connectivity issue
"TIMEOUT"                   // Request timeout
"UPLOAD_FAILED"             // Upload failed
"INVALID_TYPE"              // Unknown upload type
"UNKNOWN_ERROR"             // Catch-all error
```

## Testing

### Manual Testing
1. Enqueue a video upload from StudioListScreen
2. Verify progress updates in real-time
3. Test cancel button during upload
4. Test retry button on failed upload
5. Enqueue multiple uploads and verify queue processing
6. Test device rotation (verify queue persists)

### Error Scenarios
1. Test with 402 response (show capacity error)
2. Test with network disconnection (show retry)
3. Test partial photo bundle failure (show confirmation dialog)
4. Test file not found error
5. Test large file handling

## Performance Considerations

- Parts upload in parallel (4 max) to minimize upload time
- Photos upload in parallel (3 max) to avoid overwhelming network
- Retry with exponential backoff to avoid thundering herd
- Progress updates on main thread via StateFlow (safe in Compose)
- File reading done on IO dispatcher (not blocking main)

## Future Enhancements (Phase 4)

- WorkManager integration for background uploads
- Offline queue persistence (database)
- Resume interrupted uploads
- Smart retry based on error type (network vs server)
- Upload speed analytics
- Bandwidth throttling options
- Compression options (for photos)
