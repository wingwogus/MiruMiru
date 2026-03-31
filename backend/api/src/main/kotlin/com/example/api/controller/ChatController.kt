package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.chat.ChatRequests
import com.example.api.dto.chat.ChatResponses
import com.example.application.chat.ChatCommand
import com.example.application.chat.ChatQuery
import com.example.application.chat.ChatQueryService
import com.example.application.chat.ChatService
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/message-rooms")
class ChatController(
    private val chatService: ChatService,
    private val chatQueryService: ChatQueryService,
) {

    @GetMapping
    fun getMyRooms(
        @AuthenticationPrincipal userId: String,
        @Valid params: ChatRequests.GetRoomsParams,
    ): ResponseEntity<ApiResponse<List<ChatResponses.RoomSummaryResponse>>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val result = chatQueryService.getMyRooms(
            ChatQuery.GetMyRooms(
                requesterId = requesterId,
                limit = params.limit,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                result.rooms.map {
                    ChatResponses.RoomSummaryResponse(
                        roomId = it.roomId,
                        postId = it.postId,
                        postTitle = it.postTitle,
                        otherMemberId = it.otherMemberId,
                        lastMessageId = it.lastMessageId,
                        lastMessageContent = it.lastMessageContent,
                        lastMessageCreatedAt = it.lastMessageCreatedAt,
                        unreadCount = it.unreadCount,
                        myLastReadMessageId = it.myLastReadMessageId,
                        otherLastReadMessageId = it.otherLastReadMessageId,
                        isAnonMe = it.isAnonMe,
                        isAnonOther = it.isAnonOther,
                    )
                }
            )
        )
    }

    @PostMapping
    fun createRoom(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: ChatRequests.CreateRoomRequest,
    ): ResponseEntity<ApiResponse<ChatResponses.RoomCreatedResponse>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val postId = request.postId ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        val result = chatService.createRoom(
            ChatCommand.CreateRoom(
                requesterId = requesterId,
                postId = postId,
                requesterIsAnonymous = request.requesterIsAnonymous,
                partnerMemberId = request.partnerMemberId,
            )
        )

        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.ok(
                    ChatResponses.RoomCreatedResponse(
                        roomId = result.roomId,
                        postId = result.postId,
                        member1Id = result.member1Id,
                        member2Id = result.member2Id,
                        isAnon1 = result.isAnon1,
                        isAnon2 = result.isAnon2,
                        created = result.created,
                    )
                )
            )
    }

    @GetMapping("/{roomId}/messages")
    fun getMessages(
        @AuthenticationPrincipal userId: String,
        @PathVariable roomId: Long,
        @Valid params: ChatRequests.GetMessagesParams,
    ): ResponseEntity<ApiResponse<ChatResponses.MessagesResponse>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val result = chatQueryService.getMessages(
            ChatQuery.GetMessages(
                requesterId = requesterId,
                roomId = roomId,
                beforeMessageId = params.beforeMessageId,
                limit = params.limit,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                ChatResponses.MessagesResponse(
                    roomId = result.roomId,
                    messages = result.messages.map(ChatResponses.ChatMessagePayload::from),
                    requesterLastReadMessageId = result.requesterLastReadMessageId,
                    otherLastReadMessageId = result.otherLastReadMessageId,
                    nextBeforeMessageId = result.nextBeforeMessageId,
                )
            )
        )
    }

    @PatchMapping("/{roomId}/read")
    fun markRead(
        @AuthenticationPrincipal userId: String,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: ChatRequests.MarkReadRequest,
    ): ResponseEntity<ApiResponse<ChatResponses.ReadMarkedResponse>> {
        val readerId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val lastReadMessageId = request.lastReadMessageId ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        val result = chatService.markRead(
            ChatCommand.MarkRead(
                readerId = readerId,
                roomId = roomId,
                lastReadMessageId = lastReadMessageId,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                ChatResponses.ReadMarkedResponse(
                    roomId = result.roomId,
                    readerId = result.readerId,
                    lastReadMessageId = result.lastReadMessageId,
                    unreadCount = result.unreadCount,
                )
            )
        )
    }

    @PostMapping("/{roomId}/messages")
    fun sendMessage(
        @AuthenticationPrincipal userId: String,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: ChatRequests.SendMessageRequest,
    ): ResponseEntity<ApiResponse<ChatResponses.ChatMessagePayload>> {
        val senderId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val content = request.content ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        val result = chatService.sendMessage(
            ChatCommand.SendMessage(
                senderId = senderId,
                roomId = roomId,
                content = content,
            )
        )

        return ResponseEntity.ok(ApiResponse.ok(ChatResponses.ChatMessagePayload.from(result)))
    }
}
