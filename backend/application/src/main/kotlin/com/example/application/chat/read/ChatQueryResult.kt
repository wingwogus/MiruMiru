package com.example.application.chat.read

import java.time.LocalDateTime

class ChatQueryResult {
    data class RoomSummary(
        val roomId: Long,
        val postId: Long,
        val postTitle: String,
        val otherMemberId: Long,
        val lastMessageId: Long?,
        val lastMessageContent: String?,
        val lastMessageCreatedAt: LocalDateTime?,
        val unreadCount: Long,
        val myLastReadMessageId: Long?,
        val otherLastReadMessageId: Long?,
        val isAnonMe: Boolean,
        val isAnonOther: Boolean,
    )

    data class Rooms(
        val rooms: List<RoomSummary>,
    )

    data class MessageSummary(
        val id: Long,
        val roomId: Long,
        val senderId: Long,
        val content: String,
        val createdAt: LocalDateTime?,
    )

    data class Messages(
        val roomId: Long,
        val messages: List<MessageSummary>,
        val requesterLastReadMessageId: Long?,
        val otherLastReadMessageId: Long?,
        val nextBeforeMessageId: Long?,
    )
}
