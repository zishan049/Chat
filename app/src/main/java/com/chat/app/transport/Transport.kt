package com.chat.app.transport

import com.chat.app.transport.protocol.Envelope
import kotlinx.coroutines.flow.Flow

interface Transport {

    val name: String

    suspend fun send(envelope: Envelope, targetIp: String? = null, targetPort: Int? = null): SendResult

    fun incoming(): Flow<Envelope>

    suspend fun start()

    suspend fun stop()

    fun isRunning(): Boolean
}
