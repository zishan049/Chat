package com.chat.app.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import java.io.File

object AudioPlayerHelper {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: String? = null
    private var updateJob: Job? = null

    fun play(
        context: Context,
        messageId: String,
        mediaPathOrUri: String,
        onProgress: (currentMs: Int, totalMs: Int) -> Unit,
        onComplete: () -> Unit
    ) {
        stop()

        try {
            val player = MediaPlayer()
            val file = File(mediaPathOrUri)
            if (file.exists()) {
                player.setDataSource(file.absolutePath)
            } else {
                player.setDataSource(context, Uri.parse(mediaPathOrUri))
            }
            player.prepare()
            player.start()

            mediaPlayer = player
            currentPlayingId = messageId

            val totalDuration = player.duration.coerceAtLeast(1)

            updateJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive && player.isPlaying) {
                    onProgress(player.currentPosition, totalDuration)
                    delay(100L)
                }
            }

            player.setOnCompletionListener {
                stop()
                onComplete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
            onComplete()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentPlayingId = null
    }

    fun isPlaying(messageId: String): Boolean {
        return currentPlayingId == messageId && mediaPlayer?.isPlaying == true
    }

    fun getCurrentPlayingId(): String? = currentPlayingId
}
