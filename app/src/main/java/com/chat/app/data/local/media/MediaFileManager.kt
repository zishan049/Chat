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
}
