package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatBlockRepository : JpaRepository<ChatBlock, Long> {
    fun findByMember1IdAndMember2Id(member1Id: Long, member2Id: Long): ChatBlock?
    fun existsByMember1IdAndMember2Id(member1Id: Long, member2Id: Long): Boolean
    fun findAllByMember1IdOrMember2IdOrderByCreatedAtDesc(member1Id: Long, member2Id: Long): List<ChatBlock>
    fun deleteByMember1IdAndMember2Id(member1Id: Long, member2Id: Long): Long
}

