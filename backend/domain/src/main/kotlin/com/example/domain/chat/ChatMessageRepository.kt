package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository : JpaRepository<ChatMessage, Long>, ChatMessageQueryRepository
{
    fun deleteByRoomIdIn(roomIds: Collection<Long>): Long
}
