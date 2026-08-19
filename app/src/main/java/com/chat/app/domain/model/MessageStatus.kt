package com.chat.app.domain.model

enum class MessageStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED;

    val isPending: Boolean get() = this == QUEUED || this == SENDING || this == FAILED
    val isFinal: Boolean get() = this == READ
}
