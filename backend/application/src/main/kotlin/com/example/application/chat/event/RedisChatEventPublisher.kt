package com.example.application.chat.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["chat.redis.enabled"], havingValue = "true")
class RedisChatEventPublisher(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : ChatEventPublisher {

    companion object {
        const val CHANNEL = "mirumiru:chat-events"
    }

    override fun publish(event: ChatEvent) {
        redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event))
    }
}
