package com.example.application.chat

import com.example.application.chat.read.ChatMessageReadRepository
import com.example.application.chat.read.ChatQueryResult
import com.example.application.chat.read.ChatRoomReadRepository
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.post.PostAnonymousService
import com.example.domain.chat.MessageRoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ChatQueryService(
    private val messageRoomRepository: MessageRoomRepository,
    private val chatRoomReadRepository: ChatRoomReadRepository,
    private val chatMessageReadRepository: ChatMessageReadRepository,
    private val postAnonymousService: PostAnonymousService,
) {

    fun getMyRooms(query: ChatQuery.GetMyRooms): ChatQueryResult.Rooms {
        val limit = query.limit.coerceIn(1, 100)
        val rows = chatRoomReadRepository.findMyRooms(query.requesterId, limit)
        val anonNumbersByPostAndMember = postAnonymousService.getAnonNumbersByPostIds(rows.map { it.postId })

        return ChatQueryResult.Rooms(
            rooms = rows.map {
                ChatQueryResult.RoomSummary(
                    roomId = it.roomId,
                    postId = it.postId,
                    postTitle = it.postTitle,
                    roomTitle = it.postTitle,
                    otherMemberId = it.otherMemberId,
                    counterpartDisplayName = counterpartDisplayName(
                        postId = it.postId,
                        otherMemberId = it.otherMemberId,
                        otherMemberNickname = it.otherMemberNickname,
                        useAnonymousLabel = it.isAnonOther,
                        anonNumbersByPostAndMember = anonNumbersByPostAndMember,
                    ),
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

    fun getMessages(query: ChatQuery.GetMessages): ChatQueryResult.Messages {
        val room = messageRoomRepository.findById(query.roomId).orElseThrow {
            BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        }

        if (!room.isParticipant(query.requesterId)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val limit = query.limit.coerceIn(1, 100)
        val descendingMessages = if (query.beforeMessageId == null) {
            chatMessageReadRepository.findLatest(query.roomId, limit)
        } else {
            chatMessageReadRepository.findBefore(query.roomId, query.beforeMessageId, limit)
        }
        val messages = descendingMessages.reversed()

        val requesterLastRead = room.getLastReadMessageId(query.requesterId)
        val otherId = room.otherMemberId(query.requesterId)
        val otherLastRead = room.getLastReadMessageId(otherId)

        return ChatQueryResult.Messages(
            roomId = room.id,
            messages = messages,
            requesterLastReadMessageId = requesterLastRead,
            otherLastReadMessageId = otherLastRead,
            nextBeforeMessageId = descendingMessages.lastOrNull()?.id,
        )
    }

    private fun counterpartDisplayName(
        postId: Long,
        otherMemberId: Long,
        otherMemberNickname: String,
        useAnonymousLabel: Boolean,
        anonNumbersByPostAndMember: Map<Pair<Long, Long>, Int>,
    ): String {
        if (!useAnonymousLabel) {
            return otherMemberNickname
        }

        return anonNumbersByPostAndMember[postId to otherMemberId]
            ?.let { anonNumber -> "익명 $anonNumber" }
            ?: "익명"
    }
}
