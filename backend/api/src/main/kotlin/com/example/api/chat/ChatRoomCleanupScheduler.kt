package com.example.api.chat

import com.example.application.chat.ChatRoomCleanupService
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ChatRoomCleanupScheduler(
    private val chatRoomCleanupService: ChatRoomCleanupService,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(cron = "\${chat.room.cleanup.cron:0 0 4 * * *}")
    fun cleanupExpiredRooms() {
        val deleted = chatRoomCleanupService.deleteRoomsOlderThan(7)
        if (deleted > 0) {
            logger.info { "Deleted expired chat rooms older than 7 days. deleted=$deleted" }
        }
    }
}

