package com.example.application.chat

import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class ChatRoomCleanupService(
    private val messageRoomRepository: MessageRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun deleteRoomsOlderThan(days: Long = 7): Long {
        val cutoff = nowProvider().minusDays(days)
        val expiredRooms = messageRoomRepository.findAllByCreatedAtBefore(cutoff)
        if (expiredRooms.isEmpty()) {
            return 0L
        }

        val roomIds = expiredRooms.map { it.id }
        chatMessageRepository.deleteByRoomIdIn(roomIds)
        messageRoomRepository.deleteAllByIdInBatch(roomIds)
        return roomIds.size.toLong()
    }
}
