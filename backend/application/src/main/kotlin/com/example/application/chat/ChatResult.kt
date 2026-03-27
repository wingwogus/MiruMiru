package com.example.application.chat

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
        val messageId: Long,
        val roomId: Long,
    ) : ChatResult

    data class ReadMarked(
        val roomId: Long,
        val readerId: Long,
        val lastReadMessageId: Long,
        val unreadCount: Long,
    ) : ChatResult
}
