package com.example.api.dto.chat

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class ChatRequests {

    data class CreateRoomRequest(
        @field:NotNull
        val postId: Long?,
        val requesterIsAnonymous: Boolean = false,
        val partnerMemberId: Long? = null,
    )

    data class MarkReadRequest(
        @field:NotNull
        val lastReadMessageId: Long?,
    )

    data class SendMessageRequest(
        @field:NotBlank
        val content: String?,
    )

    data class GetMessagesParams(
        val beforeMessageId: Long? = null,
        @field:Min(1)
        @field:Max(100)
        val limit: Int = 30,
    )

    data class GetRoomsParams(
        @field:Min(1)
        @field:Max(100)
        val limit: Int = 30,
    )
}
