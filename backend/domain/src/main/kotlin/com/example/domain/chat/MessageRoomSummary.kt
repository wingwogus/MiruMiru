package com.example.domain.chat

import com.example.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "message_room_summary",
    indexes = [
        Index(name = "idx_room_summary_last_message_id", columnList = "last_message_id"),
        Index(name = "idx_room_summary_last_message_created_at", columnList = "last_message_created_at"),
    ]
)
class MessageRoomSummary(
    @Id
    @Column(name = "room_id")
    val roomId: Long,

    @Column(name = "last_message_id")
    var lastMessageId: Long? = null,

    @Column(name = "last_message_content")
    var lastMessageContent: String? = null,

    @Column(name = "last_message_created_at")
    var lastMessageCreatedAt: LocalDateTime? = null,

    @Column(name = "member_1_unread_count", nullable = false)
    var member1UnreadCount: Long = 0,

    @Column(name = "member_2_unread_count", nullable = false)
    var member2UnreadCount: Long = 0,
) : BaseTimeEntity() {

    fun setUnreadCountFor(memberId: Long, room: MessageRoom, unreadCount: Long) {
        if (room.member1.id == memberId) {
            member1UnreadCount = unreadCount
            return
        }

        if (room.member2.id == memberId) {
            member2UnreadCount = unreadCount
            return
        }

        throw IllegalArgumentException("Not a room participant")
    }
    fun applyLastMessage(
        messageId: Long,
        content: String,
        createdAt: LocalDateTime?,
    ) {
        if ((lastMessageId ?: 0L) > messageId) {
            return
        }
        lastMessageId = messageId
        lastMessageContent = content
        lastMessageCreatedAt = createdAt
    }
}
