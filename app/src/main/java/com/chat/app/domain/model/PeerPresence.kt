package com.chat.app.domain.model

data class PeerPresence(
    val peerId: String,
    val isOnline: Boolean,
    val isSameWifi: Boolean = false,
    val wifiSsid: String? = null,
    val lastSeenAt: Long? = null,
    val lastHeartbeatAt: Long = System.currentTimeMillis()
)
