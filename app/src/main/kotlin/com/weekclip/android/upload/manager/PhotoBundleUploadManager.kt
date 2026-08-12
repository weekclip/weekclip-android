package com.weekclip.android.upload.manager

import android.content.Context
import android.graphics.BitmapFactory
import com.weekclip.android.upload.api.UploadApiClient
import com.weekclip.android.upload.model.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import timber.log.Timber
import java.io.File
import kotlin.math.min

/**
 * Manages batch upload of photos (JPEG/PNG/WebP).
 */
class PhotoBundleUploadManager(
  private val context: Context,
  private val apiClient: UploadApiClient
) {
  companion object {
    const val MAX_PHOTO_COUNT = 50
    const val MAX_PHOTO_SIZE = 25 * 1024 * 1024L // 25MB per photo
    const val MAX_PARALLEL_PHOTOS = 3
    const val MAX_RETRIES_PER_PHOTO = 2
    const val RETRY_BASE_DELAY_MS = 700L

    // Thumbnail generation
    const val THUMBNAIL_WIDTH = 480
    const val THUMBNAIL_QUALITY = 80
  }

  suspend fun uploadPhotos(
    title: String,
    photoPaths: List<String>,
    mediaId: String,
    onProgress: (UploadProgress) -> Unit = {}
  ): Result<FinalizePhotosResponse> = withContext(Dispatchers.IO) {
    try {
      // Validate photos
      if (photoPaths.isEmpty()) {
        return@withContext Result.failure(Exception("No photos provided"))
      }

      if (photoPaths.size > MAX_PHOTO_COUNT) {
        return@withContext Result.failure(
          Exception("Too many photos: ${photoPaths.size} > $MAX_PHOTO_COUNT")
        )
      }

      val photos = photoPaths.mapIndexed { index, path ->
        val file = File(path)
        if (!file.exists()) {
          throw Exception("Photo not found: $path")
        }

        if (file.length() > MAX_PHOTO_SIZE) {
          throw Exception("Photo too large: ${file.length()} > $MAX_PHOTO_SIZE")
        }

        val mimeType = getMimeType(path)
        PhotoPresignRequest(
          name = file.name,
          mimeType = mimeType,
          size = file.length(),
          gpsLat = null,
          gpsLon = null
        )
      }

      val totalBytes = photos.sumOf { it.size }
      val photoCount = photos.size

      Timber.d("Starting photo bundle upload: $photoCount photos, ${totalBytes / (1024 * 1024)}MB total")

      // Batch presign
      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = 0,
          totalParts = photoCount,
          phase = UploadPhase.REQUESTING_URLS
        )
      )

      val presignResponse = apiClient.presignPhotos(mediaId, photos)
      val photoEntries = presignResponse.photos.associateBy { it.id }

      if (photoEntries.size != photoCount) {
        return@withContext Result.failure(
          Exception("Failed to presign all photos: got ${photoEntries.size}, expected $photoCount")
        )
      }

      // Upload photos in parallel (max 3)
      val uploadedPhotoIds = mutableListOf<String>()
      var totalUploaded = 0L
      val startTime = System.currentTimeMillis()
      val failedPhotoIds = mutableListOf<String>()

      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = 0,
          totalParts = photoCount,
          phase = UploadPhase.UPLOADING
        )
      )

      // Upload in parallel batches
      for (batchStart in photoPaths.indices step MAX_PARALLEL_PHOTOS) {
        val batchEnd = min(batchStart + MAX_PARALLEL_PHOTOS, photoPaths.size)
        val jobs = mutableListOf<Job>()

        for (idx in batchStart until batchEnd) {
          val job = launch {
            val photoPath = photoPaths[idx]
            val photo = photos[idx]
            val presignedEntry = photoEntries.values.find { it.name == photo.name }
              ?: throw Exception("No presigned URL for ${photo.name}")

            var lastError: Exception? = null

            // Retry logic for individual photo
            for (attempt in 1..MAX_RETRIES_PER_PHOTO) {
              try {
                // Upload original photo
                val photoFile = File(photoPath)
                val photoBody = photoFile.inputStream().use { input ->
                  val bytes = input.readBytes()
                  RequestBody.create(photo.mimeType.toMediaType(), bytes)
                }

                val response = apiClient.uploadFileToS3(
                  presignedEntry.uploadUrl,
                  photoBody
                )

                if (response.isSuccessful) {
                  Timber.d("Photo ${photo.name} uploaded successfully")

                  synchronized(uploadedPhotoIds) {
                    uploadedPhotoIds.add(presignedEntry.id)
                    totalUploaded += photo.size

                    val elapsed = System.currentTimeMillis() - startTime
                    val speed = if (elapsed > 0) (totalUploaded * 1000) / elapsed else 0L

                    onProgress(
                      UploadProgress(
                        totalBytes = totalBytes,
                        uploadedBytes = totalUploaded,
                        completedParts = uploadedPhotoIds.size,
                        totalParts = photoCount,
                        speedBytesPerSecond = speed,
                        phase = UploadPhase.UPLOADING
                      )
                    )
                  }
                  return@launch
                } else {
                  lastError = Exception("HTTP ${response.code()}: ${response.message()}")
                  if (response.code() == 402) {
                    throw UploadError(
                      code = "INSUFFICIENT_CREDIT",
                      message = "Insufficient storage capacity",
                      retryable = false
                    )
                  }
                }
              } catch (e: UploadError) {
                throw e
              } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES_PER_PHOTO) {
                  val delayMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                  Timber.w(e, "Photo ${photo.name} upload failed (attempt $attempt/$MAX_RETRIES_PER_PHOTO), retrying in ${delayMs}ms")
                  delay(delayMs)
                }
              }
            }

            synchronized(failedPhotoIds) {
              failedPhotoIds.add(presignedEntry.id)
            }
            Timber.w("Photo ${photo.name} upload failed after $MAX_RETRIES_PER_PHOTO attempts")
          }

          jobs.add(job)
        }

        // Wait for batch to complete
        jobs.awaitAll()
      }

      // Handle partial failures
      if (uploadedPhotoIds.isEmpty()) {
        return@withContext Result.failure(
          Exception("All photos failed to upload")
        )
      }

      if (failedPhotoIds.isNotEmpty()) {
        Timber.w("${failedPhotoIds.size} photos failed to upload")
        // In a real app, we might show a dialog here asking user to confirm finalizing
        // with only the successful photos
      }

      // Finalize upload
      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = totalBytes,
          completedParts = uploadedPhotoIds.size,
          totalParts = photoCount,
          phase = UploadPhase.FINALIZING
        )
      )

      val finalizeResponse = apiClient.finalizePhotos(
        mediaId,
        presignResponse.sessionId,
        uploadedPhotoIds
      )

      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = totalBytes,
          completedParts = uploadedPhotoIds.size,
          totalParts = photoCount,
          phase = UploadPhase.COMPLETE
        )
      )

      Result.success(finalizeResponse)

    } catch (e: UploadError) {
      Timber.e(e, "Photo bundle upload error: ${e.code}")
      Result.failure(e)
    } catch (e: CancellationException) {
      Timber.w("Photo bundle upload cancelled")
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Photo bundle upload failed")
      Result.failure(e)
    }
  }

  private fun getMimeType(filePath: String): String {
    return when {
      filePath.endsWith(".jpg", ignoreCase = true) ||
        filePath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
      filePath.endsWith(".png", ignoreCase = true) -> "image/png"
      filePath.endsWith(".webp", ignoreCase = true) -> "image/webp"
      else -> "image/jpeg" // default
    }
  }
}
