package com.chat.app.presence.domain

import com.chat.app.domain.model.PeerPresence
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {

    fun observePresenceMap(): Flow<Map<String, PeerPresence>>

    suspend fun getPresence(peerId: String): PeerPresence?

    suspend fun updatePresence(
        peerId: String,
        isOnline: Boolean,
        isSameWifi: Boolean = false,
        wifiSsid: String? = null,
        timestamp: Long = System.currentTimeMillis()
    )

    suspend fun sweepStalePresence(timeoutMs: Long = 30000L)
}
