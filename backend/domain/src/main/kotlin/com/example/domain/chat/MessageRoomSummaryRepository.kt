package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType

interface MessageRoomSummaryRepository : JpaRepository<MessageRoomSummary, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MessageRoomSummary s where s.roomId = :roomId")
    fun findByRoomIdForUpdate(@Param("roomId") roomId: Long): MessageRoomSummary?

    fun deleteByRoomIdIn(roomIds: List<Long>): Long
}
