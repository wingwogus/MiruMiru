package com.example.application.chat.event

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["chat.redis.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpChatEventPublisher : ChatEventPublisher {
    override fun publish(event: ChatEvent) {
        // Tests can disable Redis and still exercise chat behavior without opening a broker connection.
    }
}
