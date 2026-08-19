package com.chat.app.core.logging

import android.util.Log

/**
 * Structured logging abstraction.
 *
 * Rules:
 * - NEVER log plaintext message content
 * - NEVER log private keys, session keys, or secrets
 * - NEVER log decrypted attachments
 * - DO log operation names, IDs (truncated), durations, error types
 * - DO log state transitions with enough context for debugging
 */
object AppLog {

    private const val TAG = "ChatApp"

    fun d(tag: String, message: String) {
        Log.d("$TAG/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$TAG/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG/$tag", message, throwable)
        } else {
            Log.w("$TAG/$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG/$tag", message, throwable)
        } else {
            Log.e("$TAG/$tag", message)
        }
    }

    /**
     * Log with a truncated ID for privacy — shows enough for debugging (first 8 chars).
     */
    fun truncatedId(id: String): String = if (id.length > 8) id.take(8) + "…" else id
}
