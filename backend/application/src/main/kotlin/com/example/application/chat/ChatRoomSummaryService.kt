package com.example.application.chat

import com.example.application.chat.read.ChatMessageReadRepository
import com.example.domain.chat.ChatMessage
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomSummary
import com.example.domain.chat.MessageRoomSummaryRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ChatRoomSummaryService(
    private val messageRoomSummaryRepository: MessageRoomSummaryRepository,
    private val chatMessageReadRepository: ChatMessageReadRepository,
) {

    fun ensureInitialized(room: MessageRoom): MessageRoomSummary {
        return messageRoomSummaryRepository.findById(room.id).orElseGet {
            messageRoomSummaryRepository.save(MessageRoomSummary(roomId = room.id))
        }
    }

    fun initializeForNewRoom(room: MessageRoom) {
        ensureInitialized(room)
    }

    fun onMessageSent(
        room: MessageRoom,
        message: ChatMessage,
        receiverId: Long,
        receiverUnreadCount: Long,
    ) {
        val summary = ensureInitializedForUpdate(room)
        summary.applyLastMessage(
            messageId = message.id,
            content = message.content,
            createdAt = message.createdAt,
        )
        summary.setUnreadCountFor(receiverId, room, receiverUnreadCount)
        messageRoomSummaryRepository.save(summary)
    }

    fun onReadMarked(
        room: MessageRoom,
        readerId: Long,
        readerUnreadCount: Long,
    ) {
        val summary = ensureInitializedForUpdate(room)
        summary.setUnreadCountFor(readerId, room, readerUnreadCount)
        messageRoomSummaryRepository.save(summary)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reconcileFromReadPointers(room: MessageRoom) {
        val summary = ensureInitializedForUpdate(room)
        val latest = chatMessageReadRepository.findLatest(room.id, 1).firstOrNull()
        if (latest != null) {
            summary.applyLastMessage(
                messageId = latest.id,
                content = latest.content,
                createdAt = latest.createdAt,
            )
        }

        summary.member1UnreadCount = chatMessageReadRepository.countUnread(
            roomId = room.id,
            memberId = room.member1.id,
            lastReadMessageId = room.member1LastReadMessageId,
        )
        summary.member2UnreadCount = chatMessageReadRepository.countUnread(
            roomId = room.id,
            memberId = room.member2.id,
            lastReadMessageId = room.member2LastReadMessageId,
        )
        messageRoomSummaryRepository.save(summary)
    }

    private fun ensureInitializedForUpdate(room: MessageRoom): MessageRoomSummary {
        messageRoomSummaryRepository.findByRoomIdForUpdate(room.id)?.let { return it }

        try {
            messageRoomSummaryRepository.saveAndFlush(MessageRoomSummary(roomId = room.id))
        } catch (_: DataIntegrityViolationException) {
            // concurrent initializer created the row first
        }

        return messageRoomSummaryRepository.findByRoomIdForUpdate(room.id)
            ?: messageRoomSummaryRepository.saveAndFlush(MessageRoomSummary(roomId = room.id))
    }
}
