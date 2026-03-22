package com.example.application.chat.event

interface ChatEventPublisher {
    fun publish(event: ChatEvent)
}

