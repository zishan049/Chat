package com.chat.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class MediaStorageBreakdown(
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val filesBytes: Long = 0L,
    val avatarsBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val orphanCount: Int = 0,
    val orphanBytes: Long = 0L,
)

/**
 * Copies any URI (picked from Gallery, Camera, or file picker) into the app's
 * private internal storage so it remains accessible regardless of permission changes
 * and is viewable fully offline even after the original source file is removed.
 */
object LocalMediaManager {

    private const val MEDIA_DIR = "chat_media"

    fun getMediaDir(context: Context, subfolder: String): File {
        val dir = File(context.filesDir, "$MEDIA_DIR/$subfolder")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Saves a [Uri] to internal storage and returns the local file path as a String.
     * @param subfolder One of "images", "videos", "files", "audio", "avatars"
     * @param originalFileName Optional original filename to preserve extension.
     */
    suspend fun saveMedia(
        context: Context,
        uri: Uri,
        subfolder: String = "files",
        originalFileName: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val uriStr = uri.toString()
            if (uriStr.startsWith("/") || uri.scheme == "file") {
                val filePath = if (uriStr.startsWith("/")) uriStr else uri.path ?: ""
                val directFile = File(filePath)
                if (directFile.exists() && directFile.parentFile?.absolutePath == getMediaDir(context, subfolder).absolutePath) {
                    return@withContext directFile.absolutePath
                }
            }

            val ext = originalFileName
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?: getMimeExtension(context, uri).ifBlank { if (subfolder == "avatars" || subfolder == "images") "jpg" else "dat" }

            val fileName = "${UUID.randomUUID()}${if (ext.isNotBlank()) ".$ext" else ""}"
            val targetFile = File(getMediaDir(context, subfolder), fileName)

            val inputStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            } ?: run {
                val p = uri.path ?: uri.toString()
                val f = File(p)
                if (f.exists()) java.io.FileInputStream(f) else null
            }

            if (inputStream != null) {
                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMimeExtension(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: return ""
        return when {
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("png") -> "png"
            mime.contains("gif") -> "gif"
            mime.contains("webp") -> "webp"
            mime.contains("mp4") -> "mp4"
            mime.contains("mkv") -> "mkv"
            mime.contains("webm") -> "webm"
            mime.contains("pdf") -> "pdf"
            mime.contains("mp3") || mime.contains("mpeg") -> "mp3"
            mime.contains("ogg") -> "ogg"
            mime.contains("wav") -> "wav"
            else -> ""
        }
    }

    private fun getSubfolderSize(context: Context, subfolder: String): Long {
        val dir = File(context.filesDir, "$MEDIA_DIR/$subfolder")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Returns detailed storage usage broken down by category. */
    suspend fun getStorageBreakdown(context: Context, activePaths: Set<String>): MediaStorageBreakdown = withContext(Dispatchers.IO) {
        val images = getSubfolderSize(context, "images")
        val videos = getSubfolderSize(context, "videos")
        val audio = getSubfolderSize(context, "audio")
        val files = getSubfolderSize(context, "files")
        val avatars = getSubfolderSize(context, "avatars")
        val total = images + videos + audio + files + avatars

        // Calculate orphans
        val rootDir = File(context.filesDir, MEDIA_DIR)
        var orphanCount = 0
        var orphanBytes = 0L

        if (rootDir.exists()) {
            rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
                if (!activePaths.contains(file.absolutePath)) {
                    orphanCount++
                    orphanBytes += file.length()
                }
            }
        }

        MediaStorageBreakdown(
            imagesBytes = images,
            videosBytes = videos,
            audioBytes = audio,
            filesBytes = files,
            avatarsBytes = avatars,
            totalBytes = total,
            orphanCount = orphanCount,
            orphanBytes = orphanBytes,
        )
    }

    /** Returns the total size in bytes of all stored media. */
    suspend fun getTotalStorageUsed(context: Context): Long = withContext(Dispatchers.IO) {
        File(context.filesDir, MEDIA_DIR)
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /** Deletes any media file in storage that is not in the set of active database media paths. */
    suspend fun cleanOrphanMedia(context: Context, activePaths: Set<String>): Int = withContext(Dispatchers.IO) {
        val rootDir = File(context.filesDir, MEDIA_DIR)
        if (!rootDir.exists()) return@withContext 0

        var deletedCount = 0
        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (!activePaths.contains(file.absolutePath)) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }

    /** Clears all files in a specific subfolder (e.g. "images", "videos", "audio", "files", "avatars", "thumbnails", "temp"). */
    suspend fun clearSubfolder(context: Context, subfolder: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "$MEDIA_DIR/$subfolder")
        if (!dir.exists()) return@withContext true
        dir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
        true
    }

    /** Deletes a single media file by its absolute path. */
    suspend fun deleteFile(path: String?): Boolean = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext false
        val file = File(path)
        if (file.exists()) file.delete() else false
    }

    /** Checks if the device has enough usable storage space for a file write. */
    fun hasAvailableDiskSpace(context: Context, requiredBytes: Long): Boolean {
        val usable = context.filesDir.usableSpace
        // Reserve at least 15 MB cushion
        return usable > (requiredBytes + 15L * 1024L * 1024L)
    }

    /**
     * Streams binary chunk directly into a temporary file at a specific offset.
     * Prevents holding multi-megabyte files in JVM memory.
     */
    suspend fun writeChunkToTempFile(
        context: Context,
        tempFileName: String,
        offset: Long,
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = getMediaDir(context, "temp")
            val targetFile = File(tempDir, tempFileName)
            java.io.RandomAccessFile(targetFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Atomically moves an assembled temporary file to its final destination subfolder.
     */
    suspend fun moveTempToFinal(
        context: Context,
        tempFileName: String,
        subfolder: String,
        originalFileName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(getMediaDir(context, "temp"), tempFileName)
            if (!tempFile.exists()) return@withContext null

            val ext = originalFileName
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?: "bin"

            val finalFileName = "${UUID.randomUUID()}.$ext"
            val destinationDir = getMediaDir(context, subfolder)
            val destinationFile = File(destinationDir, finalFileName)

            if (tempFile.renameTo(destinationFile)) {
                destinationFile.absolutePath
            } else {
                // Fallback copy if rename across mount fails
                tempFile.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
                destinationFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates and stores a lightweight downscaled WebP thumbnail for fast chat rendering.
     */
    suspend fun generateThumbnail(
        context: Context,
        sourceFilePath: String,
        maxDimension: Int = 300
    ): String? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return@withContext null

            // 1. Decode bounds only
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return@withContext null

            // 2. Calculate sample size
            var sampleSize = 1
            while ((width / sampleSize) > maxDimension || (height / sampleSize) > maxDimension) {
                sampleSize *= 2
            }

            // 3. Decode scaled bitmap
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return@withContext null

            // 4. Save to thumbnails directory
            val thumbDir = getMediaDir(context, "thumbnails")
            val thumbFile = File(thumbDir, "thumb_${UUID.randomUUID()}.webp")

            FileOutputStream(thumbFile).use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 75, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 75, out)
                }
            }
            bitmap.recycle()

            thumbFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
