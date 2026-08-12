package com.weekclip.android.upload.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.weekclip.android.upload.model.EnqueueUploadInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for handling file picker operations and converting selected files
 * to upload inputs.
 */
@HiltViewModel
class MediaPickerViewModel @Inject constructor(
  @ApplicationContext private val context: Context
) : ViewModel() {

  /**
   * Convert selected video file to upload input.
   */
  fun createVideoUploadInput(
    uri: Uri,
    title: String? = null
  ): EnqueueUploadInput.Video? {
    return try {
      val file = getFileFromUri(uri) ?: return null

      val fileName = file.name
      val fileSize = file.length()

      EnqueueUploadInput.Video(
        title = title ?: fileName.substringBeforeLast('.'),
        filePath = file.absolutePath,
        fileName = fileName,
        fileSize = fileSize
      )
    } catch (e: Exception) {
      Timber.e(e, "Failed to create video upload input")
      null
    }
  }

  /**
   * Convert selected photo files to bundle upload input.
   */
  fun createPhotoBundleUploadInput(
    uris: List<Uri>,
    title: String? = null
  ): EnqueueUploadInput.PhotoBundle? {
    return try {
      val filePaths = uris.mapNotNull { uri ->
        getFileFromUri(uri)?.absolutePath
      }

      if (filePaths.isEmpty()) {
        return null
      }

      val now = System.currentTimeMillis()
      val defaultTitle = "Photo Bundle $now"

      EnqueueUploadInput.PhotoBundle(
        title = title ?: defaultTitle,
        photoPaths = filePaths,
        photoCount = filePaths.size
      )
    } catch (e: Exception) {
      Timber.e(e, "Failed to create photo bundle upload input")
      null
    }
  }

  /**
   * Get actual file from URI (handles both file:// and content://).
   */
  private fun getFileFromUri(uri: Uri): File? {
    return when {
      uri.scheme == "file" -> {
        uri.path?.let { File(it) }
      }
      uri.scheme == "content" -> {
        // Copy content to cache directory
        try {
          val inputStream = context.contentResolver.openInputStream(uri) ?: return null
          val fileName = getFileNameFromUri(uri) ?: "temp_${System.currentTimeMillis()}"
          val cacheFile = File(context.cacheDir, fileName)

          inputStream.use { input ->
            cacheFile.outputStream().use { output ->
              input.copyTo(output)
            }
          }

          cacheFile
        } catch (e: Exception) {
          Timber.e(e, "Failed to copy content URI to cache")
          null
        }
      }
      else -> null
    }
  }

  /**
   * Extract file name from URI.
   */
  private fun getFileNameFromUri(uri: Uri): String? {
    return when {
      uri.scheme == "content" -> {
        try {
          val cursor = context.contentResolver.query(uri, null, null, null, null)
          cursor?.use {
            if (it.moveToFirst()) {
              val displayNameIndex = it.getColumnIndex("_display_name")
              if (displayNameIndex != -1) {
                it.getString(displayNameIndex)
              } else {
                null
              }
            } else {
              null
            }
          }
        } catch (e: Exception) {
          Timber.e(e, "Failed to get file name from URI")
          null
        }
      }
      uri.scheme == "file" -> {
        uri.path?.substringAfterLast('/') ?: "file"
      }
      else -> null
    }
  }

  /**
   * Validate video file.
   */
  fun isValidVideoFile(file: File): Boolean {
    return try {
      val extension = file.extension.lowercase()
      val videoExtensions = listOf("mp4", "mkv", "mov", "webm", "avi", "flv", "wmv")

      extension in videoExtensions && file.length() > 0
    } catch (e: Exception) {
      Timber.e(e, "Failed to validate video file")
      false
    }
  }

  /**
   * Validate photo file.
   */
  fun isValidPhotoFile(file: File): Boolean {
    return try {
      val extension = file.extension.lowercase()
      val photoExtensions = listOf("jpg", "jpeg", "png", "webp")

      extension in photoExtensions && file.length() > 0
    } catch (e: Exception) {
      Timber.e(e, "Failed to validate photo file")
      false
    }
  }
}
