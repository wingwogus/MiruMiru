package com.example.application.chat

sealed interface ChatCommand {

    data class CreateRoom(
        val requesterId: Long,
        val postId: Long,
        val requesterIsAnonymous: Boolean,
        val partnerMemberId: Long? = null,
    ) : ChatCommand

    data class SendMessage(
        val senderId: Long,
        val roomId: Long,
        val content: String,
    ) : ChatCommand

    data class MarkRead(
        val readerId: Long,
        val roomId: Long,
        val lastReadMessageId: Long,
    ) : ChatCommand
}
