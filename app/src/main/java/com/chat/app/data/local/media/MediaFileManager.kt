package com.chat.app.data.local.media

import android.content.Context
import android.net.Uri
import com.chat.app.core.logging.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MediaStorageBreakdown(
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val filesBytes: Long = 0L,
    val totalBytes: Long = 0L
)

data class MediaItem(
    val file: File,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: MediaCategory
)

enum class MediaCategory(val subfolder: String, val label: String) {
    ALL("", "All"),
    IMAGES("images", "Images"),
    VIDEOS("videos", "Videos"),
    AUDIO("audio", "Voice & Audio"),
    FILES("files", "Files & Docs")
}

@Singleton
class MediaFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MediaFileManager"
        private const val BASE_MEDIA_DIR = "chat_media"
    }

    private fun getMediaDirectory(subfolder: String): File {
        val dir = File(context.filesDir, "$BASE_MEDIA_DIR/$subfolder")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Copies a picked content Uri into app private internal storage.
     */
    suspend fun saveMediaFromUri(
        uri: Uri,
        subfolder: String = "files",
        originalFileName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val extension = originalFileName?.substringAfterLast('.', "")?.let { if (it.isNotBlank()) ".$it" else "" } ?: ""
            val targetFile = File(getMediaDirectory(subfolder), "${UUID.randomUUID()}$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            AppLog.i(TAG, "Saved media file: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            targetFile.absolutePath
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save media from URI", e)
            null
        }
    }

    /**
     * Writes decrypted chunk directly to disk at specific byte offset (zero-memory streaming).
     */
    suspend fun writeChunkDirectToDisk(
        tempFileName: String,
        offset: Long,
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(getMediaDirectory("temp"), tempFileName)
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data)
            }
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed writing chunk to disk at offset $offset", e)
            false
        }
    }

    /**
     * Finalizes a completed multi-chunk download by moving it to the permanent media directory.
     */
    suspend fun finalizeChunkedFile(
        tempFileName: String,
        subfolder: String,
        finalFileName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(getMediaDirectory("temp"), tempFileName)
            if (!tempFile.exists()) return@withContext null

            val extension = finalFileName.substringAfterLast('.', "").let { if (it.isNotBlank()) ".$it" else "" }
            val finalFile = File(getMediaDirectory(subfolder), "${UUID.randomUUID()}_$finalFileName")

            if (tempFile.renameTo(finalFile)) {
                AppLog.i(TAG, "Finalized media file: ${finalFile.absolutePath}")
                finalFile.absolutePath
            } else {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
                finalFile.absolutePath
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to finalize chunked file", e)
            null
        }
    }

    /**
     * Computes disk storage usage breakdown.
     */
    suspend fun getStorageBreakdown(): MediaStorageBreakdown = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, BASE_MEDIA_DIR)
        if (!root.exists()) return@withContext MediaStorageBreakdown()

        fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()

        val images = dirSize(File(root, "images"))
        val videos = dirSize(File(root, "videos"))
        val audio = dirSize(File(root, "audio"))
        val files = dirSize(File(root, "files"))

        MediaStorageBreakdown(
            imagesBytes = images,
            videosBytes = videos,
            audioBytes = audio,
            filesBytes = files,
            totalBytes = images + videos + audio + files
        )
    }

    /**
     * Returns all media files sorted by last modified descending.
     */
    suspend fun getAllMediaItems(category: MediaCategory = MediaCategory.ALL): List<MediaItem> = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, BASE_MEDIA_DIR)
        if (!root.exists()) return@withContext emptyList()

        val subfolders = when (category) {
            MediaCategory.ALL -> listOf("images", "videos", "audio", "files")
            MediaCategory.IMAGES -> listOf("images")
            MediaCategory.VIDEOS -> listOf("videos")
            MediaCategory.AUDIO -> listOf("audio")
            MediaCategory.FILES -> listOf("files")
        }

        val items = mutableListOf<MediaItem>()
        for (folderName in subfolders) {
            val dir = File(root, folderName)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (file.isFile && file.length() > 0) {
                        val cat = when (folderName) {
                            "images" -> MediaCategory.IMAGES
                            "videos" -> MediaCategory.VIDEOS
                            "audio" -> MediaCategory.AUDIO
                            else -> MediaCategory.FILES
                        }
                        items.add(
                            MediaItem(
                                file = file,
                                name = file.name,
                                path = file.absolutePath,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                                category = cat
                            )
                        )
                    }
                }
            }
        }
        items.sortedByDescending { it.lastModified }
    }

    /**
     * Deletes a specific media file from disk.
     */
    suspend fun deleteMediaFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete media file: $path", e)
            false
        }
    }

    /**
     * Clears media files by category or all.
     */
    suspend fun clearMedia(category: MediaCategory = MediaCategory.ALL): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = File(context.filesDir, BASE_MEDIA_DIR)
            val subfolders = when (category) {
                MediaCategory.ALL -> listOf("images", "videos", "audio", "files")
                MediaCategory.IMAGES -> listOf("images")
                MediaCategory.VIDEOS -> listOf("videos")
                MediaCategory.AUDIO -> listOf("audio")
                MediaCategory.FILES -> listOf("files")
            }
            for (folderName in subfolders) {
                val dir = File(root, folderName)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            }
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to clear media: $category", e)
            false
        }
    }

    /**
     * Reads a local avatar image, scales to thumbnail (max 96x96), and returns a compact Base64 string.
     */
    suspend fun getAvatarThumbnailBase64(avatarUri: String?): String? = withContext(Dispatchers.IO) {
        if (avatarUri.isNullOrBlank()) return@withContext null
        try {
            val file = File(avatarUri)
            val inputStream = if (file.exists()) {
                file.inputStream()
            } else {
                context.contentResolver.openInputStream(Uri.parse(avatarUri))
            } ?: return@withContext null

            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return@withContext null

            val maxDim = 96
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height, 1f)
            val scaledBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                originalBitmap
            }

            val baos = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to create avatar thumbnail base64", e)
            null
        }
    }

    /**
     * Saves received avatar Base64 string to local storage and returns the local file path.
     */
    suspend fun saveAvatarFromBase64(contactId: String, base64Data: String): String? = withContext(Dispatchers.IO) {
        if (base64Data.isBlank()) return@withContext null
        try {
            val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            val targetFile = File(getMediaDirectory("avatars"), "avatar_${contactId}.jpg")
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            AppLog.i(TAG, "Saved contact avatar from Base64: ${targetFile.absolutePath} (${bytes.size} bytes)")
            targetFile.absolutePath
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to decode/save avatar from Base64 for contact $contactId", e)
            null
        }
    }
}
