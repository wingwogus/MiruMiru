package com.example.api.websocket

import com.example.application.chat.ChatCommand
import com.example.application.chat.ChatService
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import jakarta.validation.constraints.NotBlank
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatWebSocketController(
    private val chatService: ChatService,
) {

    data class SendMessagePayload(
        @field:NotBlank
        val content: String,
    )

    data class ReadPayload(
        val lastReadMessageId: Long,
    )

    @MessageMapping("/chat/rooms/{roomId}/send")
    fun send(
        principal: Principal,
        @DestinationVariable roomId: Long,
        @Payload payload: SendMessagePayload,
    ) {
        val senderId = principal.name.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        chatService.sendMessage(
            ChatCommand.SendMessage(
                senderId = senderId,
                roomId = roomId,
                content = payload.content,
            )
        )
    }

    @MessageMapping("/chat/rooms/{roomId}/read")
    fun read(
        principal: Principal,
        @DestinationVariable roomId: Long,
        @Payload payload: ReadPayload,
    ) {
        val readerId = principal.name.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        chatService.markRead(
            ChatCommand.MarkRead(
                readerId = readerId,
                roomId = roomId,
                lastReadMessageId = payload.lastReadMessageId,
            )
        )
    }
}

