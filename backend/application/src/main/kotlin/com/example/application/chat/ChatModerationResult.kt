package com.example.application.chat

import java.time.LocalDateTime

sealed interface ChatModerationResult {
    data class Blocked(
        val targetMemberId: Long,
        val blocked: Boolean,
        val created: Boolean,
    ) : ChatModerationResult

    data class Unblocked(
        val targetMemberId: Long,
        val unblocked: Boolean,
    ) : ChatModerationResult

    data class Reported(
        val reportId: Long,
        val targetMemberId: Long,
        val blocked: Boolean,
        val blockCreated: Boolean,
    ) : ChatModerationResult

    data class BlockList(
        val blocks: List<BlockItem>,
    ) : ChatModerationResult

    data class BlockItem(
        val targetMemberId: Long,
        val blockedAt: LocalDateTime?,
    )
}

