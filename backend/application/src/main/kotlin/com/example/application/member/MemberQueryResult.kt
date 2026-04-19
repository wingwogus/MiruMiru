package com.example.application.member

object MemberQueryResult {
    data class UniversitySummary(
        val universityId: Long,
        val name: String,
        val emailDomain: String
    )

    data class MajorSummary(
        val majorId: Long,
        val code: String,
        val name: String
    )

    data class MemberProfile(
        val memberId: Long,
        val email: String,
        val nickname: String,
        val university: UniversitySummary,
        val major: MajorSummary
    )
}
