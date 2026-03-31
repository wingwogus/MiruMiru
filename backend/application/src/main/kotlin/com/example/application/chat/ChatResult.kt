package com.example.application.chat

import java.time.LocalDateTime

sealed interface ChatResult {
    data class RoomCreated(
        val roomId: Long,
        val postId: Long,
        val member1Id: Long,
        val member2Id: Long,
        val isAnon1: Boolean,
        val isAnon2: Boolean,
        val created: Boolean,
    ) : ChatResult

    data class MessageSent(
        val id: Long,
        val roomId: Long,
        val senderId: Long,
        val content: String,
        val createdAt: LocalDateTime?,
    ) : ChatResult

    data class ReadMarked(
        val roomId: Long,
        val readerId: Long,
        val lastReadMessageId: Long,
        val unreadCount: Long,
    ) : ChatResult
}
