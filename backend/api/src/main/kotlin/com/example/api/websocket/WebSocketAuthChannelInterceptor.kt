package com.example.api.websocket

import com.example.application.security.TokenProvider
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor

class WebSocketAuthChannelInterceptor(
    private val tokenProvider: TokenProvider,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        if (accessor.command != StompCommand.CONNECT) {
            return message
        }

        val authHeader = accessor.getFirstNativeHeader("Authorization")
            ?: accessor.getFirstNativeHeader("authorization")
            ?: return null

        val token = authHeader.removePrefix("Bearer ").trim()
        if (token.isBlank() || !tokenProvider.validateToken(token)) {
            return null
        }

        val authentication = tokenProvider.getAuthentication(token)
        accessor.user = authentication
        return message
    }
}

