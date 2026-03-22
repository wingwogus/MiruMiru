package com.example.domain.chat

interface ChatMessageQueryRepository {
    fun findLatest(roomId: Long, limit: Int): List<ChatMessageSummary>
    fun findBefore(roomId: Long, beforeMessageId: Long, limit: Int): List<ChatMessageSummary>
    fun countUnread(roomId: Long, memberId: Long, lastReadMessageId: Long?): Long
}

data class ChatMessageSummary(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val createdAt: java.time.LocalDateTime?,
)
