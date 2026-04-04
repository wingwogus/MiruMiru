package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatBlockRepository : JpaRepository<ChatBlock, Long> {
    fun findByMember1IdAndMember2IdAndBlockedById(member1Id: Long, member2Id: Long, blockedById: Long): ChatBlock?
    fun existsByMember1IdAndMember2Id(member1Id: Long, member2Id: Long): Boolean
    fun findAllByBlockedByIdOrderByCreatedAtDesc(blockedById: Long): List<ChatBlock>
    fun deleteByBlockedByIdAndMember1IdAndMember2Id(blockedById: Long, member1Id: Long, member2Id: Long): Long
}
