package com.example.api.chat

import com.example.application.chat.event.RedisChatEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
@ConditionalOnProperty(name = ["chat.redis.enabled"], havingValue = "true")
class ChatRedisPubSubConfig {

    @Bean
    fun chatRedisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        subscriber: ChatRedisSubscriber,
    ): RedisMessageListenerContainer {
        return RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            addMessageListener(subscriber, ChannelTopic(RedisChatEventPublisher.CHANNEL))
        }
    }
}
