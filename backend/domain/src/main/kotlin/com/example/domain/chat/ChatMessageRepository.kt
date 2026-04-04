package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun existsByIdAndRoomId(id: Long, roomId: Long): Boolean
    fun deleteByRoomIdIn(roomIds: List<Long>): Long
}
