package com.weekclip.android.upload.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekclip.android.upload.api.UploadApiClient
import com.weekclip.android.upload.manager.MultipartUploadManager
import com.weekclip.android.upload.manager.PhotoBundleUploadManager
import com.weekclip.android.upload.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

data class UploadManagerUiState(
  val uploadQueue: List<UploadQueueItem> = emptyList(),
  val activeUploadId: String? = null, // currently uploading
  val showPartialFailureDialog: Boolean = false,
  val partialFailureData: PartialFailureData? = null
)

data class PartialFailureData(
  val queueItemId: String,
  val successCount: Int,
  val failureCount: Int
)

@HiltViewModel
class UploadManagerViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val apiClient: UploadApiClient
) : ViewModel() {

  private val _uiState = MutableStateFlow(UploadManagerUiState())
  val uiState: StateFlow<UploadManagerUiState> = _uiState.asStateFlow()

  private val multipartUploadManager = MultipartUploadManager(context, apiClient)
  private val photoBundleUploadManager = PhotoBundleUploadManager(context, apiClient)

  /**
   * Enqueue an upload (video or photo bundle).
   * Returns the queue ID if successful.
   */
  fun enqueueUpload(input: EnqueueUploadInput): String? {
    val queueId = UUID.randomUUID().toString()

    val queueItem = when (input) {
      is EnqueueUploadInput.Video -> {
        UploadQueueItem(
          id = queueId,
          title = input.title,
          fileName = input.fileName,
          totalBytes = input.fileSize,
          status = UploadStatus.QUEUED,
          uploadType = "video"
        )
      }
      is EnqueueUploadInput.PhotoBundle -> {
        UploadQueueItem(
          id = queueId,
          title = input.title,
          fileName = "photos", // plural
          totalBytes = 0L, // will be calculated during upload
          status = UploadStatus.QUEUED,
          uploadType = "photo_bundle",
          photoCount = input.photoCount
        )
      }
    }

    _uiState.update { state ->
      state.copy(uploadQueue = state.uploadQueue + queueItem)
    }

    Timber.d("Enqueued upload: $queueId (${input.javaClass.simpleName})")

    // Start processing queue if not already processing
    processQueue()

    return queueId
  }

  /**
   * Cancel an upload that's in progress.
   */
  fun cancelUpload(queueItemId: String) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId && item.status == UploadStatus.UPLOADING) {
            item.copy(status = UploadStatus.CANCELED)
          } else {
            item
          }
        }
      )
    }

    Timber.d("Cancelled upload: $queueItemId")
    processQueue()
  }

  /**
   * Retry a failed upload.
   */
  fun retryUpload(queueItemId: String) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId && item.status == UploadStatus.ERROR) {
            item.copy(
              status = UploadStatus.QUEUED,
              progress = 0f,
              errorMessage = null,
              errorCode = null
            )
          } else {
            item
          }
        }
      )
    }

    Timber.d("Retrying upload: $queueItemId")
    processQueue()
  }

  /**
   * Dismiss a completed or errored upload from the list.
   */
  fun dismissUpload(queueItemId: String) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.filter { it.id != queueItemId }
      )
    }
  }

  /**
   * Confirm partial failure (user choice to finalize with successful photos).
   */
  fun confirmPartialFailure(queueItemId: String) {
    // Mark the upload as done even though some photos failed
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId) {
            item.copy(status = UploadStatus.DONE)
          } else {
            item
          }
        },
        showPartialFailureDialog = false,
        partialFailureData = null
      )
    }

    Timber.d("Confirmed partial failure for upload: $queueItemId")
    processQueue()
  }

  /**
   * Cancel on partial failure (user choice to cancel entire upload).
   */
  fun cancelOnPartialFailure(queueItemId: String) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId) {
            item.copy(status = UploadStatus.CANCELED)
          } else {
            item
          }
        },
        showPartialFailureDialog = false,
        partialFailureData = null
      )
    }

    Timber.d("Cancelled on partial failure for upload: $queueItemId")
    processQueue()
  }

  /**
   * Process the upload queue sequentially (one upload at a time).
   */
  private fun processQueue() {
    val state = _uiState.value

    // If already processing, don't start another
    if (state.activeUploadId != null) {
      return
    }

    // Find next queued item
    val nextItem = state.uploadQueue.firstOrNull { it.status == UploadStatus.QUEUED }
      ?: return

    _uiState.update { it.copy(activeUploadId = nextItem.id) }

    viewModelScope.launch {
      try {
        when (nextItem.uploadType) {
          "video" -> uploadVideo(nextItem)
          "photo_bundle" -> uploadPhotoBundle(nextItem)
          else -> {
            updateItemError(nextItem.id, "INVALID_TYPE", "Unknown upload type")
          }
        }
      } catch (e: Exception) {
        Timber.e(e, "Error during upload processing")
        updateItemError(nextItem.id, "UNKNOWN_ERROR", e.message ?: "Unknown error")
      } finally {
        _uiState.update { it.copy(activeUploadId = null) }
        // Continue processing queue
        processQueue()
      }
    }
  }

  private suspend fun uploadVideo(item: UploadQueueItem) {
    try {
      // Find the original upload input - we'll need to reconstruct it
      // For now, we assume the item has the file path stored somewhere
      // In a real app, you might store the full input in the queue item
      val filePath = item.fileName // This is simplified - real app would store full path

      // Create media draft
      updateItemProgress(item.id, 0f, UploadPhase.CREATING_DRAFT)
      val mediaDraft = apiClient.createMediaDraft(item.title, "video")

      // Create upload session
      updateItemProgress(item.id, 5f, UploadPhase.CREATING_SESSION)
      val session = apiClient.createUploadSession(mediaDraft.id, "video")

      // Upload file in parts
      val uploadResult = multipartUploadManager.uploadFile(
        item.title,
        filePath,
        session.id,
        onProgress = { progress ->
          val percentage = if (progress.totalBytes > 0) {
            (progress.uploadedBytes * 100) / progress.totalBytes
          } else {
            0
          }
          updateItemProgress(item.id, percentage.toFloat(), progress.phase)
        }
      )

      when {
        uploadResult.isSuccess -> {
          updateItemStatus(item.id, UploadStatus.DONE)
          Timber.d("Video upload completed: ${item.id}")
        }
        uploadResult.isFailure -> {
          val exception = uploadResult.exceptionOrNull()
          val (code, msg) = when (exception) {
            is UploadError -> exception.code to exception.message
            else -> "UPLOAD_FAILED" to (exception?.message ?: "Unknown error")
          }
          updateItemError(item.id, code, msg)
        }
      }

    } catch (e: UploadError) {
      updateItemError(item.id, e.code, e.message)
    } catch (e: Exception) {
      updateItemError(item.id, "UPLOAD_ERROR", e.message ?: "Upload failed")
    }
  }

  private suspend fun uploadPhotoBundle(item: UploadQueueItem) {
    try {
      // Create media draft
      updateItemProgress(item.id, 0f, UploadPhase.CREATING_DRAFT)
      val mediaDraft = apiClient.createMediaDraft(item.title, "photo_bundle")

      // Get photo paths (simplified - real app would store full paths)
      val photoPaths = emptyList<String>()

      // Upload photos
      val uploadResult = photoBundleUploadManager.uploadPhotos(
        item.title,
        photoPaths,
        mediaDraft.id,
        onProgress = { progress ->
          val percentage = if (progress.totalBytes > 0) {
            (progress.uploadedBytes * 100) / progress.totalBytes
          } else {
            0
          }
          updateItemProgress(item.id, percentage.toFloat(), progress.phase)
        }
      )

      when {
        uploadResult.isSuccess -> {
          updateItemStatus(item.id, UploadStatus.DONE)
          Timber.d("Photo bundle upload completed: ${item.id}")
        }
        uploadResult.isFailure -> {
          val exception = uploadResult.exceptionOrNull()
          val (code, msg) = when (exception) {
            is UploadError -> exception.code to exception.message
            else -> "UPLOAD_FAILED" to (exception?.message ?: "Unknown error")
          }
          updateItemError(item.id, code, msg)
        }
      }

    } catch (e: UploadError) {
      updateItemError(item.id, e.code, e.message)
    } catch (e: Exception) {
      updateItemError(item.id, "UPLOAD_ERROR", e.message ?: "Upload failed")
    }
  }

  private fun updateItemProgress(queueItemId: String, progress: Float, phase: UploadPhase) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId) {
            item.copy(
              status = UploadStatus.UPLOADING,
              progress = progress.coerceIn(0f, 100f)
            )
          } else {
            item
          }
        }
      )
    }
  }

  private fun updateItemStatus(queueItemId: String, status: UploadStatus) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId) {
            item.copy(status = status)
          } else {
            item
          }
        }
      )
    }
  }

  private fun updateItemError(queueItemId: String, errorCode: String, errorMessage: String?) {
    _uiState.update { state ->
      state.copy(
        uploadQueue = state.uploadQueue.map { item ->
          if (item.id == queueItemId) {
            item.copy(
              status = UploadStatus.ERROR,
              errorCode = errorCode,
              errorMessage = errorMessage ?: "Unknown error"
            )
          } else {
            item
          }
        }
      )
    }
  }
}
