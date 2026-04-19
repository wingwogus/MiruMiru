package com.example.application.chat

sealed interface ChatQuery {

    data class GetMyRooms(
        val requesterId: Long,
        val limit: Int = 30,
    ) : ChatQuery

    data class GetMessages(
        val requesterId: Long,
        val roomId: Long,
        val beforeMessageId: Long? = null,
        val limit: Int = 30,
    ) : ChatQuery
}
