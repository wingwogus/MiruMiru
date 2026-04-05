package com.example.application.chat

sealed interface ChatModerationQuery {
    data class GetBlocks(
        val requesterId: Long,
    ) : ChatModerationQuery
}
