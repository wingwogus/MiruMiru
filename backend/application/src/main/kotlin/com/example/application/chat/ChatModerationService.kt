package com.example.application.chat

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.chat.ChatBlock
import com.example.domain.chat.ChatBlockRepository
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.ChatReport
import com.example.domain.chat.ChatReportRepository
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.max
import kotlin.math.min

@Service
@Transactional
class ChatModerationService(
    private val memberRepository: MemberRepository,
    private val chatBlockRepository: ChatBlockRepository,
    private val chatReportRepository: ChatReportRepository,
    private val messageRoomRepository: MessageRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {
    fun block(command: ChatModerationCommand.Block): ChatModerationResult.Blocked {
        val requester = getMember(command.requesterId)
        val target = getMember(command.targetMemberId)
        validateSameUniversity(requester, target)
        validateNotSelf(requester.id, target.id)

        val existing = findBlock(requester.id, target.id)
        if (existing != null) {
            return ChatModerationResult.Blocked(
                targetMemberId = target.id,
                blocked = true,
                created = false,
            )
        }

        val (firstId, secondId) = normalizePair(requester.id, target.id)
        val first = if (firstId == requester.id) requester else target
        val second = if (secondId == target.id) target else requester

        chatBlockRepository.save(
            ChatBlock(
                member1 = first,
                member2 = second,
                blockedBy = requester,
            )
        )

        return ChatModerationResult.Blocked(
            targetMemberId = target.id,
            blocked = true,
            created = true,
        )
    }

    fun unblock(command: ChatModerationCommand.Unblock): ChatModerationResult.Unblocked {
        validateNotSelf(command.requesterId, command.targetMemberId)
        val (firstId, secondId) = normalizePair(command.requesterId, command.targetMemberId)
        val deleted = chatBlockRepository.deleteByBlockedByIdAndMember1IdAndMember2Id(
            blockedById = command.requesterId,
            member1Id = firstId,
            member2Id = secondId,
        )
        return ChatModerationResult.Unblocked(
            targetMemberId = command.targetMemberId,
            unblocked = deleted > 0,
        )
    }

    fun getBlocks(query: ChatModerationQuery.GetBlocks): ChatModerationResult.BlockList {
        val blocks = chatBlockRepository.findAllByBlockedByIdOrderByCreatedAtDesc(query.requesterId)

        return ChatModerationResult.BlockList(
            blocks = blocks.map { block ->
                val targetMemberId = if (block.member1.id == query.requesterId) {
                    block.member2.id
                } else {
                    block.member1.id
                }
                ChatModerationResult.BlockItem(
                    targetMemberId = targetMemberId,
                    blockedAt = block.createdAt,
                )
            }
        )
    }

    fun report(command: ChatModerationCommand.Report): ChatModerationResult.Reported {
        val requester = getMember(command.requesterId)
        val target = getMember(command.targetMemberId)
        validateSameUniversity(requester, target)
        validateNotSelf(requester.id, target.id)

        command.roomId?.let { roomId ->
            val room = messageRoomRepository.findById(roomId).orElseThrow {
                BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND)
            }
            if (!room.isParticipant(requester.id) || !room.isParticipant(target.id)) {
                throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "invalid_report_room_participants")
            }
        }

        command.messageId?.let { messageId ->
            val message = chatMessageRepository.findById(messageId).orElseThrow {
                BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
            }
            if (message.sender.id != target.id) {
                throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "invalid_report_target_message")
            }
            if (command.roomId != null && message.room.id != command.roomId) {
                throw BusinessException(ErrorCode.INVALID_INPUT, customMessage = "report_room_message_mismatch")
            }
            if (!message.room.isParticipant(requester.id)) {
                throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "reporter_not_room_participant")
            }
        }

        val report = chatReportRepository.save(
            ChatReport(
                reporter = requester,
                target = target,
                roomId = command.roomId,
                messageId = command.messageId,
                reason = command.reason,
                detail = command.detail,
            )
        )

        val existingBlock = findBlock(requester.id, target.id)
        val blockCreated = if (existingBlock == null) {
            val (firstId, secondId) = normalizePair(requester.id, target.id)
            val first = if (firstId == requester.id) requester else target
            val second = if (secondId == target.id) target else requester
            chatBlockRepository.save(
                ChatBlock(
                    member1 = first,
                    member2 = second,
                    blockedBy = requester,
                )
            )
            true
        } else {
            false
        }

        return ChatModerationResult.Reported(
            reportId = report.id,
            targetMemberId = target.id,
            blocked = true,
            blockCreated = blockCreated,
        )
    }

    private fun getMember(memberId: Long): Member =
        memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }

    private fun findBlock(memberAId: Long, memberBId: Long): ChatBlock? {
        val (firstId, secondId) = normalizePair(memberAId, memberBId)
        return chatBlockRepository.findByMember1IdAndMember2IdAndBlockedById(firstId, secondId, memberAId)
    }

    private fun normalizePair(memberAId: Long, memberBId: Long): Pair<Long, Long> {
        val firstId = min(memberAId, memberBId)
        val secondId = max(memberAId, memberBId)
        return firstId to secondId
    }

    private fun validateNotSelf(requesterId: Long, targetMemberId: Long) {
        if (requesterId == targetMemberId) {
            throw BusinessException(ErrorCode.INVALID_INPUT, customMessage = "cannot_target_self")
        }
    }

    private fun validateSameUniversity(requester: Member, target: Member) {
        if (requester.university.id != target.university.id) {
            throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "cross_university_chat_not_allowed")
        }
    }
}
