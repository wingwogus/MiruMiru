package com.example.application.chat

import com.example.domain.chat.ChatMessageSummary

sealed interface ChatResult {

    data class Rooms(
        val rooms: List<RoomSummary>,
    ) : ChatResult

    data class RoomSummary(
        val roomId: Long,
        val postId: Long,
        val postTitle: String,
        val otherMemberId: Long,
        val lastMessageId: Long?,
        val lastMessageContent: String?,
        val lastMessageCreatedAt: java.time.LocalDateTime?,
        val unreadCount: Long,
        val myLastReadMessageId: Long?,
        val otherLastReadMessageId: Long?,
        val isAnonMe: Boolean,
        val isAnonOther: Boolean,
    )

    data class RoomCreated(
        val roomId: Long,
        val postId: Long,
        val member1Id: Long,
        val member2Id: Long,
        val isAnon1: Boolean,
        val isAnon2: Boolean,
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

    data class Messages(
        val roomId: Long,
        val messages: List<ChatMessageSummary>,
        val requesterLastReadMessageId: Long?,
        val otherLastReadMessageId: Long?,
        val nextBeforeMessageId: Long?,
    ) : ChatResult
}
