package com.example.application.security

import com.example.domain.member.Member

data class MemberDto(
    val id: Long,
    val email: String,
    val provider: String,
    val role: String = "ROLE_USER"
) {
    companion object {
        fun from(member: Member): MemberDto {
            return MemberDto(
                id = member.id,
                email = member.loginId,
                provider = member.provider
            )
        }
    }
}