package com.example.domain.chat

import java.time.LocalDateTime

interface MessageRoomQueryRepository {
    fun findMyRooms(memberId: Long, limit: Int): List<MessageRoomSummary>
}

data class MessageRoomSummary(
    val roomId: Long,
    val postId: Long,
    val postTitle: String,
    val otherMemberId: Long,
    val lastMessageId: Long?,
    val lastMessageSenderId: Long?,
    val lastMessageContent: String?,
    val lastMessageCreatedAt: LocalDateTime?,
    val unreadCount: Long,
    val myLastReadMessageId: Long?,
    val otherLastReadMessageId: Long?,
    val isAnonMe: Boolean,
    val isAnonOther: Boolean,
)
