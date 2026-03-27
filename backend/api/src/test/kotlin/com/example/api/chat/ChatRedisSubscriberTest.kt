package com.example.api.chat

import com.example.application.chat.event.ChatEvent
import com.example.application.chat.event.ChatEventType
import com.example.application.chat.event.ChatMessageEvent
import com.example.application.chat.event.ChatUnreadCountEvent
import com.example.application.chat.event.RedisChatEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.redis.connection.Message
import org.springframework.messaging.simp.SimpMessagingTemplate

class ChatRedisSubscriberTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val messagingTemplate = mock(SimpMessagingTemplate::class.java)
    private val subscriber = ChatRedisSubscriber(objectMapper, messagingTemplate)

    @Test
    fun `message event is broadcast to room topic`() {
        val event = ChatEvent(
            type = ChatEventType.MESSAGE,
            roomId = 42L,
            message = ChatMessageEvent(
                messageId = 100L,
                senderId = 2L,
                content = "hello",
                createdAt = null,
            ),
        )

        subscriber.onMessage(serializedMessage(event), null)

        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/42"), eq(event))
    }

    @Test
    fun `unread count event is sent to user queue`() {
        val event = ChatEvent(
            type = ChatEventType.UNREAD_COUNT,
            roomId = 42L,
            unread = ChatUnreadCountEvent(
                memberId = 9L,
                unreadCount = 3L,
            ),
        )

        subscriber.onMessage(serializedMessage(event), null)

        verify(messagingTemplate).convertAndSendToUser(eq("9"), eq("/queue/unread"), eq(event))
    }

    private fun serializedMessage(event: ChatEvent): Message {
        return object : Message {
            override fun getBody(): ByteArray = objectMapper.writeValueAsBytes(event)
            override fun getChannel(): ByteArray = RedisChatEventPublisher.CHANNEL.toByteArray()
        }
    }
}
