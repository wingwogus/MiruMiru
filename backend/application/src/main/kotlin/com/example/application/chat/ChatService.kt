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
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostRepository
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
        val post = postRepository.findById(command.postId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }

        val otherMember = if (post.member.id == requester.id) {
            val partnerId = command.partnerMemberId
                ?: throw BusinessException(
                    ErrorCode.INVALID_INPUT,
                    customMessage = "partner_member_id_required_for_post_owner"
                )

            if (partnerId == requester.id) {
                throw BusinessException(ErrorCode.INVALID_INPUT, customMessage = "cannot_create_room_with_self")
            }

            memberRepository.findById(partnerId).orElseThrow {
                BusinessException(ErrorCode.USER_NOT_FOUND)
            }
        } else {
            post.member
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
}
