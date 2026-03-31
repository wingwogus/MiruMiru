package com.example.application.chat

import com.example.application.chat.event.ChatEvent
import com.example.application.chat.event.ChatEventPublisher
import com.example.application.chat.event.ChatEventType
import com.example.application.chat.event.ChatMessageEvent
import com.example.application.chat.event.ChatReadEvent
import com.example.application.chat.event.ChatUnreadCountEvent
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.chat.ChatMessage
import com.example.domain.chat.ChatBlockRepository
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.comment.CommentRepository
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.max
import kotlin.math.min

@Service
@Transactional
class ChatService(
    private val postRepository: PostRepository,
    private val memberRepository: MemberRepository,
    private val messageRoomRepository: MessageRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val commentRepository: CommentRepository,
    private val chatBlockRepository: ChatBlockRepository,
    private val chatQueryService: ChatQueryService,
    private val chatEventPublisher: ChatEventPublisher,
) {

    fun createRoom(command: ChatCommand.CreateRoom): ChatResult.RoomCreated {
        val requester = memberRepository.findById(command.requesterId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        val post = postRepository.findById(command.postId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }

        val partnerId = when {
            post.member.id == requester.id -> command.partnerMemberId
                ?: throw BusinessException(
                    ErrorCode.INVALID_INPUT,
                    customMessage = "partner_member_id_required_for_post_owner"
                )
            command.partnerMemberId != null -> command.partnerMemberId
            else -> post.member.id
        }

        if (partnerId == requester.id) {
            throw BusinessException(ErrorCode.INVALID_INPUT, customMessage = "cannot_create_room_with_self")
        }

        val otherMember = memberRepository.findById(partnerId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }

        validateSameUniversity(requesterUniversityId = requester.university.id, partnerUniversityId = otherMember.university.id)
        validatePostUniversity(postUniversityId = post.board.university.id, requesterUniversityId = requester.university.id)
        validatePostParticipant(postId = post.id, postAuthorId = post.member.id, memberId = requester.id, role = "requester")
        validatePostParticipant(postId = post.id, postAuthorId = post.member.id, memberId = otherMember.id, role = "partner")

        if (isBlockedBetween(requester.id, otherMember.id)) {
            throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "chat_blocked_between_members")
        }

        val existingRoom = messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
            postId = post.id,
            member1Id = requester.id,
            member2Id = otherMember.id,
        ) ?: messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
            postId = post.id,
            member1Id = otherMember.id,
            member2Id = requester.id,
        )

        if (existingRoom != null) {
            val requesterIsMember1 = existingRoom.member1.id == requester.id
            return ChatResult.RoomCreated(
                roomId = existingRoom.id,
                postId = existingRoom.post.id,
                member1Id = requester.id,
                member2Id = otherMember.id,
                isAnon1 = if (requesterIsMember1) existingRoom.isAnon1 else existingRoom.isAnon2,
                isAnon2 = if (requesterIsMember1) existingRoom.isAnon2 else existingRoom.isAnon1,
                created = false,
            )
        }

        val room = MessageRoom(
            post = post,
            member1 = requester,
            member2 = otherMember,
            isAnon1 = command.requesterIsAnonymous,
            isAnon2 = post.isAnonymous,
        )

        val saved = messageRoomRepository.save(room)
        return ChatResult.RoomCreated(
            roomId = saved.id,
            postId = post.id,
            member1Id = requester.id,
            member2Id = otherMember.id,
            isAnon1 = saved.isAnon1,
            isAnon2 = saved.isAnon2,
            created = true,
        )
    }

    fun sendMessage(command: ChatCommand.SendMessage): ChatResult.MessageSent {
        val sender = memberRepository.findById(command.senderId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        val room = messageRoomRepository.findById(command.roomId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }

        if (!room.isParticipant(sender.id)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val receiverId = room.otherMemberId(sender.id)
        if (isBlockedBetween(sender.id, receiverId)) {
            throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "chat_blocked_between_members")
        }

        val message = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = sender,
                content = command.content,
            )
        )

        val receiverLastReadId = room.getLastReadMessageId(receiverId)
        val unreadCount = chatQueryService.countUnread(room.id, receiverId, receiverLastReadId)

        chatEventPublisher.publish(
            ChatEvent(
                type = ChatEventType.MESSAGE,
                roomId = room.id,
                message = ChatMessageEvent(
                    id = message.id,
                    roomId = room.id,
                    senderId = sender.id,
                    content = message.content,
                    createdAt = message.createdAt,
                )
            )
        )

        chatEventPublisher.publish(
            ChatEvent(
                type = ChatEventType.UNREAD_COUNT,
                roomId = room.id,
                unread = ChatUnreadCountEvent(
                    memberId = receiverId,
                    unreadCount = unreadCount,
                )
            )
        )

        return ChatResult.MessageSent(
            id = message.id,
            roomId = room.id,
            senderId = sender.id,
            content = message.content,
            createdAt = message.createdAt,
        )
    }

    fun markRead(command: ChatCommand.MarkRead): ChatResult.ReadMarked {
        val room = messageRoomRepository.findById(command.roomId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }

        if (!room.isParticipant(command.readerId)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        room.updateLastReadMessageId(command.readerId, command.lastReadMessageId)
        val savedRoom = messageRoomRepository.save(room)

        val unreadCount = chatQueryService.countUnread(
            roomId = savedRoom.id,
            memberId = command.readerId,
            lastReadMessageId = savedRoom.getLastReadMessageId(command.readerId),
        )

        chatEventPublisher.publish(
            ChatEvent(
                type = ChatEventType.READ,
                roomId = savedRoom.id,
                read = ChatReadEvent(
                    readerId = command.readerId,
                    lastReadMessageId = command.lastReadMessageId,
                )
            )
        )

        chatEventPublisher.publish(
            ChatEvent(
                type = ChatEventType.UNREAD_COUNT,
                roomId = savedRoom.id,
                unread = ChatUnreadCountEvent(
                    memberId = command.readerId,
                    unreadCount = unreadCount,
                )
            )
        )

        return ChatResult.ReadMarked(
            roomId = savedRoom.id,
            readerId = command.readerId,
            lastReadMessageId = command.lastReadMessageId,
            unreadCount = unreadCount,
        )
    }

    private fun validateSameUniversity(requesterUniversityId: Long, partnerUniversityId: Long) {
        if (requesterUniversityId != partnerUniversityId) {
            throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "cross_university_chat_not_allowed")
        }
    }

    private fun validatePostUniversity(postUniversityId: Long, requesterUniversityId: Long) {
        if (postUniversityId != requesterUniversityId) {
            throw BusinessException(ErrorCode.FORBIDDEN, customMessage = "post_university_mismatch")
        }
    }

    private fun validatePostParticipant(postId: Long, postAuthorId: Long, memberId: Long, role: String) {
        val isAuthor = postAuthorId == memberId
        val hasCommented = commentRepository.existsByPostIdAndMemberId(postId, memberId)
        if (!isAuthor && !hasCommented) {
            throw BusinessException(
                ErrorCode.FORBIDDEN,
                customMessage = "chat_participant_required",
                detail = mapOf("role" to role)
            )
        }
    }

    private fun isBlockedBetween(memberAId: Long, memberBId: Long): Boolean {
        val first = min(memberAId, memberBId)
        val second = max(memberAId, memberBId)
        return chatBlockRepository.existsByMember1IdAndMember2Id(first, second)
    }
}
