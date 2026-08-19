package com.chat.app.utils

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Log
import com.chat.app.data.StorageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────────
//  Data model for RAM stats
// ─────────────────────────────────────────────────────────────────────────────

data class MemoryStats(
    val usedMb: Float = 0f,
    val maxMb: Float = 0f,
    val freeMb: Float = 0f,
    val bitmapCacheKb: Int = 0,
    val bitmapCacheMaxKb: Int = 0,
) {
    val usagePercent: Float
        get() = if (maxMb > 0f) (usedMb / maxMb) * 100f else 0f
}

// ─────────────────────────────────────────────────────────────────────────────
//  AppMemoryManager
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lightweight coroutine-based background memory manager.
 *
 * - LRU Bitmap Cache capped at 1/8 of max JVM heap; auto-evicts on pressure.
 * - Periodic Idle Sweep every 15 min: orphan media cleanup + WAL checkpoint.
 * - Temp File Purge: stale transfer chunk files older than 30 min.
 * - onTrimMemory response: MODERATE -> 50% evict; CRITICAL -> 100% evict.
 * - MemoryStats StateFlow for optional Settings UI.
 */
object AppMemoryManager {

    private const val TAG = "MemoryManager"
    private const val SWEEP_INTERVAL_MS  = 15L * 60L * 1000L   // 15 min
    private const val TEMP_MAX_AGE_MS    = 30L * 60L * 1000L   // 30 min

    // ── Coroutine scope ──────────────────────────────────────────────────────

    private val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sweepJob: Job? = null

    // ── Bitmap LRU cache ─────────────────────────────────────────────────────

    private val bitmapCacheMaxKb: Int by lazy {
        (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
    }

    val bitmapCache: LruCache<String, Bitmap> by lazy {
        object : LruCache<String, Bitmap>(bitmapCacheMaxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return try {
                    (value.allocationByteCount / 1024).coerceAtLeast(1)
                } catch (_: Exception) {
                    1
                }
            }
            override fun entryRemoved(evicted: Boolean, key: String, old: Bitmap, new: Bitmap?) {
                if (evicted) {
                    val sizeKb = try { (old.allocationByteCount / 1024).coerceAtLeast(1) } catch (_: Exception) { 1 }
                    com.chat.app.telemetry.AppTelemetry.logCacheEvent(
                        event = "EVICT",
                        key = key,
                        sizeKb = sizeKb,
                        currentTotalKb = size(),
                        maxKb = bitmapCacheMaxKb
                    )
                }
                if (evicted && !old.isRecycled) old.recycle()
            }
        }
    }

    // ── Memory stats flow ────────────────────────────────────────────────────

    private val _memoryStats = MutableStateFlow(MemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    // ── Public API ───────────────────────────────────────────────────────────

    fun startIdleSweep(storageManager: StorageManager) {
        sweepJob?.cancel()
        sweepJob = scope.launch {
            Log.i(TAG, "Idle sweep started (interval=${SWEEP_INTERVAL_MS / 60_000} min)")
            while (isActive) {
                delay(SWEEP_INTERVAL_MS)
                runIdleSweep(storageManager)
            }
        }
    }

    fun cancelIdleSweep() {
        sweepJob?.cancel()
        sweepJob = null
    }

    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "CRITICAL pressure (level=$level) -> evicting full bitmap cache")
                bitmapCache.evictAll()
                com.chat.app.telemetry.AppTelemetry.logCacheEvent("CLEAR", "all_entries", 0, 0, bitmapCacheMaxKb)
                scope.coroutineContext[Job]?.children?.forEach { child ->
                    if (child !== sweepJob) child.cancel()
                }
                refreshStats()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "MODERATE pressure (level=$level) -> trimming 50% of bitmap cache")
                trimCacheBy(50)
                com.chat.app.telemetry.AppTelemetry.logCacheEvent("TRIM", "50_percent", 0, bitmapCache.size(), bitmapCacheMaxKb)
                refreshStats()
            }
            else -> Unit
        }
    }

    fun cacheBitmap(key: String, bitmap: Bitmap): Boolean {
        val sizeKb = bitmap.byteCount / 1024
        if (sizeKb > bitmapCacheMaxKb / 2) return false
        bitmapCache.put(key, bitmap)
        com.chat.app.telemetry.AppTelemetry.logCacheEvent(
            event = "PUT",
            key = key,
            sizeKb = sizeKb,
            currentTotalKb = bitmapCache.size(),
            maxKb = bitmapCacheMaxKb
        )
        return true
    }

    fun getCachedBitmap(key: String): Bitmap? {
        val bmp = bitmapCache.get(key)
        com.chat.app.telemetry.AppTelemetry.logCacheEvent(
            event = if (bmp != null) "HIT" else "MISS",
            key = key,
            sizeKb = if (bmp != null) bmp.byteCount / 1024 else 0,
            currentTotalKb = bitmapCache.size(),
            maxKb = bitmapCacheMaxKb
        )
        return bmp
    }

    fun refreshStats() {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
        val max  = rt.maxMemory() / (1024f * 1024f)
        _memoryStats.value = MemoryStats(
            usedMb         = used,
            maxMb          = max,
            freeMb         = max - used,
            bitmapCacheKb  = bitmapCache.size(),
            bitmapCacheMaxKb = bitmapCacheMaxKb,
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun runIdleSweep(storageManager: StorageManager) {
        Log.d(TAG, "Idle sweep running…")

        // 1. Clean orphan media files
        try {
            val n = storageManager.cleanOrphanMedia()
            if (n > 0) Log.i(TAG, "Orphan sweep: deleted $n file(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Orphan sweep error: ${e.message}")
        }

        // 2. Purge stale temp transfer chunks from disk and memory
        try {
            val nDisk = purgeStaleTempFiles(storageManager)
            val nMem = GlobalP2PMessagingManager.purgeStaleTransferState()
            if (nDisk > 0 || nMem > 0) Log.i(TAG, "Temp purge: deleted $nDisk chunk(s) on disk, $nMem transfer session(s) from memory")
        } catch (e: Exception) {
            Log.e(TAG, "Temp purge error: ${e.message}")
        }

        // 3. WAL checkpoint
        try {
            storageManager.checkpointDatabase()
            Log.d(TAG, "WAL checkpoint done")
        } catch (e: Exception) {
            Log.e(TAG, "WAL checkpoint error: ${e.message}")
        }

        // 4. Refresh stats
        refreshStats()
        Log.d(TAG, "Sweep done — ${_memoryStats.value.usedMb.toInt()} MB / ${_memoryStats.value.maxMb.toInt()} MB")
    }

    private suspend fun purgeStaleTempFiles(storageManager: StorageManager): Int =
        withContext(Dispatchers.IO) {
            val dir = storageManager.getTempMediaDir() ?: return@withContext 0
            if (!dir.exists()) return@withContext 0
            val cutoff = System.currentTimeMillis() - TEMP_MAX_AGE_MS
            dir.walkTopDown().filter { it.isFile && it.lastModified() < cutoff }
               .count { it.delete() }
        }

    private fun trimCacheBy(percent: Int) {
        val target = (bitmapCache.size() * (100 - percent) / 100).coerceAtLeast(0)
        bitmapCache.trimToSize(target)
    }
}
