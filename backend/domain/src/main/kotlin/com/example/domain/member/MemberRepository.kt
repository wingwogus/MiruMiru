package com.example.domain.member

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository: JpaRepository<Member, Long> {
    @EntityGraph(attributePaths = ["university", "major"])
    fun findProfileById(id: Long): Member?
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
}
