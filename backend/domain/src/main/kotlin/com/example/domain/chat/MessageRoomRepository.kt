package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface MessageRoomRepository : JpaRepository<MessageRoom, Long> {
    fun findByPostIdAndMember1IdAndMember2Id(postId: Long, member1Id: Long, member2Id: Long): MessageRoom?
}
