package com.example.api.dto.chat

import com.example.application.chat.read.ChatQueryResult
import java.time.LocalDateTime

class ChatResponses {

    data class RoomSummaryResponse(
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

    data class RoomCreatedResponse(
        val roomId: Long,
        val postId: Long,
        val member1Id: Long,
        val member2Id: Long,
        val isAnon1: Boolean,
        val isAnon2: Boolean,
        val created: Boolean,
    )

    data class MessageDto(
        val id: Long,
        val senderId: Long,
        val content: String,
        val createdAt: LocalDateTime?,
    ) {
        companion object {
            fun from(summary: ChatQueryResult.MessageSummary): MessageDto =
                MessageDto(
                    id = summary.id,
                    senderId = summary.senderId,
                    content = summary.content,
                    createdAt = summary.createdAt,
                )
        }
    }

    data class MessagesResponse(
        val roomId: Long,
        val messages: List<MessageDto>,
        val requesterLastReadMessageId: Long?,
        val otherLastReadMessageId: Long?,
        val nextBeforeMessageId: Long?,
    )

    data class ReadMarkedResponse(
        val roomId: Long,
        val readerId: Long,
        val lastReadMessageId: Long,
        val unreadCount: Long,
    )
}
