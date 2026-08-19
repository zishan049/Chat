package com.chat.app.pairing.domain

import com.chat.app.core.common.Result
import com.chat.app.data.local.room.dao.ConversationDao
import com.chat.app.data.local.room.entity.ConversationEntity
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.ContactRepository
import javax.inject.Inject

class AcceptContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val conversationDao: ConversationDao
) {

    suspend operator fun invoke(contact: Contact): Result<Unit> {
        val saveResult = contactRepository.saveContact(contact)
        if (saveResult is Result.Failure) {
            return saveResult
        }

        // Initialize conversation row if it does not already exist
        val existingConv = conversationDao.getById(contact.id)
        if (existingConv == null) {
            conversationDao.insert(
                ConversationEntity(
                    id = contact.id,
                    contactId = contact.id,
                    lastMessageSnippet = "Paired on ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                    lastMessageAt = System.currentTimeMillis(),
                    unreadCount = 0,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return Result.Success(Unit)
    }
}
