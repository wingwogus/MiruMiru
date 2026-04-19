package com.example.api.dto.chat

import java.time.LocalDateTime

class ChatModerationResponses {
    data class BlockResponse(
        val targetMemberId: Long,
        val blocked: Boolean,
        val created: Boolean,
    )

    data class UnblockResponse(
        val targetMemberId: Long,
        val unblocked: Boolean,
    )

    data class BlockListItemResponse(
        val targetMemberId: Long,
        val blockedAt: LocalDateTime?,
    )

    data class ReportResponse(
        val reportId: Long,
        val targetMemberId: Long,
        val blocked: Boolean,
        val blockCreated: Boolean,
    )
}
