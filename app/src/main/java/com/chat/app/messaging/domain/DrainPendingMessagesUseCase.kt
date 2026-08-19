package com.chat.app.messaging.domain

import com.chat.app.core.logging.AppLog
import com.chat.app.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrainPendingMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val transportDispatcher: TransportDispatcher
) {

    companion object {
        private const val TAG = "DrainQueue"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend operator fun invoke() {
        val pending = messageRepository.getPendingOutgoingMessages()
        if (pending.isEmpty()) return

        AppLog.i(TAG, "Found ${pending.size} pending/queued outgoing messages to drain")
        for (msg in pending) {
            scope.launch {
                transportDispatcher.dispatchMessage(msg)
            }
        }
    }
}
