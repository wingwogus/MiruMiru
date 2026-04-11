package com.example.domain.chat

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MessageRoomRepository : JpaRepository<MessageRoom, Long> {
    fun findByPostIdAndMember1IdAndMember2Id(postId: Long, member1Id: Long, member2Id: Long): MessageRoom?
    fun findAllByCreatedAtBefore(createdAt: LocalDateTime): List<MessageRoom>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MessageRoom r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): MessageRoom?
}
