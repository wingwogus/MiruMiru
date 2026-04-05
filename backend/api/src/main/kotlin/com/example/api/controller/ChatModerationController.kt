package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.chat.ChatModerationRequests
import com.example.api.dto.chat.ChatModerationResponses
import com.example.application.chat.ChatModerationCommand
import com.example.application.chat.ChatModerationQuery
import com.example.application.chat.ChatModerationService
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/chat")
class ChatModerationController(
    private val chatModerationService: ChatModerationService,
) {
    @PostMapping("/blocks")
    fun block(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: ChatModerationRequests.BlockRequest,
    ): ResponseEntity<ApiResponse<ChatModerationResponses.BlockResponse>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val targetMemberId = request.targetMemberId ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        val result = chatModerationService.block(
            ChatModerationCommand.Block(
                requesterId = requesterId,
                targetMemberId = targetMemberId,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                ChatModerationResponses.BlockResponse(
                    targetMemberId = result.targetMemberId,
                    blocked = result.blocked,
                    created = result.created,
                )
            )
        )
    }

    @DeleteMapping("/blocks/{targetMemberId}")
    fun unblock(
        @AuthenticationPrincipal userId: String,
        @PathVariable targetMemberId: Long,
    ): ResponseEntity<ApiResponse<ChatModerationResponses.UnblockResponse>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val result = chatModerationService.unblock(
            ChatModerationCommand.Unblock(
                requesterId = requesterId,
                targetMemberId = targetMemberId,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                ChatModerationResponses.UnblockResponse(
                    targetMemberId = result.targetMemberId,
                    unblocked = result.unblocked,
                )
            )
        )
    }

    @GetMapping("/blocks")
    fun getBlocks(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<List<ChatModerationResponses.BlockListItemResponse>>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val result = chatModerationService.getBlocks(ChatModerationQuery.GetBlocks(requesterId))

        return ResponseEntity.ok(
            ApiResponse.ok(
                result.blocks.map {
                    ChatModerationResponses.BlockListItemResponse(
                        targetMemberId = it.targetMemberId,
                        blockedAt = it.blockedAt,
                    )
                }
            )
        )
    }

    @PostMapping("/reports")
    fun report(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: ChatModerationRequests.ReportRequest,
    ): ResponseEntity<ApiResponse<ChatModerationResponses.ReportResponse>> {
        val requesterId = userId.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val targetMemberId = request.targetMemberId ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        val reason = request.reason ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        val result = chatModerationService.report(
            ChatModerationCommand.Report(
                requesterId = requesterId,
                targetMemberId = targetMemberId,
                roomId = request.roomId,
                messageId = request.messageId,
                reason = reason,
                detail = request.detail,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                ChatModerationResponses.ReportResponse(
                    reportId = result.reportId,
                    targetMemberId = result.targetMemberId,
                    blocked = result.blocked,
                    blockCreated = result.blockCreated,
                )
            )
        )
    }
}
