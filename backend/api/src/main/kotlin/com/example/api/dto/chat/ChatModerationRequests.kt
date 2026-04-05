package com.example.api.dto.chat

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class ChatModerationRequests {
    data class BlockRequest(
        @field:NotNull
        val targetMemberId: Long?,
    )

    data class ReportRequest(
        @field:NotNull
        val targetMemberId: Long?,
        val roomId: Long? = null,
        val messageId: Long? = null,
        @field:NotBlank
        @field:Size(max = 100)
        val reason: String?,
        @field:Size(max = 2000)
        val detail: String? = null,
    )
}
