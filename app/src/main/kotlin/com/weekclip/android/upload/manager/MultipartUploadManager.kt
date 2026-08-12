package com.weekclip.android.upload.manager

import android.content.Context
import com.weekclip.android.upload.api.UploadApiClient
import com.weekclip.android.upload.model.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.File
import kotlin.math.min

/**
 * Manages multipart upload of single video/file.
 */
class MultipartUploadManager(
  private val context: Context,
  private val apiClient: UploadApiClient
) {
  companion object {
    const val PART_SIZE = 10 * 1024 * 1024L // 10MB
    const val MAX_PARALLEL_PARTS = 4
    const val MAX_RETRIES_PER_PART = 3
    const val RETRY_BASE_DELAY_MS = 700L
  }

  private var progressCallback: ((UploadProgress) -> Unit)? = null

  fun setProgressCallback(callback: (UploadProgress) -> Unit) {
    this.progressCallback = callback
  }

  suspend fun uploadFile(
    title: String,
    filePath: String,
    sessionId: String,
    onProgress: (UploadProgress) -> Unit = {}
  ): Result<CompleteUploadResponse> = withContext(Dispatchers.IO) {
    try {
      val file = File(filePath)
      if (!file.exists()) {
        return@withContext Result.failure(
          Exception("File not found: $filePath")
        )
      }

      val totalBytes = file.length()
      val partCount = ((totalBytes + PART_SIZE - 1) / PART_SIZE).toInt()

      Timber.d("Starting multipart upload: $partCount parts, ${totalBytes / (1024 * 1024)}MB total")

      // Request part URLs
      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = 0,
          totalParts = partCount,
          phase = UploadPhase.REQUESTING_URLS
        )
      )

      val partUrls = apiClient.requestPartUrls(sessionId, partCount)
        .associateBy { it.partNumber }

      if (partUrls.size != partCount) {
        return@withContext Result.failure(
          Exception("Failed to get all part URLs: got ${partUrls.size}, expected $partCount")
        )
      }

      // Upload parts in parallel (max 4 at a time)
      val partEtags = mutableMapOf<Int, String>()
      var totalUploaded = 0L
      val startTime = System.currentTimeMillis()

      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = 0,
          totalParts = partCount,
          phase = UploadPhase.UPLOADING
        )
      )

      // Upload parts in parallel batches
      for (batchStart in 0 until partCount step MAX_PARALLEL_PARTS) {
        val batchEnd = min(batchStart + MAX_PARALLEL_PARTS, partCount)
        val jobs = mutableListOf<Job>()

        for (partNum in batchStart until batchEnd) {
          val job = launch {
            val partNumber = partNum + 1
            val signedPart = partUrls[partNumber]
              ?: throw Exception("No signed URL for part $partNumber")

            val partData = readPart(file, partNum.toLong() * PART_SIZE, signedPart.size)
            var lastError: Exception? = null

            // Retry logic for individual part
            for (attempt in 1..MAX_RETRIES_PER_PART) {
              try {
                val response = apiClient.uploadPartToS3(
                  signedPart.uploadUrl,
                  RequestBody.create("application/octet-stream".toMediaType(), partData)
                )

                if (response.isSuccessful) {
                  val etag = response.headers()["etag"] ?: response.headers()["ETag"]
                  if (etag != null) {
                    synchronized(partEtags) {
                      partEtags[partNumber] = etag
                    }
                  }
                  Timber.d("Part $partNumber uploaded successfully")

                  // Update progress
                  synchronized(this@MultipartUploadManager) {
                    totalUploaded += partData.size
                    val elapsed = System.currentTimeMillis() - startTime
                    val speed = if (elapsed > 0) (totalUploaded * 1000) / elapsed else 0L
                    onProgress(
                      UploadProgress(
                        totalBytes = totalBytes,
                        uploadedBytes = totalUploaded,
                        completedParts = partEtags.size,
                        totalParts = partCount,
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
                if (attempt < MAX_RETRIES_PER_PART) {
                  val delayMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                  Timber.w(e, "Part $partNumber upload failed (attempt $attempt/$MAX_RETRIES_PER_PART), retrying in ${delayMs}ms")
                  delay(delayMs)
                }
              }
            }

            throw lastError ?: Exception("Part $partNumber upload failed after $MAX_RETRIES_PER_PART attempts")
          }

          jobs.add(job)
        }

        // Wait for batch to complete
        jobs.awaitAll()
      }

      if (partEtags.size != partCount) {
        return@withContext Result.failure(
          Exception("Failed to upload all parts: ${partEtags.size}/$partCount")
        )
      }

      // Complete upload
      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = totalBytes,
          completedParts = partCount,
          totalParts = partCount,
          phase = UploadPhase.FINALIZING
        )
      )

      val etags = (1..partCount).map { partNum ->
        PartEtag(partNum, partEtags[partNum] ?: "")
      }

      val completeResponse = apiClient.completeUpload(sessionId, etags)

      onProgress(
        UploadProgress(
          totalBytes = totalBytes,
          uploadedBytes = totalBytes,
          completedParts = partCount,
          totalParts = partCount,
          phase = UploadPhase.COMPLETE
        )
      )

      Result.success(completeResponse)

    } catch (e: UploadError) {
      Timber.e(e, "Multipart upload error: ${e.code}")
      Result.failure(e)
    } catch (e: CancellationException) {
      Timber.w("Multipart upload cancelled")
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Multipart upload failed")
      Result.failure(e)
    }
  }

  private fun readPart(file: File, offset: Long, size: Long): ByteArray {
    return file.inputStream().use { input ->
      input.skip(offset)
      val buffer = ByteArray(size.toInt())
      val bytesRead = input.read(buffer)
      if (bytesRead != size.toInt()) {
        throw Exception("Failed to read part: expected $size bytes, got $bytesRead")
      }
      buffer
    }
  }
}

/**
 * Custom exception for upload errors.
 */
class UploadErrorException(
  val uploadError: UploadError
) : Exception(uploadError.message)
