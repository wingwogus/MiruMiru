package com.example.application.chat

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.chat.ChatBlockRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

@Service
class ChatAccessPolicy(
    private val memberRepository: MemberRepository,
    private val chatBlockRepository: ChatBlockRepository,
) {
    fun resolveOtherMember(requester: Member, post: Post, partnerMemberId: Long?): Member {
        val targetMember = when {
            post.member.id == requester.id -> {
                val targetId = partnerMemberId
                    ?: throw BusinessException(
                        ErrorCode.INVALID_INPUT,
                        customMessage = "partner_member_id_required_for_post_owner"
                    )
                findSameUniversityMember(requester, targetId)
            }

            partnerMemberId != null -> findSameUniversityMember(requester, partnerMemberId)
            else -> post.member
        }

        if (targetMember.id == requester.id) {
            throw BusinessException(ErrorCode.INVALID_INPUT, customMessage = "cannot_create_room_with_self")
        }

        return targetMember
    }

    fun ensurePairNotBlocked(memberAId: Long, memberBId: Long) {
        if (isBlockedBetween(memberAId, memberBId)) {
            throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "chat_blocked_between_members")
        }
    }

    private fun findSameUniversityMember(requester: Member, targetMemberId: Long): Member =
        memberRepository.findByIdAndUniversityId(targetMemberId, requester.university.id)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

    private fun isBlockedBetween(memberAId: Long, memberBId: Long): Boolean {
        val firstId = min(memberAId, memberBId)
        val secondId = max(memberAId, memberBId)
        return chatBlockRepository.existsByMember1IdAndMember2Id(firstId, secondId)
    }
}
