package com.example.application.member

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberQueryService(
    private val memberRepository: MemberRepository
) {

    fun getMyProfile(userId: String): MemberQueryResult.MemberProfile {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val member = memberRepository.findProfileById(parsedUserId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        return MemberQueryResult.MemberProfile(
            memberId = member.id,
            email = member.email,
            nickname = member.nickname,
            university = MemberQueryResult.UniversitySummary(
                universityId = member.university.id,
                name = member.university.name,
                emailDomain = member.university.emailDomain
            ),
            major = MemberQueryResult.MajorSummary(
                majorId = member.major.id,
                code = member.major.code,
                name = member.major.name
            )
        )
    }
}
