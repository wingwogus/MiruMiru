package com.example.api.dto.member

import com.example.application.member.MemberQueryResult

object MemberResponses {
    data class UniversitySummaryResponse(
        val universityId: Long,
        val name: String,
        val emailDomain: String
    ) {
        companion object {
            fun from(result: MemberQueryResult.UniversitySummary): UniversitySummaryResponse {
                return UniversitySummaryResponse(
                    universityId = result.universityId,
                    name = result.name,
                    emailDomain = result.emailDomain
                )
            }
        }
    }

    data class MajorSummaryResponse(
        val majorId: Long,
        val code: String,
        val name: String
    ) {
        companion object {
            fun from(result: MemberQueryResult.MajorSummary): MajorSummaryResponse {
                return MajorSummaryResponse(
                    majorId = result.majorId,
                    code = result.code,
                    name = result.name
                )
            }
        }
    }

    data class MemberProfileResponse(
        val memberId: Long,
        val email: String,
        val nickname: String,
        val university: UniversitySummaryResponse,
        val major: MajorSummaryResponse
    ) {
        companion object {
            fun from(result: MemberQueryResult.MemberProfile): MemberProfileResponse {
                return MemberProfileResponse(
                    memberId = result.memberId,
                    email = result.email,
                    nickname = result.nickname,
                    university = UniversitySummaryResponse.from(result.university),
                    major = MajorSummaryResponse.from(result.major)
                )
            }
        }
    }
}
