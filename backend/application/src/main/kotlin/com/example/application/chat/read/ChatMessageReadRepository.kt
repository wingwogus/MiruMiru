package com.example.application.chat.read

interface ChatMessageReadRepository {
    fun findLatest(roomId: Long, limit: Int): List<ChatQueryResult.MessageSummary>
    fun findBefore(roomId: Long, beforeMessageId: Long, limit: Int): List<ChatQueryResult.MessageSummary>
    fun countUnread(roomId: Long, memberId: Long, lastReadMessageId: Long?): Long
}
