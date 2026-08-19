package com.chat.app.presence.data

import com.chat.app.domain.model.PeerPresence
import com.chat.app.presence.domain.PresenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepositoryImpl @Inject constructor() : PresenceRepository {

    private val _presenceMap = MutableStateFlow<Map<String, PeerPresence>>(emptyMap())

    override fun observePresenceMap(): Flow<Map<String, PeerPresence>> = _presenceMap.asStateFlow()

    override suspend fun getPresence(peerId: String): PeerPresence? = _presenceMap.value[peerId]

    override suspend fun updatePresence(
        peerId: String,
        isOnline: Boolean,
        isSameWifi: Boolean,
        wifiSsid: String?,
        timestamp: Long
    ) {
        _presenceMap.update { current ->
            val updated = current.toMutableMap()
            updated[peerId] = PeerPresence(
                peerId = peerId,
                isOnline = isOnline,
                isSameWifi = isSameWifi,
                wifiSsid = wifiSsid,
                lastSeenAt = if (!isOnline) timestamp else current[peerId]?.lastSeenAt,
                lastHeartbeatAt = timestamp
            )
            updated
        }
    }

    override suspend fun sweepStalePresence(timeoutMs: Long) {
        val now = System.currentTimeMillis()
        _presenceMap.update { current ->
            val updated = current.toMutableMap()
            for ((peerId, presence) in current) {
                if (presence.isOnline && (now - presence.lastHeartbeatAt) > timeoutMs) {
                    updated[peerId] = presence.copy(isOnline = false, lastSeenAt = now)
                }
            }
            updated
        }
    }
}
