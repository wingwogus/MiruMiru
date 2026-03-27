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
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ChatService(
    private val postRepository: PostRepository,
    private val memberRepository: MemberRepository,
    private val messageRoomRepository: MessageRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventPublisher: ChatEventPublisher,
) {

    fun createRoom(command: ChatCommand.CreateRoom): ChatResult.RoomCreated {
        val requester = memberRepository.findById(command.requesterId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        val post = postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(
            command.postId,
            requester.university.id,
        ) ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val otherMember = resolveOtherMember(requester = requester, command = command, postAuthor = post.member)

        val normalizedPair = normalizeParticipants(
            requester = requester,
            otherMember = otherMember,
            requesterIsAnonymous = command.requesterIsAnonymous,
            otherMemberIsAnonymous = post.isAnonymous,
        )

        val existingRoom = messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
            postId = post.id,
            member1Id = normalizedPair.member1.id,
            member2Id = normalizedPair.member2.id,
        )

        if (existingRoom != null) {
            return toRoomCreatedResult(existingRoom, requester.id, otherMember.id, created = false)
        }

        val room = MessageRoom(
            post = post,
            member1 = normalizedPair.member1,
            member2 = normalizedPair.member2,
            isAnon1 = normalizedPair.isAnon1,
            isAnon2 = normalizedPair.isAnon2,
        )

        return try {
            val saved = messageRoomRepository.save(room)
            toRoomCreatedResult(saved, requester.id, otherMember.id, created = true)
        } catch (_: DataIntegrityViolationException) {
            val concurrentRoom = messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
                postId = post.id,
                member1Id = normalizedPair.member1.id,
                member2Id = normalizedPair.member2.id,
            ) ?: throw BusinessException(ErrorCode.INTERNAL_ERROR)
            toRoomCreatedResult(concurrentRoom, requester.id, otherMember.id, created = false)
        }
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

        val message = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = sender,
                content = command.content,
            )
        )

        val receiverId = room.otherMemberId(sender.id)
        val receiverLastReadId = room.getLastReadMessageId(receiverId)
        val unreadCount = chatMessageRepository.countUnread(room.id, receiverId, receiverLastReadId)

        chatEventPublisher.publish(
            ChatEvent(
                type = ChatEventType.MESSAGE,
                roomId = room.id,
                message = ChatMessageEvent(
                    messageId = message.id,
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
            messageId = message.id,
            roomId = room.id,
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

        val unreadCount = chatMessageRepository.countUnread(
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

    @Transactional(readOnly = true)
    fun getMyRooms(query: ChatQuery.GetMyRooms): ChatResult.Rooms {
        val limit = query.limit.coerceIn(1, 100)
        val rooms = messageRoomRepository.findMyRooms(query.requesterId, limit)

        return ChatResult.Rooms(
            rooms = rooms.map {
                ChatResult.RoomSummary(
                    roomId = it.roomId,
                    postId = it.postId,
                    postTitle = it.postTitle,
                    otherMemberId = it.otherMemberId,
                    lastMessageId = it.lastMessageId,
                    lastMessageContent = it.lastMessageContent,
                    lastMessageCreatedAt = it.lastMessageCreatedAt,
                    unreadCount = it.unreadCount,
                    myLastReadMessageId = it.myLastReadMessageId?.takeIf { id -> id > 0 },
                    otherLastReadMessageId = it.otherLastReadMessageId?.takeIf { id -> id > 0 },
                    isAnonMe = it.isAnonMe,
                    isAnonOther = it.isAnonOther,
                )
            }
        )
    }

    @Transactional(readOnly = true)
    fun getMessages(query: ChatQuery.GetMessages): ChatResult.Messages {
        val room = messageRoomRepository.findById(query.roomId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }

        if (!room.isParticipant(query.requesterId)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val limit = query.limit.coerceIn(1, 100)
        val messages = if (query.beforeMessageId == null) {
            chatMessageRepository.findLatest(query.roomId, limit)
        } else {
            chatMessageRepository.findBefore(query.roomId, query.beforeMessageId, limit)
        }

        val requesterLastRead = room.getLastReadMessageId(query.requesterId)
        val otherId = room.otherMemberId(query.requesterId)
        val otherLastRead = room.getLastReadMessageId(otherId)

        return ChatResult.Messages(
            roomId = room.id,
            messages = messages,
            requesterLastReadMessageId = requesterLastRead,
            otherLastReadMessageId = otherLastRead,
            nextBeforeMessageId = messages.lastOrNull()?.id,
        )
    }

    private fun resolveOtherMember(
        requester: Member,
        command: ChatCommand.CreateRoom,
        postAuthor: Member,
    ): Member {
        if (postAuthor.id != requester.id) {
            return postAuthor
        }

        val partnerId = command.partnerMemberId
            ?: throw BusinessException(
                ErrorCode.INVALID_INPUT,
                customMessage = "partner_member_id_required_for_post_owner"
            )

        if (partnerId == requester.id) {
            throw BusinessException(ErrorCode.INVALID_INPUT, customMessage = "cannot_create_room_with_self")
        }

        return memberRepository.findByIdAndUniversityId(partnerId, requester.university.id)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }

    private fun normalizeParticipants(
        requester: Member,
        otherMember: Member,
        requesterIsAnonymous: Boolean,
        otherMemberIsAnonymous: Boolean,
    ): NormalizedParticipants {
        return if (requester.id <= otherMember.id) {
            NormalizedParticipants(
                member1 = requester,
                member2 = otherMember,
                isAnon1 = requesterIsAnonymous,
                isAnon2 = otherMemberIsAnonymous,
            )
        } else {
            NormalizedParticipants(
                member1 = otherMember,
                member2 = requester,
                isAnon1 = otherMemberIsAnonymous,
                isAnon2 = requesterIsAnonymous,
            )
        }
    }

    private fun toRoomCreatedResult(
        room: MessageRoom,
        requesterId: Long,
        otherMemberId: Long,
        created: Boolean,
    ): ChatResult.RoomCreated {
        val requesterIsMember1 = room.member1.id == requesterId
        return ChatResult.RoomCreated(
            roomId = room.id,
            postId = room.post.id,
            member1Id = requesterId,
            member2Id = otherMemberId,
            isAnon1 = if (requesterIsMember1) room.isAnon1 else room.isAnon2,
            isAnon2 = if (requesterIsMember1) room.isAnon2 else room.isAnon1,
            created = created,
        )
    }

    private data class NormalizedParticipants(
        val member1: Member,
        val member2: Member,
        val isAnon1: Boolean,
        val isAnon2: Boolean,
    )
}
