package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MessageRoomRepository : JpaRepository<MessageRoom, Long>, MessageRoomQueryRepository {
    fun findByPostIdAndMember1IdAndMember2Id(postId: Long, member1Id: Long, member2Id: Long): MessageRoom?
    fun findAllByCreatedAtBefore(createdAt: LocalDateTime): List<MessageRoom>
}
