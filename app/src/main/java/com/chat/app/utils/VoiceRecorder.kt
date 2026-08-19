package com.chat.app.utils

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    var isRecording: Boolean = false
        private set
    var isPaused: Boolean = false
        private set

    fun start(): Boolean {
        return try {
            cancel() // Clean up any previous session

            val file = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.m4a")
            currentFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            isRecording = true
            isPaused = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cancel()
            false
        }
    }

    fun pause(): Boolean {
        if (!isRecording || isPaused) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder?.pause()
                isPaused = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun resume(): Boolean {
        if (!isRecording || !isPaused) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder?.resume()
                isPaused = false
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getMaxAmplitude(): Int {
        if (!isRecording || isPaused) return 0
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun stopAndGetUri(): Pair<Uri, File>? {
        if (!isRecording) return null
        return try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
            recorder = null
            isRecording = false
            isPaused = false

            val file = currentFile
            if (file != null && file.exists() && file.length() > 0) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                Pair(uri, file)
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cancel()
            null
        }
    }

    fun cancel() {
        try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
        isPaused = false
        try {
            currentFile?.delete()
        } catch (_: Exception) {}
        currentFile = null
    }
}
