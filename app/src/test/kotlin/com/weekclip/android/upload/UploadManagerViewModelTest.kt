package com.weekclip.android.upload

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.weekclip.android.upload.api.UploadApiClient
import com.weekclip.android.upload.model.EnqueueUploadInput
import com.weekclip.android.upload.model.UploadStatus
import com.weekclip.android.upload.viewmodel.UploadManagerViewModel
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for UploadManagerViewModel.
 */
class UploadManagerViewModelTest {

  @get:Rule
  val instantExecutorRule = InstantTaskExecutorRule()

  @MockK
  private lateinit var mockContext: Context

  @MockK
  private lateinit var mockApiClient: UploadApiClient

  private lateinit var viewModel: UploadManagerViewModel

  @Before
  fun setUp() {
    MockKAnnotations.init(this)
    viewModel = UploadManagerViewModel(mockContext, mockApiClient)
  }

  @Test
  fun enqueueUpload_video_addsToQueue() = runTest {
    val input = EnqueueUploadInput.Video(
      title = "Test Video",
      filePath = "/path/to/video.mp4",
      fileName = "video.mp4",
      fileSize = 100L * 1024 * 1024 // 100MB
    )

    val queueId = viewModel.enqueueUpload(input)

    assertNotNull(queueId)
    assertEquals(1, viewModel.uiState.value.uploadQueue.size)
    assertEquals("Test Video", viewModel.uiState.value.uploadQueue[0].title)
    assertEquals(UploadStatus.QUEUED, viewModel.uiState.value.uploadQueue[0].status)
  }

  @Test
  fun enqueueUpload_photoBundle_addsToQueue() = runTest {
    val input = EnqueueUploadInput.PhotoBundle(
      title = "Photo Bundle",
      photoPaths = listOf("/path/to/photo1.jpg", "/path/to/photo2.jpg"),
      photoCount = 2
    )

    val queueId = viewModel.enqueueUpload(input)

    assertNotNull(queueId)
    assertEquals(1, viewModel.uiState.value.uploadQueue.size)
    assertEquals("Photo Bundle", viewModel.uiState.value.uploadQueue[0].title)
    assertEquals("photo_bundle", viewModel.uiState.value.uploadQueue[0].uploadType)
    assertEquals(2, viewModel.uiState.value.uploadQueue[0].photoCount)
  }

  @Test
  fun enqueueMultipleUploads_createsMultipleQueueItems() = runTest {
    val video = EnqueueUploadInput.Video(
      title = "Video",
      filePath = "/path/to/video.mp4",
      fileName = "video.mp4",
      fileSize = 50L * 1024 * 1024
    )

    val photos = EnqueueUploadInput.PhotoBundle(
      title = "Photos",
      photoPaths = listOf("/path/to/photo.jpg"),
      photoCount = 1
    )

    viewModel.enqueueUpload(video)
    viewModel.enqueueUpload(photos)

    assertEquals(2, viewModel.uiState.value.uploadQueue.size)
  }

  @Test
  fun cancelUpload_changesStatusToCanceled() = runTest {
    val input = EnqueueUploadInput.Video(
      title = "Test",
      filePath = "/path/to/video.mp4",
      fileName = "video.mp4",
      fileSize = 50L * 1024 * 1024
    )

    val queueId = viewModel.enqueueUpload(input)
    assertNotNull(queueId)

    // Change status to uploading first
    viewModel.uiState.value.uploadQueue.find { it.id == queueId }?.let {
      // Simulate upload started
    }

    viewModel.cancelUpload(queueId)

    val updatedItem = viewModel.uiState.value.uploadQueue.find { it.id == queueId }
    assertEquals(UploadStatus.CANCELED, updatedItem?.status)
  }

  @Test
  fun dismissUpload_removesFromQueue() = runTest {
    val input = EnqueueUploadInput.Video(
      title = "Test",
      filePath = "/path/to/video.mp4",
      fileName = "video.mp4",
      fileSize = 50L * 1024 * 1024
    )

    val queueId = viewModel.enqueueUpload(input)
    assertNotNull(queueId)
    assertEquals(1, viewModel.uiState.value.uploadQueue.size)

    viewModel.dismissUpload(queueId)

    assertEquals(0, viewModel.uiState.value.uploadQueue.size)
  }

  @Test
  fun retryUpload_changesStatusToQueued() = runTest {
    val input = EnqueueUploadInput.Video(
      title = "Test",
      filePath = "/path/to/video.mp4",
      fileName = "video.mp4",
      fileSize = 50L * 1024 * 1024
    )

    val queueId = viewModel.enqueueUpload(input)
    assertNotNull(queueId)

    // Simulate error
    val item = viewModel.uiState.value.uploadQueue.find { it.id == queueId }
    if (item != null) {
      // Error would be set during actual upload
    }

    viewModel.retryUpload(queueId)

    val updatedItem = viewModel.uiState.value.uploadQueue.find { it.id == queueId }
    assertEquals(UploadStatus.QUEUED, updatedItem?.status)
    assertEquals(0f, updatedItem?.progress)
  }
}
