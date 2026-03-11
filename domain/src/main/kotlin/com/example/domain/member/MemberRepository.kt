package com.example.domain.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository: JpaRepository<Member, Long> {
    fun findByLoginId(loginId: String): Member?
}