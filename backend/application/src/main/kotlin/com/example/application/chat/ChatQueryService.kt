package com.example.application.chat

import com.example.application.chat.read.ChatMessageReadRepository
import com.example.application.chat.read.ChatQueryResult
import com.example.application.chat.read.ChatRoomReadRepository
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.chat.MessageRoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ChatQueryService(
    private val messageRoomRepository: MessageRoomRepository,
    private val chatRoomReadRepository: ChatRoomReadRepository,
    private val chatMessageReadRepository: ChatMessageReadRepository,
) {

    fun getMyRooms(query: ChatQuery.GetMyRooms): ChatQueryResult.Rooms {
        val limit = query.limit.coerceIn(1, 100)
        return ChatQueryResult.Rooms(
            rooms = chatRoomReadRepository.findMyRooms(query.requesterId, limit).map {
                it.copy(
                    myLastReadMessageId = it.myLastReadMessageId?.takeIf { id -> id > 0 },
                    otherLastReadMessageId = it.otherLastReadMessageId?.takeIf { id -> id > 0 },
                )
            }
        )
    }

    fun getMessages(query: ChatQuery.GetMessages): ChatQueryResult.Messages {
        val room = messageRoomRepository.findById(query.roomId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }

        if (!room.isParticipant(query.requesterId)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val limit = query.limit.coerceIn(1, 100)
        val messages = if (query.beforeMessageId == null) {
            chatMessageReadRepository.findLatest(query.roomId, limit)
        } else {
            chatMessageReadRepository.findBefore(query.roomId, query.beforeMessageId, limit)
        }

        val requesterLastRead = room.getLastReadMessageId(query.requesterId)
        val otherId = room.otherMemberId(query.requesterId)
        val otherLastRead = room.getLastReadMessageId(otherId)

        return ChatQueryResult.Messages(
            roomId = room.id,
            messages = messages,
            requesterLastReadMessageId = requesterLastRead,
            otherLastReadMessageId = otherLastRead,
            nextBeforeMessageId = messages.lastOrNull()?.id,
        )
    }
}
