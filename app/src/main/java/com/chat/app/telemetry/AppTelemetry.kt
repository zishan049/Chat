package com.chat.app.telemetry

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import com.chat.app.data.StorageManager
import com.chat.app.utils.AppMemoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 100% Real-Time Android App Telemetry Engine.
 *
 * Streams live runtime metrics and events for:
 * - Rendering: Frame durations, 60/120fps budget, jank frames, screen transitions.
 * - Memory: JVM Heap, Native Heap, Bitmap LRU Cache, onTrimMemory events, GC.
 * - Database: Room queries, execution times, mutations, table counts.
 * - Cache: LRU Bitmap cache hits, misses, puts, evictions.
 * - Storage: Disk breakdown, file I/O operations, orphan sweeps, WAL checkpoints.
 * - Network: P2P TCP sockets, packet traffic (Text, Chunks, Acks, Typing), WebRelay throughput.
 * - System: Device hardware, battery, threads, app lifecycle.
 */
object AppTelemetry {

    private const val TAG = "ChatTelemetry"
    private const val BRIDGE_HOST = "127.0.0.1"
    private const val BRIDGE_PORT = 8088

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isInitialized = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)

    // Event queue to ensure high performance without blocking main/worker threads
    private val eventChannel = Channel<JSONObject>(capacity = 1000)

    // Running metrics
    private var appContext: Context? = null
    private var activeScreenName: String = "AppLaunched"
    private var lastScreenTransitionTime: Long = System.currentTimeMillis()
    private val renderFrameCount = AtomicLong(0)
    private val jankFrameCount = AtomicLong(0)

    // Frame monitoring via Choreographer
    private var isFrameMonitoringActive = false
    private var lastFrameTimeNanos: Long = 0L

    // ─────────────────────────────────────────────────────────────────────────────
    //  Initialization
    // ─────────────────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (!isInitialized.compareAndSet(false, true)) return
        appContext = context.applicationContext

        Log.i(TAG, "Initializing Live App Telemetry Engine...")

        // Register Activity Lifecycle Callbacks if available
        if (context is Application) {
            registerActivityLifecycleCallbacks(context)
        } else if (context.applicationContext is Application) {
            registerActivityLifecycleCallbacks(context.applicationContext as Application)
        }

        // Start connection manager to the PC Bridge
        startSocketBroadcaster()

        // Start Periodic Telemetry Probe (every 1 second)
        startPeriodicProbes()

        // Start Choreographer Frame Latency Monitor
        startFrameRateMonitoring()

        // Emit initial system boot event
        emitSystemInfo()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Public Logging & Telemetry APIs
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Track Screen & Navigation transitions
     */
    fun logScreenTransition(screenName: String, extraDetails: Map<String, Any?> = emptyMap()) {
        val now = System.currentTimeMillis()
        val durationOnPreviousScreen = now - lastScreenTransitionTime
        val previousScreen = activeScreenName

        activeScreenName = screenName
        lastScreenTransitionTime = now

        val payload = JSONObject().apply {
            put("currentScreen", screenName)
            put("previousScreen", previousScreen)
            put("timeSpentOnPreviousScreenMs", durationOnPreviousScreen)
            extraDetails.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
        }

        emitEvent(
            category = "RENDER",
            level = "INFO",
            event = "SCREEN_NAVIGATION",
            details = payload,
            message = "Navigated to $screenName (spent ${durationOnPreviousScreen}ms on $previousScreen)"
        )
    }

    /**
     * Track Compose Recomposition or Screen render cycle
     */
    fun logComposeRender(screenName: String, durationMs: Long, recomposeCount: Int = 1) {
        val payload = JSONObject().apply {
            put("screen", screenName)
            put("renderDurationMs", durationMs)
            put("recomposeCount", recomposeCount)
            put("isSlowRender", durationMs > 16)
        }

        emitEvent(
            category = "RENDER",
            level = if (durationMs > 16) "WARN" else "DEBUG",
            event = "COMPOSE_RENDER",
            details = payload,
            message = "Rendered $screenName in ${durationMs}ms"
        )
    }

    /**
     * Track Database (Room / SQLite) operations
     */
    fun logDbOperation(
        operation: String, // SELECT, INSERT, UPDATE, DELETE, TRANSACTION
        table: String,
        durationMs: Long,
        rowsAffected: Int = 1,
        details: String = ""
    ) {
        val payload = JSONObject().apply {
            put("operation", operation)
            put("table", table)
            put("durationMs", durationMs)
            put("rowsAffected", rowsAffected)
            if (details.isNotEmpty()) put("details", details)
        }

        emitEvent(
            category = "DB",
            level = if (durationMs > 25) "WARN" else "INFO",
            event = "DB_QUERY",
            details = payload,
            message = "DB $operation on [$table] took ${durationMs}ms (rows: $rowsAffected)"
        )
    }

    /**
     * Track LRU Bitmap Cache events
     */
    fun logCacheEvent(
        event: String, // HIT, MISS, PUT, EVICT, CLEAR, TRIM
        key: String,
        sizeKb: Int = 0,
        currentTotalKb: Int = 0,
        maxKb: Int = 0
    ) {
        val payload = JSONObject().apply {
            put("action", event)
            put("key", key)
            put("sizeKb", sizeKb)
            put("currentTotalKb", currentTotalKb)
            put("maxKb", maxKb)
            if (maxKb > 0) {
                put("usagePercent", (currentTotalKb.toFloat() / maxKb.toFloat()) * 100f)
            }
        }

        emitEvent(
            category = "CACHE",
            level = if (event == "EVICT") "WARN" else "DEBUG",
            event = "BITMAP_CACHE_$event",
            details = payload,
            message = "Cache $event: $key ($sizeKb KB) -> Total: $currentTotalKb/$maxKb KB"
        )
    }

    /**
     * Track Storage & File System I/O operations
     */
    fun logStorageOperation(
        action: String, // SAVE, DELETE, CLEANUP_ORPHANS, CHECKPOINT_WAL, CLEAR_FOLDER
        filePath: String? = null,
        bytes: Long = 0L,
        count: Int = 1,
        durationMs: Long = 0L
    ) {
        val payload = JSONObject().apply {
            put("action", action)
            filePath?.let { put("filePath", it) }
            put("bytes", bytes)
            put("count", count)
            put("durationMs", durationMs)
        }

        emitEvent(
            category = "STORAGE",
            level = "INFO",
            event = "STORAGE_$action",
            details = payload,
            message = "Storage $action: ${filePath ?: "$count items"} (${bytes / 1024} KB) in ${durationMs}ms"
        )
    }

    /**
     * Track Network & P2P socket and WebRelay events
     */
    fun logNetworkTraffic(
        direction: String, // INBOUND, OUTBOUND
        protocol: String,  // P2P_TCP, WEB_RELAY, DISCOVERY
        packetType: String, // TEXT, MEDIA_CHUNK, TYPING, ACK, READ_RECEIPT, PROFILE
        peerAddress: String,
        sizeBytes: Long,
        chunkIndex: Int? = null,
        totalChunks: Int? = null,
        durationMs: Long = 0L
    ) {
        val payload = JSONObject().apply {
            put("direction", direction)
            put("protocol", protocol)
            put("packetType", packetType)
            put("peer", peerAddress)
            put("sizeBytes", sizeBytes)
            chunkIndex?.let { put("chunkIndex", it) }
            totalChunks?.let { put("totalChunks", it) }
            put("durationMs", durationMs)
        }

        val chunkInfo = if (totalChunks != null && totalChunks > 1) " [chunk ${(chunkIndex ?: 0) + 1}/$totalChunks]" else ""
        emitEvent(
            category = "NETWORK",
            level = "INFO",
            event = "NET_${direction}_$packetType",
            details = payload,
            message = "Net $direction [$protocol] $packetType$chunkInfo ($sizeBytes B) <-> $peerAddress"
        )
    }

    /**
     * Track Memory Pressure / onTrimMemory
     */
    fun logTrimMemory(level: Int, levelName: String, actionTaken: String) {
        val payload = JSONObject().apply {
            put("level", level)
            put("levelName", levelName)
            put("actionTaken", actionTaken)
            put("heapUsedMb", getJvmUsedMb())
            put("nativeHeapMb", getNativeAllocatedMb())
        }

        emitEvent(
            category = "MEMORY",
            level = "WARN",
            event = "MEMORY_TRIM_PRESSURE",
            details = payload,
            message = "⚠️ Memory Pressure ($levelName) -> $actionTaken"
        )
    }

    /**
     * Track Message Bubble Status (MBS) lifecycle transitions (SENDING, SENT, DELIVERED, READ, FAILED)
     */
    fun logMbsStatusChange(
        messageId: String,
        chatId: String,
        status: String,
        durationMs: Long = 0L,
        isMine: Boolean = true,
        details: Map<String, Any?> = emptyMap()
    ) {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("chatId", chatId)
            put("status", status)
            put("isMine", isMine)
            put("durationMs", durationMs)
            details.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
        }

        val icon = when (status) {
            "SENDING" -> "🕒"
            "SENT" -> "✓"
            "DELIVERED" -> "✓✓"
            "READ" -> "✓✓ (Seen)"
            "FAILED" -> "❌"
            else -> "•"
        }

        emitEvent(
            category = "MBS",
            level = if (status == "FAILED") "ERROR" else "INFO",
            event = "MBS_STATUS_$status",
            details = payload,
            message = "MBS $icon Msg ${messageId.take(8)}... status -> $status"
        )
    }

    /**
     * Track Online Status Badge (OSB) & Peer Presence heartbeats
     */
    fun logOsbPresence(
        peerId: String,
        peerName: String?,
        isOnline: Boolean,
        pingRttMs: Long? = null,
        lastSeen: Long? = null,
        action: String = "HEARTBEAT",
        isSameWifi: Boolean = false,
        wifiSsid: String? = null
    ) {
        val payload = JSONObject().apply {
            put("peerId", peerId)
            put("peerName", peerName ?: peerId)
            put("isOnline", isOnline)
            put("isSameWifi", isSameWifi)
            if (!wifiSsid.isNullOrBlank()) put("wifiSsid", wifiSsid)
            pingRttMs?.let { put("pingRttMs", it) }
            lastSeen?.let { put("lastSeen", it) }
            put("action", action)
        }

        val badge = if (isOnline) (if (isSameWifi) "📶 SAME_WIFI" else "🟢 ONLINE") else "⚪ OFFLINE"
        val rttStr = if (pingRttMs != null) " (RTT: ${pingRttMs}ms)" else ""

        emitEvent(
            category = "OSB",
            level = "INFO",
            event = "OSB_PRESENCE_${if (isOnline) "ONLINE" else "OFFLINE"}",
            details = payload,
            message = "OSB: ${peerName ?: peerId.take(8)} is $badge$rttStr"
        )
    }

    /**
     * Track MBS Acknowledgement receipts (Delivery ACK, Read Receipt, Status Probe)
     */
    fun logMbsAck(
        messageId: String,
        chatId: String,
        ackType: String, // DELIVERY_ACK, READ_RECEIPT, STATUS_PROBE
        latencyMs: Long? = null
    ) {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("chatId", chatId)
            put("ackType", ackType)
            latencyMs?.let { put("latencyMs", it) }
        }

        emitEvent(
            category = "MBS",
            level = "INFO",
            event = "MBS_ACK_$ackType",
            details = payload,
            message = "MBS ACK: Received $ackType for Msg ${messageId.take(8)} ${if (latencyMs != null) "(${latencyMs}ms)" else ""}"
        )
    }

    /**
     * Generic App Log
     */
    fun log(category: String, level: String, event: String, message: String, details: Map<String, Any?> = emptyMap()) {
        val payload = JSONObject().apply {
            details.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
        }
        emitEvent(category, level, event, payload, message)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Event Dispatcher & Dual Transport
    // ─────────────────────────────────────────────────────────────────────────────

    internal fun emitEvent(
        category: String,
        level: String,
        event: String,
        details: JSONObject,
        message: String
    ) {
        val timestamp = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("timestamp", timestamp)
            put("category", category)
            put("level", level)
            put("event", event)
            put("message", message)
            put("activeScreen", activeScreenName)
            put("details", details)
        }

        // 1. Send to fast async channel for TCP socket streaming
        eventChannel.trySend(json)

        // 2. Structured Logcat for instant ADB logcat capture fallback
        val logLine = "[ChatTelemetry] ${json.toString()}"
        when (level.uppercase()) {
            "ERROR" -> Log.e(TAG, logLine)
            "WARN"  -> Log.w(TAG, logLine)
            "DEBUG" -> Log.d(TAG, logLine)
            else    -> Log.i(TAG, logLine)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Background Socket Broadcaster
    // ─────────────────────────────────────────────────────────────────────────────

    private fun startSocketBroadcaster() {
        scope.launch {
            while (isActive) {
                var socket: Socket? = null
                var writer: PrintWriter? = null
                var reader: BufferedReader? = null

                try {
                    Log.d(TAG, "Attempting connection to Telemetry Bridge at $BRIDGE_HOST:$BRIDGE_PORT...")
                    socket = Socket(BRIDGE_HOST, BRIDGE_PORT).apply {
                        tcpNoDelay = true
                        soTimeout = 0
                    }
                    writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
                    reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

                    isConnected.set(true)
                    Log.i(TAG, "✔ Connected to Live Telemetry Bridge on PC!")

                    // Send Handshake
                    val handshake = JSONObject().apply {
                        put("type", "HANDSHAKE")
                        put("source", "ANDROID_APP")
                        put("device", android.os.Build.MODEL)
                        put("manufacturer", android.os.Build.MANUFACTURER)
                        put("androidVersion", android.os.Build.VERSION.RELEASE)
                        put("apiLevel", android.os.Build.VERSION.SDK_INT)
                        put("pid", android.os.Process.myPid())
                        put("timestamp", System.currentTimeMillis())
                    }
                    writer.println(handshake.toString())

                    // Launch incoming command listener from Bridge
                    val readerJob = launch {
                        try {
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                handleIncomingBridgeCommand(line)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Reader loop ended: ${e.message}")
                        }
                    }

                    // Main event transmission loop
                    while (isActive) {
                        val event = eventChannel.receive()
                        writer.println(event.toString())
                        if (writer.checkError()) {
                            throw Exception("Socket write error")
                        }
                    }

                    readerJob.cancel()
                } catch (e: Exception) {
                    isConnected.set(false)
                    // Wait before reconnecting to save CPU
                    delay(2000)
                } finally {
                    try { writer?.close() } catch (_: Exception) {}
                    try { reader?.close() } catch (_: Exception) {}
                    try { socket?.close() } catch (_: Exception) {}
                    isConnected.set(false)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Remote Interactive Commands from React / Bridge
    // ─────────────────────────────────────────────────────────────────────────────

    private fun handleIncomingBridgeCommand(commandJsonStr: String) {
        try {
            val json = JSONObject(commandJsonStr)
            val command = json.optString("command", "")
            Log.i(TAG, "Received remote command: $command")

            when (command) {
                "FORCE_GC" -> {
                    val beforeHeap = getJvmUsedMb()
                    System.gc()
                    Runtime.getRuntime().runFinalization()
                    val afterHeap = getJvmUsedMb()
                    emitEvent(
                        category = "MEMORY",
                        level = "INFO",
                        event = "FORCED_GC_EXECUTED",
                        details = JSONObject().apply {
                            put("beforeHeapMb", beforeHeap)
                            put("afterHeapMb", afterHeap)
                            put("reclaimedMb", beforeHeap - afterHeap)
                        },
                        message = "Forced Garbage Collection executed: reclaimed ${String.format("%.2f", beforeHeap - afterHeap)} MB"
                    )
                }
                "TRIM_CACHE" -> {
                    AppMemoryManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
                    emitEvent(
                        category = "CACHE",
                        level = "INFO",
                        event = "TRIM_CACHE_EXECUTED",
                        details = JSONObject(),
                        message = "Bitmap cache cleared via remote trigger"
                    )
                }
                "REFRESH_STATS" -> {
                    emitPeriodicMetrics()
                }
                "START_STRESS_TEST" -> {
                    val ctx = appContext
                    val iterations = when {
                        json.has("iterations") -> json.optInt("iterations", 300)
                        json.has("payload") -> json.optJSONObject("payload")?.optInt("iterations", 300) ?: 300
                        else -> 300
                    }
                    Log.i(TAG, "Starting Stress Test with $iterations iterations (appContext=$ctx)")
                    if (ctx != null) {
                        AppDiagnosticsTestRunner.runStressTest(ctx, iterations)
                    } else {
                        Log.e(TAG, "Cannot start stress test: appContext is null")
                    }
                }
                "CANCEL_STRESS_TEST" -> {
                    Log.i(TAG, "Cancelling Stress/Security Test")
                    AppDiagnosticsTestRunner.cancelTest()
                }
                "START_SECURITY_TEST" -> {
                    val ctx = appContext
                    Log.i(TAG, "Starting Security Audit (appContext=$ctx)")
                    if (ctx != null) {
                        AppDiagnosticsTestRunner.runSecurityAudit(ctx)
                    } else {
                        Log.e(TAG, "Cannot start security audit: appContext is null")
                    }
                }
                "DISMISS_TEST_OVERLAY" -> {
                    AppDiagnosticsTestRunner.dismissOverlay()
                }
                "HIDE_TEST_OVERLAY" -> {
                    AppDiagnosticsTestRunner.hideOverlay()
                }
                "SHOW_TEST_OVERLAY" -> {
                    AppDiagnosticsTestRunner.showOverlay()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Periodic Metrics Probe (Runs every 1s)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun startPeriodicProbes() {
        scope.launch {
            while (isActive) {
                delay(1000)
                try {
                    emitPeriodicMetrics()
                } catch (e: Exception) {
                    Log.d(TAG, "Metrics probe error: ${e.message}")
                }
            }
        }
    }

    private fun emitPeriodicMetrics() {
        val rt = Runtime.getRuntime()
        val totalMem = rt.totalMemory()
        val freeMem = rt.freeMemory()
        val maxMem = rt.maxMemory()
        val usedMem = totalMem - freeMem

        val jvmUsedMb = usedMem / (1024f * 1024f)
        val jvmMaxMb = maxMem / (1024f * 1024f)
        val jvmFreeMb = (maxMem - usedMem) / (1024f * 1024f)

        val nativeAllocatedMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
        val nativeTotalMb = Debug.getNativeHeapSize() / (1024f * 1024f)

        val bitmapCacheSizeKb = AppMemoryManager.bitmapCache.size()
        val bitmapCacheMaxKb = (maxMem / 1024).toInt() / 8
        val bitmapHitCount = AppMemoryManager.bitmapCache.hitCount()
        val bitmapMissCount = AppMemoryManager.bitmapCache.missCount()
        val bitmapEvictionCount = AppMemoryManager.bitmapCache.evictionCount()

        val threadCount = Thread.activeCount()

        val metrics = JSONObject().apply {
            put("jvmUsedMb", jvmUsedMb)
            put("jvmMaxMb", jvmMaxMb)
            put("jvmFreeMb", jvmFreeMb)
            put("jvmUsagePercent", (jvmUsedMb / jvmMaxMb) * 100f)
            put("nativeAllocatedMb", nativeAllocatedMb)
            put("nativeTotalMb", nativeTotalMb)
            put("bitmapCacheSizeKb", bitmapCacheSizeKb)
            put("bitmapCacheMaxKb", bitmapCacheMaxKb)
            put("bitmapHitCount", bitmapHitCount)
            put("bitmapMissCount", bitmapMissCount)
            put("bitmapEvictionCount", bitmapEvictionCount)
            put("threadCount", threadCount)
            put("activeScreen", activeScreenName)
            put("totalFramesRendered", renderFrameCount.get())
            put("totalJankFrames", jankFrameCount.get())
        }

        emitEvent(
            category = "METRICS_TICK",
            level = "DEBUG",
            event = "PROBE_TICK",
            details = metrics,
            message = "Memory: ${String.format("%.1f", jvmUsedMb)}/${String.format("%.1f", jvmMaxMb)}MB | Cache: $bitmapCacheSizeKb KB | Screen: $activeScreenName"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Choreographer Frame Render Latency Monitor
    // ─────────────────────────────────────────────────────────────────────────────

    private fun startFrameRateMonitoring() {
        mainHandler.post {
            try {
                val choreographer = Choreographer.getInstance()
                val frameCallback = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (lastFrameTimeNanos > 0L) {
                            val frameDurationNanos = frameTimeNanos - lastFrameTimeNanos
                            val frameDurationMs = frameDurationNanos / 1_000_000f

                            renderFrameCount.incrementAndGet()

                            // Display refresh interval: 120Hz = 8.3ms, 60Hz = 16.6ms, 30Hz LTPO = 33.3ms.
                            // True UI Jank is a frame hitch exceeding normal 30Hz/60Hz VSYNC (>34.0ms).
                            if (frameDurationMs > 34.0f) {
                                jankFrameCount.incrementAndGet()
                                emitEvent(
                                    category = "RENDER",
                                    level = "WARN",
                                    event = "JANK_FRAME_DETECTED",
                                    details = JSONObject().apply {
                                        put("frameDurationMs", frameDurationMs)
                                        put("screen", activeScreenName)
                                        put("targetDurationMs", 16.66f)
                                    },
                                    message = "⚠️ UI Jank Frame: ${String.format("%.1f", frameDurationMs)}ms on screen [$activeScreenName]"
                                )
                            }
                        }
                        lastFrameTimeNanos = frameTimeNanos
                        choreographer.postFrameCallback(this)
                    }
                }
                choreographer.postFrameCallback(frameCallback)
                isFrameMonitoringActive = true
            } catch (e: Exception) {
                Log.w(TAG, "Frame monitoring unavailable: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  System & Activity Lifecycle
    // ─────────────────────────────────────────────────────────────────────────────

    private fun registerActivityLifecycleCallbacks(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                emitActivityLifecycle(activity.javaClass.simpleName, "CREATED")
            }
            override fun onActivityStarted(activity: Activity) {
                emitActivityLifecycle(activity.javaClass.simpleName, "STARTED")
            }
            override fun onActivityResumed(activity: Activity) {
                emitActivityLifecycle(activity.javaClass.simpleName, "RESUMED (Foreground)")
            }
            override fun onActivityPaused(activity: Activity) {
                emitActivityLifecycle(activity.javaClass.simpleName, "PAUSED")
            }
            override fun onActivityStopped(activity: Activity) {
                emitActivityLifecycle(activity.javaClass.simpleName, "STOPPED (Background)")
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                emitActivityLifecycle(activity.javaClass.simpleName, "DESTROYED")
            }
        })
    }

    private fun emitActivityLifecycle(activityName: String, state: String) {
        emitEvent(
            category = "SYSTEM",
            level = "INFO",
            event = "ACTIVITY_LIFECYCLE",
            details = JSONObject().apply {
                put("activity", activityName)
                put("state", state)
            },
            message = "Activity $activityName -> $state"
        )
    }

    private fun emitSystemInfo() {
        val details = JSONObject().apply {
            put("deviceModel", android.os.Build.MODEL)
            put("manufacturer", android.os.Build.MANUFACTURER)
            put("brand", android.os.Build.BRAND)
            put("androidRelease", android.os.Build.VERSION.RELEASE)
            put("sdkInt", android.os.Build.VERSION.SDK_INT)
            put("hardware", android.os.Build.HARDWARE)
            put("supportedAbis", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
            put("availableProcessors", Runtime.getRuntime().availableProcessors())
            put("maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024))
            put("pid", android.os.Process.myPid())
        }

        emitEvent(
            category = "SYSTEM",
            level = "INFO",
            event = "DEVICE_HARDWARE_SPECS",
            details = details,
            message = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})"
        )
    }

    internal fun getJvmUsedMb(): Float {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
    }

    internal fun getNativeAllocatedMb(): Float {
        return Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
    }
}
