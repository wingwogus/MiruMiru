package com.example.application.chat

sealed interface ChatModerationCommand {
    data class Block(
        val requesterId: Long,
        val targetMemberId: Long,
    ) : ChatModerationCommand

    data class Unblock(
        val requesterId: Long,
        val targetMemberId: Long,
    ) : ChatModerationCommand

    data class Report(
        val requesterId: Long,
        val targetMemberId: Long,
        val roomId: Long? = null,
        val messageId: Long? = null,
        val reason: String,
        val detail: String? = null,
    ) : ChatModerationCommand
}

