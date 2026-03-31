package com.example.application.chat.event

import java.time.LocalDateTime

enum class ChatEventType {
    MESSAGE,
    READ,
    UNREAD_COUNT,
}

data class ChatEvent(
    val type: ChatEventType,
    val roomId: Long,
    val message: ChatMessageEvent? = null,
    val read: ChatReadEvent? = null,
    val unread: ChatUnreadCountEvent? = null,
)

data class ChatMessageEvent(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val createdAt: LocalDateTime?,
)

data class ChatReadEvent(
    val readerId: Long,
    val lastReadMessageId: Long,
)

data class ChatUnreadCountEvent(
    val memberId: Long,
    val unreadCount: Long,
)
