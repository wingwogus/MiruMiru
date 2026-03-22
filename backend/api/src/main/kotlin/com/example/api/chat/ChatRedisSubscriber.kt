package com.example.api.chat

import com.example.application.chat.event.ChatEvent
import com.example.application.chat.event.ChatEventType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class ChatRedisSubscriber(
    private val objectMapper: ObjectMapper,
    private val messagingTemplate: SimpMessagingTemplate,
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val payload = message.body.toString(Charsets.UTF_8)
        val event = objectMapper.readValue(payload, ChatEvent::class.java)

        when (event.type) {
            ChatEventType.MESSAGE,
            ChatEventType.READ,
            -> messagingTemplate.convertAndSend("/sub/chat/rooms/${event.roomId}", event)

            ChatEventType.UNREAD_COUNT -> {
                val unread = event.unread ?: return
                messagingTemplate.convertAndSendToUser(
                    unread.memberId.toString(),
                    "/queue/unread",
                    event
                )
            }
        }
    }
}

