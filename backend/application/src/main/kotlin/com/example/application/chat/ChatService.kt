package com.example.application.chat

import com.example.application.chat.read.ChatMessageReadRepository
import com.example.application.chat.event.ChatEvent
import com.example.application.chat.event.ChatEventPublisher
import com.example.application.chat.event.ChatEventType
import com.example.application.chat.event.ChatMessageEvent
import com.example.application.chat.event.ChatReadEvent
import com.example.application.chat.event.ChatUnreadCountEvent
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.post.PostAnonymousService
import com.example.domain.chat.ChatMessage
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
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
    private val chatRoomCreateTxService: ChatRoomCreateTxService,
    private val chatRoomRecoveryService: ChatRoomRecoveryService,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatMessageReadRepository: ChatMessageReadRepository,
    private val chatEventPublisher: ChatEventPublisher,
    private val postAnonymousService: PostAnonymousService,
    private val chatAccessPolicy: ChatAccessPolicy,
) {

    fun createRoom(command: ChatCommand.CreateRoom): ChatResult.RoomCreated {
        val requester = memberRepository.findById(command.requesterId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        val post = postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(
            command.postId,
            requester.university.id,
        ) ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val otherMember = chatAccessPolicy.resolveOtherMember(
            requester = requester,
            post = post,
            partnerMemberId = command.partnerMemberId,
        )
        chatAccessPolicy.ensurePairNotBlocked(requester.id, otherMember.id)

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
            val anonNumbersByMemberId = ensureChatAnonNumbers(existingRoom, post)
            return toRoomCreatedResult(
                room = existingRoom,
                requester = requester,
                otherMember = otherMember,
                anonNumbersByMemberId = anonNumbersByMemberId,
                created = false,
            )
        }

        val room = MessageRoom(
            post = post,
            member1 = normalizedPair.member1,
            member2 = normalizedPair.member2,
            isAnon1 = normalizedPair.isAnon1,
            isAnon2 = normalizedPair.isAnon2,
        )
        val anonNumbersByMemberId = ensureChatAnonNumbers(room, post)

        return try {
            val saved = chatRoomCreateTxService.create(
                ChatRoomCreateTxService.CreateRequest(
                    postId = post.id,
                    member1Id = normalizedPair.member1.id,
                    member2Id = normalizedPair.member2.id,
                    isAnon1 = normalizedPair.isAnon1,
                    isAnon2 = normalizedPair.isAnon2,
                )
            )
            toRoomCreatedResult(
                room = saved,
                requester = requester,
                otherMember = otherMember,
                anonNumbersByMemberId = anonNumbersByMemberId,
                created = true,
            )
        } catch (_: DataIntegrityViolationException) {
            val concurrentRoom = chatRoomRecoveryService.findExisting(
                postId = post.id,
                member1Id = normalizedPair.member1.id,
                member2Id = normalizedPair.member2.id,
            )
            toRoomCreatedResult(
                room = concurrentRoom,
                requester = requester,
                otherMember = otherMember,
                anonNumbersByMemberId = anonNumbersByMemberId,
                created = false,
            )
        }
    }

    fun sendMessage(command: ChatCommand.SendMessage): ChatResult.MessageSent {
        val sender = memberRepository.findById(command.senderId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        val room = messageRoomRepository.findById(command.roomId).orElseThrow {
            BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        }

        if (!room.isParticipant(sender.id)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val receiverId = room.otherMemberId(sender.id)
        chatAccessPolicy.ensurePairNotBlocked(sender.id, receiverId)

        val message = chatMessageRepository.save(
            ChatMessage(
                room = room,
                sender = sender,
                content = command.content,
            )
        )

        val receiverLastReadId = room.getLastReadMessageId(receiverId)
        val unreadCount = chatMessageReadRepository.countUnread(room.id, receiverId, receiverLastReadId)

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
            BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        }

        if (!room.isParticipant(command.readerId)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        if (!chatMessageRepository.existsByIdAndRoomId(command.lastReadMessageId, room.id)) {
            throw BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND)
        }

        room.updateLastReadMessageId(command.readerId, command.lastReadMessageId)
        val savedRoom = messageRoomRepository.save(room)

        val unreadCount = chatMessageReadRepository.countUnread(
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
        requester: Member,
        otherMember: Member,
        anonNumbersByMemberId: Map<Long, Int>,
        created: Boolean,
    ): ChatResult.RoomCreated {
        val requesterIsMember1 = room.member1.id == requester.id
        val otherMemberIsAnonymous = if (requesterIsMember1) room.isAnon2 else room.isAnon1
        return ChatResult.RoomCreated(
            roomId = room.id,
            postId = room.post.id,
            member1Id = requester.id,
            member2Id = otherMember.id,
            roomTitle = room.post.title,
            counterpartDisplayName = if (otherMemberIsAnonymous) {
                anonNumbersByMemberId[otherMember.id]
                    ?.let { anonNumber -> "익명 $anonNumber" }
                    ?: "익명"
            } else {
                otherMember.nickname
            },
            isAnon1 = if (requesterIsMember1) room.isAnon1 else room.isAnon2,
            isAnon2 = if (requesterIsMember1) room.isAnon2 else room.isAnon1,
            created = created,
        )
    }

    private fun ensureChatAnonNumbers(room: MessageRoom, post: Post): Map<Long, Int> {
        val anonNumbersByMemberId = mutableMapOf<Long, Int>()

        if (room.isAnon1) {
            anonNumbersByMemberId[room.member1.id] = postAnonymousService.getOrCreateAnonNumber(post, room.member1)
        }

        if (room.isAnon2) {
            anonNumbersByMemberId[room.member2.id] = postAnonymousService.getOrCreateAnonNumber(post, room.member2)
        }

        return anonNumbersByMemberId
    }

    private data class NormalizedParticipants(
        val member1: Member,
        val member2: Member,
        val isAnon1: Boolean,
        val isAnon2: Boolean,
    )
}
