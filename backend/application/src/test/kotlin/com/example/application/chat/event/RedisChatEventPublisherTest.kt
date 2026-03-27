package com.example.application.chat.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.redis.core.StringRedisTemplate

class RedisChatEventPublisherTest {

    @Test
    fun `publish sends serialized chat event to redis channel`() {
        val redis = mock(StringRedisTemplate::class.java)
        val objectMapper = ObjectMapper()
        val publisher = RedisChatEventPublisher(redis, objectMapper)
        val event = ChatEvent(
            type = ChatEventType.MESSAGE,
            roomId = 10L,
            message = ChatMessageEvent(
                id = 99L,
                roomId = 10L,
                senderId = 1L,
                content = "hello",
                createdAt = null,
            ),
        )

        publisher.publish(event)

        verify(redis).convertAndSend(
            eq(RedisChatEventPublisher.CHANNEL),
            eq(objectMapper.writeValueAsString(event)),
        )
    }
}
